package com.drift.dev;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs a fixed argument list (never a shell string) with a timeout, bounded output capture and cancellation.
 */
public class ProcessRunner {
    public record Result(int exitCode, boolean timedOut, boolean cancelled, String output, boolean outputTruncated, long millis) {
        public boolean success() { return !timedOut && !cancelled && exitCode == 0; }
    }

    public static final class Handle {
        private volatile Process process;
        private volatile boolean cancelled;
        public void cancel() {
            cancelled = true;
            Process p = process;
            if (p != null) { p.descendants().forEach(ProcessHandle::destroyForcibly); p.destroyForcibly(); }
        }
        public boolean isCancelled() { return cancelled; }
    }

    private final int maxOutputBytes;

    public ProcessRunner(int maxOutputBytes) { this.maxOutputBytes = maxOutputBytes; }

    public Result run(List<String> command, Path workingDir, Duration timeout, Handle handle, Consumer<String> lineSink) throws IOException {
        long start = System.nanoTime();
        ProcessBuilder pb = new ProcessBuilder(command).directory(workingDir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        handle.process = p;
        if (handle.cancelled) handle.cancel();
        StringBuilder out = new StringBuilder();
        boolean[] truncated = {false};
        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (out.length() + line.length() + 1 <= maxOutputBytes) {
                        out.append(line).append('\n');
                        if (lineSink != null) lineSink.accept(line);
                    } else {
                        truncated[0] = true;
                    }
                }
            } catch (IOException ignored) {
                // stream closed by cancellation/timeout
            }
        });
        boolean finished;
        try {
            finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.cancel();
            finished = false;
        }
        boolean timedOut = false;
        if (!finished && !handle.cancelled) {
            timedOut = true;
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
        try { p.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { reader.get(2, TimeUnit.SECONDS); } catch (Exception ignored) { /* reader ends when stream closes */ }
        int code = p.isAlive() ? -1 : p.exitValue();
        String text;
        synchronized (out) { text = out.toString(); }
        return new Result(code, timedOut, handle.cancelled, text, truncated[0], (System.nanoTime() - start) / 1_000_000);
    }
}
