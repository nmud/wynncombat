package io.github.nmud.wynncombat.client.combat;

/**
 * One row in the ability log. Costs are 0 when absent.
 *
 * @param spellName     the spell display name as printed by Wynncraft
 * @param manaCost      mana spent for the cast
 * @param healthCost    health spent for the cast
 * @param timestampMs   wall-clock millis when the cast was first observed
 */
public record AbilityLogEntry(String spellName, int manaCost, int healthCost, long timestampMs) {}
