package org.msv.vt100.ANSIISequences;

import java.util.HashMap;
import java.util.Map;

public class CharsetSwitchHandler {

    // Перелік можливих наборів символів
    public enum CharsetMode {
        ASCII, GRAPHICS
    }

    // Поточний набір символів
    private CharsetMode currentCharset;

    // Таблиця відповідностей для графічних символів
    private static final Map<Character, Character> graphicsCharMap = new HashMap<>();

    static {
        graphicsCharMap.put('j', '┘'); // ┘
        graphicsCharMap.put('k', '┐'); // ┐
        graphicsCharMap.put('l', '┌'); // ┌
        graphicsCharMap.put('m', '└'); // └
        graphicsCharMap.put('n', '┼'); // ┼
        graphicsCharMap.put('q', '─'); // ─
        graphicsCharMap.put('t', '├'); // ├
        graphicsCharMap.put('u', '┤'); // ┤
        graphicsCharMap.put('v', '┴'); // ┴
        graphicsCharMap.put('w', '┬'); // ┬
        graphicsCharMap.put('x', '│'); // │
        // За потреби можна додати інші відповідності
    }

    public CharsetSwitchHandler() {
        // За замовчуванням – ASCII
        this.currentCharset = CharsetMode.ASCII;
    }

    public void switchToASCIICharset() {
        currentCharset = CharsetMode.ASCII;
    }

    public void switchToGraphicsCharset() {
        currentCharset = CharsetMode.GRAPHICS;
    }

    // Додаємо метод для отримання поточного режиму
    public CharsetMode getCurrentCharset() {
        return currentCharset;
    }

    // Якщо режим GRAPHICS, для заданого тексту виконуємо конвертацію символів
    public String processText(String text) {
        if (currentCharset == CharsetMode.GRAPHICS) {
            return convertToGraphicsCharset(text);
        }
        return text;
    }

    // Приватний метод конвертації всього рядка (як раніше)
    private String convertToGraphicsCharset(String text) {
        StringBuilder result = new StringBuilder();

        for (char currentChar : text.toCharArray()) {
            if (graphicsCharMap.containsKey(currentChar)) {
                result.append(graphicsCharMap.get(currentChar));
            } else {
                result.append(currentChar);
            }
        }
        return result.toString();
    }

    // Статичний метод для конвертації одного символу за графічною таблицею
    public static char convertChar(char c) {
        return graphicsCharMap.getOrDefault(c, c);
    }
}
