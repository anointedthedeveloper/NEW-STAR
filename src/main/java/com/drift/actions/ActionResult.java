package com.drift.actions;

import java.util.Map;

public record ActionResult(boolean success, String message, Map<String, Object> data) {
    public static ActionResult ok(String message) { return new ActionResult(true, message, Map.of()); }
    public static ActionResult ok(String message, Map<String, Object> data) { return new ActionResult(true, message, data); }
    public static ActionResult failure(String message) { return new ActionResult(false, message, Map.of()); }
}
