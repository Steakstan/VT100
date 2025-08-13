package org.msv.vt100.ui;

import org.msv.vt100.core.ScreenBuffer;

final class SelectionModel {
    private Integer selectionStartRow, selectionStartCol;
    private Integer selectionEndRow, selectionEndCol;
    private boolean isSelecting = false;

    boolean isSelecting() { return isSelecting; }

    void beginSelection(double x, double y, double cellWidth, double cellHeight, DirtyTracker dirty) {
        // vorher: clearSelectionInternal(false, dirty);
        clearSelectionInternal(dirty); // alte Auswahlbereiche invalidieren

        selectionStartCol = (int) (x / cellWidth);
        selectionStartRow = (int) (y / cellHeight);
        selectionEndCol = selectionStartCol;
        selectionEndRow = selectionStartRow;
        isSelecting = true;

        dirty.markRowDirty(selectionStartRow); // neue Startzeile neu zeichnen
    }


    void updateSelection(double x, double y, double cellWidth, double cellHeight, DirtyTracker dirty) {
        int oldEndRow = selectionEndRow == null ? -1 : selectionEndRow;
        int oldEndCol = selectionEndCol == null ? -1 : selectionEndCol;

        int newEndCol = (int) (x / cellWidth);
        int newEndRow = (int) (y / cellHeight);

        boolean colChanged = newEndCol != oldEndCol;
        boolean rowChanged = newEndRow != oldEndRow;

        if (colChanged || rowChanged) {
            // Invalideren: alte Range UND neue Range (Union abdecken)
            int fromOld = Math.min(selectionStartRow, oldEndRow);
            int toOld   = Math.max(selectionStartRow, oldEndRow);
            int fromNew = Math.min(selectionStartRow, newEndRow);
            int toNew   = Math.max(selectionStartRow, newEndRow);

            dirty.markSelectionRangeDirty(fromOld, toOld);
            dirty.markSelectionRangeDirty(fromNew, toNew);
        }

        selectionEndCol = newEndCol;
        selectionEndRow = newEndRow;
    }


    void endSelection(double x, double y, double cellWidth, double cellHeight, DirtyTracker dirty) {
        updateSelection(x, y, cellWidth, cellHeight, dirty);
        isSelecting = false;
        dirty.markSelectionRangeDirty(selectionStartRow, selectionEndRow);
    }

    void clearSelection(DirtyTracker dirty) {
        clearSelectionInternal(dirty);
    }

    private void clearSelectionInternal(DirtyTracker dirty) {
        if (selectionStartRow != null && selectionEndRow != null) {
            dirty.markSelectionRangeDirty(selectionStartRow, selectionEndRow);
        }
        selectionStartRow = selectionStartCol = null;
        selectionEndRow = selectionEndCol = null;
    }

    void selectAll(int rows, int cols, DirtyTracker dirty) {
        selectionStartRow = 0;
        selectionStartCol = 0;
        selectionEndRow = rows - 1;
        selectionEndCol = cols - 1;
        isSelecting = false;
        dirty.markAllDirty();
    }

    void selectWordAt(double x, double y, double cellWidth, double cellHeight,
                      ScreenBuffer screenBuffer, DirtyTracker dirty) {
        clearSelectionInternal(dirty);
        int col = (int) (x / cellWidth);
        int row = (int) (y / cellHeight);
        int cols = screenBuffer.getColumns();
        if (row < 0 || row >= screenBuffer.getRows()) return;
        col = Math.max(0, Math.min(cols - 1, col));
        int left = col, right = col;
        while (left > 0) {
            String ch = screenBuffer.getVisibleCell(row, left - 1).character();
            if (ch == null || ch.isBlank()) break;
            left--;
        }
        while (right < cols - 1) {
            String ch = screenBuffer.getVisibleCell(row, right + 1).character();
            if (ch == null || ch.isBlank()) break;
            right++;
        }
        selectionStartRow = row; selectionEndRow = row;
        selectionStartCol = left; selectionEndCol = right;
        isSelecting = false;
        dirty.markRowDirty(row);
    }

    void selectRowAt(double y, double cellHeight, ScreenBuffer screenBuffer, DirtyTracker dirty) {
        clearSelectionInternal(dirty);
        int row = (int) (y / cellHeight);
        if (row < 0 || row >= screenBuffer.getRows()) return;
        selectionStartRow = row; selectionEndRow = row;
        selectionStartCol = 0; selectionEndCol = screenBuffer.getColumns() - 1;
        isSelecting = false;
        dirty.markRowDirty(row);
    }

    String getSelectedText(ScreenBuffer screenBuffer) {
        if (selectionStartRow == null || selectionEndRow == null) return "";
        int sr = Math.min(selectionStartRow, selectionEndRow);
        int er = Math.max(selectionStartRow, selectionEndRow);
        int sc = Math.min(selectionStartCol, selectionEndCol);
        int ec = Math.max(selectionStartCol, selectionEndCol);
        StringBuilder sb = new StringBuilder();
        for (int r = sr; r <= er; r++) {
            for (int c = sc; c <= ec; c++) {
                String s = screenBuffer.getVisibleCell(r, c).character();
                sb.append(s == null ? ' ' : s);
            }
            if (r < er) sb.append('\n');
        }
        return sb.toString();
    }

    Integer startRow() { return selectionStartRow; }
    Integer endRow() { return selectionEndRow; }
    Integer startCol() { return selectionStartCol; }
    Integer endCol() { return selectionEndCol; }
}
