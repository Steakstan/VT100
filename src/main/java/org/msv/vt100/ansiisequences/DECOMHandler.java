package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DECOMHandler отвечает за управление DEC Origin Mode (DECOM),
 * который включает и отключает режим относительного перемещения курсора.
 * В этом режиме координаты курсора интерпретируются относительно текущего окна.
 */
public class DECOMHandler {

    private static final Logger logger = LoggerFactory.getLogger(DECOMHandler.class);

    private boolean relativeCursorMode = false;  // Хранение текущего состояния DECOM режима

    /**
     * Включает режим относительного перемещения курсора (DECOM).
     */
    public void enableRelativeCursorMode() {
        this.relativeCursorMode = true;
        logger.info("Включен режим относительного перемещения курсора (DECOM)");
    }

    /**
     * Отключает режим относительного перемещения курсора (DECOM).
     */
    public void disableRelativeCursorMode() {
        this.relativeCursorMode = false;
        logger.info("Отключен режим относительного перемещения курсора (DECOM)");
    }

    /**
     * Возвращает текущее состояние DECOM режима.
     * @return true, если включен режим относительного перемещения курсора
     */
    public boolean isRelativeCursorMode() {
        return relativeCursorMode;
    }
}