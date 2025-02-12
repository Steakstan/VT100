package org.msv.vt100.core;

import java.util.HashMap;
import java.util.Map;

public class ScreenBuffer {
    private final Map<Integer, Cell[][]> pages;
    private int currentPageNumber;
    private final int rows;
    private final int columns;

    public ScreenBuffer(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        this.pages = new HashMap<>();
        this.currentPageNumber = 1; // Номер текущей страницы по умолчанию

        // Инициализируем первую страницу
        pages.put(currentPageNumber, createEmptyPage());
    }

    private Cell[][] createEmptyPage() {
        Cell[][] page = new Cell[rows][columns];
        // Пример инициализации пустого буфера экрана
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                page[row][col] = new Cell(" ", "-fx-fill: white; -vortex-background-color: transparent");
            }
        }

        return page;
    }

    public void switchToPage(int pageNumber) {
        if (!pages.containsKey(pageNumber)) {
            pages.put(pageNumber, createEmptyPage());
        }
        currentPageNumber = pageNumber;
    }

    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    public Cell getCell(int row, int col) {
        Cell[][] currentPage = pages.get(currentPageNumber);
        if (isValidPosition(row, col)) {
            return currentPage[row][col];
        } else {
            throw new IndexOutOfBoundsException("Invalid screen buffer position");
        }
    }

    public void setCell(int row, int col, Cell cell) {
        Cell[][] currentPage = pages.get(currentPageNumber);
        if (isValidPosition(row, col)) {
            currentPage[row][col] = cell;
        } else {
            throw new IndexOutOfBoundsException("Invalid screen buffer position");
        }
    }

    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < columns;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Cell[][] currentPage = pages.get(currentPageNumber);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                sb.append(currentPage[row][col].getCharacter());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}