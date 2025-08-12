package org.msv.vt100.ui;

import java.util.Arrays;

final class DirtyTracker {
    private boolean[] dirtyRows;

    DirtyTracker(int rows) { this.dirtyRows = new boolean[rows]; markAllDirty(); }

    void ensureSize(int rows) {
        if (dirtyRows.length != rows) {
            dirtyRows = new boolean[rows];
            markAllDirty();
        }
    }

    void markAllDirty() { Arrays.fill(dirtyRows, true); }

    void markRowDirty(int r) {
        if (r >= 0 && r < dirtyRows.length) dirtyRows[r] = true;
        if (r - 1 >= 0) dirtyRows[r - 1] = true;
        if (r + 1 < dirtyRows.length) dirtyRows[r + 1] = true;
    }

    void markSelectionRangeDirty(Integer startRow, Integer endRow) {
        if (startRow == null || endRow == null) return;
        int from = Math.max(0, Math.min(startRow, endRow));
        int to   = Math.min(dirtyRows.length - 1, Math.max(startRow, endRow));
        for (int r = from; r <= to; r++) dirtyRows[r] = true;
        if (from - 1 >= 0) dirtyRows[from - 1] = true;
        if (to + 1 < dirtyRows.length) dirtyRows[to + 1] = true;
    }

    boolean isRowDirty(int r) { return r >= 0 && r < dirtyRows.length && dirtyRows[r]; }
    void clearRow(int r) { if (r >= 0 && r < dirtyRows.length) dirtyRows[r] = false; }
}

