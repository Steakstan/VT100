package org.msv.vt100.ansiisequences;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-line attributes such as double-width and double-height (top/bottom),
 * as used by DEC private sequences (e.g., DECDWL/DECDHL).
 *
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
     * Marks the given row as double-height TOP half (DECDHL top).
     * Mutually exclusive with double-width; enabling will clear DW.
     */
    public void setDoubleHeightTop(int row, boolean enabled) {
        EnumSet<LineAttr> set = ensure(row);
        if (enabled) {
            set.remove(LineAttr.DOUBLE_WIDTH);
            set.remove(LineAttr.DOUBLE_HEIGHT_BOTTOM);
            set.add(LineAttr.DOUBLE_HEIGHT_TOP);
        } else {
            set.remove(LineAttr.DOUBLE_HEIGHT_TOP);
            cleanupIfEmpty(row, set);
        }
    }

    /**
     * Marks the given row as double-height BOTTOM half (DECDHL bottom).
     * Mutually exclusive with double-width; enabling will clear DW.
     */
    public void setDoubleHeightBottom(int row, boolean enabled) {
        EnumSet<LineAttr> set = ensure(row);
        if (enabled) {
            set.remove(LineAttr.DOUBLE_WIDTH);
            set.remove(LineAttr.DOUBLE_HEIGHT_TOP);
            set.add(LineAttr.DOUBLE_HEIGHT_BOTTOM);
        } else {
            set.remove(LineAttr.DOUBLE_HEIGHT_BOTTOM);
            cleanupIfEmpty(row, set);
        }
    }

    /**
     * Clears all attributes for a specific row.
     */
    public void resetLineAttributes(int row) {
        lineAttrs.remove(row);
    }

    /**
     * Clears all line attributes (full reset).
     */
    public void resetAllLineAttributes() {
        lineAttrs.clear();
    }

    /**
     * Returns true if the row is flagged as double-width.
     */
    public boolean isDoubleWidthLine(int row) {
        EnumSet<LineAttr> set = lineAttrs.get(row);
        return set != null && set.contains(LineAttr.DOUBLE_WIDTH);
    }

    /**
     * Returns true if the row is flagged as double-height top.
     */
    public boolean isDoubleHeightTop(int row) {
        EnumSet<LineAttr> set = lineAttrs.get(row);
        return set != null && set.contains(LineAttr.DOUBLE_HEIGHT_TOP);
    }

    /**
     * Returns true if the row is flagged as double-height bottom.
     */
    public boolean isDoubleHeightBottom(int row) {
        EnumSet<LineAttr> set = lineAttrs.get(row);
        return set != null && set.contains(LineAttr.DOUBLE_HEIGHT_BOTTOM);
    }

    /**
     * Returns an immutable view of attributes for the row (empty if none).
     */
    public EnumSet<LineAttr> getAttributes(int row) {
        EnumSet<LineAttr> set = lineAttrs.get(row);
        return set == null ? EnumSet.noneOf(LineAttr.class) : EnumSet.copyOf(set);
    }

    /**
     * Returns a canonical style string for the row to aid the renderer.
     * We avoid hard-coding font sizes or colors; instead we expose semantic flags.
     * Unknown keys are preserved by StyleUtils and can be interpreted downstream.
     *
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
