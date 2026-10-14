package com.drift.history;

import java.util.regex.Pattern;

/** Removes obviously sensitive values from text before it reaches logs or history. */
public final class Redactor {
    private static final Pattern KEY_VALUE = Pattern.compile("(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|authorization)\\b(\\s*[:=]\\s*|\\s+is\\s+)(\\S+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern LONG_TOKEN = Pattern.compile("\\b[A-Za-z0-9_\\-]{32,}\\b");
    private static final Pattern USER_PATH = Pattern.compile("(?i)([A-Z]:\\\\Users\\\\)[^\\\\\\s]+");

    private Redactor() {}

    public static String redact(String s) {
        if (s == null) return null;
        String r = KEY_VALUE.matcher(s).replaceAll("$1$2[redacted]");
        r = BEARER.matcher(r).replaceAll("Bearer [redacted]");
        r = LONG_TOKEN.matcher(r).replaceAll("[redacted]");
        r = USER_PATH.matcher(r).replaceAll("$1<user>");
        return r;
    }
}
