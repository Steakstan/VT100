package org.msv.vt100.ANSIISequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeftRightMarginModeHandler {
    private int leftMargin = 0;
    private int rightMargin = 79;

    private static final Logger logger = LoggerFactory.getLogger(LeftRightMarginModeHandler.class);

    private boolean isLeftRightMarginModeEnabled = false;

    /**
     * Включает режим левых и правых полей (DECVLRM).
     */
    public void enableLeftRightMarginMode() {
        isLeftRightMarginModeEnabled = true;
        logger.info("Режим левых и правых полей (DECVLRM) включен.");
    }

    /**
     * Отключает режим левых и правых полей (DECVLRM).
     */
    public void disableLeftRightMarginMode() {
        isLeftRightMarginModeEnabled = false;
        logger.info("Режим левых и правых полей (DECVLRM) отключен.");
    }

    /**
     * Проверяет, включен ли режим левых и правых полей.
     * @return true, если режим включен; иначе false.
     */
    public boolean isLeftRightMarginModeEnabled() {
        return isLeftRightMarginModeEnabled;
    }

    public void setLeftRightMargins(int left, int right) {
        this.leftMargin = left;
        this.rightMargin = right;
    }

    public int getLeftMargin() {
        return leftMargin;
    }

    public int getRightMargin() {
        return rightMargin;
    }
}