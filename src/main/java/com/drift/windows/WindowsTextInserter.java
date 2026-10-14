package com.drift.windows;

import com.drift.dictation.TextInserter;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinUser.INPUT;
import java.io.IOException;

/** Types text with SendInput (Unicode key events), which works for any text-accepting window and needs no clipboard. */
public class WindowsTextInserter implements TextInserter {
    static final int MAX_CHARS = 20_000;
    private static final int KEYEVENTF_KEYUP = 0x2, KEYEVENTF_UNICODE = 0x4;
    private static final int VK_RETURN = 0x0D, VK_CONTROL = 0x11, VK_Z = 0x5A;

    @Override public boolean supported() { return PlatformInfo.isWindows(); }

    @Override
    public void insert(String text) throws IOException {
        if (!supported()) throw new IOException("Typing is only supported on Windows");
        if (text.length() > MAX_CHARS) throw new IOException("Text is longer than " + MAX_CHARS + " characters");
        try {
            for (int offset = 0; offset < text.length(); ) {
                int end = Math.min(text.length(), offset + 100);
                INPUT[] batch = (INPUT[]) new INPUT().toArray(0);
                java.util.List<int[]> events = new java.util.ArrayList<>(); // {vk, scan, flags}
                for (int i = offset; i < end; i++) {
                    char c = text.charAt(i);
                    if (c == '\r') continue;
                    if (c == '\n') { events.add(new int[]{VK_RETURN, 0, 0}); events.add(new int[]{VK_RETURN, 0, KEYEVENTF_KEYUP}); continue; }
                    if (c < 0x20 && c != '\t') continue; // never send other control characters
                    events.add(new int[]{0, c, KEYEVENTF_UNICODE});
                    events.add(new int[]{0, c, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP});
                }
                offset = end;
                send(events);
                Thread.sleep(5);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } catch (RuntimeException | LinkageError e) {
            throw new IOException("Windows input API unavailable: " + e.getMessage());
        }
    }

    @Override
    public void sendUndo() throws IOException {
        if (!supported()) throw new IOException("Undo is only supported on Windows");
        send(java.util.List.of(new int[]{VK_CONTROL, 0, 0}, new int[]{VK_Z, 0, 0}, new int[]{VK_Z, 0, KEYEVENTF_KEYUP}, new int[]{VK_CONTROL, 0, KEYEVENTF_KEYUP}));
    }

    private static void send(java.util.List<int[]> events) throws IOException {
        if (events.isEmpty()) return;
        INPUT[] inputs = (INPUT[]) new INPUT().toArray(events.size());
        for (int i = 0; i < events.size(); i++) {
            int[] e = events.get(i);
            inputs[i].type = new DWORD(INPUT.INPUT_KEYBOARD);
            inputs[i].input.setType("ki");
            inputs[i].input.ki.wVk = new WORD(e[0]);
            inputs[i].input.ki.wScan = new WORD(e[1]);
            inputs[i].input.ki.dwFlags = new DWORD(e[2]);
            inputs[i].input.ki.time = new DWORD(0);
            inputs[i].input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        }
        DWORD sent = User32.INSTANCE.SendInput(new DWORD(inputs.length), inputs, inputs[0].size());
        if (sent.intValue() != inputs.length) throw new IOException("Windows blocked some of the input (the target may be running as administrator)");
    }
}
