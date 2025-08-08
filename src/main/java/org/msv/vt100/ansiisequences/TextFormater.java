package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class TextFormater {

    // Logger instance for debugging
    private static final Logger logger = LoggerFactory.getLogger(TextFormater.class);
    private static final String REVERSE_SWAP_BG_FALLBACK = "black";

    // Enumeration for possible text formatting modes
    public enum TextFormatMode {
        NORMAL,
        UNDERLINE,
        REVERSE_VIDEO,
        BLINK,
        BOLD,
        CONCEAL
    }

    // Active formatting modes
    private final EnumSet<TextFormatMode> activeFormats;

    // Current canonical style string consumed by the renderer
    private String currentStyle;

    // Reference to LineAttributeHandler (to manage line-specific attributes)
    private final LineAttributeHandler lineAttributeHandler;

    // Current color state (canonical names used by the renderer)
    // Keep null to represent "default" (so that 39/49 can restore default)
    private String currentForeground; // e.g., "white"
    private String currentBackground; // e.g., "transparent"

    // Default colors (VT-style: typically foreground=white, background=transparent/black depending on theme)
    private static final String DEFAULT_FG = "white";
    private static final String DEFAULT_BG = "transparent";

    // Color maps for SGR 30–37 / 90–97 and 40–47 / 100–107
    private static final Map<Integer, String> FG_COLOR_MAP = new HashMap<>();
    private static final Map<Integer, String> BG_COLOR_MAP = new HashMap<>();

    static {
        // Normal intensity foreground (30–37)
        FG_COLOR_MAP.put(30, "black");
        FG_COLOR_MAP.put(31, "red");
        FG_COLOR_MAP.put(32, "green");
        FG_COLOR_MAP.put(33, "yellow");
        FG_COLOR_MAP.put(34, "blue");
        FG_COLOR_MAP.put(35, "magenta");
        FG_COLOR_MAP.put(36, "cyan");
        FG_COLOR_MAP.put(37, "white");

        // Bright foreground (90–97)
        FG_COLOR_MAP.put(90, "bright-black");
        FG_COLOR_MAP.put(91, "bright-red");
        FG_COLOR_MAP.put(92, "bright-green");
        FG_COLOR_MAP.put(93, "bright-yellow");
        FG_COLOR_MAP.put(94, "bright-blue");
        FG_COLOR_MAP.put(95, "bright-magenta");
        FG_COLOR_MAP.put(96, "bright-cyan");
        FG_COLOR_MAP.put(97, "bright-white");

        // Normal background (40–47)
        BG_COLOR_MAP.put(40, "black");
        BG_COLOR_MAP.put(41, "red");
        BG_COLOR_MAP.put(42, "green");
        BG_COLOR_MAP.put(43, "yellow");
        BG_COLOR_MAP.put(44, "blue");
        BG_COLOR_MAP.put(45, "magenta");
        BG_COLOR_MAP.put(46, "cyan");
        BG_COLOR_MAP.put(47, "white");

        // Bright background (100–107)
        BG_COLOR_MAP.put(100, "bright-black");
        BG_COLOR_MAP.put(101, "bright-red");
        BG_COLOR_MAP.put(102, "bright-green");
        BG_COLOR_MAP.put(103, "bright-yellow");
        BG_COLOR_MAP.put(104, "bright-blue");
        BG_COLOR_MAP.put(105, "bright-magenta");
        BG_COLOR_MAP.put(106, "bright-cyan");
        BG_COLOR_MAP.put(107, "bright-white");
    }

    public TextFormater(LineAttributeHandler lineAttributeHandler) {
        this.lineAttributeHandler = lineAttributeHandler;
        this.activeFormats = EnumSet.noneOf(TextFormatMode.class);
        // Initialize defaults
        resetAllAttributes();
    }

    /**
     * Processes the SGR (Select Graphic Rendition) sequence and updates active formatting modes.
     * This method expects the sequence starting after ESC, e.g. "[1;4m".
     */
    public void handleSgrSequence(String sequence) {
        // Remove '[' and trailing 'm'
        String params = sequence.replace("[", "").replace("m", "");
        String[] paramArray = params.split(";");

        if (paramArray.length == 0 || paramArray[0].isEmpty()) {
            // Empty parameter equals full reset
            resetAllAttributes();
            return;
        }

        for (String p : paramArray) {
            int code;
            try {
                code = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                logger.debug("Ignoring non-numeric SGR param: {}", p);
                continue;
            }

            // Apply parameters in the order received (VT semantics)
            if (code == 0) {
                resetAllAttributes();
                continue;
            }

            if (applyBasicAttribute(code)) continue;
            if (applyColor(code)) continue;

            // Not implemented attributes are ignored, but logged at debug to avoid noise
            logger.debug("Unknown/unsupported SGR code: {}", code);
        }

        updateCurrentStyle();
    }

    /**
     * Resets all text and line attributes (CSI 0 m).
     */
    public void resetAllAttributes() {
        activeFormats.clear();
        activeFormats.add(TextFormatMode.NORMAL);
        currentForeground = DEFAULT_FG;
        currentBackground = DEFAULT_BG;

        updateCurrentStyle();

        // Reset line attributes as well
        lineAttributeHandler.resetAllLineAttributes();
        logger.debug("All text and line attributes have been reset.");
    }

    // ----- SGR helpers -----

    /**
     * Applies non-color SGR attributes. Returns true if handled.
     */
    private boolean applyBasicAttribute(int code) {
        switch (code) {
            case 1: // bold
                enableBold();
                return true;
            case 2: // faint (map to not-bold if bold is not desired, here we keep bold unaffected)
                // Optional: implement faint as "font-weight: normal" unless bold set later
                // For now, we ignore 'faint' to avoid conflicting semantics; could add a FAINT state.
                return true;
            case 3: // italic (not supported by current renderer)
                // ignored
                return true;
            case 4: // underline
                enableUnderline();
                return true;
            case 5: // blink
                enableBlink();
                return true;
            case 7: // reverse video
                enableReverseVideo();
                return true;
            case 8: // conceal
                enableConceal();
                return true;
            case 21: // double underline -> treat as underline
                enableUnderline();
                return true;
            case 22: // not bold, not faint
                disableBold();
                return true;
            case 23: // not italic
                // ignored
                return true;
            case 24: // not underline
                disableUnderline();
                return true;
            case 25: // not blink
                disableBlink();
                return true;
            case 27: // not reverse
                disableReverseVideo();
                return true;
            case 28: // reveal (cancel conceal)
                disableConceal();
                return true;
            case 39: // default foreground
                currentForeground = DEFAULT_FG;
                return true;
            case 49: // default background
                currentBackground = DEFAULT_BG;
                return true;
            default:
                return false;
        }
    }

    /**
     * Applies color SGR attributes. Returns true if handled.
     * Supports 30–37/90–97 (foreground), 40–47/100–107 (background).
     * Note: 38/48 with extended color are not implemented here.
     */
    private boolean applyColor(int code) {
        if (FG_COLOR_MAP.containsKey(code)) {
            currentForeground = FG_COLOR_MAP.get(code);
            return true;
        }
        if (BG_COLOR_MAP.containsKey(code)) {
            currentBackground = BG_COLOR_MAP.get(code);
            return true;
        }
        return false;
    }

    // ----- Attribute toggles -----

    public void enableReverseVideo() {
        activeFormats.add(TextFormatMode.REVERSE_VIDEO);
        logger.debug("Reverse video enabled.");
    }

    public void disableReverseVideo() {
        activeFormats.remove(TextFormatMode.REVERSE_VIDEO);
        logger.debug("Reverse video disabled.");
    }

    public void enableUnderline() {
        activeFormats.add(TextFormatMode.UNDERLINE);
        logger.debug("Underline enabled.");
    }

    public void disableUnderline() {
        activeFormats.remove(TextFormatMode.UNDERLINE);
        logger.debug("Underline disabled.");
    }

    public void enableBlink() {
        activeFormats.add(TextFormatMode.BLINK);
        logger.debug("Blink enabled.");
    }

    public void disableBlink() {
        activeFormats.remove(TextFormatMode.BLINK);
        logger.debug("Blink disabled.");
    }

    public void enableBold() {
        activeFormats.add(TextFormatMode.BOLD);
        logger.debug("Bold enabled.");
    }

    public void disableBold() {
        activeFormats.remove(TextFormatMode.BOLD);
        logger.debug("Bold disabled.");
    }

    public void enableConceal() {
        activeFormats.add(TextFormatMode.CONCEAL);
        logger.debug("Conceal enabled.");
    }

    public void disableConceal() {
        activeFormats.remove(TextFormatMode.CONCEAL);
        logger.debug("Conceal disabled.");
    }

    /**
     * Rebuilds the canonical style string from state.
     * This style string is later interpreted by the renderer.
     */
    private void updateCurrentStyle() {
        // Resolve current colors (defaults applied)
        String effFg = currentForeground != null ? currentForeground : DEFAULT_FG;
        String effBg = currentBackground != null ? currentBackground : DEFAULT_BG;

        String fg = effFg;
        String bg = effBg;

        // Reverse-video means swapping colors. If background is transparent,
        // we fallback to a solid color (black) for the new foreground to keep text visible.
        if (activeFormats.contains(TextFormatMode.REVERSE_VIDEO)) {
            // Use a non-transparent surrogate for foreground after swap
            String swapSourceBg = "transparent".equalsIgnoreCase(effBg) ? REVERSE_SWAP_BG_FALLBACK : effBg;
            String newFg = swapSourceBg; // text color comes from (effective) background
            String newBg = effFg;        // background comes from previous foreground

            fg = newFg;
            bg = newBg;
        }

        // Conceal overrides foreground regardless of reverse-video
        if (activeFormats.contains(TextFormatMode.CONCEAL)) {
            fg = "transparent";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("fill: ").append(fg).append("; ");
        sb.append("background: ").append(bg).append("; ");
        sb.append("underline: ").append(activeFormats.contains(TextFormatMode.UNDERLINE) ? "true" : "false").append("; ");
        sb.append("font-weight: ").append(activeFormats.contains(TextFormatMode.BOLD) ? "bold" : "normal").append("; ");
        if (activeFormats.contains(TextFormatMode.BLINK)) {
            sb.append("blink: true; ");
        }

        currentStyle = sb.toString().trim();
    }

    /**
     * Returns the current canonical style string.
     */
    public String getCurrentStyle() {
        return currentStyle;
    }
}
