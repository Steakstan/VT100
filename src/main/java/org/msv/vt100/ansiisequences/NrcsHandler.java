package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles National Replacement Character Sets (NRCS).
 * VT terminals can map a small ASCII subset to national variants when NRCS is enabled.
 * This class performs a simple character-for-character replacement when enabled.
 *
 * Notes:
 * - NRCS is independent from DEC Special Graphics (handled elsewhere).
 * - If NRCS is disabled, input text is passed through unchanged.
 */
public class NrcsHandler {

    private static final Logger logger = LoggerFactory.getLogger(NrcsHandler.class);

    /** Supported NRCS modes (extend as needed). */
    public enum NrcsMode {
        US,      // US-ASCII (identity mapping)
        UK,      // British NRCS (e.g., '#' -> '£')
        GERMAN   // German NRCS (brackets -> umlauts, tilde -> ß, etc.)
    }

    /** Current NRCS mode. */
    private NrcsMode currentNrcsMode = NrcsMode.US;

    /** Whether NRCS translation is active. */
    private boolean nrcsEnabled = false;

    /** Current mapping table for the active NRCS. */
    private Map<Character, Character> nrcsMapping = Collections.emptyMap();

    public NrcsHandler() {
        // Default: US (identity) and disabled.
        loadMappingFor(NrcsMode.US);
    }

    /**
     * Enables NRCS with the given mode.
     */
    public void enableNrcsMode(NrcsMode mode) {
        if (mode == null) mode = NrcsMode.US;
        this.currentNrcsMode = mode;
        this.nrcsEnabled = true;
        loadMappingFor(mode);
        logger.debug("NRCS enabled. Mode={}", mode);
    }

    /**
     * Disables NRCS (reverts to US mapping but pass-through remains in effect).
     */
    public void disableNrcsMode() {
        this.nrcsEnabled = false;
        this.currentNrcsMode = NrcsMode.US;
        loadMappingFor(NrcsMode.US);
        logger.debug("NRCS disabled.");
    }

    /**
     * Processes a whole string through the current NRCS mapping.
     * If NRCS is disabled or text is null/empty, returns the input unchanged.
     */
    public String processText(String text) {
        if (!nrcsEnabled || text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append(mapChar(text.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * Processes a single character through NRCS mapping.
     * Useful for hot paths where characters are emitted one-by-one.
     */
    public char processChar(char c) {
        if (!nrcsEnabled) return c;
        return mapChar(c);
    }

    /**
     * Returns the current NRCS mode.
     */
    public NrcsMode getCurrentNrcsMode() {
        return currentNrcsMode;
    }

    /**
     * Returns whether NRCS translation is currently enabled.
     */
    public boolean isNrcsEnabled() {
        return nrcsEnabled;
    }

    // ---- Internals ----

    private char mapChar(char c) {
        Character mapped = nrcsMapping.get(c);
        return (mapped != null) ? mapped : c;
    }

    /**
     * Loads the mapping for a given NRCS mode.
     * The mapping is intentionally minimal and targets common historical sets.
     * Extend carefully if your sources require more substitutions.
     */
    private void loadMappingFor(NrcsMode mode) {
        Map<Character, Character> map = new HashMap<>();

        switch (mode) {
            case US:
                // Identity — keep map empty for fast path
                break;

            case UK:
                // Classic UK NRCS: '#' (0x23) becomes '£'
                map.put('#', '£');
                break;

            case GERMAN:
                // Common German NRCS substitutions (ISO 646-DE style variants used by VT-class terminals)
                // Brackets and some punctuation replaced by umlauts/ß.
                map.put('[', 'Ä');
                map.put('\\', 'Ö');
                map.put(']', 'Ü');
                map.put('{', 'ä');
                map.put('|', 'ö');
                map.put('}', 'ü');
                map.put('~', 'ß');
                // Optional acute accent on many legacy sets was a dead key; we skip combining behavior here.
                // Add only safe, non-combining characters to avoid breaking column math.
                break;

            default:
                // Fallback to identity
                logger.debug("Unknown NRCS mode {}; using US identity mapping.", mode);
        }

        // Freeze the map to avoid accidental mutation
        this.nrcsMapping = map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }
}
