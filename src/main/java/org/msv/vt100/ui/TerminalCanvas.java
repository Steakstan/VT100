package org.msv.vt100.ui;

import javafx.beans.value.ObservableValue;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.util.StyleUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * TerminalCanvas (переписано):
 * - Двойной буфер отображения: сравнение с shadow (lastChars/lastStyles), отрисовываем только "грязные" строки.
 * - Без глобальной очистки: чистим только прямоугольник строки, которую перерисовываем.
 * - Рендер курсора и выделения поверх содержимого, dirty — только затронутые строки.
 * - Стабильные размеры ячейки; пересоздаём шрифты только при реальном изменении высоты ячейки.
 * - Консистентные ключи стиля: fill/background/underline/font-weight (StyleUtils должен нормализовать).
 */
public class TerminalCanvas extends Canvas {

    private final ScreenBuffer screenBuffer;

    // Размеры в ячейках
    private double cellWidth;
    private double cellHeight;

    // Текущее состояние курсора на канвасе (логическое)
    public boolean cursorVisible = true;
    private int cursorRow = -1, cursorCol = -1;
    private int prevCursorRow = -1, prevCursorCol = -1;
    private boolean prevCursorVisible = false;

    // Выделение
    private Integer selectionStartRow, selectionStartCol;
    private Integer selectionEndRow, selectionEndCol;
    private boolean isSelecting = false;

    // Shadow-буфер
    private String[][] lastChars;
    private String[][] lastStyles; // сериализованный стиль (после нормализации)
    private boolean[] dirtyRows;

    // Кэш цветов и шрифтов
    private final Map<String, Color> colorCache = new HashMap<>();
    private Font normalFont, boldFont;
    private double lastFontPixelSize = -1;

    // Контекстное меню
    private ContextMenu contextMenu;

    private String canvasBackground = "transparent"; // или задай свой цвет, если не нужен прозрачный

    public void setCanvasBackground(String cssColor) {
        this.canvasBackground = (cssColor == null || cssColor.isBlank()) ? "transparent" : cssColor;
        markAllDirty();
        updateScreen();
    }

    public TerminalCanvas(ScreenBuffer screenBuffer, double width, double height) {
        super(width, height);
        this.screenBuffer = Objects.requireNonNull(screenBuffer, "screenBuffer");

        initBuffers();
        recalcCellDimensions(true);
        initMouseHandlers();
        initKeyHandlers();
        initContextMenu();
        initSceneMouseFilter();

        setFocusTraversable(true);
        getStyleClass().add("terminal-canvas");

        // Изменение размера канваса — пересчёт геометрии и полная перерисовка только при реальном изменении
        widthProperty().addListener((obs, ov, nv) -> {
            if (nv.doubleValue() != ov.doubleValue()) {
                recalcCellDimensions(false);
                markAllDirty();
                updateScreen();
            }
        });
        heightProperty().addListener((obs, ov, nv) -> {
            if (nv.doubleValue() != ov.doubleValue()) {
                recalcCellDimensions(false);
                markAllDirty();
                updateScreen();
            }
        });
    }

    /* ===================== Буферы/геометрия ===================== */

    private void initBuffers() {
        int rows = screenBuffer.getRows();
        int cols = screenBuffer.getColumns();
        lastChars = new String[rows][cols];
        lastStyles = new String[rows][cols];
        dirtyRows = new boolean[rows];
        markAllDirty();
    }

    private void recalcCellDimensions(boolean forceDirty) {
        int cols = screenBuffer.getColumns();
        int rows = screenBuffer.getRows();
        if (cols <= 0 || rows <= 0) return;

        // Подгоняем размер ячейки к целым пикселям для crisp-рендера
        double newCW = Math.floor(getWidth() / cols);
        double newCH = Math.floor(getHeight() / rows);
        if (newCW <= 0 || newCH <= 0) return;

        boolean sizeChanged = (newCW != cellWidth) || (newCH != cellHeight);
        cellWidth = newCW;
        cellHeight = newCH;

        // Обновляем шрифты только при изменении высоты ячейки
        if (sizeChanged || forceDirty) {
            if (cellHeight != lastFontPixelSize) {
                double px = Math.max(8, Math.floor(cellHeight * 0.8));
                normalFont = Font.font("Consolas", px);
                boldFont   = Font.font("Consolas", FontWeight.BOLD, px);
                lastFontPixelSize = cellHeight;
                markAllDirty();
            }
        }

        // Подгоняем физический размер канваса под целые ячейки (минимум изменений)
        double targetW = Math.floor(cellWidth * cols);
        double targetH = Math.floor(cellHeight * rows);
        if (Math.abs(getWidth() - targetW) >= 1.0) setWidth(targetW);
        if (Math.abs(getHeight() - targetH) >= 1.0) setHeight(targetH);
    }

    public void markAllDirty() {
        for (int r = 0; r < dirtyRows.length; r++) dirtyRows[r] = true;
    }

    private void markRowDirty(int r) {
        if (r >= 0 && r < dirtyRows.length) dirtyRows[r] = true;
    }

    private void markSelectionRangeDirty(Integer startRow, Integer endRow) {
        if (startRow == null || endRow == null) return;
        int rows = screenBuffer.getRows();
        int from = Math.max(0, Math.min(startRow, endRow));
        int to   = Math.min(rows - 1, Math.max(startRow, endRow));
        for (int r = from; r <= to; r++) dirtyRows[r] = true;
        if (to + 1 < rows) dirtyRows[to + 1] = true; // захватываем +1 снизу, без выхода за границу
    }


    /* ===================== Основной апдейт ===================== */

    /**
     * Сверяем committed-экран с shadow и перерисовываем только изменившиеся строки.
     * Также учитываем курсор и выделение.
     */
    public void updateScreen() {
        recalcCellDimensions(false);

        final int rows = screenBuffer.getRows();
        final int cols = screenBuffer.getColumns();
        final GraphicsContext gc = getGraphicsContext2D();

        // 1) Дифф по содержимому
        for (int r = 0; r < rows; r++) {
            if (dirtyRows[r]) continue;
            for (int c = 0; c < cols; c++) {
                Cell cell = screenBuffer.getVisibleCell(r, c);
                String ch = cell.character();
                String st = normalizeStyle(cell.style());
                if (!equalsNullSafe(ch, lastChars[r][c]) || !equalsNullSafe(st, lastStyles[r][c])) {
                    dirtyRows[r] = true;
                    break;
                }
            }
        }

        // 2) Курсор → грязним старую и новую строки
        if (cursorVisible != prevCursorVisible ||
                cursorRow != prevCursorRow || cursorCol != prevCursorCol) {
            markRowDirty(prevCursorRow);
            markRowDirty(cursorRow);
        }

        // 3) Расширяем dirty-зону на одну строку вниз
        for (int r = rows - 2; r >= 0; r--) {
            if (dirtyRows[r]) dirtyRows[r + 1] = true;
        }

        // 4) Рендер dirty-строк
        for (int r = 0; r < rows; r++) {
            if (!dirtyRows[r]) continue;

            double y = r * cellHeight;
            double h = cellHeight;

            // ROW CLEAR: очищаем старое содержимое строки
            if ("transparent".equalsIgnoreCase(canvasBackground)) {
                gc.clearRect(0, y, getWidth(), h);                // в прозрачность
            } else {
                gc.setFill(getCachedColor(canvasBackground));     // в базовый цвет канваса
                gc.fillRect(0, y, getWidth(), h);
            }

            // Рисуем ячейки строки
            for (int c = 0; c < cols; c++) {
                renderCell(gc, r, c);
            }

            // Обновляем shadow
            for (int c = 0; c < cols; c++) {
                Cell cell = screenBuffer.getVisibleCell(r, c);
                lastChars[r][c]  = cell.character();
                lastStyles[r][c] = normalizeStyle(cell.style());
            }

            dirtyRows[r] = false;
        }

        // 5) Курсор поверх всего
        drawCursorOverlay(gc);

        prevCursorRow = cursorRow;
        prevCursorCol = cursorCol;
        prevCursorVisible = cursorVisible;
    }




    private void renderCell(GraphicsContext gc, int row, int col) {
        Cell cell = screenBuffer.getVisibleCell(row, col);
        Map<String, String> style = StyleUtils.parseStyleString(normalizeStyle(cell.style()));

        String fillColor = style.getOrDefault("fill", "white");
        String bgColor   = style.getOrDefault("background", "transparent");
        boolean underline = "true".equalsIgnoreCase(style.getOrDefault("underline", "false"));
        boolean bold      = "bold".equalsIgnoreCase(style.getOrDefault("font-weight", "normal"));

        double x = col * cellWidth;
        double y = row * cellHeight;
        double w = cellWidth;
        double h = cellHeight;

        // ФОН: перекрываем только по X (±1 px), по Y — ровно
        if (!"transparent".equalsIgnoreCase(bgColor)) {
            gc.setFill(getCachedColor(bgColor));

            double ox = Math.max(0, x - 1);
            double oy = Math.max(0, y - 1);
            double ow = Math.min(getWidth()  - ox, w + 2);
            double oh = Math.min(getHeight() - oy, h + 2);

            gc.fillRect(ox, oy, ow, oh);
        }

        // Псевдографика?
        String ch = cell.character();
        if (isBoxDrawingChar(ch)) {
            drawBoxCharacter(gc, ch.charAt(0), x, y, w, h, fillColor, false /* isCursor - больше не здесь */);
        } else {
            if (ch != null && !ch.isBlank()) {
                gc.setFont(bold ? boldFont : normalFont);
                gc.setFill(getCachedColor(fillColor));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText(ch, x + w / 2.0, y + h / 2.0);
            }
            if (underline) {
                gc.setStroke(getCachedColor(fillColor));
                gc.setLineWidth(1);
                gc.strokeLine(x, y + h - 1, x + w, y + h - 1);
            }
        }
    }



    /* ===================== Псевдографика ===================== */

    private boolean isBoxDrawingChar(String ch) {
        return ch != null && ch.length() == 1 && "┌┐└┘├┤┬┴┼│─".indexOf(ch.charAt(0)) >= 0;
    }

    private void drawCursorOverlay(GraphicsContext gc) {
        if (!cursorVisible || cursorRow < 0 || cursorCol < 0) return;

        // Берём цвет обводки из текущего стиля ячейки под курсором (как раньше)
        Cell cell = screenBuffer.getVisibleCell(cursorRow, cursorCol);
        Map<String, String> style = StyleUtils.parseStyleString(normalizeStyle(cell.style()));
        String fillColor = style.getOrDefault("fill", "white");

        double x = cursorCol * cellWidth;
        double y = cursorRow * cellHeight;
        double w = cellWidth;
        double h = cellHeight;

        gc.setFill(getCachedColor(fillColor));
        // Четыре 1-px полосы по периметру — строго по целым координатам
        gc.fillRect(x,       y,       w, 1);   // top
        gc.fillRect(x,       y+h-1,   w, 1);   // bottom
        gc.fillRect(x,       y,       1, h);   // left
        gc.fillRect(x+w-1,   y,       1, h);   // right
    }


    private void drawBoxCharacter(GraphicsContext gc,
                                  char c, double x, double y, double w, double h,
                                  String fillColor, boolean isCursor) {
        Color lineColor = getCachedColor(fillColor);
        if (isCursor) lineColor = lineColor.brighter();

        gc.setStroke(lineColor);
        gc.setLineWidth(1.5);

        double left = Math.floor(x) + 0.5;
        double top = Math.floor(y) + 0.5;
        double right = Math.floor(x + w) - 0.5;
        double bottom = Math.floor(y + h) - 0.5;
        double midX = Math.floor((x + x + w) / 2.0) + 0.5;
        double midY = Math.floor((y + y + h) / 2.0) + 0.5;

        switch (c) {
            case '─' -> gc.strokeLine(left, midY, right, midY);
            case '│' -> gc.strokeLine(midX, top, midX, bottom);
            case '┌' -> { gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); }
            case '┐' -> { gc.strokeLine(left, midY, midX, midY); gc.strokeLine(midX, midY, midX, bottom); }
            case '└' -> { gc.strokeLine(midX, top, midX, midY); gc.strokeLine(midX, midY, right, midY); }
            case '┘' -> { gc.strokeLine(midX, top, midX, midY); gc.strokeLine(left, midY, midX, midY); }
            case '├' -> { gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, top, midX, bottom); }
            case '┤' -> { gc.strokeLine(left, midY, midX, midY); gc.strokeLine(midX, top, midX, bottom); }
            case '┬' -> { gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); }
            case '┴' -> { gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, top, midX, midY); }
            case '┼' -> { gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, top, midX, bottom); }
        }

        // Рамка курсора поверх (если нужно)
        if (isCursor) {
            gc.setStroke(lineColor);
            gc.setLineWidth(2);
            gc.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
        }
    }

    /* ===================== Цвета ===================== */

    private Color getCachedColor(String cssHexOrName) {
        String key = cssHexOrName.toLowerCase();
        return colorCache.computeIfAbsent(key, k -> Color.web(cssHexOrName, 1.0));
    }

    /* ===================== Выделение/копирование ===================== */

    private boolean isWithinSelection(int row, int col) {
        if (selectionStartRow == null || selectionEndRow == null) return false;
        int sr = Math.min(selectionStartRow, selectionEndRow);
        int er = Math.max(selectionStartRow, selectionEndRow);
        int sc = Math.min(selectionStartCol, selectionEndCol);
        int ec = Math.max(selectionStartCol, selectionEndCol);
        return row >= sr && row <= er && col >= sc && col <= ec;
    }

    private void beginSelection(double x, double y) {
        clearSelectionInternal(false);
        selectionStartCol = (int) (x / cellWidth);
        selectionStartRow = (int) (y / cellHeight);
        selectionEndCol = selectionStartCol;
        selectionEndRow = selectionStartRow;
        isSelecting = true;
        markRowDirty(selectionStartRow);
    }

    private void updateSelection(double x, double y) {
        int oldEndRow = selectionEndRow == null ? -1 : selectionEndRow;
        int oldEndCol = selectionEndCol == null ? -1 : selectionEndCol;

        int newEndCol = (int) (x / cellWidth);
        int newEndRow = (int) (y / cellHeight);

        if (newEndCol != oldEndCol) {
            markRowDirty(selectionEndRow);
        }
        if (newEndRow != oldEndRow) {
            markSelectionRangeDirty(selectionStartRow, oldEndRow);
            markSelectionRangeDirty(selectionStartRow, newEndRow);
        }

        selectionEndCol = newEndCol;
        selectionEndRow = newEndRow;
    }

    private void endSelection(double x, double y) {
        updateSelection(x, y);
        isSelecting = false;
        markSelectionRangeDirty(selectionStartRow, selectionEndRow);
    }

    public void clearSelection() {
        clearSelectionInternal(true);
    }

    private void clearSelectionInternal(boolean markDirty) {
        if (markDirty && selectionStartRow != null && selectionEndRow != null) {
            markSelectionRangeDirty(selectionStartRow, selectionEndRow);
        }
        selectionStartRow = selectionStartCol = null;
        selectionEndRow   = selectionEndCol   = null;
    }

    public String getSelectedText() {
        if (selectionStartRow == null || selectionEndRow == null) return "";
        int sr = Math.min(selectionStartRow, selectionEndRow);
        int er = Math.max(selectionStartRow, selectionEndRow);
        int sc = Math.min(selectionStartCol, selectionEndCol);
        int ec = Math.max(selectionStartCol, selectionEndCol);
        StringBuilder sb = new StringBuilder();
        for (int r = sr; r <= er; r++) {
            for (int c = sc; c <= ec; c++) {
                sb.append(screenBuffer.getVisibleCell(r, c).character());
            }
            if (r < er) sb.append("\n");
        }
        return sb.toString();
    }

    /* ===================== Ввод/контекстное меню ===================== */

    private void initMouseHandlers() {
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                beginSelection(e.getX(), e.getY());
                updateScreen();
            }
            e.consume();
        });

        setOnMouseDragged(e -> {
            if (isSelecting) {
                updateSelection(e.getX(), e.getY());
                updateScreen();
            }
            e.consume();
        });

        setOnMouseReleased(e -> {
            if (isSelecting) {
                endSelection(e.getX(), e.getY());
                updateScreen();
            }
            e.consume();
        });

        setOnContextMenuRequested(e -> {
            if (contextMenu != null) {
                contextMenu.show(this, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
    }

    private void initKeyHandlers() {
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (contextMenu != null && contextMenu.isShowing()) {
                e.consume();
                return;
            }
            if (e.isControlDown() && e.getCode() == KeyCode.C) {
                String selectedText = getSelectedText();
                if (!selectedText.isEmpty()) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(selectedText);
                    Clipboard.getSystemClipboard().setContent(content);
                }
                e.consume();
            }
        });
    }

    private void initContextMenu() {
        contextMenu = new ContextMenu();
        Label label = new Label("Kopieren");
        label.getStyleClass().add("copy-button-label");
        StackPane container = new StackPane(label);
        container.getStyleClass().add("copy-button-container");

        MenuItem copyItem = new MenuItem();
        copyItem.setGraphic(container);
        copyItem.setOnAction(e -> {
            String selected = getSelectedText();
            if (!selected.isEmpty()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(selected);
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        contextMenu.getItems().add(copyItem);
    }

    private void initSceneMouseFilter() {
        sceneProperty().addListener((ObservableValue<? extends Scene> obs, Scene oldScene, Scene newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (contextMenu != null && contextMenu.isShowing()) {
                        contextMenu.hide();
                        event.consume();
                    }
                });
            }
        });
    }

    /* ===================== Публичный API для курсора ===================== */

    public void setCursorPosition(int row, int col) {
        if (row != cursorRow) {
            markRowDirty(cursorRow);
            markRowDirty(row);
        }
        if (col != cursorCol) {
            markRowDirty(cursorRow);
            markRowDirty(row);
        }
        this.cursorRow = row;
        this.cursorCol = col;
    }

    public void setCursorVisible(boolean visible) {
        if (this.cursorVisible != visible) {
            this.cursorVisible = visible;
            markRowDirty(cursorRow);
        }
    }

    /* ===================== Утилиты ===================== */

    public void forceFullRedraw() {
        markAllDirty();
        updateScreen();
    }

    /** Вызывайте на тик мигания курсора — грязним только две строки (старая/новая). */
    public void onBlinkTick() {
        markRowDirty(cursorRow);
        markRowDirty(prevCursorRow);
        updateScreen();
    }

    private boolean equalsNullSafe(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    /**
     * Нормализуем строку стиля:
     * - приводим возможные ключи к каноническим: fill/background/underline/font-weight
     * - убираем лишние пробелы
     * Прим.: StyleUtils.parseStyleString должен поддерживать и старые ключи (-fx-fill, -rtfx-background-color) → map в новые.
     */
    private String normalizeStyle(String raw) {
        if (raw == null || raw.isBlank()) return "fill: white; background: transparent;";
        // Мини-санитайзер: трим и одинарные пробелы после ';'
        String s = raw.trim().replaceAll("\\s*;\\s*", "; ").replaceAll("\\s*:\\s*", ": ");
        // Не насильно переписываем ключи здесь — парсер занимается маппингом,
        // но нормализованная строка поможет для equals и кэша.
        return s;
    }
}