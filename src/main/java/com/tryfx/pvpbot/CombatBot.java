package com.tryfx.pvpbot;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Wraps a single Citizens NPC and holds all mutable runtime state needed by
 * the combat AI: current state machine state, timers, target, strafe direction,
 * etc. One instance exists per spawned training bot.
 */
public final class CombatBot {

    private final NPC npc;
    private BotConfig config;

    private BotState state = BotState.IDLE;
    private UUID targetUuid;

    // Timing trackers (in ticks since epoch-ish server uptime; see BotAI for usage)
    private long lastDecisionTick = 0;
    private long lastAttackTick = 0;
    private long lastReactionNoticeTick = 0;
    private long stateEnteredTick = 0;

    // Movement state
    private int strafeDirection = 1; // 1 = right, -1 = left
    private int strafeTicksRemaining = 0;
    private boolean circleStrafing = false;
    private boolean wasSprinting = false;
    private int sprintResetCooldown = 0;

    // Crit state
    private boolean critJumpInProgress = false;
    private double jumpStartY = 0;

    // Combo state
    private int comboHitsLanded = 0;
    private long lastHitLandedTick = 0;

    // Healing/item-use state
    private boolean isUsingItem = false;
    private long itemUseStartTick = 0;

    public CombatBot(NPC npc, BotConfig config) {
        this.npc = npc;
        this.config = config;
    }

    public NPC getNpc() {
        return npc;
    }

    /**
     * Returns the underlying Bukkit Player entity Citizens spawned for this NPC.
     * Player-type Citizens NPCs satisfy instanceof Player, so direct velocity/
     * rotation/inventory control works exactly as it would on a real player.
     */
    public Player getEntity() {
        if (!npc.isSpawned()) return null;
        return (Player) npc.getEntity();
    }

    public BotConfig getConfig() {
        return config;
    }

    public void setConfig(BotConfig config) {
        this.config = config;
    }

    public BotState getState() {
        return state;
    }

    public void setState(BotState newState, long currentTick) {
        if (this.state != newState) {
            this.state = newState;
            this.stateEnteredTick = currentTick;
        }
    }

    public long getStateEnteredTick() {
        return stateEnteredTick;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public long getLastDecisionTick() {
        return lastDecisionTick;
    }

    public void setLastDecisionTick(long tick) {
        this.lastDecisionTick = tick;
    }

    public long getLastAttackTick() {
        return lastAttackTick;
    }

    public void setLastAttackTick(long tick) {
        this.lastAttackTick = tick;
    }

    public long getLastReactionNoticeTick() {
        return lastReactionNoticeTick;
    }

    public void setLastReactionNoticeTick(long tick) {
        this.lastReactionNoticeTick = tick;
    }

    public int getStrafeDirection() {
        return strafeDirection;
    }

    public void setStrafeDirection(int dir) {
        this.strafeDirection = dir;
    }

    public int getStrafeTicksRemaining() {
        return strafeTicksRemaining;
    }

    public void setStrafeTicksRemaining(int ticks) {
        this.strafeTicksRemaining = ticks;
    }

    public boolean isCircleStrafing() {
        return circleStrafing;
    }

    public void setCircleStrafing(boolean circleStrafing) {
        this.circleStrafing = circleStrafing;
    }

    public boolean wasSprinting() {
        return wasSprinting;
    }

    public void setWasSprinting(boolean sprinting) {
        this.wasSprinting = sprinting;
    }

    public int getSprintResetCooldown() {
        return sprintResetCooldown;
    }

    public void setSprintResetCooldown(int cooldown) {
        this.sprintResetCooldown = cooldown;
    }

    public boolean isCritJumpInProgress() {
        return critJumpInProgress;
    }

    public void setCritJumpInProgress(boolean inProgress) {
        this.critJumpInProgress = inProgress;
    }

    public double getJumpStartY() {
        return jumpStartY;
    }

    public void setJumpStartY(double y) {
        this.jumpStartY = y;
    }

    public int getComboHitsLanded() {
        return comboHitsLanded;
    }

    public void setComboHitsLanded(int hits) {
        this.comboHitsLanded = hits;
    }

    public long getLastHitLandedTick() {
        return lastHitLandedTick;
    }

    public void setLastHitLandedTick(long tick) {
        this.lastHitLandedTick = tick;
    }

    public boolean isUsingItem() {
        return isUsingItem;
    }

    public void setUsingItem(boolean usingItem) {
        this.isUsingItem = usingItem;
    }

    public long getItemUseStartTick() {
        return itemUseStartTick;
    }

    public void setItemUseStartTick(long tick) {
        this.itemUseStartTick = tick;
    }
}
