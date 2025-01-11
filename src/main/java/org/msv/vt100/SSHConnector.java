package org.msv.vt100;

import com.jcraft.jsch.*;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.*;

public class SSHConnector {

    private static final Logger logger = LoggerFactory.getLogger(SSHConnector.class);

    private final String user;
    private final String host;
    private final int port;
    private final String privateKey;
    private final TerminalApp terminalApp;

    private Session session;
    private ChannelShell channel;
    private OutputStream outputStream;
    private InputStream inputStream;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private int retryAttempts = 0;
    private final int maxRetryAttempts = 5;

    public SSHConnector(String user, String host, int port, String privateKey, TerminalApp terminalApp) {
        this.user = user;
        this.host = host;
        this.port = port;
        this.privateKey = privateKey;
        this.terminalApp = terminalApp;
        logger.debug("Создание SSHConnector с user={}, host={}, port={}, privateKey={}", user, host, port, privateKey);
    }

    /**
     * Устанавливает соединение и запускает чтение данных.
     *
     * @param terminalApp Экземпляр TerminalApp для передачи данных.
     */
    public void connectAndStart(TerminalApp terminalApp) {
        logger.debug("Вызов метода connectAndStart с TerminalApp={}", terminalApp);
        try {
            connect();
            logger.info("Подключение установлено, запускаем SSHReader.");
            executorService.submit(new SSHReader(inputStream, terminalApp));
        } catch (Exception e) {
            logger.error("Ошибка при подключении SSH: ", e);
            retryConnection(terminalApp);
        }
    }

    /**
     * Устанавливает SSH-соединение и открывает shell-канал.
     *
     * @throws JSchException Если возникла ошибка при установке соединения.
     * @throws IOException   Если возникла ошибка при открытии канала.
     */
    private void connect() throws JSchException, IOException {
        logger.debug("Начало подключения SSH. user={}, host={}, port={}, privateKey={}", user, host, port, privateKey);
        JSch jsch = new JSch();
        jsch.addIdentity(privateKey);
        logger.info("Добавлен ключ для SSH аутентификации: {}", privateKey);

        session = jsch.getSession(user, host, port);
        logger.debug("Создана SSH-сессия: user={}, host={}, port={}", user, host, port);

        // Отключение строгой проверки ключа хоста
        session.setConfig("StrictHostKeyChecking", "no");
        logger.debug("Настроен SSH конфиг: StrictHostKeyChecking=no");

        logger.info("Подключение к {}:{} с пользователем {}", host, port, user);
        int connectionTimeout = 30000; // 30 секунд
        session.connect(connectionTimeout);
        logger.info("SSH-сессия успешно подключена.");

        channel = (ChannelShell) session.openChannel("shell");
        inputStream = channel.getInputStream();
        outputStream = channel.getOutputStream();
        logger.debug("Открыт shell-канал. inputStream={}, outputStream={}", inputStream, outputStream);

        channel.connect(connectionTimeout);
        logger.info("Shell-канал подключен и готов к использованию.");
    }

    /**
     * Отправляет данные на SSH-сервер без каких-либо изменений.
     *
     * @param data Данные для отправки.
     * @throws IOException Если возникла ошибка при отправке данных.
     */
    public void sendData(String data) throws IOException, InterruptedException {
        logger.debug("Вызов метода sendData с данными: {}", data);
        if (isConnected()) {
            try {
                logger.info("Отправка данных: {}", data.trim());
                outputStream.write(data.getBytes());
                outputStream.flush();
                logger.debug("Данные отправлены и сброшены.");
            } catch (IOException e) {
                logger.error("Ошибка при отправке данных: {}", data, e);
                throw e;
            }
        } else {
            String errorMsg = "SSH соединение не установлено. Данные не могут быть отправлены.";
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    /**
     * Пытается повторно подключиться к SSH-серверу с экспоненциальной задержкой.
     *
     * @param terminalApp Экземпляр TerminalApp для передачи данных.
     */
    private void retryConnection(TerminalApp terminalApp) {
        if (retryAttempts < maxRetryAttempts) {
            long delay = (long) Math.pow(2, retryAttempts) * 1000; // Экспоненциальная задержка
            logger.warn("Попытка повторного подключения через {} миллисекунд. Попытка {}/{}", delay, retryAttempts + 1, maxRetryAttempts);
            scheduler.schedule(() -> {
                retryAttempts++;
                logger.info("Повторное подключение. Попытка {}/{}", retryAttempts, maxRetryAttempts);
                connectAndStart(terminalApp);
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            logger.error("Достигнуто максимальное количество попыток подключения ({})", maxRetryAttempts);
        }
    }

    /**
     * Отключает SSH-соединение и закрывает каналы.
     */
    public void disconnect() {
        logger.debug("Вызов метода disconnect.");
        try {
            if (channel != null) {
                if (channel.isConnected()) {
                    channel.disconnect();
                    logger.info("Shell-канал отключен.");
                } else {
                    logger.warn("Shell-канал уже отключен.");
                }
            }
            if (session != null) {
                if (session.isConnected()) {
                    session.disconnect();
                    logger.info("SSH-сессия отключена.");
                } else {
                    logger.warn("SSH-сессия уже отключена.");
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при закрытии SSH-сессии или канала: ", e);
        } finally {
            shutdownExecutor();
        }
    }

    /**
     * Завершает ExecutorService.
     */
    private void shutdownExecutor() {
        logger.debug("Завершение ExecutorService и ScheduledExecutorService.");
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                logger.warn("ExecutorService принудительно завершён.");
            } else {
                logger.debug("ExecutorService корректно завершён.");
            }
        } catch (InterruptedException e) {
            logger.error("Ошибка при завершении ExecutorService: ", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warn("Scheduler принудительно завершён.");
            } else {
                logger.debug("Scheduler корректно завершён.");
            }
        } catch (InterruptedException e) {
            logger.error("Ошибка при завершении Scheduler: ", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Проверяет, установлено ли SSH-соединение.
     *
     * @return true, если соединение установлено; иначе false.
     */
    public boolean isConnected() {
        boolean connected = session != null && session.isConnected() && channel != null && channel.isConnected();
        logger.debug("Проверка соединения: {}", connected);
        return connected;
    }
    void startSSHConnection() {
        Task<Void> sshTask = new Task<>() {
            @Override
            protected Void call() {
                int retries = 3;
                while (retries > 0) {
                    try {
                        connectAndStart(terminalApp);
                        return null;
                    } catch (Exception e) {
                        retries--;
                        logger.error("Ошибка подключения по SSH, осталось попыток: {}", retries, e);
                        if (retries == 0) {
                            throw e;
                        }
                    }
                }
                return null;
            }
        };
        sshTask.setOnFailed(event -> {
            logger.error("Ошибка подключения по SSH", sshTask.getException());
        });
        executorService.submit(sshTask);
    }
}
