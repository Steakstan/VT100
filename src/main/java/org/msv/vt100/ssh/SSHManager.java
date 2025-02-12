
package org.msv.vt100.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer; /**
 * SSHManager – современный сервис для работы с SSH.
 * Он устанавливает соединение, читает входящие данные в отдельном потоке,
 * уведомляет подписчиков (listeners) о поступлении данных, а также предоставляет метод для отправки данных.
 */
public class SSHManager {
    private static final Logger logger = LoggerFactory.getLogger(SSHManager.class);

    private final SSHConfig config;
    private Session session;
    private ChannelShell channel;
    private InputStream inputStream;
    private OutputStream outputStream;

    // Используем современные executor‑ы
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Для уведомления о входящих данных используем потокобезопасный список слушателей
    private final List<Consumer<String>> dataListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    public SSHManager(SSHConfig config) {
        this.config = config;
    }

    /**
     * Асинхронное установление SSH‑соединения.
     */
    public CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                connect();
            } catch (JSchException | IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Устанавливает SSH-соединение с использованием JSch.
     */
    private void connect() throws JSchException, IOException {
        JSch jsch = new JSch();
        jsch.addIdentity(config.privateKeyPath());
        session = jsch.getSession(config.user(), config.host(), config.port());
        session.setConfig("StrictHostKeyChecking", "no");
        logger.info("Подключение к {}:{} с пользователем {}", config.host(), config.port(), config.user());
        session.connect(30000); // таймаут 30 секунд

        channel = (ChannelShell) session.openChannel("shell");
        // Получаем потоки ввода-вывода
        inputStream = channel.getInputStream();
        outputStream = channel.getOutputStream();
        channel.connect(30000);
        isConnected.set(true);
        logger.info("SSH-соединение установлено.");

        startReading();
    }

    /**
     * Отправляет данные на SSH-сервер.
     * @param data строка данных
     * @throws IOException при ошибке записи в поток
     */
    public void send(String data) throws IOException {
        if (!isConnected.get()) {
            throw new IllegalStateException("SSH-соединение не установлено.");
        }
        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        logger.debug("Отправлено: {}", data);
    }

    /**
     * Асинхронное чтение входящих данных.
     * При получении данных вызываются все зарегистрированные слушатели.
     */
    private void startReading() {
        executor.submit(() -> {
            byte[] buffer = new byte[4096];
            try {
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    logger.debug("Получено: {}", received);
                    dataListeners.forEach(listener -> listener.accept(received));
                }
            } catch (IOException e) {
                logger.error("Ошибка чтения SSH-данных", e);
            }
        });
    }

    /**
     * Добавляет слушателя для входящих данных.
     */
    public void addDataListener(Consumer<String> listener) {
        dataListeners.add(listener);
    }

    /**
     * Отключает SSH-соединение и завершает все фоновые executor‑ы.
     */
    public void disconnect() {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        shutdownExecutors();
        isConnected.set(false);
        logger.info("SSH-соединение закрыто.");
    }

    /**
     * Корректное завершение executor‑ов.
     */
    private void shutdownExecutors() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }
}

