package org.msv.vt100.ANSIISequences;

import org.msv.vt100.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DECCRASequenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(DECCRASequenceHandler.class);

    private final CopyRectangularAreaHandler copyRectangularAreaHandler;
    private final ScreenBuffer screenBuffer;

    public DECCRASequenceHandler(
            CopyRectangularAreaHandler copyRectangularAreaHandler,
            ScreenBuffer screenBuffer) {
        this.copyRectangularAreaHandler = copyRectangularAreaHandler;
        this.screenBuffer = screenBuffer;
    }

    public void handleDECCRA(String sequence) {
        // Remove ESC and "$v"
        String paramsPart = sequence.substring(1, sequence.length() - 2);
        String[] params = paramsPart.split(";");

        if (params.length < 8) {
            logger.warn("Not enough parameters for DECCRA: {}", sequence);
            return;
        }

        try {
            int maxRows = screenBuffer.getRows();
            int maxCols = screenBuffer.getColumns();

            // Parse source coordinates and destination coordinates using default conversion:
            int Pts = parseParameter(params[0], 1, 1, maxRows);
            int Pls = parseParameter(params[1], 1, 1, maxCols);
            int Pbs = parseParameter(params[2], maxRows, 1, maxRows);
            int Prs = parseParameter(params[3], maxCols, 1, maxCols);
            int Ptd = parseParameter(params[4], 1, 1, maxRows);
            int Pld = parseParameter(params[5], 1, 1, maxCols);
            int Psrc_page = parseParameter(params[6], 1);
            int Pdst_page = parseParameter(params[7], 1);

            // Ensure that lower coordinates are not less than upper ones.
            if (Pbs < Pts) {
                int temp = Pts;
                Pts = Pbs;
                Pbs = temp;
            }
            if (Prs < Pls) {
                int temp = Pls;
                Pls = Prs;
                Prs = temp;
            }

            // If source and destination are on the same page and the destination top is only one row below source,
            // adjust the destination to avoid shifting the entire text.
            if (Psrc_page == Pdst_page && Ptd == Pts + 1) {
                logger.info("Overlapping source and destination on the same page with vertical offset detected; " +
                        "adjusting destination top row from {} to {} to avoid shifting the text.", Ptd, Pts);
                Ptd = Pts;
            }

            // Call the copy area handler with the (possibly adjusted) parameters.
            copyRectangularAreaHandler.copyArea(Pts, Pls, Pbs, Prs, Ptd, Pld, Psrc_page, Pdst_page);

            if (shouldSwitchToDestinationPage(Pdst_page)) {
                // Switch to destination page.
                screenBuffer.switchToPage(Pdst_page);
            } else {
                // Stay on the source page.
                screenBuffer.switchToPage(Psrc_page);
            }

        } catch (NumberFormatException e) {
            logger.error("Error parsing DECCRA parameters: {}", sequence, e);
        }
    }

    // Helper method with bounds checking.
    private int parseParameter(String param, int defaultValue, int minValue, int maxValue) {
        int value;
        if (param == null || param.isEmpty()) {
            value = defaultValue;
        } else {
            value = Integer.parseInt(param);
            value = (value == 0) ? defaultValue : value;
        }
        value = Math.max(minValue, Math.min(value, maxValue));
        return value;
    }

    // Overloaded helper.
    private int parseParameter(String param, int defaultValue) {
        if (param == null || param.isEmpty()) {
            return defaultValue;
        }
        int value = Integer.parseInt(param);
        return (value == 0) ? defaultValue : value;
    }

    private boolean shouldSwitchToDestinationPage(int Pdst_page) {
        // Switch if destination page equals 1 or 2.
        return Pdst_page == 1 || Pdst_page == 2;
    }
}
