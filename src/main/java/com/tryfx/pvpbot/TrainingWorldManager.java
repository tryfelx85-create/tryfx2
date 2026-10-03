package com.tryfx.pvpbot;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;

/**
 * Creates and manages the dedicated PvP training world: a vanilla-style
 * walled arena, generated flat with a bordered platform so there's a clear
 * boundary and no open-world distractions.
 *
 * Arena is built programmatically on first setup so no external schematic
 * file/plugin (WorldEdit etc.) is required.
 */
public final class TrainingWorldManager {

    private static final String WORLD_NAME = "tryfx_training";

    // Arena dimensions (blocks)
    private static final int ARENA_RADIUS = 20;   // half-width of the fighting floor
    private static final int WALL_HEIGHT = 8;
    private static final int FLOOR_Y = 64;

    private final TryFXTheBot plugin;
    private World trainingWorld;

    public TrainingWorldManager(TryFXTheBot plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            trainingWorld = existing;
            return;
        }

        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
                + "{\"block\":\"minecraft:stone\",\"height\":3}],\"biome\":\"minecraft:plains\"}");
        creator.generateStructures(false);

        trainingWorld = Bukkit.createWorld(creator);
        if (trainingWorld == null) {
            plugin.getLogger().severe("Failed to create training world!");
            return;
        }

        trainingWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        trainingWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        trainingWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        trainingWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        trainingWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        trainingWorld.setTime(6000); // midday
        trainingWorld.setSpawnLocation(0, FLOOR_Y + 1, 0);

        buildArena();

        plugin.getLogger().info("Training world generated.");
    }

    /**
     * Builds a vanilla-style walled square arena: stone-brick floor,
     * cobblestone/stone-brick perimeter walls, open roof.
     */
    private void buildArena() {
        World w = trainingWorld;

        for (int x = -ARENA_RADIUS; x <= ARENA_RADIUS; x++) {
            for (int z = -ARENA_RADIUS; z <= ARENA_RADIUS; z++) {
                Block floor = w.getBlockAt(x, FLOOR_Y, z);
                boolean isEdge = (x == -ARENA_RADIUS || x == ARENA_RADIUS || z == -ARENA_RADIUS || z == ARENA_RADIUS);

                if (isEdge) {
                    // Perimeter wall
                    for (int y = FLOOR_Y; y <= FLOOR_Y + WALL_HEIGHT; y++) {
                        w.getBlockAt(x, y, z).setType(Material.STONE_BRICKS);
                    }
                } else {
                    // Floor with a simple checkered pattern for visual clarity
                    boolean checker = ((x + z) & 1) == 0;
                    floor.setType(checker ? Material.STONE_BRICKS : Material.SMOOTH_STONE);
                }
            }
        }

        // Corner pillars/torches for lighting (peaceful difficulty + no mob
        // spawning means this is purely aesthetic/visibility, not safety-critical)
        placeCornerTorch(ARENA_RADIUS - 1, ARENA_RADIUS - 1);
        placeCornerTorch(ARENA_RADIUS - 1, -(ARENA_RADIUS - 1));
        placeCornerTorch(-(ARENA_RADIUS - 1), ARENA_RADIUS - 1);
        placeCornerTorch(-(ARENA_RADIUS - 1), -(ARENA_RADIUS - 1));
    }

    private void placeCornerTorch(int x, int z) {
        trainingWorld.getBlockAt(x, FLOOR_Y + 1, z).setType(Material.TORCH);
    }

    public World getTrainingWorld() {
        return trainingWorld;
    }

    public Location getArenaSpawn() {
        return new Location(trainingWorld, 0.5, FLOOR_Y + 1, 0.5);
    }
}
