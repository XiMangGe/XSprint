package com.xmangge.xsprint.language;

import com.xmangge.xsprint.XSprint;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class LanguageManager {

    private final XSprint plugin;
    private FileConfiguration messages;
    private String currentLang = "zh_cn";

    // 支持的语言列表
    public static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("zh_cn", "en_us");

    public LanguageManager(XSprint plugin) {
        this.plugin = plugin;
    }

    public void loadLanguage() {
        loadLanguage(currentLang);
    }

    public void loadLanguage(String lang) {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File langFile = new File(langDir, lang + ".yml");
        if (!langFile.exists()) {
            // 尝试从jar内资源加载
            plugin.saveResource("lang/" + lang + ".yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(langFile);

        // 如果文件为空，尝试从resources加载
        if (messages.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Language file " + lang + ".yml is empty, falling back to default");
            plugin.saveResource("lang/" + lang + ".yml", true);
            messages = YamlConfiguration.loadConfiguration(langFile);
        }

        currentLang = lang;
        plugin.getLogger().info("Language set to: " + lang);
    }

    public void setLanguage(String lang) {
        if (!SUPPORTED_LANGUAGES.contains(lang)) {
            plugin.getLogger().warning("Unsupported language: " + lang + ", using zh_cn");
            lang = "zh_cn";
        }
        currentLang = lang;
        loadLanguage(lang);
    }

    public String get(String key) {
        String value = messages.getString(key);
        if (value == null) {
            // 回退到英语
            if (!currentLang.equals("en_us")) {
                value = messages.getString("en_us." + key);
            }
            if (value == null) {
                return "&c[missing:" + key + "]";
            }
        }
        return value;
    }

    public String get(String key, Object... args) {
        String value = get(key);
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value;
    }

    public List<String> getList(String key) {
        return messages.getStringList(key);
    }

    public String getCurrentLang() {
        return currentLang;
    }

    public FileConfiguration getMessages() {
        return messages;
    }
}
