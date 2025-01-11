package org.msv.vt100.ANSIISequences;

import java.util.HashMap;
import java.util.Map;

public class CharsetSwitchHandler {

    // Enumeration for possible character sets
    enum CharsetMode {
        ASCII, GRAPHICS
    }

    // Current character set
    private CharsetMode currentCharset;

    // Mapping for graphics characters
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
        // Add more mappings as needed
    }

    public CharsetSwitchHandler() {
        // Default to ASCII
        this.currentCharset = CharsetMode.ASCII;
    }

    // Switch to ASCII charset (ESC (K)
    public void switchToASCIICharset() {
        currentCharset = CharsetMode.ASCII;
    }

    // Switch to graphics charset (ESC (0)
    public void switchToGraphicsCharset() {
        currentCharset = CharsetMode.GRAPHICS;
    }

    // Process text based on current charset
    public String processText(String text) {
        switch (currentCharset) {
            case GRAPHICS:
                return convertToGraphicsCharset(text);
            case ASCII:
            default:
                return text; // In ASCII mode, return text as is
        }
    }

    // Convert text to graphics charset
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
}
