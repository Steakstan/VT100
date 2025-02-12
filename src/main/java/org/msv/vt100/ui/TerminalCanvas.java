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

public class TerminalCanvas extends Canvas {

    private final ScreenBuffer screenBuffer;
    private double cellWidth;
    private double cellHeight;

    // Поля для відображення курсора
    public boolean cursorVisible = true;
    private int cursorRow = -1;
    private int cursorCol = -1;

    public TerminalCanvas(ScreenBuffer screenBuffer, double width, double height) {
        super(width, height);
        this.screenBuffer = screenBuffer;
        recalcCellDimensions();
    }

    private void recalcCellDimensions() {
        int cols = screenBuffer.getColumns();
        int rows = screenBuffer.getRows();
        cellWidth  = getWidth()  / cols;
        cellHeight = getHeight() / rows;
        double newFontSize = cellHeight * 0.8;
        // Базовий шрифт – без особливостей форматування, його будемо перевизначати для кожної комірки
        Font font = Font.font("Consolas", newFontSize);
    }

    public void updateScreen() {
        recalcCellDimensions();
        GraphicsContext gc = getGraphicsContext2D();

        // Очищення фону всієї канви
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
                String character = cell.getCharacter();
                String style = cell.getStyle();

                // Парсимо рядок стилів, який формується в TextFormater, наприклад:
                // "fill: white; background: transparent; underline: true; font-weight: bold;"
                Map<String, String> styleMap = parseStyleString(style);
                String fillColor = styleMap.getOrDefault("fill", "white");
                String backgroundColor = styleMap.getOrDefault("background", "transparent");
                boolean underline = "true".equalsIgnoreCase(styleMap.get("underline"));
                String fontWeight = styleMap.getOrDefault("font-weight", "normal");

                // Формуємо шрифт для комірки залежно від жирності
                Font cellFont;
                if (fontWeight.equalsIgnoreCase("bold")) {
                    cellFont = Font.font("Consolas", FontWeight.BOLD, cellHeight * 0.8);
                } else {
                    cellFont = Font.font("Consolas", cellHeight * 0.8);
                }

                // Малюємо фон комірки
                gc.setFill(backgroundColor.equals("transparent") ? Color.TRANSPARENT : Color.web(backgroundColor));
                gc.fillRect(x, y, cellWidth, cellHeight);

                // Перевіряємо, чи знаходиться курсор у цій комірці
                boolean isCursorCell = (row == cursorRow && col == cursorCol && cursorVisible);

                // Якщо символ – псевдографічний (наприклад, "┌", "─" тощо),
                // він уже має бути перетворений класом CharsetSwitchHandler, тому перевіряємо лише набір Unicode‑символів.
                if (isBoxDrawingChar(character)) {
                    char c = character.charAt(0);
                    drawBoxCharacter(gc, c, x, y, cellWidth, cellHeight, backgroundColor, fillColor, isCursorCell);
                } else {
                    // Малюємо звичайний текст з використанням заданого шрифту та кольорів
                    gc.setFont(cellFont);
                    Color textColor = Color.web(fillColor);
                    if (isCursorCell) {
                        gc.setStroke(textColor);
                        gc.setLineWidth(2);
                        gc.strokeRect(x, y, cellWidth, cellHeight);
                    }
                    gc.setFill(textColor);
                    gc.fillText(character, x + cellWidth / 2, y + cellHeight / 2);

                    // Якщо задано підкреслення – малюємо лінію внизу комірки
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
     * Метод для визначення, чи є рядок символом псевдографіки.
     * Тут перевіряємо лише Unicode-символи, які має показувати термінал.
     */
    private boolean isBoxDrawingChar(String ch) {
        return "┌┐└┘├┤┬┴┼│─".contains(ch);
    }

    /**
     * Малює псевдографічний символ у межах комірки.
     * Оскільки символи вже оброблені класом CharsetSwitchHandler,
     * тут не потрібно робити додаткову конвертацію – використовується саме отриманий символ.
     */
    private void drawBoxCharacter(GraphicsContext gc,
                                  char c,
                                  double x, double y,
                                  double w, double h,
                                  String backgroundColor,
                                  String fillColor,
                                  boolean isCursorCell) {
        // Малюємо фон комірки
        gc.setFill(backgroundColor.equals("transparent") ? Color.TRANSPARENT : Color.web(backgroundColor));
        gc.fillRect(x, y, w, h);

        Color lineColor = Color.web(fillColor);
        if (isCursorCell) {
            lineColor = lineColor.brighter();
        }
        gc.setStroke(lineColor);
        gc.setLineWidth(2);

        double right  = x + w;
        double bottom = y + h;
        double midX   = x + w / 2.0;
        double midY   = y + h / 2.0;

        switch (c) {
            case '─': // Горизонтальна лінія
                gc.strokeLine(x, midY, right, midY);
                break;
            case '│': // Вертикальна лінія
                gc.strokeLine(midX, y, midX, bottom);
                break;
            case '┌': // Верхній лівий кут
                gc.strokeLine(midX, midY, right, midY);
                gc.strokeLine(midX, midY, midX, bottom);
                break;
            case '┐': // Верхній правий кут
                gc.strokeLine(x, midY, midX, midY);
                gc.strokeLine(midX, midY, midX, bottom);
                break;
            case '└': // Нижній лівий кут
                gc.strokeLine(midX, y, midX, midY);
                gc.strokeLine(midX, midY, right, midY);
                break;
            case '┘': // Нижній правий кут
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
     * Допоміжний метод для парсингу рядка стилів.
     * Рядок стилів очікується у форматі: "fill: white; background: transparent; underline: true; font-weight: bold;"
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

    public void setCursorPosition(int row, int col) {
        this.cursorRow = row;
        this.cursorCol = col;
    }
}