package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class TextFormater {

    // Logger instance for debugging
    private static final Logger logger = LoggerFactory.getLogger(TextFormater.class);

    // Enumeration for possible text formatting modes
    public enum TextFormatMode {
        NORMAL,
        UNDERLINE,
        REVERSE_VIDEO,
        BLINK,
        BOLD,
        CONCEAL // Added CONCEAL mode
    }

    // Set of active formatting modes
    private final EnumSet<TextFormatMode> activeFormats;

    // Current style represented as a style string (to be interpreted by the TerminalCanvas renderer)
    private String currentStyle;

    // Reference to LineAttributeHandler (to manage line-specific attributes)
    private final LineAttributeHandler lineAttributeHandler;

    public TextFormater(LineAttributeHandler lineAttributeHandler) {
        this.lineAttributeHandler = lineAttributeHandler;
        // Set default mode to NORMAL
        this.activeFormats = EnumSet.of(TextFormatMode.NORMAL);
        updateCurrentStyle();
    }

    /**
     * Processes the SGR (Select Graphic Rendition) sequence and updates active formatting modes.
     * This method strips the '[' and 'm' characters and then parses the parameters.
     *
     * @param sequence the SGR escape sequence (e.g., "[1;4m")
     */
    public void handleSgrSequence(String sequence) {
        // Remove '[' and 'm' from the sequence
        String params = sequence.replace("[", "").replace("m", "");
        String[] paramArray = params.split(";");

        if (paramArray.length == 0 || paramArray[0].isEmpty()) {
            // No parameters provided; reset all attributes
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
                // Additional SGR codes can be handled as needed
                default:
                    logger.warn("Unknown SGR code: {}", code);
                    break;
            }
        }
    }

    /**
     * Resets all text and line attributes (corresponds to ESC [0m).
     */
    public void resetAllAttributes() {
        activeFormats.clear();
        activeFormats.add(TextFormatMode.NORMAL);
        updateCurrentStyle();
        // Reset line attributes as well
        lineAttributeHandler.resetAllLineAttributes();
        logger.info("All text and line attributes have been reset.");
    }

    /**
     * Enables reverse video (swaps text and background colors).
     */
    public void enableReverseVideo() {
        activeFormats.add(TextFormatMode.REVERSE_VIDEO);
        updateCurrentStyle();
        logger.info("Reverse video enabled.");
    }

    /**
     * Disables reverse video.
     */
    public void disableReverseVideo() {
        activeFormats.remove(TextFormatMode.REVERSE_VIDEO);
        updateCurrentStyle();
        logger.info("Reverse video disabled.");
    }

    /**
     * Enables underline formatting.
     */
    public void enableUnderline() {
        activeFormats.add(TextFormatMode.UNDERLINE);
        updateCurrentStyle();
        logger.info("Underline enabled.");
    }

    /**
     * Disables underline formatting.
     */
    public void disableUnderline() {
        activeFormats.remove(TextFormatMode.UNDERLINE);
        updateCurrentStyle();
        logger.info("Underline disabled.");
    }

    /**
     * Enables blinking text.
     */
    public void enableBlink() {
        activeFormats.add(TextFormatMode.BLINK);
        updateCurrentStyle();
        logger.info("Blink enabled.");
    }

    /**
     * Disables blinking text.
     */
    public void disableBlink() {
        activeFormats.remove(TextFormatMode.BLINK);
        updateCurrentStyle();
        logger.info("Blink disabled.");
    }

    /**
     * Enables bold text.
     */
    public void enableBold() {
        activeFormats.add(TextFormatMode.BOLD);
        updateCurrentStyle();
        logger.info("Bold text enabled.");
    }

    /**
     * Disables bold text.
     */
    public void disableBold() {
        activeFormats.remove(TextFormatMode.BOLD);
        updateCurrentStyle();
        logger.info("Bold text disabled.");
    }

    /**
     * Enables conceal mode (hides text by making it transparent).
     */
    public void enableConceal() {
        activeFormats.add(TextFormatMode.CONCEAL);
        updateCurrentStyle();
        logger.info("Conceal mode enabled.");
    }

    /**
     * Disables conceal mode.
     */
    public void disableConceal() {
        activeFormats.remove(TextFormatMode.CONCEAL);
        updateCurrentStyle();
        logger.info("Conceal mode disabled.");
    }

    /**
     * Updates the current style string based on active formatting modes.
     * This style string is later interpreted by the TerminalCanvas to set drawing parameters.
     */
    private void updateCurrentStyle() {
        String textColor = "white";
        String backgroundColor = "transparent";

        if (activeFormats.contains(TextFormatMode.REVERSE_VIDEO)) {
            // For reverse video, swap text and background colors
            String temp = textColor;
            textColor = "blue"; // You may adjust this to a preferred color
            backgroundColor = temp;
        }

        if (activeFormats.contains(TextFormatMode.CONCEAL)) {
            // Conceal text by setting text color to transparent
            textColor = "transparent";
        }

        StringBuilder styleBuilder = new StringBuilder();
        // The style string format is a simple key-value pair sequence that can be parsed by the canvas renderer.
        styleBuilder.append("fill: ").append(textColor).append("; ");
        styleBuilder.append("background: ").append(backgroundColor).append("; ");

        if (activeFormats.contains(TextFormatMode.UNDERLINE)) {
            styleBuilder.append("underline: true; ");
        } else {
            styleBuilder.append("underline: false; ");
        }

        if (activeFormats.contains(TextFormatMode.BOLD)) {
            styleBuilder.append("font-weight: bold; ");
        } else {
            styleBuilder.append("font-weight: normal; ");
        }

        // Additional formatting options (e.g., blink) can be added here

        currentStyle = styleBuilder.toString().trim();
    }

    /**
     * Returns the current style string.
     *
     * @return the current style string
     */
    public String getCurrentStyle() {
        return currentStyle;
    }
}













