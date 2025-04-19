package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;

import java.util.Map;

public class CursorController {

    private final Cursor cursor;
    private final ScreenBuffer screenBuffer;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final DECOMHandler decomHandler;
    private final LineAttributeHandler lineAttributeHandler;

    private ScrollingRegionHandler scrollingRegionHandler;

    private boolean wraparoundEnabled = true;
    private int leftMargin = 0;
    private int rightMargin;

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

    public void setScrollingRegionHandler(ScrollingRegionHandler handler) {
        this.scrollingRegionHandler = handler;
    }

    public void setLeftRightMargins(int left, int right) {
        this.leftMargin = Math.max(0, left);
        this.rightMargin = Math.min(screenBuffer.getColumns() - 1, right);
    }

    public void setWraparoundModeEnabled(boolean enabled) {
        this.wraparoundEnabled = enabled;
    }

    public void setCursorPosition(int row, int col) {
        int maxRows = screenBuffer.getRows();
        int maxCols = screenBuffer.getColumns();

        if (decomHandler.isRelativeCursorMode()) {
            int top = scrollingRegionHandler.getWindowStartRow();
            int bottom = scrollingRegionHandler.getWindowEndRow();
            int lMargin = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? leftMargin : 0;
            int rMargin = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? rightMargin : maxCols - 1;

            row = Math.max(0, Math.min(row, bottom - top));
            col = Math.max(0, Math.min(col, rMargin - lMargin));

            cursor.setPosition(top + row, lMargin + col);
        } else {
            int rMargin = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? rightMargin : maxCols - 1;
            col = Math.min(col, rMargin);
            row = Math.min(row, maxRows - 1);

            cursor.setPosition(row, col);
        }
    }

    public void moveCursorToLineStart() {
        int startCol = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? leftMargin : 0;
        cursor.setPosition(cursor.getRow(), startCol);
    }

    public void moveCursorDown() {
        int nextRow = cursor.getRow() + 1;
        int bottom = scrollingRegionHandler.getWindowEndRow();

        if (nextRow <= bottom) {
            cursor.setPosition(nextRow, cursor.getColumn());
        } else {
            scrollingRegionHandler.scrollUpWithinRegion();
            cursor.setPosition(bottom, cursor.getColumn());
        }
    }

    public void handleCharacter(String ch, String style) {
        if (cursor.getRow() < 0 || cursor.getRow() >= screenBuffer.getRows() ||
                cursor.getColumn() < 0 || cursor.getColumn() >= screenBuffer.getColumns()) {
            return;
        }

        String lineStyle = lineAttributeHandler.getLineStyle(cursor.getRow());
        String finalStyle = combineStyles(style, lineStyle);

        if (ch.equals("\n") || ch.equals("\r")) {
            String symbol = ch.equals("\n") ? "␤" : "␍";
            Cell current = screenBuffer.getCell(cursor.getRow(), cursor.getColumn());
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(),
                    new Cell(current.character() + symbol, finalStyle));
        } else if (!isControlCharacter(ch)) {
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(ch, finalStyle));
            moveCursorRight();
        }
    }

    private void moveCursorRight() {
        int maxCol = leftRightMarginModeHandler.isLeftRightMarginModeEnabled()
                ? rightMargin : screenBuffer.getColumns() - 1;

        if (cursor.getColumn() < maxCol) {
            cursor.moveRight();
        } else if (wraparoundEnabled) {
            cursor.moveToColumnStart();
            moveCursorDown();
        }
    }

    private boolean isControlCharacter(String ch) {
        return ch.codePoints().allMatch(Character::isISOControl);
    }

    private String combineStyles(String cellStyle, String lineStyle) {
        Map<String, String> combined = StyleUtils.parseStyleString(cellStyle);
        combined.putAll(StyleUtils.parseStyleString(lineStyle));
        return StyleUtils.buildStyleString(combined);
    }
}
