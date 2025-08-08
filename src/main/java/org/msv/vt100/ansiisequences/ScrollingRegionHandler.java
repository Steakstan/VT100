package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScrollingRegionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScrollingRegionHandler.class);

    private final ScreenBuffer screenBuffer;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final LeftRightMarginSequenceHandler leftRightMarginSequenceHandler;

    // Region bounds are 0-based, inclusive
    private int windowStartRow = 0;
    private int windowEndRow;

    // Pattern for CSI Pt;Pb r  (both params optional for full reset)
    private static final Pattern CSI_SCROLL_REGION = Pattern.compile("^\\u001B?\\[(\\d*);?(\\d*)r$");

    public ScrollingRegionHandler(ScreenBuffer screenBuffer,
                                  LeftRightMarginModeHandler leftRightMarginModeHandler,
                                  LeftRightMarginSequenceHandler leftRightMarginSequenceHandler) {
        this.screenBuffer = screenBuffer;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.leftRightMarginSequenceHandler = leftRightMarginSequenceHandler;
        // Initialize to full screen
        this.windowEndRow = screenBuffer.getRows() - 1;
    }

    /**
     * Parses and applies CSI Pt;Pb r (DECSTBM).
     * Empty parameters mean "reset to full screen".
     * Indices in the sequence are 1-based; internally we store 0-based.
     */
    public void setScrollingRegion(String sequence) {
        try {
            Matcher m = CSI_SCROLL_REGION.matcher(sequence);
            if (!m.matches()) {
                logger.debug("Ignoring non-scroll-region sequence: {}", sequence);
                return;
            }

            int rows = screenBuffer.getRows();
            // Defaults per spec: Pt=1, Pb=rows
            int Pt = parseOrDefault(m.group(1), 1);
            int Pb = parseOrDefault(m.group(2), rows);

            // Clamp to [1..rows]
            Pt = Math.max(1, Math.min(Pt, rows));
            Pb = Math.max(1, Math.min(Pb, rows));

            // Ensure Pt <= Pb
            if (Pt > Pb) {
                // Swap to keep a valid region instead of rejecting
                int t = Pt; Pt = Pb; Pb = t;
            }

            int newStart = Pt - 1;
            int newEnd   = Pb - 1;

            if (!isValidScrollingRegion(newStart, newEnd)) {
                // Fallback to full screen if invalid (shouldn't happen after clamping)
                resetToFullScreen();
                logger.debug("Invalid region after clamp; reset to full screen.");
                return;
            }

            this.windowStartRow = newStart;
            this.windowEndRow = newEnd;

            int left = getCurrentLeftMargin();
            int right = getCurrentRightMargin();

            logger.debug("Scrolling region set: rows {}..{} (1-based), columns {}..{} (1-based)",
                    windowStartRow + 1, windowEndRow + 1, left + 1, right + 1);

        } catch (Exception e) {
            // Be robust: on any parsing failure, reset to full screen
            resetToFullScreen();
            logger.debug("Failed to parse scrolling region '{}'. Reset to full screen.", sequence, e);
        }
    }

    /**
     * Scrolls down within the current region by n lines.
     * Lines move towards larger row indices; new lines appear at the top.
     */
    public void scrollDownWithinRegion(int n) {
        if (n <= 0 || windowEndRow < windowStartRow) return;

        int columns = screenBuffer.getColumns();
        int left = getCurrentLeftMargin();
        int right = Math.min(getCurrentRightMargin(), columns - 1);

        // Clamp n to region height
        int height = windowEndRow - windowStartRow + 1;
        n = Math.min(n, height);

        // Shift down: bottom to top to avoid overwriting; deep-copy cells to prevent aliasing
        for (int row = windowEndRow; row >= windowStartRow + n; row--) {
            for (int col = left; col <= right; col++) {
                Cell src = screenBuffer.getCell(row - n, col);
                screenBuffer.setCell(row, col, cloneCell(src));
            }
        }

        // Clear top n lines within margins
        for (int row = windowStartRow; row < windowStartRow + n; row++) {
            clearLine(row, left, right);
        }

        logger.debug("Scrolled down within region rows {}..{}, cols {}..{}, by {} lines",
                windowStartRow + 1, windowEndRow + 1, left + 1, right + 1, n);
    }

    /**
     * Scrolls up within the current region by one line.
     * Lines move towards smaller row indices; a new blank line appears at the bottom.
     */
    public void scrollUpWithinRegion() {
        if (windowEndRow < windowStartRow) return;

        int columns = screenBuffer.getColumns();
        int left = getCurrentLeftMargin();
        int right = Math.min(getCurrentRightMargin(), columns - 1);

        // Shift up: top to bottom; deep-copy cells to prevent aliasing
        for (int row = windowStartRow; row < windowEndRow; row++) {
            for (int col = left; col <= right; col++) {
                Cell src = screenBuffer.getCell(row + 1, col);
                screenBuffer.setCell(row, col, cloneCell(src));
            }
        }

        // Clear bottom line within margins
        clearLine(windowEndRow, left, right);

        logger.debug("Scrolled up within region rows {}..{}, cols {}..{}",
                windowStartRow + 1, windowEndRow + 1, left + 1, right + 1);
    }

    /**
     * Resets the region to the full screen (1..rows).
     */
    public void resetToFullScreen() {
        this.windowStartRow = 0;
        this.windowEndRow = screenBuffer.getRows() - 1;
    }

    public int getWindowStartRow() {
        return windowStartRow;
    }

    public int getWindowEndRow() {
        return windowEndRow;
    }

    // ----- Helpers -----

    private boolean isValidScrollingRegion(int startRow, int endRow) {
        int totalRows = screenBuffer.getRows();
        return startRow >= 0 && endRow < totalRows && startRow <= endRow;
    }

    private int parseOrDefault(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try {
            int v = Integer.parseInt(s);
            return (v == 0) ? def : v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int getCurrentLeftMargin() {
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            return Math.max(0, leftRightMarginSequenceHandler.getLeftMargin());
        }
        return 0;
    }

    private int getCurrentRightMargin() {
        int max = screenBuffer.getColumns() - 1;
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            return Math.min(max, leftRightMarginSequenceHandler.getRightMargin());
        }
        return max;
    }

    private void clearLine(int row, int left, int right) {
        for (int col = left; col <= right; col++) {
            screenBuffer.setCell(row, col, new Cell(" ", StyleUtils.getDefaultStyle()));
        }
    }

    private Cell cloneCell(Cell c) {
        // Defensive copy; assumes Cell is immutable-like (char + style string)
        return new Cell(c.character(), c.style());
    }
}
