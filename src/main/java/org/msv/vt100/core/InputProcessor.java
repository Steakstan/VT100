package org.msv.vt100.core;

import org.msv.vt100.ansiisequences.NrcsHandler;
import org.msv.vt100.ansiisequences.TextFormater;
import org.msv.vt100.ansiisequences.CursorController;
import org.msv.vt100.ansiisequences.EscapeSequenceHandler;
import org.msv.vt100.ansiisequences.CharsetSwitchHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputProcessor:
 * - Стриминговый парсинг входящих символов.
 * - Поддержка ESC-семейств, включая DCS (ESC P ... ESC \) с явным роутингом/игнором.
 * - CR/LF/BS/SUB/NUL обработка по терминальной семантике.
 * - Обычный текст проходит через CharsetSwitch → NRCS → TextFormater → CursorController.
 * - Защита от слишком длинных/некорректных последовательностей (лимит длины).
 */
public class InputProcessor {

    private static final Logger log = LoggerFactory.getLogger(InputProcessor.class);

    // Управляющие символы
    private static final char ESC = '\u001B';
    private static final char ST  = '\\';      // 7-битное ST в виде ESC \
    private static final char BEL = '\u0007';  // Альтернативный терминатор для некоторых OSC/DCS (не используем как ST здесь)
    private static final char CR  = '\r';
    private static final char LF  = '\n';
    private static final char BS  = '\b';
    private static final char NUL = '\u0000';
    private static final char SUB = '\u001A';  // часто используется для отмены ESC-последовательности

    // Ограничители
    private static final int MAX_ESC_LEN = 4096; // предохранитель от разрастания буфера
    private static final int MAX_DCS_LEN = 16384;

    private final EscapeSequenceHandler escapeSequenceHandler;
    private final CursorController cursorController;
    private final NrcsHandler nrcsHandler;
    private final CharsetSwitchHandler charsetSwitchHandler;
    private final TextFormater textFormater;
    private final Runnable backspaceHandler;

    // Состояние парсера
    private enum Mode { TEXT, ESCAPE, DCS }
    private Mode mode = Mode.TEXT;

    private final StringBuilder escBuf = new StringBuilder(256);  // буфер для ESC/CSI/OSC/etc.
    private final StringBuilder dcsBuf = new StringBuilder(1024); // буфер для DCS payload + wrap

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
        this.cursorController.attachTextPipeline(charsetSwitchHandler, nrcsHandler, textFormater);
    }

    /**
     * Главный вход: покодово обрабатываем массив символов.
     */
    public void processInput(char[] inputChars) {
        for (int i = 0; i < inputChars.length; i++) {
            char ch = inputChars[i];

            switch (mode) {
                case DCS -> {
                    // DCS завершается ESC \
                    if (ch == ESC) {
                        char next = (i + 1 < inputChars.length) ? inputChars[i + 1] : 0;
                        if (next == ST) {
                            // Включаем завершающую пару в буфер и закрываем DCS
                            dcsBuf.append(ESC).append(ST);
                            handleDcsSequence(dcsBuf);
                            dcsBuf.setLength(0);
                            mode = Mode.TEXT;
                            i++; // потребили \ после ESC
                            continue;
                        } else {
                            // Встречен ESC, но не ST → по спецификации часто трактуется как отмена DCS.
                            // Завершим текущую DCS и перекинем в обработку ESC-последовательностей.
                            handleDcsSequence(dcsBuf);
                            dcsBuf.setLength(0);
                            mode = Mode.ESCAPE;
                            escBuf.setLength(0);
                            escBuf.append(ESC);
                            // НЕ увеличиваем i: текущий символ ESC уже учтён (в escBuf).
                            continue;
                        }
                    } else {
                        dcsBuf.append(ch);
                        // Safety: ограничитель размера
                        if (dcsBuf.length() > MAX_DCS_LEN) {
                            log.warn("DCS payload too long (>{}) — truncated & ignored", MAX_DCS_LEN);
                            dcsBuf.setLength(0);
                            mode = Mode.TEXT;
                        }
                    }
                }

                case ESCAPE -> {
                    escBuf.append(ch);
                    if (escapeSequenceHandler.isEndOfSequence(escBuf)) {
                        // Полная ESC-последовательность собрана
                        String seq = escBuf.toString();
                        try {
                            escapeSequenceHandler.processEscapeSequence(seq);
                        } catch (Exception ex) {
                            log.warn("ESC sequence processing failed: '{}': {}", printable(seq), ex.toString());
                        } finally {
                            escBuf.setLength(0);
                            mode = Mode.TEXT;
                        }
                    } else if (startsDcs(escBuf)) {
                        // Переключаемся на DCS: переносим пролог в dcsBuf
                        mode = Mode.DCS;
                        dcsBuf.setLength(0);
                        dcsBuf.append(escBuf); // хранить полезно для диагностики/будущей маршрутизации
                        escBuf.setLength(0);
                    } else if (escBuf.length() > MAX_ESC_LEN) {
                        // Слишком длинно — сброс
                        log.warn("ESC sequence too long (>{}) — dropping buffer", MAX_ESC_LEN);
                        escBuf.setLength(0);
                        mode = Mode.TEXT;
                    }
                }

                case TEXT -> {
                    // Быстрые контролы
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
                        // SUB можно трактовать как «отмена последовательности», но мы уже в TEXT: просто игнор
                        continue;
                    }

                    // Обычный символ(ы) — прогон через Charset/NRCS и на экран с текущим стилем
                    // Поддержка суррогатных пар: переводим в кодпоинты.
                    if (Character.isHighSurrogate(ch) && i + 1 < inputChars.length) {
                        char ch2 = inputChars[i + 1];
                        if (Character.isLowSurrogate(ch2)) {
                            int codePoint = Character.toCodePoint(ch, ch2);
                            writeCodePoint(codePoint);
                            i++; // потребили low surrogate
                            continue;
                        }
                    }
                    // BMP символ
                    writeCodePoint(ch);
                }
            }
        }
    }

    /* ============================== Helpers ============================== */

    /**
     * DCS стартует с ESC P (0x1B 'P').
     */
    private boolean startsDcs(CharSequence buf) {
        if (buf.length() < 2) return false;
        return buf.charAt(0) == ESC && buf.charAt(1) == 'P';
    }

    /**
     * Обработка DCS: сейчас по умолчанию игнор (но логируем пролог и длину).
     * Если потребуется — здесь можно интегрировать обработчики (DECRQSS, Sixel и т.п.).
     */
    private void handleDcsSequence(StringBuilder dcs) {
        if (dcs.length() == 0) return;
        // Пример диагностики (без спама): логируем только заголовок и длину
        final int previewLen = Math.min(32, dcs.length());
        String head = printable(dcs.substring(0, previewLen));
        log.debug("DCS received (len={}): head='{}' ...", dcs.length(), head);
        // TODO: роутинг DCS при необходимости
        // NOP сейчас
    }

    /**
     * Пишем один Unicode кодпоинт в буфер экрана.
     */
    private void writeCodePoint(int codePoint) {
        String raw = new String(Character.toChars(codePoint));
        // 1) переключение набора символов (G0/G1/…)
        String afterCharset = charsetSwitchHandler.processText(raw);
        // 2) NRCS преобразования
        String afterNrcs = nrcsHandler.processText(afterCharset);
        // 3) Текущий стиль
        String style = textFormater.getCurrentStyle();

        // 4) Пошлём посимвольно в CursorController (на случай, если afterNrcs > 1 символа)
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
