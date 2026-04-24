package io.github.nmud.wynncombat.client.recorder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The five Wynncraft player archetypes, plus an {@link #UNKNOWN} fallback.
 *
 * <p>We have no first-class network or packet signal for "what class is the
 * player". Wynncraft puts the info on the held weapon's tooltip and in a few
 * ability icons, but both require scraping item lore or textures.
 * Spell names are a much cleaner heuristic: each class has a unique set of
 * four spells, and during a recording we'll typically see several of them.
 *
 * <p>So we infer via {@link #inferFrom(List)} - tally how many casts from the
 * recording match each class and pick the winner. A short recording with
 * zero identifiable casts just yields {@link #UNKNOWN}, which is a better
 * outcome than guessing wrong.
 */
public enum WynnClass {
	UNKNOWN("Unknown"),
	WARRIOR("Warrior"),
	MAGE("Mage"),
	ARCHER("Archer"),
	ASSASSIN("Assassin"),
	SHAMAN("Shaman");

	public final String displayName;

	WynnClass(String displayName) {
		this.displayName = displayName;
	}

	private static final Map<String, WynnClass> SPELL_TO_CLASS = buildSpellMap();

	private static Map<String, WynnClass> buildSpellMap() {
		Map<String, WynnClass> m = new java.util.HashMap<>();
		// Warrior.
		m.put("Bash", WARRIOR);
		m.put("Charge", WARRIOR);
		m.put("Uppercut", WARRIOR);
		m.put("War Scream", WARRIOR);
		// Mage.
		m.put("Heal", MAGE);
		m.put("Teleport", MAGE);
		m.put("Meteor", MAGE);
		m.put("Ice Snake", MAGE);
		// Archer.
		m.put("Arrow Storm", ARCHER);
		m.put("Escape", ARCHER);
		m.put("Arrow Shield", ARCHER);
		m.put("Arrow Bomb", ARCHER);
		// Assassin.
		m.put("Spin Attack", ASSASSIN);
		m.put("Vanish", ASSASSIN);
		m.put("Multihit", ASSASSIN);
		m.put("Smoke Bomb", ASSASSIN);
		// Shaman.
		m.put("Totem", SHAMAN);
		m.put("Haul", SHAMAN);
		m.put("Aura", SHAMAN);
		m.put("Uproot", SHAMAN);
		return m;
	}

	public static WynnClass forSpell(String spellName) {
		if (spellName == null) return UNKNOWN;
		return SPELL_TO_CLASS.getOrDefault(spellName, UNKNOWN);
	}

	/**
	 * Best-guess class from a series of cast spell names. Picks the class
	 * with the most matches; ties go to the first-encountered winner.
	 */
	public static WynnClass inferFrom(List<String> spellNames) {
		if (spellNames == null || spellNames.isEmpty()) return UNKNOWN;
		Map<WynnClass, Integer> counts = new EnumMap<>(WynnClass.class);
		for (String s : spellNames) {
			WynnClass c = forSpell(s);
			if (c == UNKNOWN) continue;
			counts.merge(c, 1, Integer::sum);
		}
		WynnClass best = UNKNOWN;
		int bestCount = 0;
		for (Map.Entry<WynnClass, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestCount) {
				best = e.getKey();
				bestCount = e.getValue();
			}
		}
		return best;
	}

	/** Safe parse from a stored string (recording file). */
	public static WynnClass parse(String name) {
		if (name == null) return UNKNOWN;
		try {
			return WynnClass.valueOf(name);
		} catch (IllegalArgumentException e) {
			return UNKNOWN;
		}
	}
}
