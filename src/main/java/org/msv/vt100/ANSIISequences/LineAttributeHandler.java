package org.msv.vt100.ANSIISequences;

import java.util.HashSet;
import java.util.Set;

public class LineAttributeHandler {
    private Set<Integer> doubleWidthLines;

    public LineAttributeHandler() {
        doubleWidthLines = new HashSet<>();
    }

    public void setDoubleWidthLine(int row) {
        doubleWidthLines.add(row);
    }

    public void resetAllLineAttributes() {
        doubleWidthLines.clear();
    }

    public boolean isDoubleWidthLine(int row) {
        return doubleWidthLines.contains(row);
    }

    // Method to get additional style for a line
    public String getLineStyle(int row) {
        if (isDoubleWidthLine(row)) {
            // Define the style for double-width lines
            return "-fx-font-size: 32px; -fx-font-stretch: extra-expanded; -fx-fill: red;";
        } else {
            return ""; // No additional style
        }
    }
}

