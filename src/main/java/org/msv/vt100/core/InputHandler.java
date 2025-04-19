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
 * InputHandler processes user input events from the terminal and handles key events.
 * It supports clipboard operations, special key mappings (including VT220 escape sequences),
 * and highlights the current cursor position.
 */
public class InputHandler {

    private static final Logger logger = LoggerFactory.getLogger(InputHandler.class);

    private final TerminalApp terminalApp;
    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;

    /**
     * Constructs an InputHandler with the given TerminalApp, SSHManager, ScreenBuffer, and Cursor.
     *
     * @param terminalApp  the main terminal application.
     * @param screenBuffer the screen buffer.
     * @param cursor       the terminal cursor.
     */
    public InputHandler(TerminalApp terminalApp, ScreenBuffer screenBuffer, Cursor cursor) {
        this.terminalApp = terminalApp;
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
    }

    /**
     * Handles key pressed events.
     *
     * @param event the KeyEvent to process.
     */
    public void handleKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();

        // Check for key combinations Ctrl+C and Ctrl+V
        if (event.isControlDown()) {
            if (code == KeyCode.C) {
                handleCtrlC();
                event.consume();
            } else if (code == KeyCode.V) {
                handleCtrlV();
                event.consume();
            }
        } else if (isSpecialKey(code)) {
            // Process special keys
            String escapeSequence = getVT220EscapeSequence(code);
            if (escapeSequence != null) {
                sendToSSH(escapeSequence);
                event.consume();
            }
        } else if (code == KeyCode.F5) {
            handleF5KeyPress();
        }
    }

    /**
     * Handles key typed events.
     *
     * @param event the KeyEvent to process.
     */
    public void handleKeyTyped(KeyEvent event) {
        String character = event.getCharacter();

        if (character.isEmpty() || event.isControlDown() || event.isAltDown()) {
            return;
        }

        // If a newline character is entered, send it as a carriage return.
        if (character.equals("\n")) {
            sendToSSH("\r");
        } else {
            sendToSSH(character);
        }
        event.consume();
    }

    /**
     * Handles Ctrl+C key press.
     * Copies selected text to the clipboard, or sends an interrupt signal if no text is selected.
     */
    private void handleCtrlC() {
        // Get the selected text from the terminal
        String selectedText = terminalApp.getSelectedText();

        if (selectedText != null && !selectedText.isEmpty()) {
            // Copy the selected text to the clipboard
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(selectedText);
            clipboard.setContent(content);

            logger.info("Text wurde in die Zwischenablage kopiert: {}", selectedText);
        } else {
            // Send an interrupt signal to the server (Ctrl+C)
            sendToSSH("\u0003"); // ASCII ETX (End of Text)
        }
    }

    /**
     * Handles Ctrl+V key press.
     * Pastes text from the clipboard and sends it to the SSH server.
     */
    private void handleCtrlV() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            String clipboardText = clipboard.getString();

            // Replace all newline variants with a carriage return
            clipboardText = clipboardText.replace("\r\n", "\r").replace("\n", "\r");

            // Send the pasted text to the server
            sendToSSH(clipboardText);
        }
    }

    /**
     * Checks if the given key is a special key.
     *
     * @param code the KeyCode to check.
     * @return true if it is a special key; false otherwise.
     */
    private boolean isSpecialKey(KeyCode code) {
        return code == KeyCode.UP || code == KeyCode.DOWN || code == KeyCode.LEFT || code == KeyCode.RIGHT
                || code == KeyCode.DELETE || code == KeyCode.HOME || code == KeyCode.END
                || code == KeyCode.PAGE_UP || code == KeyCode.PAGE_DOWN || isFunctionKey(code);
    }

    /**
     * Checks if the given key is one of the function keys F1-F4.
     *
     * @param code the KeyCode to check.
     * @return true if it is F1, F2, F3, or F4; false otherwise.
     */
    private boolean isFunctionKey(KeyCode code) {
        return code == KeyCode.F1 || code == KeyCode.F2 || code == KeyCode.F3 || code == KeyCode.F4;
    }

    /**
     * Returns the VT220 escape sequence corresponding to the given special key.
     *
     * @param code the KeyCode for the key.
     * @return the corresponding escape sequence, or null if none.
     */
    private String getVT220EscapeSequence(KeyCode code) {
        return switch (code) {
            case UP -> "\u001B[A";      // ESC [ A
            case DOWN -> "\u001B[B";    // ESC [ B
            case RIGHT -> "\u001B[C";   // ESC [ C
            case LEFT -> "\u001B[D";    // ESC [ D
            case DELETE -> "\u001B[3~"; // ESC [ 3 ~
            case HOME -> "\u001B[H";    // ESC [ H
            case END -> "\u001B[F";     // ESC [ F
            case PAGE_UP -> "\u001B[5~";// ESC [ 5 ~
            case PAGE_DOWN -> "\u001B[6~"; // ESC [ 6 ~
            case F1 -> "\u001BOP";      // ESC O P
            case F2 -> "\u001BOQ";      // ESC O Q
            case F3 -> "\u001BOR";      // ESC O R
            case F4 -> "\u001BOS";      // ESC O S
            default -> null;
        };
    }

    /**
     * Highlights the current cursor position by changing the cell's background color to green.
     * After one second, the original style is restored.
     */
    public void highlightCursorPosition() {
        // Get the current cursor position
        int currentRow = cursor.getRow();
        int currentColumn = cursor.getColumn();

        // Save the current cell style
        Cell currentCell = screenBuffer.getCell(currentRow, currentColumn);
        String originalStyle = currentCell.style();

        // Set a green background for the cursor cell
        String highlightStyle = "-fx-fill: е; -rtfx-background-color: green;";
        screenBuffer.setCell(currentRow, currentColumn, new Cell(currentCell.character(), highlightStyle));

        // Restore the original style after 1 second
        Timeline restoreStyleTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            screenBuffer.setCell(currentRow, currentColumn, new Cell(currentCell.character(), originalStyle));
        }));
        restoreStyleTimeline.setCycleCount(1);
        restoreStyleTimeline.play();
    }

    /**
     * Handles the F5 key press.
     * Copies the current cursor position to the clipboard and highlights it.
     */
    private void handleF5KeyPress() {
        // Get the current cursor position (rows and columns are 1-indexed)
        int currentRow = cursor.getRow() + 1;
        int currentColumn = cursor.getColumn() + 1;

        // Format the string with the cursor position
        String cursorPosition = String.format("Cursorposition: Zeile %d, Spalte %d", currentRow, currentColumn);

        // Copy the cursor position to the clipboard
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(cursorPosition);
        clipboard.setContent(content);

        // Log for debugging
        logger.info("Cursorposition wurde in die Zwischenablage kopiert: {}", cursorPosition);

        // Highlight the cursor position in green
        highlightCursorPosition();
    }



    /**
     * Sends the specified data to the SSH server.
     *
     * @param data the string data to send.
     */
    private void sendToSSH(String data) {
        SSHManager manager = terminalApp.getSSHManager();
        if (manager != null) {
            try {
                manager.send(data);
                logger.debug("Gesendet an SSH: {}", data);
            } catch (IOException e) {
                logger.error("Fehler beim Senden der Daten über SSH: {}", e.getMessage(), e);
            }
        } else {
            logger.warn("SSHManager ist nicht gesetzt. Daten wurden nicht gesendet: {}", data);
        }
    }
}
