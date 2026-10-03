package com.tryfx.pvpbot;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles item-use decisions: healing (golden apples etc.), and the shield /
 * totem framework. With the sword-only starter kit these mostly stay inert,
 * but the decision logic and timing cost are implemented now so richer kits
 * can be dropped in later without touching the state machine.
 *
 * Item use intentionally occupies real time (itemUseStartTick / duration)
 * and should block attacking/movement-cancel actions while active, matching
 * vanilla eat/block animation lockout.
 */
public final class BotItemController {

    private static final int EAT_DURATION_TICKS = 32; // vanilla golden apple eat time ~1.6s

    /**
     * Returns true if the bot's health fraction is low enough, per its
     * configured threshold, to consider healing at all.
     */
    public boolean shouldConsiderHealing(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return false;

        double healthFraction = entity.getHealth() / entity.getAttribute(
                org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        return healthFraction <= bot.getConfig().healHealthThreshold;
    }

    /**
     * Decides whether this is actually a *safe* moment to heal (not mid-combo,
     * not about to be hit) - lower-skill bots make this judgment poorly
     * (may heal too late, or interrupt a good attack sequence unnecessarily).
     */
    public boolean isSafeToHeal(CombatBot bot, Player target) {
        BotConfig cfg = bot.getConfig();
        double distance = bot.getEntity() != null
                ? bot.getEntity().getLocation().distance(target.getLocation())
                : Double.MAX_VALUE;

        boolean genuinelySafe = distance > cfg.preferredDistanceMax * 1.5;

        // healDecisionQuality represents how reliably the bot correctly
        // judges safety; a "bad" roll means it may heal despite danger
        // (mistake) or fail to heal despite safety (overly cautious/slow).
        if (ThreadLocalRandom.current().nextDouble() < cfg.healDecisionQuality) {
            return genuinelySafe;
        } else {
            return ThreadLocalRandom.current().nextBoolean();
        }
    }

    /**
     * Finds a healing item in the bot's inventory (golden apple or enchanted
     * golden apple), if any.
     */
    public ItemStack findHealingItem(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return null;

        PlayerInventory inv = entity.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                return item;
            }
        }
        return null;
    }

    /**
     * Begins the eating action: swaps the item into the main hand and marks
     * the bot as using an item for EAT_DURATION_TICKS. Movement/attack
     * controllers should check CombatBot.isUsingItem() and hold off.
     */
    public void beginEating(CombatBot bot, ItemStack healingItem, long currentTick) {
        Player entity = bot.getEntity();
        if (entity == null) return;

        entity.getInventory().setItemInMainHand(healingItem);
        bot.setUsingItem(true);
        bot.setItemUseStartTick(currentTick);
    }

    /**
     * Call every tick while isUsingItem() is true. Completes the eat once
     * the duration has elapsed, consuming one item and healing the bot.
     */
    public void tickEating(CombatBot bot, long currentTick) {
        if (!bot.isUsingItem()) return;

        if (currentTick - bot.getItemUseStartTick() >= EAT_DURATION_TICKS) {
            Player entity = bot.getEntity();
            if (entity != null) {
                ItemStack mainHand = entity.getInventory().getItemInMainHand();
                if (mainHand != null && mainHand.getAmount() > 0) {
                    if (mainHand.getAmount() > 1) {
                        mainHand.setAmount(mainHand.getAmount() - 1);
                    } else {
                        entity.getInventory().setItemInMainHand(null);
                    }
                }
                double maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                double healAmount = mainHand != null && mainHand.getType() == Material.ENCHANTED_GOLDEN_APPLE ? 4.0 : 4.0;
                entity.setHealth(Math.min(maxHealth, entity.getHealth() + healAmount));
            }
            bot.setUsingItem(false);
        }
    }

    /**
     * Shield handling (framework for future kits). Raises the shield in the
     * off-hand if present and the bot is currently intending to block.
     */
    public boolean hasShield(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null) return false;
        EntityEquipment eq = entity.getEquipment();
        return eq != null && eq.getItemInOffHand().getType() == Material.SHIELD;
    }

    public void raiseShield(CombatBot bot) {
        Player entity = bot.getEntity();
        if (entity == null || !hasShield(bot)) return;
        // Blocking state on a real player entity is driven by the client holding
        // right-click; for an NPC we approximate by setting the "blocking" flag
        // via Player#setItemInUseTicks is not exposed. Direct block-state
        // control over a non-real-client Player entity is a known Citizens
        // limitation - see README for shield-kit caveats.
    }

    public void lowerShield(CombatBot bot) {
        // See raiseShield() note.
    }
}
