package org.msv.vt100.ssh;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Менеджер SSH-профилей с:
 * - атомарной записью JSON;
 * - синхронизированным доступом;
 * - корректным обновлением профиля (вариант old->updated);
 * - единственным autoConnect-профилем;
 * - мягкой миграцией формата (если файл пустой/битый).
 */
public class SSHProfileManager {

    private static final String CONFIG_FILE = System.getProperty("user.home")
            + File.separator + ".vt100_ssh_profiles.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Блокировки на чтение/запись файла
    private static final ReentrantReadWriteLock RW = new ReentrantReadWriteLock();

    /** Обёртка для хранения списка профилей. */
    public static class ProfilesConfig {
        public List<SSHConfig> profiles = new ArrayList<>();
    }

    /* ===================== ПУБЛИЧНЫЙ API ===================== */

    /** Вернуть все профили (defensive copy). */
    public static List<SSHConfig> getProfiles() {
        RW.readLock().lock();
        try {
            return new ArrayList<>(loadInternal().profiles);
        } finally {
            RW.readLock().unlock();
        }
    }

    /** Первый профиль с autoConnect=true, либо null. */
    public static SSHConfig getAutoConnectProfile() {
        RW.readLock().lock();
        try {
            for (SSHConfig p : loadInternal().profiles) {
                if (p.autoConnect()) return p;
            }
            return null;
        } finally {
            RW.readLock().unlock();
        }
    }

    /**
     * Добавить профиль.
     * Если profile.autoConnect == true — отключим autoConnect у остальных.
     * Дубликаты (полное совпадение user/host/port/keyPath/auto) не добавляем.
     */
    public static void addProfile(SSHConfig profile) {
        Objects.requireNonNull(profile, "profile");
        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            // уже есть полный дубликат?
            if (cfg.profiles.stream().anyMatch(p -> equalsFull(p, profile))) {
                return; // не дублируем
            }
            // если автоконнект — снимаем у остальных
            if (profile.autoConnect()) {
                cfg.profiles = disableAutoConnectForAll(cfg.profiles);
            }
            cfg.profiles.add(profile);
            saveInternal(cfg);
        } finally {
            RW.writeLock().unlock();
        }
    }

    /**
     * Обновить профиль (БЕЗОПАСНЫЙ ВАРИАНТ): старый -> новый.
     * Сопоставление происходит по полному совпадению старого профиля (кроме autoConnect? нет — включая).
     * Если старый профиль не найден — добавим как новый.
     */
    public static void updateProfile(SSHConfig oldProfile, SSHConfig updatedProfile) {
        Objects.requireNonNull(oldProfile, "oldProfile");
        Objects.requireNonNull(updatedProfile, "updatedProfile");

        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            boolean replaced = false;

            List<SSHConfig> updated = new ArrayList<>(cfg.profiles.size());
            for (SSHConfig p : cfg.profiles) {
                if (equalsFull(p, oldProfile)) {
                    updated.add(updatedProfile);
                    replaced = true;
                } else {
                    // если updated помечен как auto — снимем флаг у остальных
                    if (updatedProfile.autoConnect() && p.autoConnect()) {
                        updated.add(copyWithAuto(p, false));
                    } else {
                        updated.add(p);
                    }
                }
            }

            if (!replaced) {
                // старый не найден — добавим новый
                if (updatedProfile.autoConnect()) {
                    updated = disableAutoConnectForAll(updated);
                }
                updated.add(updatedProfile);
            }

            cfg.profiles = updated;
            saveInternal(cfg);
        } finally {
            RW.writeLock().unlock();
        }
    }

    /**
     * Обновить профиль (СОВМЕСТИМЫЙ ВАРИАНТ — как раньше).
     * Пытаемся:
     *  1) заменить точное совпадение (без autoConnect),
     *  2) если нет — заменить единственный профиль с тем же (user, host),
     *  3) иначе — добавить как новый.
     */
    public static void updateProfile(SSHConfig updatedProfile) {
        Objects.requireNonNull(updatedProfile, "updatedProfile");

        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            boolean replaced = false;

            // шаг 1: точное совпадение по sameProfile (user, host, port, keyPath)
            int idx = indexOfSameProfile(cfg.profiles, updatedProfile);
            if (idx >= 0) {
                cfg.profiles.set(idx, updatedProfile);
                replaced = true;
            } else {
                // шаг 2: единственный профиль с тем же user+host
                List<Integer> sameUH = indexOfSameUserHost(cfg.profiles, updatedProfile.user(), updatedProfile.host());
                if (sameUH.size() == 1) {
                    cfg.profiles.set(sameUH.get(0), updatedProfile);
                    replaced = true;
                }
            }

            // автофлаг — обеспечить единственность
            if (updatedProfile.autoConnect()) {
                cfg.profiles = disableAutoConnectForAll(cfg.profiles);
                if (replaced) {
                    // уже стоит updatedProfile — он и будет авто
                } else {
                    // если ещё не добавлен — добавим с авто
                    cfg.profiles.add(updatedProfile);
                    replaced = true;
                }
            } else if (!replaced) {
                // просто добавить новый
                cfg.profiles.add(updatedProfile);
            }

            saveInternal(cfg);
        } finally {
            RW.writeLock().unlock();
        }
    }

    /** Удалить профиль (сопоставление по sameProfile: user/host/port/keyPath). */
    public static void deleteProfile(SSHConfig profile) {
        Objects.requireNonNull(profile, "profile");
        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            cfg.profiles.removeIf(p -> sameProfile(p, profile));
            saveInternal(cfg);
        } finally {
            RW.writeLock().unlock();
        }
    }

    /**
     * Установить autoConnect для заданного профиля (по sameProfile).
     * Если профиль не найден — ничего не делаем.
     */
    public static void setAutoConnect(SSHConfig profile, boolean auto) {
        Objects.requireNonNull(profile, "profile");
        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            List<SSHConfig> result = new ArrayList<>(cfg.profiles.size());
            boolean found = false;

            if (auto) {
                // один авто — остальные выключаем
                for (SSHConfig p : cfg.profiles) {
                    if (sameProfile(p, profile)) {
                        result.add(copyWithAuto(profile, true));
                        found = true;
                    } else if (p.autoConnect()) {
                        result.add(copyWithAuto(p, false));
                    } else {
                        result.add(p);
                    }
                }
            } else {
                for (SSHConfig p : cfg.profiles) {
                    if (sameProfile(p, profile)) {
                        result.add(copyWithAuto(profile, false));
                        found = true;
                    } else {
                        result.add(p);
                    }
                }
            }

            if (found) {
                cfg.profiles = result;
                saveInternal(cfg);
            }
        } finally {
            RW.writeLock().unlock();
        }
    }

    /* ===================== ВНУТРЕННИЕ УТИЛИТЫ ===================== */

    private static boolean sameProfile(SSHConfig a, SSHConfig b) {
        return a.user().equals(b.user()) &&
                a.host().equals(b.host()) &&
                a.port() == b.port() &&
                a.privateKeyPath().equals(b.privateKeyPath());
    }

    private static boolean equalsFull(SSHConfig a, SSHConfig b) {
        return sameProfile(a, b) && a.autoConnect() == b.autoConnect();
    }

    private static SSHConfig copyWithAuto(SSHConfig p, boolean auto) {
        return new SSHConfig(p.user(), p.host(), p.port(), p.privateKeyPath(), auto);
    }

    private static int indexOfSameProfile(List<SSHConfig> list, SSHConfig probe) {
        for (int i = 0; i < list.size(); i++) {
            if (sameProfile(list.get(i), probe)) return i;
        }
        return -1;
    }

    private static List<Integer> indexOfSameUserHost(List<SSHConfig> list, String user, String host) {
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            SSHConfig p = list.get(i);
            if (p.user().equals(user) && p.host().equals(host)) {
                idxs.add(i);
            }
        }
        return idxs;
    }

    private static List<SSHConfig> disableAutoConnectForAll(List<SSHConfig> src) {
        List<SSHConfig> res = new ArrayList<>(src.size());
        for (SSHConfig p : src) {
            if (p.autoConnect()) res.add(copyWithAuto(p, false));
            else res.add(p);
        }
        return res;
    }

    /* ===================== ЧТЕНИЕ/ЗАПИСЬ JSON ===================== */

    private static ProfilesConfig loadInternal() {
        Path path = Path.of(CONFIG_FILE);
        if (!Files.exists(path)) {
            return new ProfilesConfig();
        }
        try (Reader r = Files.newBufferedReader(path)) {
            ProfilesConfig cfg = GSON.fromJson(r, ProfilesConfig.class);
            if (cfg == null || cfg.profiles == null) {
                return new ProfilesConfig(); // мягкая миграция
            }
            // Санитация null-списка/элементов
            cfg.profiles.removeIf(Objects::isNull);
            return cfg;
        } catch (Exception e) {
            System.err.println("Fehler beim Lesen der Konfigurationsdatei: " + e.getMessage());
            return new ProfilesConfig(); // мягкая деградация при битом JSON
        }
    }

    /** Атомарная запись: во временный файл, затем move(REPLACE_EXISTING). */
    private static void saveInternal(ProfilesConfig cfg) {
        Path path = Path.of(CONFIG_FILE);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

        try {
            // гарантируем каталог
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(cfg, w);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // если ATOMIC_MOVE недоступен — повторим без него
            try {
                if (Files.exists(tmp)) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
            System.err.println("Fehler beim Speichern der Konfigurationsdatei: " + e.getMessage());
        } finally {
            // подчистим tmp на всякий случай
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}
