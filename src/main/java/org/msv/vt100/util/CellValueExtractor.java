package org.msv.vt100.util;

import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Универсальный класс для извлечения значений ячеек из буфера экрана.
 * Позволяет получить значения ячеек в указанной строке и указанных столбцах в виде единой строки без разделителей.
 */
public class CellValueExtractor {
    private static final Logger logger = LoggerFactory.getLogger(CellValueExtractor.class);

    /**
     * Извлекает значения ячеек из заданной строки и столбцов.
     *
     * @param screenBuffer  объект ScreenBuffer для получения данных ячеек
     * @param row           номер строки (отсчёт с 1) откуда извлекаются ячейки
     * @param columnIndices номера столбцов (индексация с 0) для извлечения значений
     * @return строка, содержащая склеенные значения ячеек без пробелов между ними
     */
    public static String extractCells(ScreenBuffer screenBuffer, int row, int... columnIndices) {
        int rowIndex = row - 1; // Преобразуем номер строки из 1-based в 0-based
        StringBuilder sb = new StringBuilder();
        for (int col : columnIndices) {
            try {
                Cell cell = screenBuffer.getCell(rowIndex, col);
                sb.append(cell.character());
            } catch (IndexOutOfBoundsException e) {
                logger.error("Неверная позиция ячейки: строка {}, столбец {}", row, col);
            }
        }
        String result = sb.toString();
        logger.info("Извлечено значение ячеек (строка {}, столбцы {}) : {}", row, columnIndices, result);
        return result;
    }
}
