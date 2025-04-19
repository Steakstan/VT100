package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrollingRegionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScrollingRegionHandler.class);
    private final ScreenBuffer screenBuffer;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler; // Added
    private final LeftRightMarginSequenceHandler leftRightMarginSequenceHandler; // Added

    private int windowStartRow = 0; // The starting row of the scrolling region (0-indexed)
    private int windowEndRow;       // The ending row of the scrolling region (0-indexed)

    // Default style for new cells
    private static final String DEFAULT_STYLE = "-fx-fill: white; -rtfx-background-color: transparent;";

    /**
     * Constructs a ScrollingRegionHandler.
     *
     * @param screenBuffer                   the screen buffer to be manipulated
     * @param leftRightMarginModeHandler     handler for left/right margin mode
     * @param leftRightMarginSequenceHandler handler for left/right margin sequences
     */
    public ScrollingRegionHandler(ScreenBuffer screenBuffer,
                                  LeftRightMarginModeHandler leftRightMarginModeHandler,
                                  LeftRightMarginSequenceHandler leftRightMarginSequenceHandler) {
        this.screenBuffer = screenBuffer;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.leftRightMarginSequenceHandler = leftRightMarginSequenceHandler;
        this.windowEndRow = screenBuffer.getRows() - 1; // Initialize the bottom boundary of the scrolling region
    }

    /**
     * Sets the scrolling region based on an ANSI control sequence.
     *
     * @param sequence the sequence containing the scrolling region boundaries in the format "[Pt;Pb]r"
     */
    public void setScrollingRegion(String sequence) {
        try {
            // Remove ESC, '[', and 'r' for proper parsing of the boundaries
            sequence = sequence.replaceAll("[\\u001B\\[r]", "");
            String[] bounds = sequence.split(";");

            int totalRows = screenBuffer.getRows();

            // Get the top and bottom boundaries with validation
            windowStartRow = parseBoundary(bounds, 0, 1) - 1;
            windowEndRow = parseBoundary(bounds, 1, totalRows) - 1;

            // Ensure boundaries are within valid range
            windowStartRow = Math.max(0, windowStartRow);
            windowEndRow = Math.min(totalRows - 1, windowEndRow);

            if (!isValidScrollingRegion(windowStartRow, windowEndRow)) {
                logger.warn("Scrolling region out of valid range: {}", sequence);
                return;
            }

            logger.info("Scrolling region set: from row {} to {}, columns {} to {}",
                    windowStartRow + 1, windowEndRow + 1,
                    leftRightMarginSequenceHandler.getLeftMargin() + 1,
                    leftRightMarginSequenceHandler.getRightMargin() + 1);

        } catch (NumberFormatException e) {
            logger.error("Invalid format for scrolling region: {}", sequence, e);
        }
    }

    /**
     * Scrolls down within the scrolling region by n lines.
     *
     * @param n the number of lines to scroll down
     */
    public void scrollDownWithinRegion(int n) {
        int columns = screenBuffer.getColumns();

        // Get current left and right margins
        int leftMargin = 0;
        int rightMargin = columns - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginSequenceHandler.getLeftMargin();
            rightMargin = leftRightMarginSequenceHandler.getRightMargin();
        }

        // Ensure margins are within bounds
        leftMargin = Math.max(0, leftMargin);
        rightMargin = Math.min(columns - 1, rightMargin);

        // Adjust n so that it does not exceed the scrolling region
        n = Math.min(n, windowEndRow - windowStartRow + 1);

        // Scroll down within the scrolling region and margins
        for (int row = windowEndRow; row >= windowStartRow + n; row--) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                Cell prevCell = screenBuffer.getCell(row - n, col);
                screenBuffer.setCell(row, col, prevCell);
            }
        }

        // Clear the top n lines within the margins
        for (int row = windowStartRow; row < windowStartRow + n; row++) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                screenBuffer.setCell(row, col, new Cell(" ", DEFAULT_STYLE));
            }
        }

        logger.info("Scrolled down within region from row {} to {}, columns {}-{}, by {} lines",
                windowStartRow + 1, windowEndRow + 1, leftMargin + 1, rightMargin + 1, n);
    }

    /**
     * Scrolls up the content within the scrolling region by one line.
     * Takes into account the set left and right margins.
     */
    public void scrollUpWithinRegion() {
        int columns = screenBuffer.getColumns();

        // Get current left and right margins
        int leftMargin = 0;
        int rightMargin = columns - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginSequenceHandler.getLeftMargin();
            rightMargin = leftRightMarginSequenceHandler.getRightMargin();
        }

        // Ensure margin boundaries are correct
        leftMargin = Math.max(0, leftMargin);
        rightMargin = Math.min(columns - 1, rightMargin);

        // Scroll up within the scrolling region and margins
        for (int row = windowStartRow; row < windowEndRow; row++) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                Cell nextCell = screenBuffer.getCell(row + 1, col);
                screenBuffer.setCell(row, col, nextCell);
            }
        }

        // Clear the bottom row within the margins
        for (int col = leftMargin; col <= rightMargin; col++) {
            screenBuffer.setCell(windowEndRow, col, new Cell(" ", DEFAULT_STYLE));
        }

        logger.info("Scrolled up within region from row {} to {}, columns {}-{}",
                windowStartRow + 1, windowEndRow + 1, leftMargin + 1, rightMargin + 1);
    }

    /**
     * Checks whether the given scrolling region is valid.
     *
     * @param startRow the start row index
     * @param endRow   the end row index
     * @return true if the region is valid; false otherwise
     */
    private boolean isValidScrollingRegion(int startRow, int endRow) {
        int totalRows = screenBuffer.getRows();
        return startRow >= 0 && endRow < totalRows && startRow <= endRow;
    }

    /**
     * Parses a boundary value from the bounds array.
     *
     * @param bounds       an array of boundary strings
     * @param index        the index to parse
     * @param defaultValue the default value if the boundary is missing
     * @return the parsed boundary value
     */
    private int parseBoundary(String[] bounds, int index, int defaultValue) {
        if (bounds.length > index && !bounds[index].isEmpty()) {
            return Integer.parseInt(bounds[index]);
        }
        return defaultValue;
    }

    /**
     * Returns the starting row of the scrolling region (1-indexed).
     *
     * @return the starting row
     */
    public int getWindowStartRow() {
        return windowStartRow;
    }

    /**
     * Returns the ending row of the scrolling region (1-indexed).
     *
     * @return the ending row
     */
    public int getWindowEndRow() {
        return windowEndRow;
    }
}
