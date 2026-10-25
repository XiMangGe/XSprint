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
        Entity entity = event.getEntity();
        if (entity instanceof Minecart) {
            applySpeedToMinecart((Minecart) entity);
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

    public void applySpeedToMinecart(Minecart minecart) {
        String type = getMinecartTypeString(minecart.getType());
        ConfigManager.MinecartSpeedConfig config = plugin.getConfigManager().getMinecartConfig(type);
        if (config != null && config.enabled) {
            float maxSpeed = config.getEffectiveMaxSpeed(config.speed);
            minecart.setMaxSpeed(maxSpeed);
            minecartMaxSpeeds.put(minecart.getUniqueId().toString(), maxSpeed);
        }
    }

    private void startSpeedUpdater() {
        speedUpdater = new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity entity : plugin.getServer().getOnlinePlayers()) {
                    if (entity.getVehicle() instanceof Minecart) {
                        clampVelocity((Minecart) entity.getVehicle());
                    }
                }
            }
        };
        speedUpdater.runTaskTimer(plugin, 0L, 5L);
    }

    private void clampVelocity(Minecart minecart) {
        String uid = minecart.getUniqueId().toString();
        Float maxSpeed = minecartMaxSpeeds.get(uid);
        if (maxSpeed == null) {
            applySpeedToMinecart(minecart);
            maxSpeed = minecartMaxSpeeds.get(uid);
        }
        if (maxSpeed == null || maxSpeed <= 0) return;

        double dx = minecart.getVelocity().getX();
        double dz = minecart.getVelocity().getZ();
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        if (horizontalSpeed > maxSpeed) {
            double ratio = maxSpeed / horizontalSpeed;
            minecart.setVelocity(new Vector(dx * ratio, minecart.getVelocity().getY(), dz * ratio));
        }
    }

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
