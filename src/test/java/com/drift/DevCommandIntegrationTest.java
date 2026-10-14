package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.drift.actions.CommandOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real Maven template end to end. Needs `mvn` on PATH and network for plugin download: set DRIFT_TEST_MAVEN=1. */
class DevCommandIntegrationTest {
    @TempDir Path dir;

    private TestEnv project(String javaSource) throws Exception {
        TestEnv env = new TestEnv(dir.resolve("env"));
        Path p = Files.createDirectories(dir.resolve("demo"));
        Files.writeString(p.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                <groupId>demo</groupId><artifactId>demo</artifactId><version>1</version>
                <properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties></project>""");
        Files.createDirectories(p.resolve("src/main/java"));
        Files.writeString(p.resolve("src/main/java/A.java"), javaSource);
        env.projects.approve(p);
        env.projects.markOpened(p);
        env.settings.update(s -> s.withDeveloper(s.developer().withEnabled(true).withProjectRoots(java.util.List.of(dir.toString()))));
        return env;
    }

    @Test void approvedMavenTestsRunWithRealOutputAndExitStatus() throws Exception {
        assumeTrue("1".equals(System.getenv("DRIFT_TEST_MAVEN")), "set DRIFT_TEST_MAVEN=1 to run");
        TestEnv ok = project("public class A { }");
        var out = ok.orchestrator.process("Run the approved Maven tests");
        assertThat(out).isInstanceOf(CommandOutcome.Done.class);
        var data = ((CommandOutcome.Done) out).result().data();
        assertThat(data.get("exitCode")).isEqualTo(0);
        assertThat((String) data.get("output")).contains("BUILD SUCCESS");
        assertThat(ok.history.recent(1).get(0)).extracting("actionId", "status").containsExactly("dev.run", "OK");
    }

    @Test void failingBuildReportsNonZeroExitAndOutput() throws Exception {
        assumeTrue("1".equals(System.getenv("DRIFT_TEST_MAVEN")), "set DRIFT_TEST_MAVEN=1 to run");
        TestEnv bad = project("public class A { this does not compile }");
        var out = bad.orchestrator.process("run maven tests");
        assertThat(out).isInstanceOf(CommandOutcome.Failed.class);
        assertThat(out.message()).contains("failed").contains("exit code 1");
        assertThat(bad.history.recent(1).get(0).status()).isEqualTo("FAILED");
    }
}
