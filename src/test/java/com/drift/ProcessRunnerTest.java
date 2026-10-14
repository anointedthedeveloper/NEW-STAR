package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.drift.dev.ProcessRunner;
import com.drift.windows.PlatformInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses POSIX shell commands as stand-ins for build tools; the runner itself is platform neutral. */
class ProcessRunnerTest {
    @TempDir Path dir;

    @BeforeEach void posixOnly() { assumeFalse(PlatformInfo.isWindows()); }

    @Test void capturesOutputAndExitCode() throws Exception {
        var r = new ProcessRunner(10_000).run(List.of("sh", "-c", "echo hello; echo oops >&2; exit 3"), dir, Duration.ofSeconds(10), new ProcessRunner.Handle(), null);
        assertThat(r.exitCode()).isEqualTo(3);
        assertThat(r.success()).isFalse();
        assertThat(r.output()).contains("hello").contains("oops");
    }

    @Test void successHasZeroExit() throws Exception {
        var r = new ProcessRunner(10_000).run(List.of("sh", "-c", "echo ok"), dir, Duration.ofSeconds(10), new ProcessRunner.Handle(), null);
        assertThat(r.success()).isTrue();
    }

    @Test void timeoutKillsProcess() throws Exception {
        long t = System.currentTimeMillis();
        var r = new ProcessRunner(10_000).run(List.of("sh", "-c", "sleep 30"), dir, Duration.ofMillis(300), new ProcessRunner.Handle(), null);
        assertThat(r.timedOut()).isTrue();
        assertThat(System.currentTimeMillis() - t).isLessThan(10_000);
    }

    @Test void cancellationStopsProcess() throws Exception {
        var handle = new ProcessRunner.Handle();
        new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException ignored) { } handle.cancel(); }).start();
        var r = new ProcessRunner(10_000).run(List.of("sh", "-c", "sleep 30"), dir, Duration.ofSeconds(30), handle, null);
        assertThat(r.cancelled()).isTrue();
        assertThat(r.success()).isFalse();
    }

    @Test void outputIsBounded() throws Exception {
        var r = new ProcessRunner(2_000).run(List.of("sh", "-c", "i=0; while [ $i -lt 5000 ]; do echo line-$i; i=$((i+1)); done"), dir,
                Duration.ofSeconds(20), new ProcessRunner.Handle(), null);
        assertThat(r.output().length()).isLessThanOrEqualTo(2_000);
        assertThat(r.outputTruncated()).isTrue();
        assertThat(r.exitCode()).isZero();
    }
}
