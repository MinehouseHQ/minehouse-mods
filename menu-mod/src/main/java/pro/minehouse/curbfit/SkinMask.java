package pro.minehouse.curbfit;

import com.mojang.blaze3d.platform.NativeImage;

/** Copy alpha only; never retain or close Minecraft's dynamic skin image. */
final class SkinMask {
    static final SkinMask EMPTY = new SkinMask(new long[64]);
    private final long[] rows;
    private SkinMask(long[] rows) { this.rows = rows; }

    static SkinMask from(NativeImage image) {
        if (image == null || image.isClosed() || image.getWidth() != 64 || image.getHeight() != 64
                || image.format() != NativeImage.Format.RGBA) return EMPTY;
        long[] rows = new long[64];
        for (int y=0; y<64; y++) for (int x=0; x<64; x++)
            if (image.getLuminanceOrAlpha(x,y) == (byte)255) rows[y] |= 1L << x;
        return new SkinMask(rows);
    }
    boolean opaque(int x, int y) { return (rows[y] & (1L << x)) != 0; }
}
