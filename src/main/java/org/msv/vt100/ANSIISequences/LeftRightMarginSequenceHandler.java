package org.msv.vt100.ANSIISequences;

import org.msv.vt100.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeftRightMarginSequenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeftRightMarginSequenceHandler.class);

    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final CursorController cursorController;
    private final ScreenBuffer screenBuffer;

    private int leftMargin = 0;  // Значения левого и правого полей
    private int rightMargin;

    public LeftRightMarginSequenceHandler(
            LeftRightMarginModeHandler leftRightMarginModeHandler,
            CursorController cursorController,
            ScreenBuffer screenBuffer) {
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.cursorController = cursorController;
        this.screenBuffer = screenBuffer;
        this.rightMargin = screenBuffer.getColumns() - 1; // Инициализируем правое поле
    }

    public void handleLeftRightMarginSequence(String sequence) {
        try {
            // Удаляем '[', 's' и разделяем параметры
            String[] params = sequence.replace("[", "").replace("s", "").split(";");

            int leftParam = Integer.parseInt(params[0]);
            int rightParam = Integer.parseInt(params[1]);

            int left = leftParam > 0 ? leftParam - 1 : 0; // Если leftParam == 0, оставляем 0
            int right = rightParam > 0 ? rightParam - 1 : screenBuffer.getColumns() - 1; // Если rightParam == 0, устанавливаем максимальное значение

            // Проверяем, что left и right находятся в допустимых пределах
            left = Math.max(0, left);
            right = Math.min(screenBuffer.getColumns() - 1, right);

            // Сохраняем значения полей
            this.leftMargin = left;
            this.rightMargin = right;

            // Устанавливаем поля в соответствующих обработчиках
            leftRightMarginModeHandler.setLeftRightMargins(left, right);
            cursorController.setLeftRightMargins(left, right);

            logger.info("Левые и правые поля установлены: leftMargin={}, rightMargin={}", left + 1, right + 1);

        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            logger.error("Неверный формат последовательности установки полей: {}", sequence, e);
        }
    }

    public int getLeftMargin() {
        return leftMargin;
    }

    public int getRightMargin() {
        return rightMargin;
    }
}
