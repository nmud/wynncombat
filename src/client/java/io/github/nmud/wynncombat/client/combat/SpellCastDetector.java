package io.github.nmud.wynncombat.client.combat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Wynncraft spell casts by parsing the action-bar payload.
 *
 * <p>Wynncraft renders a small "Cast!" banner inside the action bar whenever
 * the player completes a spell input combo. The banner has the shape:
 * <pre>{@code <spacer><spell name> Cast! -<cost1> <icon1>[ -<cost2> <icon2>]<spacer>}</pre>
 * where the spacer is a private-use supplementary code point (the surrogate
 * pair {@code \uDAFF\uDFDA} in UTF-16), and each cost icon is one of:
 * <ul>
 *   <li>{@code \uE531} - mana</li>
 *   <li>{@code \uE530} - health</li>
 * </ul>
 *
 * <p>The regex is taken directly from Wynntils
 * (<a href="https://github.com/Wynntils/Wynntils/blob/main/common/src/main/java/com/wynntils/models/spells/actionbar/matchers/SpellCastSegmentMatcher.java">SpellCastSegmentMatcher</a>);
 * we just skip the surrogate-boundary sanity check because the literal
 * {@code Cast!} + private-use icon combo is already distinctive enough that
 * false positives in regular Wynncraft chat are not a concern.
 *
 * <p>This class is purely a pure-function parser. Callers are responsible for
 * deduplication (the cast banner stays in the action bar for ~3s and is
 * re-broadcast on every action-bar tick).
 */
public final class SpellCastDetector {
	private static final String MANA_ICON = "\uE531";
	private static final String HEALTH_ICON = "\uE530";

	private static final Pattern SPELL_REGEX = Pattern.compile(
		".(?<spellName>[A-Za-z ]+?) Cast!"
			+ "(?: -(?<costOne>[0-9]+) ?(?<costOneIcon>" + MANA_ICON + "|" + HEALTH_ICON + "))?"
			+ "(?: -(?<costTwo>[0-9]+) ?(?<costTwoIcon>" + MANA_ICON + "|" + HEALTH_ICON + "))?"
			+ "."
	);

	private SpellCastDetector() {}

	/**
	 * Parse a flattened action-bar string. Returns {@code null} if no cast
	 * banner is present.
	 */
	public static SpellCastInfo parse(String actionBar) {
		if (actionBar == null || actionBar.isEmpty()) return null;

		Matcher m = SPELL_REGEX.matcher(actionBar);
		if (!m.find()) return null;

		int mana = 0;
		int health = 0;

		String costOne = m.group("costOne");
		if (costOne != null) {
			int v = parseIntSafe(costOne);
			if (MANA_ICON.equals(m.group("costOneIcon"))) {
				mana = v;
			} else {
				health = v;
			}
		}

		String costTwo = m.group("costTwo");
		if (costTwo != null) {
			int v = parseIntSafe(costTwo);
			if (MANA_ICON.equals(m.group("costTwoIcon"))) {
				mana = v;
			} else {
				health = v;
			}
		}

		String name = m.group("spellName");
		if (name == null) return null;
		name = name.trim();
		if (name.isEmpty()) return null;

		return new SpellCastInfo(name, mana, health);
	}

	private static int parseIntSafe(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}
}
