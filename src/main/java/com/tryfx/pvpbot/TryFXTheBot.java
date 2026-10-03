package com.tryfx.pvpbot;

import org.bukkit.plugin.java.JavaPlugin;

public final class TryFXTheBot extends JavaPlugin {

    private BotManager botManager;
    private TrainingWorldManager trainingWorldManager;

    @Override
    public void onEnable() {
        // isPluginEnabled(), not getPlugin() != null - the latter stays
        // non-null even when Citizens' own onEnable() failed (e.g. a
        // Minecraft-version-compatibility refusal), which previously let
        // this plugin continue past this check with Citizens actually
        // unavailable and crash later with NoClassDefFoundError instead of
        // failing cleanly here with a useful message.
        if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            getLogger().severe("Citizens is not installed OR failed to enable (check the server log above this line "
                    + "for a Citizens error - a common cause is Citizens not yet supporting your exact Minecraft "
                    + "version). TryFXTheBot requires a working Citizens install. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        this.trainingWorldManager = new TrainingWorldManager(this);
        this.trainingWorldManager.setup();

        this.botManager = new BotManager(this);
        this.botManager.start();

        TrainingCommand trainingCmd = new TrainingCommand(this);
        getCommand("training").setExecutor(trainingCmd);

        SpawnBotCommand spawnCmd = new SpawnBotCommand(this);
        getCommand("trainingbot").setExecutor(spawnCmd);
        getCommand("trainingbot").setTabCompleter(spawnCmd);

        getLogger().info("TryFXTheBot enabled.");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.stop();
        }
        getLogger().info("TryFXTheBot disabled.");
    }

    public BotManager getBotManager() {
        return botManager;
    }

    public TrainingWorldManager getTrainingWorldManager() {
        return trainingWorldManager;
    }
}
