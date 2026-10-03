package com.tryfx.pvpbot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Toggles between the training arena and the player's previous location.
 */
public final class TrainingCommand implements CommandExecutor {

    private final TryFXTheBot plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Tracks where to send a player back to when they leave the arena
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public TrainingCommand(TryFXTheBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String prefix = plugin.getConfig().getString("messages.prefix", "");
        var trainingWorld = plugin.getTrainingWorldManager().getTrainingWorld();

        if (trainingWorld == null) {
            player.sendMessage(MM.deserialize(prefix + "<red>Training world is not available."));
            return true;
        }

        if (player.getWorld().equals(trainingWorld)) {
            // Leaving the arena
            Location returnLoc = returnLocations.remove(player.getUniqueId());
            if (returnLoc != null) {
                player.teleport(returnLoc);
            } else {
                player.teleport(player.getWorld().getSpawnLocation()); // fallback
            }
            String msg = plugin.getConfig().getString("messages.left-training", "");
            player.sendMessage(MM.deserialize(prefix + msg));
        } else {
            // Entering the arena
            returnLocations.put(player.getUniqueId(), player.getLocation());
            player.teleport(plugin.getTrainingWorldManager().getArenaSpawn());
            String msg = plugin.getConfig().getString("messages.entered-training", "");
            player.sendMessage(MM.deserialize(prefix + msg));
        }

        return true;
    }
}
