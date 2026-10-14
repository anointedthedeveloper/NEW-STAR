package com.drift.intents;

/** What the user wants, before it is resolved to a concrete, validated action. */
public sealed interface Intent {
    record OpenApp(String name) implements Intent {}
    record OpenFolder(String name) implements Intent {}
    record OpenFile(String name) implements Intent {}
    record FindFiles(String query) implements Intent {}
    record FindProjects() implements Intent {}
    record OpenRecentProject(String when) implements Intent {}
    record OpenProject(String name) implements Intent {}
    record OpenTerminal() implements Intent {}
    record RunDevTask(String task) implements Intent {}
    /** The user asked to run an arbitrary command; recognised so that we can refuse it honestly. */
    record RunRawCommand(String command) implements Intent {}
    record StartDictation() implements Intent {}
    record StopDictation() implements Intent {}
    record TypeText(String text) implements Intent {}
    record MediaQuery() implements Intent {}
    record MediaControl(String action) implements Intent {}
    record SetTheme(String name) implements Intent {}
    record AdjustTransparency(String direction) implements Intent {}
    record ShowWidget(String type) implements Intent {}
    record Remember(String phrase, String target) implements Intent {}
    record StopListening() implements Intent {}
    record Unknown(String text) implements Intent {}
}
