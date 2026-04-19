package io.github.nmud.wynncombat.client.combat;

/**
 * One parsed Wynncraft spell-cast banner. Costs are 0 when not present in the
 * banner (most spells only have a mana cost; a few -- like Mage's Heal at low
 * mana -- can also or instead consume health).
 */
public record SpellCastInfo(String spellName, int manaCost, int healthCost) {}
