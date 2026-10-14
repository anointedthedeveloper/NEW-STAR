package com.drift.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Minimal in-process synchronous pub/sub. Subscribers must hop threads themselves (the UI does). */
@Component
public class DriftEventBus {
    private static final Logger log = LoggerFactory.getLogger(DriftEventBus.class);
    private final List<Consumer<DriftEvent>> subscribers = new CopyOnWriteArrayList<>();

    public Runnable subscribe(Consumer<DriftEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public void publish(DriftEvent event) {
        for (Consumer<DriftEvent> s : subscribers) {
            try {
                s.accept(event);
            } catch (RuntimeException e) {
                log.warn("Event subscriber failed for {}", event.getClass().getSimpleName(), e);
            }
        }
    }
}
