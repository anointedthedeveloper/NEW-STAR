package com.drift.dictation;

import java.util.Optional;

public interface ForegroundWindow {
    record Info(long handle, String title) {}
    Optional<Info> current();
}
