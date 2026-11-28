package pro.minehouse.riftclient.vision;

import pro.minehouse.riftclient.cine.Mesh;
import pro.minehouse.riftclient.cine.Shapes;

/** The shared shapes of the vision, built once. */
final class Meshes implements AutoCloseable {
	final Mesh sphere, sphereLow, box, quad, pipe, pipeLow, lump, cross;

	Meshes() {
		Mesh.Builder b = new Mesh.Builder();
		Shapes.sphere(b, 64, 128);
		sphere = b.upload("sphere");
		b = new Mesh.Builder();
		Shapes.sphere(b, 10, 16);
		sphereLow = b.upload("small sphere");
		b = new Mesh.Builder();
		Shapes.box(b);
		box = b.upload("box");
		b = new Mesh.Builder();
		Shapes.quad(b);
		quad = b.upload("quad");
		b = new Mesh.Builder();
		Shapes.hollowTube(b, 28, 0.8f);
		pipe = b.upload("pipe");
		b = new Mesh.Builder();
		Shapes.hollowTube(b, 10, 0.72f);
		pipeLow = b.upload("small pipe");
		b = new Mesh.Builder();
		Shapes.lump(b, 48, 96, 0.12f, 3);
		lump = b.upload("lump");
		b = new Mesh.Builder();
		Shapes.cross(b);
		cross = b.upload("cross");
	}

	@Override
	public void close() {
		sphere.close();
		sphereLow.close();
		box.close();
		quad.close();
		pipe.close();
		pipeLow.close();
		lump.close();
		cross.close();
	}
}
