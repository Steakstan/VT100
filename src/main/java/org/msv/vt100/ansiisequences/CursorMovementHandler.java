package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles absolute cursor positioning:
 * - CUP:  CSI row;col H
 * - HVP:  CSI row;col f
 *
 * Semantics:
 * - row/col are 1-based; 0 or empty mean default 1.
 * - DECOM/DECVLRM effects are applied by CursorController, not here.
 * - If parameters exceed screen or active window, CursorController clamps them.
 */
public class CursorMovementHandler {

    private static final Logger logger = LoggerFactory.getLogger(CursorMovementHandler.class);

    private final CursorController cursorController;

    // CUP and HVP accept optional/empty parameters which default to 1
    private static final Pattern CUP_PATTERN = Pattern.compile("^\\[(\\d*);(\\d*)H$");
    private static final Pattern HVP_PATTERN = Pattern.compile("^\\[(\\d*);(\\d*)f$");

    public CursorMovementHandler(CursorController cursorController) {
        this.cursorController = cursorController;
    }

    /**
     * Parses CUP/HVP sequence and forwards to CursorController with 0-based coordinates.
     * Missing/zero parameters default to 1 (VT semantics).
     */
    public void handleCursorMovement(String sequence) {
        int row1 = -1;
        int col1 = -1;

        Matcher m = CUP_PATTERN.matcher(sequence);
        if (m.matches()) {
            row1 = parse1BasedOrDefault(m.group(1));
            col1 = parse1BasedOrDefault(m.group(2));
        } else {
            m = HVP_PATTERN.matcher(sequence);
            if (m.matches()) {
                row1 = parse1BasedOrDefault(m.group(1));
                col1 = parse1BasedOrDefault(m.group(2));
            } else {
                logger.debug("Keine CUP/HVP-Sequenz, ignoriert: {}", sequence);
                return;
            }
        }

        // Convert to 0-based for internal API
        int row0 = row1 - 1;
        int col0 = col1 - 1;

        // Delegate clamping and DECOM/DECVLRM math to CursorController
        cursorController.setCursorPosition(row0, col0);
        logger.debug("CUP/HVP angeforderte Position: Zeile={}, Spalte={} (1-basiert).", row1, col1);
    }

    // ---- helpers ----

    /** Parses 1-based integer; empty or zero -> 1. */
    private int parse1BasedOrDefault(String raw) {
        if (raw == null || raw.isEmpty()) return 1;
        try {
            int v = Integer.parseInt(raw);
            return v <= 0 ? 1 : v;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
