package com.tryfx.pvpbot;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Immutable set of tunable combat AI stats for one bot.
 * Loaded from a preset section in config.yml (presets.EASY, presets.MEDIUM, etc.)
 * or a custom named section under the same "presets" map.
 */
public final class BotConfig {

    public final String presetName;

    public final int reactionDelayTicks;
    public final int decisionIntervalTicks;

    public final double aimTurnSpeedDegPerTick;
    public final double aimErrorDegrees;
    public final double aimErrorChance;

    public final int attackTimingJitterTicks;
    public final double missChance;

    public final double strafeChance;
    public final int strafeDurationTicksMin;
    public final int strafeDurationTicksMax;
    public final double circleStrafeChance;
    public final double directionChangeChance;

    public final double jumpChance;
    public final double critAttemptChance;
    public final double critSuccessChance;

    public final double sprintResetChance;

    public final double comboSkill;

    public final double blockReactionChance;
    public final int blockReactionDelayTicks;

    public final double healHealthThreshold;
    public final double healDecisionQuality;
    public final double itemUseMistakeChance;

    public final double humanizationJitter;

    public final double aggression;

    // Preferred combat distance, shared across all presets (not per-difficulty,
    // but still exposed here for convenience / future per-preset override)
    public final double preferredDistanceMin;
    public final double preferredDistanceMax;

    private BotConfig(Builder b) {
        this.presetName = b.presetName;
        this.reactionDelayTicks = b.reactionDelayTicks;
        this.decisionIntervalTicks = b.decisionIntervalTicks;
        this.aimTurnSpeedDegPerTick = b.aimTurnSpeedDegPerTick;
        this.aimErrorDegrees = b.aimErrorDegrees;
        this.aimErrorChance = b.aimErrorChance;
        this.attackTimingJitterTicks = b.attackTimingJitterTicks;
        this.missChance = b.missChance;
        this.strafeChance = b.strafeChance;
        this.strafeDurationTicksMin = b.strafeDurationTicksMin;
        this.strafeDurationTicksMax = b.strafeDurationTicksMax;
        this.circleStrafeChance = b.circleStrafeChance;
        this.directionChangeChance = b.directionChangeChance;
        this.jumpChance = b.jumpChance;
        this.critAttemptChance = b.critAttemptChance;
        this.critSuccessChance = b.critSuccessChance;
        this.sprintResetChance = b.sprintResetChance;
        this.comboSkill = b.comboSkill;
        this.blockReactionChance = b.blockReactionChance;
        this.blockReactionDelayTicks = b.blockReactionDelayTicks;
        this.healHealthThreshold = b.healHealthThreshold;
        this.healDecisionQuality = b.healDecisionQuality;
        this.itemUseMistakeChance = b.itemUseMistakeChance;
        this.humanizationJitter = b.humanizationJitter;
        this.aggression = b.aggression;
        this.preferredDistanceMin = b.preferredDistanceMin;
        this.preferredDistanceMax = b.preferredDistanceMax;
    }

    /**
     * Loads a BotConfig from a named preset section under "presets" in config.yml,
     * e.g. loadPreset(plugin, "EASY") reads presets.EASY.*
     */
    public static BotConfig loadPreset(TryFXTheBot plugin, String presetName) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("presets." + presetName);
        if (root == null) {
            throw new IllegalArgumentException("Unknown preset: " + presetName);
        }

        double distMin = plugin.getConfig().getDouble("distance.preferred-min", 2.5);
        double distMax = plugin.getConfig().getDouble("distance.preferred-max", 3.2);

        Builder b = new Builder(presetName.toUpperCase());
        b.reactionDelayTicks = root.getInt("reaction-delay-ticks", 5);
        b.decisionIntervalTicks = Math.max(1, root.getInt("decision-interval-ticks", 4));
        b.aimTurnSpeedDegPerTick = root.getDouble("aim-turn-speed-deg-per-tick", 14.0);
        b.aimErrorDegrees = root.getDouble("aim-error-degrees", 6.0);
        b.aimErrorChance = root.getDouble("aim-error-chance", 0.18);
        b.attackTimingJitterTicks = root.getInt("attack-timing-jitter-ticks", 2);
        b.missChance = root.getDouble("miss-chance", 0.08);
        b.strafeChance = root.getDouble("strafe-chance", 0.35);
        b.strafeDurationTicksMin = root.getInt("strafe-duration-ticks-min", 5);
        b.strafeDurationTicksMax = Math.max(b.strafeDurationTicksMin, root.getInt("strafe-duration-ticks-max", 12));
        b.circleStrafeChance = root.getDouble("circle-strafe-chance", 0.15);
        b.directionChangeChance = root.getDouble("direction-change-chance", 0.30);
        b.jumpChance = root.getDouble("jump-chance", 0.12);
        b.critAttemptChance = root.getDouble("crit-attempt-chance", 0.25);
        b.critSuccessChance = root.getDouble("crit-success-chance", 0.65);
        b.sprintResetChance = root.getDouble("sprint-reset-chance", 0.30);
        b.comboSkill = root.getDouble("combo-skill", 0.50);
        b.blockReactionChance = root.getDouble("block-reaction-chance", 0.40);
        b.blockReactionDelayTicks = root.getInt("block-reaction-delay-ticks", 6);
        b.healHealthThreshold = root.getDouble("heal-health-threshold", 0.40);
        b.healDecisionQuality = root.getDouble("heal-decision-quality", 0.60);
        b.itemUseMistakeChance = root.getDouble("item-use-mistake-chance", 0.12);
        b.humanizationJitter = root.getDouble("humanization-jitter", 0.25);
        b.aggression = root.getDouble("aggression", 0.55);
        b.preferredDistanceMin = distMin;
        b.preferredDistanceMax = distMax;

        return new BotConfig(b);
    }

    private static final class Builder {
        final String presetName;
        int reactionDelayTicks;
        int decisionIntervalTicks;
        double aimTurnSpeedDegPerTick;
        double aimErrorDegrees;
        double aimErrorChance;
        int attackTimingJitterTicks;
        double missChance;
        double strafeChance;
        int strafeDurationTicksMin;
        int strafeDurationTicksMax;
        double circleStrafeChance;
        double directionChangeChance;
        double jumpChance;
        double critAttemptChance;
        double critSuccessChance;
        double sprintResetChance;
        double comboSkill;
        double blockReactionChance;
        int blockReactionDelayTicks;
        double healHealthThreshold;
        double healDecisionQuality;
        double itemUseMistakeChance;
        double humanizationJitter;
        double aggression;
        double preferredDistanceMin;
        double preferredDistanceMax;

        Builder(String presetName) {
            this.presetName = presetName;
        }
    }
}
