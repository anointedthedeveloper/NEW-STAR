package com.drift.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Builds the command that starts the bundled PowerShell media helper without touching execution policy or disk. */
public final class MediaBridgeLauncher {
    private MediaBridgeLauncher() {}

    public static List<String> powershellCommand() throws IOException {
        try (InputStream in = MediaBridgeLauncher.class.getResourceAsStream("/bridge/media-bridge.ps1")) {
            if (in == null) throw new IOException("media-bridge.ps1 missing from the application");
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
        }
    }
}
