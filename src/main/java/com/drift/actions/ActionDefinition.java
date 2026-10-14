package com.drift.actions;

import java.time.Duration;
import java.util.List;

/**
 * Declaration of an approved action. Anything not registered here cannot be executed.
 * @param resultShape documentation of the keys placed in {@link ActionResult#data()}
 * Error behaviour is uniform: an exception or timeout becomes a failed result, is recorded in history and is reported to the user.
 */
public record ActionDefinition(String id, String description, List<ParamSpec> params, Permission permission,
                               Confirmation confirmation, Duration timeout, String resultShape, ActionHandler handler) {
    public enum Confirmation { NEVER, ALWAYS }

    public record ParamSpec(String name, boolean required, int maxLength, String description) {}

    public static ParamSpec required(String name, int maxLength, String description) { return new ParamSpec(name, true, maxLength, description); }
    public static ParamSpec optional(String name, int maxLength, String description) { return new ParamSpec(name, false, maxLength, description); }
}
