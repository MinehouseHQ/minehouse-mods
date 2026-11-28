package pro.minehouse.riftclient.cine;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** One frame of a film being put together: the camera, the light, the look, and what to draw. */
public final class Frame {
	record Draw(Mesh mesh, Instances instances, Material material, Matrix4f model) {
	}

	final List<Draw> draws = new ArrayList<>();
	final Vector3f eye = new Vector3f(0, 0, 5);
	final Vector3f target = new Vector3f();
	final Vector3f up = new Vector3f(0, 1, 0);
	float fov = 40;
	float near = 0.05f, far = 4000;
	final Vector3f lightDir = new Vector3f(-0.4f, 0.7f, 0.6f);
	float backLight = 0.5f;
	final Vector4f fog = new Vector4f(0, 0, 0, 0);
	final Vector4f clear = new Vector4f(0, 0, 0, 1);
	/** exposure, bloom, grain, vignette */
	final Vector4f look = new Vector4f(1, 0.6f, 0.045f, 0.55f);
	/** letterbox aspect, black, zoom blur, bloom threshold */
	final Vector4f framing = new Vector4f(2.39f, 0, 0, 0.6f);
	/** zoom blur centre x, y, lift of the blacks, spare */
	final Vector4f aim = new Vector4f(0.5f, 0.5f, 0, 0);
	float white;
	/** How much of the real colours shows through the steel grade (0 = all steel). */
	float colour;
	/** focus distance (0 = all sharp), blur per unit of defocus in pixels at 1080p, largest blur */
	final Vector4f focus = new Vector4f(0, 0, 0, 0);
	float shotSeconds;
	float filmSeconds;
	final Matrix4f view = new Matrix4f();

	void reset(float seconds) {
		draws.clear();
		eye.set(0, 0, 5);
		target.set(0);
		up.set(0, 1, 0);
		fov = 40;
		near = 0.05f;
		far = 4000;
		lightDir.set(-0.4f, 0.7f, 0.6f);
		backLight = 0.5f;
		fog.set(0, 0, 0, 0);
		clear.set(0, 0, 0, 100000);
		look.set(1, 0.6f, 0.045f, 0.55f);
		framing.set(2.39f, 0, 0, 0.6f);
		aim.set(0.5f, 0.5f, 0, 0);
		white = 0;
		colour = 0;
		focus.set(0, 0, 0, 0);
		clear.w = 100000;
		shotSeconds = seconds;
		filmSeconds = seconds;
	}

	public Frame camera(Vector3f eye, Vector3f target, float fov) {
		this.eye.set(eye);
		this.target.set(target);
		this.fov = fov;
		return this;
	}

	public Frame camera(float ex, float ey, float ez, float tx, float ty, float tz, float fov) {
		eye.set(ex, ey, ez);
		target.set(tx, ty, tz);
		this.fov = fov;
		return this;
	}

	/** Tilts the camera round its line of sight, in degrees. */
	public Frame roll(float degrees) {
		Vector3f fwd = new Vector3f(target).sub(eye).normalize();
		up.set(0, 1, 0);
		if (Math.abs(fwd.y) > 0.98f) {
			up.set(0, 0, -1);
		}
		up.rotateAxis((float) Math.toRadians(degrees), fwd.x, fwd.y, fwd.z);
		return this;
	}

	public Frame up(float x, float y, float z) {
		up.set(x, y, z);
		return this;
	}

	public Frame clip(float near, float far) {
		this.near = near;
		this.far = far;
		return this;
	}

	/** Where the key light comes from (in the world), and how strong the light from behind is. */
	public Frame light(float x, float y, float z, float back) {
		lightDir.set(x, y, z).normalize();
		backLight = back;
		return this;
	}

	public Frame fog(float grey, float density) {
		fog.set(grey, grey, grey, density);
		return this;
	}

	public Frame background(float grey) {
		clear.set(grey, grey, grey, 100000);
		return this;
	}

	public Frame exposure(float e) {
		look.x = e;
		return this;
	}

	public Frame bloom(float strength, float threshold) {
		look.y = strength;
		framing.w = threshold;
		return this;
	}

	public Frame vignette(float v) {
		look.w = v;
		return this;
	}

	public Frame black(float b) {
		framing.y = Math.max(framing.y, b);
		return this;
	}

	public Frame white(float w) {
		white = Math.max(white, w);
		return this;
	}

	/** Pulls the picture towards a point on the screen (0..1), as a fast zoom does. */
	public Frame zoomBlur(float amount, float x, float y) {
		framing.z = amount;
		aim.x = x;
		aim.y = y;
		return this;
	}

	/** A macro lens: sharp at this distance, soft before and behind it. */
	public Frame focus(float distance, float strength, float largest) {
		focus.set(distance, strength, largest, 0);
		return this;
	}

	/** Lets the real colours through the steel grade: 0 = all steel (the default), 1 = the colours as they are. */
	public Frame colour(float c) {
		colour = Math.max(0, Math.min(1, c));
		return this;
	}

	public Frame lift(float l) {
		aim.z = l;
		return this;
	}

	/** Seconds of the current shot, for materials that move by themselves. */
	public Frame shotTime(float s) {
		shotSeconds = s;
		return this;
	}

	public Frame draw(Mesh mesh, Instances instances, Material material, Matrix4f model) {
		draws.add(new Draw(mesh, instances, material, model == null ? new Matrix4f() : model));
		return this;
	}

	public Frame draw(Mesh mesh, Instances instances, Material material) {
		return draw(mesh, instances, material, null);
	}

	public Vector3f eye() {
		return eye;
	}
}
