package com.drift.applications;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Scores spoken names against application names. */
public final class NameMatcher {
    /** Built-in spoken shortcuts; only applied when the expanded name exists on this machine. */
    private static final Map<String, String> SYNONYMS = Map.of(
            "vs code", "visual studio code", "vscode", "visual studio code", "code", "visual studio code",
            "chrome", "google chrome", "word", "microsoft word", "excel", "microsoft excel",
            "edge", "microsoft edge", "terminal", "windows terminal", "powershell", "windows powershell");

    private NameMatcher() {}

    public record Scored(AppEntry entry, double score) {}

    public static List<Scored> rank(String spoken, List<AppEntry> candidates) {
        String q = spoken.toLowerCase(Locale.ROOT).strip();
        List<String> queries = new ArrayList<>(List.of(q));
        if (SYNONYMS.containsKey(q)) queries.add(SYNONYMS.get(q));
        List<Scored> out = new ArrayList<>();
        for (AppEntry e : candidates) {
            double best = 0;
            for (String query : queries) best = Math.max(best, score(query, e.name().toLowerCase(Locale.ROOT)));
            if (best >= 0.6) out.add(new Scored(e, best));
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    static double score(String q, String name) {
        if (q.isEmpty()) return 0;
        if (q.equals(name)) return 1.0;
        List<String> qt = List.of(q.split("\\s+"));
        List<String> nt = List.of(name.split("[\\s\\-_.]+"));
        if (nt.containsAll(qt)) return 0.9 - Math.min(0.2, 0.02 * (nt.size() - qt.size()));
        if (name.startsWith(q)) return 0.85;
        if (name.contains(q) && q.length() >= 4) return 0.75;
        double sim = 1.0 - (double) levenshtein(q, name) / Math.max(q.length(), name.length());
        return sim >= 0.8 ? sim * 0.95 : 0;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
