package com.drift.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.drift.core.DriftPaths;
import com.drift.core.Json;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** Offline recognition with Vosk (Apache-2.0). Native library ships inside the vosk jar; models are downloaded by the user. */
@Component
public class VoskSpeechProvider implements SpeechProvider {
    public static final float SAMPLE_RATE = 16000f;
    private final ModelLocator locator;
    private final ConcurrentMap<String, org.vosk.Model> models = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public VoskSpeechProvider(DriftPaths paths) { this.locator = new ModelLocator(paths.models()); }

    public VoskSpeechProvider(ModelLocator locator) { this.locator = locator; }

    public ModelLocator locator() { return locator; }

    @Override public String id() { return "vosk"; }
    @Override public String displayName() { return "Vosk (offline)"; }

    @Override
    public Status status(String modelPath, String language) {
        try {
            Class.forName("org.vosk.LibVosk");
        } catch (ClassNotFoundException | LinkageError e) {
            return new Status(State.LIBRARY_UNAVAILABLE, "The Vosk native library could not be loaded: " + e.getClass().getSimpleName() + ". See docs/SPEECH_SETUP.md.");
        }
        Optional<Path> model = locator.select(modelPath, language);
        if (model.isEmpty()) {
            String where = locator.modelsDir().toString();
            return new Status(State.MODEL_MISSING, "No Vosk model found for " + language + ". Download one from https://alphacephei.com/vosk/models and unzip it into " + where
                    + " (or choose a model folder in Settings > Voice recognition).");
        }
        return new Status(State.READY, "Model: " + model.get().getFileName());
    }

    @Override
    public Session open(String modelPath, String language) throws SpeechException {
        Status st = status(modelPath, language);
        if (!st.ready()) throw new SpeechException(st.detail());
        Path path = locator.select(modelPath, language).orElseThrow();
        try {
            org.vosk.Model model = models.computeIfAbsent(path.toString(), k -> {
                try { return new org.vosk.Model(k); } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            });
            org.vosk.Recognizer rec = new org.vosk.Recognizer(model, SAMPLE_RATE);
            rec.setWords(true);
            return new VoskSession(rec);
        } catch (RuntimeException | LinkageError | java.io.IOException e) {
            throw new SpeechException("Could not load the speech model at " + path + ": " + e.getMessage(), e);
        }
    }

    private static final class VoskSession implements Session {
        private final org.vosk.Recognizer rec;
        private final StringBuilder finals = new StringBuilder();
        private final java.util.List<Double> confidences = new java.util.ArrayList<>();
        private boolean closed;

        VoskSession(org.vosk.Recognizer rec) { this.rec = rec; }

        @Override public synchronized String accept(byte[] pcm, int length) {
            if (closed) return null;
            if (rec.acceptWaveForm(pcm, length)) {
                collect(rec.getResult());
                return finals.toString().strip();
            }
            String partial = Json.read(rec.getPartialResult(), JsonNode.class).path("partial").asText("");
            return (finals + " " + partial).strip();
        }

        @Override public synchronized Transcript finish() {
            if (closed) return new Transcript("", Transcript.UNKNOWN_CONFIDENCE);
            collect(rec.getFinalResult());
            double conf = confidences.isEmpty() ? Transcript.UNKNOWN_CONFIDENCE : confidences.stream().mapToDouble(Double::doubleValue).average().orElse(-1);
            return new Transcript(finals.toString().strip(), conf);
        }

        private void collect(String json) {
            JsonNode n = Json.read(json, JsonNode.class);
            String text = n.path("text").asText("").strip();
            if (!text.isEmpty()) {
                if (finals.length() > 0) finals.append(' ');
                finals.append(text);
            }
            for (JsonNode w : n.path("result")) if (w.has("conf")) confidences.add(w.get("conf").asDouble());
        }

        @Override public synchronized void close() {
            if (!closed) { closed = true; rec.close(); }
        }
    }
}
