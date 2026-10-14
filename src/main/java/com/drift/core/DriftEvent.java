package com.drift.core;

import java.util.List;

/** Events that drive the interface. Everything the UI shows is derived from one of these. */
public sealed interface DriftEvent {
    record ListeningStarted(String mode) implements DriftEvent {}
    record ListeningStopped() implements DriftEvent {}
    record AudioLevel(double level) implements DriftEvent {}
    record PartialTranscript(String text) implements DriftEvent {}
    record FinalTranscript(String text, double confidence) implements DriftEvent {}
    record CommandPlanned(String utterance, String plan) implements DriftEvent {}
    record ExecutionStarted(String actionId, String description) implements DriftEvent {}
    record ExecutionFinished(String actionId, boolean success, String message) implements DriftEvent {}
    record ConfirmationRequested(String pendingId, String prompt) implements DriftEvent {}
    record ChoiceRequested(String pendingId, String prompt, List<String> options) implements DriftEvent {}
    record MediaChanged(String title, String artist, String status) implements DriftEvent {}
    record Notification(String title, String message) implements DriftEvent {}
    record Failure(String title, String detail, String recovery) implements DriftEvent {}
    record AssistantOpened() implements DriftEvent {}
    record SettingsChanged() implements DriftEvent {}
    record LayoutChanged() implements DriftEvent {}
    record Reply(String message, boolean ok) implements DriftEvent {}
    record DictationChanged(String state, String target, String text) implements DriftEvent {}
}
