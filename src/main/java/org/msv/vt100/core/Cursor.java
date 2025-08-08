package org.msv.vt100.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Потокобезопасный курсор терминала.
 * - row/column всегда в [0..max-1]
 * - операции атомарны под lock
 * - есть Condition для ожидания изменений (опционально)
 */
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

    /* ==================== GET ==================== */

    public int getRow() {
        return row; // volatile — видимость без lock
    }

    public int getColumn() {
        return column; // volatile — видимость без lock
    }

    /* ==================== SET/CHANGE ==================== */

    public void setPosition(int newRow, int newColumn) {
        lock.lock();
        try {
            int nr = clamp(newRow, 0, maxRows - 1);
            int nc = clamp(newColumn, 0, maxColumns - 1);
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
                // по желанию можно переносить на новую строку; текущая логика — стоп у края
                // column = 0; row++;
                column = maxColumns - 1; // остаёмся на месте (как в исходной логике)
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

    public void moveToColumnStart() {
        lock.lock();
        try {
            if (column != 0) {
                column = 0;
                positionChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /* ==================== WAIT/UTIL ==================== */

    /**
     * Возвращает позицию курсора как "row,col" (1-индексация для UI-совместимости).
     * Сигнатура оставлена с throws для совместимости; фактически ничего не бросает.
     */
    public String getCursorPosition() throws InterruptedException {
        int r = this.row;     // volatile read
        int c = this.column;  // volatile read
        return (r + 1) + "," + (c + 1);
    }

    /**
     * Опционально: подождать изменения позиции до таймаута.
     * @return true — позиция изменилась; false — таймаут.
     */
    public boolean awaitPositionChange(long timeoutMillis) throws InterruptedException {
        lock.lock();
        try {
            int r0 = row, c0 = column;
            if (timeoutMillis <= 0) {
                positionChanged.await();
                return (row != r0 || column != c0);
            } else {
                boolean signaled = positionChanged.await(timeoutMillis, TimeUnit.MILLISECONDS);
                return signaled && (row != r0 || column != c0);
            }
        } finally {
            lock.unlock();
        }
    }

    private static int clamp(int v, int min, int max) {
        return (v < min) ? min : (v > max ? max : v);
    }
}
