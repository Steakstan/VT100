package org.msv.vt100.ui;

import javafx.scene.paint.Color;
import org.msv.vt100.util.StyleUtils;

import java.util.HashMap;
import java.util.Map;

final class StyleRegistry {
    private final Map<String, Short> paletteIndexByCss = new HashMap<>();
    private final java.util.List<Color> palette = new java.util.ArrayList<>();
    private final Map<String, StyleKey> styleKeyCache = new HashMap<>();
    private final Map<String, Integer> styleIdCache = new HashMap<>();
    private final Map<Integer, StyleKey> idToStyleKey = new HashMap<>();

    short colorIndexFor(String css) {
        if (css == null || css.isBlank()) return colorIndexFor("white");
        String key = css.trim().toLowerCase();
        if ("transparent".equals(key)) return -1;
        Short idx = paletteIndexByCss.get(key);
        if (idx != null) return idx;
        Color c = Color.web(key);
        short newIdx = (short) palette.size();
        palette.add(c);
        paletteIndexByCss.put(key, newIdx);
        return newIdx;
    }

    Color paletteColor(short idx) {
        return palette.get(idx);
    }

    StyleKey styleKeyFor(String rawStyle) {
        String canon = StyleUtils.canonicalize(rawStyle == null ? "" : rawStyle);
        StyleKey cached = styleKeyCache.get(canon);
        if (cached != null) return cached;

        Map<String, String> m = StyleUtils.parseStyleString(canon);
        String fill = m.getOrDefault("fill", "white");
        String bg = m.getOrDefault("background", "transparent");
        boolean underline =
                "true".equalsIgnoreCase(m.getOrDefault("underline", "false")) ||
                        "underline".equalsIgnoreCase(
                                m.getOrDefault("text-decoration", "").trim()
                        );

        boolean bold = "bold".equalsIgnoreCase(m.getOrDefault("font-weight", "normal"));

        short fgIdx = colorIndexFor(fill);
        short bgIdx = colorIndexFor(bg);
        byte flags = 0;
        if (underline) flags |= 1;
        if (bold) flags |= 2;

        StyleKey sk = new StyleKey(fgIdx, bgIdx, flags);
        styleKeyCache.put(canon, sk);
        return sk;
    }

    int styleIdFor(String rawStyle) {
        String canon = StyleUtils.canonicalize(rawStyle == null ? "" : rawStyle);
        Integer id = styleIdCache.get(canon);
        if (id != null) return id;

        StyleKey sk = styleKeyFor(canon);
        int newId = styleIdCache.size() + 1;
        styleIdCache.put(canon, newId);
        idToStyleKey.put(newId, sk);
        return newId;
    }

    static final class StyleKey {
        final short fgIdx;
        final short bgIdx;
        final byte flags;
        final int hash;

        StyleKey(short fgIdx, short bgIdx, byte flags) {
            this.fgIdx = fgIdx;
            this.bgIdx = bgIdx;
            this.flags = flags;
            int h = 17;
            h = 31 * h + fgIdx;
            h = 31 * h + bgIdx;
            h = 31 * h + flags;
            this.hash = h;
        }

        boolean isUnderline() { return (flags & 1) != 0; }
        boolean isBold() { return (flags & 2) != 0; }

        boolean sameTextAttrs(StyleKey other) {
            return other != null && this.fgIdx == other.fgIdx && this.flags == other.flags;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StyleKey sk)) return false;
            return fgIdx == sk.fgIdx && bgIdx == sk.bgIdx && flags == sk.flags;
        }
        @Override public int hashCode() { return hash; }
    }
}
