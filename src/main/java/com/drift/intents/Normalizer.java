package com.drift.intents;

import java.util.Locale;
import java.util.regex.Pattern;

/** Turns raw recognised text into a canonical lower-case form. Does not interpret meaning. */
public final class Normalizer {
    private static final Pattern LEADING_FILLER = Pattern.compile(
            "^(?:(?:hey|ok|okay)\\s+)?(?:drift\\s+)?(?:(?:please|could you|can you|would you|will you|i want you to|i would like you to|i'd like you to)\\s+)*");
    private static final Pattern TRAILING_FILLER = Pattern.compile("\\s+(?:please|for me|thanks|thank you)$");
    private static final Pattern PUNCT = Pattern.compile("[^\\p{L}\\p{N}\\s'\\-.]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Normalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String t = raw.toLowerCase(Locale.ROOT).strip();
        t = PUNCT.matcher(t).replaceAll(" ");
        t = SPACES.matcher(t).replaceAll(" ").strip();
        t = t.replaceAll("[.]+$", "").strip();
        t = LEADING_FILLER.matcher(t).replaceFirst("");
        t = TRAILING_FILLER.matcher(t).replaceFirst("");
        return SPACES.matcher(t).replaceAll(" ").strip();
    }

    /** Strips leading articles/possessives: "my downloads" -> "downloads". */
    public static String stripDeterminer(String s) {
        return s.replaceFirst("^(?:my|the|a|an|our)\\s+", "").strip();
    }
}
