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
    // GUI 最多能显示的格子数（不含翻页按钮）
    private static final int MAX_VISIBLE_ITEMS = 25;

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
        // 实际能显示的钻石数（不超过25个格子）
        int displayCount = Math.min(diamondCount, MAX_VISIBLE_ITEMS);

        int slot = 0;
        for (int i = 0; i < displayCount && slot < 25; i++) {
            int speedValue = (i + 1) * DIAMOND_PER_CLICK;
            boolean isLastVisible = (i == diamondCount - 1);
            ItemStack diamond = new ItemStack(Material.DIAMOND);
            ItemMeta meta = diamond.getItemMeta();
            meta.setDisplayName("§b§l钻石 §7(+" + DIAMOND_PER_CLICK + "格/s)");
            meta.setLore(Arrays.asList(
                "§7代表速度: §e" + speedValue + " 格/秒",
                "§7右键: +" + DIAMOND_PER_CLICK + "  左键: -" + DIAMOND_PER_CLICK,
                isLastVisible ? "§a§l当前已达配置速度" : null,
                "§7最高可达: §e" + maxSpeed + " 格/秒"
            ).stream().filter(s -> s != null).collect(java.util.stream.Collectors.toList()));
            diamond.setItemMeta(meta);
            inv.setItem(slot++, diamond);
        }

        // 中间显示当前速度的信息牌
        ItemStack infoItem = new ItemStack(Material.NAME_TAG);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§l⚡ 当前速度: §f" + currentSpeed + " §e格/秒");
        infoMeta.setLore(Arrays.asList(
            "§7上限: §e" + maxSpeed + " 格/秒",
            "§7右键钻石: +" + DIAMOND_PER_CLICK,
            "§7左键钻石: -" + DIAMOND_PER_CLICK
        ));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(13, infoItem);

        // 底部填玻璃板装饰
        ItemStack glass = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§f");
        glass.setItemMeta(glassMeta);
        for (int i = 18; i <= 25; i++) {
            if (i != 26) inv.setItem(i, glass);
        }
        // 翻页按钮
        ItemStack nextPaper = new ItemStack(Material.PAPER);
        ItemMeta nextMeta = nextPaper.getItemMeta();
        nextMeta.setDisplayName("§e§l下一页 › 红石");
        nextMeta.setLore(Arrays.asList("§7每红石粉 +1 格/秒"));
        nextPaper.setItemMeta(nextMeta);
        inv.setItem(26, nextPaper);

        ItemStack prevBook = new ItemStack(Material.BOOK);
        ItemMeta prevMeta = prevBook.getItemMeta();
        prevMeta.setDisplayName("§e§l‹ 上一页");
        prevBook.setItemMeta(prevMeta);
        inv.setItem(18, prevBook);
    }

    private void buildRedstonePage(Inventory inv, int currentSpeed, ConfigManager.MinecartSpeedConfig config) {
        int maxSpeed = (int) config.getEffectiveMaxSpeed(999);
        // 红石最多显示25个，剩余用文字牌显示进度
        int redstoneCount = Math.min(Math.min(currentSpeed, maxSpeed), MAX_VISIBLE_ITEMS);

        int slot = 0;
        for (int i = 0; i < redstoneCount && slot < 25; i++) {
            ItemStack redstone = new ItemStack(Material.REDSTONE);
            ItemMeta meta = redstone.getItemMeta();
            meta.setDisplayName("§c§l红石粉 §7(+" + REDSTONE_PER_CLICK + "格/s)");
            meta.setLore(Arrays.asList(
                "§7当前速度: §e" + (i + 1) + " 格/秒",
                "§7右键: +" + REDSTONE_PER_CLICK + "  左键: -" + REDSTONE_PER_CLICK,
                "§7最高可达: §e" + maxSpeed + " 格/秒"
            ));
            redstone.setItemMeta(meta);
            inv.setItem(slot++, redstone);
        }

        // 如果还有更多速度没显示完，用文字牌表示
        if (currentSpeed > MAX_VISIBLE_ITEMS) {
            int remaining = currentSpeed - MAX_VISIBLE_ITEMS;
            ItemStack infoItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.setDisplayName("§e§l📖 更多速度...");
            infoMeta.setLore(Arrays.asList(
                "§7已显示: §e" + MAX_VISIBLE_ITEMS + " 格/秒（前25红石）",
                "§7剩余: §e" + remaining + " 格/秒",
                "§7总计: §e" + currentSpeed + " 格/秒",
                "§7右击增加 1 格/秒  |  左击减少 1 格/秒"
            ));
            infoItem.setItemMeta(infoMeta);
            inv.setItem(13, infoItem);
        }

        // 翻页按钮
        ItemStack prevPaper = new ItemStack(Material.PAPER);
        ItemMeta prevMeta = prevPaper.getItemMeta();
        prevMeta.setDisplayName("§e§l‹ 上一页 钻石");
        prevMeta.setLore(Arrays.asList("§7每钻石 +5 格/秒（大跨度）"));
        prevPaper.setItemMeta(prevMeta);
        inv.setItem(26, prevPaper);

        // 底部填玻璃板装饰
        ItemStack glass = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§f");
        glass.setItemMeta(glassMeta);
        for (int i = 18; i <= 25; i++) {
            if (i != 26) inv.setItem(i, glass);
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

        // 翻页按钮（slot 18 = 上一页, slot 26 = 下一页）
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

        // 钻石页面（第一页）
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

        // 红石页面（第二页）- 点击红石或信息牌
        if (currentPage == 1) {
            if (clicked.getType() == Material.REDSTONE && slot >= 0 && slot <= 24) {
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
            // 点击信息牌（名称标签 / 可写书）进行 ±1 调整
            if ((clicked.getType() == Material.NAME_TAG || clicked.getType() == Material.WRITABLE_BOOK)
                    && slot == 13) {
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
