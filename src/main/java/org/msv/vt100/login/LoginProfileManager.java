package org.msv.vt100.login;

import java.util.ArrayList;
import java.util.List;

public class LoginProfileManager {

    public static List<LoginProfile> loadProfiles() {
        LoginProfilesConfig config = LoginProfilesConfig.loadConfig();
        return config.profiles;
    }

    public static void saveProfiles(List<LoginProfile> profiles) {
        LoginProfilesConfig config = new LoginProfilesConfig();
        config.profiles = profiles;
        LoginProfilesConfig.saveConfig(config);
    }

    public static void addProfile(LoginProfile profile) {
        List<LoginProfile> profiles = loadProfiles();
        if (profile.autoConnect()) {
            // Если новый профиль отмечен для автоподключения, сбрасываем у остальных
            List<LoginProfile> updated = new ArrayList<>();
            for (LoginProfile p : profiles) {
                if (p.autoConnect()) {
                    updated.add(new LoginProfile(p.profileName(), p.username(), p.password(), false));
                } else {
                    updated.add(p);
                }
            }
            profiles = updated;
        }
        profiles.add(profile);
        saveProfiles(profiles);
    }

    public static void deleteProfile(LoginProfile profile) {
        List<LoginProfile> profiles = loadProfiles();
        profiles.removeIf(p -> p.profileName().equals(profile.profileName()));
        saveProfiles(profiles);
    }

    public static LoginProfile getAutoConnectProfile() {
        for (LoginProfile profile : loadProfiles()) {
            if (profile.autoConnect()) {
                return profile;
            }
        }
        return null;
    }

    public static void updateProfile(LoginProfile updatedProfile) {
        List<LoginProfile> profiles = loadProfiles();
        List<LoginProfile> updatedList = new ArrayList<>();
        boolean newAuto = updatedProfile.autoConnect();
        for (LoginProfile p : profiles) {
            if (p.profileName().equals(updatedProfile.profileName())) {
                updatedList.add(updatedProfile);
            } else {
                updatedList.add(new LoginProfile(p.profileName(), p.username(), p.password(), !newAuto && p.autoConnect()));
            }
        }
        saveProfiles(updatedList);
    }
}
