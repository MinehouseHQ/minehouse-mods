package pro.minehouse.menu.wardrobe;

/** Mount of the rendered pose, independent of the HAT wardrobe slot. Boundaries are the
 * half-height crossings (the Alex-fit track grazes the line once on each climb, so 285 and 974
 * are single head frames) of the baked showcase mount track (1378 slots); test_karat_mount.py
 * verifies both modules against every source sample. Reuse the frame placed on the stack. */
public final class KaratMount {
	private KaratMount() {}

	public static boolean onShoulder(String id, int frame) {
		return "karat".equals(id) && frame >= 0 && frame < 1378
				&& (frame < 285 || frame == 286 || (frame >= 652 && frame < 974) || frame == 975 || frame >= 1371);
	}
}
