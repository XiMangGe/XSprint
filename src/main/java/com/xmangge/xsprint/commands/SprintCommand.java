package com.xmangge.xsprint.commands;

import com.xmangge.xsprint.XSprint;
import com.xmangge.xsprint.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class SprintCommand implements CommandExecutor, TabCompleter {

    private static final List<String> MINECART_TYPES = Arrays.asList(
        "minecart", "chest_minecart", "hopper_minecart",
        "furnace_minecart", "TNT_minecart", "spawner_minecart"
    );

    private static final List<String> DISPLAY_METHODS = Arrays.asList(
        "actionbar", "title", "bossbar", "scoreboard"
    );

    private static final List<String> LANGUAGES = Arrays.asList("zh_cn", "en_us");

    private final XSprint plugin;

    public SprintCommand(XSprint plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 使用 label（命令标签）而非硬编码，支持别名
        String prefix = ChatColor.translateAlternateColorCodes('&',
            plugin.getLanguageManager().get("prefix"));

        if (args.length == 0) {
            sendHelp(sender, prefix);
            return true;
        }

        String subcmd = args[0].toLowerCase();

        switch (subcmd) {
            case "help":
                sendHelp(sender, prefix);
                break;

            case "set":
                if (!sender.hasPermission("xsprint.admin")) {
                    sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-no-permission"));
                    break;
                }
                if (args.length < 3) {
                    sender.sendMessage(prefix + "§c用法: /" + label + " set <矿车类型> <速度>");
                    break;
                }
                try {
                    handleSet(sender, prefix, label, args[1], Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(prefix + "§c速度必须是整数");
                }
                break;

            case "setglobal":
                if (!sender.hasPermission("xsprint.admin")) {
                    sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-no-permission"));
                    break;
                }
                if (args.length < 2) {
                    sender.sendMessage(prefix + "§c用法: /" + label + " setglobal <速度>");
                    break;
                }
                try {
                    handleSetGlobal(sender, prefix, label, Float.parseFloat(args[1]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(prefix + "§c速度必须是数字");
                }
                break;

            case "lang":
                if (args.length < 2) {
                    sender.sendMessage(prefix + "§c用法: /" + label + " lang <语言>");
                    break;
                }
                handleLang(sender, prefix, args[1]);
                break;

            case "display":
                if (args.length < 2) {
                    sender.sendMessage(prefix + "§c用法: /" + label + " display <enable/disable/toggle> [方法]");
                    break;
                }
                handleDisplay(sender, prefix, label, args[1], args.length > 2 ? args[2] : null);
                break;

            case "reload":
                if (!sender.hasPermission("xsprint.admin")) {
                    sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-no-permission"));
                    break;
                }
                plugin.getConfigManager().reload();
                plugin.getLanguageManager().loadLanguage();
                sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-reload-success"));
                break;

            case "myspeed":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (player.getVehicle() instanceof Minecart) {
                        Minecart mc = (Minecart) player.getVehicle();
                        double speed = getHorizontalSpeed(mc);
                        player.sendMessage(prefix + plugin.getLanguageManager().get("display-speed")
                            .replace("{speed}", String.format("%.3f", speed)));
                    } else {
                        player.sendMessage(prefix + "§c你当前没有坐在矿车里");
                    }
                } else {
                    sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-player-only"));
                }
                break;

            default:
                sendHelp(sender, prefix);
                break;
        }

        return true;
    }

    private void handleSet(CommandSender sender, String prefix, String label, String type, int speed) {
        String lowerType = type.toLowerCase();
        ConfigManager.MinecartSpeedConfig config = plugin.getConfigManager().getMinecartConfig(lowerType);

        if (config == null) {
            boolean valid = false;
            for (String mt : MINECART_TYPES) {
                if (mt.equalsIgnoreCase(lowerType)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-set-not-found")
                    .replace("{type}", type));
                return;
            }
            plugin.getConfigManager().setMinecartSpeed(type, speed);
            sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-set-success")
                .replace("{type}", type)
                .replace("{speed}", String.valueOf(speed)));
            return;
        }

        config.speed = speed;
        plugin.getConfigManager().setMinecartSpeed(type, speed);
        sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-set-success")
            .replace("{type}", type)
            .replace("{speed}", String.valueOf(speed)));
    }

    private void handleSetGlobal(CommandSender sender, String prefix, String label, float speed) {
        plugin.getConfigManager().setGlobalSpeedLimit(speed);

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart) {
                    plugin.getSpeedManager().applySpeedToMinecart((Minecart) entity);
                }
            }
        }

        sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-set-global-success")
            .replace("{speed}", String.valueOf(speed)));
    }

    private void handleLang(CommandSender sender, String prefix, String lang) {
        if (!LANGUAGES.contains(lang.toLowerCase())) {
            sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-lang-invalid")
                .replace("{langs}", String.join(", ", LANGUAGES)));
            return;
        }
        plugin.getConfigManager().setLanguage(lang.toLowerCase());
        sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-lang-success")
            .replace("{lang}", lang));
    }

    private void handleDisplay(CommandSender sender, String prefix, String label, String action, String method) {
        boolean enabled = action.equalsIgnoreCase("enable");
        boolean disabled = action.equalsIgnoreCase("disable");
        boolean toggle = action.equalsIgnoreCase("toggle");

        if (disabled || (toggle && plugin.getConfigManager().isSpeedDisplayEnabled())) {
            plugin.getConfigManager().setDisplayEnabled(false);
            sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-display-disabled"));
        } else if (enabled || toggle) {
            plugin.getConfigManager().setDisplayEnabled(true);
            if (method != null && DISPLAY_METHODS.contains(method.toLowerCase())) {
                plugin.getConfigManager().setDisplayMethod(method.toLowerCase());
                sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-display-success")
                    .replace("{action}", "启用")
                    .replace("{method}", method));
            } else {
                sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-display-success")
                    .replace("{action}", "启用")
                    .replace("{method}", plugin.getConfigManager().getDisplayMethod()));
            }
        } else {
            sender.sendMessage(prefix + "§c用法: /" + label + " display <enable/disable/toggle> [方法]");
        }
    }

    private void sendHelp(CommandSender sender, String prefix) {
        sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-help.header"));
        for (String line : plugin.getLanguageManager().getList("cmd-help.entries")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
        sender.sendMessage(prefix + plugin.getLanguageManager().get("cmd-help.footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("help", "set", "setglobal", "lang", "display", "reload", "myspeed");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (String t : MINECART_TYPES) {
                if (t.startsWith(args[1].toLowerCase())) completions.add(t);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lang")) {
            for (String l : LANGUAGES) {
                if (l.startsWith(args[1].toLowerCase())) completions.add(l);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("display")) {
            for (String d : Arrays.asList("enable", "disable", "toggle")) {
                if (d.startsWith(args[1].toLowerCase())) completions.add(d);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("display")) {
            for (String m : DISPLAY_METHODS) {
                if (m.startsWith(args[2].toLowerCase())) completions.add(m);
            }
        }
        return completions;
    }

    private double getHorizontalSpeed(Minecart minecart) {
        org.bukkit.util.Vector v = minecart.getVelocity();
        return Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
    }
}
