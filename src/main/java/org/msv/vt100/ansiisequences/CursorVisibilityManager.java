package org.msv.vt100.ansiisequences;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class CursorVisibilityManager {

    private boolean cursorEnabled; // Управляется ESC[?25h и ESC[?25l]
    private boolean blinkState;    // Используется для мигания курсора

    private final List<Runnable> visibilityChangeListeners = new ArrayList<>();

    public CursorVisibilityManager() {
        // Изначально курсор отключен (невидим)
        this.cursorEnabled = false;
        this.blinkState = false;
    }

    public void toggleBlinkState() {
        this.blinkState = !this.blinkState;
        notifyVisibilityChange();
    }

    public void showCursor() {
        if (!cursorEnabled) {
            this.cursorEnabled = true;
            notifyVisibilityChange();
        }
    }

    public void hideCursor() {
        if (cursorEnabled) {
            this.cursorEnabled = false;
            notifyVisibilityChange();
        }
    }

    public boolean isCursorVisible() {
        // Курсор видим только если он включен и в состоянии "видим"
        return cursorEnabled && blinkState;
    }


    private void notifyVisibilityChange() {
        for (Runnable listener : visibilityChangeListeners) {
            listener.run();
        }
    }

    public void initializeCursorBlinking() {
        Timeline cursorBlinkTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0.5), event -> toggleBlinkState())
        );
        cursorBlinkTimeline.setCycleCount(Timeline.INDEFINITE);
        cursorBlinkTimeline.play();
    }
}