package io.github.nmud.wynncombat.client.recorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.nmud.wynncombat.WynnCombat;
import io.github.nmud.wynncombat.client.combat.SpellCastInfo;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Session-level state for the DPS recorder.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Start / stop a single live recording, with at most one active at a
 *       time. {@link #start()} is idempotent on an already-running recorder
 *       (returns {@code false}); ditto {@link #stop()}.</li>
 *   <li>Accept cast + damage events from
 *       {@link io.github.nmud.wynncombat.client.combat.AbilityLog} and
 *       {@link io.github.nmud.wynncombat.client.damage.DamageTracker} and
 *       funnel them into the current recording.</li>
 *   <li>On each client tick, close out any 1-second damage buckets that
 *       have rolled over. This is how {@link DpsRecording#secondDps} is
 *       built: one number per wall-clock second of the recording.</li>
 *   <li>Persist stopped recordings to
 *       {@code config/wynncombat-recordings/<id>.json} and expose a
 *       cached in-memory list to the UI.</li>
 * </ul>
 *
 * <p>All methods are safe to call from any thread. In practice everything
 * routes through the client / network thread so contention is effectively
 * zero, but the cast/damage events originate on the netty pipeline and UI
 * reads happen on the render thread, so synchronization stays.
 */
public final class DpsRecorder {
	private static final DpsRecorder INSTANCE = new DpsRecorder();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	public static DpsRecorder get() {
		return INSTANCE;
	}

	private final Object lock = new Object();

	private boolean recording = false;
	private long startMs = 0L;
	private long bucketStartMs = 0L;
	private long bucketDamage = 0L;
	private long totalDamage = 0L;
	private final List<Long> liveSeconds = new ArrayList<>();
	private final List<DpsRecording.CastEntry> liveCasts = new ArrayList<>();

	private List<DpsRecording> saved;

	private DpsRecorder() {}

	/** {@code true} iff a recording session is currently open. */
	public boolean isRecording() {
		synchronized (lock) {
			return recording;
		}
	}

	/** Elapsed ms into the active recording, or 0 if idle. */
	public long elapsedMs() {
		synchronized (lock) {
			return recording ? System.currentTimeMillis() - startMs : 0L;
		}
	}

	/** Number of casts observed since the active recording began, or 0. */
	public int liveCastCount() {
		synchronized (lock) {
			return liveCasts.size();
		}
	}

	/** Total damage dealt since the active recording began, or 0. */
	public long liveTotalDamage() {
		synchronized (lock) {
			return totalDamage;
		}
	}

	/**
	 * Begin a new recording. No-op if one is already running. Returns
	 * {@code true} if this call actually started a recording.
	 */
	public boolean start() {
		synchronized (lock) {
			if (recording) return false;
			recording = true;
			startMs = System.currentTimeMillis();
			bucketStartMs = startMs;
			bucketDamage = 0L;
			totalDamage = 0L;
			liveSeconds.clear();
			liveCasts.clear();
			return true;
		}
	}

	/**
	 * Finish the recording and persist it. Returns the built
	 * {@link DpsRecording}, or {@code null} if no recording was active.
	 */
	public DpsRecording stop() {
		DpsRecording built;
		synchronized (lock) {
			if (!recording) return null;
			long now = System.currentTimeMillis();
			flushBucketsTo(now);
			// Flush any residual partial bucket so total_damage == sum(seconds).
			if (bucketDamage > 0) {
				liveSeconds.add(bucketDamage);
				bucketDamage = 0;
			}
			recording = false;
			built = buildRecording(now);
		}
		persist(built);
		synchronized (lock) {
			getSavedLocked().add(0, built);
		}
		return built;
	}

	/**
	 * Cancel the active recording without persisting. Returns {@code true}
	 * if a recording was cancelled.
	 */
	public boolean cancel() {
		synchronized (lock) {
			if (!recording) return false;
			recording = false;
			liveSeconds.clear();
			liveCasts.clear();
			return true;
		}
	}

	/**
	 * Called from the client tick handler. Only does work while recording:
	 * closes out any 1-second buckets whose end has passed.
	 */
	public void tick() {
		synchronized (lock) {
			if (!recording) return;
			flushBucketsTo(System.currentTimeMillis());
		}
	}

	/** Called from {@code AbilityLog.record} for every observed cast. */
	public void onCast(SpellCastInfo info) {
		if (info == null) return;
		synchronized (lock) {
			if (!recording) return;
			long off = System.currentTimeMillis() - startMs;
			liveCasts.add(new DpsRecording.CastEntry(off, info.spellName(), info.manaCost(), info.healthCost()));
		}
	}

	/** Called from {@code DamageTracker.onEntityText} for every positive delta. */
	public void onDamage(long amount) {
		if (amount <= 0) return;
		synchronized (lock) {
			if (!recording) return;
			flushBucketsTo(System.currentTimeMillis());
			bucketDamage += amount;
			totalDamage += amount;
		}
	}

	private void flushBucketsTo(long now) {
		// Emit one second entry per 1000ms elapsed since the last emission.
		// Handles multi-second gaps (e.g. hitch on network thread) by
		// emitting consecutive zero buckets until we catch up.
		while (now - bucketStartMs >= 1000L) {
			liveSeconds.add(bucketDamage);
			bucketDamage = 0L;
			bucketStartMs += 1000L;
		}
	}

	private DpsRecording buildRecording(long endMs) {
		DpsRecording r = new DpsRecording();
		r.id = newId();
		r.startedAtMs = startMs;
		r.durationMs = Math.max(0L, endMs - startMs);
		r.casts = new ArrayList<>(liveCasts);
		r.secondDps = new ArrayList<>(liveSeconds);
		r.totalDamage = totalDamage;

		double seconds = Math.max(1.0, r.durationMs / 1000.0);
		r.avgDps = Math.round(totalDamage / seconds);

		long peak = 0L;
		int peakIdx = 0;
		for (int i = 0; i < r.secondDps.size(); i++) {
			long v = r.secondDps.get(i);
			if (v > peak) {
				peak = v;
				peakIdx = i;
			}
		}
		r.peakDps = peak;
		r.peakDpsSecond = peakIdx;

		List<String> spells = new ArrayList<>(r.casts.size());
		for (DpsRecording.CastEntry c : r.casts) {
			if (c != null && c.name != null) spells.add(c.name);
		}
		r.playerClass = WynnClass.inferFrom(spells).name();
		r.label = defaultLabel(r);
		return r;
	}

	private static String newId() {
		return Long.toString(System.currentTimeMillis(), 36)
			+ "-" + Long.toString((long) (Math.random() * 0x7FFFFFFF), 36);
	}

	private static String defaultLabel(DpsRecording r) {
		String when = ZonedDateTime.ofInstant(
			java.time.Instant.ofEpochMilli(r.startedAtMs), ZoneId.systemDefault()).format(LABEL_FMT);
		WynnClass cls = r.wynnClass();
		if (cls == WynnClass.UNKNOWN) return when;
		return when + " " + cls.displayName;
	}

	// ---------------------------------------------------------------- disk --

	/**
	 * Immutable snapshot of all saved recordings, newest first. The returned
	 * list is safe to iterate on the render thread.
	 */
	public List<DpsRecording> savedRecordings() {
		synchronized (lock) {
			return List.copyOf(getSavedLocked());
		}
	}

	/**
	 * Persist any config-side edits made to a recording (rename, etc.). If
	 * the recording isn't known to the recorder this is a no-op.
	 */
	public void persist(DpsRecording rec) {
		if (rec == null || rec.id == null) return;
		try {
			Path dir = recordingsDir();
			Files.createDirectories(dir);
			Path file = dir.resolve(rec.id + ".json");
			Files.writeString(file, GSON.toJson(rec), StandardCharsets.UTF_8);
		} catch (IOException e) {
			WynnCombat.LOGGER.error("Failed to save DPS recording", e);
		}
	}

	/** Delete a recording from disk and from the in-memory cache. */
	public boolean delete(DpsRecording rec) {
		if (rec == null || rec.id == null) return false;
		boolean removedFromDisk = false;
		try {
			Path file = recordingsDir().resolve(rec.id + ".json");
			removedFromDisk = Files.deleteIfExists(file);
		} catch (IOException e) {
			WynnCombat.LOGGER.warn("Failed to delete recording {}", rec.id, e);
		}
		synchronized (lock) {
			getSavedLocked().removeIf(r -> rec.id.equals(r.id));
		}
		return removedFromDisk;
	}

	private List<DpsRecording> getSavedLocked() {
		if (saved == null) {
			saved = loadAllRecordings();
		}
		return saved;
	}

	private static List<DpsRecording> loadAllRecordings() {
		List<DpsRecording> out = new ArrayList<>();
		Path dir = recordingsDir();
		if (!Files.exists(dir)) return out;
		try (Stream<Path> stream = Files.list(dir)) {
			stream.filter(p -> p.getFileName().toString().endsWith(".json"))
				.forEach(p -> {
					try {
						String json = Files.readString(p, StandardCharsets.UTF_8);
						DpsRecording r = GSON.fromJson(json, DpsRecording.class);
						if (r != null && r.id != null) out.add(r);
					} catch (IOException | RuntimeException e) {
						WynnCombat.LOGGER.warn("Skipping unreadable recording {}", p, e);
					}
				});
		} catch (IOException e) {
			WynnCombat.LOGGER.warn("Failed to list recordings", e);
		}
		// Newest first. We sort by startedAtMs rather than file mtime so a
		// backed-up / copied-in JSON appears in its logical position.
		out.sort(Comparator.comparingLong((DpsRecording r) -> r.startedAtMs).reversed());
		return out;
	}

	private static Path recordingsDir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config/wynncombat-recordings");
	}
}
