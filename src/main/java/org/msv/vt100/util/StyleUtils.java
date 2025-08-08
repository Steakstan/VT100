package org.msv.vt100.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utilities to parse and build canonical style strings used by the renderer.
 *
 * Canonical keys we care about:
 *  - fill
 *  - background
 *  - underline
 *  - font-weight
 *
 * Parsing:
 *  - Accepts multiple vendor/alias keys and normalizes them to canonical ones.
 *  - Unknown keys are preserved as-is (after trim) to allow extensibility (e.g., "blink", "line-double-width").
 *  - Robust against extra semicolons, whitespace, and entries without a colon.
 *
 * Building:
 *  - Produces a deterministic order: fill; background; underline; font-weight; then all other keys
 *    in insertion order.
 *  - Emits "key: value; " segments; trailing space is trimmed by caller expectations.
 */
public class StyleUtils {

    /** Deterministic order for canonical keys. */
    private static final String[] CANONICAL_ORDER = new String[] {
            "fill", "background", "underline", "font-weight"
    };

    /**
     * Parses a style string into a canonicalized map (LinkedHashMap to preserve insertion order).
     * Returns an empty map for null/blank input.
     */
    public static Map<String, String> parseStyleString(String style) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (style == null || style.isBlank()) {
            return map;
        }

        // Split by ';' but ignore empty fragments
        String[] entries = style.split(";");
        for (String rawEntry : entries) {
            if (rawEntry == null) continue;
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;

            int sep = entry.indexOf(':');
            if (sep < 0) {
                // No key:value pair; ignore silently
                continue;
            }

            String rawKey = entry.substring(0, sep).trim();
            String rawVal = entry.substring(sep + 1).trim();
            if (rawKey.isEmpty()) continue;

            String key = normalizeKey(rawKey);
            String value = normalizeValue(key, rawVal);

            // Last write wins for duplicate keys (common SGR merges)
            map.put(key, value);
        }

        return map;
    }

    /**
     * Builds a canonical style string from a map. Deterministic key order:
     * canonical keys first, then all others in insertion order.
     */
    public static String buildStyleString(Map<String, String> styleMap) {
        if (styleMap == null || styleMap.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // Emit canonical keys in fixed order if present
        for (String k : CANONICAL_ORDER) {
            if (styleMap.containsKey(k)) {
                append(sb, k, styleMap.get(k));
            }
        }

        // Emit remaining keys in insertion order
        if (styleMap instanceof LinkedHashMap) {
            for (Map.Entry<String, String> e : styleMap.entrySet()) {
                String k = e.getKey();
                if (!isCanonical(k)) {
                    append(sb, k, e.getValue());
                }
            }
        } else {
            // Fallback for non-linked maps: still append the rest, order unspecified
            for (Map.Entry<String, String> e : styleMap.entrySet()) {
                String k = e.getKey();
                if (!isCanonical(k)) {
                    append(sb, k, e.getValue());
                }
            }
        }

        // Trim trailing space
        return sb.toString().trim();
    }

    /**
     * Returns default canonical style used by the renderer.
     */
    public static String getDefaultStyle() {
        // Use LinkedHashMap to ensure deterministic build order
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("fill", "white");
        map.put("background", "transparent");
        map.put("underline", "false");
        map.put("font-weight", "normal");
        return buildStyleString(map);
    }

    // ---- Normalization helpers ----

    private static boolean isCanonical(String key) {
        for (String s : CANONICAL_ORDER) {
            if (s.equals(key)) return true;
        }
        return false;
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (key == null || key.isBlank()) return;
        if (value == null || value.isBlank()) return;
        sb.append(key).append(": ").append(value).append("; ");
    }

    /**
     * Normalizes a raw key to canonical form.
     */
    private static String normalizeKey(String rawKey) {
        String key = rawKey.toLowerCase();

        switch (key) {
            // foreground color
            case "-fx-fill":
            case "text-fill":
            case "color":
            case "foreground":
            case "fg":
                return "fill";

            // background color
            case "-rtfx-background-color":
            case "-fx-background-color":
            case "background-color":
            case "bg":
                return "background";

            // underline flag
            case "-fx-underline":
            case "underline":
                return "underline";

            // font weight
            case "-fx-font-weight":
            case "weight":
            case "fontweight":
            case "font-weight":
                return "font-weight";

            default:
                // Preserve unknown keys as-is (already lower-cased)
                return key;
        }
    }

    /**
     * Normalizes a value based on the canonical key semantics.
     */
    private static String normalizeValue(String key, String rawVal) {
        String value = Objects.toString(rawVal, "").trim();
        if (value.isEmpty()) return value;

        switch (key) {
            case "font-weight":
                return normalizeFontWeight(value);

            case "underline":
                return normalizeBoolean(value) ? "true" : "false";

            default:
                // Pass-through for colors and unknown keys
                return value;
        }
    }

    /**
     * Converts various font-weight notations to "bold" or "normal".
     */
    private static String normalizeFontWeight(String v) {
        String s = v.toLowerCase();

        // Numeric CSS weights
        try {
            int w = Integer.parseInt(s);
            return (w >= 600) ? "bold" : "normal";
        } catch (NumberFormatException ignored) {
            // fallthrough
        }

        // Textual weights
        switch (s) {
            case "bold":
            case "bolder":
            case "semi-bold":
            case "semibold":
            case "demibold":
            case "extra-bold":
            case "extrabold":
            case "heavy":
            case "black":
                return "bold";
            case "normal":
            case "regular":
            case "book":
            default:
                return "normal";
        }
    }

    /**
     * Parses common boolean-like strings.
     */
    private static boolean normalizeBoolean(String v) {
        String s = v.toLowerCase();
        return s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("on");
    }
}
