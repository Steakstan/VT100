package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScrollingRegionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ScrollingRegionHandler.class);
    private final ScreenBuffer screenBuffer;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler; // Добавлено
    private final LeftRightMarginSequenceHandler leftRightMarginSequenceHandler; // Добавлено

    private int windowStartRow = 0; // Начальная строка области прокрутки (индекс с 0)
    private int windowEndRow;       // Конечная строка области прокрутки (индекс с 0)

    // Стиль по умолчанию для новых ячеек
    private static final String DEFAULT_STYLE = "-fx-fill: white; -rtfx-background-color: transparent;";

    public ScrollingRegionHandler(ScreenBuffer screenBuffer,
                                  LeftRightMarginModeHandler leftRightMarginModeHandler,
                                  LeftRightMarginSequenceHandler leftRightMarginSequenceHandler) {
        this.screenBuffer = screenBuffer;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.leftRightMarginSequenceHandler = leftRightMarginSequenceHandler;
        this.windowEndRow = screenBuffer.getRows() - 1; // Инициализация нижней границы области прокрутки
    }

    /**
     * Устанавливает область прокрутки на основе управляющей последовательности ANSI.
     *
     * @param sequence Последовательность, содержащая границы области прокрутки в формате "[Pt;Pb]r".
     */
    public void setScrollingRegion(String sequence) {
        try {
            // Удаляем ESC, '[', и 'r' для корректного разбора границ
            sequence = sequence.replaceAll("[\\u001B\\[r]", "");
            String[] bounds = sequence.split(";");

            int totalRows = screenBuffer.getRows();

            // Получаем верхнюю и нижнюю границы с проверкой
            windowStartRow = parseBoundary(bounds, 0, 1) - 1;
            windowEndRow = parseBoundary(bounds, 1, totalRows) - 1;

            // Убеждаемся, что границы находятся в допустимом диапазоне
            windowStartRow = Math.max(0, windowStartRow);
            windowEndRow = Math.min(totalRows - 1, windowEndRow);

            if (!isValidScrollingRegion(windowStartRow, windowEndRow)) {
                logger.warn("Область прокрутки вне допустимого диапазона: {}", sequence);
                return;
            }

            logger.info("Область прокрутки установлена: от строки {} до {} и от {} рядка и до {} рядка ", windowStartRow + 1, windowEndRow + 1, leftRightMarginSequenceHandler.getLeftMargin()+1, leftRightMarginSequenceHandler.getRightMargin()+1);

        } catch (NumberFormatException e) {
            logger.error("Неверный формат области прокрутки: {}", sequence, e);
        }
    }

    /**
     * Проверяет, находится ли данная позиция в области прокрутки.
     * @param row Индекс строки (с 0).
     * @param col Индекс столбца (с 0).
     * @return true, если позиция находится в области прокрутки; false иначе.
     */
    public boolean isInScrollingRegion(int row, int col) {
        return row >= windowStartRow && row <= windowEndRow;
    }

    public void scrollDownWithinRegion(int n) {
        int columns = screenBuffer.getColumns();

        // Get current left and right margins
        int leftMargin = 0;
        int rightMargin = columns - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginSequenceHandler.getLeftMargin();
            rightMargin = leftRightMarginSequenceHandler.getRightMargin();
        }

        // Ensure margins are within bounds
        leftMargin = Math.max(0, leftMargin);
        rightMargin = Math.min(columns - 1, rightMargin);

        // Adjust n to not exceed the scrolling region
        n = Math.min(n, windowEndRow - windowStartRow + 1);

        // Scroll down within the scrolling region and margins
        for (int row = windowEndRow; row >= windowStartRow + n; row--) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                Cell prevCell = screenBuffer.getCell(row - n, col);
                screenBuffer.setCell(row, col, prevCell);
            }
        }

        // Clear the top n lines within margins
        for (int row = windowStartRow; row < windowStartRow + n; row++) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                screenBuffer.setCell(row, col, new Cell(" ", DEFAULT_STYLE));
            }
        }

        logger.info("Scrolled down within region from row {} to {}, columns {}-{}, by {} lines", windowStartRow + 1, windowEndRow + 1, leftMargin + 1, rightMargin + 1, n);
    }

    /**
     * Прокручивает содержимое в области прокрутки вверх на одну строку.
     * Учитывает установленные левые и правые поля.
     */
    public void scrollUpWithinRegion() {
        int columns = screenBuffer.getColumns();

        // Получаем текущие значения левых и правых полей
        int leftMargin = 0;
        int rightMargin = columns - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginSequenceHandler.getLeftMargin();
            rightMargin = leftRightMarginSequenceHandler.getRightMargin();
        }

        // Убедимся, что границы полей корректны
        leftMargin = Math.max(0, leftMargin);
        rightMargin = Math.min(columns - 1, rightMargin);

        // Выполняем прокрутку внутри области прокрутки и полей
        for (int row = windowStartRow; row < windowEndRow; row++) {
            for (int col = leftMargin; col <= rightMargin; col++) {
                Cell nextCell = screenBuffer.getCell(row + 1, col);
                screenBuffer.setCell(row, col, nextCell);
            }
        }

        // Очищаем нижнюю строку в пределах полей
        for (int col = leftMargin; col <= rightMargin; col++) {
            screenBuffer.setCell(windowEndRow, col, new Cell(" ", DEFAULT_STYLE));
        }

        logger.info("Прокрутка вверх в области от строки {} до {}, столбцы {}-{}", windowStartRow + 1, windowEndRow + 1, leftMargin + 1, rightMargin + 1);
    }

    private boolean isValidScrollingRegion(int startRow, int endRow) {
        int totalRows = screenBuffer.getRows();
        return startRow >= 0 && endRow < totalRows && startRow <= endRow;
    }

    private int parseBoundary(String[] bounds, int index, int defaultValue) {
        if (bounds.length > index && !bounds[index].isEmpty()) {
            return Integer.parseInt(bounds[index]);
        }
        return defaultValue;
    }

    // Геттеры для границ области прокрутки
    public int getWindowStartRow() {
        return windowStartRow;
    }

    public int getWindowEndRow() {
        return windowEndRow;
    }
}