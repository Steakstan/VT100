package org.msv.vt100.core;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Cursor {
    private int row;
    private int column;
    private final int maxRows;
    private final int maxColumns;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition positionChanged = lock.newCondition();

    public Cursor(int maxRows, int maxColumns) {
        this.row = 0;
        this.column = 0;
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public void setPosition(int row, int column) {
        lock.lock();
        try {
            this.row = Math.max(0, Math.min(row, maxRows - 1));
            this.column = Math.max(0, Math.min(column, maxColumns - 1));
            positionChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void moveRight() {
        if (column < maxColumns - 1) {
            column++;
        }
    }

    public void moveLeft() {
        if (column > 0) {
            column--;
        } else if (row > 0) {
            row--;
            column = maxColumns - 1;
        }
    }

    public void moveToColumnStart() {
        this.column = 0;
    }

    public String getCursorPositionString() {
        lock.lock();
        try {
            return (row + 1) + "," + (column + 1);
        } finally {
            lock.unlock();
        }
    }
}
