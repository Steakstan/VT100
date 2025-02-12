package org.msv.vt100.core;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.ssh.SSHManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class InputHandler {

    private static final Logger logger = LoggerFactory.getLogger(InputHandler.class);

    private final TerminalApp terminalApp;
    private final SSHManager sshConnector;
    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;


    public InputHandler(TerminalApp terminalApp, SSHManager sshConnector, ScreenBuffer screenBuffer, Cursor cursor) {
        this.terminalApp = terminalApp;
        this.sshConnector = sshConnector;
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
    }

    public void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();

        // Проверка на сочетание клавиш Ctrl+C и Ctrl+V
        if (event.isControlDown()) {
            if (code == KeyCode.C) {
                handleCtrlC();
                event.consume();
            } else if (code == KeyCode.V) {
                handleCtrlV();
                event.consume();
            }
        } else if (isSpecialKey(code)) {
            // Обработка специальных клавиш
            String escapeSequence = getVT220EscapeSequence(code);
            if (escapeSequence != null) {
                sendToSSH(escapeSequence);
                event.consume();
            }
        } else if (code == KeyCode.F5) {
            handleF5KeyPress();}
    }

    public void handleKeyTyped(KeyEvent event) {
        String character = event.getCharacter();

        if (character.isEmpty() || event.isControlDown() || event.isAltDown()) {
            return;
        }

        // Если введён символ новой строки, отправляем его как возврат каретки
        if (character.equals("\n")) {
            sendToSSH("\r");
        } else {
            sendToSSH(character);
        }
        event.consume();
    }



    private void handleCtrlC() {
        // Получаем выделенный текст из терминала
        String selectedText = terminalApp.getSelectedText();

        if (selectedText != null && !selectedText.isEmpty()) {
            // Копируем выделенный текст в буфер обмена
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);

            logger.info("Текст скопирован в буфер обмена: {}", selectedText);
        } else {
            // Отправляем сигнал прерывания на сервер (Ctrl+C)
            sendToSSH("\u0003"); // ASCII ETX (End of Text)
        }
    }

    private void handleCtrlV() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String clipboardText = clipboard.getString();

            // Заменяем все варианты переноса строки на символ возврата каретки
            clipboardText = clipboardText.replace("\r\n", "\r").replace("\n", "\r");

            // Отправляем вставленный текст на сервер
            sendToSSH(clipboardText);
        }
    }




    private boolean isSpecialKey(KeyCode code) {
        return code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.LEFT || code == KeyCode.RIGHT
                || code == KeyCode.DELETE || code == KeyCode.HOME || code == KeyCode.END
                || code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN || isFunctionKey(code);
    }

    private boolean isFunctionKey(KeyCode code) {
        // Проверяем функциональные клавиши F1-F4
        return code == KeyCode.F1 || code == KeyCode.F2 || code == KeyCode.F3 || code == KeyCode.F4;
    }

    private String getVT220EscapeSequence(KeyCode code) {
        return switch (code) {
            case UP -> "\u001B[A"; // ESC [ A
            case DOWN -> "\u001B[B"; // ESC [ B
            case RIGHT -> "\u001B[C"; // ESC [ C
            case LEFT -> "\u001B[D"; // ESC [ D
            case DELETE -> "\u001B[3~"; // ESC [ 3 ~
            case HOME -> "\u001B[H"; // ESC [ H
            case END -> "\u001B[F"; // ESC [ F
            case PAGE_UP -> "\u001B[5~"; // ESC [ 5 ~
            case PAGE_DOWN -> "\u001B[6~"; // ESC [ 6 ~
            case F1 -> "\u001BOP"; // ESC O P
            case F2 -> "\u001BOQ"; // ESC O Q
            case F3 -> "\u001BOR"; // ESC O R
            case F4 -> "\u001BOS"; // ESC O S
            default -> null;
        };
    }

    public void highlightCursorPosition() {
        // Получаем текущую позицию курсора
        int currentRow = cursor.getRow();
        int currentColumn = cursor.getColumn();

        // Сохраняем текущий стиль ячейки
        Cell currentCell = screenBuffer.getCell(currentRow, currentColumn);
        String originalStyle = currentCell.getStyle();

        // Устанавливаем зелёный фон для ячейки курсора
        String highlightStyle = "-fx-fill: е; -rtfx-background-color: green;";
        screenBuffer.setCell(currentRow, currentColumn, new Cell(currentCell.getCharacter(), highlightStyle));



        // Через 1 секунду восстанавливаем оригинальный стиль
        Timeline restoreStyleTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            screenBuffer.setCell(currentRow, currentColumn, new Cell(currentCell.getCharacter(), originalStyle));

        }));
        restoreStyleTimeline.setCycleCount(1);
        restoreStyleTimeline.play();
    }

    private void handleF5KeyPress() {
        // Получаем текущую позицию курсора
        int currentRow = cursor.getRow() + 1; // Строки обычно нумеруются с 1
        int currentColumn = cursor.getColumn() + 1; // Столбцы тоже

        // Форматируем строку с позицией курсора
        String cursorPosition = String.format("Курсор на позиции: Строка %d, Столбец %d", currentRow, currentColumn);

        // Копируем позицию курсора в буфер обмена
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(cursorPosition);
        clipboard.setContent(content);

        // Логирование для отладки
        logger.info("Позиция курсора скопирована в буфер обмена: {}", cursorPosition);

        // Подсветка позиции курсора зелёным цветом
        highlightCursorPosition();
    }

    private void sendToSSH(String data) {
        try {
            sshConnector.send(data);
            logger.debug("Отправлено на SSH: {}", data);
        } catch (IOException e) {
            logger.error("Ошибка при отправке данных через SSH: {}", e.getMessage(), e);
        }
    }
}