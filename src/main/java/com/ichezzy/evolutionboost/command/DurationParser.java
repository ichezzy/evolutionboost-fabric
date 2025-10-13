package com.ichezzy.evolutionboost.command;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private DurationParser() {}

    // Alte Single-String-Variante behalten wir (z.B. falls du sie woanders nutzt)
    private static final Pattern SINGLE =
            Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(d|day|days|h|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)\\s*$",
                    Pattern.CASE_INSENSITIVE);

    public static long parse(String raw) {
        if (raw == null) return 1000L;
        final String s = raw.trim().toLowerCase(Locale.ROOT);
        final Matcher m = SINGLE.matcher(s);
        if (!m.matches()) {
            try {
                double seconds = Double.parseDouble(s);
                return Math.max(1000L, (long) Math.ceil(seconds * 1000.0));
            } catch (NumberFormatException ignored) {
                return 1000L;
            }
        }
        double num = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        return fromValueUnit(num, unit);
    }

    /** Neue API: Dauer aus <value> + <unit> (unit: s|m|h|d bzw. Wörter). */
    public static long fromValueUnit(double value, String unitRaw) {
        if (unitRaw == null) return Math.max(1000L, (long) Math.ceil(value * 1000.0));
        String unit = unitRaw.trim().toLowerCase(Locale.ROOT);
        long ms = switch (unit.charAt(0)) {
            case 'd' -> (long) Math.ceil(value * 86_400_000.0);
            case 'h' -> (long) Math.ceil(value * 3_600_000.0);
            case 'm' -> (long) Math.ceil(value * 60_000.0);
            case 's' -> (long) Math.ceil(value * 1_000.0);
            default  -> (long) Math.ceil(value * 1_000.0);
        };
        return Math.max(1000L, ms);
    }

    public static String pretty(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86_400; s %= 86_400;
        long h = s / 3600;   s %= 3600;
        long m = s / 60;     s %= 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
