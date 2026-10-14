package com.drift.dictation;

/** Sends text to the foreground application through an OS input mechanism. */
public interface TextInserter {
    void insert(String text) throws java.io.IOException;
    /** Sends the standard Undo shortcut; only meaningful where the target application supports it. */
    void sendUndo() throws java.io.IOException;
    boolean supported();
}
