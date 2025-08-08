package org.msv.vt100.core;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.ssh.SSHManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * InputHandler:
 * - Обрабатывает KeyPressed/KeyTyped.
 * - Ctrl+C: копирование выделенного или отправка ETX (0x03).
 * - Ctrl+V: вставка (переводит \n/\r\n в \r).
 * - Спецклавиши: стрелки, Delete/Home/End/PageUp/PageDown, F1–F12 (VT/ANSI).
 * - Backspace по KeyPressed (надежно на всех платформах).
 * - F5: копирует позицию курсора и подсвечивает ячейку (нормализованный стиль fill/background).
 */
public class InputHandler {

    private static final Logger logger = LoggerFactory.getLogger(InputHandler.class);

    private final TerminalApp terminalApp;
    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;

    public InputHandler(TerminalApp terminalApp, ScreenBuffer screenBuffer, Cursor cursor) {
        this.terminalApp = terminalApp;
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
    }

    /* ========================= KeyPressed ========================= */

    public void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();

        // Ctrl+комбинации
        if (event.isControlDown()) {
            if (code == KeyCode.C) {
                handleCtrlC();
                event.consume();
                return;
            }
            if (code == KeyCode.V) {
                handleCtrlV();
                event.consume();
                return;
            }
        }

        // Спецклавиши → escape sequences
        String esc = getEscapeSequence(code);
        if (esc != null) {
            sendToSSH(esc);
            event.consume();
            return;
        }

        // F5 — сервисная
        if (code == KeyCode.F5) {
            handleF5KeyPress();
            event.consume();
        }
    }

    /* ========================= KeyTyped ========================= */

    public void handleKeyTyped(KeyEvent event) {
        String ch = event.getCharacter();
        if (ch.isEmpty() || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        // Перевод строки → CR
        if ("\n".equals(ch)) {
            sendToSSH("\r");
        } else {
            sendToSSH(ch);
        }
        event.consume();
    }

    /* ========================= Комбинации ========================= */

    private void handleCtrlC() {
        String selectedText = terminalApp.getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText);
            Clipboard.getSystemClipboard().setContent(content);
            logger.info("Text in die Zwischenablage kopiert ({} Zeichen).", selectedText.length());
        } else {
            // ETX (Interrupt)
            sendToSSH("\u0003");
        }
    }

    private void handleCtrlV() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasString()) return;

        String text = clipboard.getString();
        if (text == null || text.isEmpty()) return;

        // Нормализация переводов строк: \r\n / \n → \r
        text = text.replace("\r\n", "\r").replace("\n", "\r");
        sendToSSH(text);
    }

    /* ========================= Подсветка курсора (F5) ========================= */

    private void handleF5KeyPress() {
        int row1 = cursor.getRow() + 1;
        int col1 = cursor.getColumn() + 1;
        String cursorPosition = String.format("Cursorposition: Zeile %d, Spalte %d", row1, col1);

        ClipboardContent content = new ClipboardContent();
        content.putString(cursorPosition);
        Clipboard.getSystemClipboard().setContent(content);
        logger.info("Cursorposition kopiert: {}", cursorPosition);

        highlightCursorPosition();
    }

    /**
     * Подсветка текущей ячейки курсора на 1 секунду. Используем
     * нормализованные ключи стиля: "fill" и "background".
     */
    public void highlightCursorPosition() {
        int r = cursor.getRow();
        int c = cursor.getColumn();

        Cell current = screenBuffer.getCell(r, c);
        String originalStyle = current.style();

        String highlightStyle = "fill: black; background: green;";
        screenBuffer.setCell(r, c, new Cell(current.character(), highlightStyle));

        Timeline restore = new Timeline(new KeyFrame(
                Duration.seconds(1),
                e -> screenBuffer.setCell(r, c, new Cell(current.character(), originalStyle))
        ));
        restore.setCycleCount(1);
        restore.play();
    }

    /* ========================= Escape sequences ========================= */

    /**
     * Карта распространённых VT/ANSI escape-последовательностей.
     * Стрелки/навигация — CSI.
     * Function keys — совместимые коды (xterm/VT220-стиль).
     */
    private String getEscapeSequence(KeyCode code) {
        return switch (code) {
            // Навигация
            case UP       -> "\u001B[A";
            case DOWN     -> "\u001B[B";
            case RIGHT    -> "\u001B[C";
            case LEFT     -> "\u001B[D";
            case INSERT   -> "\u001B[2~";
            case DELETE   -> "\u001B[3~";
            case HOME     -> "\u001B[H";    // альтернативно: \u001B[1~
            case END      -> "\u001B[F";    // альтернативно: \u001B[4~
            case PAGE_UP  -> "\u001B[5~";
            case PAGE_DOWN-> "\u001B[6~";

            // Функциональные клавиши — xterm/VT220 номера
            case F1  -> "\u001BOP";   // SS3 P
            case F2  -> "\u001BOQ";   // SS3 Q
            case F3  -> "\u001BOR";   // SS3 R
            case F4  -> "\u001BOS";   // SS3 S
            case F5  -> "\u001B[15~";
            case F6  -> "\u001B[17~";
            case F7  -> "\u001B[18~";
            case F8  -> "\u001B[19~";
            case F9  -> "\u001B[20~";
            case F10 -> "\u001B[21~";
            case F11 -> "\u001B[23~";
            case F12 -> "\u001B[24~";

            default -> null;
        };
    }

    /* ========================= SSH ========================= */

    private void sendToSSH(String data) {
        SSHManager manager = terminalApp.getSSHManager();
        if (manager == null) {
            logger.warn("SSHManager nicht gesetzt. Daten nicht gesendet: {}", summarize(data));
            return;
        }
        try {
            manager.send(data);
            // Не логируем полный ввод, чтобы не засветить пароли
            logger.debug("An SSH gesendet ({} Zeichen).", data.length());
        } catch (IOException e) {
            logger.error("Fehler beim Senden an SSH: {}", e.getMessage(), e);
        }
    }

    private String summarize(String s) {
        if (s == null) return "null";
        return s.length() <= 16 ? s : (s.substring(0, 16) + "…");
    }
}
