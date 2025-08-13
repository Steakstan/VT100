package org.msv.vt100.ansiisequences;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-line attributes such as double-width and double-height (top/bottom),
 * as used by DEC private sequences (e.g., DECDWL/DECDHL).
 * Notes:
 * - This class stores semantic attributes only; it does not enforce visual resizing
 *   by itself. The renderer (or CursorController) should interpret these attributes
 *   to adjust layout, cursor movement, and glyph rendering.
 * - Double-height lines come in pairs (top/bottom). Enforcing pairing is a caller's
 *   responsibility; here we only store flags.
 */
public class LineAttributeHandler {

    /** Attributes applicable to a line. */
    public enum LineAttr {
        DOUBLE_WIDTH,
        DOUBLE_HEIGHT_TOP,
        DOUBLE_HEIGHT_BOTTOM
    }

    // Map: rowIndex (0-based) -> attribute set
    private final Map<Integer, EnumSet<LineAttr>> lineAttrs = new HashMap<>();

    /**
     * Enables or disables double-width for the given row.
     * Double-width is mutually exclusive with double-height; enabling will clear DH flags.
     */
    public void setDoubleWidthLine(int row, boolean enabled) {
        EnumSet<LineAttr> set = ensure(row);
        if (enabled) {
            set.remove(LineAttr.DOUBLE_HEIGHT_TOP);
            set.remove(LineAttr.DOUBLE_HEIGHT_BOTTOM);
            set.add(LineAttr.DOUBLE_WIDTH);
        } else {
            set.remove(LineAttr.DOUBLE_WIDTH);
            cleanupIfEmpty(row, set);
        }
    }

    /**
     * Clears all line attributes (full reset).
     */
    public void resetAllLineAttributes() {
        lineAttrs.clear();
    }

    /**
     * Returns a canonical style string for the row to aid the renderer.
     * We avoid hard-coding font sizes or colors; instead we expose semantic flags.
     * Unknown keys are preserved by StyleUtils and can be interpreted downstream.
     * Example keys:
     * - "line-double-width: true"
     * - "line-double-height: top|bottom|none"
     */
    public String getLineStyle(int row) {
        EnumSet<LineAttr> set = lineAttrs.get(row);
        if (set == null || set.isEmpty()) {
            return "line-double-width: false; line-double-height: none;";
        }
        boolean dw = set.contains(LineAttr.DOUBLE_WIDTH);
        boolean dht = set.contains(LineAttr.DOUBLE_HEIGHT_TOP);
        boolean dhb = set.contains(LineAttr.DOUBLE_HEIGHT_BOTTOM);

        StringBuilder sb = new StringBuilder();
        sb.append("line-double-width: ").append(dw ? "true" : "false").append("; ");

        if (dht) {
            sb.append("line-double-height: top; ");
        } else if (dhb) {
            sb.append("line-double-height: bottom; ");
        } else {
            sb.append("line-double-height: none; ");
        }

        return sb.toString().trim();
    }

    // ---- internals ----

    private EnumSet<LineAttr> ensure(int row) {
        EnumSet<LineAttr> set = lineAttrs.get(row);
        if (set == null) {
            set = EnumSet.noneOf(LineAttr.class);
            lineAttrs.put(row, set);
        }
        return set;
    }

    private void cleanupIfEmpty(int row, EnumSet<LineAttr> set) {
        if (set.isEmpty()) {
            lineAttrs.remove(row);
        }
    }
}
