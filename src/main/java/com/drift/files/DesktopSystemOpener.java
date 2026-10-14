package com.drift.files;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class DesktopSystemOpener implements SystemOpener {
    @Override
    public void open(Path path) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("This system cannot open files or folders from Drift");
        }
        Desktop.getDesktop().open(path.toFile());
    }

    @Override
    public void startDetached(List<String> command, Path workingDir) throws IOException {
        new ProcessBuilder(command).directory(workingDir.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }
}
