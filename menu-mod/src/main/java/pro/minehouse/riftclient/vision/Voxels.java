package pro.minehouse.riftclient.vision;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import pro.minehouse.riftclient.cine.BlockTex;
import pro.minehouse.riftclient.cine.Frame;
import pro.minehouse.riftclient.cine.Instances;
import pro.minehouse.riftclient.cine.Material;

/**
 * A little world of the game's blocks for a shot: set blocks by name ("stone", "grass_block", "oak_log"), then
 * {@link #build} keeps only the blocks that can be seen and {@link #draw} draws them with their textures.
 */
final class Voxels implements AutoCloseable {
	/** How a block looks: its side, top and bottom textures, a green top (grass), how much it glows, see-through. */
	record Kind(String side, String top, String bottom, boolean grassTop, float glow, boolean clear, float[] tint) {
		Kind(String side, String top, String bottom, boolean grassTop, float glow, boolean clear) {
			this(side, top, bottom, grassTop, glow, clear, null);
		}
	}

	private static final Map<String, Kind> KINDS = new HashMap<>();

	static {
		kind("grass_block", new Kind("grass_block_side", "grass_block_top", "dirt", true, 0, false));
		kind("snowy_grass", new Kind("grass_block_snow", "snow", "dirt", false, 0, false));
		kind("oak_log", new Kind("oak_log", "oak_log_top", "oak_log_top", false, 0, false));
		kind("basalt", new Kind("basalt_side", "basalt_top", "basalt_top", false, 0, false));
		kind("dirt_path", new Kind("dirt_path_side", "dirt_path_top", "dirt", false, 0, false));
		kind("sandstone", new Kind("sandstone", "sandstone_top", "sandstone_bottom", false, 0, false));
		kind("purpur_pillar", new Kind("purpur_pillar_side", "purpur_pillar_top", "purpur_pillar_top", false, 0, false));
		kind("sculk_catalyst", new Kind("sculk_catalyst_side_bloom", "sculk_catalyst_top_bloom", "sculk_catalyst_bottom", false, 0.6f, false));
		for (String g : new String[] { "glowstone", "sea_lantern", "shroomlight", "redstone_lamp_on", "lava_still", "magma", "cave_vines_lit",
				"cave_vines_plant_lit", "torch", "lantern", "beacon", "crying_obsidian", "end_rod", "amethyst_cluster" }) {
			kind(g, new Kind(g, g, g, false, g.startsWith("cave_vines") ? 0.5f : g.equals("crying_obsidian") ? 0.4f : 0.9f, false));
		}
		kind("water", new Kind("water_still", "water_still", "water_still", false, 0, false, new float[] { 0.25f, 0.42f, 0.85f }));
		kind("grass_all", new Kind("grass_block_top", "grass_block_top", "grass_block_top", false, 0, false, new float[] { 0.5f, 0.75f, 0.33f }));
		kind("oak_leaves", new Kind("oak_leaves", "oak_leaves", "oak_leaves", false, 0, true, new float[] { 0.36f, 0.62f, 0.22f }));
		kind("wheat", new Kind("wheat_stage7", "wheat_stage7", "wheat_stage7", false, 0, true));
		for (String c : new String[] { "glass", "azalea_leaves", "hanging_roots", "cave_vines", "cave_vines_plant", "spore_blossom" }) {
			kind(c, new Kind(c, c, c, false, 0, true));
		}
	}

	private static void kind(String name, Kind k) {
		KINDS.put(name, k);
	}

	static Kind kindOf(String name) {
		return KINDS.getOrDefault(name, new Kind(name, name, name, false, 0, false));
	}

	private final Map<Long, String> cells = new HashMap<>();
	/** The blocks that can be seen, one set per name. */
	final Map<String, Instances> built = new LinkedHashMap<>();
	/** Plants, roots and vines: two crossed squares in a block, one set per name. */
	final Map<String, Instances> plants = new LinkedHashMap<>();
	private final String label;

	Voxels(String label) {
		this.label = label;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}

	void set(int x, int y, int z, String name) {
		if (name == null) {
			cells.remove(key(x, y, z));
		} else {
			cells.put(key(x, y, z), name);
		}
	}

	String get(int x, int y, int z) {
		return cells.get(key(x, y, z));
	}

	/** A plant (crossed squares) in the block at x, y, z; it hides nothing. */
	void plant(int x, int y, int z, String name) {
		float[] r = BlockTex.rect(name);
		plants.computeIfAbsent(name, q -> new Instances(label + " " + q)).add(x + 0.5f, y + 0.5f, z + 0.5f, 1, new Quaternionf(), 1, 1, 1, r[0], r[1], r[2], r[3]);
	}

	int count() {
		return cells.size();
	}

	private boolean hides(int x, int y, int z) {
		String n = get(x, y, z);
		return n != null && !kindOf(n).clear();
	}

	/** Keeps the blocks with at least one open face, one unit per block, centred on the block's corner + 0.5. */
	Voxels build() {
		for (Instances i : built.values()) {
			i.close();
		}
		built.clear();
		Quaternionf none = new Quaternionf();
		for (Map.Entry<Long, String> e : cells.entrySet()) {
			long k = e.getKey();
			int x = (int) (k >> 42) << 11 >> 11, y = (int) ((k >> 21) & 0x1FFFFF) << 11 >> 11, z = (int) (k & 0x1FFFFF) << 11 >> 11;
			if (hides(x + 1, y, z) && hides(x - 1, y, z) && hides(x, y + 1, z) && hides(x, y - 1, z) && hides(x, y, z + 1) && hides(x, y, z - 1)) {
				continue;
			}
			String n = e.getValue();
			float[] r = BlockTex.rect(kindOf(n).side());
			built.computeIfAbsent(n, q -> new Instances(label + " " + q)).add(x + 0.5f, y + 0.5f, z + 0.5f, 1, none, 1, 1, 1, r[0], r[1], r[2], r[3]);
		}
		return this;
	}

	/** The material of a block: its textures, its glow; key: how much the film's light shapes it (0 = as in the game). */
	static Material material(String name, float key, float glow) {
		Kind k = kindOf(name);
		Material m = Material.solid(Material.BLOCK).grey(1).p(k.glow() * glow, key, k.grassTop() ? 1 : 0);
		if (k.tint() != null) {
			m.color(k.tint()[0], k.tint()[1], k.tint()[2]);
		}
		if (!k.top().equals(k.side()) || !k.bottom().equals(k.side())) {
			m.faces(BlockTex.rect(k.top()), BlockTex.rect(k.bottom()));
		}
		return m;
	}

	void draw(Frame f, Meshes m, Matrix4f model, float key, float glow, float brightness) {
		for (Map.Entry<String, Instances> e : built.entrySet()) {
			f.draw(m.box, e.getValue(), dim(material(e.getKey(), key, glow), brightness), model);
		}
		for (Map.Entry<String, Instances> e : plants.entrySet()) {
			f.draw(m.cross, e.getValue(), dim(material(e.getKey(), key, glow), brightness), model);
		}
	}

	/** Darker by this much, keeping the material's own tint. */
	static Material dim(Material m, float brightness) {
		return m.scale(brightness);
	}

	@Override
	public void close() {
		for (Instances i : built.values()) {
			i.close();
		}
		for (Instances i : plants.values()) {
			i.close();
		}
		built.clear();
		plants.clear();
	}
}
