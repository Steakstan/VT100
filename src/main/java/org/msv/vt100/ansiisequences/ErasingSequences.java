package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements erase operations (ED/EL/ECH family) and line deletion within the scrolling region.
 *
 * Notes on margins and regions:
 * - EL/ECH honor DECVLRM: operations are restricted to [leftMargin..rightMargin].
 * - ED(0) "from cursor to end of screen" and ED(1) "from start to cursor" honor margins horizontally
 *   to match typical app expectations in margin mode (consistent with existing codebase).
 * - ED(2) "entire screen" explicitly ignores L/R margins and clears full width of all rows).
 *
 * Scrolling region:
 * - deleteLines (DL) only acts when the cursor is inside the scrolling region and respects DECVLRM horizontally.
 */
public class ErasingSequences {

    private static final Logger logger = LoggerFactory.getLogger(ErasingSequences.class);

    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final ScrollingRegionHandler scrollingRegionHandler;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private TextFormater textFormater;

    public ErasingSequences(ScreenBuffer screenBuffer,
                            Cursor cursor,
                            ScrollingRegionHandler scrollingRegionHandler,
                            LeftRightMarginModeHandler leftRightMarginModeHandler) {
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.scrollingRegionHandler = scrollingRegionHandler;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
    }

    /** Inject TextFormater after construction. */
    public void setTextFormater(TextFormater textFormater) {
        this.textFormater = textFormater;
    }

    // -------------------- Public API (ED/EL/ECH used by dispatcher) --------------------

    /** ED(0): Clear from cursor to end of screen (rows below cursor fully; this row from cursor to right). */
    public void clearFromCursorToEndOfScreen() {
        performWithCursorRestore(() -> {
            int row = cursor.getRow();
            int col = Math.max(cursor.getColumn(), getLeftMargin());
            int rows = screenBuffer.getRows();

            // 1) Clear from cursor to end of line (within margins)
            clearRangeInLine(row, col, getRightMargin());

            // 2) Clear all lines after current (within margins)
            for (int r = row + 1; r < rows; r++) {
                clearLineWithinMargins(r);
            }

            logger.debug("ED(0): von Cursor bis Bildschirmende gelöscht ab Zeile {}, Spalte {} (1-basiert).",
                    row + 1, col + 1);
        });
    }

    /** ED(1): Clear from start of screen to cursor (rows above cursor fully; this row from left to cursor). */
    public void clearFromStartOfScreenToCursor() {
        performWithCursorRestore(() -> {
            int row = cursor.getRow();
            int col = Math.min(cursor.getColumn(), getRightMargin());

            // 1) Clear all lines before current (within margins)
            for (int r = 0; r < row; r++) {
                clearLineWithinMargins(r);
            }

            // 2) Clear from left margin to cursor in current row
            clearRangeInLine(row, getLeftMargin(), col);

            logger.debug("ED(1): vom Bildschirmanfang bis zum Cursor gelöscht, endet bei Zeile {}, Spalte {} (1-basiert).",
                    row + 1, col + 1);
        });
    }

    /** ED(2): Clear entire screen (ignores L/R margins; clears full width of all rows). */
    public void clearEntireScreen() {
        performWithCursorRestore(() -> {
            int rows = screenBuffer.getRows();
            int cols = screenBuffer.getColumns();
            for (int r = 0; r < rows; r++) {
                clearRangeInLineFullWidth(r, 0, cols - 1);
            }
            logger.debug("ED(2): gesamten Bildschirm gelöscht (volle Breite, alle Zeilen).");
        });
    }

    /** EL(2): Clear entire line at the cursor (within margins). */
    public void clearEntireLine() {
        performWithCursorRestore(() -> {
            int row = cursor.getRow();
            clearLineWithinMargins(row);
            logger.debug("EL(2): ganze Zeile {} gelöscht (1-basiert).", row + 1);
        });
    }

    /** EL(0): Clear from cursor to end of line (within margins). */
    public void clearFromCursorToEndOfLine() {
        performWithCursorRestore(() -> {
            int row = cursor.getRow();
            int start = Math.max(cursor.getColumn(), getLeftMargin());
            int end = getRightMargin();
            clearRangeInLine(row, start, end);
            logger.debug("EL(0): Zeile {} von Spalte {} bis {} gelöscht (1-basiert).", row + 1, start + 1, end + 1);
        });
    }

    /** EL(1): Clear from start of line to cursor (within margins). */
    public void clearFromStartOfLineToCursor() {
        performWithCursorRestore(() -> {
            int row = cursor.getRow();
            int start = getLeftMargin();
            int end = Math.min(cursor.getColumn(), getRightMargin());
            clearRangeInLine(row, start, end);
            logger.debug("EL(1): Zeile {} von Spalte {} bis {} gelöscht (1-basiert).", row + 1, start + 1, end + 1);
        });
    }

    /**
     * DL (Delete Lines): delete n lines starting at the cursor row, within the scrolling region.
     * Lines below shift up; the bottom n lines are cleared (within margins).
     */
    public void deleteLines(int n) {
        if (n <= 0) return;

        int currentRow = cursor.getRow();
        int top = scrollingRegionHandler.getWindowStartRow();
        int bottom = scrollingRegionHandler.getWindowEndRow();

        // Must be inside scrolling region
        if (currentRow < top || currentRow > bottom) {
            logger.debug("DL ignoriert: Cursorzeile {} außerhalb des Scrollbereichs {}..{} (1-basiert).",
                    currentRow + 1, top + 1, bottom + 1);
            return;
        }

        // Clamp n to available lines in region from currentRow downwards
        n = Math.min(n, bottom - currentRow + 1);
        if (n == 0) return;

        int left = getLeftMargin();
        int right = getRightMargin();

        // Shift lines up within [currentRow .. bottom - n]
        for (int row = currentRow; row <= bottom - n; row++) {
            for (int col = left; col <= right; col++) {
                Cell src = screenBuffer.getCell(row + n, col);
                screenBuffer.setCell(row, col, cloneCell(src));
            }
        }

        // Clear bottom n lines in region (within margins)
        for (int row = bottom - n + 1; row <= bottom; row++) {
            clearRangeInLine(row, left, right);
        }

        logger.debug("DL: {} Zeile(n) bei Zeile {} innerhalb Bereich {}..{}, Spalten {}..{} gelöscht (1-basiert).",
                n, currentRow + 1, top + 1, bottom + 1, left + 1, right + 1);
    }

    // -------------------- Internals --------------------

    /** Saves the cursor position, runs the operation, then restores the cursor. */
    private void performWithCursorRestore(Runnable op) {
        int initialRow = cursor.getRow();
        int initialCol = cursor.getColumn();
        try {
            op.run();
        } finally {
            cursor.setPosition(initialRow, initialCol);
        }
    }

    /** Clears a full line range ignoring margins (used by ED(2)). */
    private void clearRangeInLineFullWidth(int row, int startCol, int endCol) {
        String style = eraseStyle();
        for (int col = startCol; col <= endCol; col++) {
            screenBuffer.setCell(row, col, new Cell(" ", style));
        }
    }

    /** Clears a line within current margins. */
    private void clearLineWithinMargins(int row) {
        clearRangeInLine(row, getLeftMargin(), getRightMargin());
    }

    /** Clears an inclusive column range in a line (clamped to screen). */
    private void clearRangeInLine(int row, int startCol, int endCol) {
        int maxCols = screenBuffer.getColumns();
        startCol = Math.max(0, Math.min(startCol, maxCols - 1));
        endCol = Math.max(0, Math.min(endCol,   maxCols - 1));
        if (endCol < startCol) return;

        String style = eraseStyle();
        for (int col = startCol; col <= endCol; col++) {
            screenBuffer.setCell(row, col, new Cell(" ", style));
        }
    }

    /** Returns current left margin or 0 if margins disabled. */
    private int getLeftMargin() {
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            return Math.max(0, leftRightMarginModeHandler.getLeftMargin());
        }
        return 0;
    }

    /** Returns current right margin or (cols-1) if margins disabled. */
    private int getRightMargin() {
        int max = screenBuffer.getColumns() - 1;
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            return Math.min(max, leftRightMarginModeHandler.getRightMargin());
        }
        return max;
    }

    /** Defensive clone for Cell to avoid aliasing when shifting lines. */
    private Cell cloneCell(Cell c) {
        return new Cell(c.character(), c.style());
    }

    /** Style to use when erasing/clearing cells. */
    private String eraseStyle() {
        return (textFormater != null) ? textFormater.getEraseFillStyle() : StyleUtils.getDefaultStyle();
    }
}
