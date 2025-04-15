package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;

import java.util.HashMap;
import java.util.Map;

public class TerminalCanvas extends Canvas {

    private final ScreenBuffer screenBuffer;
    private double cellWidth;
    private double cellHeight;

    public boolean cursorVisible = true;
    private int cursorRow = -1;
    private int cursorCol = -1;

    private Integer selectionStartRow = null, selectionStartCol = null;
    private Integer selectionEndRow = null, selectionEndCol = null;
    private boolean isSelecting = false;

    private ContextMenu contextMenu;

    public TerminalCanvas(ScreenBuffer screenBuffer, double width, double height) {
        super(width, height);
        this.screenBuffer = screenBuffer;
        recalcCellDimensions();
        initMouseHandlers();
        initKeyHandlers();
        initContextMenu();
        setFocusTraversable(true);
        initSceneMouseFilter();
        this.getStyleClass().add("terminal-canvas");
    }

    private void initMouseHandlers() {
        this.setOnMousePressed(e -> {
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

        this.setOnMouseDragged(e -> {
            if (isSelecting) {
                selectionEndCol = (int) (e.getX() / cellWidth);
                selectionEndRow = (int) (e.getY() / cellHeight);
                updateScreen();
            }
            e.consume();
        });

        this.setOnMouseReleased(e -> {
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

        this.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                int clickedCol = (int) (e.getX() / cellWidth);
                int clickedRow = (int) (e.getY() / cellHeight);
                selectWordAt(clickedRow, clickedCol);
            }
            e.consume();
        });

        this.setOnContextMenuRequested(e -> {
            contextMenu.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void initKeyHandlers() {
        this.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (contextMenu.isShowing()) {
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

        Label copyLabel = new Label("Kopieren");
        copyLabel.getStyleClass().add("copy-button-label");

        StackPane copyContainer = new StackPane(copyLabel);
        copyContainer.getStyleClass().add("copy-button-container");

        MenuItem copyItem = new MenuItem();
        copyItem.setGraphic(copyContainer);
        copyItem.setOnAction(e -> {
            String selectedText = getSelectedText();
            if (!selectedText.isEmpty()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(selectedText);
                Clipboard.getSystemClipboard().setContent(content);
            }
        });

        contextMenu.getItems().add(copyItem);
    }

    private void initSceneMouseFilter() {
        sceneProperty().addListener((ObservableValue<? extends Scene> obs, Scene oldScene, Scene newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    if (contextMenu.isShowing()) {
                        contextMenu.hide();
                        event.consume();
                    }
                });
            }
        });
    }

    private void recalcCellDimensions() {
        int cols = screenBuffer.getColumns();
        int rows = screenBuffer.getRows();
        cellWidth = getWidth() / cols;
        cellHeight = getHeight() / rows;
    }

    public void updateScreen() {
        recalcCellDimensions();
        GraphicsContext gc = getGraphicsContext2D();

        gc.setFill(Color.web("#002b36", 0.95)); // этот цвет можно заменить на CSS-свойство, если подключить через Background
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        int rows = screenBuffer.getRows();
        int cols = screenBuffer.getColumns();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double x = col * cellWidth;
                double y = row * cellHeight;
                Cell cell = screenBuffer.getCell(row, col);
                String character = cell.character();
                Map<String, String> styleMap = parseStyleString(cell.style());
                String fillColor = styleMap.getOrDefault("fill", "white");
                String backgroundColor = styleMap.getOrDefault("background", "transparent");
                boolean underline = "true".equalsIgnoreCase(styleMap.get("underline"));
                String fontWeight = styleMap.getOrDefault("font-weight", "normal");

                Font cellFont = fontWeight.equalsIgnoreCase("bold") ?
                        Font.font("Consolas", FontWeight.BOLD, cellHeight * 0.8) :
                        Font.font("Consolas", cellHeight * 0.8);

                gc.setFill(backgroundColor.equals("transparent") ? Color.TRANSPARENT : Color.web(backgroundColor));
                gc.fillRect(x, y, cellWidth, cellHeight);

                boolean isCursorCell = (row == cursorRow && col == cursorCol && cursorVisible);

                if (isBoxDrawingChar(character)) {
                    drawBoxCharacter(gc, character.charAt(0), x, y, cellWidth, cellHeight, backgroundColor, fillColor, isCursorCell);
                } else {
                    gc.setFont(cellFont);
                    Color textColor = Color.web(fillColor);

                    if (isCursorCell) {
                        gc.setStroke(textColor);
                        gc.setLineWidth(2);
                        gc.strokeRect(x, y, cellWidth, cellHeight);
                    }

                    gc.setFill(textColor);
                    gc.fillText(character, x + cellWidth / 2, y + cellHeight / 2);

                    if (underline) {
                        gc.setStroke(textColor);
                        gc.setLineWidth(1);
                        double underlineY = y + cellHeight - 2;
                        gc.strokeLine(x, underlineY, x + cellWidth, underlineY);
                    }
                }

                if (isWithinSelection(row, col)) {
                    gc.setFill(Color.rgb(255, 140, 0, 0.4));
                    gc.fillRect(x, y, cellWidth, cellHeight);
                }
            }
        }
    }

    private void selectWordAt(int row, int col) {
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < screenBuffer.getColumns(); c++) {
            line.append(screenBuffer.getCell(row, c).character());
        }

        String lineText = line.toString();
        if (lineText.isEmpty()) return;
        if (col >= lineText.length()) col = lineText.length() - 1;

        int start = col;
        while (start > 0 && !Character.isWhitespace(lineText.charAt(start - 1))) start--;
        int end = col;
        while (end < lineText.length() && !Character.isWhitespace(lineText.charAt(end))) end++;

        selectionStartRow = row;
        selectionStartCol = start;
        selectionEndRow = row;
        selectionEndCol = end - 1;
        updateScreen();
    }

    private void clearSelection() {
        selectionStartRow = null;
        selectionStartCol = null;
        selectionEndRow = null;
        selectionEndCol = null;
        updateScreen();
    }

    private boolean isWithinSelection(int row, int col) {
        if (selectionStartRow == null || selectionEndRow == null) return false;
        int startRow = Math.min(selectionStartRow, selectionEndRow);
        int endRow = Math.max(selectionStartRow, selectionEndRow);
        int startCol = Math.min(selectionStartCol, selectionEndCol);
        int endCol = Math.max(selectionStartCol, selectionEndCol);
        return row >= startRow && row <= endRow && col >= startCol && col <= endCol;
    }

    private Map<String, String> parseStyleString(String style) {
        Map<String, String> styleMap = new HashMap<>();
        if (style != null) {
            String[] styles = style.split(";");
            for (String s : styles) {
                String[] keyValue = s.trim().split(":", 2);
                if (keyValue.length == 2) {
                    styleMap.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return styleMap;
    }

    private boolean isBoxDrawingChar(String ch) {
        return "┌┐└┘├┤┬┴┼│─".contains(ch);
    }

    private void drawBoxCharacter(GraphicsContext gc, char c, double x, double y, double w, double h,
                                  String backgroundColor, String fillColor, boolean isCursorCell) {
        gc.setFill(backgroundColor.equals("transparent") ? Color.TRANSPARENT : Color.web(backgroundColor));
        gc.fillRect(x, y, w, h);
        Color lineColor = Color.web(fillColor);
        if (isCursorCell) lineColor = lineColor.brighter();
        gc.setStroke(lineColor);
        gc.setLineWidth(2);
        double right = x + w, bottom = y + h, midX = x + w / 2, midY = y + h / 2;

        switch (c) {
            case '─': gc.strokeLine(x, midY, right, midY); break;
            case '│': gc.strokeLine(midX, y, midX, bottom); break;
            case '┌': gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, midY, midX, bottom); break;
            case '┐': gc.strokeLine(x, midY, midX, midY); gc.strokeLine(midX, midY, midX, bottom); break;
            case '└': gc.strokeLine(midX, y, midX, midY); gc.strokeLine(midX, midY, right, midY); break;
            case '┘': gc.strokeLine(midX, y, midX, midY); gc.strokeLine(x, midY, midX, midY); break;
            case '├': gc.strokeLine(midX, midY, right, midY); gc.strokeLine(midX, y, midX, bottom); break;
            case '┤': gc.strokeLine(x, midY, midX, midY); gc.strokeLine(midX, y, midX, bottom); break;
            case '┬': gc.strokeLine(midX, midY, midX, bottom); gc.strokeLine(x, midY, right, midY); break;
            case '┴': gc.strokeLine(midX, y, midX, midY); gc.strokeLine(x, midY, right, midY); break;
            case '┼': gc.strokeLine(x, midY, right, midY); gc.strokeLine(midX, y, midX, bottom); break;
        }
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
}
