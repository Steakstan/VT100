package org.msv.vt100.login;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class LoginProfilesConfig {
    public List<LoginProfile> profiles = new ArrayList<>();

    private static final String CONFIG_FILE = System.getProperty("user.home") + File.separator + ".vt100_login_profiles.enc";
    private static final String ALGORITHM = "AES";
    // В реальном приложении ключ не должен быть захардкожен
    private static final byte[] KEY_BYTES = "MySecretKey12345".getBytes(StandardCharsets.UTF_8); // 16 байт для AES
    private static final Key SECRET_KEY = new SecretKeySpec(KEY_BYTES, ALGORITHM);
    private static final Gson gson = new Gson();

    public static LoginProfilesConfig loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            return new LoginProfilesConfig();
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] encrypted = fis.readAllBytes();
            byte[] decrypted = decrypt(encrypted);
            String json = new String(decrypted, StandardCharsets.UTF_8).trim();
            LoginProfilesConfig config;
            if (json.startsWith("[")) {
                // Файл содержит массив, оборачиваем его в объект конфигурации
                List<LoginProfile> profiles = gson.fromJson(json, new TypeToken<List<LoginProfile>>(){}.getType());
                config = new LoginProfilesConfig();
                config.profiles = profiles != null ? profiles : new ArrayList<>();
            } else {
                // Файл содержит объект с полем profiles
                config = gson.fromJson(json, LoginProfilesConfig.class);
                if (config == null) {
                    config = new LoginProfilesConfig();
                }
            }
            return config;
        } catch (Exception e) {
            System.err.println("Ошибка чтения конфигурации логин-профилей: " + e.getMessage());
            return new LoginProfilesConfig();
        }
    }


    public static void saveConfig(LoginProfilesConfig config) {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            String json = gson.toJson(config);
            byte[] encrypted = encrypt(json.getBytes(StandardCharsets.UTF_8));
            fos.write(encrypted);
        } catch (Exception e) {
            System.err.println("Ошибка сохранения конфигурации логин-профилей: " + e.getMessage());
        }
    }

    private static byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY);
        return cipher.doFinal(data);
    }

    private static byte[] decrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY);
        return cipher.doFinal(data);
    }
}
