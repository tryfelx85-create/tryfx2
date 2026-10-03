package com.tryfx.pvpbot;

/**
 * States for the bot's combat state machine. The AI continually reassesses
 * which state it should be in every decision tick (see BotConfig.decisionIntervalTicks) -
 * these are not a rigid scripted sequence.
 */
public enum BotState {
    IDLE,              // no target, standing by
    SEARCHING,         // looking for a valid opponent
    APPROACHING,        // moving toward opponent, out of attack range
    ENGAGING,          // within range, deciding next action
    ATTACKING,         // performing a standard attack
    CRITICAL_ATTACK,   // performing a jump-timed critical attack
    STRAFING,          // sidestepping while maintaining distance/pressure
    COMBOING,          // chaining attacks after a successful hit
    RETREATING,        // backing off (too close, low health, or repositioning)
    HEALING,           // consuming a healing item
    DEFENDING,         // raising shield / blocking
    RECOVERING,        // post-action cooldown before re-engaging
    DEAD               // bot has died, awaiting respawn/removal
}
