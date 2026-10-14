package com.drift.actions;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.core.Json;
import com.drift.dev.ProcessRunner;
import com.drift.history.OperationHistory;
import com.drift.intents.Intent;
import com.drift.intents.IntentParser;
import com.drift.intents.Normalizer;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.learning.LearningService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recognition is upstream; this class runs the rest of the pipeline:
 * normalise -> interpret -> resolve -> validate -> safety check -> confirm -> execute -> record -> respond.
 * Blocking: call from a background thread, never from the JavaFX thread.
 */
@Service
public class CommandOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(CommandOrchestrator.class);
    private static final Duration PENDING_TTL = Duration.ofMinutes(2);

    private record Pending(long expiresAtNanos, Supplier<CommandOutcome> onConfirm, List<IntentResolver.Option> options,
                           String rememberPhrase, String utterance) {}

    private final IntentParser parser;
    private final IntentResolver resolver;
    private final ActionRegistry registry;
    private final SafetyPolicy safety;
    private final OperationHistory history;
    private final LearningService learning;
    private final DriftEventBus bus;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private volatile ProcessRunner.Handle currentHandle;

    public CommandOrchestrator(IntentParser parser, IntentResolver resolver, ActionRegistry registry, SafetyPolicy safety,
                               OperationHistory history, LearningService learning, DriftEventBus bus) {
        this.parser = parser;
        this.resolver = resolver;
        this.registry = registry;
        this.safety = safety;
        this.history = history;
        this.learning = learning;
        this.bus = bus;
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    public CommandOutcome process(String utterance) {
        String normalized = Normalizer.normalize(utterance);
        if (normalized.isEmpty()) return new CommandOutcome.Unsupported("I didn't catch anything to act on.");
        Intent intent = parser.parse(utterance);
        IntentResolver.Resolution resolution;
        if (intent instanceof Intent.Unknown) {
            var learned = learning.lookup(normalized);
            if (learned.isPresent()) {
                @SuppressWarnings("unchecked") Map<String, String> params = Json.read(learned.get().paramsJson(), Map.class);
                resolution = new IntentResolver.Resolution.Ready(new ActionRequest(learned.get().actionId(), params, utterance,
                        Source.LEARNED_COMMAND, null, "Learned command: " + learned.get().actionId()));
            } else {
                resolution = resolver.resolve(intent, utterance);
            }
        } else {
            resolution = resolver.resolve(intent, utterance);
        }
        return switch (resolution) {
            case IntentResolver.Resolution.Unsupported u -> {
                history.record(utterance, "none", "{}", "UNSUPPORTED", u.reason(), 0);
                yield new CommandOutcome.Unsupported(u.reason());
            }
            case IntentResolver.Resolution.Ready r -> handle(r.request(), normalized);
            case IntentResolver.Resolution.Choice c -> {
                String id = newId();
                pending.put(id, new Pending(expiry(), null, c.options(), c.rememberPhrase(), normalized));
                List<String> labels = c.options().stream().map(IntentResolver.Option::label).toList();
                bus.publish(new DriftEvent.ChoiceRequested(id, c.prompt(), labels));
                yield new CommandOutcome.NeedsChoice(id, c.prompt(), labels, c.rememberPhrase() != null);
            }
        };
    }

    /** Runs steps 6-11 for a typed request (also used directly by UI-originated requests). */
    public CommandOutcome handle(ActionRequest request, String normalizedUtterance) {
        ActionDefinition def = registry.find(request.actionId()).orElse(null);
        if (def == null) return new CommandOutcome.Unsupported("Drift has no handler for \"" + request.actionId() + "\".");
        List<String> errors = registry.validate(request);
        if (!errors.isEmpty()) {
            String msg = "That request is not valid: " + String.join("; ", errors);
            history.record(request.utterance(), request.actionId(), safeParams(request), "REFUSED", msg, 0);
            return new CommandOutcome.Refused(msg);
        }
        bus.publish(new DriftEvent.CommandPlanned(request.utterance(), request.summary()));
        SafetyPolicy.Decision decision = safety.decide(def, request);
        return switch (decision) {
            case SafetyPolicy.Decision.Deny d -> {
                history.record(request.utterance(), request.actionId(), safeParams(request), "REFUSED", d.reason(), 0);
                yield new CommandOutcome.Refused(d.reason());
            }
            case SafetyPolicy.Decision.Confirm c -> {
                String id = newId();
                pending.put(id, new Pending(expiry(), () -> execute(def, request, normalizedUtterance), List.of(), null, normalizedUtterance));
                bus.publish(new DriftEvent.ConfirmationRequested(id, c.prompt()));
                yield new CommandOutcome.NeedsConfirmation(id, c.prompt());
            }
            case SafetyPolicy.Decision.Allow a -> execute(def, request, normalizedUtterance);
        };
    }

    public CommandOutcome confirm(String pendingId, boolean approve) {
        Pending p = take(pendingId);
        if (p == null || p.onConfirm() == null) return new CommandOutcome.Refused("That confirmation has expired or does not exist.");
        if (!approve) {
            history.record(p.utterance(), "confirmation", "{}", "CANCELLED", "User declined", 0);
            return new CommandOutcome.Refused("Cancelled. Nothing was changed.");
        }
        return p.onConfirm().get();
    }

    /** Resolves a NeedsChoice. Optionally stores an application alias for the original phrase. */
    public CommandOutcome choose(String pendingId, int index, boolean remember) {
        Pending p = take(pendingId);
        if (p == null || p.options().isEmpty()) return new CommandOutcome.Refused("That question has expired.");
        if (index < 0 || index >= p.options().size()) return new CommandOutcome.Refused("That is not one of the options.");
        IntentResolver.Option opt = p.options().get(index);
        if (remember && p.rememberPhrase() != null && opt.rememberTarget() != null) {
            learning.remember(AliasKind.APP, p.rememberPhrase(), opt.rememberTarget());
        }
        return handle(opt.request(), p.utterance());
    }

    public void cancelRunning() {
        ProcessRunner.Handle h = currentHandle;
        if (h != null) h.cancel();
    }

    private CommandOutcome execute(ActionDefinition def, ActionRequest request, String normalizedUtterance) {
        long start = System.nanoTime();
        bus.publish(new DriftEvent.ExecutionStarted(def.id(), request.summary()));
        ProcessRunner.Handle handle = new ProcessRunner.Handle();
        currentHandle = handle;
        Future<ActionResult> future = executor.submit(() -> def.handler().execute(request,
                new ActionHandler.Context(handle, line -> bus.publish(new DriftEvent.Notification(def.id(), line)))));
        ActionResult result;
        String status;
        try {
            result = future.get(def.timeout().toMillis(), TimeUnit.MILLISECONDS);
            status = result.success() ? "OK" : "FAILED";
        } catch (TimeoutException e) {
            handle.cancel();
            future.cancel(true);
            result = ActionResult.failure("Timed out after " + def.timeout().toSeconds() + " seconds.");
            status = "TIMEOUT";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Action {} failed", def.id(), cause);
            result = ActionResult.failure(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
            status = "FAILED";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.cancel();
            result = ActionResult.failure("Interrupted.");
            status = "FAILED";
        } finally {
            currentHandle = null;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        history.record(request.utterance(), def.id(), safeParams(request), status, result.message(), ms);
        bus.publish(new DriftEvent.ExecutionFinished(def.id(), result.success(), result.message()));
        if (result.success() && request.source() != Source.PARSED && request.source() != Source.UI) {
            learning.recordSuccess(normalizedUtterance, def.id(), request.params());
        }
        return result.success() ? new CommandOutcome.Done(result) : new CommandOutcome.Failed(result.message());
    }

    /** Parameters stored in history. Free text typed into other applications is never stored, only its length. */
    static String safeParams(ActionRequest r) {
        Map<String, String> p = new LinkedHashMap<>(r.params());
        if (p.containsKey("text")) p.put("text", "<" + p.get("text").length() + " chars>");
        return Json.write(p);
    }

    private Pending take(String id) {
        long now = System.nanoTime();
        pending.values().removeIf(p -> p.expiresAtNanos() < now);
        return pending.remove(id);
    }

    private static long expiry() { return System.nanoTime() + PENDING_TTL.toNanos(); }

    private static String newId() { return UUID.randomUUID().toString().substring(0, 8); }
}
