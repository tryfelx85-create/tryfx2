package com.tryfx.pvpbot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The combat state machine. One BotAI instance drives one CombatBot, ticked
 * every server tick by TryFXTheBot's scheduler. Transitions between states
 * are re-evaluated continuously based on distance, health, cooldowns, and
 * difficulty-scaled randomness - never a fixed scripted sequence.
 */
public final class BotAI {

    private final CombatBot bot;
    private final BotAimController aimController = new BotAimController();
    private final BotMovementController movementController = new BotMovementController();
    private final BotCombatController combatController = new BotCombatController();
    private final BotItemController itemController = new BotItemController();

    public BotAI(CombatBot bot) {
        this.bot = bot;
    }

    public void tick(long currentTick) {
        Player entity = bot.getEntity();
        if (entity == null || !entity.isValid()) return;

        if (bot.getState() == BotState.DEAD) return;

        // Always tick item-use and sprint-reset timers regardless of state,
        // since they're time-based commitments that must resolve.
        itemController.tickEating(bot, currentTick);
        combatController.tickSprintReset(bot);

        if (bot.isUsingItem()) {
            // Fully committed to eating - no movement/attack this tick,
            // matching vanilla item-use lockout.
            return;
        }

        Player target = resolveTarget();
        if (target == null) {
            bot.setState(BotState.SEARCHING, currentTick);
            target = findNearestPlayer(entity.getLocation(), 24.0);
            if (target != null) {
                bot.setTargetUuid(target.getUniqueId());
            }
            return;
        }

        // Respect reaction delay: only re-decide at the configured interval,
        // representing human-like processing time rather than per-tick
        // omniscient decision-making.
        BotConfig cfg = bot.getConfig();
        if (currentTick - bot.getLastDecisionTick() >= cfg.decisionIntervalTicks) {
            bot.setLastDecisionTick(currentTick);
            decide(entity, target, currentTick);
        }

        // Aim is updated every tick (smoothed by BotAimController's turn-speed
        // cap) so rotation looks continuous rather than stepped.
        aimController.tickAim(bot, target.getEyeLocation(), currentTick);

        execute(entity, target, currentTick);
    }

    private void decide(Player entity, Player target, long currentTick) {
        BotConfig cfg = bot.getConfig();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double health = entity.getHealth();
        double maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double healthFraction = health / maxHealth;
        double distance = entity.getLocation().distance(target.getLocation());

        // Low health: consider disengaging/healing before anything else.
        if (itemController.shouldConsiderHealing(bot)) {
            if (itemController.findHealingItem(bot) != null && itemController.isSafeToHeal(bot, target)) {
                bot.setState(BotState.HEALING, currentTick);
                return;
            } else if (healthFraction < cfg.healHealthThreshold * 0.6) {
                // Too dangerous to heal right now but still critical - retreat first.
                bot.setState(BotState.RETREATING, currentTick);
                return;
            }
        }

        // Too close: back off to preferred range.
        if (distance < cfg.preferredDistanceMin) {
            bot.setState(BotState.RETREATING, currentTick);
            return;
        }

        // Out of attack range entirely: approach.
        if (distance > cfg.preferredDistanceMax + 1.0) {
            bot.setState(BotState.APPROACHING, currentTick);
            return;
        }

        // In workable range - choose an engagement action.
        boolean comboActive = bot.getComboHitsLanded() > 0
                && (currentTick - bot.getLastHitLandedTick()) < 30;

        if (comboActive && combatController.shouldContinueCombo(bot, target)) {
            bot.setState(BotState.COMBOING, currentTick);
            return;
        }

        if (combatController.isAttackReady(bot, currentTick) && combatController.isInRange(bot, target)) {
            boolean attemptCrit = rnd.nextDouble() < cfg.critAttemptChance && entity.isOnGround();
            if (attemptCrit) {
                bot.setState(BotState.CRITICAL_ATTACK, currentTick);
            } else {
                bot.setState(BotState.ATTACKING, currentTick);
            }
            return;
        }

        if (rnd.nextDouble() < cfg.strafeChance) {
            bot.setState(BotState.STRAFING, currentTick);
            bot.setCircleStrafing(rnd.nextDouble() < cfg.circleStrafeChance);
            if (bot.getStrafeTicksRemaining() <= 0 || rnd.nextDouble() < cfg.directionChangeChance) {
                bot.setStrafeDirection(rnd.nextBoolean() ? 1 : -1);
                int min = cfg.strafeDurationTicksMin;
                int max = cfg.strafeDurationTicksMax;
                bot.setStrafeTicksRemaining(min + rnd.nextInt(Math.max(1, max - min + 1)));
            }
            return;
        }

        bot.setState(BotState.ENGAGING, currentTick);
    }

    private void execute(Player entity, Player target, long currentTick) {
        BotConfig cfg = bot.getConfig();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        switch (bot.getState()) {
            case APPROACHING -> {
                movementController.approach(bot, target);
                maybeJump(entity, cfg, rnd);
            }
            case RETREATING -> movementController.retreat(bot, target);
            case STRAFING -> {
                movementController.strafe(bot, target);
                int remaining = bot.getStrafeTicksRemaining() - 1;
                bot.setStrafeTicksRemaining(remaining);
                if (remaining <= 0 && !bot.isCircleStrafing()) {
                    bot.setState(BotState.ENGAGING, currentTick);
                }
                maybeJump(entity, cfg, rnd);
            }
            case ENGAGING -> {
                // Hold position, keep facing target, minor idle jitter.
                movementController.applyMovementJitter(bot);
            }
            case ATTACKING -> {
                if (combatController.isInRange(bot, target)) {
                    if (rnd.nextDouble() < cfg.sprintResetChance && entity.isSprinting()) {
                        combatController.performSprintReset(bot);
                    }
                    combatController.performAttack(bot, target, currentTick);
                }
                bot.setState(BotState.RECOVERING, currentTick);
            }
            case CRITICAL_ATTACK -> executeCriticalAttack(entity, target, currentTick);
            case COMBOING -> {
                if (combatController.isInRange(bot, target) && combatController.isAttackReady(bot, currentTick)) {
                    combatController.performAttack(bot, target, currentTick);
                }
                bot.setState(BotState.RECOVERING, currentTick);
            }
            case HEALING -> {
                var healingItem = itemController.findHealingItem(bot);
                if (healingItem != null) {
                    itemController.beginEating(bot, healingItem, currentTick);
                } else {
                    bot.setState(BotState.ENGAGING, currentTick);
                }
            }
            case RECOVERING -> {
                // Brief pause after an attack before the next decision cycle
                // re-evaluates - mirrors natural post-swing repositioning.
                if (currentTick - bot.getStateEnteredTick() > 2) {
                    bot.setState(BotState.ENGAGING, currentTick);
                }
            }
            default -> { /* IDLE, SEARCHING, DEFENDING, DEAD handled elsewhere or no-op */ }
        }
    }

    /**
     * Drives the full jump -> fall -> attack sequence for a critical hit.
     * The crit "success" is determined by whether the bot's attack lands
     * within the genuine falling window - not by flagging arbitrary bonus
     * damage.
     */
    private void executeCriticalAttack(Player entity, Player target, long currentTick) {
        if (!bot.isCritJumpInProgress()) {
            if (movementController.jump(bot)) {
                bot.setCritJumpInProgress(true);
            } else {
                // Can't jump right now (not grounded) - fall back to a normal attack.
                bot.setState(BotState.ATTACKING, currentTick);
            }
            return;
        }

        if (movementController.isInCritWindow(bot)) {
            if (combatController.isInRange(bot, target)) {
                combatController.performCriticalAttack(bot, target, currentTick);
            }
            bot.setCritJumpInProgress(false);
            bot.setState(BotState.RECOVERING, currentTick);
        } else if (entity.isOnGround()) {
            // Landed without ever entering a valid fall window (e.g. jumped
            // too early) - abandon this crit attempt cleanly.
            bot.setCritJumpInProgress(false);
            bot.setState(BotState.RECOVERING, currentTick);
        }
        // else: still rising or not yet falling - wait for next tick.
    }

    private void maybeJump(Player entity, BotConfig cfg, ThreadLocalRandom rnd) {
        if (entity.isOnGround() && rnd.nextDouble() < cfg.jumpChance * 0.1) {
            movementController.jump(bot);
        }
    }

    private Player resolveTarget() {
        if (bot.getTargetUuid() == null) return null;
        Player p = Bukkit.getPlayer(bot.getTargetUuid());
        if (p == null || !p.isOnline() || !p.isValid()) {
            bot.setTargetUuid(null);
            return null;
        }
        return p;
    }

    private Player findNearestPlayer(Location origin, double radius) {
        Player nearest = null;
        double nearestDistSq = radius * radius;
        for (Player p : origin.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double distSq = p.getLocation().distanceSquared(origin);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    public CombatBot getBot() {
        return bot;
    }
}
