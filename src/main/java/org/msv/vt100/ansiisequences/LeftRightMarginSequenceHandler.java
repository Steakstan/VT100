package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles DECSLRM (CSI Pl;Pr s) — Set Left and Right Margins.
 * Notes:
 * - Columns in the sequence are 1-based inclusive.
 * - DECSLRM only takes effect when DECVLRM mode (left/right margin mode) is enabled.
 * - If parameters are invalid (left >= right) the sequence is ignored.
 * - Callers may want to reposition the cursor after applying margins.
 */
public class LeftRightMarginSequenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeftRightMarginSequenceHandler.class);

    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final CursorController cursorController;
    private final ScreenBuffer screenBuffer;

    // Stored margins in 0-based inclusive coordinates
    private int leftMargin = 0;
    private int rightMargin;

    // CSI Pl;Pr s  (parameters optional but both expected; we apply safe defaults)
    private static final Pattern CSI_LR_MARGINS = Pattern.compile("^\\u001B?\\[(\\d*);(\\d*)s$");

    public LeftRightMarginSequenceHandler(
            LeftRightMarginModeHandler leftRightMarginModeHandler,
            CursorController cursorController,
            ScreenBuffer screenBuffer) {
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.cursorController = cursorController;
        this.screenBuffer = screenBuffer;
        this.rightMargin = screenBuffer.getColumns() - 1;
    }

    /**
     * Parses and applies DECSLRM (CSI Pl;Pr s).
     * If DECVLRM is disabled, the sequence is ignored as per DEC behavior.
     */
    public void handleLeftRightMarginSequence(String sequence) {
        // Require DECVLRM enabled
        if (!leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            logger.debug("DECSLRM ignoriert, da DECVLRM deaktiviert ist: {}", sequence);
            return;
        }

        Matcher m = CSI_LR_MARGINS.matcher(sequence);
        if (!m.matches()) {
            logger.debug("Keine DECSLRM-Sequenz, ignoriert: {}", sequence);
            return;
        }

        int cols = screenBuffer.getColumns();

        int Pl = parseCol(m.group(1), 1, cols);      // default 1 if empty/0
        int Pr = parseCol(m.group(2), cols, cols);   // default max if empty/0

        // Validate and clamp to [1..cols]
        Pl = Math.max(1, Math.min(Pl, cols));
        Pr = Math.max(1, Math.min(Pr, cols));

        // Left must be strictly less than right; zero-width regions are invalid
        if (Pl >= Pr) {
            logger.debug("Ungültige DECSLRM-Ränder (Pl >= Pr): Pl={}, Pr={}, Spalten={}", Pl, Pr, cols);
            return;
        }

        // Convert to 0-based inclusive
        int newLeft = Pl - 1;
        int newRight = Pr - 1;

        // Store locally
        this.leftMargin = newLeft;
        this.rightMargin = newRight;

        // Propagate to mode handler & cursor controller
        leftRightMarginModeHandler.setLeftRightMargins(newLeft, newRight);
        cursorController.setLeftRightMargins(newLeft, newRight);

        logger.debug("DECSLRM angewendet: Spalten {}..{} (1-basiert).", Pl, Pr);
    }

    public int getLeftMargin() {
        return leftMargin;
    }

    public int getRightMargin() {
        return rightMargin;
    }

    // ----- helpers -----

    private int parseCol(String raw, int def, int max) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            int v = Integer.parseInt(raw);
            if (v == 0) return def;       // 0 treated as default per typical CSI behavior
            return Math.max(1, Math.min(v, max));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
