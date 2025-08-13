package org.msv.vt100.core;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class Cursor {

    private volatile int row;
    private volatile int column;

    private final int maxRows;
    private final int maxColumns;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition positionChanged = lock.newCondition();

    public Cursor(int maxRows, int maxColumns) {
        if (maxRows <= 0 || maxColumns <= 0)
            throw new IllegalArgumentException("maxRows/maxColumns must be > 0");
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
        this.row = 0;
        this.column = 0;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }


    public void setPosition(int newRow, int newColumn) {
        lock.lock();
        try {
            int nr = clamp(newRow, maxRows - 1);
            int nc = clamp(newColumn, maxColumns - 1);
            if (nr != row || nc != column) {
                row = nr;
                column = nc;
                positionChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void moveRight() {
        lock.lock();
        try {
            if (column < maxColumns - 1) {
                column++;
            } else if (row < maxRows - 1) {
                row++;
                column = 0;
            }
            positionChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void moveLeft() {
        lock.lock();
        try {
            if (column > 0) {
                column--;
            } else if (row > 0) {
                row--;
                column = maxColumns - 1;
            }
            positionChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public String getCursorPosition() {
        int r = this.row;     // volatile read
        int c = this.column;  // volatile read
        return (r + 1) + "," + (c + 1);
    }

    private static int clamp(int v, int max) {
        return (v < 0) ? 0 : (Math.min(v, max));
    }
}
