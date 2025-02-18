package org.msv.vt100.ssh;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

public class SSHProfileManager {
    private static final String CONFIG_FILE = System.getProperty("user.home")
            + File.separator + ".vt100_ssh_profiles.json";
    private static final Gson gson = new Gson();

    public static class ProfilesConfig {
        public List<SSHConfig> profiles = new ArrayList<>();
    }

    public static ProfilesConfig loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            return new ProfilesConfig();
        }
        try (Reader reader = new FileReader(file)) {
            ProfilesConfig config = gson.fromJson(reader, ProfilesConfig.class);
            return config != null ? config : new ProfilesConfig();
        } catch(IOException e) {
            e.printStackTrace();
            return new ProfilesConfig();
        }
    }

    public static void saveConfig(ProfilesConfig config) {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static List<SSHConfig> getProfiles() {
        return loadConfig().profiles;
    }

    public static SSHConfig getAutoConnectProfile() {
        for (SSHConfig profile : getProfiles()) {
            if (profile.autoConnect()) {
                return profile;
            }
        }
        return null;
    }

    // Сравнение профилей без учета поля autoConnect
    private static boolean sameProfile(SSHConfig a, SSHConfig b) {
        return a.user().equals(b.user()) &&
                a.host().equals(b.host()) &&
                a.port() == b.port() &&
                a.privateKeyPath().equals(b.privateKeyPath());
    }

    public static void addProfile(SSHConfig profile) {
        ProfilesConfig config = loadConfig();
        if (profile.autoConnect()) {
            // Сбрасываем автоподключение у остальных
            List<SSHConfig> updated = new ArrayList<>();
            for (SSHConfig p : config.profiles) {
                if (p.autoConnect()) {
                    updated.add(new SSHConfig(p.user(), p.host(), p.port(), p.privateKeyPath(), false));
                } else {
                    updated.add(p);
                }
            }
            config.profiles = updated;
        }
        config.profiles.add(profile);
        saveConfig(config);
    }

    public static void updateProfile(SSHConfig updatedProfile) {
        ProfilesConfig config = loadConfig();
        List<SSHConfig> updatedList = new ArrayList<>();
        boolean auto = updatedProfile.autoConnect();
        for (SSHConfig p : config.profiles) {
            // Сравнение без учета autoConnect
            if (sameProfile(p, updatedProfile)) {
                updatedList.add(updatedProfile);
            } else {
                // Если новый профиль помечен для автоподключения – у остальных снимаем галочку
                if (auto && p.autoConnect()) {
                    updatedList.add(new SSHConfig(p.user(), p.host(), p.port(), p.privateKeyPath(), false));
                } else {
                    updatedList.add(p);
                }
            }
        }
        config.profiles = updatedList;
        saveConfig(config);
    }

    public static void deleteProfile(SSHConfig profile) {
        ProfilesConfig config = loadConfig();
        config.profiles.removeIf(p -> sameProfile(p, profile));
        saveConfig(config);
    }
}
