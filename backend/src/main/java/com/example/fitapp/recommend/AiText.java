package com.example.fitapp.recommend;

/** Cleaning for text that came from the AI model before it is stored or shown. */
final class AiText {

    private AiText() {}

    /** Plain text only: no markup, no links, no control characters, bounded length. */
    static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String t =
                text.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                        .replaceAll("[<>]", "")
                        .replaceAll("\\p{Cntrl}", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }
}
