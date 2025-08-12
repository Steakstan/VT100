package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EscapeSequenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(EscapeSequenceHandler.class);

    private final ErasingSequences erasingSequences;
    private final CursorMovementHandler cursorMovementHandler;
    private final DECOMHandler decomHandler;
    private final ScrollingRegionHandler scrollingRegionHandler;
    private final CharsetSwitchHandler charsetSwitchHandler;
    private final CursorVisibilityManager cursorVisibilityManager;
    private final TextFormater textFormater;
    private final NrcsHandler nrcsHandler;
    private final CursorController cursorController;
    private final LeftRightMarginModeHandler leftRightMarginModeHandler;
    private final LeftRightMarginSequenceHandler leftRightMarginSequenceHandler;
    private final DECCRASequenceHandler deccraSequenceHandler;
    private final EraseCharacterHandler eraseCharacterHandler;
    private final FillRectangularAreaHandler fillRectangularAreaHandler;
    private final Cursor cursor;
    private final LineAttributeHandler lineAttributeHandler;
    private final InsertLineHandler insertLineHandler;

    public EscapeSequenceHandler(
            ErasingSequences erasingSequences,
            CursorMovementHandler cursorMovementHandler,
            DECOMHandler decomHandler,
            ScrollingRegionHandler scrollingRegionHandler,
            CharsetSwitchHandler charsetSwitchHandler,
            CursorVisibilityManager cursorVisibilityManager,
            TextFormater textFormater,
            NrcsHandler nrcsHandler,
            CursorController cursorController,
            LeftRightMarginModeHandler leftRightMarginModeHandler,
            CopyRectangularAreaHandler copyRectangularAreaHandler,
            EraseCharacterHandler eraseCharacterHandler,
            FillRectangularAreaHandler fillRectangularAreaHandler,
            Cursor cursor,
            LineAttributeHandler lineAttributeHandler,
            ScreenBuffer screenBuffer,
            LeftRightMarginSequenceHandler leftRightMarginSequenceHandler,
            InsertLineHandler insertLineHandler) {

        this.erasingSequences = erasingSequences;
        this.cursorMovementHandler = cursorMovementHandler;
        this.decomHandler = decomHandler;
        this.scrollingRegionHandler = scrollingRegionHandler;
        this.charsetSwitchHandler = charsetSwitchHandler;
        this.cursorVisibilityManager = cursorVisibilityManager;
        this.textFormater = textFormater;
        this.nrcsHandler = nrcsHandler;
        this.cursorController = cursorController;
        this.leftRightMarginModeHandler = leftRightMarginModeHandler;
        this.eraseCharacterHandler = eraseCharacterHandler;
        this.fillRectangularAreaHandler = fillRectangularAreaHandler;
        this.cursor = cursor;
        this.lineAttributeHandler = lineAttributeHandler;
        this.deccraSequenceHandler = new DECCRASequenceHandler(
                copyRectangularAreaHandler,
                screenBuffer
        );
        this.leftRightMarginSequenceHandler = leftRightMarginSequenceHandler;
        this.insertLineHandler = insertLineHandler;
        this.erasingSequences.setTextFormater(textFormater);
        this.insertLineHandler.setTextFormater(textFormater);
        this.scrollingRegionHandler.setTextFormater(textFormater);
        this.eraseCharacterHandler.setTextFormater(textFormater);
        this.fillRectangularAreaHandler.setTextFormater(textFormater);
        this.fillRectangularAreaHandler.setLeftRightMarginModeHandler(leftRightMarginModeHandler);
        this.fillRectangularAreaHandler.setScrollingRegionHandler(scrollingRegionHandler);
    }
    public void processEscapeSequence(String sequence) {
        if (sequence.startsWith("\u001B")) {
            sequence = sequence.substring(1);
        }

        logger.info("Escape-Sequenz empfangen: {}", sequence);

        switch (sequence) {
            case "[?6l":
                decomHandler.disableRelativeCursorMode();
                break;
            case "[?6h":
                decomHandler.enableRelativeCursorMode();
                break;
            case "(K":
                charsetSwitchHandler.switchToASCIICharset();
                break;
            case "(0":
                charsetSwitchHandler.switchToGraphicsCharset();
                break;
            case "[?25h":
                cursorVisibilityManager.showCursor();
                break;
            case "[?25l":
                cursorVisibilityManager.hideCursor();
                break;
            case "[0J":
                erasingSequences.clearFromCursorToEndOfScreen();
                break;
            case "[2J":
                erasingSequences.clearEntireScreen();
                break;
            case "[2K":
                erasingSequences.clearEntireLine();
                break;
            case "[0K":
                erasingSequences.clearFromCursorToEndOfLine();
                break;
            case "[?7h":
                cursorController.setWraparoundModeEnabled(false);
                logger.info("Automatischer Zeilenumbruchmodus aktiviert.");
                break;
            case "[?7l":
                cursorController.setWraparoundModeEnabled(true);
                logger.info("Automatischer Zeilenumbruchmodus deaktiviert.");
                break;
            case "[?42h":
                nrcsHandler.enableNrcsMode(NrcsHandler.NrcsMode.GERMAN);
                break;
            case "[?42l":
                nrcsHandler.disableNrcsMode();
                break;
            case ">":
                break;
            case "[?68l":
                break;
            case "[2*x":
                break;
            case "[?69h":
                leftRightMarginModeHandler.enableLeftRightMarginMode();
                break;
            case "[?69l":
                leftRightMarginModeHandler.disableLeftRightMarginMode();
                break;
            case "#6":
                lineAttributeHandler.setDoubleWidthLine(cursor.getRow(),true);
                logger.info("Doppelte Breite für Zeile {} gesetzt", cursor.getRow() + 1);
                break;
            case "#5":
                break;
            default:
                if (sequence.matches("\\[\\d+;\\d+r")) {
                    scrollingRegionHandler.setScrollingRegion(sequence);

                    int top = scrollingRegionHandler.getWindowStartRow();
                    int left = leftRightMarginModeHandler.isLeftRightMarginModeEnabled()
                            ? leftRightMarginModeHandler.getLeftMargin()
                            : 0;

                    cursor.setPosition(top, left);
                    logger.info("Cursor in die linke obere Ecke des Bereichs verschoben: Zeile={}, Spalte={}", top + 1, left + 1);
                    return;
                }

                else if (sequence.matches("\\[\\d+;\\d+H")) {
                    cursorMovementHandler.handleCursorMovement(sequence);
                }
                else if (sequence.matches("\\[([0-9;]*?)\\$x")) {
                    fillRectangularAreaHandler.handleDECFRA(sequence);
                    return;
                }

                else if (sequence.matches("\\[(\\d+)M")) {
                    Matcher matcher = Pattern.compile("\\[(\\d+)M").matcher(sequence);
                    if (matcher.matches()) {
                        int n = Integer.parseInt(matcher.group(1));
                        erasingSequences.deleteLines(n);
                        return;
                    }
                    break;
                }
                else if (sequence.matches("\\[(\\d+(;\\d+)*)?m")) {
                    textFormater.handleSgrSequence(sequence);
                }
                else if (sequence.matches("\\[\\d+;\\d+s")) {
                    leftRightMarginSequenceHandler.handleLeftRightMarginSequence(sequence);

                    int top = scrollingRegionHandler.getWindowStartRow();
                    int left = leftRightMarginModeHandler.isLeftRightMarginModeEnabled()
                            ? leftRightMarginModeHandler.getLeftMargin()
                            : 0;

                    cursor.setPosition(top, left);
                    logger.info("Cursor nach Setzen der Ränder in die linke obere Ecke verschoben: Zeile={}, Spalte={}", top + 1, left + 1);
                }

                else if (sequence.matches("\\[([0-9;]*?)\\$v")) {
                    deccraSequenceHandler.handleDECCRA(sequence);
                }
                else if (sequence.matches("\\[\\d+X")) {
                    eraseCharacterHandler.handleEraseCharacterSequence(sequence);
                }
                else if (sequence.matches("\\[\\d*L")) {
                    insertLineHandler.handleInsertLine(sequence);
                }


                else {
                    logger.warn("Unbekannte oder nicht unterstützte Escape-Sequenz: {}", sequence);              }
                break;
        }
    }

    public boolean isEndOfSequence(StringBuilder currentSequence) {
        String currentSeqString = currentSequence.toString();

        if (currentSeqString.matches("\\u001B\\[\\d+;\\d+r")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[([0-9;]*?)\\$x")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[\\d*L")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[\\d+X")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[\\d+;\\d+H")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[\\d+K")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[(\\d+)M")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[(\\d+(;\\d+)*)?m")) {
            return true;
        }
        if (currentSeqString.equals("\u001B[?69h") || currentSeqString.equals("\u001B[?69l")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[\\d+;\\d+s")) {
            return true;
        }
        if (currentSeqString.matches("\\u001B\\[[0-9;]*\\$v")) {
            return true;
        }

        return switch (currentSeqString) {
            case "\u001B[0J", "\u001B[?6l","\u001B[?6h", "\u001B(K", "\u001B[?25h", "\u001B[?25l","\u001B[?42h", "\u001B[?68l", "\u001B[?7h",
                 "\u001B>", "\u001BP4.zGerman()", "\u001B[2J", "\u001B[2*x", "\u001B(0", "\u001B[7m",
                 "\u001B[0m", "\u001B[4m", "\u001B[2K", "\u001B[5m","\u001B#6","\u001B#5","\u001B[0K" -> true;
            default -> false;
        };
    }

}