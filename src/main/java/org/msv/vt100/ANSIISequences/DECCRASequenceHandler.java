package org.msv.vt100.ANSIISequences;

import org.msv.vt100.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DECCRASequenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(DECCRASequenceHandler.class);

    private final CopyRectangularAreaHandler copyRectangularAreaHandler;
    private final ScreenBuffer screenBuffer;

    public DECCRASequenceHandler(
            CopyRectangularAreaHandler copyRectangularAreaHandler,
            ScreenBuffer screenBuffer) {
        this.copyRectangularAreaHandler = copyRectangularAreaHandler;
        this.screenBuffer = screenBuffer;
    }

    public void handleDECCRA(String sequence) {
        // Удаляем ESC и '$v'
        String paramsPart = sequence.substring(1, sequence.length() - 2);
        String[] params = paramsPart.split(";");

        if (params.length < 8) {
            logger.warn("Недостаточно параметров для DECCRA: {}", sequence);
            return;
        }

        try {
            int maxRows = screenBuffer.getRows();
            int maxCols = screenBuffer.getColumns();

            int Pts = parseParameter(params[0], 1, 1, maxRows);
            int Pls = parseParameter(params[1], 1, 1, maxCols);
            int Pbs = parseParameter(params[2], maxRows, 1, maxRows);
            int Prs = parseParameter(params[3], maxCols, 1, maxCols);
            int Ptd = parseParameter(params[4], 1, 1, maxRows);
            int Pld = parseParameter(params[5], 1, 1, maxCols);
            int Psrc_page = parseParameter(params[6], 1);
            int Pdst_page = parseParameter(params[7], 1);

            // Убеждаемся, что нижние координаты не меньше верхних
            if (Pbs < Pts) {
                int temp = Pts;
                Pts = Pbs;
                Pbs = temp;
            }
            if (Prs < Pls) {
                int temp = Pls;
                Pls = Prs;
                Prs = temp;
            }

            // Вызываем обработчик DECCRA
            copyRectangularAreaHandler.copyArea(Pts, Pls, Pbs, Prs, Ptd, Pld, Psrc_page, Pdst_page);

            if (shouldSwitchToDestinationPage(Pdst_page)) {
                // Переключаемся на страницу назначения
                screenBuffer.switchToPage(Pdst_page);
            } else {
                // Остаёмся на исходной странице или возвращаемся на неё
                screenBuffer.switchToPage(Psrc_page);
            }

        } catch (NumberFormatException e) {
            logger.error("Ошибка разбора параметров DECCRA: {}", sequence, e);
        }
    }

    // Вспомогательный метод для разбора параметров с ограничениями
    private int parseParameter(String param, int defaultValue, int minValue, int maxValue) {
        int value;
        if (param == null || param.isEmpty()) {
            value = defaultValue;
        } else {
            value = Integer.parseInt(param);
            value = (value == 0) ? defaultValue : value;
        }
        // Ограничиваем значение в пределах min и max
        value = Math.max(minValue, Math.min(value, maxValue));
        return value;
    }

    // Перегруженный метод для параметров без ограничений
    private int parseParameter(String param, int defaultValue) {
        if (param == null || param.isEmpty()) {
            return defaultValue;
        }
        int value = Integer.parseInt(param);
        return (value == 0) ? defaultValue : value;
    }
    private boolean shouldSwitchToDestinationPage(int Pdst_page) {
        // Определяем, нужно ли переключаться на страницу назначения
        // Например, если Pdst_page равно 1 или 2, переключаемся
        // Если больше 2, считаем, что это буферная страница и не переключаемся
        return Pdst_page == 1 || Pdst_page == 2;
    }
}
