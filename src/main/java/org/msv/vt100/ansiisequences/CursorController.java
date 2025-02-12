package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CursorController {

    private static final Logger logger = LoggerFactory.getLogger(CursorController.class);

    private final Cursor cursor;
    private final ScreenBuffer screenBuffer;
    private ScrollingRegionHandler scrollingRegionHandler;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final DECOMHandler decomHandler;
    private final LineAttributeHandler lineAttributeHandler;

    private boolean wraparoundModeEnabled = true;

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
        this.rightMargin = screenBuffer.getColumns() - 1;
        this.lineAttributeHandler = lineAttributeHandler;
    }

    public void setWraparoundModeEnabled(boolean enabled) {
        this.wraparoundModeEnabled = enabled;
        //logger.info("Режим переноса строк {}", enabled ? "включен" : "отключен");
    }


    public void setLeftRightMargins(int left, int right) {
        this.leftMargin = Math.max(0, left);
        this.rightMargin = Math.min(screenBuffer.getColumns() - 1, right);
        //logger.info("Установлены левые и правые поля: левое = {}, правое = {}", this.leftMargin + 1, this.rightMargin + 1);
    }

    public void setCursorPosition(int row, int column) {
        int maxRows = screenBuffer.getRows();
        int maxColumns = screenBuffer.getColumns();

        if (decomHandler.isRelativeCursorMode()) {
            // Если DECOM включен, позиция интерпретируется относительно области прокрутки и левого поля
            int topMargin = scrollingRegionHandler.getWindowStartRow();
            int bottomMargin = scrollingRegionHandler.getWindowEndRow();
            int leftMargin = this.leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? this.leftMargin : 0;
            int rightMargin = this.leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? this.rightMargin : maxColumns - 1;

            row = Math.max(0, Math.min(row, bottomMargin - topMargin));
            column = Math.max(0, Math.min(column, rightMargin - leftMargin));

            int actualRow = topMargin + row;
            int actualColumn = leftMargin + column;

            cursor.setPosition(actualRow, actualColumn);
            //logger.info("Курсор перемещен в относительном режиме на позицию: строка {}, колонка {}", actualRow + 1, actualColumn + 1);
        } else {
            // Если DECOM отключен, позиция интерпретируется относительно всего экрана
            int leftMargin = this.leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? this.leftMargin : 0;
            int rightMargin = this.leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? this.rightMargin : maxColumns - 1;

            row = Math.max(0, Math.min(row, maxRows - 1));
            column = Math.max(0, Math.min(column, rightMargin));

            cursor.setPosition(row, column);
            //logger.info("Курсор перемещен в абсолютном режиме на позицию: строка {}, колонка {}", row + 1, column + 1);
        }
    }
    public void handleCharacter(String currentChar, String currentStyle) {
        if (cursor.getRow() < 0 || cursor.getRow() >= screenBuffer.getRows()
                || cursor.getColumn() < 0 || cursor.getColumn() >= screenBuffer.getColumns()) {
            return;
        }

        if (currentChar.equals("\n") || currentChar.equals("\r")) {
            String lineStyle = lineAttributeHandler.getLineStyle(cursor.getRow());
            String finalStyle = combineStyles(currentStyle, lineStyle);

            // Use a symbol to represent the control character
            String symbol = currentChar.equals("\n") ? "␤" : "␍";

            // Get the current cell content
            Cell currentCell = screenBuffer.getCell(cursor.getRow(), cursor.getColumn());
            String existingChar = currentCell.getCharacter();

            // Append the symbol to the existing character
            String newChar = existingChar + symbol;

            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(newChar, finalStyle));

            // Optionally, do not move the cursor
            // moveCursorRightAfterCharacter();
        }
        else if (!isControlCharacter(currentChar)) {
            String lineStyle = lineAttributeHandler.getLineStyle(cursor.getRow());
            String finalStyle = combineStyles(currentStyle, lineStyle);

            //logger.debug("Добавление символа '{}' в позицию ({}, {}) с финальным стилем: {}", currentChar, cursor.getRow(), cursor.getColumn(), finalStyle);

            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(currentChar, finalStyle));

            moveCursorRightAfterCharacter();
        }
    }

    private boolean isControlCharacter(String character) {
        return character.codePoints().allMatch(Character::isISOControl);
    }





    private String combineStyles(String cellStyle, String lineStyle) {
        Map<String, String> cellStyleMap = parseStyleString(cellStyle);
        Map<String, String> lineStyleMap = parseStyleString(lineStyle);

        // lineStyle should override cellStyle
        cellStyleMap.putAll(lineStyleMap);

        return buildStyleString(cellStyleMap);
    }

    private Map<String, String> parseStyleString(String style) {
        Map<String, String> styleMap = new HashMap<>();
        String[] styles = style.split(";");
        for (String s : styles) {
            String[] keyValue = s.trim().split(":", 2);
            if (keyValue.length == 2) {
                styleMap.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return styleMap;
    }

    private String buildStyleString(Map<String, String> styleMap) {
        StringBuilder styleBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : styleMap.entrySet()) {
            styleBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
        }
        return styleBuilder.toString().trim();
    }




    private void moveCursorRightAfterCharacter() {
        int maxColumn = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? rightMargin : screenBuffer.getColumns() - 1;

        if (cursor.getColumn() < maxColumn) {
            cursor.moveRight();
        } else {
            if (wraparoundModeEnabled) {
                cursor.moveToColumnStart();
                moveCursorDown();
            }
        }
    }

    public void moveCursorDown() {
        int nextRow = cursor.getRow() + 1;
        int bottomMargin = scrollingRegionHandler.getWindowEndRow();

        if (nextRow <= bottomMargin) {
            cursor.setPosition(nextRow, cursor.getColumn());
        } else {
            // Если достигнут нижний край экрана, выполняем прокрутку
            scrollingRegionHandler.scrollUpWithinRegion();
            cursor.setPosition(bottomMargin, cursor.getColumn());
        }
    }


    public void moveCursorToLineStart() {
        int column = leftRightMarginModeHandler.isLeftRightMarginModeEnabled() ? leftMargin : 0;
        cursor.setPosition(cursor.getRow(), column);
    }

    public void setScrollingRegionHandler(ScrollingRegionHandler scrollingHandler) {
        this.scrollingRegionHandler = scrollingHandler;
    }



}