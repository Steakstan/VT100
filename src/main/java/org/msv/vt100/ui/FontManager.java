package org.msv.vt100.ui;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FontManager {
    private Font normalFont, boldFont;
    private double lastFontCellH = -1;
    private final String family;

    FontManager() {
        this.family = chooseFontFamily();
    }

    boolean updateForCellHeight(double cellHeight) {
        if (cellHeight == lastFontCellH) return false;
        double px = computeFontPxForCellHeight(family, cellHeight);
        normalFont = Font.font(family, px);
        boldFont = Font.font(family, FontWeight.BOLD, px);
        lastFontCellH = cellHeight;
        return true;
    }

    Font normal() { return normalFont; }
    Font bold() { return boldFont; }

    private static String chooseFontFamily() {
        List<String> preferred = Arrays.asList(
                "Cascadia Mono", "JetBrains Mono", "DejaVu Sans Mono", "Consolas", "Monospaced");
        Set<String> available = new HashSet<>(Font.getFamilies());
        for (String f : preferred) if (available.contains(f)) return f;
        return Font.getDefault().getFamily();
    }

    private static double computeFontPxForCellHeight(String family, double cellH) {
        double low = 6, high = Math.max(8, cellH);
        double best = Math.max(8, Math.floor(cellH * 0.82));
        for (int i = 0; i < 6; i++) {
            double mid = (low + high) / 2.0;
            Text t = new Text("Mg");
            t.setFont(Font.font(family, mid));
            double h = Math.ceil(t.getLayoutBounds().getHeight());
            if (h <= cellH * 0.96) {
                best = mid;
                low = mid;
            } else {
                high = mid;
            }
        }
        return Math.floor(best);
    }
}
