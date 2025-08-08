package org.msv.vt100.ssh;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * SSHManager — управление SSH-сессией (JSch) с:
 * - автоматическим приёмом и сохранением HostKey при первом подключении (если записи нет),
 * - строгой проверкой HostKey на последующих сеансах,
 * - PreferredAuthentications=publickey (быстрее),
 * - опциональным форсированием IPv4 + HostKeyAlias,
 * - безопасной остановкой и асинхронным чтением,
 * - keep-alive.
 */
public class SSHManager {
    private static final Logger logger = LoggerFactory.getLogger(SSHManager.class);

    // Рекомендованные таймауты для быстрого UX
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int CHANNEL_TIMEOUT_MS = 3_000;

    private final SSHConfig config;

    private volatile Session session;
    private volatile ChannelShell channel;
    private volatile InputStream inputStream;
    private volatile OutputStream outputStream;

    // Отдельные single-thread executors под коннект и чтение
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ssh-reader");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ssh-connect");
        t.setDaemon(true);
        return t;
    });

    private final List<Consumer<String>> dataListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    // Настройки безопасности/сети
    private boolean strictHostKeyChecking = true;    // после "обучения" оставляем строгую проверку
    private String knownHostsPath = null;            // если null — используем ~/.ssh/known_hosts
    private int serverAliveIntervalMs = 15_000;
    private int serverAliveCountMax = 3;
    private boolean forceIPv4 = false;               // опциональный форс IPv4 (ускоряет при проблемах с IPv6)

    public SSHManager(SSHConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /* ===================== ПУБЛИЧНЫЙ API ===================== */

    /** Асинхронное подключение (не блокирует UI-поток). */
    public CompletableFuture<Void> connectAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                connect();
            } catch (Exception e) {
                isConnected.set(false);
                logger.error("SSH-Verbindungsfehler", e);
                throw new CompletionException(e);
            }
        }, connectExecutor);
    }

    /** Потокобезопасно отправляет данные в SSH-канал. */
    public void send(String data) throws IOException {
        if (!isConnected.get()) {
            throw new IllegalStateException("SSH-Verbindung ist nicht hergestellt.");
        }
        if (data == null || data.isEmpty()) return;
        synchronized (this) {
            if (outputStream == null) throw new IOException("OutputStream ist geschlossen.");
            outputStream.write(data.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
        logger.debug("Gesendet: {}", data);
    }

    /** Подписка на входящие данные. Возвращает AutoCloseable для удобной отписки. */
    public AutoCloseable addDataListener(Consumer<String> listener) {
        dataListeners.add(listener);
        return () -> dataListeners.remove(listener);
    }

    /** Корректно разрывает соединение и останавливает фоновые потоки. */
    public void disconnect() {
        closeQuietly(inputStream);
        closeQuietly(outputStream);
        try { if (channel != null && channel.isConnected()) channel.disconnect(); } catch (Exception ignore) {}
        try { if (session != null && session.isConnected()) session.disconnect(); } catch (Exception ignore) {}
        isConnected.set(false);
        shutdownExecutors();
        logger.info("SSH-Verbindung geschlossen.");
    }

    public boolean isConnected() { return isConnected.get(); }

    /* ===================== НАСТРОЙКИ (флюент) ===================== */

    /** Строгая проверка host key (после "обучения" — да/нет). По умолчанию true. */
    public SSHManager withStrictHostKeyChecking(boolean strict) {
        this.strictHostKeyChecking = strict;
        return this;
    }

    /** Путь к known_hosts (если не задан — возьмём ~/.ssh/known_hosts). */
    public SSHManager withKnownHosts(String path) {
        this.knownHostsPath = path;
        return this;
    }

    /** Keep-alive параметры. */
    public SSHManager withKeepAlive(int intervalMs, int countMax) {
        this.serverAliveIntervalMs = Math.max(0, intervalMs);
        this.serverAliveCountMax = Math.max(1, countMax);
        return this;
    }

    /** Форсировать IPv4 (ускоряет при проблемном IPv6). По умолчанию false. */
    public SSHManager withForceIPv4(boolean force) {
        this.forceIPv4 = force;
        return this;
    }

    /* ===================== ВНУТРЕННЯЯ ЛОГИКА ===================== */

    private void connect() throws JSchException, IOException {
        JSch jsch = new JSch();

        // 1) known_hosts
        String khPath = resolveKnownHostsPath();
        boolean khExists = khPath != null && new File(khPath).exists();
        if (khExists) {
            try {
                jsch.setKnownHosts(khPath);
                logger.debug("KnownHosts verwendet: {}", khPath);
            } catch (JSchException e) {
                logger.warn("KnownHosts konnte nicht gesetzt werden: {}", e.getMessage());
            }
        }

        // 2) Идентификация пользователем (ключ)
        jsch.addIdentity(config.privateKeyPath());

        // 3) Хост/адрес назначения
        String sessionHost;
        if (forceIPv4) {
            InetAddress[] all = InetAddress.getAllByName(config.host());
            InetAddress v4 = Arrays.stream(all)
                    .filter(a -> a instanceof Inet4Address)
                    .findFirst()
                    .orElse(all[0]); // если нет IPv4, берём что есть
            sessionHost = v4.getHostAddress();
            logger.info("Connecting via IPv4 {} (alias {})", sessionHost, config.host());
        } else {
            sessionHost = config.host();
        }

        // 4) Создаём и настраиваем сессию
        session = jsch.getSession(config.user(), sessionHost, config.port());

        // Чтобы ключ в known_hosts сопоставлялся с именем сервера, а не IP (актуально при forceIPv4)
        if (forceIPv4) {
            session.setConfig("HostKeyAlias", config.host());
        }

        // Ускоряем — используем только publickey
        session.setConfig("PreferredAuthentications", "publickey");

        // Keep-alive
        try {
            session.setServerAliveInterval(serverAliveIntervalMs);
            session.setServerAliveCountMax(serverAliveCountMax);
        } catch (Exception e) {
            logger.warn("Keep-alive konnte nicht gesetzt werden: {}", e.getMessage());
        }

        // 5) Решаем StrictHostKeyChecking для ЭТОГО подключения
        String strictThisTime = "yes";
        if (khExists) {
            HostKeyRepository repo = jsch.getHostKeyRepository();
            HostKey[] existing = safeGetHostKeys(repo, config.host());
            if (existing != null && existing.length > 0) {
                // Запись для хоста уже есть → уважаем глобальную настройку
                strictThisTime = strictHostKeyChecking ? "yes" : "no";
            } else {
                // Для этого хоста записи нет → примем ключ и потом сохраним
                strictThisTime = "no";
            }
        } else {
            // Файла known_hosts нет → примем и создадим
            strictThisTime = "no";
        }
        session.setConfig("StrictHostKeyChecking", strictThisTime);

        logger.info("Verbinde zu {}:{} als {} (StrictHostKeyChecking={}, forceIPv4={})",
                config.host(), config.port(), config.user(), strictThisTime, forceIPv4);

        // 6) Подключаемся
        long t0 = System.currentTimeMillis();
        session.connect(CONNECT_TIMEOUT_MS);
        logger.debug("session.connect in {} ms", (System.currentTimeMillis() - t0));

        // 7) Если принимали хост-ключ впервые — сохраним его
        if ("no".equalsIgnoreCase(strictThisTime)) {
            try {
                if (khPath == null) {
                    khPath = System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "known_hosts";
                    this.knownHostsPath = khPath;
                }
                File khFile = new File(khPath);
                File parent = khFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                jsch.setKnownHosts(khPath);
                HostKeyRepository repo = jsch.getHostKeyRepository();
                HostKey hk = session.getHostKey();

                HostKey[] existing = safeGetHostKeys(repo, config.host());
                if (existing == null || existing.length == 0) {
                    repo.add(hk, null);
                    logger.info("HostKey für '{}' gespeichert: {}", hk.getHost(), khPath);
                } else {
                    logger.info("HostKey-Eintrag für '{}' existiert bereits — nicht überschrieben.", config.host());
                }
            } catch (Exception e) {
                logger.warn("Konnte HostKey nicht speichern: {}", e.getMessage());
            }
        }

        // 8) Открываем shell-канал
        channel = (ChannelShell) session.openChannel("shell");
        inputStream = channel.getInputStream();
        outputStream = channel.getOutputStream();

        long t1 = System.currentTimeMillis();
        channel.connect(CHANNEL_TIMEOUT_MS);
        logger.debug("channel.connect in {} ms", (System.currentTimeMillis() - t1));

        isConnected.set(true);
        logger.info("SSH-Verbindung hergestellt.");

        startReading();
    }

    private HostKey[] safeGetHostKeys(HostKeyRepository repo, String host) {
        try {
            return repo.getHostKey(host, null);
        } catch (Exception e) {
            logger.debug("getHostKey failed for {}: {}", host, e.getMessage());
            return null;
        }
    }

    private String resolveKnownHostsPath() {
        if (knownHostsPath != null && !knownHostsPath.isBlank()) return knownHostsPath;
        return System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "known_hosts";
    }

    private void startReading() {
        readerExecutor.submit(() -> {
            byte[] buffer = new byte[4096];
            try {
                int bytesRead;
                while (isConnected.get() && inputStream != null && (bytesRead = inputStream.read(buffer)) != -1) {
                    if (bytesRead == 0) continue;
                    String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    logger.debug("Empfangen: {}", received);
                    for (Consumer<String> l : dataListeners) {
                        try { l.accept(received); } catch (Throwable t) {
                            logger.warn("Listener-Fehler: {}", t.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                if (isConnected.get()) {
                    logger.error("Fehler beim Lesen der SSH-Daten", e);
                } else {
                    logger.debug("Leseschleife beendet (Disconnect).");
                }
            } finally {
                isConnected.set(false);
            }
        });
    }

    private void shutdownExecutors() {
        readerExecutor.shutdownNow();
        connectExecutor.shutdownNow();
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignore) {}
    }
}
