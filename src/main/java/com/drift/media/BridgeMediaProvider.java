package com.drift.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.drift.core.Json;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Talks to a long-lived helper process over a one-line-request / one-line-JSON-response protocol.
 * On Windows the helper is a PowerShell script using the documented GlobalSystemMediaTransportControls API.
 * The protocol (and failure handling) is covered by tests with a stand-in helper; the WinRT script itself can
 * only be verified on Windows.
 *
 * Protocol: first line from helper {"ready":true}. Requests: "LIST", "ART &lt;id&gt;", "CTRL &lt;PLAY|PAUSE|TOGGLE|NEXT|PREVIOUS&gt; &lt;id&gt;".
 */
public class BridgeMediaProvider implements MediaProvider {
    private static final int TIMEOUT_SECONDS = 8;
    private final List<String> command;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("drift-media-bridge").unstarted(r));
    private Process process;
    private BufferedWriter out;
    private BufferedReader in;
    private String lastError;
    private String currentId;

    public BridgeMediaProvider(List<String> command) { this.command = List.copyOf(command); }

    @Override
    public synchronized Availability availability() {
        if (lastError != null) return new Availability(false, lastError);
        return new Availability(true, "Windows media sessions (System Media Transport Controls)");
    }

    private synchronized JsonNode request(String line) throws MediaException {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ensureStarted();
                out.write(line);
                out.newLine();
                out.flush();
                Future<String> reply = io.submit(() -> in.readLine());
                String json = reply.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (json == null) throw new IOException("media helper exited");
                JsonNode n = Json.read(json, JsonNode.class);
                if (n.has("error")) throw new MediaException(n.get("error").asText());
                lastError = null;
                return n;
            } catch (IOException | TimeoutException | java.util.concurrent.ExecutionException e) {
                stopProcess();
                lastError = "Media helper failed: " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MediaException("Interrupted");
            }
        }
        throw new MediaException(lastError);
    }

    private void ensureStarted() throws IOException, InterruptedException, TimeoutException, java.util.concurrent.ExecutionException {
        if (process != null && process.isAlive()) return;
        process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String hello = io.submit(() -> in.readLine()).get(TIMEOUT_SECONDS * 2L, TimeUnit.SECONDS);
        if (hello == null || !Json.read(hello, JsonNode.class).path("ready").asBoolean(false)) throw new IOException("media helper did not become ready");
    }

    private void stopProcess() {
        if (process != null) { process.destroyForcibly(); process = null; }
    }

    @Override
    public List<MediaSession> sessions() throws MediaException {
        JsonNode n = request("LIST");
        currentId = n.path("current").isNull() || n.path("current").isMissingNode() ? null : n.path("current").asText(null);
        List<MediaSession> out = new ArrayList<>();
        for (JsonNode s : n.path("sessions")) {
            out.add(new MediaSession(s.path("id").asText(), s.path("app").asText(""), text(s, "title"), text(s, "artist"), text(s, "album"),
                    status(s.path("status").asText("")), s.path("position").asDouble(-1), s.path("duration").asDouble(-1),
                    s.path("canPlayPause").asBoolean(false), s.path("canNext").asBoolean(false), s.path("canPrevious").asBoolean(false),
                    s.path("hasArtwork").asBoolean(false)));
        }
        return out;
    }

    private static String text(JsonNode n, String f) { String v = n.path(f).asText(""); return v.isBlank() ? null : v; }

    private static MediaSession.Status status(String s) {
        return switch (s.toUpperCase()) { case "PLAYING" -> MediaSession.Status.PLAYING; case "PAUSED" -> MediaSession.Status.PAUSED;
            case "STOPPED" -> MediaSession.Status.STOPPED; default -> MediaSession.Status.OTHER; };
    }

    @Override public synchronized Optional<String> currentSessionId() { return Optional.ofNullable(currentId); }

    @Override
    public void send(String sessionId, Command c) throws MediaException {
        if (sessionId == null || !sessionId.matches("[\\w.!\\-_:\\\\ ]{1,260}")) throw new MediaException("Invalid media session id");
        JsonNode r = request("CTRL " + c.name() + " " + sessionId);
        if (!r.path("ok").asBoolean(false)) throw new MediaException("The media app did not accept that command");
    }

    @Override
    public Optional<byte[]> artwork(String sessionId) throws MediaException {
        if (sessionId == null || !sessionId.matches("[\\w.!\\-_:\\\\ ]{1,260}")) return Optional.empty();
        JsonNode r = request("ART " + sessionId);
        String b64 = r.path("data").asText("");
        if (b64.isEmpty() || b64.length() > 3_000_000) return Optional.empty();
        try { return Optional.of(Base64.getDecoder().decode(b64)); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    @Override public synchronized void close() { stopProcess(); io.shutdownNow(); }
}
