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

public class TerminalCanvas extends Canvas {
    private final ScreenBuffer screenBuffer;
    private double cellWidth;
    private double cellHeight;

    public boolean cursorVisible = true;
    private int cursorRow = -1, cursorCol = -1;

    private Integer selectionStartRow, selectionStartCol;
    private Integer selectionEndRow, selectionEndCol;
    private boolean isSelecting = false;

    private final Map<String, Color> colorCache = new HashMap<>();
    private Font normalFont, boldFont;
    private double lastFontHeight = -1;
    private ContextMenu contextMenu;

    public TerminalCanvas(ScreenBuffer screenBuffer, double width, double height) {
        super(width, height);
        this.screenBuffer = screenBuffer;
        recalcCellDimensions();
        initMouseHandlers();
        initKeyHandlers();
        initContextMenu();
        initSceneMouseFilter();
        setFocusTraversable(true);
        getStyleClass().add("terminal-canvas");
    }

    private void recalcCellDimensions() {
        int cols = screenBuffer.getColumns();
        int rows = screenBuffer.getRows();
        cellWidth = Math.floor(getWidth() / cols);
        cellHeight = Math.floor(getHeight() / rows);

        if (cellHeight != lastFontHeight) {
            normalFont = Font.font("Consolas", cellHeight * 0.8);
            boldFont = Font.font("Consolas", FontWeight.BOLD, cellHeight * 0.8);
            lastFontHeight = cellHeight;
        }
    }

    public void updateScreen() {
        recalcCellDimensions();
        setWidth(cellWidth * screenBuffer.getColumns());
        setHeight(cellHeight * screenBuffer.getRows());

        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        int rows = screenBuffer.getRows();
        int cols = screenBuffer.getColumns();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double x = Math.floor(col * cellWidth);
                double y = Math.floor(row * cellHeight);
                double w = Math.ceil(cellWidth);
                double h = Math.ceil(cellHeight);

                Cell cell = screenBuffer.getCell(row, col);
                String ch = cell.character();

                Map<String, String> style = StyleUtils.parseStyleString(cell.style());
                String fillColor = style.getOrDefault("fill", "white");
                String bgColor = style.getOrDefault("background", "transparent");
                boolean underline = "true".equalsIgnoreCase(style.getOrDefault("underline", "false"));
                boolean bold = "bold".equalsIgnoreCase(style.getOrDefault("font-weight", "normal"));

                boolean isCursor = (row == cursorRow && col == cursorCol && cursorVisible);

                if (!"transparent".equalsIgnoreCase(bgColor)) {
                    gc.setFill(getCachedColor(bgColor, 1));
                    gc.fillRect(x, y, w, h);
                }

                if (isBoxDrawingChar(ch)) {
                    drawBoxCharacter(gc, ch.charAt(0), x, y, w, h, bgColor, fillColor, isCursor);
                    continue;
                }

                if (isCursor) {
                    gc.setStroke(getCachedColor(fillColor, 1));
                    gc.setLineWidth(2);
                    gc.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
                }

                if (!ch.isBlank()) {
                    gc.setFont(bold ? boldFont : normalFont);
                    gc.setFill(getCachedColor(fillColor, 1));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.setTextBaseline(VPos.CENTER);
                    gc.fillText(ch, x + w / 2, y + h / 2);
                }

                if (underline) {
                    gc.setStroke(getCachedColor(fillColor, 1));
                    gc.setLineWidth(1);
                    gc.strokeLine(x, y + h - 2, x + w, y + h - 2);
                }

                if (isWithinSelection(row, col)) {
                    gc.setFill(Color.rgb(255, 140, 0, 0.4));
                    gc.fillRect(x, y, w, h);
                }
            }
        }
    }

    private void drawBoxCharacter(GraphicsContext gc, char c, double x, double y, double w, double h,
                                  String backgroundColor, String fillColor, boolean isCursor) {
        gc.setFill("transparent".equals(backgroundColor) ? Color.TRANSPARENT : getCachedColor(backgroundColor, 1));
        gc.fillRect(x, y, w, h);

        Color lineColor = getCachedColor(fillColor, 1);
        if (isCursor) lineColor = lineColor.brighter();
        gc.setStroke(lineColor);
        gc.setLineWidth(1.5);

        double midX = Math.floor(x + w / 2) + 0.5;
        double midY = Math.floor(y + h / 2) + 0.5;
        double left = Math.floor(x) + 0.5;
        double top = Math.floor(y) + 0.5;
        double right = Math.floor(x + w) - 0.5;
        double bottom = Math.floor(y + h) - 0.5;

        switch (c) {
            case '─': gc.strokeLine(left, midY, right, midY); break;
            case '│': gc.strokeLine(midX, top, midX, bottom); break;
            case '┌': gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); break;
            case '┐': gc.strokeLine(left, midY, midX, midY); gc.strokeLine(midX, midY, midX, bottom); break;
            case '└': gc.strokeLine(midX, top, midX, midY); gc.strokeLine(midX, midY, right, midY); break;
            case '┘': gc.strokeLine(midX, top, midX, midY); gc.strokeLine(left, midY, midX, midY); break;
            case '├': gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, top, midX, bottom); break;
            case '┤': gc.strokeLine(left, midY, midX, midY); gc.strokeLine(midX, top, midX, bottom); break;
            case '┬': gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); break;
            case '┴': gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, top, midX, midY); break;
            case '┼': gc.strokeLine(left, midY, right, midY); gc.strokeLine(midX, top, midX, bottom); break;
        }
    }

    private Color getCachedColor(String hex, double opacity) {
        String key = hex + ":" + opacity;
        return colorCache.computeIfAbsent(key, k -> Color.web(hex, opacity));
    }

    private boolean isBoxDrawingChar(String ch) {
        return "┌┐└┘├┤┬┴┼│─".contains(ch);
    }

    private boolean isWithinSelection(int row, int col) {
        if (selectionStartRow == null || selectionEndRow == null) return false;
        int startRow = Math.min(selectionStartRow, selectionEndRow);
        int endRow = Math.max(selectionStartRow, selectionEndRow);
        int startCol = Math.min(selectionStartCol, selectionEndCol);
        int endCol = Math.max(selectionStartCol, selectionEndCol);
        return row >= startRow && row <= endRow && col >= startCol && col <= endCol;
    }

    private void initMouseHandlers() {
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                clearSelection();
                selectionStartCol = (int) (e.getX() / cellWidth);
                selectionStartRow = (int) (e.getY() / cellHeight);
                selectionEndCol = selectionStartCol;
                selectionEndRow = selectionStartRow;
                isSelecting = true;
            }
            e.consume();
        });

        setOnMouseDragged(e -> {
            if (isSelecting) {
                selectionEndCol = (int) (e.getX() / cellWidth);
                selectionEndRow = (int) (e.getY() / cellHeight);
                updateScreen();
            }
            e.consume();
        });

        setOnMouseReleased(e -> {
            if (isSelecting) {
                selectionEndCol = (int) (e.getX() / cellWidth);
                selectionEndRow = (int) (e.getY() / cellHeight);
                isSelecting = false;
                if (selectionStartRow.equals(selectionEndRow) && selectionStartCol.equals(selectionEndCol)) {
                    clearSelection();
                }
                updateScreen();
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

    public void setCursorPosition(int row, int col) {
        this.cursorRow = row;
        this.cursorCol = col;
    }

    public String getSelectedText() {
        if (selectionStartRow == null || selectionEndRow == null) return "";
        int startRow = Math.min(selectionStartRow, selectionEndRow);
        int endRow = Math.max(selectionStartRow, selectionEndRow);
        int startCol = Math.min(selectionStartCol, selectionEndCol);
        int endCol = Math.max(selectionStartCol, selectionEndCol);

        StringBuilder sb = new StringBuilder();
        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                sb.append(screenBuffer.getCell(row, col).character());
            }
            if (row < endRow) sb.append("\n");
        }
        return sb.toString();
    }

    private void clearSelection() {
        selectionStartRow = null;
        selectionStartCol = null;
        selectionEndRow = null;
        selectionEndCol = null;
        updateScreen();
    }
}
