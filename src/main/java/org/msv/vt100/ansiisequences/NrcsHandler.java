package org.msv.vt100.ansiisequences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Objects;

public class NrcsHandler {

    private static final Logger logger = LoggerFactory.getLogger(NrcsHandler.class);

    // Перечисление доступных национальных наборов символов
    public enum NrcsMode {
        US,      // Американский (ASCII)
        UK,      // Британский
        GERMAN,  // Немецкий
        // Добавьте другие наборы символов по необходимости
    }

    // Текущий режим NRCS
    private NrcsMode currentNrcsMode;

    // Флаг, указывающий, включен ли режим NRCS
    private boolean isNrcsEnabled;

    // Маппинг символов для текущего набора NRCS
    private final HashMap<Character, Character> nrcsMapping;

    public NrcsHandler() {
        // По умолчанию используем американский ASCII набор символов
        this.currentNrcsMode = NrcsMode.US;
        this.isNrcsEnabled = false;
        this.nrcsMapping = new HashMap<>();
        initializeMappings();
    }

    // Метод для включения NRCS режима
    public void enableNrcsMode(NrcsMode nrcsMode) {
        this.currentNrcsMode = nrcsMode;
        this.isNrcsEnabled = true;
        initializeMappings();
        logger.info("NRCS режим включен. Текущий набор символов: {}", nrcsMode);
    }

    // Метод для отключения NRCS режима
    public void disableNrcsMode() {
        this.isNrcsEnabled = false;
        this.currentNrcsMode = NrcsMode.US;
        this.nrcsMapping.clear();
        logger.info("NRCS режим отключен.");
    }

    // Метод для обработки текста с учетом NRCS
    public String processText(String text) {
        if (!isNrcsEnabled) {
            return text;
        }

        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (nrcsMapping.containsKey(c)) {
                result.append(nrcsMapping.get(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    // Инициализация маппинга символов для выбранного NRCS
    private void initializeMappings() {
        nrcsMapping.clear();

        if (Objects.requireNonNull(currentNrcsMode) == NrcsMode.GERMAN) {// Пример маппинга для немецкого набора символов
            nrcsMapping.put('[', 'Ä');
            nrcsMapping.put('\\', 'Ö');
            nrcsMapping.put(']', 'Ü');
            nrcsMapping.put('{', 'ä');
            nrcsMapping.put('|', 'ö');
            nrcsMapping.put('}', 'ü');
            nrcsMapping.put('~', 'ß');
            nrcsMapping.put((char) 0xB4, '´');
            nrcsMapping.put('®', '®');
        }
    }
}