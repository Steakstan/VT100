package org.msv.vt100.ANSIISequences;

import org.msv.vt100.Cell;
import org.msv.vt100.Cursor;
import org.msv.vt100.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsertLineHandler {

    private static final Logger logger = LoggerFactory.getLogger(InsertLineHandler.class);

    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final ScrollingRegionHandler scrollingRegionHandler;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;

    // Default style for new lines
    private static final String DEFAULT_STYLE = "-fx-fill: white; -rtfx-background-color: transparent;";

    public InsertLineHandler(ScreenBuffer screenBuffer, Cursor cursor,
                             ScrollingRegionHandler scrollingRegionHandler,
                             LeftRightMarginModeHandler leftRightMarginModeHandler) {
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.scrollingRegionHandler = scrollingRegionHandler;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
    }

    /**
     * Handles the insert line sequence ESC [ Pn L.
     *
     * @param sequence The sequence, e.g., ESC [ Pn L
     */
    public void handleInsertLine(String sequence) {
        // Extract parameter Pn
        int n = parseParameter(sequence);

        // Get current cursor position
        int currentRow = cursor.getRow();
        int currentColumn = cursor.getColumn();

        // Get scrolling region boundaries
        int topMargin = scrollingRegionHandler.getWindowStartRow();
        int bottomMargin = scrollingRegionHandler.getWindowEndRow();

        // Ensure cursor is within scrolling region
        if (currentRow < topMargin || currentRow > bottomMargin) {
            logger.info("Cursor is outside the scrolling region. Insert lines not performed.");
            return;
        }

        // Adjust n to not exceed scrolling region boundaries
        n = Math.min(n, bottomMargin - currentRow + 1);

        // Get left and right margins
        int leftMargin = 0;
        int rightMargin = screenBuffer.getColumns() - 1;
        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginModeHandler.getLeftMargin();
            rightMargin = leftRightMarginModeHandler.getRightMargin();
        }

        // If cursor is at the top of the scrolling region, perform scrolling down
        if (currentRow == topMargin) {
            scrollingRegionHandler.scrollDownWithinRegion(n);
        } else {
            // Shift lines down within the scrolling region
            for (int row = bottomMargin; row >= currentRow + n; row--) {
                for (int col = leftMargin; col <= rightMargin; col++) {
                    Cell cell = screenBuffer.getCell(row - n, col);
                    screenBuffer.setCell(row, col, cell);
                }
            }

            // Clear inserted lines
            for (int row = currentRow; row < currentRow + n; row++) {
                clearLine(row, leftMargin, rightMargin);
            }
        }

        logger.info("Inserted {} lines starting from row {}", n, currentRow + 1);
    }

    private int parseParameter(String sequence) {
        try {
            String numberPart = sequence.replace("\u001B", "").replace("[", "").replace("L", "");
            if (numberPart.isEmpty()) {
                return 1; // Default value
            }
            return Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            logger.error("Error parsing parameter in insert line sequence: {}", sequence, e);
            return 1; // Default value
        }
    }

    private void clearLine(int row, int leftMargin, int rightMargin) {
        for (int col = leftMargin; col <= rightMargin; col++) {
            screenBuffer.setCell(row, col, new Cell(" ", DEFAULT_STYLE));
        }
    }
}
