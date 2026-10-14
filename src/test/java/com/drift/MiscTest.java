package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drift.applications.AppEntry;
import com.drift.applications.NameMatcher;
import com.drift.applications.StartMenuAppCatalog;
import com.drift.files.FileSearchService;
import com.drift.history.Redactor;
import com.drift.windows.Hotkey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiscTest {
    @TempDir Path dir;

    @Test void hotkeyParsing() {
        Hotkey h = Hotkey.parse("Ctrl+Alt+Space");
        assertThat(h.modifiers()).isEqualTo(Hotkey.MOD_CONTROL | Hotkey.MOD_ALT);
        assertThat(h.virtualKey()).isEqualTo(0x20);
        assertThat(Hotkey.parse("ctrl+shift+f5").virtualKey()).isEqualTo(0x74);
        assertThat(Hotkey.parse("win+d").virtualKey()).isEqualTo('D');
        assertThat(Hotkey.isValid("space")).isFalse();      // needs a modifier
        assertThat(Hotkey.isValid("ctrl+alt")).isFalse();   // needs a key
        assertThat(Hotkey.isValid("ctrl+a+b")).isFalse();
        assertThat(Hotkey.isValid("ctrl+banana")).isFalse();
        assertThatThrownBy(() -> Hotkey.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void nameMatcherPrefersExactAndSupportsSynonyms() {
        List<AppEntry> apps = List.of(new AppEntry("1", "Visual Studio Code", "p"), new AppEntry("2", "Visual Studio 2022", "p"), new AppEntry("3", "Notepad", "p"));
        assertThat(NameMatcher.rank("visual studio code", apps).get(0).entry().id()).isEqualTo("1");
        assertThat(NameMatcher.rank("vscode", apps).get(0).entry().id()).isEqualTo("1");
        assertThat(NameMatcher.rank("notepd", apps).get(0).entry().id()).isEqualTo("3"); // small recognition error tolerated
        assertThat(NameMatcher.rank("calculator", apps)).isEmpty();
        assertThat(NameMatcher.rank("visual studio", apps)).hasSize(2); // ambiguous stays ambiguous
    }

    @Test void startMenuCatalogDiscoversShortcutsOnly() throws Exception {
        Path sub = Files.createDirectories(dir.resolve("Dev"));
        Files.writeString(sub.resolve("Cool Editor.lnk"), "");
        Files.writeString(dir.resolve("Uninstall Cool Editor.lnk"), "");
        Files.writeString(dir.resolve("readme.txt"), "");
        List<AppEntry> found = new StartMenuAppCatalog(List.of(dir, dir.resolve("missing"))).scan();
        assertThat(found).extracting(AppEntry::name).containsExactly("Cool Editor");
        assertThat(found.get(0).id()).hasSize(16);
    }

    @Test void fileSearchIsBoundedToRootsAndSkipsHidden() throws Exception {
        Path a = Files.createDirectories(dir.resolve("work/sub"));
        Files.writeString(a.resolve("Budget Report 2025.xlsx"), "x");
        Files.createDirectories(dir.resolve(".hidden"));
        Files.writeString(dir.resolve(".hidden/budget report.txt"), "x");
        var res = new FileSearchService().searchByName("budget report", List.of(dir));
        assertThat(res.matches()).hasSize(1);
        assertThat(new FileSearchService().searchByName("budget", List.of(dir.resolve("nowhere"))).matches()).isEmpty();
    }

    @Test void projectDiscoveryFindsMavenAndGradleRoots() throws Exception {
        Path p1 = Files.createDirectories(dir.resolve("alpha"));
        Files.writeString(p1.resolve("pom.xml"), "<project/>");
        Files.createDirectories(p1.resolve("module"));
        Files.writeString(p1.resolve("module/pom.xml"), "<project/>");
        Path p2 = Files.createDirectories(dir.resolve("beta"));
        Files.writeString(p2.resolve("build.gradle"), "");
        var res = new FileSearchService().findJavaProjects(List.of(dir));
        assertThat(res.matches()).containsExactlyInAnyOrder(p1, p2);
    }

    @Test void redactionRemovesSecrets() {
        assertThat(Redactor.redact("password: hunter2 ok")).doesNotContain("hunter2");
        assertThat(Redactor.redact("Authorization Bearer abcdefghijklmnop")).doesNotContain("abcdefghijklmnop");
        assertThat(Redactor.redact("key " + "a".repeat(40))).doesNotContain("a".repeat(40));
        assertThat(Redactor.redact("C:\\Users\\alice\\Documents\\x")).doesNotContain("alice");
        assertThat(Redactor.redact(null)).isNull();
    }
}
