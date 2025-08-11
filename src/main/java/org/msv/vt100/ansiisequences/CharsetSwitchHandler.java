package org.msv.vt100.ansiisequences;

import java.util.Map;

/**
 * Handles DEC charset designation and runtime switching between ASCII and DEC Special Graphics.
 * Model:
 * - We model two banks: G0 and G1. Each bank can be designated to a charset.
 * - The "active bank" (GL) is either G0 (after SI) or G1 (after SO).
 * - For compatibility with legacy code, switchToASCIICharset()/switchToGraphicsCharset()
 *   designate G0 and also make G0 active.

 * Supported charsets:
 * - ASCII
 * - DEC_SPECIAL_GRAPHICS (DEC "Special Graphics" set, commonly used for box drawing)
 * Mapping rules:
 * - Control characters and spaces pass through unchanged.
 * - For DEC Special Graphics, we map known ASCII letters to Unicode box-drawing glyphs.
 */
public class CharsetSwitchHandler {

    /** Supported charsets. */
    public enum CharsetMode {
        ASCII,
        DEC_SPECIAL_GRAPHICS
    }

    /** Charset banks. */
    private enum Bank {
        G0, G1
    }

    // Current designation of banks (defaults to ASCII).
    private CharsetMode g0 = CharsetMode.ASCII;
    private final CharsetMode g1 = CharsetMode.ASCII;

    // Active bank (GL). VT default is G0 (after SI).
    private Bank activeBank = Bank.G0;

    // Immutable mapping for DEC Special Graphics.
    private static final Map<Character, Character> SG_MAP;
    static {
        // Common DEC Special Graphics mappings (subset widely used by ncurses):

        // You can extend this table if your workloads rely on more DEC graphics.
        // vertical line
        SG_MAP = Map.ofEntries(Map.entry('j', '┘'), // lower-right corner
                Map.entry('k', '┐'), // upper-right corner
                Map.entry('l', '┌'), // upper-left corner
                Map.entry('m', '└'), // lower-left corner
                Map.entry('n', '┼'), // crossing lines
                Map.entry('q', '─'), // horizontal line
                Map.entry('t', '├'), // left tee
                Map.entry('u', '┤'), // right tee
                Map.entry('v', '┴'), // bottom tee
                Map.entry('w', '┬'), // top tee
                Map.entry('x', '│'));
    }

    /* ===================== Public API ===================== */

    /** Designate G0 to ASCII (ESC ( B / here simplified as ESC ( K in upstream). */
    public void switchToASCIICharset() {
        designateG0(CharsetMode.ASCII);
        // Keep legacy behavior: make G0 active as well.
        shiftIn();
    }

    /** Designate G0 to DEC Special Graphics (ESC ( 0). */
    public void switchToGraphicsCharset() {
        designateG0(CharsetMode.DEC_SPECIAL_GRAPHICS);
        // Keep legacy behavior: make G0 active as well.
        shiftIn();
    }

    /** Designate G0 to the provided mode. */
    public void designateG0(CharsetMode mode) {
        g0 = (mode == null) ? CharsetMode.ASCII : mode;
    }


    /** SI (Shift In): make G0 active (GL). */
    public void shiftIn() {
        activeBank = Bank.G0;
    }


    /**
     * Transforms a text chunk according to the active bank's charset.
     * Control characters and spaces are preserved.
     */
    public String processText(String text) {
        if (text == null || text.isEmpty()) return text;
        if (getActiveCharset() != CharsetMode.DEC_SPECIAL_GRAPHICS) {
            return text;
        }
        return mapToDecSpecialGraphics(text);
    }

    /* ===================== Internals ===================== */

    private CharsetMode getActiveCharset() {
        return (activeBank == Bank.G0) ? g0 : g1;
    }

    private String mapToDecSpecialGraphics(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Preserve control chars and spaces
            if (c == ' ' || Character.isISOControl(c)) {
                out.append(c);
                continue;
            }

            // Map known symbols; pass-through for unknowns
            out.append(SG_MAP.getOrDefault(c, c));
        }
        return out.toString();
    }
}
