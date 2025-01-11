package org.msv.vt100.ANSIISequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CursorMovementHandler {

    private static final Logger logger = LoggerFactory.getLogger(CursorMovementHandler.class);

    private final CursorController cursorController;

    // Регулярное выражение для поиска escape-последовательностей перемещения курсора
    private static final Pattern CURSOR_MOVE_PATTERN = Pattern.compile("\\[(\\d+)?;(\\d+)?H");

    public CursorMovementHandler(CursorController cursorController) {
        this.cursorController = cursorController;
    }

    // Метод для обработки последовательности перемещения курсора
    public void handleCursorMovement(String sequence) {
        Matcher matcher = CURSOR_MOVE_PATTERN.matcher(sequence);

        if (matcher.matches()) {
            int row = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) - 1 : 0;
            int column = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) - 1 : 0;

            cursorController.setCursorPosition(row, column);
            logger.info("Запрошено перемещение курсора на позицию: строка {}, колонка {}", row + 1, column + 1);
        } else {
            logger.warn("Последовательность не соответствует шаблону перемещения курсора: {}", sequence);
        }
    }
}