package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;
import org.msv.vt100.core.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles "Insert Line" (IL) control sequence: CSI Pn L
 * - Inserts Pn blank lines at and below the current row within the scrolling region.
 * - Lines that would roll off the bottom of the region are discarded.
 * - Inserted lines are cleared using the current default style.
 * - Left/right margins (DECVLRM) are honored if enabled; otherwise, the full width is used.
 */
public class InsertLineHandler {

    private static final Logger logger = LoggerFactory.getLogger(InsertLineHandler.class);

    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final ScrollingRegionHandler scrollingRegionHandler;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;

    // NEW
    private TextFormater textFormater;

    // CSI Pn L (Pn optional, default 1)
    private static final Pattern CSI_IL = Pattern.compile("^\\u001B?\\[(\\d*)L$");

    public InsertLineHandler(ScreenBuffer screenBuffer,
                             Cursor cursor,
                             ScrollingRegionHandler scrollingRegionHandler,
                             LeftRightMarginModeHandler leftRightMarginModeHandler) {
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.scrollingRegionHandler = scrollingRegionHandler;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
    }

    public void setTextFormater(TextFormater textFormater) {
        this.textFormater = textFormater;
    }

    /**
     * Parses and applies IL (Insert Line): CSI Pn L.
     * If cursor is outside the scrolling region, the sequence has no effect.
     */
    public void handleInsertLine(String sequence) {
        int n = parseCount(sequence);
        if (n <= 0) return;

        final int currentRow = cursor.getRow();
        final int top = scrollingRegionHandler.getWindowStartRow();
        final int bottom = scrollingRegionHandler.getWindowEndRow();

        // Outside of scrolling region -> no-op
        if (currentRow < top || currentRow > bottom) {
            logger.debug("IL ignoriert: Cursorzeile {} außerhalb des Scrollbereichs {}..{} (1-basiert).",
                    currentRow + 1, top + 1, bottom + 1);
            return;
        }

        // Honor DECVLRM if enabled
        final int left = getLeftMargin();
        final int right = getRightMargin();

        // Clamp n to region height below (and including) current row
        n = Math.min(n, bottom - currentRow + 1);
        if (n == 0) return;

        // If cursor is at the very top of the region, scrolling down by n is equivalent
        if (currentRow == top) {
            scrollingRegionHandler.scrollDownWithinRegion(n);
            logger.debug("IL durch Regions-Scroll-Down um {} Zeile(n) oben ausgeführt.", n);
            return;
        }

        // Shift lines down within [currentRow .. bottom], respecting margins
        shiftDown(currentRow, bottom, left, right, n);

        // Clear the n inserted lines at [currentRow .. currentRow + n - 1]
        for (int row = currentRow; row < currentRow + n; row++) {
            clearLine(row, left, right);
        }

        logger.debug("IL hat {} Zeile(n) bei Zeile {} innerhalb Bereich {}..{}, Spalten {}..{} eingefügt (1-basiert).",
                n, currentRow + 1, top + 1, bottom + 1, left + 1, right + 1);
    }

    // ----- helpers -----

    private int parseCount(String sequence) {
        try {
            Matcher m = CSI_IL.matcher(sequence);
            if (!m.matches()) {
                logger.debug("Keine IL-Sequenz, ignoriert: {}", sequence);
                return 0;
            }
            String grp = m.group(1);
            if (grp == null || grp.isEmpty()) return 1; // default Pn
            int val = Integer.parseInt(grp);
            return (val <= 0) ? 1 : val; // VT semantics: non-positive -> treat as 1
        } catch (Exception e) {
            logger.debug("Parsen von IL '{}' fehlgeschlagen: {}", sequence, e.toString());
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

    private void shiftDown(int fromRow, int toRow, int left, int right, int n) {
        // Move content down bottom-up; deep-copy cells to avoid aliasing
        for (int row = toRow; row >= fromRow + n; row--) {
            for (int col = left; col <= right; col++) {
                org.msv.vt100.core.Cell src = screenBuffer.getCell(row - n, col);
                screenBuffer.setCell(row, col, cloneCell(src));
            }
        }
    }

    private void clearLine(int row, int left, int right) {
        String style = (textFormater != null) ? textFormater.getEraseFillStyle() : StyleUtils.getDefaultStyle();
        for (int col = left; col <= right; col++) {
            screenBuffer.setCell(row, col, new Cell(" ", style));
        }
    }

    private Cell cloneCell(Cell c) {
        return new Cell(c.character(), c.style());
    }
}
