package pro.minehouse.menu.explainer;

import static pro.minehouse.menu.explainer.Explainer.FRONT;
import static pro.minehouse.menu.explainer.Explainer.HALF;
import static pro.minehouse.menu.explainer.Explainer.LEFT;
import static pro.minehouse.menu.explainer.Explainer.RIGHT;
import static pro.minehouse.menu.explainer.Explainer.during;
import static pro.minehouse.menu.explainer.Explainer.ease;
import static pro.minehouse.menu.explainer.Explainer.hurtAt;
import static pro.minehouse.menu.explainer.Explainer.move;
import static pro.minehouse.menu.explainer.Explainer.swingAt;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityTypes;

import pro.minehouse.menu.explainer.Explainer.Cast;
import pro.minehouse.menu.explainer.Explainer.Scene;

/** The scenes of the second batch of hall events (MineHouse End {@code MoreRoomEvents}, 27.11). */
final class MoreScenes {
	private MoreScenes() {
	}

	/** «Лёд»: the floor turns to ice, everyone slides; strike in short steps. */
	static final class Ice implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.2f, 4.2f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Пол становится льдом", "The floor turns to ice"},
					{"Скользят все: и ты, и мобы", "Everyone slides, mobs too"},
					{"Бей короткими шагами", "Strike in short steps"}};
		}

		public void play(Cast c, float t) {
			c.floor(x -> t >= 0.6f + (x + HALF) * 0.08f ? "minecraft:packed_ice" : null);
			// you slide past where you meant to stop, then come back
			double px = move(t, 1.2f, 2.4f, -2.4f, 0.4f) - move(t, 2.6f, 3.4f, 0, 1.6f);
			AvatarRenderState you = c.you(px, 0.4, RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.walk(you, during(t, 2.6f, 3.2f) ? 0.6f : 0);
			c.swing(you, swingAt(t, 4.3f, 5.0f));
			if (t < 6.6f) {
				double zx = move(t, 1.8f, 3.4f, 2.8f, 0.2f) + move(t, 4.35f, 5.0f, 0, 1.2f) + move(t, 5.05f, 5.7f, 0, 1.1f);
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, zx, 0.6, LEFT);
				c.walk(z, during(t, 1.8f, 2.4f) ? 0.7f : 0);
				z.hasRedOverlay = hurtAt(t, 4.3f, 5.0f);
				c.dead(z, t - 5.7f);
			}
		}
	}

	/** «Метеоры»: a ring of fire marks where a fireball lands; step away. */
	static final class Meteors implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.6f, 4.0f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Огненный круг: сюда упадёт метеор", "A ring of fire: a meteor lands here"},
					{"Отойди, пока не поздно", "Step away in time"},
					{"Метеор бьёт и мобов", "Meteors hit the mobs too"}};
		}

		public void play(Cast c, float t) {
			c.floor(x -> x == -1 && during(t, 2.7f, 3.4f) || x == 2 && during(t, 5.6f, 6.3f) ? "minecraft:magma_block" : null);
			meteor(c, t, -1, 0.5f, 2.6f);
			meteor(c, t, 2, 3.5f, 5.5f);

			double px = move(t, 1.6f, 2.3f, -1.0f, -2.6f);
			AvatarRenderState you = c.you(px, 0.5, t < 1.6f ? FRONT : t < 2.3f ? LEFT : RIGHT);
			c.walk(you, during(t, 1.6f, 2.3f) ? 1 : 0);
			c.hold(you, "minecraft:iron_sword");

			if (t < 7.0f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 3.0f, 4.2f, 3.0f, 2.0f), 0.5, LEFT);
				c.walk(z, during(t, 3.0f, 4.2f) ? 0.7f : 0);
				z.hasRedOverlay = during(t, 5.5f, 5.9f);
				c.dead(z, t - 5.6f);
			}
		}

		/** A mark from {@code mark}, the fall in its last second, the blast at {@code land}. */
		private static void meteor(Cast c, float t, double x, float mark, float land) {
			if (during(t, mark, land)) {
				if (((int) (t * 6) & 1) == 0 || t > land - 1) c.flame(x, -0.75, 0.5);
			}
			if (during(t, land - 0.8f, land)) {
				float k = ease(t, land - 0.8f, land);
				c.item("minecraft:fire_charge", x - 1.2 * (1 - k), 0.3 + 4.5 * (1 - k), 0.5, 0.9f);
			}
			if (during(t, land, land + 0.5f)) {
				float k = (t - land) / 0.5f;
				c.flame(x, 0, 0.5);
				c.flame(x - 0.6 * k, 0, 0.2);
				c.flame(x + 0.6 * k, 0, 0.8);
			}
		}
	}

	/** «Великаны и карлики»: one huge, one tiny; the dwarf first. */
	static final class Giants implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.6f, 4.0f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Мобы огромные или крошечные", "Mobs come huge or tiny"},
					{"Сначала добей быстрых карликов", "Finish the fast dwarves first"},
					{"Великан медленный, но живучий", "A giant is slow but tough"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			AvatarRenderState you = c.you(move(t, 4.2f, 5.0f, -1.5f, -2.4f), 0.4, RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.walk(you, during(t, 4.2f, 5.0f) ? 0.8f : 0);
			c.swing(you, swingAt(t, 2.0f, 2.6f, 5.6f, 6.2f, 6.8f));

			if (t < 3.6f) {
				LivingEntityRenderState d = c.mob(EntityTypes.ZOMBIE, move(t, 0.3f, 1.8f, 3.0f, -0.6f), 0.3, LEFT);
				d.scale = 0.55f;
				c.walk(d, t < 1.8f ? 1 : 0);
				d.hasRedOverlay = hurtAt(t, 2.0f, 2.6f);
				c.dead(d, t - 2.8f);
			}
			LivingEntityRenderState g = c.mob(EntityTypes.ZOMBIE, move(t, 0.6f, 5.5f, 3.4f, -0.8f), 0.7, LEFT);
			g.scale = 1.6f;
			c.walk(g, during(t, 0.6f, 5.5f) ? 0.35f : 0);
			c.swing(g, swingAt(t, 4.6f));
			g.hasRedOverlay = hurtAt(t, 5.6f, 6.2f, 6.8f);
		}
	}

	/** «Призраки»: invisible mobs, soul flames at their feet; a hit shows one for a moment. */
	static final class Ghosts implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 2.0f, 3.8f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Мобы невидимы: следи за огоньками", "Invisible mobs: watch the flames"},
					{"Попал — моб виден полторы секунды", "A hit shows it for a moment"},
					{"Добивай, пока видно", "Finish it while you see it"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			c.dark = 0.25f;
			AvatarRenderState you = c.you(-1.6, 0.4, RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.swing(you, swingAt(t, 2.0f, 3.9f, 4.5f));
			if (t < 6.4f) {
				double zx = move(t, 0, 1.9f, 3.0f, -0.2f);
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, zx, 0.5, LEFT);
				c.walk(z, t < 1.9f ? 0.7f : 0);
				boolean shown = during(t, 2.12f, 3.6f) || t >= 3.9f;
				z.isInvisible = !shown;
				z.hasRedOverlay = hurtAt(t, 2.0f, 3.9f, 4.5f);
				c.dead(z, t - 4.7f);
				if (!shown) {
					float bob = (float) Math.sin(t * 9) * 0.04f;
					c.item("minecraft:soul_torch", zx - 0.15, 0.15 + bob, 0.4, 0.35f);
					c.item("minecraft:soul_torch", zx + 0.15, 0.2 - bob, 0.6, 0.3f);
				}
			}
		}
	}

	/** «Стеклянная пушка»: one hit drops a mob, but theirs hurt you just as much. */
	static final class GlassCannon implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.4f, 3.4f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Удары втрое сильнее", "Hits do three times the damage"},
					{"Твой удар валит моба", "Your hit drops a mob"},
					{"Но и мобы бьют так же: отходи", "Theirs hurt as much: back off"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			double px = -1.2 - move(t, 4.6f, 5.3f, 0, 1.3f);
			AvatarRenderState you = c.you(px, 0.4, RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.walk(you, during(t, 4.6f, 5.3f) ? 0.9f : 0);
			c.swing(you, swingAt(t, 1.5f, 6.0f));
			you.hasRedOverlay = during(t, 4.25f, 4.7f);

			if (t < 2.8f) {
				LivingEntityRenderState a = c.mob(EntityTypes.ZOMBIE, move(t, 0, 1.3f, 2.6f, 0.2f), 0.5, LEFT);
				c.walk(a, t < 1.3f ? 0.8f : 0);
				a.hasRedOverlay = hurtAt(t, 1.5f);
				c.dead(a, t - 1.65f);
			}
			if (t >= 2.6f) {
				double bx = move(t, 2.6f, 4.0f, 3.0f, 0.2f) + move(t, 6.05f, 6.5f, 0, 0.5f);
				LivingEntityRenderState b = c.mob(EntityTypes.ZOMBIE, bx, 0.6, LEFT);
				c.walk(b, during(t, 2.6f, 4.0f) || during(t, 4.6f, 5.3f) ? 0.8f : 0);
				c.swing(b, swingAt(t, 4.1f));
				b.hasRedOverlay = hurtAt(t, 6.0f);
				c.dead(b, t - 6.15f);
			}
			if (t < 1.2f) c.item("minecraft:glass", 0, 1.8, 0.5, 0.6f);
		}
	}

	/** «Тотемы»: a crystal heals the mob; break it, then the mob falls. */
	static final class Totems implements Scene {
		public float length() {
			return 8.5f;
		}

		public float[] beats() {
			return new float[] {0, 2.6f, 5.6f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Кристаллы лечат мобов", "Crystals heal the mobs"},
					{"Разбей кристалл: 4 удара", "Break the crystal: 4 hits"},
					{"Без кристаллов мобы падают", "Without them mobs fall"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			boolean standing = t < 5.0f;
			if (standing) {
				c.block("minecraft:crying_obsidian", 2.4, 0, 1);
				ExplainerRenderer.Actor top = c.item("minecraft:amethyst_block", 2.4, 1.35 + Math.sin(t * 3) * 0.06, 1, 0.55f);
				top.spin = t * 60;
			} else if (t < 5.6f) {
				float k = ease(t, 5.0f, 5.6f);
				c.item("minecraft:amethyst_shard", 2.2, 1.1 + k, 0.8, 0.4f);
				c.item("minecraft:amethyst_shard", 2.6, 1.2 + k * 0.8, 1.1, 0.4f);
			}
			// a thread of light from the crystal to the hurt mob
			if (during(t, 1.3f, 1.9f)) {
				for (int i = 1; i < 6; i++) {
					double k = i / 6.0;
					c.item("minecraft:end_rod", 2.4 + (0.3 - 2.4) * k, 1.35 + (1.1 - 1.35) * k, 1 + (0.5 - 1) * k, 0.25f);
				}
			}

			double px = move(t, 2.6f, 3.4f, -1.2f, 1.4f) - move(t, 5.2f, 5.8f, 0, 2.4f);
			AvatarRenderState you = c.you(px, 0.4, t >= 5.2f && t < 5.8f ? LEFT : RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.walk(you, during(t, 2.6f, 3.4f) || during(t, 5.2f, 5.8f) ? 0.9f : 0);
			c.swing(you, swingAt(t, 0.7f, 3.5f, 3.9f, 4.3f, 4.7f, 6.2f));

			if (t < 7.4f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 0, 0.6f, 1.2f, 0.3f) - move(t, 5.2f, 5.8f, 0, 1.1f), 0.5, LEFT);
				c.walk(z, t < 0.6f || during(t, 5.2f, 5.8f) ? 0.7f : 0);
				z.hasRedOverlay = hurtAt(t, 0.7f, 6.2f);
				c.dead(z, t - 6.35f);
			}
		}
	}

	/** «Взрывные»: a slain mob smokes, then blows up; step back, let it hit its neighbour. */
	static final class Volatile implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 2.0f, 3.4f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Убитый моб взрывается через секунду", "A slain mob blows up a second later"},
					{"Отойди на пару шагов", "Step back a couple of blocks"},
					{"Взрыв ранит соседних мобов", "The blast hurts the mobs nearby"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			double px = -0.9 - move(t, 2.0f, 2.7f, 0, 1.6f);
			AvatarRenderState you = c.you(px, 0.4, t >= 2.0f && t < 2.7f ? LEFT : RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.walk(you, during(t, 2.0f, 2.7f) ? 1 : 0);
			c.swing(you, swingAt(t, 0.9f, 1.3f));

			if (t < 3.2f) {
				LivingEntityRenderState a = c.mob(EntityTypes.ZOMBIE, move(t, 0, 0.7f, 2.0f, 0.5f), 0.5, LEFT);
				c.walk(a, t < 0.7f ? 0.8f : 0);
				a.hasRedOverlay = hurtAt(t, 0.9f, 1.3f);
				c.dead(a, t - 1.5f);
				if (t >= 1.6f) {
					c.item("minecraft:gunpowder", 0.5, 0.7 + (t - 1.6f) * 0.4, 0.9, 0.4f);
					if (((int) (t * 8) & 1) == 0) c.item("minecraft:gunpowder", 0.4, 1.0 + (t - 1.6f) * 0.3, 0.6, 0.3f);
				}
			}
			if (during(t, 3.2f, 3.8f)) {
				float k = ease(t, 3.2f, 3.8f);
				c.flame(0.5, 0, 0.5);
				c.flame(0.5 - k, 0, 0.3);
				c.flame(0.5 + k, 0, 0.7);
				c.item("minecraft:tnt", 0.5, 1.0 + k, 0.5, 0.6f + k);
			}
			if (t < 7.0f) {
				LivingEntityRenderState b = c.mob(EntityTypes.ZOMBIE, move(t, 0.5f, 2.6f, 3.0f, 1.5f) + move(t, 3.25f, 3.6f, 0, 0.9f), 0.7, LEFT);
				c.walk(b, during(t, 0.5f, 2.6f) ? 0.7f : 0);
				b.hasRedOverlay = hurtAt(t, 3.2f);
				c.dead(b, t - 3.4f);
			}
		}
	}

	/** «Гроза»: a sparking ring, then lightning; step off it and lure the mob under. */
	static final class Storm implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.2f, 3.8f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Искрящий круг: сюда ударит молния", "A sparking ring: lightning hits here"},
					{"Уйди с круга", "Step off the ring"},
					{"Молния бьёт и мобов", "Lightning hits the mobs too"}};
		}

		public void play(Cast c, float t) {
			c.floor(x -> x == -1 && spark(t, 0.3f, 2.4f) || x == 1 && spark(t, 3.4f, 5.4f) ? "minecraft:light_blue_concrete" : null);
			bolt(c, t, -1, 2.4f);
			bolt(c, t, 1, 5.4f);
			c.dark = 0.2f + (during(t, 2.4f, 2.6f) || during(t, 5.4f, 5.6f) ? -0.2f : 0);

			double px = move(t, 1.2f, 1.9f, -1.0f, -2.6f);
			AvatarRenderState you = c.you(px, 0.5, t < 1.2f ? FRONT : t < 1.9f ? LEFT : RIGHT);
			c.walk(you, during(t, 1.2f, 1.9f) ? 1 : 0);
			c.hold(you, "minecraft:iron_sword");

			if (t < 7.0f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 2.6f, 4.4f, 3.2f, 1.0f), 0.5, LEFT);
				c.walk(z, during(t, 2.6f, 4.4f) ? 0.7f : 0);
				z.hasRedOverlay = during(t, 5.4f, 5.8f);
				c.dead(z, t - 5.5f);
			}
		}

		private static boolean spark(float t, float a, float b) {
			return during(t, a, b) && ((int) (t * 7) & 1) == 0;
		}

		private static void bolt(Cast c, float t, double x, float at) {
			if (!during(t, at, at + 0.35f)) return;
			for (double y = 0.2; y < 5; y += 0.45) {
				c.item("minecraft:end_rod", x + Math.sin(y * 5 + at) * 0.12, y, 0.5, 0.5f);
			}
		}
	}

	/** «Спешка»: everyone fast; get your back to a wall and hit first. */
	static final class Frenzy implements Scene {
		public float length() {
			return 7;
		}

		public float[] beats() {
			return new float[] {0, 1.4f, 3.2f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Всё в зале ускорилось", "Everything speeds up"},
					{"Мобы догоняют быстро", "Mobs catch up fast"},
					{"Встань к стене и бей первым", "Back to a wall, hit first"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			c.block("minecraft:deepslate_bricks", -HALF, 0, 0);
			c.block("minecraft:deepslate_bricks", -HALF, 0, 1);
			c.block("minecraft:deepslate_bricks", -HALF, 1, 1);
			double px = move(t, 0.3f, 0.9f, 1.0f, -1.6f) - move(t, 1.6f, 2.0f, 0, 0.4f);
			AvatarRenderState you = c.you(px, 0.5, t < 0.9f ? LEFT : RIGHT);
			c.walk(you, during(t, 0.3f, 0.9f) || during(t, 1.6f, 2.0f) ? 1 : 0);
			c.hold(you, "minecraft:iron_sword");
			c.swing(you, swingAt(t, 3.3f, 3.6f, 3.9f, 4.6f, 4.9f));
			for (int i = 0; i < 2; i++) {
				if (t >= 5.4f + i * 0.3f + 0.6f) continue;
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 1.2f + i * 0.4f, 2.4f + i * 0.4f, 3.0f, -0.6f + i * 0.4f), 0.2 + i * 0.6, LEFT);
				z.walkAnimationPos = c.age * 1.4f;
				z.walkAnimationSpeed = during(t, 1.2f + i * 0.4f, 2.4f + i * 0.4f) ? 1 : 0;
				z.hasRedOverlay = i == 0 ? hurtAt(t, 3.3f, 3.6f, 3.9f) : hurtAt(t, 4.6f, 4.9f);
				c.dead(z, t - (i == 0 ? 4.1f : 5.1f));
			}
			if (during(t, 1.2f, 2.8f)) {
				c.item("minecraft:sugar", move(t, 1.2f, 2.4f, 2.8f, 0), 0.3, 0.1, 0.3f);
			}
		}
	}

	/** «Ветер»: clouds drift the way the gust will blow; let it carry the mobs off. */
	static final class Wind implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 2.0f, 4.0f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Облачка показывают, куда подует", "Clouds show where it will blow"},
					{"Порыв толкает всех в одну сторону", "A gust pushes everyone one way"},
					{"Пусть ветер уносит мобов от тебя", "Let the wind carry mobs away"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			c.block("minecraft:deepslate_bricks", HALF + 1, 0, 0);
			c.block("minecraft:deepslate_bricks", HALF + 1, 0, 1);
			c.block("minecraft:deepslate_bricks", HALF + 1, 1, 0);
			if (t < 3.6f) {
				for (int i = 0; i < 5; i++) {
					double x = -3 + ((t * 1.6 + i * 1.3) % 6.5);
					c.item("minecraft:white_wool", x, 0.8 + (i % 3) * 0.6, 0.2 + (i % 2) * 0.6, 0.35f);
				}
			}
			float gust = ease(t, 2.0f, 3.2f);
			double px = -2.0 + gust * 0.6;
			AvatarRenderState you = c.you(px, 0.4, RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.swing(you, swingAt(t, 1.4f));
			if (t < 6.8f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 0, 1.2f, 1.6f, -0.6f) + gust * 3.4, 0.6, LEFT);
				c.walk(z, t < 1.2f ? 0.7f : 0);
				z.hasRedOverlay = hurtAt(t, 1.4f) || during(t, 3.1f, 3.4f);
				c.walk(z, during(t, 4.0f, 6.0f) ? 0.7f : 0);
				c.at(z).x -= move(t, 4.0f, 6.0f, 0, 0.6f);
			}
		}
	}

	/** «Ядовитый туман»: green clouds creep; go round them, the mobs walk through. */
	static final class Poison implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.8f, 4.4f};
		}

		public String[][] captions() {
			return new String[][] {
					{"По залу ползут облака яда", "Poison clouds creep around"},
					{"Обходи их", "Go round them"},
					{"Мобам яд не страшен", "The mobs don't mind it"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			double cx = move(t, 0, 8, -0.4f, 1.2f);
			for (int i = 0; i < 4; i++) {
				double x = cx + (i % 2) * 0.7 - 0.35;
				double y = 0.35 + (i / 2) * 0.55 + Math.sin(t * 2 + i) * 0.05;
				ExplainerRenderer.Actor cl = c.item("minecraft:lime_stained_glass", x, y, 0.5 + (i % 2) * 0.2, 0.7f);
				cl.spin = t * 20 + i * 40;
			}
			// you walk round behind the cloud
			double px = move(t, 1.8f, 3.6f, -2.6f, 2.4f);
			double pz = 0.2 + Math.sin(Math.PI * ease(t, 1.8f, 3.6f)) * 0.8;
			AvatarRenderState you = c.you(px, pz, RIGHT);
			c.walk(you, during(t, 1.8f, 3.6f) ? 0.9f : 0);
			c.hold(you, "minecraft:iron_sword");
			if (t >= 4.2f) you.bodyRot = LEFT;
			LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 4.4f, 6.6f, -2.8f, 1.2f), 0.5, RIGHT);
			c.walk(z, during(t, 4.4f, 6.6f) ? 0.7f : 0);
		}
	}

	/** «Царь горы»: stand in the circle while the bar fills; extra loot. */
	static final class Hill implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.4f, 5.6f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Встань в светящийся круг", "Stand in the glowing circle"},
					{"Полоска копится, пока ты в круге", "The bar fills while you stand in it"},
					{"Удержал — лишняя добыча", "Held it: extra loot"}};
		}

		public void play(Cast c, float t) {
			boolean glow = ((int) (t * 3) & 1) == 0 || t >= 5.6f;
			c.floor(x -> Math.abs(x) <= 1 && glow ? "minecraft:gold_block" : null);
			c.bar = ease(t, 1.4f, 5.6f);
			AvatarRenderState you = c.you(move(t, 0.2f, 1.3f, -2.6f, 0), 0.4, RIGHT);
			c.walk(you, t < 1.3f ? 0.9f : 0);
			c.hold(you, "minecraft:iron_sword");
			c.swing(you, swingAt(t, 2.6f, 3.0f, 4.2f, 4.6f));
			if (t < 5.3f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 1.4f, 2.4f, 3.0f, 1.3f), 0.5, LEFT);
				c.walk(z, during(t, 1.4f, 2.4f) ? 0.7f : 0);
				z.hasRedOverlay = hurtAt(t, 2.6f, 3.0f);
				c.dead(z, t - 3.2f);
				if (t >= 3.2f) z.isInvisible = t > 3.9f;
			}
			if (during(t, 3.4f, 5.4f)) {
				LivingEntityRenderState s = c.mob(EntityTypes.ZOMBIE, move(t, 3.4f, 4.0f, -3.0f, -1.3f), 0.6, RIGHT);
				c.walk(s, t < 4.0f ? 0.7f : 0);
				s.hasRedOverlay = hurtAt(t, 4.2f, 4.6f);
				c.dead(s, t - 4.8f);
				if (t < 4.1f) you.bodyRot = LEFT;
			}
			if (t >= 4.1f && t < 4.8f) you.bodyRot = LEFT;
			if (t >= 5.8f) {
				float k = ease(t, 5.8f, 6.6f);
				c.item("minecraft:diamond", -0.3, 1.2 + k * 0.8, 0.7, 0.5f);
				c.item("minecraft:emerald", 0.3, 1.1 + k, 0.7, 0.5f);
			}
		}
	}

	/** «Чемпион»: one big mob with a bar; it roars, leaps and stomps; run, then hit. */
	static final class Champion implements Scene {
		public float length() {
			return 8.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.6f, 4.6f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Вместо волн один чемпион", "One champion instead of waves"},
					{"Зарычал — сейчас прыгнет: отбегай", "It roars, then leaps: run"},
					{"Бей, пока он приходит в себя", "Hit it while it recovers"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			c.bar = 1 - ease(t, 4.7f, 7.0f) * 0.55f;
			double px = move(t, 2.0f, 2.6f, -0.6f, -2.5f) + move(t, 4.4f, 4.8f, 0, 0.9f);
			AvatarRenderState you = c.you(px, 0.4, t >= 2.0f && t < 2.6f ? LEFT : RIGHT);
			c.walk(you, during(t, 2.0f, 2.6f) || during(t, 4.4f, 4.8f) ? 1 : 0);
			c.hold(you, "minecraft:netherite_sword");
			c.swing(you, swingAt(t, 5.0f, 5.5f, 6.0f, 6.5f));

			// stands far, roars (head back), leaps to where you were, stomps
			float leap = ease(t, 2.2f, 3.2f);
			double bx = 2.4 + (0.2 - 2.4) * leap;
			double by = Math.sin(Math.PI * leap) * 0.8;
			LivingEntityRenderState b = c.mob(EntityTypes.ZOMBIE, bx, 0.6, LEFT);
			c.at(b).y = by;
			b.scale = 1.4f;
			if (b instanceof HumanoidRenderState h) {
				c.wear(h, "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots");
			}
			if (during(t, 1.4f, 2.2f)) b.xRot = -35;
			b.hasRedOverlay = hurtAt(t, 5.0f, 5.5f, 6.0f, 6.5f);
			if (during(t, 3.2f, 3.6f)) {
				float k = ease(t, 3.2f, 3.6f);
				c.flame(0.2 - 1.4 * k, 0, 0.3);
				c.flame(0.2 + 1.4 * k, 0, 0.7);
			}
		}
	}
}
