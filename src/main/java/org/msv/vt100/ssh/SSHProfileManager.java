package org.msv.vt100.ssh;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;

/**
 * SSHProfileManager is responsible for loading, saving, and managing SSH profiles
 * stored in a JSON configuration file. All user-facing error messages are in German.
 */
public class SSHProfileManager {
    private static final String CONFIG_FILE = System.getProperty("user.home")
            + File.separator + ".vt100_ssh_profiles.json";
    private static final Gson gson = new Gson();

    /**
     * ProfilesConfig holds a list of SSH profiles.
     */
    public static class ProfilesConfig {
        public List<SSHConfig> profiles = new ArrayList<>();
    }

    /**
     * Loads the SSH profiles configuration from the JSON file.
     *
     * @return a ProfilesConfig object containing the list of profiles.
     */
    public static ProfilesConfig loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            return new ProfilesConfig();
        }
        try (Reader reader = new FileReader(file)) {
            ProfilesConfig config = gson.fromJson(reader, ProfilesConfig.class);
            return config != null ? config : new ProfilesConfig();
        } catch (IOException e) {
            System.err.println("Fehler beim Lesen der Konfigurationsdatei: " + e.getMessage());
            return new ProfilesConfig();
        }
    }

    /**
     * Saves the given SSH profiles configuration to the JSON file.
     *
     * @param config the ProfilesConfig object to save.
     */
    public static void saveConfig(ProfilesConfig config) {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern der Konfigurationsdatei: " + e.getMessage());
        }
    }

    /**
     * Returns the list of SSH profiles from the configuration.
     *
     * @return a List of SSHConfig objects.
     */
    public static List<SSHConfig> getProfiles() {
        return loadConfig().profiles;
    }

    /**
     * Returns the first SSH profile marked for auto-connect.
     *
     * @return an SSHConfig marked for auto-connect, or null if none found.
     */
    public static SSHConfig getAutoConnectProfile() {
        for (SSHConfig profile : getProfiles()) {
            if (profile.autoConnect()) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Compares two SSH profiles ignoring the autoConnect flag.
     *
     * @param a the first SSHConfig
     * @param b the second SSHConfig
     * @return true if the profiles are the same (ignoring autoConnect), false otherwise.
     */
    private static boolean sameProfile(SSHConfig a, SSHConfig b) {
        return a.user().equals(b.user()) &&
                a.host().equals(b.host()) &&
                a.port() == b.port() &&
                a.privateKeyPath().equals(b.privateKeyPath());
    }

    /**
     * Adds a new SSH profile to the configuration. If the new profile is marked for auto-connect,
     * all other profiles are updated to disable auto-connect.
     *
     * @param profile the SSHConfig to add.
     */
    public static void addProfile(SSHConfig profile) {
        ProfilesConfig config = loadConfig();
        if (profile.autoConnect()) {
            // Disable auto-connect for all other profiles
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

    /**
     * Updates an existing SSH profile in the configuration.
     * If the updated profile is marked for auto-connect, auto-connect is disabled for others.
     *
     * @param updatedProfile the SSHConfig with updated details.
     */
    public static void updateProfile(SSHConfig updatedProfile) {
        ProfilesConfig config = loadConfig();
        List<SSHConfig> updatedList = new ArrayList<>();
        boolean auto = updatedProfile.autoConnect();
        for (SSHConfig p : config.profiles) {
            // Compare profiles ignoring autoConnect
            if (sameProfile(p, updatedProfile)) {
                updatedList.add(updatedProfile);
            } else {
                // If the new profile is marked for auto-connect, disable it for others
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

    /**
     * Deletes the specified SSH profile from the configuration.
     *
     * @param profile the SSHConfig to delete.
     */
    public static void deleteProfile(SSHConfig profile) {
        ProfilesConfig config = loadConfig();
        config.profiles.removeIf(p -> sameProfile(p, profile));
        saveConfig(config);
    }
}
