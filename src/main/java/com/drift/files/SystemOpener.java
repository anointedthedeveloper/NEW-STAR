package com.drift.files;

import java.nio.file.Path;
import java.util.List;

/** Opens things through the OS. Replaceable so tests never launch real programs. */
public interface SystemOpener {
    /** Opens a file or folder with its associated application / the file manager. */
    void open(Path path) throws java.io.IOException;

    /** Starts a program with a fixed argument list, detached, without a shell. */
    void startDetached(List<String> command, Path workingDir) throws java.io.IOException;
}
