package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles DECFRA (Fill Rectangular Area): CSI Pch;Pts;Pls;Pbs;Prs $x
 *
 * Semantics:
 * - Fills the rectangle from (Pts,Pls) to (Pbs,Prs) with the given character Pch.
 * - Coordinates are 1-based and inclusive.
 * - When DECVLRM is enabled, the effective fill area is intersected with current L/R margins.
 * - When a scrolling region (DECSTBM) is set, the effective fill area is intersected with its vertical bounds.
 * - Coordinates are clamped to the screen. Empty intersection => no-op.
 *
 * Notes:
 * - DECFRA uses the current rendition, so we apply the current TextFormater style
 *   (including reverse) instead of defaults.
 */
public class FillRectangularAreaHandler {

    private static final Logger logger = LoggerFactory.getLogger(FillRectangularAreaHandler.class);

    private final ScreenBuffer screenBuffer;
    private LeftRightMarginModeHandler leftRightMarginModeHandler;
    private ScrollingRegionHandler scrollingRegionHandler;

    // NEW
    private TextFormater textFormater;

    // CSI Pch;Pts;Pls;Pbs;Prs $x
    private static final Pattern CSI_DECFRA =
            Pattern.compile("^\\u001B?\\[(\\d*);(\\d*);(\\d*);(\\d*);(\\d*)\\$x$");

    public FillRectangularAreaHandler(ScreenBuffer screenBuffer) {
        this.screenBuffer = screenBuffer;
    }

    // Injected from EscapeSequenceHandler
    public void setLeftRightMarginModeHandler(LeftRightMarginModeHandler handler) {
        this.leftRightMarginModeHandler = handler;
    }

    public void setScrollingRegionHandler(ScrollingRegionHandler handler) {
        this.scrollingRegionHandler = handler;
    }

    public void setTextFormater(TextFormater tf) {
        this.textFormater = tf;
    }

    /**
     * Parses and executes DECFRA. Invalid or out-of-bounds rectangles are safely ignored.
     */
    public void handleDECFRA(String sequence) {
        Matcher m = CSI_DECFRA.matcher(sequence);
        if (!m.matches()) {
            logger.debug("Keine DECFRA-Sequenz, ignoriert: {}", sequence);
            return;
        }

        // Parse parameters with safe defaults
        int rows = screenBuffer.getRows();
        int cols = screenBuffer.getColumns();

        int Pch = parseOrDefault(m.group(1), 32);     // default to space if empty/0
        int Pts = parseOrDefault(m.group(2), 1);
        int Pls = parseOrDefault(m.group(3), 1);
        int Pbs = parseOrDefault(m.group(4), rows);
        int Prs = parseOrDefault(m.group(5), cols);

        // Clamp coordinates to screen bounds [1..rows], [1..cols]
        Pts = clamp(Pts, 1, rows);
        Pbs = clamp(Pbs, 1, rows);
        Pls = clamp(Pls, 1, cols);
        Prs = clamp(Prs, 1, cols);

        // Normalize so that top <= bottom, left <= right
        if (Pbs < Pts) {
            int t = Pts; Pts = Pbs; Pbs = t;
        }
        if (Prs < Pls) {
            int t = Pls; Pls = Prs; Prs = t;
        }

        // Intersect with DECVLRM margins if enabled
        if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            int lm = leftRightMarginModeHandler.getLeftMargin() + 1;          // to 1-based
            int rm = leftRightMarginModeHandler.getRightMargin() + 1;         // to 1-based
            Pls = Math.max(Pls, lm);
            Prs = Math.min(Prs, rm);
        }

        // Intersect with scrolling region if available
        if (scrollingRegionHandler != null) {
            int top = scrollingRegionHandler.getWindowStartRow() + 1;         // to 1-based
            int bot = scrollingRegionHandler.getWindowEndRow() + 1;           // to 1-based
            Pts = Math.max(Pts, top);
            Pbs = Math.min(Pbs, bot);
        }

        // Check for empty intersection
        if (Pts > Pbs || Pls > Prs) {
            logger.debug("DECFRA-Schnittmenge ist leer; nichts zu füllen. Rechteck nach Begrenzung: ({},{} → {},{})",
                    Pts, Pls, Pbs, Prs);
            return;
        }

        // Execute fill
        char fillChar = (char) (Pch & 0xFFFF);
        fillArea(Pts - 1, Pls - 1, Pbs - 1, Prs - 1, String.valueOf(fillChar));

        logger.debug("DECFRA füllte Bereich ({},{} → {},{}) mit '{}'(U+{}).",
                Pts, Pls, Pbs, Prs, printable(fillChar), String.format("%04X", (int) fillChar));
    }

    // ---- internals ----

    private void fillArea(int top, int left, int bottom, int right, String ch) {
        String style = (textFormater != null) ? textFormater.getCurrentStyle() : StyleUtils.getDefaultStyle();
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                screenBuffer.setCell(row, col, new Cell(ch, style));
            }
        }
    }

    private int parseOrDefault(String raw, int def) {
        if (raw == null || raw.isEmpty()) return def;
        try {
            int v = Integer.parseInt(raw);
            return (v == 0) ? def : v; // treat 0 as "default" per common CSI practice
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String printable(char c) {
        if (Character.isISOControl(c)) return String.format("\\u%04X", (int) c);
        return String.valueOf(c);
    }
}
