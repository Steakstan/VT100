package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.core.Cell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyRectangularAreaHandler {

    private final ScreenBuffer screenBuffer;

    private static final Logger logger = LoggerFactory.getLogger(CopyRectangularAreaHandler.class);

    public CopyRectangularAreaHandler(ScreenBuffer screenBuffer) {
        this.screenBuffer = screenBuffer;
    }

    public void copyArea(int Pts, int Pls, int Pbs, int Prs,
                         int Ptd, int Pld, int Psrc_page, int Pdst_page) {
        Pts = Math.max(1, Pts);
        Pls = Math.max(1, Pls);
        Pbs = Math.max(1, Pbs);
        Prs = Math.max(1, Prs);
        Ptd = Math.max(1, Ptd);
        Pld = Math.max(1, Pld);

        int numRows = Pbs - Pts + 1;
        int numCols = Prs - Pls + 1;

        logger.debug("Parameter nach Korrektur: Pts={}, Pls={}, Pbs={}, Prs={}, Ptd={}, Pld={}, numRows={}, numCols={}",
                Pts, Pls, Pbs, Prs, Ptd, Pld, numRows, numCols);

        if (isValidArea(Pts, Pls, numRows, numCols) || isValidArea(Ptd, Pld, numRows, numCols)) {
            logger.warn("Kopierbereich liegt außerhalb des Bildschirms");
            return;
        }

        copyAreaBetweenPages(Pts - 1, Pls - 1, Ptd - 1, Pld - 1, numRows, numCols, Psrc_page, Pdst_page);
        logger.info("Kopieren des Bereichs zwischen den Seiten {} und {} abgeschlossen", Psrc_page, Pdst_page);
    }

    private void copyAreaBetweenPages(int srcRowStart, int srcColStart, int dstRowStart, int dstColStart,
                                      int numRows, int numCols, int srcPageNumber, int dstPageNumber) {
        int originalPage = screenBuffer.getCurrentPageNumber();

        screenBuffer.switchToPage(srcPageNumber);

        Cell[][] tempBuffer = new Cell[numRows][numCols];
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                tempBuffer[row][col] = screenBuffer.getCell(srcRowStart + row, srcColStart + col);
            }
        }

        screenBuffer.switchToPage(dstPageNumber);

        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                screenBuffer.setCell(dstRowStart + row, dstColStart + col, tempBuffer[row][col]);
            }
        }

        screenBuffer.switchToPage(originalPage);
    }

    private boolean isValidArea(int rowStart, int colStart, int numRows, int numCols) {
        int maxRows = screenBuffer.getRows();
        int maxCols = screenBuffer.getColumns();
        return rowStart < 1 || colStart < 1
                || rowStart + numRows - 1 > maxRows
                || colStart + numCols - 1 > maxCols;
    }
}