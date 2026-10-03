package com.tryfx.pvpbot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class SpawnBotCommand implements CommandExecutor, TabCompleter {

    private static final List<String> DIFFICULTIES = Arrays.asList("EASY", "MEDIUM", "HARD", "IMPOSSIBLE");

    private final TryFXTheBot plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public SpawnBotCommand(TryFXTheBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String prefix = plugin.getConfig().getString("messages.prefix", "");

        if (args.length == 0) {
            player.sendMessage(MM.deserialize(prefix + "<yellow>Usage: /trainingbot <spawn|remove|removeall> [difficulty]"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "spawn" -> {
                String difficulty = args.length > 1 ? args[1].toUpperCase() : "MEDIUM";
                if (!DIFFICULTIES.contains(difficulty)) {
                    player.sendMessage(MM.deserialize(prefix + plugin.getConfig().getString("messages.invalid-difficulty", "")));
                    return true;
                }

                CombatBot bot = plugin.getBotManager().spawnBot(player.getLocation(), difficulty);
                plugin.getBotManager().setBotTarget(bot, player);

                String msg = plugin.getConfig().getString("messages.bot-spawned", "")
                        .replace("%difficulty%", difficulty);
                player.sendMessage(MM.deserialize(prefix + msg));
                return true;
            }
            case "remove" -> {
                BotAI nearest = plugin.getBotManager().getNearestBot(player.getLocation(), 15.0);
                if (nearest == null) {
                    player.sendMessage(MM.deserialize(prefix + plugin.getConfig().getString("messages.bot-not-found", "")));
                    return true;
                }
                plugin.getBotManager().removeBot(nearest.getBot().getNpc().getUniqueId());
                player.sendMessage(MM.deserialize(prefix + plugin.getConfig().getString("messages.bot-removed", "")));
                return true;
            }
            case "removeall" -> {
                boolean removed = plugin.getBotManager().removeAllBots();
                if (removed) {
                    player.sendMessage(MM.deserialize(prefix + "<green>All training bots removed."));
                } else {
                    player.sendMessage(MM.deserialize(prefix + plugin.getConfig().getString("messages.bot-not-found", "")));
                }
                return true;
            }
            default -> {
                player.sendMessage(MM.deserialize(prefix + "<red>Unknown subcommand. Usage: /trainingbot <spawn|remove|removeall> [difficulty]"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "remove", "removeall").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            String partial = args[1].toUpperCase();
            return DIFFICULTIES.stream()
                    .filter(d -> d.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
