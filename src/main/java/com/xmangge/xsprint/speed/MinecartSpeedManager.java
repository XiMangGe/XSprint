package com.xmangge.xsprint.speed;

import com.xmangge.xsprint.XSprint;
import com.xmangge.xsprint.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 矿车速度管理器
 *
 * 速度规则：
 * - 平地直道：只设置 setMaxSpeed()，原版物理自然平滑加速到配置上限
 *   （加速速率与原版一致，不会瞬间拉满）
 * - 上坡/下坡/拐弯：强制 8格/s（原版最高速度 0.4格/tick），防止脱轨
 * - 上坡时速度一旦低于 8格/s 自动助推，保证不会在坡顶停死
 */
public class MinecartSpeedManager implements Listener {

    /** 坡道/弯道固定速度：8格/s = 0.4格/tick（原版矿车最高速度） */
    private static final double SLOPE_SPEED = 0.4;

    private final XSprint plugin;
    private BukkitRunnable stallTask;

    public MinecartSpeedManager(XSprint plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        applySpeedsToExistingMinecarts();

        // 每 10 tick 扫描一次，救回完全停死（不再触发移动事件）的上坡矿车
        stallTask = new BukkitRunnable() {
            @Override
            public void run() {
                unstallSlopeMinecarts();
            }
        };
        stallTask.runTaskTimer(plugin, 20L, 10L);
    }

    public void onDisable() {
        if (stallTask != null) {
            stallTask.cancel();
            stallTask = null;
        }
        clearAllSpeedMods();
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

    @EventHandler
    public void onMinecartSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Minecart) {
            applySpeedToMinecart((Minecart) event.getEntity());
        }
    }

    /**
     * 矿车移动时检查：当前脚下或前方（一个 tick 移动距离内）是否有坡道/弯道。
     * 有 → 水平速度固定为原版 8格/s（上限不动，避免被原版钳死）；
     * 上坡速度不足 8格/s 时自动补速，坡顶不会停死。
     */
    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart)) return;
        Minecart minecart = (Minecart) event.getVehicle();

        ConfigManager.MinecartSpeedConfig config = getConfigFor(minecart);
        if (config == null || !config.enabled) return;

        // 上限始终保持配置值：压低上限会被原版钳制，反而让坡上的矿车停死
        float normalMax = config.getEffectiveMaxSpeed(config.speed);
        if (minecart.getMaxSpeed() != normalMax) {
            minecart.setMaxSpeed(normalMax);
        }

        // 坡道/弯道（含提前一格的预判）：水平速度固定回原版 8格/s
        if (findSlopeOrCurveAhead(minecart) == null) return;

        double h = horizontalSpeed(minecart);
        if (h > SLOPE_SPEED) {
            clampHorizontal(minecart, h);
        } else if (h < SLOPE_SPEED && isAscendingAt(minecart.getLocation())) {
            // 上坡速度不足 8格/s → 沿行进方向补到 8格/s，坡顶不会停死
            boostAlong(minecart);
        }
    }

    /** 只压缩水平速度，保留垂直分量，否则上坡会被打断 */
    private void clampHorizontal(Minecart cart, double h) {
        Vector v = cart.getVelocity();
        double scale = SLOPE_SPEED / h;
        cart.setVelocity(new Vector(v.getX() * scale, v.getY(), v.getZ() * scale));
    }

    /**
     * 为矿车应用配置的速度上限（生成时/命令修改后调用）
     */
    public void applySpeedToMinecart(Minecart minecart) {
        ConfigManager.MinecartSpeedConfig config = getConfigFor(minecart);
        if (config != null && config.enabled) {
            minecart.setMaxSpeed(config.getEffectiveMaxSpeed(config.speed));
        }
    }

    /**
     * 获取矿车的配置最高速度（用于显示）
     */
    public float getMinecartMaxSpeed(Minecart minecart) {
        ConfigManager.MinecartSpeedConfig config = getConfigFor(minecart);
        if (config != null && config.enabled) {
            return config.getEffectiveMaxSpeed(config.speed);
        }
        return 0.4f;
    }

    /**
     * 扫描所有已加载矿车，把卡死在上坡（速度为0不再触发移动事件）的矿车推起来。
     * 完全静止、没人坐的空车不推（可能是故意停着的）。
     */
    private void unstallSlopeMinecarts() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Minecart)) continue;
                Minecart cart = (Minecart) entity;

                ConfigManager.MinecartSpeedConfig config = getConfigFor(cart);
                if (config == null || !config.enabled) continue;

                Location loc = cart.getLocation();
                Rail.Shape shape = railShapeAt(loc, 0);
                if (shape == null) shape = railShapeAt(loc, -1);
                if (shape == null || !shape.name().startsWith("ASCENDING")) continue;

                double h = horizontalSpeed(cart);
                if (h >= SLOPE_SPEED) continue;
                if (h < 0.01 && cart.getPassengers().isEmpty()) continue;

                boostAlong(cart);
            }
        }
    }

    /**
     * 沿矿车当前行进方向助推到 8格/s；
     * 完全停死（无速度）时按铁轨坡向朝上坡方向推。
     */
    private void boostAlong(Minecart cart) {
        Vector v = cart.getVelocity();
        double h = Math.hypot(v.getX(), v.getZ());
        double dx, dz;

        if (h > 0.01) {
            dx = v.getX() / h;
            dz = v.getZ() / h;
        } else {
            Location loc = cart.getLocation();
            Rail.Shape shape = railShapeAt(loc, 0);
            if (shape == null) shape = railShapeAt(loc, -1);
            if (shape == null) return;
            Vector dir = uphillDirection(shape);
            if (dir == null) return;
            dx = dir.getX();
            dz = dir.getZ();
        }

        cart.setVelocity(new Vector(dx * SLOPE_SPEED, v.getY(), dz * SLOPE_SPEED));
    }

    /**
     * 检查矿车脚下及前方（一个 tick 移动距离内）是否有坡道或弯道。
     * 高速矿车每 tick 移动多格，必须提前检测，否则会整格跳过弯道导致脱轨。
     */
    private Rail.Shape findSlopeOrCurveAhead(Minecart cart) {
        Location loc = cart.getLocation();

        Rail.Shape current = railShapeAt(loc, 0);
        if (current == null) current = railShapeAt(loc, -1);
        if (isDanger(current)) return current;

        Vector v = cart.getVelocity();
        double h = Math.hypot(v.getX(), v.getZ());
        if (h < 0.01) return null;

        double dx = v.getX() / h;
        double dz = v.getZ() / h;
        int steps = (int) Math.ceil(h) + 1;
        if (steps > 8) steps = 8;

        for (int k = 1; k <= steps; k++) {
            Location sample = loc.clone().add(dx * k, 0, dz * k);
            Rail.Shape s = railShapeAt(sample, 0);
            if (s == null) s = railShapeAt(sample, -1);
            if (isDanger(s)) return s;
        }
        return null;
    }

    /** 坡道（上/下坡）与弯道都算需要限速的地形；直线只排除南北/东西 */
    private boolean isDanger(Rail.Shape shape) {
        return shape != null
                && shape != Rail.Shape.NORTH_SOUTH
                && shape != Rail.Shape.EAST_WEST;
    }

    private boolean isAscendingAt(Location loc) {
        Rail.Shape shape = railShapeAt(loc, 0);
        if (shape == null) shape = railShapeAt(loc, -1);
        return shape != null && shape.name().startsWith("ASCENDING");
    }

    /** 读取指定位置（含向下偏移一格）的铁轨形状 */
    private Rail.Shape railShapeAt(Location loc, int yOffset) {
        Block block = loc.getWorld().getBlockAt(
                loc.getBlockX(), loc.getBlockY() + yOffset, loc.getBlockZ());
        if (block.getBlockData() instanceof Rail) {
            return ((Rail) block.getBlockData()).getShape();
        }
        return null;
    }

    /** 铁轨坡向 → 上坡方向（北=-Z，南=+Z，东=+X，西=-X） */
    private Vector uphillDirection(Rail.Shape shape) {
        switch (shape) {
            case ASCENDING_NORTH: return new Vector(0, 0, -1);
            case ASCENDING_SOUTH: return new Vector(0, 0, 1);
            case ASCENDING_EAST:  return new Vector(1, 0, 0);
            case ASCENDING_WEST:  return new Vector(-1, 0, 0);
            default: return null;
        }
    }

    private double horizontalSpeed(Minecart minecart) {
        Vector v = minecart.getVelocity();
        return Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
    }

    /**
     * 矿车类型 → 配置键（minecart / chest_minecart / furnace_minecart / tnt_minecart ...）
     * 命令矿车（command minecart）不处理，返回 null
     */
    private ConfigManager.MinecartSpeedConfig getConfigFor(Minecart minecart) {
        String name = minecart.getType().name();
        if (name.contains("COMMAND")) return null;
        return plugin.getConfigManager().getMinecartConfig(name.toLowerCase());
    }

    /**
     * 插件关闭时清除所有矿车的速度修改，恢复原版 0.4
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
}
