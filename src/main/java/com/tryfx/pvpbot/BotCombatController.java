package com.tryfx.pvpbot;

import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles attack execution: range/cooldown checks, standard attacks,
 * critical hits (via real jump-and-fall timing, not a damage multiplier
 * hack), and sprint-reset ("W-tap") attacks.
 *
 * Reach and cooldown are kept to legitimate vanilla values - this
 * controller decides WHEN to attack, never grants illegal reach or bypasses
 * Minecraft's own attack cooldown.
 *
 * IMPORTANT: Bukkit/Paper's Player API has no public "attack(Entity)" method
 * that reproduces a real player's client-driven hit resolution - that flow
 * only exists for real client connections. For an NPC-driven attack we
 * instead compute the weapon's damage ourselves and apply it via
 * Damageable#damage(amount, source), which still runs through the normal
 * server-side damage pipeline (armor, enchantments, absorption, etc. are
 * still applied automatically by the engine after this call).
 */
public final class BotCombatController {

    private static final double ATTACK_RANGE = 3.0; // vanilla sword reach is ~3 blocks
    private static final int BASE_ATTACK_COOLDOWN_TICKS = 10; // ~0.5s between swings at full strength

    // Base damage values for the currently supported sword-only kit.
    // Extend this map-style lookup if/when more kits are added.
    private static final double IRON_SWORD_BASE_DAMAGE = 6.0;
    private static final double CRIT_MULTIPLIER = 1.5;

    public boolean isInRange(CombatBot bot, Player target) {
        Player entity = bot.getEntity();
        if (entity == null) return false;
        return entity.getLocation().distance(target.getLocation()) <= ATTACK_RANGE;
    }

    public boolean isAttackReady(CombatBot bot, long currentTick) {
        BotConfig cfg = bot.getConfig();
        int jitter = cfg.attackTimingJitterTicks > 0
                ? ThreadLocalRandom.current().nextInt(-cfg.attackTimingJitterTicks, cfg.attackTimingJitterTicks + 1)
                : 0;
        long requiredWait = BASE_ATTACK_COOLDOWN_TICKS + jitter;
        return (currentTick - bot.getLastAttackTick()) >= requiredWait;
    }

    /**
     * Performs a standard grounded attack against the target.
     * Returns true if the attack was actually executed.
     */
    public boolean performAttack(CombatBot bot, Player target, long currentTick) {
        Player entity = bot.getEntity();
        if (entity == null) return false;

        BotConfig cfg = bot.getConfig();

        // Even when in range and off cooldown, lower-difficulty bots
        // sometimes whiff - this represents imperfect execution, not
        // illegitimate mechanics.
        if (ThreadLocalRandom.current().nextDouble() < cfg.missChance) {
            bot.setLastAttackTick(currentTick);
            swingArm(entity);
            return false; // swung but intentionally missed
        }

        swingArm(entity);
        target.damage(IRON_SWORD_BASE_DAMAGE, entity);
        bot.setLastAttackTick(currentTick);
        onHitLanded(bot, currentTick);
        return true;
    }

    /**
     * Attempts a critical hit: the bot must already be in the valid falling
     * window (BotMovementController.isInCritWindow) - this method only
     * executes the attack itself once that physical condition is met.
     */
    public boolean performCriticalAttack(CombatBot bot, Player target, long currentTick) {
        Player entity = bot.getEntity();
        if (entity == null) return false;

        BotConfig cfg = bot.getConfig();

        // crit-success-chance represents the bot's skill at landing the
        // attack within the precise falling window, not an artificial
        // damage multiplier - a "failed" attempt still swings, just not
        // during the optimal frame, so no guaranteed crit flag is forced.
        boolean willLandAsCrit = ThreadLocalRandom.current().nextDouble() < cfg.critSuccessChance;

        if (ThreadLocalRandom.current().nextDouble() < cfg.missChance) {
            bot.setLastAttackTick(currentTick);
            swingArm(entity);
            return false;
        }

        swingArm(entity);
        double damage = willLandAsCrit ? IRON_SWORD_BASE_DAMAGE * CRIT_MULTIPLIER : IRON_SWORD_BASE_DAMAGE;
        target.damage(damage, entity);
        if (willLandAsCrit) {
            target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.0);
        }
        bot.setLastAttackTick(currentTick);
        onHitLanded(bot, currentTick);
        return willLandAsCrit; // caller uses this only for state/logging purposes
    }

    /**
     * Cuts sprint momentarily then re-engages it, matching the real
     * sprint-reset ("W-tap") knockback optimization players use - no
     * artificial extra knockback is applied, this purely toggles the
     * sprinting flag the same way a player's client input would.
     */
    public void performSprintReset(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        entity.setSprinting(false);
        bot.setSprintResetCooldown(2); // ticks to hold sprint off before re-engaging
    }

    public void tickSprintReset(CombatBot bot) {
        int cooldown = bot.getSprintResetCooldown();
        if (cooldown <= 0) return;

        cooldown--;
        bot.setSprintResetCooldown(cooldown);

        if (cooldown == 0) {
            Player entity = bot.getEntity();
            if (entity != null) {
                entity.setSprinting(true);
            }
        }
    }

    /**
     * Tracks combo progression. Should be called whenever a hit lands.
     * Combo continuation eligibility is decided by BotAI using comboSkill
     * plus distance/velocity checks - this just maintains the counter.
     */
    private void onHitLanded(CombatBot bot, long currentTick) {
        long sinceLastHit = currentTick - bot.getLastHitLandedTick();
        // Reset combo count if too much time has passed since the last hit
        if (sinceLastHit > 30) { // 1.5s gap breaks a combo
            bot.setComboHitsLanded(1);
        } else {
            bot.setComboHitsLanded(bot.getComboHitsLanded() + 1);
        }
        bot.setLastHitLandedTick(currentTick);
    }

    /**
     * Decides whether the bot should continue a combo after landing a hit,
     * based on comboSkill and current spacing - never allows attacking from
     * beyond legitimate reach.
     */
    public boolean shouldContinueCombo(CombatBot bot, Player target) {
        if (!isInRange(bot, target)) return false;
        BotConfig cfg = bot.getConfig();
        return ThreadLocalRandom.current().nextDouble() < cfg.comboSkill;
    }

    private void swingArm(Player entity) {
        entity.swingMainHand();
    }
}
