package com.drift.windows;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System-wide shortcuts via RegisterHotKey on a dedicated message-loop thread. Push-to-talk needs the
 * release event, which RegisterHotKey does not give, so a short polling loop watches the key state while held.
 */
public class GlobalHotkeys implements AutoCloseable {
    public interface Handler {
        void onPress();
        default void onRelease() { }
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalHotkeys.class);
    private static final int WM_HOTKEY = 0x0312, MOD_NOREPEAT = 0x4000;
    private final Map<String, Handler> bindings;
    private final Consumer<String> errors;
    private Thread thread;
    private volatile int threadId;
    private final AtomicBoolean running = new AtomicBoolean();

    /** @param bindings shortcut text (e.g. "ctrl+alt+space") to handler; invalid or empty shortcuts are skipped */
    public GlobalHotkeys(Map<String, Handler> bindings, Consumer<String> errors) {
        this.bindings = Map.copyOf(bindings);
        this.errors = errors;
    }

    public void start() {
        if (!PlatformInfo.isWindows() || !running.compareAndSet(false, true)) return;
        thread = Thread.ofPlatform().daemon().name("drift-hotkeys").start(this::loop);
    }

    private void loop() {
        try {
            threadId = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentThreadId();
            Map<Integer, Handler> byId = new java.util.HashMap<>();
            Map<Integer, Hotkey> keys = new java.util.HashMap<>();
            int id = 1;
            for (var e : bindings.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) continue;
                Hotkey hk;
                try { hk = Hotkey.parse(e.getKey()); } catch (IllegalArgumentException ex) { errors.accept(ex.getMessage()); continue; }
                if (!User32.INSTANCE.RegisterHotKey(null, id, hk.modifiers() | MOD_NOREPEAT, hk.virtualKey())) {
                    errors.accept("Shortcut " + hk.text() + " is already used by another program");
                    continue;
                }
                byId.put(id, e.getValue());
                keys.put(id, hk);
                id++;
            }
            WinUser.MSG msg = new WinUser.MSG();
            while (running.get() && User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) {
                    int hid = msg.wParam.intValue();
                    Handler h = byId.get(hid);
                    if (h != null) {
                        h.onPress();
                        int vk = keys.get(hid).virtualKey();
                        Thread.ofPlatform().daemon().start(() -> awaitRelease(vk, h));
                    }
                }
            }
            for (int registered : byId.keySet()) User32.INSTANCE.UnregisterHotKey(null, registered);
        } catch (RuntimeException | LinkageError e) {
            log.warn("Global hotkeys unavailable", e);
            errors.accept("Global shortcuts could not be started: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private static void awaitRelease(int vk, Handler h) {
        try {
            long deadline = System.currentTimeMillis() + 10 * 60_000;
            while ((User32.INSTANCE.GetAsyncKeyState(vk) & 0x8000) != 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
            h.onRelease();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, null, null);
    }
}
