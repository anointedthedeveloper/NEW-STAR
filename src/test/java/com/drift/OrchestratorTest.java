package com.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.drift.actions.CommandOutcome;
import com.drift.config.DriftSettings;
import com.drift.learning.AliasRepository.AliasKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrchestratorTest {
    @TempDir Path dir;
    TestEnv env;

    @BeforeEach void setUp() { env = new TestEnv(dir); }

    @Test void opensDiscoveredApplication() {
        env.addApp("Visual Studio Code");
        CommandOutcome out = env.orchestrator.process("Open Visual Studio Code.");
        assertThat(out).isInstanceOf(CommandOutcome.Done.class);
        assertThat(env.launched).containsExactly("Visual Studio Code");
        assertThat(env.history.recent(5)).extracting("actionId", "status").contains(org.assertj.core.groups.Tuple.tuple("app.open", "OK"));
    }

    @Test void unknownApplicationIsNotInventedAndNothingLaunches() {
        env.addApp("Notepad");
        CommandOutcome out = env.orchestrator.process("open frobnicator");
        assertThat(out).isInstanceOf(CommandOutcome.Unsupported.class); // nothing to offer: no favourites or recents, so no guessing
        assertThat(env.launched).isEmpty();
    }

    @Test void learnsAliasFromUserChoiceAndResolvesItAfterRestart() {
        env.addApp("Visual Studio Code");
        env.addApp("Notepad");
        env.apps.setFavorite(env.apps.apps().get(1), true); // VS Code sorted after Notepad? ensure it is offered
        env.apps.setFavorite(env.apps.apps().get(0), true);
        var choice = (CommandOutcome.NeedsChoice) env.orchestrator.process("open my coding editor");
        assertThat(choice.canRemember()).isTrue();
        int idx = choice.options().indexOf("Visual Studio Code");
        assertThat(idx).isGreaterThanOrEqualTo(0);
        CommandOutcome done = env.orchestrator.choose(choice.pendingId(), idx, true);
        assertThat(done).isInstanceOf(CommandOutcome.Done.class);
        assertThat(env.launched).containsExactly("Visual Studio Code");

        // "restart": new service graph on the same database file
        TestEnv again = new TestEnv(dir);
        again.catalog.addAll(env.catalog);
        assertThat(again.orchestrator.process("open my coding editor")).isInstanceOf(CommandOutcome.Done.class);
        assertThat(again.launched).containsExactly("Visual Studio Code");
        // and deleting the alias removes the behaviour
        again.aliases.listAll().forEach(a -> again.aliases.delete(a.id()));
        assertThat(again.orchestrator.process("open my coding editor")).isNotInstanceOf(CommandOutcome.Done.class);
    }

    @Test void rememberByVoiceCreatesAliasAndAsksBeforeReplacing() {
        env.addApp("Visual Studio Code");
        env.addApp("Notepad");
        assertThat(env.orchestrator.process("Remember that I call Visual Studio Code my coding editor")).isInstanceOf(CommandOutcome.Done.class);
        assertThat(env.aliases.find(AliasKind.APP, "coding editor")).isPresent();
        // re-pointing the same phrase needs confirmation and changes nothing until approved
        var confirm = (CommandOutcome.NeedsConfirmation) env.orchestrator.process("Remember that I call Notepad my coding editor");
        assertThat(env.aliases.find(AliasKind.APP, "coding editor").get().target()).isEqualTo("id-visual-studio-code");
        env.orchestrator.confirm(confirm.pendingId(), false);
        assertThat(env.aliases.find(AliasKind.APP, "coding editor").get().target()).isEqualTo("id-visual-studio-code");
        var again = (CommandOutcome.NeedsConfirmation) env.orchestrator.process("Remember that I call Notepad my coding editor");
        env.orchestrator.confirm(again.pendingId(), true);
        assertThat(env.aliases.find(AliasKind.APP, "coding editor").get().target()).isEqualTo("id-notepad");
    }

    @Test void arbitraryCommandsAreNeverExecuted() {
        CommandOutcome out = env.orchestrator.process("run command del everything");
        assertThat(out).isInstanceOf(CommandOutcome.Unsupported.class);
        assertThat(out.message()).contains("never runs arbitrary commands");
        assertThat(env.started).isEmpty();
    }

    @Test void developerCommandsAreRefusedWhileDeveloperModeIsOff() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        env.projects.markOpened(project);
        CommandOutcome out = env.orchestrator.process("run the maven tests");
        assertThat(out).isInstanceOf(CommandOutcome.Refused.class);
        assertThat(out.message()).contains("Developer mode");
    }

    @Test void destructiveDevTemplateRequiresConfirmationShowingExactCommand() throws Exception {
        Path project = Files.createDirectories(dir.resolve("proj"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        env.projects.markOpened(project);
        env.settings.update(s -> new DriftSettings(s.appearance(), s.floating(), s.voice(), s.startup(),
                new DriftSettings.Developer(true, "", java.util.List.of(), 60), s.privacy(), s.monitoring(), s.shortcuts(), s.weather()));
        CommandOutcome out = env.orchestrator.process("run maven clean");
        assertThat(out).isInstanceOf(CommandOutcome.NeedsConfirmation.class);
        assertThat(out.message()).contains("clean").contains(project.toString());
        // declining runs nothing
        env.orchestrator.confirm(((CommandOutcome.NeedsConfirmation) out).pendingId(), false);
        assertThat(Files.exists(project.resolve("pom.xml"))).isTrue();
        assertThat(env.history.recent(10)).extracting("actionId").doesNotContain("dev.run");
    }

    @Test void foldersOpenOnlyWhenAllowed() throws Exception {
        Path docs = Files.createDirectories(dir.resolve("stuff"));
        env.aliases.put(AliasKind.FOLDER, "my stuff", docs.toString());
        assertThat(env.orchestrator.process("open my stuff folder")).isInstanceOf(CommandOutcome.Done.class);
        assertThat(env.opened).containsExactly(docs.toRealPath().toString());
    }

    @Test void executableFilesAreNeverOpenedByVoice() throws Exception {
        Path exe = Files.writeString(dir.resolve("setup.exe"), "x");
        var out = env.orchestrator.handle(new com.drift.actions.ActionRequest("file.open", java.util.Map.of("path", exe.toString()), "open setup",
                com.drift.actions.Source.PARSED, null, "x"), "open setup");
        assertThat(out).isInstanceOf(CommandOutcome.Failed.class);
        assertThat(env.opened).isEmpty();
    }

    @Test void learnedMappingCannotBeCreatedForPrivilegedActions() {
        env.learning.recordSuccess("run it", "dev.run", java.util.Map.of("task", "maven-clean"));
        assertThat(env.learning.lookup("run it")).isEmpty();
        assertThat(com.drift.learning.LearningService.canLearn("text.type")).isFalse();
        assertThat(com.drift.learning.LearningService.canLearn("app.open")).isTrue();
    }

    @Test void invalidRequestsAreRejectedBeforeExecution() {
        var out = env.orchestrator.handle(new com.drift.actions.ActionRequest("app.open", java.util.Map.of("bogus", "1"), "x",
                com.drift.actions.Source.PARSED, null, "x"), "x");
        assertThat(out).isInstanceOf(CommandOutcome.Refused.class);
        assertThat(env.launched).isEmpty();
    }

    @Test void timeoutsAreReportedAndRecorded() {
        env.registry.register(new com.drift.actions.ActionDefinition("test.slow", "slow", java.util.List.of(), com.drift.actions.Permission.SAFE,
                com.drift.actions.ActionDefinition.Confirmation.NEVER, java.time.Duration.ofMillis(200), "none", (r, c) -> {
                    Thread.sleep(5000);
                    return com.drift.actions.ActionResult.ok("never");
                }));
        var out = env.orchestrator.handle(new com.drift.actions.ActionRequest("test.slow", java.util.Map.of(), "slow", com.drift.actions.Source.UI, null, "slow"), "slow");
        assertThat(out).isInstanceOf(CommandOutcome.Failed.class);
        assertThat(out.message()).contains("Timed out");
        assertThat(env.history.recent(1).get(0).status()).isEqualTo("TIMEOUT");
    }
}
