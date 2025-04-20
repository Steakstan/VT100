package org.msv.vt100.core;

import java.util.HashMap;
import java.util.Map;

public class ScreenBuffer {
    private final Map<Integer, Cell[][]> committedPages;
    private final Map<Integer, Cell[][]> backbufferPages;
    private int currentPageNumber;

    private final int rows;
    private final int columns;

    public ScreenBuffer(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        this.committedPages = new HashMap<>();
        this.backbufferPages = new HashMap<>();
        this.currentPageNumber = 1;

        committedPages.put(currentPageNumber, createEmptyPage());
        backbufferPages.put(currentPageNumber, createEmptyPage());
    }

    private Cell[][] createEmptyPage() {
        Cell[][] page = new Cell[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                page[row][col] = new Cell(" ", "-fx-fill: white; -rtfx-background-color: transparent;");
            }
        }
        return page;
    }

    public void switchToPage(int pageNumber) {
        if (!committedPages.containsKey(pageNumber)) {
            committedPages.put(pageNumber, createEmptyPage());
        }
        if (!backbufferPages.containsKey(pageNumber)) {
            backbufferPages.put(pageNumber, createEmptyPage());
        }
        currentPageNumber = pageNumber;
    }

    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    public Cell getCell(int row, int col) {
        if (!isValidPosition(row, col)) {
            throw new IndexOutOfBoundsException("Invalid screen buffer position");
        }
        return backbufferPages.get(currentPageNumber)[row][col];
    }

    public Cell getVisibleCell(int row, int col) {
        if (!isValidPosition(row, col)) {
            throw new IndexOutOfBoundsException("Invalid screen buffer position");
        }
        return committedPages.get(currentPageNumber)[row][col];
    }

    public void setCell(int row, int col, Cell cell) {
        if (!isValidPosition(row, col)) {
            throw new IndexOutOfBoundsException("Invalid screen buffer position");
        }
        backbufferPages.get(currentPageNumber)[row][col] = cell;
    }

    public void commit() {
        Cell[][] committed = committedPages.get(currentPageNumber);
        Cell[][] backbuffer = backbufferPages.get(currentPageNumber);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                committed[row][col] = backbuffer[row][col];
            }
        }
    }

    public void clearBackbuffer() {
        Cell[][] backbuffer = backbufferPages.get(currentPageNumber);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                backbuffer[row][col] = new Cell(" ", "-fx-fill: white; -rtfx-background-color: transparent;");
            }
        }
    }

    public String toStringVisible() {
        StringBuilder sb = new StringBuilder();
        Cell[][] visible = committedPages.get(currentPageNumber);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                sb.append(visible[row][col].character());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Cell[][] back = backbufferPages.get(currentPageNumber);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                sb.append(back[row][col].character());
            }
            sb.append('\n');
        }
        return sb.toString();
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
}
