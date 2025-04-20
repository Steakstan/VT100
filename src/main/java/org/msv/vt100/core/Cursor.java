package org.msv.vt100.core;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
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

    public String getCursorPosition() throws InterruptedException {
        final String[] position = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            position[0] = (getRow() + 1) + "," + (getColumn() + 1);
            latch.countDown();
        });
        latch.await();
        return position[0];
    }

}
