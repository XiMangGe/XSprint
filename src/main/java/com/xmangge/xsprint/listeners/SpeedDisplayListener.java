package com.xmangge.xsprint.listeners;

import com.xmangge.xsprint.XSprint;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpeedDisplayListener implements Listener {

    private final XSprint plugin;
    private final Map<UUID, String> playerDisplayMethods = new HashMap<>();
    private final Map<UUID, Runnable> displayTasks = new HashMap<>();
    private final Map<UUID, org.bukkit.boss.BossBar> playerBossBars = new HashMap<>();
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public SpeedDisplayListener(XSprint plugin) {
        this.plugin = plugin;
    }

    /**
     * 玩家骑乘矿车时启动速度显示
     */
    @EventHandler
    public void onVehicleChange(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        UUID playerId = player.getUniqueId();
        cancelDisplayTask(playerId);

        if (event.getMount() instanceof Minecart) {
            startSpeedDisplay(player, event.getMount());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelDisplayTask(event.getPlayer().getUniqueId());
    }

    private void startSpeedDisplay(Player player, Entity vehicle) {
        if (!plugin.getConfigManager().isSpeedDisplayEnabled()) return;

        UUID playerId = player.getUniqueId();
        String method = playerDisplayMethods.getOrDefault(playerId,
            plugin.getConfigManager().getDisplayMethod());

        switch (method) {
            case "actionbar":
                startActionBarDisplay(player, vehicle);
                break;
            case "title":
                startTitleDisplay(player, vehicle);
                break;
            case "bossbar":
                startBossBarDisplay(player, vehicle);
                break;
            case "scoreboard":
                startScoreboardDisplay(player, vehicle);
                break;
            default:
                startActionBarDisplay(player, vehicle);
        }
    }

    private void startActionBarDisplay(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        cancelDisplayTask(playerId);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getVehicle() != vehicle) {
                    cancel();
                    return;
                }
                Minecart minecart = (Minecart) vehicle;
                double speed = getHorizontalSpeed(minecart);
                String formatted = String.format("%.3f", speed);
                String message = plugin.getLanguageManager().get("display-speed")
                    .replace("{speed}", formatted);
                player.sendActionBar(ChatColor.translateAlternateColorCodes('&', message));
            }
        };
        task.runTaskTimer(plugin, 0L, 5L);
        displayTasks.put(playerId, task);
    }

    private void startTitleDisplay(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        cancelDisplayTask(playerId);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getVehicle() != vehicle) {
                    cancel();
                    return;
                }
                Minecart minecart = (Minecart) vehicle;
                double speed = getHorizontalSpeed(minecart);
                String formatted = String.format("%.3f", speed);

                String title = plugin.getLanguageManager().get("display-speed-title.title");
                String subtitle = plugin.getLanguageManager().get("display-speed-title.subtitle")
                    .replace("{speed}", formatted);

                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    2, 20, 2
                );
            }
        };
        task.runTaskTimer(plugin, 0L, 10L);
        displayTasks.put(playerId, task);
    }

    private void startBossBarDisplay(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        cancelDisplayTask(playerId);

        org.bukkit.boss.BossBar bossBar = Bukkit.createBossBar(
            "§7⚡ §eLoading...",
            org.bukkit.boss.BarColor.YELLOW,
            org.bukkit.boss.BarStyle.SOLID,
            new org.bukkit.boss.BarFlag[0]
        );
        bossBar.addPlayer(player);
        playerBossBars.put(playerId, bossBar);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getVehicle() != vehicle) {
                    bossBar.removeAll();
                    cancel();
                    return;
                }
                Minecart minecart = (Minecart) vehicle;
                double speed = getHorizontalSpeed(minecart);
                String formatted = String.format("%.3f", speed);
                bossBar.setTitle("§7⚡ §e" + formatted + " §7blocks/s");
                bossBar.setProgress((float) Math.min(speed / 75.0, 1.0));
            }
        };
        task.runTaskTimer(plugin, 0L, 5L);
        displayTasks.put(playerId, task);
    }

    private void startScoreboardDisplay(Player player, Entity vehicle) {
        UUID playerId = player.getUniqueId();
        cancelDisplayTask(playerId);

        ScoreboardManager sm = Bukkit.getScoreboardManager();
        Scoreboard sb = sm.getNewScoreboard();
        Objective obj = sb.registerNewObjective("xsprint_speed", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(plugin.getLanguageManager().get("display-scoreboard.title"));

        Score placeholder = obj.getScore("§f");
        placeholder.setScore(1);

        player.setScoreboard(sb);
        playerScoreboards.put(playerId, sb);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getVehicle() != vehicle) {
                    cancel();
                    return;
                }
                Minecart minecart = (Minecart) vehicle;
                double speed = getHorizontalSpeed(minecart);
                String formatted = String.format("%.3f", speed);

                Score speedScore = obj.getScore(formatted + " blocks/s");
                speedScore.setScore(0);
            }
        };
        task.runTaskTimer(plugin, 0L, 5L);
        displayTasks.put(playerId, task);
    }

    private void cancelDisplayTask(UUID playerId) {
        Runnable task = displayTasks.remove(playerId);
        if (task instanceof org.bukkit.scheduler.BukkitTask) {
            ((org.bukkit.scheduler.BukkitTask) task).cancel();
        }

        org.bukkit.boss.BossBar bossBar = playerBossBars.remove(playerId);
        if (bossBar != null) bossBar.removeAll();

        playerScoreboards.remove(playerId);
    }

    private double getHorizontalSpeed(Minecart minecart) {
        Vector v = minecart.getVelocity();
        return Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
    }

    public void setPlayerDisplayMethod(Player player, String method) {
        playerDisplayMethods.put(player.getUniqueId(), method);
    }
}
