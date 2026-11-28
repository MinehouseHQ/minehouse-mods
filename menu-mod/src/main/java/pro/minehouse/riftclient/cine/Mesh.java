package pro.minehouse.riftclient.cine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** A shape on the graphics card: triangles of points that know which way they face. */
public final class Mesh implements AutoCloseable {
	final GpuBuffer vertices;
	final GpuBuffer indices;
	final int count;

	private Mesh(GpuBuffer vertices, GpuBuffer indices, int count) {
		this.vertices = vertices;
		this.indices = indices;
		this.count = count;
	}

	@Override
	public void close() {
		vertices.close();
		indices.close();
	}

	/** Builds a mesh point by point. */
	public static final class Builder {
		private final FloatArrayList v = new FloatArrayList();
		private final IntArrayList i = new IntArrayList();

		public int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float w) {
			v.add(x);
			v.add(y);
			v.add(z);
			v.add(nx);
			v.add(ny);
			v.add(nz);
			v.add(u);
			v.add(w);
			return v.size() / 8 - 1;
		}

		public int vertex(Vector3f p, Vector3f n, float u, float w) {
			return vertex(p.x, p.y, p.z, n.x, n.y, n.z, u, w);
		}

		public void tri(int a, int b, int c) {
			i.add(a);
			i.add(b);
			i.add(c);
		}

		public void quad(int a, int b, int c, int d) {
			tri(a, b, c);
			tri(a, c, d);
		}

		public int vertices() {
			return v.size() / 8;
		}

		public Mesh upload(String label) {
			ByteBuffer vb = MemoryUtil.memAlloc(v.size() * 4).order(ByteOrder.nativeOrder());
			ByteBuffer ib = MemoryUtil.memAlloc(Math.max(1, i.size()) * 4).order(ByteOrder.nativeOrder());
			try {
				for (int k = 0; k < v.size(); k++) {
					vb.putFloat(v.getFloat(k));
				}
				for (int k = 0; k < i.size(); k++) {
					ib.putInt(i.getInt(k));
				}
				if (i.isEmpty()) {
					ib.putInt(0);
				}
				vb.flip();
				ib.flip();
				GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "MineHouse film " + label + " vertices", GpuBuffer.USAGE_VERTEX, vb);
				GpuBuffer indices = RenderSystem.getDevice().createBuffer(() -> "MineHouse film " + label + " indices", GpuBuffer.USAGE_INDEX, ib);
				return new Mesh(vertices, indices, i.size());
			} finally {
				MemoryUtil.memFree(vb);
				MemoryUtil.memFree(ib);
			}
		}
	}
}
