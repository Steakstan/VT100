package org.msv.vt100.core;

import org.msv.vt100.ansiisequences.NrcsHandler;
import org.msv.vt100.ansiisequences.TextFormater;
import org.msv.vt100.ansiisequences.CursorController;
import org.msv.vt100.ansiisequences.EscapeSequenceHandler;
import org.msv.vt100.ansiisequences.CharsetSwitchHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InputProcessor {

    private static final Logger log = LoggerFactory.getLogger(InputProcessor.class);


    private static final char ESC = '\u001B';
    private static final char ST  = '\\';
    private static final char CR  = '\r';
    private static final char LF  = '\n';
    private static final char BS  = '\b';
    private static final char NUL = '\u0000';
    private static final char SUB = '\u001A';


    private static final int MAX_ESC_LEN = 4096;
    private static final int MAX_DCS_LEN = 16384;

    private final EscapeSequenceHandler escapeSequenceHandler;
    private final CursorController cursorController;
    private final NrcsHandler nrcsHandler;
    private final CharsetSwitchHandler charsetSwitchHandler;
    private final Runnable backspaceHandler;
    private enum Mode { TEXT, ESCAPE, DCS }
    private Mode mode = Mode.TEXT;

    private final StringBuilder escBuf = new StringBuilder(256);
    private final StringBuilder dcsBuf = new StringBuilder(1024);

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
        this.backspaceHandler = backspaceHandler;
        this.cursorController.attachTextPipeline(charsetSwitchHandler, nrcsHandler, textFormater);
    }

    public void processInput(char[] inputChars) {
        for (int i = 0; i < inputChars.length; i++) {
            char ch = inputChars[i];

            switch (mode) {
                case DCS -> {
                    if (ch == ESC) {
                        char next = (i + 1 < inputChars.length) ? inputChars[i + 1] : 0;
                        if (next == ST) {

                            dcsBuf.append(ESC).append(ST);
                            handleDcsSequence(dcsBuf);
                            dcsBuf.setLength(0);
                            mode = Mode.TEXT;
                            i++;
                        } else {

                            handleDcsSequence(dcsBuf);
                            dcsBuf.setLength(0);
                            mode = Mode.ESCAPE;
                            escBuf.setLength(0);
                            escBuf.append(ESC);
                        }
                    } else {
                        dcsBuf.append(ch);
                        if (dcsBuf.length() > MAX_DCS_LEN) {
                            log.warn("DCS-Nutzlast zu lang (>{}) – abgeschnitten und ignoriert", MAX_DCS_LEN);
                            dcsBuf.setLength(0);
                            mode = Mode.TEXT;
                        }
                    }
                }

                case ESCAPE -> {
                    escBuf.append(ch);
                    if (escapeSequenceHandler.isEndOfSequence(escBuf)) {

                        String seq = escBuf.toString();
                        try {
                            escapeSequenceHandler.processEscapeSequence(seq);
                        } catch (Exception ex) {
                            log.warn("Verarbeitung der ESC-Sequenz fehlgeschlagen: '{}': {}", printable(seq), ex.toString());
                        } finally {
                            escBuf.setLength(0);
                            mode = Mode.TEXT;
                        }
                    } else if (startsDcs(escBuf)) {

                        mode = Mode.DCS;
                        dcsBuf.setLength(0);
                        dcsBuf.append(escBuf);
                        escBuf.setLength(0);
                    } else if (escBuf.length() > MAX_ESC_LEN) {

                        log.warn("ESC-Sequenz zu lang (>{}) – Puffer verworfen", MAX_ESC_LEN);
                        escBuf.setLength(0);
                        mode = Mode.TEXT;
                    }
                }

                case TEXT -> {
                    if (ch == ESC) {
                        mode = Mode.ESCAPE;
                        escBuf.setLength(0);
                        escBuf.append(ch);
                        continue;
                    }
                    if (ch == CR) {
                        cursorController.moveCursorToLineStart();
                        continue;
                    }
                    if (ch == LF) {
                        cursorController.moveCursorDown();
                        continue;
                    }
                    if (ch == BS) {
                        backspaceHandler.run();
                        continue;
                    }
                    if (ch == NUL || ch == SUB) {
                        continue;
                    }

                    if (Character.isHighSurrogate(ch) && i + 1 < inputChars.length) {
                        char ch2 = inputChars[i + 1];
                        if (Character.isLowSurrogate(ch2)) {
                            int codePoint = Character.toCodePoint(ch, ch2);
                            writeCodePoint(codePoint);
                            i++;
                            continue;
                        }
                    }

                    writeCodePoint(ch);
                }
            }
        }
    }

    private boolean startsDcs(CharSequence buf) {
        if (buf.length() < 2) return false;
        return buf.charAt(0) == ESC && buf.charAt(1) == 'P';
    }


    private void handleDcsSequence(StringBuilder dcs) {
        if (dcs.isEmpty()) return;
        final int previewLen = Math.min(32, dcs.length());
        String head = printable(dcs.substring(0, previewLen));
        log.debug("DCS empfangen (Länge={}): Kopf='{}' ...", dcs.length(), head);
    }

    private void writeCodePoint(int codePoint) {
        String raw = new String(Character.toChars(codePoint));

        String afterCharset = charsetSwitchHandler.processText(raw);

        String afterNrcs = nrcsHandler.processText(afterCharset);

        for (int off = 0; off < afterNrcs.length(); ) {
            int cp = afterNrcs.codePointAt(off);
            String ch = new String(Character.toChars(cp));
            cursorController.handleCharacter(ch);
            off += Character.charCount(cp);
        }
    }

    private String printable(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                out.append(String.format("\\x%02X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
