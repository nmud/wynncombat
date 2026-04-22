package io.github.nmud.wynncombat.client.combat;

/**
 * One row in the ability log. Costs are 0 when absent.
 *
 * <p>Consecutive casts of the same spell are merged into a single entry by
 * {@link AbilityLog}: {@link #stackCount} goes up by one per extra cast, and
 * {@link #manaCost} / {@link #healthCost} hold the running sum so the overlay
 * can render "Bash x3 -63 mana" without re-aggregating.
 *
 * @param spellName     the spell display name as printed by Wynncraft
 * @param manaCost      total mana spent across all merged casts
 * @param healthCost    total health spent across all merged casts
 * @param timestampMs   wall-clock millis of the most recent observed cast
 * @param stackCount    how many casts are merged into this row (&ge;1)
 */
public record AbilityLogEntry(
	String spellName,
	int manaCost,
	int healthCost,
	long timestampMs,
	int stackCount
) {
	public AbilityLogEntry(String spellName, int manaCost, int healthCost, long timestampMs) {
		this(spellName, manaCost, healthCost, timestampMs, 1);
	}
}
