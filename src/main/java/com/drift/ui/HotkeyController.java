package com.drift.ui;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEvent.Notification;
import com.drift.speech.VoiceService;
import com.drift.windows.GlobalHotkeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Binds the configured global shortcuts and rebinds when settings change. */
public class HotkeyController implements AutoCloseable {
    private final UiServices svc;
    private final FloatingWindow floating;
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private GlobalHotkeys hotkeys;

    public HotkeyController(UiServices svc, FloatingWindow floating) {
        this.svc = svc;
        this.floating = floating;
        svc.bus.subscribe(e -> { if (e instanceof DriftEvent.SettingsChanged) rebind(); });
    }

    public synchronized void rebind() {
        if (hotkeys != null) hotkeys.close();
        var sc = svc.settings.get().shortcuts();
        Map<String, GlobalHotkeys.Handler> map = new LinkedHashMap<>();
        if (!sc.pushToTalk().isBlank()) map.put(sc.pushToTalk(), new GlobalHotkeys.Handler() {
            @Override public void onPress() {
                worker.submit(() -> { if (svc.settings.get().voice().pushToTalk() || !svc.voice.isListening()) svc.voice.start(VoiceService.Mode.COMMAND); else svc.voice.stop(); });
            }
            @Override public void onRelease() { if (svc.settings.get().voice().pushToTalk()) worker.submit(svc.voice::stop); }
        });
        if (!sc.toggleInterface().isBlank()) map.put(sc.toggleInterface(), () -> floating.toggle());
        if (!sc.dictation().isBlank()) map.put(sc.dictation(), () -> worker.submit(() ->
                svc.coordinator.submit(svc.dictation.state() == com.drift.dictation.DictationService.State.DICTATING ? "stop dictation" : "start dictation")));
        hotkeys = new GlobalHotkeys(map, msg -> svc.bus.publish(new Notification("Shortcuts", msg)));
        hotkeys.start();
    }

    @Override public synchronized void close() {
        if (hotkeys != null) hotkeys.close();
        worker.shutdownNow();
    }
}
