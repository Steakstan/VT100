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
import java.util.function.Consumer;

/**
 * SSHManager is a modern service for handling SSH connections.
 * It establishes a connection, reads incoming data on a separate thread,
 * notifies registered listeners about incoming data, and provides a method for sending data.
 */
public class SSHManager {
    private static final Logger logger = LoggerFactory.getLogger(SSHManager.class);

    private final SSHConfig config;
    private Session session;
    private ChannelShell channel;
    private InputStream inputStream;
    private OutputStream outputStream;

    // Using modern executors for asynchronous operations
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Thread-safe list for data listeners
    private final List<Consumer<String>> dataListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    /**
     * Constructs an SSHManager with the given SSH configuration.
     *
     * @param config the SSH configuration details.
     */
    public SSHManager(SSHConfig config) {
        this.config = config;
    }

    /**
     * Asynchronously establishes an SSH connection.
     *
     * @return a CompletableFuture that completes when the connection is established.
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
     * Establishes an SSH connection using JSch.
     *
     * @throws JSchException if a JSch error occurs.
     * @throws IOException   if an I/O error occurs.
     */
    private void connect() throws JSchException, IOException {
        JSch jsch = new JSch();
        jsch.addIdentity(config.privateKeyPath());
        session = jsch.getSession(config.user(), config.host(), config.port());
        session.setConfig("StrictHostKeyChecking", "no");
        logger.info("Verbinde zu {}:{} mit Benutzer {}", config.host(), config.port(), config.user());
        session.connect(30000); // Timeout 30 seconds

        channel = (ChannelShell) session.openChannel("shell");
        // Obtain input and output streams
        inputStream = channel.getInputStream();
        outputStream = channel.getOutputStream();
        channel.connect(30000);
        isConnected.set(true);
        logger.info("SSH-Verbindung hergestellt.");

        startReading();
    }

    /**
     * Sends data to the SSH server.
     *
     * @param data the string data to send.
     * @throws IOException if an error occurs while writing to the stream.
     */
    public void send(String data) throws IOException {
        if (!isConnected.get()) {
            throw new IllegalStateException("SSH-Verbindung ist nicht hergestellt.");
        }
        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        logger.debug("Gesendet: {}", data);
    }

    /**
     * Asynchronously reads incoming data.
     * When data is received, all registered listeners are notified.
     */
    private void startReading() {
        executor.submit(() -> {
            byte[] buffer = new byte[4096];
            try {
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    logger.debug("Empfangen: {}", received);
                    dataListeners.forEach(listener -> listener.accept(received));
                }
            } catch (IOException e) {
                logger.error("Fehler beim Lesen der SSH-Daten", e);
            }
        });
    }

    /**
     * Adds a listener to receive incoming SSH data.
     *
     * @param listener a Consumer that processes incoming string data.
     */
    public void addDataListener(Consumer<String> listener) {
        dataListeners.add(listener);
    }

    /**
     * Disconnects the SSH connection and shuts down all background executors.
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
        logger.info("SSH-Verbindung geschlossen.");
    }

    /**
     * Properly shuts down the executors.
     */
    private void shutdownExecutors() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    /**
     * Checks whether the SSH connection is currently established.
     *
     * @return true if connected, false otherwise.
     */
    public boolean isConnected() {
        return isConnected.get();
    }
}
