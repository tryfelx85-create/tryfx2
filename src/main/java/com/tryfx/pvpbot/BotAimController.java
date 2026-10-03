package com.tryfx.pvpbot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles turning the bot's body/head toward its target with a bounded
 * per-tick rotation speed and configurable aim error, so it never snaps
 * instantly to face the player (which would look robotic/impossible).
 */
public final class BotAimController {

    // Per-bot persistent aim error offset, refreshed periodically rather than
    // every tick, so mistakes look like brief tracking lag rather than noise.
    private double currentErrorYaw = 0;
    private double currentErrorPitch = 0;
    private long lastErrorRefreshTick = 0;

    /**
     * Rotates the bot's current facing toward the target location by at most
     * the configured degrees-per-tick, applying a bounded random aim error.
     */
    public void tickAim(CombatBot bot, Location targetLoc, long currentTick) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        BotConfig cfg = bot.getConfig();
        Location current = entity.getLocation();

        // Refresh the error offset every ~10 ticks so it reads as momentary
        // tracking imprecision rather than per-frame jitter.
        if (currentTick - lastErrorRefreshTick > 10) {
            lastErrorRefreshTick = currentTick;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            if (rnd.nextDouble() < cfg.aimErrorChance) {
                currentErrorYaw = (rnd.nextDouble() * 2 - 1) * cfg.aimErrorDegrees;
                currentErrorPitch = (rnd.nextDouble() * 2 - 1) * (cfg.aimErrorDegrees * 0.5);
            } else {
                // decay back toward zero error
                currentErrorYaw *= 0.5;
                currentErrorPitch *= 0.5;
            }
        }

        Vector direction = targetLoc.toVector().subtract(current.toVector());
        if (direction.lengthSquared() < 1.0E-6) return;

        double desiredYaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())) + currentErrorYaw;
        double horizontalDist = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        double desiredPitch = -Math.toDegrees(Math.atan2(direction.getY(), horizontalDist)) + currentErrorPitch;
        desiredPitch = clamp(desiredPitch, -90.0, 90.0);

        float currentYaw = current.getYaw();
        float currentPitch = current.getPitch();

        double yawDelta = shortestAngleDelta(currentYaw, (float) desiredYaw);
        double pitchDelta = desiredPitch - currentPitch;

        double maxStep = cfg.aimTurnSpeedDegPerTick;
        double yawStep = clamp(yawDelta, -maxStep, maxStep);
        double pitchStep = clamp(pitchDelta, -maxStep, maxStep);

        float newYaw = wrapDegrees(currentYaw + (float) yawStep);
        float newPitch = (float) clamp(currentPitch + pitchStep, -90.0, 90.0);

        Location newLoc = current.clone();
        newLoc.setYaw(newYaw);
        newLoc.setPitch(newPitch);

        // Rotation-only teleport: same position, new look direction.
        // Using teleport for look-only changes keeps head/body rotation in
        // sync for Citizens player-type NPCs without needing raw packets.
        entity.teleport(newLoc);
    }

    /** True once the bot is facing within toleranceDegrees of the target. */
    public boolean isFacingTarget(CombatBot bot, Location targetLoc, double toleranceDegrees) {
        Player entity = bot.getEntity();
        if (entity == null) return false;

        Location current = entity.getLocation();
        Vector direction = targetLoc.toVector().subtract(current.toVector());
        if (direction.lengthSquared() < 1.0E-6) return true;

        double desiredYaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        double delta = Math.abs(shortestAngleDelta(current.getYaw(), (float) desiredYaw));
        return delta <= toleranceDegrees;
    }

    private static double shortestAngleDelta(float from, float to) {
        double delta = (to - from) % 360.0;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return delta;
    }

    private static float wrapDegrees(float deg) {
        deg %= 360.0f;
        if (deg < -180.0f) deg += 360.0f;
        if (deg > 180.0f) deg -= 360.0f;
        return deg;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
