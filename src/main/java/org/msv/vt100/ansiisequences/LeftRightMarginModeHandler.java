package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks DECVLRM (left/right margin mode) and current horizontal margins.
 *
 * Notes:
 * - Margins are stored as 0-based inclusive indices.
 * - When DECVLRM is disabled, margins are ignored by consumers, but we still retain
 *   the last set values in case the mode is re-enabled later.
 * - Callers should clamp margins against current screen width; this class provides
 *   helpers to normalize after a resize.
 */
public class LeftRightMarginModeHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeftRightMarginModeHandler.class);

    // 0-based inclusive margins
    private int leftMargin = 0;
    private int rightMargin = 79; // default to 80 cols until normalized by caller

    private boolean leftRightMarginModeEnabled = false;

    /**
     * Enables DECVLRM (left/right margin mode).
     */
    public void enableLeftRightMarginMode() {
        leftRightMarginModeEnabled = true;
        logger.debug("DECVLRM enabled.");
    }

    /**
     * Disables DECVLRM (left/right margin mode).
     * Margins are retained but ignored while the mode is off.
     */
    public void disableLeftRightMarginMode() {
        leftRightMarginModeEnabled = false;
        logger.debug("DECVLRM disabled.");
    }

    /**
     * Returns whether DECVLRM is enabled.
     */
    public boolean isLeftRightMarginModeEnabled() {
        return leftRightMarginModeEnabled;
    }

    /**
     * Sets left/right margins (0-based inclusive). Invalid input is clamped and corrected.
     * If left >= right after clamping, the call is ignored and margins remain unchanged.
     */
    public void setLeftRightMargins(int left, int right) {
        int newLeft = Math.max(0, left);
        int newRight = Math.max(newLeft, right); // ensure non-decreasing before validation

        if (newLeft >= newRight) {
            logger.debug("Ignored margins because left >= right (left={}, right={}).", left, right);
            return;
        }

        this.leftMargin = newLeft;
        this.rightMargin = newRight;
        logger.debug("Margins set to left={}, right={} (0-based, inclusive).", leftMargin, rightMargin);
    }

    /**
     * Normalizes margins to fit within [0..columns-1] after a screen resize.
     * If the normalized margins collapse (left >= right), they are reset to full width.
     */
    public void normalizeAfterResize(int columns) {
        if (columns <= 0) {
            // Nothing sensible to do; keep current values.
            logger.debug("normalizeAfterResize skipped due to non-positive column count: {}", columns);
            return;
        }

        int max = columns - 1;
        int newLeft = Math.max(0, Math.min(leftMargin, max));
        int newRight = Math.max(0, Math.min(rightMargin, max));

        if (newLeft >= newRight) {
            // Reset to full width
            leftMargin = 0;
            rightMargin = max;
            logger.debug("Margins collapsed after resize; reset to full width 0..{}.", max);
        } else {
            leftMargin = newLeft;
            rightMargin = newRight;
            logger.debug("Margins normalized after resize to {}..{} within 0..{}.", leftMargin, rightMargin, max);
        }
    }

    /**
     * Resets margins to full width given current column count.
     * Caller must pass the actual screen width.
     */
    public void resetToFullWidth(int columns) {
        if (columns <= 0) return;
        leftMargin = 0;
        rightMargin = columns - 1;
        logger.debug("Margins reset to full width 0..{}.", rightMargin);
    }

    public int getLeftMargin() {
        return leftMargin;
    }

    public int getRightMargin() {
        return rightMargin;
    }
}
