package org.msv.vt100.ansiisequences;

import org.msv.vt100.ansiisequences.LeftRightMarginModeHandler;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.ansiisequences.ScrollingRegionHandler;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErasingSequences {

    private static final Logger logger = LoggerFactory.getLogger(ErasingSequences.class);

    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final ScrollingRegionHandler scrollingRegionHandler;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;

    public ErasingSequences(ScreenBuffer screenBuffer, Cursor cursor, ScrollingRegionHandler scrollingRegionHandler, LeftRightMarginModeHandler leftRightMarginModeHandler) {
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.scrollingRegionHandler = scrollingRegionHandler;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
    }

    // Сохраняет текущую позицию курсора, выполняет операцию очистки и восстанавливает позицию курсора
    private void performClearOperation(Runnable clearOperation) {
        // Сохраняем текущую позицию курсора
        int initialRow = cursor.getRow();
        int initialColumn = cursor.getColumn();

        // Выполняем операцию очистки
        clearOperation.run();

        // Восстанавливаем курсор на сохранённую позицию
        cursor.setPosition(initialRow, initialColumn);
        logger.info("Курсор возвращен на исходную позицию: строка = {}, колонка = {}", initialRow + 1, initialColumn + 1);
    }

    // Очищает экран от текущей позиции курсора до конца экрана
    public void clearFromCursorToEndOfScreen() {
        performClearOperation(() -> {
            int currentRow = cursor.getRow();

            logger.info("Начало очистки экрана от курсора: строка = {}, столбец = {}", currentRow + 1, cursor.getColumn() + 1);

            // Очищаем от текущей позиции до конца текущей строки
            clearFromCursorToEndOfLine();

            // Очищаем все строки после текущей
            int totalRows = screenBuffer.getRows();
            for (int row = currentRow + 1; row < totalRows; row++) {
                clearLine(row);
            }

            logger.info("Завершена очистка экрана от строки {} до конца экрана.", currentRow + 1);
        });
    }

    // Очищает весь экран
    public void clearEntireScreen() {
        performClearOperation(() -> {
            logger.info("Начало полной очистки экрана.");
            int totalRows = screenBuffer.getRows();
            for (int row = 0; row < totalRows; row++) {
                clearLine(row);
            }
            logger.info("Завершена полная очистка экрана.");
        });
    }

    // Очищает всю строку, где в данный момент находится курсор
    public void clearEntireLine() {
        performClearOperation(() -> {
            int currentRow = cursor.getRow();
            logger.info("Очистка всей строки: строка = {}", currentRow + 1);
            clearLine(currentRow);
            logger.info("Завершена очистка строки: строка = {}", currentRow + 1);
        });
    }

    // Очищает от текущей позиции курсора до конца строки
    public void clearFromCursorToEndOfLine() {
        performClearOperation(() -> {
            int currentRow = cursor.getRow();
            int currentColumn = cursor.getColumn();

            int leftMargin = 0;
            int rightMargin = screenBuffer.getColumns() - 1;

            if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
                leftMargin = leftRightMarginModeHandler.getLeftMargin();
                rightMargin = leftRightMarginModeHandler.getRightMargin();
            }

            // Убеждаемся, что текущая колонка не выходит за пределы левого поля
            currentColumn = Math.max(currentColumn, leftMargin);

            logger.info("Очистка строки от позиции курсора до конца строки: строка = {}, начало = столбец {}", currentRow + 1, currentColumn + 1);

            for (int col = currentColumn; col <= rightMargin; col++) {
                // Устанавливаем ячейку в пробел с дефолтным стилем
                screenBuffer.setCell(currentRow, col, new Cell(" ", "-fx-fill: white; -rtfx-background-color: transparent;"));
            }

            logger.info("Завершена очистка строки: строка = {}, от столбца {} до столбца {}", currentRow + 1, currentColumn + 1, rightMargin + 1);
        });
    }

    public void deleteLines(int n) {
        int currentRow = cursor.getRow();
        int topMargin = scrollingRegionHandler.getWindowStartRow();
        int bottomMargin = scrollingRegionHandler.getWindowEndRow();

        // Убедимся, что мы в области прокрутки
        if (currentRow < topMargin || currentRow > bottomMargin) {
            logger.info("Курсор вне области прокрутки. Удаление строк не выполнено.");
            return; // Вне области прокрутки, ничего не делаем
        }

        // Корректируем n, чтобы не выйти за пределы области прокрутки
        n = Math.min(n, bottomMargin - currentRow + 1);

        logger.info("Удаление {} строк начиная с строки {}", n, currentRow + 1);

        // Получаем левые и правые границы
        int leftMargin = 0;
        int rightMargin = screenBuffer.getColumns() - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginModeHandler.getLeftMargin();
            rightMargin = leftRightMarginModeHandler.getRightMargin();
        }

        // Сдвигаем строки вверх внутри области прокрутки и левых/правых полей
        for (int row = currentRow; row <= bottomMargin - n; row++) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                Cell cell = screenBuffer.getCell(row + n, col);
                screenBuffer.setCell(row, col, cell);
            }
        }

        // Очищаем нижние n строк в области прокрутки и левых/правых полей
        for (int row = bottomMargin - n + 1; row <= bottomMargin; row++) {
            clearLine(row, leftMargin, rightMargin);
        }

        logger.info("Удаление строк завершено.");
    }

    private void clearLine(int row) {
        int leftMargin = 0;
        int rightMargin = screenBuffer.getColumns() - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginModeHandler.getLeftMargin();
            rightMargin = leftRightMarginModeHandler.getRightMargin();
        }

        clearLine(row, leftMargin, rightMargin);
    }

    private void clearLine(int row, int leftMargin, int rightMargin) {
        for (int col = leftMargin; col <= rightMargin; col++) {
            screenBuffer.setCell(row, col, new Cell(" ", "-fx-fill: white; -rtfx-background-color: transparent;"));
        }
        logger.info("Завершена очистка : строка = {}, от столбца {} до столбца {}", row + 1, leftMargin + 1, rightMargin + 1);
    }
}