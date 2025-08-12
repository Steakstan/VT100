package org.msv.vt100.ansiisequences;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages cursor visibility and blinking.
 * Model:
 * - "Enabled" means the cursor is logically shown (ESC [?25h]) or hidden (ESC [?25l]).
 * - "Blinking" controls whether the visible state toggles over time.
 * - If enabled && !blinking => cursor is continuously visible.
 * - If enabled &&  blinking  => cursor visibility toggles with the configured period.
 * - If !enabled => cursor is invisible regardless of blinking.
 * Listeners are notified whenever the effective visibility may have changed.
 * This class does not render anything; consumers should call {@link #isCursorVisible()}.
 */
public class CursorVisibilityManager {

    /** Logical show/hide state controlled by ESC [?25h / ?25l]. */
    private boolean enabled = false;

    /** Whether blinking behavior is active. */
    private boolean blinking = true;

    /** Current on/off phase when blinking is enabled. */
    private boolean blinkPhaseOn = true;

    /** Blink timer (JavaFX Timeline). */
    private Timeline blinkTimeline;

    /** Blink period (defaults to 500ms). */
    private final Duration blinkPeriod = Duration.millis(500);

    /** Subscribers notified on any visibility-affecting change. */
    private final List<Runnable> visibilityChangeListeners = new ArrayList<>();

    // ---- Public API ----

    /** Enables the cursor (ESC [?25h]) and makes it immediately visible. */
    public void showCursor() {
        if (!enabled) {
            enabled = true;
            // Make the cursor visible immediately (no initial half-second delay).
            blinkPhaseOn = true;
            notifyVisibilityChange();
        }
    }

    /** Disables the cursor (ESC [?25l]) and hides it. */
    public void hideCursor() {
        if (enabled) {
            enabled = false;
            notifyVisibilityChange();
        }
    }

    /** Returns true if the cursor should be painted as visible right now. */
    public boolean isCursorVisible() {
        if (!enabled) return false;
        if (!blinking) return true;
        return blinkPhaseOn;
    }



    /** Initializes the internal timer; safe to call repeatedly. */
    public void initializeCursorBlinking() {
        updateBlinkTimer();
    }

    // ---- Internals ----

    private void updateBlinkTimer() {
        // Rebuild the timeline to apply the current period and state.
        if (blinkTimeline != null) {
            blinkTimeline.stop();
            blinkTimeline = null;
        }

        if (!blinking) {
            // No timer needed; phase forced to ON when blinking is disabled.
            return;
        }

        blinkTimeline = new Timeline(
                new KeyFrame(blinkPeriod, event -> toggleBlinkPhase())
        );
        blinkTimeline.setCycleCount(Timeline.INDEFINITE);
        blinkTimeline.play();
    }

    private void toggleBlinkPhase() {
        // Toggle phase and notify; visibility depends on enabled && phase
        blinkPhaseOn = !blinkPhaseOn;
        notifyVisibilityChange();
    }

    private void notifyVisibilityChange() {
        for (Runnable listener : visibilityChangeListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // Listener errors must not break the visibility manager
            }
        }
    }

    // CursorVisibilityManager.java
    public void shutdown() {
        try {
            if (blinkTimeline != null) {
                blinkTimeline.stop();
                blinkTimeline = null;
            }
        } catch (Throwable ignore) {
        }
    }


}
