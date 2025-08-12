package org.msv.vt100.ssh;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SSHProfileManager {

    private static final String CONFIG_FILE = System.getProperty("user.home")
            + File.separator + ".vt100_ssh_profiles.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ReentrantReadWriteLock RW = new ReentrantReadWriteLock();

    public static class ProfilesConfig {
        public List<SSHConfig> profiles = new ArrayList<>();
    }

    public static List<SSHConfig> getProfiles() {
        RW.readLock().lock();
        try {
            return new ArrayList<>(loadInternal().profiles);
        } finally {
            RW.readLock().unlock();
        }
    }

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

    public static void addProfile(SSHConfig profile) {
        Objects.requireNonNull(profile, "profile");
        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            if (cfg.profiles.stream().anyMatch(p -> equalsFull(p, profile))) {
                return;
            }
            if (profile.autoConnect()) {
                cfg.profiles = disableAutoConnectForAll(cfg.profiles);
            }
            cfg.profiles.add(profile);
            saveInternal(cfg);
        } finally {
            RW.writeLock().unlock();
        }
    }

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
                    if (updatedProfile.autoConnect() && p.autoConnect()) {
                        updated.add(copyWithAuto(p, false));
                    } else {
                        updated.add(p);
                    }
                }
            }

            if (!replaced) {
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

    public static void setAutoConnect(SSHConfig profile, boolean auto) {
        Objects.requireNonNull(profile, "profile");
        RW.writeLock().lock();
        try {
            ProfilesConfig cfg = loadInternal();
            List<SSHConfig> result = new ArrayList<>(cfg.profiles.size());
            boolean found = false;

            if (auto) {
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

    private static List<SSHConfig> disableAutoConnectForAll(List<SSHConfig> src) {
        List<SSHConfig> res = new ArrayList<>(src.size());
        for (SSHConfig p : src) {
            if (p.autoConnect()) res.add(copyWithAuto(p, false));
            else res.add(p);
        }
        return res;
    }

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
            cfg.profiles.removeIf(Objects::isNull);
            return cfg;
        } catch (Exception e) {
            System.err.println("Fehler beim Lesen der Konfigurationsdatei: " + e.getMessage());
            return new ProfilesConfig();
        }
    }

    private static void saveInternal(ProfilesConfig cfg) {
        Path path = Path.of(CONFIG_FILE);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(cfg, w);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                if (Files.exists(tmp)) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
            System.err.println("Fehler beim Speichern der Konfigurationsdatei: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}
