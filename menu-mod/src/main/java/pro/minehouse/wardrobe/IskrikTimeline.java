package pro.minehouse.wardrobe;

import java.util.UUID;

/** Random-access controller for Iskrik's baked fN poses. Both mods compile this source. */
public final class IskrikTimeline {
	private static final int VISIT = 300, FLIGHT = 119;
	private static final int IDLE = 72, WAVE = 72, YAWN = 113, SPIT = 164, ORBIT = 210, REVERSE = 329;

	private IskrikTimeline() {
	}

	public static boolean matches(String id, int frames, int fps) {
		return "iskrik".equals(id) && frames == 448 && fps == 20;
	}

	public static int frame(UUID wearer, long gameTick) {
		long seed = mix(wearer.getMostSignificantBits()) ^ wearer.getLeastSignificantBits();
		// Two visits share 600 ticks. Lend up to 12 ticks from either visit to the other,
		// so landings vary but we can seek straight to the pair after hiding, joining or pausing.
		long time = gameTick + Math.floorMod(mix(seed), 4 * VISIT);
		long pair = Math.floorDiv(time, 2 * VISIT);
		int tick = Math.floorMod(time, 2 * VISIT);
		int split = VISIT - 12 + Math.floorMod(mix(seed + pair), 25);
		long visit = 2 * pair;
		int duration = split;
		if (tick >= split) {
			visit++;
			tick -= split;
			duration = 2 * VISIT - split;
		}

		// One volley on the second or third visit of each four: 3..5 visits between
		// volleys, including across groups. Otherwise wave, yawn or take the 26-tick nap.
		int fireVisit = 1 + (int) (mix(seed ^ Math.floorDiv(visit, 4) ^ 0xA0761D6478BD642FL) & 1);
		long draw = mix(seed + visit * 0x9E3779B97F4A7C15L);
		int pick = (int) ((draw >>> 32) % 100);
		int action = Math.floorMod(visit, 4) == fireVisit ? SPIT : pick < 47 ? WAVE : pick < 81 ? YAWN : 0;
		int takeoff = duration - FLIGHT;
		if (tick >= takeoff) return ((draw & 1) == 0 ? ORBIT : REVERSE) + tick - takeoff;
		if (action == 0) return tick % IDLE; // home, nap and ready all use the idle clip

		int length = switch (action) { case WAVE -> YAWN - WAVE; case YAWN -> SPIT - YAWN; default -> ORBIT - SPIT; };
		int rest = takeoff - length;
		// Redistribute the remaining rest without leaving either source range.
		int minHome = Math.max(56, rest - 64), maxHome = Math.min(104, rest - 34);
		int home = minHome + (int) ((draw >>> 1 & 0x7fffffffL) % (maxHome - minHome + 1));
		if (tick < home) return tick % IDLE;
		if (tick < home + length) return action + tick - home;
		return (tick - home - length) % IDLE;
	}

	/** SplitMix64 finalizer: stable logical draws keyed by wearer and visit, not render calls. */
	private static long mix(long x) {
		x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
		x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
		return x ^ (x >>> 31);
	}
}
