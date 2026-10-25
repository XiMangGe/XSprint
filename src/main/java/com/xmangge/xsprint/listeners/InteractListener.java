package com.xmangge.xsprint.listeners;

import com.xmangge.xsprint.XSprint;
import com.xmangge.xsprint.gui.SpeedConfigGUI;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InteractListener implements Listener {

    private final XSprint plugin;
    private final SpeedConfigGUI gui;

    // 追踪玩家的交互序列状态
    // key: UUID, value: 当前序列阶段 (0=未开始, 1=已潜行, 2=已潜行+左键)
    private final Map<UUID, Integer> clickStage = new HashMap<>();
    private final Map<UUID, Long> lastActionTime = new HashMap<>();

    public InteractListener(XSprint plugin) {
        this.plugin = plugin;
        this.gui = new SpeedConfigGUI(plugin);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.isSneaking()) {
            clickStage.put(playerId, 1);
            lastActionTime.put(playerId, System.currentTimeMillis());
        } else {
            clickStage.remove(playerId);
            lastActionTime.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (lastActionTime.containsKey(playerId) &&
            System.currentTimeMillis() - lastActionTime.get(playerId) > 2000) {
            clickStage.remove(playerId);
            lastActionTime.remove(playerId);
            return;
        }

        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR ||
            event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            Integer stage = clickStage.get(playerId);
            if (stage != null && stage == 1 && player.isSneaking()) {
                clickStage.put(playerId, 2);
                lastActionTime.put(playerId, System.currentTimeMillis());
                return;
            }
            clickStage.remove(playerId);
            lastActionTime.remove(playerId);
        }

        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            Integer stage = clickStage.get(playerId);
            if (stage != null && stage == 2 && player.isSneaking()) {
                clickStage.remove(playerId);
                lastActionTime.remove(playerId);
                checkNearbyMinecart(player);
            } else {
                clickStage.remove(playerId);
                lastActionTime.remove(playerId);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        UUID playerId = player.getUniqueId();

        if (lastActionTime.containsKey(playerId) &&
            System.currentTimeMillis() - lastActionTime.get(playerId) > 2000) {
            clickStage.remove(playerId);
            lastActionTime.remove(playerId);
        }

        if (!(entity instanceof Minecart)) return;

        Minecart minecart = (Minecart) entity;

        if (event.getRightClicked() instanceof Minecart) {
            Integer stage = clickStage.get(playerId);
            if (player.isSneaking()) {
                if (stage != null && stage == 1) {
                    // 潜行+左键点击矿车 → 阶段2
                    clickStage.put(playerId, 2);
                    lastActionTime.put(playerId, System.currentTimeMillis());
                    return;
                } else if (stage != null && stage == 2) {
                    // 潜行+左键+右键点击矿车 → 完成！
                    event.setCancelled(true);
                    player.sendMessage(plugin.getLanguageManager().get("interact-success"));
                    gui.openGUI(player, minecart);
                    clickStage.remove(playerId);
                    lastActionTime.remove(playerId);
                    return;
                }
            }
            clickStage.remove(playerId);
            lastActionTime.remove(playerId);
        }
    }

    private void checkNearbyMinecart(Player player) {
        for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof Minecart) {
                gui.openGUI(player, (Minecart) entity);
                return;
            }
        }
        player.sendMessage(plugin.getLanguageManager().get("interact-prompt"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        gui.onInventoryClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        gui.onInventoryClose(event);
    }
}
