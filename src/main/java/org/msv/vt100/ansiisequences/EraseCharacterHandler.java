package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.swing.text.StyleContext.DEFAULT_STYLE;

/**
 * Класс для обработки управляющих последовательностей удаления символов ECH (Erase Character).
 * Последовательность ESC [nX удаляет n символов начиная с текущей позиции курсора.
 */
public class EraseCharacterHandler {
    private static final Logger logger = LoggerFactory.getLogger(EraseCharacterHandler.class);

    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;

    public EraseCharacterHandler(ScreenBuffer screenBuffer, Cursor cursor, LeftRightMarginModeHandler leftRightMarginModeHandler) {
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
    }


    /**
     * Метод для обработки последовательности удаления символов "ESC [nX".
     * @param sequence Строка последовательности, например, "ESC [39X".
     */
    public void handleEraseCharacterSequence(String sequence) {
        // Удаляем ESC, '[', и 'X' для получения количества символов
        try {
            String numberPart = sequence.replace("\u001B", "").replace("[", "").replace("X", "");
            int count = Integer.parseInt(numberPart);
            eraseCharacters(count);
        } catch (NumberFormatException e) {
            logger.error("Ошибка при разборе количества символов для удаления из последовательности: {}", sequence, e);
        }
    }

    /**
     * Удаляет заданное количество символов, начиная с текущей позиции курсора.
     * @param count Количество символов для удаления.
     */
    private void eraseCharacters(int count) {
        int currentRow = cursor.getRow();
        int currentColumn = cursor.getColumn();

        int leftMargin = 0;
        int rightMargin = screenBuffer.getColumns() - 1;

        if (leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
            leftMargin = leftRightMarginModeHandler.getLeftMargin();
            rightMargin = leftRightMarginModeHandler.getRightMargin();
        }

        // Ensure currentColumn is within margins
        currentColumn = Math.max(currentColumn, leftMargin);
        int maxColumn = Math.min(rightMargin, currentColumn + count - 1);

        logger.info("Erasing {} characters from position: row={}, column={}", count, currentRow + 1, currentColumn + 1);

        for (int col = currentColumn; col <= maxColumn; col++) {
            screenBuffer.setCell(currentRow, col, new Cell(" ", DEFAULT_STYLE));
        }
    }

}