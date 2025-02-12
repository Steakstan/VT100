package org.msv.vt100.core;

public class Cell {
    private final String character; // Изменено с char на String
    private final String style;

    public Cell(String character, String style) {
        this.character = character;
        this.style = style;
    }

    public String getCharacter() {
        return character;
    }

    public String getStyle() {
        return style;
    }
}