package pro.minehouse.riftclient.cine;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * The game's block textures for a film: where a texture sits in the block atlas (for {@link Material#BLOCK}) and
 * its pixels (for building it out of {@link Material#PIXEL} cubes). Names are the files in textures/block,
 * like "stone" or "grass_block_top"; a texture of a resource pack wins, as in the game.
 */
public final class BlockTex {
	private static final Map<String, float[]> RECTS = new HashMap<>();
	private static final Map<String, int[]> PIXELS = new HashMap<>();

	private BlockTex() {
	}

	/** u0, v0, u1, v1 of the texture in the block atlas. */
	public static float[] rect(String name) {
		return RECTS.computeIfAbsent(name, n -> {
			TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
			TextureAtlasSprite s = atlas.getSprite(Identifier.withDefaultNamespace("block/" + n));
			return new float[] { s.getU0(), s.getV0(), s.getU1(), s.getV1() };
		});
	}

	/** A picture of the game: its size and its pixels, ARGB, row by row from the top. */
	public record Image(int width, int height, int[] argb) {
		public int at(int x, int y) {
			return argb[Math.floorMod(y, height) * width + Math.floorMod(x, width)];
		}
	}

	private static final Map<String, Image> IMAGES = new HashMap<>();

	/**
	 * Any texture of the game by its path under assets/minecraft, like "textures/item/ender_eye.png". One that
	 * cannot be read comes back as one grey pixel.
	 */
	public static Image image(String path) {
		return IMAGES.computeIfAbsent(path, p -> {
			Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace(p));
			if (r.isPresent()) {
				try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
					int w = img.getWidth(), h = img.getHeight();
					int[] px = new int[w * h];
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							px[y * w + x] = img.getPixel(x, y);
						}
					}
					return new Image(w, h, px);
				} catch (Exception e) {
					// grey it is
				}
			}
			return new Image(1, 1, new int[] { 0xFF808080 });
		});
	}

	/** The 16×16 pixels of a block texture (the first frame of one that moves), ARGB, row by row from the top. */
	public static int[] pixels(String name) {
		return PIXELS.computeIfAbsent(name, n -> {
			Image img = image("textures/block/" + n + ".png");
			int w = img.width();
			int[] out = new int[256];
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					out[y * 16 + x] = img.at(x * w / 16, y * w / 16);
				}
			}
			return out;
		});
	}

	/** Puts the texture's rectangle into the four numbers of an instance: {@code inst.add(..., r[0], r[1], r[2], r[3])}. */
	public static Instances block(Instances inst, float x, float y, float z, float size, String name) {
		float[] r = rect(name);
		return inst.add(x, y, z, size, new org.joml.Quaternionf(), 1, 1, 1, r[0], r[1], r[2], r[3]);
	}
}
