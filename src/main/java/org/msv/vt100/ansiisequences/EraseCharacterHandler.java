package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.Cell;
import org.msv.vt100.util.StyleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles ECH (Erase Character): CSI Pn X
 *
 * Semantics:
 * - Replaces Pn characters starting at the cursor position with spaces.
 * - Does NOT move the cursor.
 * - Honors DECVLRM horizontally: erasure is restricted to [leftMargin..rightMargin] when enabled.
 * - If Pn extends beyond the effective right boundary, it is clamped.
 * - Default Pn is 1 when omitted or non-positive.
 *
 * We erase using current background (effective), not default transparent style.
 */
public class EraseCharacterHandler {

    private static final Logger logger = LoggerFactory.getLogger(EraseCharacterHandler.class);

    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;

    // NEW
    private TextFormater textFormater;

    // CSI Pn X (Pn optional)
    private static final Pattern CSI_ECH = Pattern.compile("^\\u001B?\\[(\\d*)X$");

    public EraseCharacterHandler(ScreenBuffer screenBuffer,
                                 Cursor cursor,
                                 LeftRightMarginModeHandler leftRightMarginModeHandler) {
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
    }

    public void setTextFormater(TextFormater textFormater) {
        this.textFormater = textFormater;
    }

    /** Parses and executes ECH (Erase Character). */
    public void handleEraseCharacterSequence(String sequence) {
        int n = parseCount(sequence);
        if (n <= 0) return;

        int row = cursor.getRow();
        int col = cursor.getColumn();

        int left = getLeftMargin();
        int right = getRightMargin();

        // Ensure start column is within margins
        col = Math.max(col, left);
        if (col > right) {
            // Cursor is to the right of the effective area -> nothing to do
            logger.debug("ECH no-op: cursor col {} beyond right margin {} (1-based {}).", col, right, right + 1);
            return;
        }

        // Compute inclusive end column, clamped to right boundary
        int end = Math.min(right, col + n - 1);

        String style = (textFormater != null) ? textFormater.getEraseFillStyle() : StyleUtils.getDefaultStyle();
        for (int c = col; c <= end; c++) {
            screenBuffer.setCell(row, c, new Cell(" ", style));
        }

        logger.debug("ECH: erased {} char(s) at row {}, cols {}..{} (1-based).",
                (end - col + 1), row + 1, col + 1, end + 1);
    }

    // ----- helpers -----

    private int parseCount(String sequence) {
        try {
            Matcher m = CSI_ECH.matcher(sequence);
            if (!m.matches()) {
                logger.debug("Not an ECH sequence, ignored: {}", sequence);
                return 0;
            }
            String grp = m.group(1);
            if (grp == null || grp.isEmpty()) return 1; // default
            int v = Integer.parseInt(grp);
            return (v <= 0) ? 1 : v; // non-positive treated as 1
        } catch (Exception e) {
            logger.debug("Failed to parse ECH '{}': {}", sequence, e.toString());
            return 1;
        }
    }

    private int getLeftMargin() {
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            return Math.max(0, leftRightMarginModeHandler.getLeftMargin());
        }
        return 0;
    }

    private int getRightMargin() {
        int max = screenBuffer.getColumns() - 1;
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            return Math.min(max, leftRightMarginModeHandler.getRightMargin());
        }
        return max;
    }
}
