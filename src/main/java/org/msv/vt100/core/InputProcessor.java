package org.msv.vt100.core;

import org.msv.vt100.ansiisequences.NrcsHandler;
import org.msv.vt100.ansiisequences.TextFormater;
import org.msv.vt100.ansiisequences.CursorController;
import org.msv.vt100.ansiisequences.EscapeSequenceHandler;
import org.msv.vt100.ansiisequences.CharsetSwitchHandler;

/**
 * InputProcessor is a class for processing the incoming stream of characters.
 * It is responsible for splitting the stream into escape sequences, special control characters (CR, LF, Backspace),
 * and passing regular text to the screen via the CursorController.
 * All required dependencies (escape sequence handler, cursor controller, character handlers, text formatter)
 * are provided through the constructor.
 */
public class InputProcessor {

    private final EscapeSequenceHandler escapeSequenceHandler;
    private final CursorController cursorController;
    private final NrcsHandler nrcsHandler;
    private final CharsetSwitchHandler charsetSwitchHandler;
    private final TextFormater textFormater;
    private final Runnable backspaceHandler;       // Callback for handling Backspace

    // Internal state for parsing escape sequences
    private boolean inEscapeSequence = false;
    private boolean inDCSSequence = false;
    private final StringBuilder escapeSequence = new StringBuilder();

    /**
     * Constructs an InputProcessor.
     *
     * @param escapeSequenceHandler the handler for escape sequences.
     * @param cursorController      the controller for cursor movement and character output.
     * @param nrcsHandler           the handler for national character sets.
     * @param charsetSwitchHandler  the handler for switching character sets.
     * @param textFormater          the text formatter that defines the current style.
     * @param backspaceHandler      a callback for handling Backspace (e.g., a method implementing the delete logic).
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
     * Processes an incoming array of characters (e.g., received via SSH).
     *
     * @param inputChars the array of input characters.
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
                // Process a regular character: first via CharsetSwitch and NRCS,
                // then output it to the screen using the current style from TextFormater.
                String processedChar = nrcsHandler.processText(
                        charsetSwitchHandler.processText(String.valueOf(currentChar))
                );
                addTextToBuffer(processedChar);
            }
        }
    }

    /**
     * Adds the given text to the screen buffer by calling the handleCharacter method of the CursorController for each character.
     *
     * @param input the text to add.
     */
    private void addTextToBuffer(String input) {
        // If additional processing is needed, additional method calls can be added here.
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            String currentChar = new String(Character.toChars(codePoint));
            String currentStyle = textFormater.getCurrentStyle();
            cursorController.handleCharacter(currentChar, currentStyle);
            offset += Character.charCount(codePoint);
        }
    }
}
