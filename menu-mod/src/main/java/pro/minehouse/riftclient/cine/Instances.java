package pro.minehouse.riftclient.cine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.joml.Quaternionf;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

/**
 * Copies of one mesh: each with its place, turn, stretch and four numbers for the material (brightness, a seed...).
 * Fixed sets are sent to the graphics card once; changing ones are sent again when they change.
 */
public final class Instances implements AutoCloseable {
	static final int FLOATS = 16;
	private final FloatArrayList data = new FloatArrayList();
	private final String label;
	private GpuBuffer buffer;
	private boolean dirty = true;
	private int uploaded;

	public Instances(String label) {
		this.label = label;
	}

	public Instances clear() {
		data.clear();
		dirty = true;
		return this;
	}

	public int size() {
		return data.size() / FLOATS;
	}

	public Instances add(float x, float y, float z, float size, Quaternionf rot, float sx, float sy, float sz, float d0, float d1, float d2, float d3) {
		data.add(x);
		data.add(y);
		data.add(z);
		data.add(size);
		data.add(rot.x);
		data.add(rot.y);
		data.add(rot.z);
		data.add(rot.w);
		data.add(sx);
		data.add(sy);
		data.add(sz);
		data.add(0);
		data.add(d0);
		data.add(d1);
		data.add(d2);
		data.add(d3);
		dirty = true;
		return this;
	}

	public Instances add(float x, float y, float z, float size, float d0, float d1) {
		return add(x, y, z, size, IDENTITY, 1, 1, 1, d0, d1, 0, 0);
	}

	static final Quaternionf IDENTITY = new Quaternionf();

	GpuBuffer upload() {
		int n = Math.max(1, size());
		if (buffer == null || buffer.isClosed() || uploaded < n) {
			if (buffer != null && !buffer.isClosed()) {
				buffer.close();
			}
			int cap = Math.max(n, uploaded * 2);
			buffer = RenderSystem.getDevice().createBuffer(() -> "MineHouse film " + label + " instances", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) cap * FLOATS * 4);
			uploaded = cap;
			dirty = true;
		}
		if (dirty) {
			ByteBuffer bb = MemoryUtil.memAlloc(Math.max(1, data.size()) * 4).order(ByteOrder.nativeOrder());
			try {
				if (data.isEmpty()) {
					bb.putFloat(0);
				}
				for (int k = 0; k < data.size(); k++) {
					bb.putFloat(data.getFloat(k));
				}
				bb.flip();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, bb.remaining()), bb);
			} finally {
				MemoryUtil.memFree(bb);
			}
			dirty = false;
		}
		return buffer;
	}

	@Override
	public void close() {
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
	}
}
