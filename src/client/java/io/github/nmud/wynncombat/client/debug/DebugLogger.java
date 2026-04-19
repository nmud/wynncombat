package io.github.nmud.wynncombat.client.debug;

import io.github.nmud.wynncombat.WynnCombat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Diagnostic logger for inspecting Wynncraft server output. When enabled, every
 * captured event is written to {@code logs/wynncombat-debug.log} in the game
 * directory. Non-printable / non-ASCII characters are escaped as {@code \\uXXXX}
 * so we can see exactly what code points the server sends (for example the
 * U+DB00 U+DC0A separator Wynncraft uses inside damage labels).
 */
public final class DebugLogger {
	private static final DebugLogger INSTANCE = new DebugLogger();

	private static final DateTimeFormatter TIMESTAMP =
		DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private final Object lock = new Object();
	private volatile boolean enabled = false;
	private BufferedWriter writer;

	private DebugLogger() {}

	public static DebugLogger get() {
		return INSTANCE;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Toggle and return the new state. */
	public boolean toggle() {
		synchronized (lock) {
			if (enabled) {
				close();
				enabled = false;
			} else {
				try {
					open();
					enabled = true;
				} catch (IOException e) {
					WynnCombat.LOGGER.error("Failed to open debug log", e);
					return false;
				}
			}
			return enabled;
		}
	}

	public void log(String category, String message) {
		if (!enabled) return;

		String line = "[" + LocalDateTime.now().format(TIMESTAMP) + "] "
			+ category + " " + message + System.lineSeparator();

		synchronized (lock) {
			if (writer == null) return;
			try {
				writer.write(line);
				writer.flush();
			} catch (IOException e) {
				WynnCombat.LOGGER.error("Failed to write debug log line", e);
			}
		}
	}

	/**
	 * Escape any character that isn't printable ASCII as {@code \\uXXXX}. Tabs,
	 * carriage returns, and newlines are also escaped so each event stays on a
	 * single log line.
	 */
	public static String escape(String input) {
		if (input == null) return "null";
		StringBuilder sb = new StringBuilder(input.length() + 16);
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c >= 0x20 && c <= 0x7E && c != '\\') {
				sb.append(c);
			} else if (c == '\\') {
				sb.append("\\\\");
			} else {
				sb.append(String.format("\\u%04X", (int) c));
			}
		}
		return sb.toString();
	}

	/**
	 * Walk the component tree and emit each styled segment prefixed with the
	 * matching legacy {@code §X} color code (or {@code §r} when style resets).
	 * This lets us see whether a damage label like {@code "645"} was actually
	 * styled red (fire), yellow (thunder), green (earth), etc., even when the
	 * server uses {@link Style#withColor(TextColor)} instead of embedding raw
	 * section signs in the text.
	 *
	 * <p>The output is then run through {@link #escape(String)} so non-ASCII
	 * code points (including the section sign itself) become {@code \\uXXXX}.
	 */
	public static String styled(Component component) {
		if (component == null) return "null";
		StringBuilder sb = new StringBuilder();
		int[] lastColor = {Integer.MIN_VALUE};
		component.visit((style, content) -> {
			int color = -1;
			TextColor c = style.getColor();
			if (c != null) color = c.getValue();
			if (color != lastColor[0]) {
				ChatFormatting fmt = (c == null) ? null : ChatFormatting.getByName(c.serialize());
				if (fmt == null || fmt.getColor() == null) {
					sb.append("§r");
				} else {
					sb.append('§').append(fmt.getChar());
				}
				lastColor[0] = color;
			}
			sb.append(content);
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private void open() throws IOException {
		Minecraft mc = Minecraft.getInstance();
		Path logDir = mc.gameDirectory.toPath().resolve("logs");
		Files.createDirectories(logDir);
		Path logFile = logDir.resolve("wynncombat-debug.log");

		writer = Files.newBufferedWriter(
			logFile,
			StandardCharsets.UTF_8,
			StandardOpenOption.CREATE,
			StandardOpenOption.APPEND
		);

		String header = "===== wynncombat debug session started at "
			+ LocalDateTime.now() + " =====" + System.lineSeparator();
		writer.write(header);
		writer.flush();

		WynnCombat.LOGGER.info("WynnCombat debug logging enabled, writing to {}", logFile);
	}

	private void close() {
		if (writer == null) return;
		try {
			writer.write("===== wynncombat debug session ended at "
				+ LocalDateTime.now() + " =====" + System.lineSeparator());
			writer.flush();
			writer.close();
		} catch (IOException e) {
			WynnCombat.LOGGER.error("Failed to close debug log", e);
		} finally {
			writer = null;
		}
		WynnCombat.LOGGER.info("WynnCombat debug logging disabled");
	}
}
