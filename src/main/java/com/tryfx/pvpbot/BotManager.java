package com.tryfx.pvpbot;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the registry of active training bots and runs the master tick loop
 * that advances every bot's BotAI once per server tick.
 */
public final class BotManager {

    private final TryFXTheBot plugin;
    private final Map<UUID, BotAI> activeBots = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private long tickCounter = 0;
    private NPCRegistry npcRegistry;

    public BotManager(TryFXTheBot plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Use Citizens' own default registry rather than creating a custom
        // named one. createNamedNPCRegistry(name, dataStore) requires a real
        // NPCDataStore as its second argument - passing null (as this code
        // previously did) leaves Citizens' internal "saves" reference null,
        // which crashes with a NullPointerException the moment an NPC is
        // actually created (generateIntegerId -> createUniqueNPCId needs a
        // working data store to hand out IDs). CitizensAPI.getNPCRegistry()
        // is already correctly backed by Citizens' own saves.yml at startup,
        // so it sidesteps this entirely. The practical effect is that
        // training bots are now tracked in the same place as any other
        // Citizens NPC on the server (e.g. the lobby's portal villagers) -
        // they're still tagged and managed independently via our own
        // activeBots map, so this doesn't cause any cross-talk between the
        // bot plugin and LobbyCore's portal NPCs.
        npcRegistry = CitizensAPI.getNPCRegistry();

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickCounter++;
            for (BotAI ai : activeBots.values()) {
                try {
                    ai.tick(tickCounter);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error ticking bot " + ai.getBot().getNpc().getId() + ": " + e.getMessage());
                }
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        // Despawn all bots on plugin disable to avoid orphaned NPCs
        for (BotAI ai : activeBots.values()) {
            NPC npc = ai.getBot().getNpc();
            if (npc.isSpawned()) {
                npc.despawn();
            }
            npc.destroy();
        }
        activeBots.clear();
    }

    /**
     * Spawns a new training bot at the given location with the given
     * difficulty preset. Returns the new CombatBot.
     */
    public CombatBot spawnBot(Location location, String presetName) {
        String botName = plugin.getConfig().getString("bot.name", "TryFXTheBot");

        NPC npc = npcRegistry.createNPC(EntityType.PLAYER, botName);
        npc.spawn(location);

        // NOTE ON SKINS: The Citizens Java API deliberately does not support
        // setting a skin directly from an image/URL (confirmed in Citizens'
        // own docs) - only the in-game "/npc skin --url <direct-image-url>"
        // command performs the Mojang upload+signing handshake, and it
        // requires the NPC to be selected by the command sender first.
        // Console-dispatched skin commands are also known to be unreliable
        // across Citizens versions (NPC selection is tracked per-sender and
        // behaves inconsistently from console).
        //
        // Rather than silently fail or produce a flaky skin, this is a
        // ONE-TIME MANUAL SETUP STEP documented in the plugin's README:
        // after first spawning a bot in-game, run:
        //   /npc select <id>
        //   /npc skin --url <direct PNG texture URL>
        // The skin then persists on that NPC going forward via Citizens'
        // own save data - this only needs to be done once per NPC, not per
        // spawn command invocation.
        if (!npc.hasTrait(SkinTrait.class)) {
            npc.getOrAddTrait(SkinTrait.class);
        }

        // Disable Citizens' own pathfinding/AI entirely - our BotAI drives
        // movement and rotation directly via the underlying entity.
        npc.getNavigator().setPaused(true);
        npc.data().setPersistent(net.citizensnpcs.api.npc.NPC.Metadata.NAMEPLATE_VISIBLE, true);
        npc.data().setPersistent(net.citizensnpcs.api.npc.NPC.Metadata.DEFAULT_PROTECTED, false);
        npc.data().setPersistent(net.citizensnpcs.api.npc.NPC.Metadata.COLLIDABLE, true);

        BotConfig config = BotConfig.loadPreset(plugin, presetName.toUpperCase());
        CombatBot combatBot = new CombatBot(npc, config);

        applyKit(combatBot);

        BotAI ai = new BotAI(combatBot);
        activeBots.put(npc.getUniqueId(), ai);

        return combatBot;
    }

    /**
     * Equips the bot's configured starter kit. Only "sword" is implemented
     * for now; additional kits can be added here without touching AI logic.
     */
    private void applyKit(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        String kit = plugin.getConfig().getString("bot.default-kit", "sword");
        switch (kit) {
            case "sword" -> {
                entity.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
            }
            default -> {
                plugin.getLogger().warning("Unknown kit '" + kit + "', defaulting to sword.");
                entity.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD));
            }
        }
    }

    public boolean removeBot(UUID npcUuid) {
        BotAI ai = activeBots.remove(npcUuid);
        if (ai == null) return false;

        NPC npc = ai.getBot().getNpc();
        if (npc.isSpawned()) {
            npc.despawn();
        }
        npc.destroy();
        return true;
    }

    public boolean removeAllBots() {
        if (activeBots.isEmpty()) return false;
        for (UUID id : activeBots.keySet()) {
            removeBot(id);
        }
        return true;
    }

    public void setBotTarget(CombatBot bot, Player target) {
        bot.setTargetUuid(target != null ? target.getUniqueId() : null);
    }

    public Map<UUID, BotAI> getActiveBots() {
        return activeBots;
    }

    public BotAI getNearestBot(Location location, double radius) {
        BotAI nearest = null;
        double nearestDistSq = radius * radius;
        for (BotAI ai : activeBots.values()) {
            Player entity = ai.getBot().getEntity();
            if (entity == null) continue;
            double distSq = entity.getLocation().distanceSquared(location);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = ai;
            }
        }
        return nearest;
    }
}
