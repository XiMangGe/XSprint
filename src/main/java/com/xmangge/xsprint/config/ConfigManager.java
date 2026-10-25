package com.xmangge.xsprint.config;

import com.xmangge.xsprint.XSprint;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final XSprint plugin;
    private FileConfiguration config;
    private File config_file;

    private final Map<String, MinecartSpeedConfig> minecartConfigs = new HashMap<>();
    private float globalSpeedLimit = 25.0f;

    public ConfigManager(XSprint plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        saveDefaultConfig();
        config_file = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(config_file);

        String lang = config.getString("language", "zh_cn");
        plugin.getLanguageManager().setLanguage(lang);

        minecartConfigs.clear();
        if (config.contains("minecarts")) {
            for (String key : config.getConfigurationSection("minecarts").getKeys(false)) {
                boolean enabled = config.getBoolean("minecarts." + key + ".enabled", true);
                int speed = config.getInt("minecarts." + key + ".speed", 25);
                minecartConfigs.put(key, new MinecartSpeedConfig(key, enabled, speed));
            }
        }

        globalSpeedLimit = (float) config.getDouble("global-speed-limit", 25.0);

        plugin.getLogger().info("Config loaded. Minecart configs: " + minecartConfigs.size());
    }

    public void reload() {
        if (config_file.exists()) {
            config = YamlConfiguration.loadConfiguration(config_file);
        }
        loadConfigs();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public Map<String, MinecartSpeedConfig> getMinecartConfigs() {
        return minecartConfigs;
    }

    public MinecartSpeedConfig getMinecartConfig(String type) {
        return minecartConfigs.get(type.toLowerCase());
    }

    public float getGlobalSpeedLimit() {
        return globalSpeedLimit;
    }

    public boolean isSpeedDisplayEnabled() {
        return config.getBoolean("speed-display.enabled", true);
    }

    public String getDisplayMethod() {
        return config.getString("speed-display.method", "actionbar");
    }

    public void setLanguage(String lang) {
        config.set("language", lang);
        saveConfig();
        plugin.getLanguageManager().setLanguage(lang);
    }

    public void setMinecartSpeed(String type, int speed) {
        String lowerType = type.toLowerCase();
        MinecartSpeedConfig existing = minecartConfigs.get(lowerType);
        if (existing != null) {
            existing.speed = speed;
        } else {
            // 新类型，自动添加到配置
            minecartConfigs.put(lowerType, new MinecartSpeedConfig(lowerType, true, speed));
        }
        config.set("minecarts." + lowerType + ".enabled", true);
        config.set("minecarts." + lowerType + ".speed", speed);
        saveConfig();
    }

    public void setMinecartEnabled(String type, boolean enabled) {
        config.set("minecarts." + type + ".enabled", enabled);
        MinecartSpeedConfig existing = minecartConfigs.get(type.toLowerCase());
        if (existing != null) {
            existing.enabled = enabled;
        }
        saveConfig();
    }

    public void setGlobalSpeedLimit(float speed) {
        this.globalSpeedLimit = speed;
        config.set("global-speed-limit", speed);
        saveConfig();
    }

    public void setDisplayEnabled(boolean enabled) {
        config.set("speed-display.enabled", enabled);
        saveConfig();
    }

    public void setDisplayMethod(String method) {
        config.set("speed-display.method", method);
        saveConfig();
    }

    private void saveConfig() {
        try {
            config.save(config_file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config: " + e.getMessage());
        }
    }

    public void saveDefaultConfig() {
        if (!config_file.exists()) {
            plugin.saveDefaultConfig();
            File langDir = new File(plugin.getDataFolder(), "lang");
            if (!langDir.exists()) {
                langDir.mkdirs();
            }
            copyLangFile("zh_cn.yml");
            copyLangFile("en_us.yml");
        }
    }

    private void copyLangFile(String fileName) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        File target = new File(langDir, fileName);
        if (!target.exists()) {
            InputStream def = plugin.getResource("lang/" + fileName);
            if (def != null) {
                try (OutputStream out = java.nio.file.Files.newOutputStream(target.toPath())) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = def.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to copy lang file: " + e.getMessage());
                }
            }
        }
    }

    public static class MinecartSpeedConfig {
        public final String type;
        public boolean enabled;
        public int speed;

        public MinecartSpeedConfig(String type, boolean enabled, int speed) {
            this.type = type;
            this.enabled = enabled;
            this.speed = speed;
        }

        public float getMaxSpeed() {
            if (!enabled) return 0f;
            return getEffectiveMaxSpeed(speed);
        }

        public float getEffectiveMaxSpeed(int requestedSpeed) {
            if (!enabled) return 0f;
            // 高速矿车（熔炉/TNT）上限更高，普通矿车保守一些
            if (type.equals("furnace_minecart") || type.equals("TNT_minecart")) {
                return Math.min(requestedSpeed, 300);
            }
            return Math.min(requestedSpeed, 100);
        }
    }
}
