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

public class MinecartSpeedManager implements Listener {

    private final XSprint plugin;
    // uid -> 配置的最高速度
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
     * 将 setMaxSpeed 设为极大值，让原版物理自由加速；
     * 实际上限由 smoothLimitVelocity 在后台逐 tick 平滑钳制。
     */
    public void applySpeedToMinecart(Minecart minecart) {
        String type = getMinecartTypeString(minecart.getType());
        ConfigManager.MinecartSpeedConfig config = plugin.getConfigManager().getMinecartConfig(type);
        if (config != null && config.enabled) {
            float maxSpeed = config.getEffectiveMaxSpeed(config.speed);
            minecartMaxSpeeds.put(minecart.getUniqueId().toString(), maxSpeed);
            minecart.setMaxSpeed(999.0f);
        }
    }

    private void startSpeedUpdater() {
        speedUpdater = new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity entity : plugin.getServer().getOnlinePlayers()) {
                    if (entity.getVehicle() instanceof Minecart) {
                        smoothLimitVelocity((Minecart) entity.getVehicle());
                    }
                }
            }
        };
        // 每 5 tick（0.25s）检查一次，保证加速过程平滑
        speedUpdater.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * 平滑速度限制：
     * - 当前速度未超限时：不干预，让原版物理自然加速/减速
     * - 当前速度超限时：每 5 tick 最多衰减 10%，避免瞬间跳变
     */
    private void smoothLimitVelocity(Minecart minecart) {
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
        double currentSpeed = Math.sqrt(dx * dx + dz * dz);

        if (currentSpeed > maxSpeed) {
            // 每 5 tick 最多降 10%，模拟原版的减速度感
            double ratio = (currentSpeed - maxSpeed * 0.10) / currentSpeed;
            minecart.setVelocity(new Vector(dx * ratio, v.getY(), dz * ratio));
        }
    }

    private void clearAllSpeedMods() {
        minecartMaxSpeeds.clear();
        plugin.getServer().getWorlds().forEach(world -> {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart) {
                    ((Minecart) entity).setMaxSpeed(999.0f);
                }
            }
        });
    }

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
