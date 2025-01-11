package org.msv.vt100;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Класс SSHReader отвечает за чтение данных из SSH-сессии и передачу их в TerminalApp.
 * Также записывает полученные данные в файл для последующего анализа.
 */
public class SSHReader implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SSHReader.class);

    private final InputStream inputStream;
    private final TerminalApp terminalApp;
    private static final String FILE_PATH = "C:\\Users\\andru\\Documents\\XXXLUTZ\\ssh_raw_output.txt";

    /**
     * Конструктор SSHReader.
     *
     * @param inputStream  Входящий поток данных из SSH-сессии.
     * @param terminalApp  Экземпляр TerminalApp для передачи полученных данных.
     */
    public SSHReader(InputStream inputStream, TerminalApp terminalApp) {
        logger.debug("Создание SSHReader с inputStream={} и terminalApp={}", inputStream, terminalApp);
        this.inputStream = inputStream;
        this.terminalApp = terminalApp;

        // Создаём директорию, если она не существует
        File file = new File(FILE_PATH);
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            logger.debug("Директория {} не существует. Попытка создания...", parentDir.getAbsolutePath());
            if (parentDir.mkdirs()) {
                logger.info("Директория для сохранения файла успешно создана: {}", parentDir.getAbsolutePath());
            } else {
                logger.error("Ошибка при создании директории для файла: {}", parentDir.getAbsolutePath());
            }
        } else {
            logger.debug("Директория для сохранения файла уже существует: {}", parentDir.getAbsolutePath());
        }
    }

    /**
     * Метод run выполняется в отдельном потоке и отвечает за чтение данных из SSH-сессии.
     */
    // Внутри класса SSHReader

    @Override
    public void run() {
        logger.debug("Запуск метода run() SSHReader.");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, true), 32768)) {
            logger.debug("BufferedWriter инициализирован для файла: {}", FILE_PATH);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                logger.trace("Прочитано {} байт из inputStream.", bytesRead);
                if (bytesRead > 0) {
                    String response = new String(buffer, 0, bytesRead, StandardCharsets.ISO_8859_1);
                    logger.debug("Полученные данные: {}", sanitizeLogMessage(response));

                    // Обрабатываем данные в терминале
                    Platform.runLater(() -> {
                        try {
                            terminalApp.processInput(response.toCharArray());
                            logger.trace("Данные успешно переданы в TerminalApp.processInput.");
                        } catch (Exception e) {
                            logger.error("Ошибка при передаче данных в TerminalApp.processInput: ", e);
                        }
                    });

                    // Записываем данные в файл только если логирование включено
                    if (terminalApp.isLoggingEnabled()) {
                        writer.write(response);
                        logger.trace("Данные записаны в файл: {}", FILE_PATH);
                    }

                } else {
                    logger.warn("Прочитано 0 байт из inputStream.");
                }
            }

            logger.info("Поток чтения SSH завершён нормально.");
        } catch (IOException e) {
            logger.error("Ошибка при чтении данных SSH: ", e);
        } catch (Exception e) {
            logger.error("Неизвестная ошибка в SSHReader.run(): ", e);
        } finally {
            logger.debug("Метод run() SSHReader завершён.");
        }
    }


    /**
     * Метод для очистки и безопасного отображения сообщений в логах.
     * Удаляет или заменяет символы, которые могут нарушить форматирование логов.
     *
     * @param message Исходное сообщение.
     * @return Очищенное сообщение.
     */
    private String sanitizeLogMessage(String message) {
        if (message == null) {
            return "null";
        }
        // Заменяем символы переноса строки и табуляции для корректного отображения в логах
        return message.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}