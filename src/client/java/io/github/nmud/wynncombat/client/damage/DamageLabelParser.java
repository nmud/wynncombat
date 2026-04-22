package io.github.nmud.wynncombat.client.damage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Wynncraft floating damage labels and returns the total damage
 * amount. Labels look like <code>§c1.2k§2500</code> (fire 1200 + earth 500),
 * possibly with additional fluff color codes between parts and with a
 * private-use separator <code>U+DB00 U+DC0A</code> between damage segments.
 *
 * <p>The regex is adapted directly from Wynntils
 * (<a href="https://github.com/Wynntils/Wynntils/blob/main/common/src/main/java/com/wynntils/models/combat/label/DamageLabelParser.java">DamageLabelParser</a>).
 * We ignore the per-type breakdown and just sum everything into one long,
 * which is all the DPS counter needs.
 */
public final class DamageLabelParser {
	private static final String FMT_NOISE = "(?:\u00a7\\{[^}]*}|\u00a7.)*";
	private static final String TYPE_COLOR = "\u00a7([245bcef])";
	private static final String NUMBER = "(\\d+(?:\\.\\d+)?)";
	private static final String SUFFIX = "([kKmMbB]?)";
	private static final String SEP_OR_END = "(?:\uDB00\uDC0A|$)";

	private static final String DAMAGE_LABEL_PART =
		FMT_NOISE + TYPE_COLOR + FMT_NOISE + NUMBER + SUFFIX + FMT_NOISE + SEP_OR_END;

	private static final Pattern DAMAGE_LABEL_PATTERN =
		Pattern.compile("(?:" + DAMAGE_LABEL_PART + ")+");
	private static final Pattern DAMAGE_LABEL_PART_PATTERN =
		Pattern.compile(DAMAGE_LABEL_PART);

	private DamageLabelParser() {}

	/**
	 * Returns the summed damage of every segment in {@code legacyStyledText},
	 * or {@code -1} if the text is not a damage label at all. {@code -1}
	 * rather than 0 so callers can distinguish "not a label" from "0 damage".
	 *
	 * @param legacyStyledText a legacy formatted string (uses {@code §} codes)
	 *                         as produced by walking a {@link net.minecraft.network.chat.Component}
	 *                         with {@code DebugLogger.styled}.
	 */
	public static long parseTotal(String legacyStyledText) {
		if (legacyStyledText == null || legacyStyledText.isEmpty()) return -1L;

		Matcher matcher = DAMAGE_LABEL_PATTERN.matcher(legacyStyledText);
		if (!matcher.matches()) return -1L;

		long total = 0L;
		Matcher partMatcher = DAMAGE_LABEL_PART_PATTERN.matcher(legacyStyledText);
		while (partMatcher.find()) {
			String numberStr = partMatcher.group(2);
			String suffixStr = partMatcher.group(3);
			total += parseAbbreviated(numberStr, suffixStr);
		}
		return total;
	}

	/** {@code 1.5k -> 1500}, {@code 2m -> 2_000_000}, etc. */
	private static long parseAbbreviated(String number, String suffix) {
		double value;
		try {
			value = Double.parseDouble(number);
		} catch (NumberFormatException e) {
			return 0L;
		}
		if (suffix == null || suffix.isEmpty()) return Math.round(value);
		char c = Character.toLowerCase(suffix.charAt(0));
		return switch (c) {
			case 'k' -> Math.round(value * 1_000d);
			case 'm' -> Math.round(value * 1_000_000d);
			case 'b' -> Math.round(value * 1_000_000_000d);
			default -> Math.round(value);
		};
	}
}
