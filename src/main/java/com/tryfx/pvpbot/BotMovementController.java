package com.tryfx.pvpbot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the bot's horizontal movement (approach, retreat, strafe) and
 * jump/fall logic by directly setting entity velocity each tick, rather
 * than using Citizens' pathfinding Navigator (which is built for point-A to
 * point-B movement, not frame-by-frame PvP combat positioning).
 *
 * Movement speed constants approximate vanilla player walk/sprint speed
 * (0.2 / 0.28 blocks-per-tick-ish horizontal velocity magnitude; actual
 * vanilla base speed attribute is 0.1, modified by sprint ~1.3x - we work
 * in velocity-vector terms tuned to feel correct rather than reproducing
 * the attribute math exactly).
 */
public final class BotMovementController {

    private static final double WALK_SPEED = 0.215;
    private static final double SPRINT_SPEED = 0.29;
    private static final double STRAFE_SPEED = 0.24;
    private static final double RETREAT_SPEED = 0.24;
    private static final double JUMP_VELOCITY = 0.42; // vanilla player jump Y velocity

    /**
     * Moves the bot toward the target, sprinting, until within attack range.
     */
    public void approach(CombatBot bot, Player target) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        Vector toTarget = horizontalDirection(entity.getLocation(), target.getLocation());
        if (toTarget.lengthSquared() < 1.0E-6) return;

        entity.setSprinting(true);
        bot.setWasSprinting(true);

        Vector velocity = toTarget.multiply(SPRINT_SPEED);
        applyHorizontalVelocity(entity, velocity);
    }

    /**
     * Backs the bot away from the target while continuing to face it
     * (handled separately by BotAimController).
     */
    public void retreat(CombatBot bot, Player target) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        Vector awayFromTarget = horizontalDirection(target.getLocation(), entity.getLocation());
        if (awayFromTarget.lengthSquared() < 1.0E-6) return;

        entity.setSprinting(false);
        bot.setWasSprinting(false);

        Vector velocity = awayFromTarget.multiply(RETREAT_SPEED);
        applyHorizontalVelocity(entity, velocity);
    }

    /**
     * Strafes sideways relative to the target. If circleStrafe is true,
     * continuously orbits; otherwise performs a short burst in the bot's
     * current strafe direction before BotAI flips/ends it.
     */
    public void strafe(CombatBot bot, Player target) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        Vector toTarget = horizontalDirection(entity.getLocation(), target.getLocation());
        if (toTarget.lengthSquared() < 1.0E-6) return;

        // Perpendicular vector in the horizontal plane (rotate 90 degrees)
        Vector perpendicular = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        perpendicular.multiply(bot.getStrafeDirection());

        // Blend a small amount of approach/retreat-correction so strafing
        // doesn't drift the bot to an unreasonable distance over time.
        double currentDist = entity.getLocation().distance(target.getLocation());
        BotConfig cfg = bot.getConfig();
        Vector correction = new Vector(0, 0, 0);
        if (currentDist > cfg.preferredDistanceMax) {
            correction = toTarget.clone().multiply(0.10);
        } else if (currentDist < cfg.preferredDistanceMin) {
            correction = toTarget.clone().multiply(-0.10);
        }

        Vector velocity = perpendicular.multiply(STRAFE_SPEED).add(correction);
        entity.setSprinting(false);
        applyHorizontalVelocity(entity, velocity);
    }

    /**
     * Stops horizontal movement (used briefly during sprint-reset / W-tap
     * attacks, and while winding up certain actions).
     */
    public void stopHorizontalMovement(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return;
        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(0, v.getY(), 0));
        entity.setSprinting(false);
        bot.setWasSprinting(false);
    }

    /**
     * Performs a jump (used for normal movement variation, crit attempts,
     * and jump-resets). Only triggers if the bot is on the ground.
     */
    public boolean jump(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null || !entity.isOnGround()) return false;

        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(v.getX(), JUMP_VELOCITY, v.getZ()));
        bot.setJumpStartY(entity.getLocation().getY());
        return true;
    }

    /**
     * Returns true once the bot has started falling after a jump (i.e. is
     * airborne and moving downward) - the valid window for a critical hit
     * per vanilla mechanics (falling, not on ground, not in water/climbing,
     * no blindness, not riding).
     */
    public boolean isInCritWindow(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return false;

        boolean falling = entity.getVelocity().getY() < 0;
        boolean airborne = !entity.isOnGround();
        boolean notClimbing = !entity.isClimbing();
        boolean notInWater = !entity.isInWater();
        boolean notRiding = entity.getVehicle() == null;

        return falling && airborne && notClimbing && notInWater && notRiding;
    }

    /**
     * Small random rhythm-breaking movement jitter applied occasionally to
     * avoid perfectly linear, predictable movement. Scaled by humanizationJitter.
     */
    public void applyMovementJitter(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        BotConfig cfg = bot.getConfig();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() > cfg.humanizationJitter * 0.1) return;

        double jitterX = (rnd.nextDouble() * 2 - 1) * 0.03;
        double jitterZ = (rnd.nextDouble() * 2 - 1) * 0.03;
        Vector v = entity.getVelocity();
        entity.setVelocity(v.add(new Vector(jitterX, 0, jitterZ)));
    }

    private Vector horizontalDirection(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0E-6) return new Vector(0, 0, 0);
        return dir.normalize();
    }

    private void applyHorizontalVelocity(Player entity, Vector horizontal) {
        Vector current = entity.getVelocity();
        entity.setVelocity(new Vector(horizontal.getX(), current.getY(), horizontal.getZ()));
    }
}
