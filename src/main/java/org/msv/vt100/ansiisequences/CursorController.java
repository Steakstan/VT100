package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;

import java.util.Map;

/**
 * Controls cursor positioning and character emission into the ScreenBuffer.
 * Responsibilities:
 * - Apply DECOM (relative origin) and DECVLRM (left/right margins) when setting cursor position.
 * - Handle auto-wrap (DECAWM) on character emission.
 * - Implement CR/LF semantics (without writing control glyphs).
 * - Pass text through active charset transformers (DEC Special Graphics, NRCS) before writing.
 * - Merge per-cell style (from TextFormater) with per-line style (from LineAttributeHandler).
 * Notes:
 * - This class does not parse escape sequences; it is called by respective handlers.
 * - ScreenBuffer coordinates are 0-based, inclusive.
 */
public class CursorController {

    private final Cursor cursor;
    private final ScreenBuffer screenBuffer;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final DECOMHandler decomHandler;
    private final LineAttributeHandler lineAttributeHandler;

    private ScrollingRegionHandler scrollingRegionHandler;

    // These can be injected later via attachers (see attachTextPipeline()).
    private CharsetSwitchHandler charsetSwitchHandler;
    private NrcsHandler nrcsHandler;
    private TextFormater textFormater;

    private boolean wraparoundEnabled = true;
    private int leftMargin = 0;                  // 0-based, inclusive (effective when DECVLRM is enabled)
    private int rightMargin;                     // 0-based, inclusive

    public CursorController(Cursor cursor,
                            ScreenBuffer screenBuffer,
                            LeftRightMarginModeHandler leftRightMarginModeHandler,
                            DECOMHandler decomHandler,
                            LineAttributeHandler lineAttributeHandler) {
        this.cursor = cursor;
        this.screenBuffer = screenBuffer;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.decomHandler = decomHandler;
        this.lineAttributeHandler = lineAttributeHandler;
        this.rightMargin = screenBuffer.getColumns() - 1;
    }


    /** Attaches text-processing pipeline (charset → NRCS → TextFormater). Safe to call anytime. */
    public void attachTextPipeline(CharsetSwitchHandler charsetSwitchHandler,
                                   NrcsHandler nrcsHandler,
                                   TextFormater textFormater) {
        this.charsetSwitchHandler = charsetSwitchHandler;
        this.nrcsHandler = nrcsHandler;
        this.textFormater = textFormater;
    }

    public void setScrollingRegionHandler(ScrollingRegionHandler handler) {
        this.scrollingRegionHandler = handler;
    }

    /** Sets current left/right margins (0-based, inclusive). */
    public void setLeftRightMargins(int left, int right) {
        this.leftMargin = Math.max(0, left);
        this.rightMargin = Math.min(screenBuffer.getColumns() - 1, right);
    }

    /** Enables or disables DECAWM (auto-wrap). */
    public void setWraparoundModeEnabled(boolean enabled) {
        this.wraparoundEnabled = enabled;
    }

    /**
     * Positions the cursor, applying DECOM and DECVLRM constraints.
     * Input coordinates are 0-based (row, col) relative to full screen or region per DECOM.
     */
    public void setCursorPosition(int row, int col) {
        int maxRows = screenBuffer.getRows();
        int maxCols = screenBuffer.getColumns();

        if (decomHandler.isRelativeCursorMode() && scrollingRegionHandler != null) {
            int top = scrollingRegionHandler.getWindowStartRow();
            int bottom = scrollingRegionHandler.getWindowEndRow();
            int lMargin = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? leftMargin : 0;
            int rMargin = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? rightMargin : maxCols - 1;

            row = clamp(row, bottom - top) + top;
            col = clamp(col, rMargin - lMargin) + lMargin;
        } else {
            int rMargin = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? rightMargin : maxCols - 1;
            row = clamp(row, maxRows - 1);
            col = clamp(col, rMargin);
        }

        cursor.setPosition(row, col);
    }

    /** Moves the cursor to the start column of the current line (respecting DECVLRM). */
    public void moveCursorToLineStart() {
        int startCol = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? leftMargin : 0;
        cursor.setPosition(cursor.getRow(), startCol);
    }

    /** Moves the cursor down by one row within the scrolling region or scrolls the region up. */
    public void moveCursorDown() {
        int nextRow = cursor.getRow() + 1;
        int bottom = (scrollingRegionHandler != null) ? scrollingRegionHandler.getWindowEndRow() : (screenBuffer.getRows() - 1);

        if (nextRow <= bottom) {
            cursor.setPosition(nextRow, cursor.getColumn());
        } else if (scrollingRegionHandler != null) {
            // Scroll within region and keep cursor on the last line
            scrollingRegionHandler.scrollUpWithinRegion();
            cursor.setPosition(bottom, cursor.getColumn());
        } else {
            // Outside of a defined region: clamp to last row
            cursor.setPosition(Math.min(nextRow, screenBuffer.getRows() - 1), cursor.getColumn());
        }
    }

    /**
     * Emits a single "printable" character using the current TextFormater style.
     * - Control characters are handled (CR/LF) and never written as glyphs.
     * - Text is passed through charset/NRCS processors when available.
     */
    public void handleCharacter(String ch) {
        if (ch == null || ch.isEmpty()) return;

        // Handle control chars: CR/LF only. Others are ignored here (processed by upstream handlers).
        if ("\r".equals(ch)) {                  // Carriage Return
            moveCursorToLineStart();
            return;
        }
        if ("\n".equals(ch)) {                  // Line Feed
            moveCursorDown();
            return;
        }

        // Guard against out-of-bounds
        if (isCursorInBounds()) return;

        // Skip ISO control characters (not printable)
        if (isControl(ch)) return;

        // Apply DEC Special Graphics and NRCS, if handlers are provided
        String out = ch;
        if (charsetSwitchHandler != null) {
            out = charsetSwitchHandler.processText(out);
        }
        if (nrcsHandler != null) {
            out = nrcsHandler.processText(out);
        }

        // After mapping, we may have 1+ code points; render each as its own cell
        for (int i = 0; i < out.length(); i++) {
            String glyph = String.valueOf(out.charAt(i));
            writeGlyphAndAdvance(glyph);
        }
    }

    // ---- internals ----

    private void writeGlyphAndAdvance(String glyph) {
        if (isCursorInBounds()) return;

        // Merge per-cell style (from current TextFormater) with per-line style.
        // IMPORTANT: Having a non-null textFormater here is what restores reverse video and other SGR effects.
        String cellStyle = (textFormater != null) ? textFormater.getCurrentStyle() : StyleUtils.getDefaultStyle();
        String lineStyle = lineAttributeHandler.getLineStyle(cursor.getRow());
        String finalStyle = combineStyles(cellStyle, lineStyle);

        screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(glyph, finalStyle));
        advanceAfterWrite();
    }

    private void advanceAfterWrite() {
        int col = cursor.getColumn();

        int effectiveRight = leftRightMarginModeHandler.isLeftRightMarginModeEnabled()
                ? rightMargin : (screenBuffer.getColumns() - 1);

        if (col < effectiveRight) {
            cursor.moveRight();
            return;
        }

        if (!wraparoundEnabled) {
            return;
        }

        // Auto-wrap behavior: move to start of next line (within scrolling region), scrolling if needed
        moveCursorToLineStart();
        moveCursorDown();
    }

    private boolean isControl(String s) {
        return s.codePoints().allMatch(Character::isISOControl);
    }

    private boolean isCursorInBounds() {
        int r = cursor.getRow();
        int c = cursor.getColumn();
        return r < 0 || r >= screenBuffer.getRows() || c < 0 || c >= screenBuffer.getColumns();
    }

    private int clamp(int v, int hi) {
        return Math.max(0, Math.min(hi, v));
    }

    private String combineStyles(String cellStyle, String lineStyle) {
        Map<String, String> m = StyleUtils.parseStyleString(cellStyle);
        m.putAll(StyleUtils.parseStyleString(lineStyle)); // line-level flags override/add
        return StyleUtils.buildStyleString(m);
    }
}
