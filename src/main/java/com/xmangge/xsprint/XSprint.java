package com.xmangge.xsprint;

import com.xmangge.xsprint.commands.SprintCommand;
import com.xmangge.xsprint.config.ConfigManager;
import com.xmangge.xsprint.language.LanguageManager;
import com.xmangge.xsprint.listeners.InteractListener;
import com.xmangge.xsprint.listeners.SpeedDisplayListener;
import com.xmangge.xsprint.speed.MinecartSpeedManager;
import org.bukkit.plugin.java.JavaPlugin;

public class XSprint extends JavaPlugin {

    private static XSprint instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private MinecartSpeedManager speedManager;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // 初始化语言管理器
        languageManager = new LanguageManager(this);
        languageManager.loadLanguage();

        // 初始化速度管理器
        speedManager = new MinecartSpeedManager(this);
        speedManager.onEnable();

        // 注册监听器
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new SpeedDisplayListener(this), this);

        // 注册命令
        getCommand("XSprint").setExecutor(new SprintCommand(this));

        getLogger().info("XSprint v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (speedManager != null) {
            speedManager.onDisable();
        }
        getLogger().info("XSprint v" + getDescription().getVersion() + " disabled!");
    }

    public static XSprint getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public MinecartSpeedManager getSpeedManager() {
        return speedManager;
    }
}
