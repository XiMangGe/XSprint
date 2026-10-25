package com.xmangge.xsprint.speed;

import com.xmangge.xsprint.XSprint;
import com.xmangge.xsprint.config.ConfigManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * 矿车速度管理器
 *
 * 设计思路：
 * - setMaxSpeed 设为极高值（999），让原版物理完全自由运行，坡道/弯道速度不受限
 * - 只在水平方向做钳制：每 tick 检查水平速度，超过上限时按比例衰减
 * - 垂直速度（y轴）完全不受影响，保证上下坡时能冲上斜坡
 * - 拐弯时的离心力也不会被干预，防止因速度被突然拉低导致脱轨
 */
public class MinecartSpeedManager implements Listener {

    private final XSprint plugin;
    // uid -> 配置的最高速度（水平方向）
    private final Map<String, Float> minecartMaxSpeeds = new HashMap<>();

    private BukkitRunnable speedUpdater;

    public MinecartSpeedManager(XSprint plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        applySpeedsToExistingMinecarts();
        startSpeedUpdater();
    }

    public void onDisable() {
        if (speedUpdater != null) {
            speedUpdater.cancel();
        }
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
     * setMaxSpeed 设为极大值，原版物理完全自由（坡道/弯道不限速）。
     * 实际上限由 clampHorizontalVelocity 在后台逐 tick 钳制水平分量。
     */
    public void applySpeedToMinecart(Minecart minecart) {
        String type = getMinecartTypeString(minecart.getType());
        ConfigManager.MinecartSpeedConfig config = plugin.getConfigManager().getMinecartConfig(type);
        if (config != null && config.enabled) {
            float maxSpeed = config.getEffectiveMaxSpeed(config.speed);
            minecartMaxSpeeds.put(minecart.getUniqueId().toString(), maxSpeed);
            // 极大值：让原版物理完全自由，坡道和弯道速度不受限
            minecart.setMaxSpeed(999.0f);
        }
    }

    /**
     * 每 tick 执行一次，只对水平分量做平滑钳制。
     * 垂直分量（y轴）完全保留，确保上下坡时有足够惯性冲上坡。
     */
    private void startSpeedUpdater() {
        speedUpdater = new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity entity : plugin.getServer().getOnlinePlayers()) {
                    if (entity.getVehicle() instanceof Minecart) {
                        clampHorizontalVelocity((Minecart) entity.getVehicle());
                    }
                }
            }
        };
        // 每 tick 执行，保证响应及时但不会太激进
        speedUpdater.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 平滑钳制水平速度：
     * - 仅修改 XZ 平面分量，Y 轴（垂直）完全保留
     * - 超过上限时每 tick 衰减 5%，避免瞬间跳变
     * - 坡道/弯道时原版物理自由计算，不会被干预
     */
    private void clampHorizontalVelocity(Minecart minecart) {
        String uid = minecart.getUniqueId().toString();
        Float maxSpeed = minecartMaxSpeeds.get(uid);
        if (maxSpeed == null) {
            applySpeedToMinecart(minecart);
            maxSpeed = minecartMaxSpeeds.get(uid);
        }
        if (maxSpeed == null || maxSpeed <= 0) return;

        Vector v = minecart.getVelocity();
        double dx = v.getX();
        double dz = v.getZ();
        double dy = v.getY(); // 垂直分量，永不修改
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        if (horizontalSpeed > maxSpeed) {
            // 每 tick 衰减 5%，逐渐回归上限，不造成瞬间卡顿感
            double ratio = 1.0 - 0.05;
            minecart.setVelocity(new Vector(dx * ratio, dy, dz * ratio));
        }
        // 未超限时完全不做任何修改，让原版物理自由运行
    }

    /**
     * 插件关闭时清除所有矿车的速度修改，恢复原版行为
     */
    private void clearAllSpeedMods() {
        minecartMaxSpeeds.clear();
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
