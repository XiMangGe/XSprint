package com.xmangge.xsprint.speed;

import com.xmangge.xsprint.XSprint;
import com.xmangge.xsprint.config.ConfigManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 矿车速度管理器
 *
 * 设计理念：
 * - 只调用 setMaxSpeed() 设置速度上限，不做任何速度修改
 * - 原版 Paper 物理完整运行：上坡自然减速、弯道自然减速到 ~8格/s
 * - setMaxSpeed 控制的是合速度（含垂直分量），所以只要上限设得够高
 *   就不会影响原版上下坡时的惯性表现
 * - 不需要每 tick 干预，避免干扰原版摩擦/重力行为
 */
public class MinecartSpeedManager implements Listener {

    private final XSprint plugin;

    public MinecartSpeedManager(XSprint plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        applySpeedsToExistingMinecarts();
    }

    public void onDisable() {
        clearAllSpeedMods();
    }

    @EventHandler
    public void onMinecartSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Minecart) {
            applySpeedToMinecart((Minecart) event.getEntity());
        }
    }

    private void applySpeedsToExistingMinecarts() {
        plugin.getServer().getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart) {
                    applySpeedToMinecart((Minecart) entity);
                }
            }
        });
    }

    /**
     * 为矿车应用速度配置。
     * 直接调用 setMaxSpeed 设置上限，原版物理完全自由：
     * - 平地上加速到配置的上限
     * - 上坡/弯道时原版摩擦力自然减速到 8格/s
     */
    public void applySpeedToMinecart(Minecart minecart) {
        String type = getMinecartTypeString(minecart.getType());
        ConfigManager.MinecartSpeedConfig config = plugin.getConfigManager().getMinecartConfig(type);
        if (config != null && config.enabled) {
            float maxSpeed = config.getEffectiveMaxSpeed(config.speed);
            minecart.setMaxSpeed(maxSpeed);
        }
    }

    /**
     * 插件关闭时清除所有矿车的速度修改，恢复原版 0.4格/s
     */
    private void clearAllSpeedMods() {
        plugin.getServer().getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart) {
                    ((Minecart) entity).setMaxSpeed(0.4f);
                }
            }
        });
    }

    /**
     * 获取矿车的当前配置最高速度（用于显示）
     */
    public float getMinecartMaxSpeed(Minecart minecart) {
        String type = getMinecartTypeString(minecart.getType());
        ConfigManager.MinecartSpeedConfig config = plugin.getConfigManager().getMinecartConfig(type);
        if (config != null && config.enabled) {
            return config.getEffectiveMaxSpeed(config.speed);
        }
        return 0.4f;
    }

    private String getMinecartTypeString(org.bukkit.entity.EntityType type) {
        String name = type.toString().toLowerCase();
        return name.replace("minecart", "_minecart");
    }
}
