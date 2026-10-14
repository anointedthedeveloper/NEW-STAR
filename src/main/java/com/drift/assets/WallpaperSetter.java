package com.drift.assets;

import java.nio.file.Path;
import java.util.Optional;

public interface WallpaperSetter {
    boolean supported();
    Optional<String> current();
    void set(Path image) throws java.io.IOException;
}
