package org.msv.vt100.core;

import org.msv.vt100.ansiisequences.NrcsHandler;
import org.msv.vt100.ansiisequences.TextFormater;
import org.msv.vt100.ansiisequences.CursorController;
import org.msv.vt100.ansiisequences.EscapeSequenceHandler;
import org.msv.vt100.ansiisequences.CharsetSwitchHandler;

/**
 * InputProcessor – класс для обработки входящего потока символов.
 * Он отвечает за разбиение потока на escape-последовательности, специальные управляющие символы (CR, LF, Backspace)
 * и передачу обычного текста для вывода через CursorController.
 * Все необходимые зависимости (обработчики escape-последовательностей, курсор, обработчики символов, форматирование)
 * передаются через конструктор.
 */
public class InputProcessor {

    private final EscapeSequenceHandler escapeSequenceHandler;
    private final CursorController cursorController;
    private final NrcsHandler nrcsHandler;
    private final CharsetSwitchHandler charsetSwitchHandler;
    private final TextFormater textFormater;
    private final Runnable backspaceHandler;       // обратный вызов для обработки Backspace

    // Внутреннее состояние для разбора escape-последовательностей
    private boolean inEscapeSequence = false;
    private boolean inDCSSequence = false;
    private final StringBuilder escapeSequence = new StringBuilder();

    /**
     * Конструктор InputProcessor.
     *
     * @param escapeSequenceHandler обработчик escape-последовательностей
     * @param cursorController      контроллер курсора (для перемещения и вывода символов)
     * @param nrcsHandler           обработчик национальных наборов символов
     * @param charsetSwitchHandler  обработчик переключения набора символов
     * @param textFormater          объект для форматирования текста (определяет текущий стиль)
     * @param backspaceHandler      обратный вызов для обработки нажатия Backspace (например, метод, реализующий логику удаления символа)
     */
    public InputProcessor(EscapeSequenceHandler escapeSequenceHandler,
                          CursorController cursorController,
                          NrcsHandler nrcsHandler,
                          CharsetSwitchHandler charsetSwitchHandler,
                          TextFormater textFormater,
                          Runnable backspaceHandler) {
        this.escapeSequenceHandler = escapeSequenceHandler;
        this.cursorController = cursorController;
        this.nrcsHandler = nrcsHandler;
        this.charsetSwitchHandler = charsetSwitchHandler;
        this.textFormater = textFormater;
        this.backspaceHandler = backspaceHandler;
    }

    /**
     * Обрабатывает входящий массив символов.
     *
     * @param inputChars массив входящих символов (например, полученных из SSH)
     */
    public void processInput(char[] inputChars) {
        for (int i = 0; i < inputChars.length; i++) {
            char currentChar = inputChars[i];
            if (inDCSSequence) {
                if (currentChar == '\u001B') {
                    if (i + 1 < inputChars.length) {
                        char nextChar = inputChars[i + 1];
                        if (nextChar == '\\') {
                            escapeSequence.append(currentChar).append(nextChar);
                            inDCSSequence = false;
                            escapeSequence.setLength(0);
                            i++;
                        } else {
                            inDCSSequence = false;
                            escapeSequence.setLength(0);
                            inEscapeSequence = true;
                            escapeSequence.append(currentChar);
                        }
                    } else {
                        escapeSequence.append(currentChar);
                    }
                } else {
                    escapeSequence.append(currentChar);
                }
            } else if (inEscapeSequence) {
                escapeSequence.append(currentChar);
                if (escapeSequenceHandler.isEndOfSequence(escapeSequence)) {
                    escapeSequenceHandler.processEscapeSequence(escapeSequence.toString());
                    inEscapeSequence = false;
                    escapeSequence.setLength(0);
                } else if (escapeSequence.toString().startsWith("\u001BP")) {
                    inEscapeSequence = false;
                    inDCSSequence = true;
                }
            } else if (currentChar == '\u001B') {
                inEscapeSequence = true;
                escapeSequence.setLength(0);
                escapeSequence.append(currentChar);
            } else if (currentChar == '\r') {
                cursorController.moveCursorToLineStart();
            } else if (currentChar == '\n') {
                cursorController.moveCursorDown();
            } else if (currentChar == '\b') {
                backspaceHandler.run();
            } else {
                // Обработка обычного символа: сначала через CharsetSwitch и NRCS,
                // затем вывод на экран с использованием текущего стиля из TextFormater.
                String processedChar = nrcsHandler.processText(
                        charsetSwitchHandler.processText(String.valueOf(currentChar))
                );
                addTextToBuffer(processedChar);
            }
        }
    }

    /**
     * Добавляет текст в буфер экрана, вызывая метод handleCharacter у CursorController для каждого символа.
     *
     * @param input текст для добавления
     */
    private void addTextToBuffer(String input) {
        // Если нужна дополнительная обработка, можно добавить вызов методов
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            String currentChar = new String(Character.toChars(codePoint));
            String currentStyle = textFormater.getCurrentStyle();
            cursorController.handleCharacter(currentChar, currentStyle);
            offset += Character.charCount(codePoint);
        }
    }
}