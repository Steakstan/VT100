package org.msv.vt100.util;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CellValueExtractor {
    private static final Logger logger = LoggerFactory.getLogger(CellValueExtractor.class);

    public static String extractCells(ScreenBuffer screenBuffer, int row, int... columnIndices) {
        int rowIndex = row - 1;
        StringBuilder sb = new StringBuilder();
        for (int col : columnIndices) {
            try {
                Cell cell = screenBuffer.getVisibleCell(rowIndex, col);
                sb.append(cell.character());
            } catch (IndexOutOfBoundsException e) {
                logger.error("Ungültige Zellposition: Zeile {}, Spalte {}", row, col);
            }
        }
        String result = sb.toString();
        logger.info("Zellwerte extrahiert (Zeile {}, Spalten {}) : {}", row, columnIndices, result);
        return result;
    }
}
