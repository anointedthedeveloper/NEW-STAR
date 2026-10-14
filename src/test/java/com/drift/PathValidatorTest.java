package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drift.files.PathValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathValidatorTest {
    @TempDir Path root;

    PathValidator validator() { return new PathValidator(() -> List.of(root)); }

    @Test void acceptsFilesInsideRoot() throws Exception {
        Path f = Files.writeString(root.resolve("notes.txt"), "x");
        assertThat(validator().validateOpenableFile(f.toString())).isEqualTo(f.toRealPath());
    }

    @Test void rejectsTraversalOutsideRoot() throws Exception {
        Path sub = Files.createDirectories(root.resolve("a"));
        Path outside = Files.createTempFile("outside", ".txt");
        try {
            String traversal = sub.resolve("..").resolve("..").resolve(outside.getParent().getFileName()).resolve(outside.getFileName()).toString();
            assertThatThrownBy(() -> validator().validateExisting(traversal)).isInstanceOf(PathValidator.PathRejectedException.class);
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void rejectsSymlinkEscape() throws Exception {
        Path outsideDir = Files.createTempDirectory("outsidedir");
        try {
            Files.writeString(outsideDir.resolve("secret.txt"), "s");
            Path link = root.resolve("link");
            try { Files.createSymbolicLink(link, outsideDir); } catch (UnsupportedOperationException | java.io.IOException e) { return; }
            assertThatThrownBy(() -> validator().validateExisting(link.resolve("secret.txt").toString()))
                    .isInstanceOf(PathValidator.PathRejectedException.class);
        } finally {
            Files.deleteIfExists(outsideDir.resolve("secret.txt"));
            Files.deleteIfExists(outsideDir);
        }
    }

    @Test void rejectsExecutablesAndScripts() throws Exception {
        for (String name : List.of("a.exe", "b.BAT", "c.ps1", "d.lnk", "e.jar", "f.cmd")) {
            Path f = Files.writeString(root.resolve(name), "x");
            assertThatThrownBy(() -> validator().validateOpenableFile(f.toString())).hasMessageContaining("executable or script");
        }
    }

    @Test void rejectsExcludedDirectoriesAndMalformedInput() throws Exception {
        Path sys = Files.createDirectories(root.resolve("Windows").resolve("System32"));
        assertThatThrownBy(() -> validator().validateExisting(sys.toString())).hasMessageContaining("excluded");
        assertThatThrownBy(() -> validator().validateExisting("")).isInstanceOf(PathValidator.PathRejectedException.class);
        assertThatThrownBy(() -> validator().validateExisting("bad\0path")).isInstanceOf(PathValidator.PathRejectedException.class);
        assertThatThrownBy(() -> validator().validateExisting(root.resolve("missing").toString())).isInstanceOf(PathValidator.PathRejectedException.class);
    }
}
