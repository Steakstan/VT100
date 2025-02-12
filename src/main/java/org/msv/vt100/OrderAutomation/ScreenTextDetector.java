package org.msv.vt100.OrderAutomation;

import org.msv.vt100.core.ScreenBuffer;

public class ScreenTextDetector {

    private final ScreenBuffer screenBuffer;

    public ScreenTextDetector(ScreenBuffer screenBuffer) {
        this.screenBuffer = screenBuffer;
    }

    public boolean isAufNrDisplayed() {
        // Поиск фразы "Auf-Nr.:" в буфере экрана
        String screenText = screenBuffer.toString();
        return screenText.contains("Auf-Nr.:");
    }

    public boolean isLbNrDisplayed() {
        // Поиск фразы "LB-Nr.:" в буфере экрана
        String screenText = screenBuffer.toString();
        return screenText.contains("LB-Nr.:");
    }

    public boolean isWareneingangDisplayed(){
        String screenText = screenBuffer.toString();
        return screenText.contains("Wareneingang");
    }

    public boolean isPosNrDisplayed() {
        // Поиск фразы "LB-Nr.:" в буфере экрана
        String screenText = screenBuffer.toString();
        return screenText.contains("Pos-Nr.:");
    }
}