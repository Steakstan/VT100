package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.core.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FillRectangularAreaHandler {

    private static final Logger logger = LoggerFactory.getLogger(FillRectangularAreaHandler.class);

    private final ScreenBuffer screenBuffer;

    public FillRectangularAreaHandler(ScreenBuffer screenBuffer) {
        this.screenBuffer = screenBuffer;
    }

    /**
     * Handles the DECFRA sequence to fill a rectangular area with a specific character.
     * Sequence format: ESC [ Pch ; Pts ; Pls ; Pbs ; Prs $ x
     *
     * @param sequence The DECFRA escape sequence.
     */
    public void handleDECFRA(String sequence) {
        // Remove ESC, '[', and '$x' to extract parameters
        String paramsPart = sequence.replace("\u001B", "").replace("[", "").replace("$x", "");
        String[] params = paramsPart.split(";");

        if (params.length < 5) {
            logger.warn("Insufficient parameters for DECFRA: {}", sequence);
            return;
        }

        try {
            int Pch = Integer.parseInt(params[0]);
            int Pts = Integer.parseInt(params[1]);
            int Pls = Integer.parseInt(params[2]);
            int Pbs = Integer.parseInt(params[3]);
            int Prs = Integer.parseInt(params[4]);

            // Ensure parameters are within valid ranges
            if (!isValidArea(Pts, Pls, Pbs, Prs)) {
                logger.warn("Invalid area specified in DECFRA: {}", sequence);
                return;
            }

            // Convert character code to actual character
            char fillChar = (char) Pch;

            // Fill the specified area
            fillArea(Pts - 1, Pls - 1, Pbs - 1, Prs - 1, String.valueOf(fillChar));

            logger.info("Filled area from ({}, {}) to ({}, {}) with character '{}'",
                    Pts, Pls, Pbs, Prs, fillChar);

        } catch (NumberFormatException e) {
            logger.error("Error parsing DECFRA parameters: {}", sequence, e);
        }
    }

    /**
     * Fills the specified rectangular area with the given character.
     *
     * @param top    Top row index (0-based).
     * @param left   Left column index (0-based).
     * @param bottom Bottom row index (0-based).
     * @param right  Right column index (0-based).
     * @param ch     Character to fill.
     */
    private void fillArea(int top, int left, int bottom, int right, String ch) {
        String currentStyle = "-fx-fill: white; -rtfx-background-color: transparent;"; // Or get from context

        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                screenBuffer.setCell(row, col, new Cell(ch, currentStyle));
            }
        }
    }

    /**
     * Validates that the specified area is within the screen buffer boundaries.
     *
     * @return True if valid, false otherwise.
     */
    private boolean isValidArea(int Pts, int Pls, int Pbs, int Prs) {
        int maxRows = screenBuffer.getRows();
        int maxCols = screenBuffer.getColumns();

        return Pts >= 1 && Pbs >= Pts && Pbs <= maxRows
                && Pls >= 1 && Prs >= Pls && Prs <= maxCols;
    }
}