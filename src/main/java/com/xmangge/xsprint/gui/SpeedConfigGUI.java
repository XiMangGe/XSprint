package com.xmangge.xsprint.gui;

import com.xmangge.xsprint.XSprint;
import com.xmangge.xsprint.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpeedConfigGUI {

    private final XSprint plugin;
    private static final int DIAMOND_PER_CLICK = 5;
    private static final int REDSTONE_PER_CLICK = 1;

    private final Map<UUID, Minecart> playerMinecart = new HashMap<>();
    private final Map<UUID, Integer> playerPage = new HashMap<>();

    public SpeedConfigGUI(XSprint plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player player, Minecart minecart) {
        playerMinecart.put(player.getUniqueId(), minecart);
        playerPage.put(player.getUniqueId(), 0);
        showPage(player, minecart, 0);
    }

    public void showPage(Player player, Minecart minecart, int page) {
        ConfigManager.MinecartSpeedConfig config = getConfigForMinecart(minecart);
        if (config == null || !config.enabled) {
            player.sendMessage("§c该矿车类型未启用速度修改");
            return;
        }

        int currentSpeed = config.speed;
        Inventory inv;
        String title;

        if (page == 0) {
            title = plugin.getLanguageManager().get("gui-page1-title");
            inv = Bukkit.createInventory(null, 27, title);
            buildDiamondPage(inv, currentSpeed, config);
        } else {
            title = plugin.getLanguageManager().get("gui-page2-title");
            inv = Bukkit.createInventory(null, 27, title);
            buildRedstonePage(inv, currentSpeed, config);
        }

        player.openInventory(inv);
    }

    private void buildDiamondPage(Inventory inv, int currentSpeed, ConfigManager.MinecartSpeedConfig config) {
        int maxSpeed = (int) config.getEffectiveMaxSpeed(999);
        int diamondCount = Math.min(currentSpeed / DIAMOND_PER_CLICK + (currentSpeed % DIAMOND_PER_CLICK == 0 ? 0 : 1), maxSpeed / DIAMOND_PER_CLICK);

        int slot = 0;
        for (int i = 0; i < diamondCount && slot < 25; i++) {
            ItemStack diamond = new ItemStack(Material.DIAMOND);
            ItemMeta meta = diamond.getItemMeta();
            meta.setDisplayName("§b§l钻石 §7(+" + DIAMOND_PER_CLICK + "格/s)");
            meta.setLore(Arrays.asList(
                "§7当前速度: §e" + ((i + 1) * DIAMOND_PER_CLICK) + " 格/秒",
                "§7右键点击增加 " + DIAMOND_PER_CLICK + " 格/秒",
                "§7最大速度: §e" + maxSpeed + " 格/秒"
            ));
            diamond.setItemMeta(meta);
            inv.setItem(slot++, diamond);
        }

        ItemStack nextPaper = new ItemStack(Material.PAPER);
        ItemMeta nextMeta = nextPaper.getItemMeta();
        nextMeta.setDisplayName("§e§l下一页 ›");
        nextMeta.setLore(Arrays.asList("§7切换到红石页面"));
        nextPaper.setItemMeta(nextMeta);
        inv.setItem(26, nextPaper);

        ItemStack prevBook = new ItemStack(Material.BOOK);
        ItemMeta prevMeta = prevBook.getItemMeta();
        prevMeta.setDisplayName("§e§l‹ 上一页");
        prevBook.setItemMeta(prevMeta);
        inv.setItem(18, prevBook);

        ItemStack glass = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§f");
        glass.setItemMeta(glassMeta);
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, glass);
        }
    }

    private void buildRedstonePage(Inventory inv, int currentSpeed, ConfigManager.MinecartSpeedConfig config) {
        int maxSpeed = (int) config.getEffectiveMaxSpeed(999);
        int redstoneCount = Math.min(currentSpeed, maxSpeed);

        int slot = 0;
        for (int i = 0; i < redstoneCount && slot < 25; i++) {
            ItemStack redstone = new ItemStack(Material.REDSTONE);
            ItemMeta meta = redstone.getItemMeta();
            meta.setDisplayName("§c§l红石粉 §7(+" + REDSTONE_PER_CLICK + "格/s)");
            meta.setLore(Arrays.asList(
                "§7当前速度: §e" + (i + 1) + " 格/秒",
                "§7右键点击增加 " + REDSTONE_PER_CLICK + " 格/秒",
                "§7最大速度: §e" + maxSpeed + " 格/秒"
            ));
            redstone.setItemMeta(meta);
            inv.setItem(slot++, redstone);
        }

        ItemStack prevPaper = new ItemStack(Material.PAPER);
        ItemMeta prevMeta = prevPaper.getItemMeta();
        prevMeta.setDisplayName("§e§l‹ 上一页");
        prevMeta.setLore(Arrays.asList("§7切换回钻石页面"));
        prevPaper.setItemMeta(prevMeta);
        inv.setItem(26, prevPaper);

        ItemStack nextBook = new ItemStack(Material.BOOK);
        ItemMeta nextMeta = nextBook.getItemMeta();
        nextMeta.setDisplayName("§e§l下一页 ›");
        nextBook.setItemMeta(nextMeta);
        inv.setItem(18, nextBook);

        ItemStack glass = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§f");
        glass.setItemMeta(glassMeta);
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, glass);
        }
    }

    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        Minecart minecart = playerMinecart.get(playerId);
        if (minecart == null) return;

        ConfigManager.MinecartSpeedConfig config = getConfigForMinecart(minecart);
        if (config == null || !config.enabled) return;

        int currentSpeed = config.speed;
        int maxSpeed = (int) config.getEffectiveMaxSpeed(999);
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        ClickType clickType = event.getClick();

        event.setCancelled(true);

        if (clicked == null || clicked.getType() == Material.AIR) return;

        int currentPage = playerPage.getOrDefault(playerId, 0);

        if (slot == 26) {
            int nextPage = (currentPage == 0) ? 1 : 0;
            playerPage.put(playerId, nextPage);
            showPage(player, minecart, nextPage);
            return;
        }

        if (slot == 18) {
            int prevPage = (currentPage == 0) ? 1 : 0;
            playerPage.put(playerId, prevPage);
            showPage(player, minecart, prevPage);
            return;
        }

        if (currentPage == 0 && clicked.getType() == Material.DIAMOND && slot >= 0 && slot <= 24) {
            if (clickType.isRightClick() && currentSpeed + DIAMOND_PER_CLICK <= maxSpeed) {
                config.speed += DIAMOND_PER_CLICK;
                plugin.getConfigManager().setMinecartSpeed(getMinecartType(minecart), config.speed);
                applySpeedToMinecart(minecart, config);
                player.sendMessage(plugin.getLanguageManager().get("cmd-set-success")
                    .replace("{type}", minecart.getType().toString())
                    .replace("{speed}", String.valueOf(config.speed)));
                showPage(player, minecart, 0);
            } else if (clickType.isLeftClick() && currentSpeed >= DIAMOND_PER_CLICK) {
                config.speed -= DIAMOND_PER_CLICK;
                plugin.getConfigManager().setMinecartSpeed(getMinecartType(minecart), config.speed);
                applySpeedToMinecart(minecart, config);
                showPage(player, minecart, 0);
            }
            return;
        }

        if (currentPage == 1 && clicked.getType() == Material.REDSTONE && slot >= 0 && slot <= 24) {
            if (clickType.isRightClick() && currentSpeed + REDSTONE_PER_CLICK <= maxSpeed) {
                config.speed += REDSTONE_PER_CLICK;
                plugin.getConfigManager().setMinecartSpeed(getMinecartType(minecart), config.speed);
                applySpeedToMinecart(minecart, config);
                player.sendMessage(plugin.getLanguageManager().get("cmd-set-success")
                    .replace("{type}", minecart.getType().toString())
                    .replace("{speed}", String.valueOf(config.speed)));
                showPage(player, minecart, 1);
            } else if (clickType.isLeftClick() && currentSpeed >= REDSTONE_PER_CLICK) {
                config.speed -= REDSTONE_PER_CLICK;
                plugin.getConfigManager().setMinecartSpeed(getMinecartType(minecart), config.speed);
                applySpeedToMinecart(minecart, config);
                showPage(player, minecart, 1);
            }
            return;
        }
    }

    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            UUID playerId = ((Player) event.getPlayer()).getUniqueId();
            playerMinecart.remove(playerId);
            playerPage.remove(playerId);
        }
    }

    private void applySpeedToMinecart(Minecart minecart, ConfigManager.MinecartSpeedConfig config) {
        float maxSpeed = config.getEffectiveMaxSpeed(config.speed);
        minecart.setMaxSpeed(maxSpeed);
    }

    private ConfigManager.MinecartSpeedConfig getConfigForMinecart(Minecart minecart) {
        return plugin.getConfigManager().getMinecartConfig(getMinecartType(minecart));
    }

    private String getMinecartType(Minecart minecart) {
        return minecart.getType().toString().toLowerCase().replace("minecart", "_minecart");
    }
}
