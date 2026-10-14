package com.drift.dictation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts spoken punctuation commands in dictated text ("hello comma world period"). */
public final class Punctuation {
    private static final Map<String, String> COMMANDS = new LinkedHashMap<>();
    static {
        COMMANDS.put("new paragraph", "\n\n");
        COMMANDS.put("new line", "\n");
        COMMANDS.put("question mark", "?");
        COMMANDS.put("exclamation mark", "!");
        COMMANDS.put("exclamation point", "!");
        COMMANDS.put("full stop", ".");
        COMMANDS.put("period", ".");
        COMMANDS.put("comma", ",");
        COMMANDS.put("semicolon", ";");
        COMMANDS.put("colon", ":");
    }

    private Punctuation() {}

    public static String apply(String spoken) {
        String t = " " + spoken.strip() + " ";
        for (Map.Entry<String, String> e : COMMANDS.entrySet()) {
            t = Pattern.compile("(?i)\\s+" + Pattern.quote(e.getKey()) + "(?=\\s)").matcher(t).replaceAll(Matcher.quoteReplacement(e.getValue()));
        }
        t = t.strip().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n[ \\t]+", "\n");
        return capitalizeSentences(t);
    }

    private static String capitalizeSentences(String t) {
        StringBuilder sb = new StringBuilder(t.length());
        boolean cap = true;
        for (char c : t.toCharArray()) {
            if (cap && Character.isLetter(c)) { sb.append(Character.toUpperCase(c)); cap = false; }
            else {
                sb.append(c);
                if (c == '.' || c == '?' || c == '!' || c == '\n') cap = true;
                else if (!Character.isWhitespace(c)) cap = false;
            }
        }
        return sb.toString();
    }
}
