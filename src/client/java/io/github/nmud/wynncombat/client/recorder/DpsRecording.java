package io.github.nmud.wynncombat.client.recorder;

import java.util.ArrayList;
import java.util.List;

/**
 * One completed DPS recording. Persisted to
 * {@code config/wynncombat-recordings/<id>.json} as-is via Gson, so every
 * field here is part of the on-disk format; think twice before renaming.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #id}: unique per-recording (see {@link DpsRecorder#newId}).</li>
 *   <li>{@link #label}: user-editable name, shown in the list and viewer.</li>
 *   <li>{@link #startedAtMs} / {@link #durationMs}: wall-clock bounds.</li>
 *   <li>{@link #playerClass}: stored as {@link WynnClass#name()} so unknown
 *       enum values from older versions don't explode Gson.</li>
 *   <li>{@link #casts}: every spell cast during the recording, with its
 *       offset from {@link #startedAtMs}.</li>
 *   <li>{@link #secondDps}: damage-per-bucket, one bucket per second. The
 *       length is the number of full seconds that elapsed during recording.</li>
 *   <li>{@link #totalDamage}, {@link #avgDps}, {@link #peakDps},
 *       {@link #peakDpsSecond}: derived stats computed at stop time so the
 *       viewer doesn't have to recompute.</li>
 * </ul>
 */
public final class DpsRecording {
	public String id;
	public String label;
	public long startedAtMs;
	public long durationMs;
	public String playerClass;
	public List<CastEntry> casts = new ArrayList<>();
	public List<Long> secondDps = new ArrayList<>();
	public long totalDamage;
	public long avgDps;
	public long peakDps;
	public int peakDpsSecond;

	public WynnClass wynnClass() {
		return WynnClass.parse(playerClass);
	}

	public int secondCount() {
		return secondDps == null ? 0 : secondDps.size();
	}

	/** One recorded cast. Mana / hp are per-cast costs, not session totals. */
	public static final class CastEntry {
		public long offsetMs;
		public String name;
		public int mana;
		public int hp;

		public CastEntry() {}

		public CastEntry(long offsetMs, String name, int mana, int hp) {
			this.offsetMs = offsetMs;
			this.name = name;
			this.mana = mana;
			this.hp = hp;
		}
	}
}
