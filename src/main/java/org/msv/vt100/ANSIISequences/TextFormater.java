package org.msv.vt100.ANSIISequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class TextFormater {

    // Logger SLF4J
    private static final Logger logger = LoggerFactory.getLogger(TextFormater.class);

    // Enumeration for possible text formatting modes
    public enum TextFormatMode {
        NORMAL,
        UNDERLINE,
        REVERSE_VIDEO,
        BLINK,
        BOLD,
        CONCEAL // Добавили CONCEAL mode
    }

    // Set of active formatting modes
    private EnumSet<TextFormatMode> activeFormats;

    // Current style as CSS string
    private String currentStyle;

    // Reference to LineAttributeHandler
    private LineAttributeHandler lineAttributeHandler;

    public TextFormater(LineAttributeHandler lineAttributeHandler) {
        this.lineAttributeHandler = lineAttributeHandler;
        // Default to normal text
        this.activeFormats = EnumSet.of(TextFormatMode.NORMAL);
        updateCurrentStyle();
    }

    public void handleSgrSequence(String sequence) {
        // Remove '[' and 'm' from the sequence
        String params = sequence.replace("[", "").replace("m", "");
        String[] paramArray = params.split(";");

        if (paramArray.length == 0 || paramArray[0].isEmpty()) {
            // No parameters, reset all attributes
            resetAllAttributes();
            return;
        }

        for (String param : paramArray) {
            int code = Integer.parseInt(param);
            switch (code) {
                case 0:
                    resetAllAttributes();
                    break;
                case 1:
                    enableBold();
                    break;
                case 4:
                    enableUnderline();
                    break;
                case 5:
                    enableBlink();
                    break;
                case 7:
                    enableReverseVideo();
                    break;
                case 8:
                    enableConceal();
                    break;
                case 22:
                    disableBold();
                    break;
                case 24:
                    disableUnderline();
                    break;
                case 25:
                    disableBlink();
                    break;
                case 27:
                    disableReverseVideo();
                    break;
                case 28:
                    disableConceal();
                    break;
                // Add handling for other SGR codes as needed
                default:
                    logger.warn("Неизвестный код SGR: {}", code);
                    break;
            }
        }
    }

    // Method to reset all formatting (ESC [0m)
    public void resetAllAttributes() {
        activeFormats.clear();
        activeFormats.add(TextFormatMode.NORMAL);
        updateCurrentStyle();
        // Reset line attributes
        lineAttributeHandler.resetAllLineAttributes();
        logger.info("Все текстовые и строковые атрибуты сброшены.");
    }

    // Methods to enable/disable reverse video
    public void enableReverseVideo() {
        activeFormats.add(TextFormatMode.REVERSE_VIDEO);
        updateCurrentStyle();
        logger.info("Реверс видео включен.");
    }

    public void disableReverseVideo() {
        activeFormats.remove(TextFormatMode.REVERSE_VIDEO);
        updateCurrentStyle();
        logger.info("Реверс видео отключен.");
    }

    // Methods to enable/disable underline
    public void enableUnderline() {
        activeFormats.add(TextFormatMode.UNDERLINE);
        updateCurrentStyle();
        logger.info("Подчёркивание включено.");
    }

    public void disableUnderline() {
        activeFormats.remove(TextFormatMode.UNDERLINE);
        updateCurrentStyle();
        logger.info("Подчёркивание отключено.");
    }

    // Methods to enable/disable blink
    public void enableBlink() {
        activeFormats.add(TextFormatMode.BLINK);
        updateCurrentStyle();
        logger.info("Мигание включено.");
    }

    public void disableBlink() {
        activeFormats.remove(TextFormatMode.BLINK);
        updateCurrentStyle();
        logger.info("Мигание отключено.");
    }

    // Methods to enable/disable bold
    public void enableBold() {
        activeFormats.add(TextFormatMode.BOLD);
        updateCurrentStyle();
        logger.info("Полужирный шрифт включен.");
    }

    public void disableBold() {
        activeFormats.remove(TextFormatMode.BOLD);
        updateCurrentStyle();
        logger.info("Полужирный шрифт отключен.");
    }

    // Methods to enable/disable conceal
    public void enableConceal() {
        activeFormats.add(TextFormatMode.CONCEAL);
        updateCurrentStyle();
        logger.info("Режим скрытого текста включен.");
    }

    public void disableConceal() {
        activeFormats.remove(TextFormatMode.CONCEAL);
        updateCurrentStyle();
        logger.info("Режим скрытого текста отключен.");
    }

    // Method to update current style based on active formats
    private void updateCurrentStyle() {
        String textColor = "white";
        String backgroundColor = "rgba(0, 43, 54, 0.0)";

        if (activeFormats.contains(TextFormatMode.REVERSE_VIDEO)) {
            // Invert colors
            String temp = textColor;
            textColor = "BLUE";
            backgroundColor = temp;
        }

        if (activeFormats.contains(TextFormatMode.CONCEAL)) {
            // Conceal text by setting text color to transparent
            textColor = "transparent";
        }

        StringBuilder styleBuilder = new StringBuilder();
        styleBuilder.append("-fx-fill: ").append(textColor).append("; ");
        styleBuilder.append("-rtfx-background-color: ").append(backgroundColor).append("; ");

        if (activeFormats.contains(TextFormatMode.UNDERLINE)) {
            styleBuilder.append("-fx-underline: true; ");
        } else {
            styleBuilder.append("-fx-underline: false; ");
        }

        if (activeFormats.contains(TextFormatMode.BOLD)) {
            styleBuilder.append("-fx-font-weight: bold; ");
        } else {
            styleBuilder.append("-fx-font-weight: normal; ");
        }

        // Если нужно добавить мигание, потребуется дополнительная логика

        currentStyle = styleBuilder.toString().trim();
    }

    // Method to get current style
    public String getCurrentStyle() {
        return currentStyle;
    }
}
