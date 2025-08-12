package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TerminalCanvas extends Canvas {

    private final ScreenBuffer screenBuffer;

    private double cellWidth;
    private double cellHeight;

    public boolean cursorVisible = true;
    private int cursorRow = -1, cursorCol = -1;
    private int prevCursorRow = -1, prevCursorCol = -1;
    private boolean prevCursorVisible = false;

    private String[][] lastChars;
    private int[][] lastStyleIds;

    private final DirtyTracker dirty;
    private final SelectionModel selection;
    private final StyleRegistry styles;
    private final FontManager fonts;
    private final TerminalRenderer renderer;

    private ContextMenu contextMenu;

    private double[] rowEdges;

    public TerminalCanvas(ScreenBuffer screenBuffer, double width, double height) {
        super(width, height);
        this.screenBuffer = Objects.requireNonNull(screenBuffer, "screenBuffer");
        this.styles = new StyleRegistry();
        this.fonts = new FontManager();
        this.selection = new SelectionModel();
        this.dirty = new DirtyTracker(screenBuffer.getRows());
        initBuffers();
        recalcCellDimensions(true);
        recomputeRowEdges();
        this.renderer = new TerminalRenderer(styles, selection, fonts);
        initMouseHandlers();
        initKeyHandlers();
        initContextMenu();
        initSceneMouseFilter();
        setFocusTraversable(true);
        getStyleClass().add("terminal-canvas");

        widthProperty().addListener((obs, ov, nv) -> {
            if (!Objects.equals(ov, nv)) {
                recalcCellDimensions(false);
                recomputeRowEdges();
                dirty.markAllDirty();
                updateScreen();
            }
        });
        heightProperty().addListener((obs, ov, nv) -> {
            if (!Objects.equals(ov, nv)) {
                recalcCellDimensions(false);
                recomputeRowEdges();
                dirty.markAllDirty();
                updateScreen();
            }
        });
    }

    private void initBuffers() {
        int rows = screenBuffer.getRows();
        int cols = screenBuffer.getColumns();
        lastChars = new String[rows][cols];
        lastStyleIds = new int[rows][cols];
        dirty.markAllDirty();
    }

    private void ensureBuffersSize() {
        int rows = screenBuffer.getRows();
        int cols = screenBuffer.getColumns();
        if (lastChars.length != rows || lastChars[0].length != cols) {
            lastChars = new String[rows][cols];
            lastStyleIds = new int[rows][cols];
            dirty.ensureSize(rows);
            dirty.markAllDirty();
            recomputeRowEdges();
        }
    }

    private void recalcCellDimensions(boolean forceDirty) {
        int cols = screenBuffer.getColumns();
        int rows = screenBuffer.getRows();
        if (cols <= 0 || rows <= 0) return;

        double newCW = Math.floor(getWidth() / cols);
        double newCH = Math.floor(getHeight() / rows);
        if (newCW <= 0 || newCH <= 0) return;

        boolean sizeChanged = (newCW != cellWidth) || (newCH != cellHeight);
        cellWidth = newCW;
        cellHeight = newCH;

        if (sizeChanged || forceDirty) {
            if (fonts.updateForCellHeight(cellHeight)) {
                dirty.markAllDirty();
            }
        }

        double targetW = Math.floor(cellWidth * cols);
        double targetH = Math.floor(cellHeight * rows);
        if (Math.abs(getWidth() - targetW) >= 1.0) setWidth(targetW);
        if (Math.abs(getHeight() - targetH) >= 1.0) setHeight(targetH);
    }

    private void recomputeRowEdges() {
        int rows = screenBuffer.getRows();
        if (rows <= 0) return;
        if (rowEdges == null || rowEdges.length != rows + 1) {
            rowEdges = new double[rows + 1];
        }
        rowEdges[0] = 0.0;
        for (int i = 1; i <= rows; i++) {
            rowEdges[i] = Math.floor(i * cellHeight);
        }
    }

    private void markRowDirty(int r) {
        dirty.markRowDirty(r);
        if (r - 1 >= 0) dirty.markRowDirty(r - 1);
        if (r + 1 < screenBuffer.getRows()) dirty.markRowDirty(r + 1);
    }

    public void updateScreen() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateScreen);
            return;
        }
        ensureBuffersSize();
        recalcCellDimensions(false);
        recomputeRowEdges();

        final int rows = screenBuffer.getRows();
        final int cols = screenBuffer.getColumns();
        final GraphicsContext gc = getGraphicsContext2D();
        gc.setTextAlign(TextAlignment.CENTER);

        for (int r = 0; r < rows; r++) {
            if (dirty.isRowDirty(r)) continue;
            for (int c = 0; c < cols; c++) {
                Cell cell = screenBuffer.getVisibleCell(r, c);
                String ch = cell.character();
                int stId = styles.styleIdFor(cell.style());
                if (!Objects.equals(ch, lastChars[r][c]) || stId != lastStyleIds[r][c]) {
                    markRowDirty(r);
                    break;
                }
            }
        }

        if (cursorVisible != prevCursorVisible || cursorRow != prevCursorRow || cursorCol != prevCursorCol) {
            markRowDirty(prevCursorRow);
            markRowDirty(cursorRow);
        }

        List<int[]> bands = new ArrayList<>();
        for (int r = 0; r < rows; ) {
            if (!dirty.isRowDirty(r)) { r++; continue; }
            int start = r;
            while (r < rows && dirty.isRowDirty(r)) r++;
            int end = r - 1;

            int ns = Math.max(0, start - 1);
            int ne = Math.min(rows - 1, end + 1);

            if (!bands.isEmpty()) {
                int[] last = bands.get(bands.size() - 1);
                if (ns <= last[1] + 1) {
                    last[1] = Math.max(last[1], ne);
                } else {
                    bands.add(new int[]{ns, ne});
                }
            } else {
                bands.add(new int[]{ns, ne});
            }
        }

        if (bands.isEmpty()) {
            prevCursorRow = cursorRow;
            prevCursorCol = cursorCol;
            prevCursorVisible = cursorVisible;
            return;
        }

        for (int[] band : bands) {
            int start = band[0];
            int end = band[1];

            double y0 = Math.max(0, Math.floor(rowEdges[start]) - 1);
            double y1 = Math.min(getHeight(), Math.ceil(rowEdges[end + 1]) + 1);
            double bandH = y1 - y0;

            gc.clearRect(0, y0, getWidth(), bandH);

            gc.save();
            gc.beginPath();
            gc.rect(0, y0, getWidth(), bandH);
            gc.clip();


            for (int r = start; r <= end; r++) {
                renderer.renderBackgroundRuns(gc, screenBuffer, r, cellWidth, cellHeight, getWidth(), getHeight());
                renderer.renderSelectionOverlay(gc, r, cellWidth, cellHeight, screenBuffer.getColumns());
                renderer.renderTextAndUnderline(gc, screenBuffer, r, cellWidth, cellHeight);
                renderer.renderBoxChars(gc, screenBuffer, r, cellWidth, cellHeight);


                for (int c = 0; c < cols; c++) {
                    Cell cell = screenBuffer.getVisibleCell(r, c);
                    lastChars[r][c] = cell.character();
                    lastStyleIds[r][c] = styles.styleIdFor(cell.style());
                }
                dirty.clearRow(r);
            }

            gc.restore();
        }

        renderer.drawCursorOverlay(gc, screenBuffer, cursorVisible, cursorRow, cursorCol, cellWidth, cellHeight);

        prevCursorRow = cursorRow;
        prevCursorCol = cursorCol;
        prevCursorVisible = cursorVisible;
    }

    private void initMouseHandlers() {
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selection.beginSelection(e.getX(), e.getY(), cellWidth, cellHeight, dirty);
                updateScreen();
            }
            e.consume();
        });
        setOnMouseDragged(e -> {
            if (selection.isSelecting()) {
                selection.updateSelection(e.getX(), e.getY(), cellWidth, cellHeight, dirty);
                updateScreen();
            }
            e.consume();
        });
        setOnMouseReleased(e -> {
            if (selection.isSelecting()) {
                selection.endSelection(e.getX(), e.getY(), cellWidth, cellHeight, dirty);
                updateScreen();
            }
            e.consume();
        });
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 2) {
                    selection.selectWordAt(e.getX(), e.getY(), cellWidth, cellHeight, screenBuffer, dirty);
                    updateScreen();
                } else if (e.getClickCount() == 3) {
                    selection.selectRowAt(e.getY(), cellHeight, screenBuffer, dirty);
                    updateScreen();
                }
            }
        });
        setOnContextMenuRequested(e -> {
            if (contextMenu != null) contextMenu.show(this, e.getScreenX(), e.getScreenY());
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
                String text = selection.getSelectedText(screenBuffer);
                if (!text.isEmpty()) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    Clipboard.getSystemClipboard().setContent(content);
                }
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.A) {
                selection.selectAll(screenBuffer.getRows(), screenBuffer.getColumns(), dirty);
                updateScreen();
                e.consume();
            }
        });
    }

    private void initContextMenu() {
        contextMenu = new ContextMenu();
        Label copyLabel = new Label("Kopieren");
        copyLabel.getStyleClass().add("buttons");
        StackPane copyContainer = new StackPane(copyLabel);
        copyContainer.getStyleClass().add("copy-button-container");
        MenuItem copyItem = new MenuItem();
        copyItem.setGraphic(copyContainer);
        copyItem.setOnAction(e -> {
            String text = selection.getSelectedText(screenBuffer);
            if (!text.isEmpty()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                Clipboard.getSystemClipboard().setContent(content);
            }
        });
        MenuItem selectAllItem = new MenuItem("Alles auswählen");
        selectAllItem.setOnAction(e -> { selection.selectAll(screenBuffer.getRows(), screenBuffer.getColumns(), dirty); updateScreen(); });
        MenuItem clearSelItem = new MenuItem("Auswahl aufheben");
        clearSelItem.setOnAction(e -> { selection.clearSelection(dirty); updateScreen(); });
        contextMenu.getItems().addAll(copyItem, selectAllItem, clearSelItem);
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

    public String getSelectedText() {
        return selection.getSelectedText(screenBuffer);
    }

    public void setCursorPosition(int row, int col) {
        if (row != cursorRow) { markRowDirty(cursorRow); markRowDirty(row); }
        if (col != cursorCol) { markRowDirty(cursorRow); markRowDirty(row); }
        this.cursorRow = row;
        this.cursorCol = col;
    }

    public void setCursorVisible(boolean visible) {
        if (this.cursorVisible != visible) {
            this.cursorVisible = visible;
            markRowDirty(cursorRow);
        }
    }
}
