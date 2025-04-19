package org.msv.vt100.OrderAutomation;

import org.msv.vt100.core.ScreenBuffer;

public class ScreenTextDetector {

    private final ScreenBuffer screenBuffer;

    public ScreenTextDetector(ScreenBuffer screenBuffer) {
        this.screenBuffer = screenBuffer;
    }

    public boolean isWareneingangDisplayed(){
        String screenText = screenBuffer.toString();
        return screenText.contains("Wareneingang");
    }
    public boolean isAchtungDisplayed(){
        String screenText = screenBuffer.toString();
        return screenText.contains("Aenderungsauftrag");
    }

    public boolean isPosNrDisplayed() {
        // Поиск фразы "LB-Nr.:" в буфере экрана
        String screenText = screenBuffer.toString();
        return screenText.contains("Pos-Nr.:");
    }
    public String getScreenText() {
        return screenBuffer.toString();
    }
}