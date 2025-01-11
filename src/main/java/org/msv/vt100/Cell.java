package org.msv.vt100;

public class Cell {
    private String character; // Изменено с char на String
    private String style;

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
