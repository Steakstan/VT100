package org.msv.vt100.ansiisequences;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FillRectangularAreaHandler {

    private static final Logger logger = LoggerFactory.getLogger(FillRectangularAreaHandler.class);

    private final ScreenBuffer screenBuffer;
    private LeftRightMarginModeHandler leftRightMarginModeHandler;
    private ScrollingRegionHandler scrollingRegionHandler;

    private static final String DEFAULT_STYLE = "-fx-fill: white; -rtfx-background-color: transparent;";

    public FillRectangularAreaHandler(ScreenBuffer screenBuffer) {
        this.screenBuffer = screenBuffer;
    }

    // Устанавливается из EscapeSequenceHandler
    public void setLeftRightMarginModeHandler(LeftRightMarginModeHandler handler) {
        this.leftRightMarginModeHandler = handler;
    }

    public void setScrollingRegionHandler(ScrollingRegionHandler handler) {
        this.scrollingRegionHandler = handler;
    }

    /**
     * Обрабатывает ESC [Pch;Pts;Pls;Pbs;Prs$x — команду заполнения области.
     */
    public void handleDECFRA(String sequence) {
        String cleaned = sequence.replace("\u001B", "").replace("[", "").replace("$x", "");
        String[] params = cleaned.split(";");

        if (params.length < 5) {
            logger.warn("DECFRA: недостаточно параметров: {}", sequence);
            return;
        }

        try {
            int Pch = Integer.parseInt(params[0]);
            int Pts = Integer.parseInt(params[1]);
            int Pls = Integer.parseInt(params[2]);
            int Pbs = Integer.parseInt(params[3]);
            int Prs = Integer.parseInt(params[4]);

            int maxRows = screenBuffer.getRows();
            int maxCols = screenBuffer.getColumns();

            // Базовая валидация
            if (Pts < 1 || Pbs < Pts || Pbs > maxRows ||
                    Pls < 1 || Prs < Pls || Prs > maxCols) {
                logger.warn("DECFRA: координаты вне границ экрана: {}", sequence);
                return;
            }

            // Проверка полей (DECVLRM)
            if (leftRightMarginModeHandler != null && leftRightMarginModeHandler.isLeftRightMarginModeEnabled()) {
                int marginLeft = leftRightMarginModeHandler.getLeftMargin() + 1;
                int marginRight = leftRightMarginModeHandler.getRightMargin() + 1;

                if (Pls < marginLeft || Prs > marginRight) {
                    logger.warn("DECFRA: координаты ({},{}–{},{}), вне полей {}–{}",
                            Pts, Pls, Pbs, Prs, marginLeft, marginRight);
                    return;
                }
            }

            // Проверка области прокрутки (если есть)
            if (scrollingRegionHandler != null) {
                int scrollTop = scrollingRegionHandler.getWindowStartRow() + 1;
                int scrollBottom = scrollingRegionHandler.getWindowEndRow() + 1;

                if (Pts < scrollTop || Pbs > scrollBottom) {
                    logger.warn("DECFRA: координаты вне области прокрутки {}–{}: {}", scrollTop, scrollBottom, sequence);
                    return;
                }
            }

            // Заполнение
            char fillChar = (char) Pch;
            fillArea(Pts - 1, Pls - 1, Pbs - 1, Prs - 1, String.valueOf(fillChar));

            logger.info("DECFRA: заполнена область ({},{} → {},{}) символом '{}'",
                    Pts, Pls, Pbs, Prs, fillChar);

        } catch (NumberFormatException e) {
            logger.error("DECFRA: ошибка парсинга параметров: {}", sequence, e);
        }
    }

    /**
     * Заполняет прямоугольную область символом.
     */
    private void fillArea(int top, int left, int bottom, int right, String ch) {
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) {
                screenBuffer.setCell(row, col, new Cell(ch, DEFAULT_STYLE));
            }
        }
    }
}
