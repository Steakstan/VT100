package org.msv.vt100.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ScreenBuffer:
 * - Двойная буферизация по страницам (committed/backbuffer).
 * - Коммит копирует ТОЛЬКО грязные строки (перфоманс).
 * - Стиль по умолчанию нормализован: "fill: white; background: transparent;".
 * - API совместим с предыдущей версией.
 */
public class ScreenBuffer {

    /** Хранилище страниц: номер → страница */
    private final Map<Integer, Page> pages = new HashMap<>();

    private int currentPageNumber;

    private final int rows;
    private final int columns;

    public ScreenBuffer(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("rows/columns must be > 0");
        }
        this.rows = rows;
        this.columns = columns;
        this.currentPageNumber = 1;
        pages.put(currentPageNumber, createEmptyPage());
    }

    /* =========================== Публичный API =========================== */

    public void switchToPage(int pageNumber) {
        if (pageNumber <= 0) throw new IllegalArgumentException("pageNumber must be > 0");
        pages.computeIfAbsent(pageNumber, k -> createEmptyPage());
        currentPageNumber = pageNumber;
    }

    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    /** Возвращает ячейку из backbuffer (рабочий слой). */
    public Cell getCell(int row, int col) {
        ensureValid(row, col);
        return page().backbuffer[row][col];
    }

    /** Возвращает видимую ячейку из committed (экран). */
    public Cell getVisibleCell(int row, int col) {
        ensureValid(row, col);
        return page().committed[row][col];
    }

    /** Устанавливает ячейку в backbuffer и помечает строку грязной при изменении. */
    public void setCell(int row, int col, Cell cell) {
        ensureValid(row, col);
        Objects.requireNonNull(cell, "cell");
        Page p = page();
        Cell prev = p.backbuffer[row][col];
        if (!equalsCell(prev, cell)) {
            p.backbuffer[row][col] = cell;
            p.dirtyRows[row] = true;
        }
    }

    /**
     * Переносит изменения из backbuffer → committed.
     * Копируются только строки, помеченные как грязные.
     */
    public void commit() {
        Page p = page();
        for (int r = 0; r < rows; r++) {
            if (!p.dirtyRows[r]) continue;
            // Копируем строку целиком (ячейки — неизменяемые record, копирование ссылок дёшево)
            System.arraycopy(p.backbuffer[r], 0, p.committed[r], 0, columns);
            p.dirtyRows[r] = false;
        }
    }

    /**
     * Очищает backbuffer текущей страницы (заполняет пробелами и прозрачным фоном).
     * Помечает все строки как грязные (чтобы commit обновил экран).
     */
    public void clearBackbuffer() {
        Page p = page();
        for (int r = 0; r < rows; r++) {
            Cell[] rowArr = p.backbuffer[r];
            for (int c = 0; c < columns; c++) {
                rowArr[c] = DEFAULT_CELL;
            }
            p.dirtyRows[r] = true;
        }
    }

    /** Текстовое представление committed-экрана. */
    public String toStringVisible() {
        StringBuilder sb = new StringBuilder(rows * (columns + 1));
        Cell[][] v = page().committed;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                sb.append(v[r][c].character());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Текстовое представление backbuffer. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(rows * (columns + 1));
        Cell[][] b = page().backbuffer;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                sb.append(b[r][c].character());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    /* =========================== Внутренняя модель =========================== */

    private static final String DEFAULT_STYLE = "fill: white; background: transparent;";
    private static final Cell DEFAULT_CELL = new Cell(" ", DEFAULT_STYLE);

    private Page page() {
        return pages.get(currentPageNumber);
    }

    private Page createEmptyPage() {
        Cell[][] committed = new Cell[rows][columns];
        Cell[][] backbuffer = new Cell[rows][columns];
        boolean[] dirtyRows = new boolean[rows];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                // Один и тот же DEFAULT_CELL (record immutable) безопасно шарится по сетке
                committed[r][c] = DEFAULT_CELL;
                backbuffer[r][c] = DEFAULT_CELL;
            }
            dirtyRows[r] = true; // первая отрисовка — вся страница грязная
        }
        return new Page(committed, backbuffer, dirtyRows);
    }

    private void ensureValid(int row, int col) {
        if (!isValidPosition(row, col)) {
            throw new IndexOutOfBoundsException("Invalid screen buffer position: (" + row + "," + col + ")");
        }
    }

    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < columns;
    }

    private static boolean equalsCell(Cell a, Cell b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // сравниваем и символ, и стиль
        return Objects.equals(a.character(), b.character()) &&
                Objects.equals(a.style(), b.style());
    }

    private static final class Page {
        final Cell[][] committed;
        final Cell[][] backbuffer;
        final boolean[] dirtyRows;

        Page(Cell[][] committed, Cell[][] backbuffer, boolean[] dirtyRows) {
            this.committed = committed;
            this.backbuffer = backbuffer;
            this.dirtyRows = dirtyRows;
        }
    }

    public Cell[] getVisibleRow(int row) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("Invalid row: " + row);
        }
        return page().committed[row];
    }
}
