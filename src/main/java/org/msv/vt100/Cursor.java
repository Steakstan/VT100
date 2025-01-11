package org.msv.vt100;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Cursor {
    private int row;
    private int column;
    private final int maxRows;
    private final int maxColumns;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition positionChanged = lock.newCondition();

    public Cursor(int maxRows, int maxColumns) {
        this.row = 0;      // Начальная строка
        this.column = 0;   // Начальная колонка
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    // Метод для установки позиции курсора
    public void setPosition(int row, int column) {
        lock.lock();
        try {
            this.row = Math.max(0, Math.min(row, maxRows - 1));
            this.column = Math.max(0, Math.min(column, maxColumns - 1));
            positionChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    // Перемещаем курсор вправо
    public void moveRight() {
        if (column < maxColumns - 1) {
            column++;
        }
        // Не переходим на новую строку автоматически
    }

    // Перемещаем курсор влево
    public void moveLeft() {
        if (column > 0) {
            column--;
        } else if (row > 0) {
            row--;
            column = maxColumns - 1;
        }
    }

    public void moveToColumnStart() {
        this.column = 0;
    }

    String invertColors(String style) {
        Map<String, String> styleMap = parseStyleString(style);

        String fillColor = styleMap.get("-fx-fill");
        String backgroundColor = styleMap.get("-rtfx-background-color");

        if (fillColor != null && backgroundColor != null) {
            // Инвертируем цвета
            styleMap.put("-fx-fill", backgroundColor);
            styleMap.put("-rtfx-background-color", fillColor);
        } else {
            // Если цвета не заданы, устанавливаем контрастные значения
            styleMap.put("-fx-fill", "black");
            styleMap.put("-rtfx-background-color", "white");
        }

        return buildStyleString(styleMap);
    }

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

    private String buildStyleString(Map<String, String> styleMap) {
        StringBuilder styleBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : styleMap.entrySet()) {
            styleBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
        }
        return styleBuilder.toString().trim();
    }
    public String getCursorPosition() {
        lock.lock();
        try {
            return String.valueOf(row + 1) + (column + 1);
        } finally {
            lock.unlock();
        }
    }
    public void waitForPosition(String desiredPosition) throws InterruptedException {
        lock.lock();
        try {
            while (!getCursorPosition().equals(desiredPosition)) {
                positionChanged.await();
            }
        } finally {
            lock.unlock();
        }
    }

}