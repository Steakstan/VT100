package org.msv.vt100.util;

import java.util.HashMap;
import java.util.Map;

public class StyleUtils {

    /**
     * Парсит строку CSS-стилей вида: "fill: white; background: black;"
     * в Map<String, String> → {fill=white, background=black}
     *
     * @param style строка стиля (может быть null или пустая)
     * @return карта ключ-значение всех стилей
     */
    public static Map<String, String> parseStyleString(String style) {
        Map<String, String> styleMap = new HashMap<>();
        if (style == null || style.isBlank()) return styleMap;

        String[] entries = style.split(";");
        for (String entry : entries) {
            String[] kv = entry.trim().split(":", 2);
            if (kv.length == 2) {
                styleMap.put(kv[0].trim(), kv[1].trim());
            }
        }
        return styleMap;
    }

    /**
     * Собирает Map стилей обратно в строку CSS-стиля.
     *
     * @param styleMap карта ключ-значение (например, fill → white)
     * @return строка стиля вида: "fill: white; background: black;"
     */
    public static String buildStyleString(Map<String, String> styleMap) {
        if (styleMap == null || styleMap.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : styleMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
        }
        return sb.toString().trim();
    }
}
