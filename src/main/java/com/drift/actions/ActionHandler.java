package com.drift.actions;

import com.drift.dev.ProcessRunner;
import java.util.function.Consumer;

@FunctionalInterface
public interface ActionHandler {
    record Context(ProcessRunner.Handle cancel, Consumer<String> progress) {}

    ActionResult execute(ActionRequest request, Context context) throws Exception;
}
