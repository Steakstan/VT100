package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles DEC Origin Mode (DECOM).
 * Semantics:
 * - When DECOM is ON (CSI ?6h), the origin for CUP (cursor position) is the current scrolling region's
 *   top-left corner (and, if DECVLRM is active, the left margin).
 * - When DECOM is OFF (CSI ?6l), CUP is absolute relative to the full screen (top-left at 1,1).
 * This class stores the current mode and notifies listeners on changes. It does not apply cursor math itself;
 * consumers (e.g., CursorController) should query {@link #isRelativeCursorMode()} and adapt positioning logic.
 */
public class DECOMHandler {

    private static final Logger logger = LoggerFactory.getLogger(DECOMHandler.class);

    private volatile boolean relativeCursorMode = false;  // current DECOM state

    // Optional listeners to react on mode changes (e.g., to repaint or normalize cursor)
    private final List<Runnable> changeListeners = new ArrayList<>();

    /** Enables relative cursor origin (DECOM ON). Idempotent. */
    public void enableRelativeCursorMode() {
        setRelativeCursorMode(true);
    }

    /** Disables relative cursor origin (DECOM OFF). Idempotent. */
    public void disableRelativeCursorMode() {
        setRelativeCursorMode(false);
    }

    /** Returns current DECOM state. */
    public boolean isRelativeCursorMode() {
        return relativeCursorMode;
    }

    // ---- internals ----

    private void setRelativeCursorMode(boolean on) {
        if (this.relativeCursorMode == on) {
            return; // no-op if unchanged
        }
        this.relativeCursorMode = on;
        logger.debug("DECOM {}.", on ? "aktiviert" : "deaktiviert");
        notifyListeners();
    }

    private void notifyListeners() {
        for (Runnable l : changeListeners) {
            try {
                l.run();
            } catch (Exception e) {
                logger.debug("DECOM-Listener warf: {}", e.toString());
            }
        }
    }
}
