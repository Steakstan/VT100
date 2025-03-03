package org.msv.vt100.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.msv.vt100.core.Cell;
import org.msv.vt100.core.ScreenBuffer;

import java.util.HashMap;
import java.util.Map;

/**
 * TerminalCanvas is a custom Canvas for rendering the terminal screen.
 * It displays the contents of a ScreenBuffer and handles drawing of characters,
 * box-drawing symbols, and cursor highlighting.
 */
public class TerminalCanvas extends Canvas {

    private final ScreenBuffer screenBuffer;
    private double cellWidth;
    private double cellHeight;

    // Fields for displaying the cursor
    public boolean cursorVisible = true;
    private int cursorRow = -1;
    private int cursorCol = -1;

    /**
     * Constructs a TerminalCanvas with the given screen buffer and dimensions.
     *
     * @param screenBuffer the screen buffer containing terminal cells.
     * @param width        the width of the canvas.
     * @param height       the height of the canvas.
     */
    public TerminalCanvas(ScreenBuffer screenBuffer, double width, double height) {
        super(width, height);
        this.screenBuffer = screenBuffer;
        recalcCellDimensions();
    }

    /**
     * Recalculates the cell dimensions based on the canvas size and the screen buffer.
     */
    private void recalcCellDimensions() {
        int cols = screenBuffer.getColumns();
        int rows = screenBuffer.getRows();
        cellWidth = getWidth() / cols;
        cellHeight = getHeight() / rows;
        double newFontSize = cellHeight * 0.8;
        // Base font – without additional formatting features, which will be overridden per cell
        Font font = Font.font("Consolas", newFontSize);
    }

    /**
     * Updates the terminal screen by recalculating cell dimensions, clearing the canvas,
     * and rendering each cell of the screen buffer.
     */
    public void updateScreen() {
        recalcCellDimensions();
        GraphicsContext gc = getGraphicsContext2D();

        // Clear the background of the entire canvas
        gc.setFill(Color.rgb(0, 43, 54, 0.95));
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
                String style = cell.style();

                Map<String, String> styleMap = parseStyleString(style);
                String fillColor = styleMap.getOrDefault("fill", "white");
                String backgroundColor = styleMap.getOrDefault("background", "transparent");
                boolean underline = "true".equalsIgnoreCase(styleMap.get("underline"));
                String fontWeight = styleMap.getOrDefault("font-weight", "normal");

                Font cellFont;
                if (fontWeight.equalsIgnoreCase("bold")) {
                    cellFont = Font.font("Consolas", FontWeight.BOLD, cellHeight * 0.8);
                } else {
                    cellFont = Font.font("Consolas", cellHeight * 0.8);
                }

                // Draw the cell background
                gc.setFill(backgroundColor.equals("transparent") ? Color.TRANSPARENT : Color.web(backgroundColor));
                gc.fillRect(x, y, cellWidth, cellHeight);

                boolean isCursorCell = (row == cursorRow && col == cursorCol && cursorVisible);

                if (isBoxDrawingChar(character)) {
                    char c = character.charAt(0);
                    drawBoxCharacter(gc, c, x, y, cellWidth, cellHeight, backgroundColor, fillColor, isCursorCell);
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
            }
        }
    }

    /**
     * Determines whether the given character string is a box drawing symbol.
     *
     * @param ch the character string to check.
     * @return true if the string is a box drawing character; false otherwise.
     */
    private boolean isBoxDrawingChar(String ch) {
        return "┌┐└┘├┤┬┴┼│─".contains(ch);
    }

    /**
     * Draws a box-drawing character within a cell.
     *
     * @param gc              the GraphicsContext to draw on.
     * @param c               the box-drawing character.
     * @param x               the x-coordinate of the cell.
     * @param y               the y-coordinate of the cell.
     * @param w               the width of the cell.
     * @param h               the height of the cell.
     * @param backgroundColor the background color of the cell.
     * @param fillColor       the fill color for the box lines.
     * @param isCursorCell    whether this cell is the current cursor cell.
     */
    private void drawBoxCharacter(GraphicsContext gc,
                                  char c,
                                  double x, double y,
                                  double w, double h,
                                  String backgroundColor,
                                  String fillColor,
                                  boolean isCursorCell) {
        // Draw the cell background
        gc.setFill(backgroundColor.equals("transparent") ? Color.TRANSPARENT : Color.web(backgroundColor));
        gc.fillRect(x, y, w, h);

        Color lineColor = Color.web(fillColor);
        if (isCursorCell) {
            lineColor = lineColor.brighter();
        }
        gc.setStroke(lineColor);
        gc.setLineWidth(2);

        double right = x + w;
        double bottom = y + h;
        double midX = x + w / 2.0;
        double midY = y + h / 2.0;

        switch (c) {
            case '─':
                gc.strokeLine(x, midY, right, midY);
                break;
            case '│':
                gc.strokeLine(midX, y, midX, bottom);
                break;
            case '┌':
                gc.strokeLine(midX, midY, right, midY);
                gc.strokeLine(midX, midY, midX, bottom);
                break;
            case '┐':
                gc.strokeLine(x, midY, midX, midY);
                gc.strokeLine(midX, midY, midX, bottom);
                break;
            case '└':
                gc.strokeLine(midX, y, midX, midY);
                gc.strokeLine(midX, midY, right, midY);
                break;
            case '┘':
                gc.strokeLine(midX, y, midX, midY);
                gc.strokeLine(x, midY, midX, midY);
                break;
            case '├':
                gc.strokeLine(midX, midY, right, midY);
                gc.strokeLine(midX, y, midX, bottom);
                break;
            case '┤':
                gc.strokeLine(x, midY, midX, midY);
                gc.strokeLine(midX, y, midX, bottom);
                break;
            case '┬':
                gc.strokeLine(midX, midY, midX, bottom);
                gc.strokeLine(x, midY, right, midY);
                break;
            case '┴':
                gc.strokeLine(midX, y, midX, midY);
                gc.strokeLine(x, midY, right, midY);
                break;
            case '┼':
                gc.strokeLine(x, midY, right, midY);
                gc.strokeLine(midX, y, midX, bottom);
                break;
        }
    }

    /**
     * Parses a style string into a map of style properties.
     *
     * @param style the style string in the format "key: value; key: value; ..."
     * @return a map of style property names to values.
     */
    private Map<String, String> parseStyleString(String style) {
        Map<String, String> styleMap = new HashMap<>();
        String[] styles = style.split(";");
        for (String s : styles) {
            String[] keyValue = s.trim().split(":", 2);
            if (keyValue.length == 2) {
                styleMap.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return styleMap;
    }

    /**
     * Sets the cursor position to be highlighted on the terminal.
     *
     * @param row the row index (0-indexed)
     * @param col the column index (0-indexed)
     */
    public void setCursorPosition(int row, int col) {
        this.cursorRow = row;
        this.cursorCol = col;
    }
}
