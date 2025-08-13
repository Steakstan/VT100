package org.msv.vt100.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;

final class TerminalRenderer {
    private static final Color SELECTION_OVERLAY = Color.web("#5D9DFF", 0.25);

    private final StyleRegistry styles;
    private final SelectionModel selection;
    private final FontManager fonts;

    TerminalRenderer(StyleRegistry styles, SelectionModel selection, FontManager fonts) {
        this.styles = styles;
        this.selection = selection;
        this.fonts = fonts;
    }

    void renderBackgroundRuns(GraphicsContext gc, ScreenBuffer screenBuffer, int r,
                              double cellWidth, double cellHeight,
                              double canvasW, double canvasH) {
        int cols = screenBuffer.getColumns();
        double y = r * cellHeight;

        int c = 0;
        while (c < cols) {
            var cell = screenBuffer.getVisibleCell(r, c);
            var sk = styles.styleKeyFor(cell.style());
            short bgIdx = sk.bgIdx;
            int start = c;
            while (c < cols) {
                var next = screenBuffer.getVisibleCell(r, c);
                if (styles.styleKeyFor(next.style()).bgIdx != bgIdx) break;
                c++;
            }
            if (bgIdx >= 0) {
                double x = start * cellWidth;
                double w = (c - start) * cellWidth;

                // жёсткий снап по X, +оверскан по Y
                double x0 = Math.floor(x);
                double x1 = Math.ceil(x + w);

                double y0 = Math.floor(y) - 1;
                double y1 = Math.ceil(y + cellHeight) + 1;
                if (y0 < 0) y0 = 0;
                if (y1 > canvasH) y1 = canvasH;

                gc.setFill(styles.paletteColor(bgIdx));
                gc.fillRect(x0, y0, x1 - x0, y1 - y0);
            }
        }
    }



    void renderSelectionOverlay(GraphicsContext gc, int r, double cellWidth, double cellHeight, int cols) {
        Integer startRow = selection.startRow();
        Integer endRow = selection.endRow();
        Integer startCol = selection.startCol();
        Integer endCol = selection.endCol();
        if (startRow == null || endRow == null) return;
        int sr = Math.min(startRow, endRow);
        int er = Math.max(startRow, endRow);
        if (r < sr || r > er) return;
        int sc = Math.min(startCol, endCol);
        int ec = Math.max(startCol, endCol);
        sc = Math.max(0, sc);
        ec = Math.min(cols - 1, ec);
        if (sc > ec) return;
        double x = Math.floor(sc * cellWidth);
        double y = Math.floor(r  * cellHeight);
        double w = Math.ceil((ec - sc + 1) * cellWidth);
        double h = Math.ceil(cellHeight);
        gc.setFill(SELECTION_OVERLAY);
        gc.fillRect(x, y, w, h);
    }

    void renderTextAndUnderline(GraphicsContext gc, ScreenBuffer screenBuffer, int r, double cellWidth, double cellHeight) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        int cols = screenBuffer.getColumns();
        int c = 0;
        while (c < cols) {
            Cell cell = screenBuffer.getVisibleCell(r, c);
            String ch = cell.character();
            if (isBoxDrawingChar(ch)) { c++; continue; }

            StyleRegistry.StyleKey base = styles.styleKeyFor(cell.style());
            int start = c;
            while (c < cols) {
                Cell cur = screenBuffer.getVisibleCell(r, c);
                String cc = cur.character();
                if (isBoxDrawingChar(cc)) break;
                StyleRegistry.StyleKey sk = styles.styleKeyFor(cur.style());
                if (!sk.sameTextAttrs(base)) break;
                c++;
            }

            gc.setFont(base.isBold() ? fonts.bold() : fonts.normal());
            gc.setFill(styles.paletteColor(base.fgIdx));

            if (base.isUnderline()) {
                double uy = Math.floor((r + 1) * cellHeight) - 1.0;
                gc.setLineWidth(1);
                gc.setStroke(styles.paletteColor(base.fgIdx));
                gc.strokeLine(start * cellWidth, uy, c * cellWidth, uy);
            }

            for (int k = start; k < c; k++) {
                String s = screenBuffer.getVisibleCell(r, k).character();
                if (s != null && !s.isBlank()) {
                    double x = k * cellWidth + cellWidth / 2.0;
                    double y = r * cellHeight + cellHeight / 2.0;
                    gc.fillText(s, x, y);
                }
            }
        }
    }


    void renderBoxChars(GraphicsContext gc, ScreenBuffer screenBuffer, int r, double cellWidth, double cellHeight) {
        int cols = screenBuffer.getColumns();
        for (int c = 0; c < cols; c++) {
            String ch = screenBuffer.getVisibleCell(r, c).character();
            if (!isBoxDrawingChar(ch)) continue;
            StyleRegistry.StyleKey sk = styles.styleKeyFor(screenBuffer.getVisibleCell(r, c).style());
            if (ch.length() != 1) continue;

            char cc = ch.charAt(0);
            double x = c * cellWidth;
            double y = r * cellHeight;

            drawBoxCharacter(gc, cc, x, y, cellWidth, cellHeight, styles.paletteColor(sk.fgIdx));
        }
    }

    void drawCursorOverlay(GraphicsContext gc, ScreenBuffer screenBuffer, boolean visible, int row, int col, double cellWidth, double cellHeight) {
        if (!visible || row < 0 || col < 0) return;
        Cell cell = screenBuffer.getVisibleCell(row, col);
        StyleRegistry.StyleKey sk = styles.styleKeyFor(cell.style());
        Color color = styles.paletteColor(sk.fgIdx);
        double x = col * cellWidth;
        double y = row * cellHeight;
        gc.setFill(color);
        gc.fillRect(x, y, cellWidth, 1);
        gc.fillRect(x, y + cellHeight, cellWidth, 1);
        gc.fillRect(x, y, 1, cellHeight);
        gc.fillRect(x + cellWidth, y, 1, cellHeight);
    }

    private static boolean isBoxDrawingChar(String ch) {
        if (ch == null || ch.length() != 1) return false;
        char c = ch.charAt(0);
        String set = "┌┐└┘├┤┬┴┼│─┏┓┗┛┣┫┳┻╋┃━";
        return set.indexOf(c) >= 0;
    }

    private static void drawBoxCharacter(GraphicsContext gc,
                                         char c, double x, double y, double w, double h,
                                         Color lineColor) {
        gc.setStroke(lineColor);
        double lw = (c == '━' || c == '┃' || c == '╋' || c == '┳' || c == '┻' || c == '┫' || c == '┣' || c == '┏' || c == '┓' || c == '┗' || c == '┛') ? 2.0 : 1.5;
        gc.setLineWidth(lw);

        double left = Math.floor(x) + 0.5;
        double top = Math.floor(y) + 0.5;
        double right = Math.floor(x + w) - 0.5;
        double bottom = Math.floor(y + h) - 0.5;
        double midX = Math.floor((x + x + w) / 2.0) + 0.5;
        double midY = Math.floor((y + y + h) / 2.0) + 0.5;

        switch (c) {
            case '─', '━' -> gc.strokeLine(left, midY, right, midY);
            case '│', '┃' -> gc.strokeLine(midX, top, midX, bottom);
            case '┌', '┏' -> { gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); }
            case '┐', '┓' -> { gc.strokeLine(left, midY, midX, midY); gc.strokeLine(midX, midY, midX, bottom); }
            case '└', '┗' -> { gc.strokeLine(midX, top, midX, midY); gc.strokeLine(midX, midY, right, midY); }
            case '┘', '┛' -> { gc.strokeLine(midX, top, midX, midY); gc.strokeLine(left, midY, midX, midY); }
            case '├', '┣' -> { gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, top, midX, bottom); }
            case '┤', '┫' -> { gc.strokeLine(left, midY, midX, midY); gc.strokeLine(midX, top, midX, bottom); }
            case '┬', '┳' -> { gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); }
            case '┴', '┻' -> { gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, top, midX, midY); }
            case '┼', '╋' -> { gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, top, midX, bottom); }
        }
    }


}
