package pro.minehouse.menu.explainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

import pro.minehouse.menu.MinehouseMenu;

/**
 * Dungeon hall events explained by a short looping 3D scene (MineHouse End {@code RoomIntro}):
 * what happens and what to do, played by you, a friend and the rift's mobs on a strip of floor,
 * with a caption per step. The server names the scene; the scenes live here.
 */
public final class Explainer {
	/** Scene x of the floor's ends; the floor is 7 blocks wide and 2 deep (z 0 and 1). */
	static final int HALF = 3;
	private static final UUID FRIEND = new UUID(0x4d48L, 0x5L);
	private static final LivingEntity.SwingDescription SWING = new LivingEntity.SwingDescription(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, 6);
	private static final Map<String, ItemStackRenderState> ITEMS = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();

	/** Screen right / left as a body turn. */
	static final float RIGHT = 90;
	static final float LEFT = -90;
	static final float FRONT = 180;

	private Explainer() {
	}

	/**
	 * One moment of a scene.
	 *
	 * @param world  the stage
	 * @param glow   drawn on top of the darkness (mobs that glow in «Тьма»)
	 * @param dark   0..1: how dark the stage is between the two
	 * @param bar    0..1: a countdown bar over the stage, or -1
	 * @param step   the caption's number, from 0, of {@code steps}
	 */
	public record Frame(List<ExplainerRenderer.Actor> world, List<ExplainerRenderer.Actor> glow, String caption, int step, int steps,
			float dark, float bar, int bg, float length) {
	}

	/** Self-test only: show every scene at this share of its length (0..1) instead of playing it; -1 plays. */
	public static float pinned = -1;

	/** The scenes the server may name. */
	public static boolean known(String scene) {
		return switch (scene) {
			case "reflection", "gravity", "darkness", "lava", "merchant", "altar", "timer",
					"ice", "meteors", "giants", "ghosts", "glass_cannon", "totems", "volatile", "storm", "frenzy", "wind", "poison", "hill",
					"champion" -> true;
			default -> false;
		};
	}

	/** The scene at {@code t} seconds since the window opened (it loops), or null for a scene we don't know. */
	public static @Nullable Frame frame(String scene, float t, boolean ru) {
		Scene s = switch (scene) {
			case "reflection" -> new Reflection();
			case "gravity" -> new Gravity();
			case "darkness" -> new Darkness();
			case "lava" -> new Lava();
			case "merchant" -> new Merchant();
			case "altar" -> new Altar();
			case "timer" -> new Timer();
			case "ice" -> new MoreScenes.Ice();
			case "meteors" -> new MoreScenes.Meteors();
			case "giants" -> new MoreScenes.Giants();
			case "ghosts" -> new MoreScenes.Ghosts();
			case "glass_cannon" -> new MoreScenes.GlassCannon();
			case "totems" -> new MoreScenes.Totems();
			case "volatile" -> new MoreScenes.Volatile();
			case "storm" -> new MoreScenes.Storm();
			case "frenzy" -> new MoreScenes.Frenzy();
			case "wind" -> new MoreScenes.Wind();
			case "poison" -> new MoreScenes.Poison();
			case "hill" -> new MoreScenes.Hill();
			case "champion" -> new MoreScenes.Champion();
			default -> null;
		};
		if (s == null) return null;
		float time = pinned >= 0 ? Math.min(pinned, 0.999f) * s.length() : t % s.length();
		Cast c = new Cast(time);
		s.play(c, time);
		int step = 0;
		for (int i = 0; i < s.beats().length; i++) {
			if (time >= s.beats()[i]) step = i;
		}
		String[] caption = s.captions()[step];
		return new Frame(c.world, c.glow, ru ? caption[0] : caption[1], step, s.beats().length, Mth.clamp(c.dark, 0, 1), c.bar, c.bg, s.length());
	}

	public static void warnOnce(String what, RuntimeException e) {
		if (WARNED.add(what)) MinehouseMenu.LOG.warn("Explainer: can't draw {}", what, e);
	}

	// ------------------------------------------------------------------ timing

	/** 0 before a, 1 after b, eased in between. */
	static float ease(float t, float a, float b) {
		if (t <= a) return 0;
		if (t >= b) return 1;
		float x = (t - a) / (b - a);
		return x * x * (3 - 2 * x);
	}

	/** From p to q between a and b. */
	static float move(float t, float a, float b, float p, float q) {
		return p + (q - p) * ease(t, a, b);
	}

	static boolean during(float t, float a, float b) {
		return t >= a && t < b;
	}

	/** How far a swing that starts at {@code at} is (0..1), or -1 when it isn't swinging. */
	static float swingAt(float t, float... at) {
		for (float a : at) {
			if (t >= a && t < a + 0.3f) return (t - a) / 0.3f;
		}
		return -1;
	}

	/** Whether a hit that lands at one of {@code at} still flashes red. */
	static boolean hurtAt(float t, float... at) {
		for (float a : at) {
			if (t >= a + 0.12f && t < a + 0.42f) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the cast

	/** Builds a frame's actors. */
	static final class Cast {
		final float age;
		final List<ExplainerRenderer.Actor> world = new ArrayList<>();
		final List<ExplainerRenderer.Actor> glow = new ArrayList<>();
		float dark;
		float bar = -1;
		int bg = 0xFF1B1B22;
		private final Minecraft mc = Minecraft.getInstance();

		Cast(float time) {
			this.age = time * 20;
		}

		/** The floor: dungeon tiles; {@code hot} gives a column's block by its x (null: the usual tile). */
		void floor(java.util.function.IntFunction<String> hot) {
			for (int x = -HALF; x <= HALF; x++) {
				String b = hot == null ? null : hot.apply(x);
				for (int z = 0; z <= 1; z++) block(b != null ? b : (x + z & 1) == 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate", x, -1, z);
			}
		}

		void floor() {
			floor(null);
		}

		ExplainerRenderer.Actor block(String id, double x, double y, double z) {
			ExplainerRenderer.Actor a = new ExplainerRenderer.Actor(ExplainerRenderer.Kind.BLOCK, null, item(id, ItemDisplayContext.NONE), x, y, z);
			world.add(a);
			return a;
		}

		/** An item turning in the air (loot, a bottle of experience). */
		ExplainerRenderer.Actor item(String id, double x, double y, double z, float size) {
			ExplainerRenderer.Actor a = new ExplainerRenderer.Actor(ExplainerRenderer.Kind.ITEM, null, item(id, ItemDisplayContext.GROUND), x, y, z);
			a.spin = age * 6;
			a.size = size;
			world.add(a);
			return a;
		}

		/** Fire over a block-sized place. */
		void flame(double x, double y, double z) {
			EntityRenderState s = new EntityRenderState();
			s.boundingBoxWidth = 0.9f;
			s.boundingBoxHeight = 0.7f;
			s.ageInTicks = age;
			world.add(new ExplainerRenderer.Actor(ExplainerRenderer.Kind.FLAME, s, null, x, y, z));
		}

		/** You, with your own skin. */
		AvatarRenderState you(double x, double z, float facing) {
			return player(selfSkin(), x, z, facing);
		}

		/** Another player, with a default skin. */
		AvatarRenderState friend(double x, double z, float facing) {
			return player(DefaultPlayerSkin.get(FRIEND), x, z, facing);
		}

		AvatarRenderState player(PlayerSkin skin, double x, double z, float facing) {
			AvatarRenderState s = new AvatarRenderState();
			s.entityType = EntityTypes.PLAYER;
			s.skin = skin;
			s.showHat = s.showJacket = s.showLeftPants = s.showRightPants = s.showLeftSleeve = s.showRightSleeve = true;
			living(s, facing, 0.6f, 1.8f);
			world.add(new ExplainerRenderer.Actor(ExplainerRenderer.Kind.ENTITY, s, null, x, 0, z));
			return s;
		}

		/** A mob; {@code glowing}: drawn over the darkness. */
		@SuppressWarnings({"unchecked", "rawtypes"})
		LivingEntityRenderState mob(EntityType<?> type, double x, double z, float facing, boolean glowing) {
			EntityRenderState probe = new EntityRenderState();
			probe.entityType = type;
			EntityRenderState s = (EntityRenderState) ((net.minecraft.client.renderer.entity.EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(probe)).createRenderState();
			s.entityType = type;
			LivingEntityRenderState l = (LivingEntityRenderState) s;
			living(l, facing, type.getWidth(), type.getHeight());
			if (l instanceof ZombieRenderState zs) zs.isAggressive = true;
			if (l instanceof SkeletonRenderState sk) sk.isAggressive = true;
			(glowing ? glow : world).add(new ExplainerRenderer.Actor(ExplainerRenderer.Kind.ENTITY, s, null, x, 0, z));
			return l;
		}

		LivingEntityRenderState mob(EntityType<?> type, double x, double z, float facing) {
			return mob(type, x, z, facing, false);
		}

		/** Where an actor stands; for moving it after it was made. */
		ExplainerRenderer.Actor at(EntityRenderState s) {
			for (ExplainerRenderer.Actor a : world) if (a.entity == s) return a;
			for (ExplainerRenderer.Actor a : glow) if (a.entity == s) return a;
			throw new IllegalArgumentException("not in the scene");
		}

		private void living(LivingEntityRenderState s, float facing, float w, float h) {
			s.ageInTicks = age;
			s.lightCoords = ExplainerRenderer.FULL_BRIGHT;
			s.boundingBoxWidth = w;
			s.boundingBoxHeight = h;
			s.eyeHeight = h * 0.85f;
			s.bodyRot = facing;
			s.yRot = 0;
			s.xRot = 0;
			s.scale = 1;
			s.ageScale = 1;
			s.pose = Pose.STANDING;
			s.nameTag = null;
			if (s instanceof ArmedEntityRenderState a) {
				a.mainArm = HumanoidArm.RIGHT;
				a.rightArmPose = HumanoidModel.ArmPose.EMPTY;
				a.leftArmPose = HumanoidModel.ArmPose.EMPTY;
				a.rightHandItemStack = ItemStack.EMPTY;
				a.leftHandItemStack = ItemStack.EMPTY;
				a.currentSwing = null;
				a.swingAnimation = 0;
			}
			if (s instanceof HumanoidRenderState hu) {
				hu.headEquipment = ItemStack.EMPTY;
				hu.chestEquipment = ItemStack.EMPTY;
				hu.legsEquipment = ItemStack.EMPTY;
				hu.feetEquipment = ItemStack.EMPTY;
				hu.useItemHand = InteractionHand.MAIN_HAND;
			}
		}

		/** Something in the right hand. */
		void hold(LivingEntityRenderState s, String id) {
			ItemStack stack = stack(id);
			if (s instanceof ArmedEntityRenderState a) {
				a.rightHandItemStack = stack;
				a.rightArmPose = stack.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
				mc.getItemModelResolver().updateForTopItem(a.rightHandItemState, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, mc.level, null, 0);
			} else if (s instanceof HoldingEntityRenderState h) {
				mc.getItemModelResolver().updateForTopItem(h.heldItem, stack, ItemDisplayContext.GROUND, mc.level, null, 0);
			}
		}

		/** Armour pieces by slot: head, chest, legs, feet. */
		void wear(HumanoidRenderState s, String head, String chest, String legs, String feet) {
			s.headEquipment = stack(head);
			s.chestEquipment = stack(chest);
			s.legsEquipment = stack(legs);
			s.feetEquipment = stack(feet);
		}

		/** A swing {@code p} (0..1) of the way through, or none for p < 0. */
		void swing(LivingEntityRenderState s, float p) {
			if (!(s instanceof ArmedEntityRenderState a)) return;
			if (p < 0 || p >= 1) {
				a.currentSwing = null;
				a.swingAnimation = 0;
			} else {
				a.currentSwing = SWING;
				a.swingAnimation = p;
			}
		}

		/** Walking legs while moving; {@code speed} 0..1. */
		void walk(LivingEntityRenderState s, float speed) {
			s.walkAnimationPos = age * 0.6f;
			s.walkAnimationSpeed = Mth.clamp(speed, 0, 1);
		}

		/**
		 * Falls over flat, red, as a mob that died {@code since} seconds ago: backwards, away from the
		 * way it faces, so it never lands on whoever hit it.
		 */
		void dead(LivingEntityRenderState s, float since) {
			if (since < 0) return;
			s.deathTime = 0;
			s.hasRedOverlay = true;
			ExplainerRenderer.Actor a = at(s);
			float away = s.bodyRot == RIGHT ? -1 : 1;
			// not off the stage: near an end it falls the other way
			double reach = s.boundingBoxHeight * s.scale;
			if (a.x + away * reach > HALF + 0.6) away = -1;
			else if (a.x + away * reach < -HALF - 0.6) away = 1;
			a.tip = away * 90 * ease(since, 0, 0.5f);
		}

		private PlayerSkin selfSkin() {
			if (mc.player != null) return mc.player.getSkin();
			return DefaultPlayerSkin.get(mc.getUser().getProfileId());
		}

		private ItemStack stack(String id) {
			// Item stacks can be made only once the game has bound item components (in a world; not on the title screen).
			if (id == null || !Items.PAPER.builtInRegistryHolder().areComponentsBound()) return ItemStack.EMPTY;
			Identifier i = Identifier.tryParse(id);
			var item = i == null ? null : BuiltInRegistries.ITEM.getValue(i);
			return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		}

		private ItemStackRenderState item(String id, ItemDisplayContext context) {
			if (!Items.PAPER.builtInRegistryHolder().areComponentsBound()) return new ItemStackRenderState();
			return ITEMS.computeIfAbsent(id + "#" + context, k -> {
				ItemStackRenderState out = new ItemStackRenderState();
				mc.getItemModelResolver().updateForTopItem(out, stack(id), context, mc.level, null, 0);
				return out;
			});
		}
	}

	// ------------------------------------------------------------------ scenes

	interface Scene {
		float length();

		/** When each caption starts, seconds. */
		float[] beats();

		/** {ru, en} per beat. */
		String[][] captions();

		void play(Cast c, float t);
	}

	/** «Отражение»: a double of you appears; hit it from both sides, step back when it swings. */
	static final class Reflection implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 2.2f, 4.8f, 6f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Разлом копирует одного из вас", "The rift copies one of you"},
					{"Бейте двойника вместе", "Hit the double together"},
					{"Он замахнулся: отойди", "It swings: step back"},
					{"Двойник даёт много опыта", "The double gives lots of experience"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			float back = ease(t, 5.0f, 5.6f) - ease(t, 5.6f, 6.0f) * 0.6f;
			double yx = move(t, 1.2f, 2.2f, -2.7f, -1.25f) - back * 0.9;
			double fx = move(t, 1.2f, 2.2f, 2.7f, 1.25f) + back * 0.9;
			AvatarRenderState you = c.you(yx, 0.35, RIGHT);
			AvatarRenderState friend = c.friend(fx, 0.65, LEFT);
			for (AvatarRenderState p : List.of(you, friend)) {
				c.hold(p, "minecraft:diamond_sword");
				c.walk(p, during(t, 1.2f, 2.2f) || during(t, 5.0f, 5.8f) ? 0.9f : 0);
			}
			c.swing(you, swingAt(t, 2.3f, 3.1f, 3.9f, 6.0f));
			c.swing(friend, swingAt(t, 2.7f, 3.5f, 4.3f, 6.1f));

			if (t >= 0.2f && t < 7.2f) {
				AvatarRenderState d = c.you(0, 0.5, t < 2.2f ? FRONT : t < 4.8f ? (((int) ((t - 2.2f) / 0.4f) & 1) == 0 ? LEFT : RIGHT) : LEFT);
				c.hold(d, "minecraft:diamond_sword");
				d.scale = 0.2f + 0.8f * ease(t, 0.2f, 0.9f);
				d.isInvisible = t < 1.0f;
				c.swing(d, swingAt(t, 4.85f));
				d.hasRedOverlay = hurtAt(t, 2.3f, 2.7f, 3.1f, 3.5f, 3.9f, 4.3f);
				// players stand on both sides of it: it shrinks away into the rift instead of falling
				if (t >= 6.15f) {
					d.hasRedOverlay = true;
					d.scale = 1 - 0.8f * ease(t, 6.15f, 6.9f);
					d.isInvisible = t > 6.9f;
				}
			}
			if (t >= 6.6f) {
				float rise = ease(t, 6.6f, 7.6f);
				c.item("minecraft:experience_bottle", 0, 0.5 + rise * 1.2, 0.5, 0.9f);
			}
		}
	}

	/** «Невесомость»: a hit throws the mob up; jump after it and finish it in the air. */
	static final class Gravity implements Scene {
		public float length() {
			return 7.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.2f, 2.0f, 4.4f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Гравитация слабеет", "Gravity gets weak"},
					{"Удар подбрасывает моба", "A hit throws a mob up"},
					{"Прыгай и добивай в воздухе", "Jump and finish it in the air"},
					{"Падать почти не больно", "Falling barely hurts"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			double px = move(t, 2.0f, 2.9f, -1.6f, -0.7f);
			double py = move(t, 2.0f, 2.8f, 0, 1.3f) - move(t, 3.4f, 5.4f, 0, 1.3f);
			AvatarRenderState you = c.you(px, 0.5, RIGHT);
			c.at(you).y = py;
			c.hold(you, "minecraft:iron_sword");
			c.swing(you, swingAt(t, 1.1f, 2.75f, 3.15f));
			if (py > 0.05) {
				you.walkAnimationSpeed = 0;
				you.xRot = -25;
			}

			if (t < 4.4f) {
				double zx = move(t, 0, 1.1f, 2.6f, 0.5f);
				double zy = move(t, 1.25f, 2.6f, 0, 1.5f);
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, zx, 0.5, LEFT);
				c.at(z).y = zy;
				c.walk(z, t < 1.1f ? 0.8f : 0);
				z.hasRedOverlay = hurtAt(t, 1.1f, 2.75f, 3.15f);
				c.dead(z, t - 3.3f);
				z.isInvisible = t > 4.0f;
			}
		}
	}

	/** «Тьма»: the lamps go out, the mobs glow; a potion of night vision lifts it. */
	static final class Darkness implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.4f, 5.0f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Лампы гаснут", "The lamps go out"},
					{"Мобы светятся: бей по силуэтам", "Mobs glow: hit the outlines"},
					{"Ночное зрение снимает тьму", "Night vision lifts the dark"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			boolean lit = t < 0.8f;
			c.block(lit ? "minecraft:sea_lantern" : "minecraft:deepslate_bricks", -HALF, 0, 1);
			c.block(lit ? "minecraft:sea_lantern" : "minecraft:deepslate_bricks", HALF, 0, 1);
			c.dark = (ease(t, 0.7f, 1.4f) - ease(t, 5.8f, 6.6f)) * 0.82f;

			AvatarRenderState you = c.you(-1.7, 0.4, RIGHT);
			boolean drinking = during(t, 5.0f, 6.2f);
			c.hold(you, drinking ? "minecraft:potion" : "minecraft:iron_sword");
			if (drinking) {
				you.xRot = -20;
				you.isUsingItem = true;
				you.ticksUsingItem = (t - 5.0f) * 20;
			}
			c.swing(you, swingAt(t, 3.0f, 3.5f, 4.0f));

			if (t < 5.0f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 1.4f, 2.9f, 3.0f, -0.2f), 0.5, LEFT, true);
				c.walk(z, during(t, 1.4f, 2.9f) ? 0.8f : 0);
				z.hasRedOverlay = hurtAt(t, 3.0f, 3.5f, 4.0f);
				c.dead(z, t - 4.2f);
			}
			LivingEntityRenderState sk = c.mob(EntityTypes.SKELETON, 2.2, 1.2, LEFT, true);
			c.hold(sk, "minecraft:bow");
			if (sk instanceof ArmedEntityRenderState a) a.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
		}
	}

	/** «Пол — лава»: a tile turns to magma, step off; then it is lava for a while. */
	static final class Lava implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.6f, 3.0f, 6.2f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Плитка греется: магма", "A tile heats up: magma"},
					{"Сразу уходи с неё", "Step off at once"},
					{"Через пару секунд там лава", "Seconds later it is lava"},
					{"Потом плитка остывает", "Then the tile cools down"}};
		}

		public void play(Cast c, float t) {
			boolean magma = during(t, 0.8f, 3.0f);
			boolean lava = during(t, 3.0f, 6.2f);
			c.floor(x -> x != 0 ? null : magma ? "minecraft:magma_block" : lava ? "minecraft:orange_concrete" : null);
			if (lava) {
				c.flame(0, -0.2, 0);
				c.flame(0, -0.2, 1);
			}

			double px = move(t, 1.6f, 2.5f, 0, -1.8f);
			AvatarRenderState you = c.you(px, 0.5, t < 1.6f ? FRONT : LEFT);
			c.walk(you, during(t, 1.6f, 2.5f) ? 1 : 0);
			if (t >= 2.5f) you.bodyRot = RIGHT;

			if (t >= 3.0f && t < 6.2f) {
				LivingEntityRenderState z = c.mob(EntityTypes.ZOMBIE, move(t, 3.0f, 3.9f, 2.7f, 0.1f), 0.5, LEFT);
				c.walk(z, t < 3.9f ? 0.8f : 0);
				boolean burning = t >= 3.8f;
				z.displayFireAnimation = burning;
				z.hasRedOverlay = burning && ((int) (t * 4) & 1) == 0;
				c.dead(z, t - 5.0f);
				z.isInvisible = t > 5.8f;
			}
		}
	}

	/** «Торговец разлома»: no fight; buy for emeralds, mend for experience. */
	static final class Merchant implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.4f, 3.9f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Здесь нет боя: торговец", "No fight here: a trader"},
					{"ПКМ: зелья и яблоки за изумруды", "Right-click: potions and apples for emeralds"},
					{"Вещь в руке + ПКМ: починка за опыт", "Item in hand + right-click: mends for XP"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			LivingEntityRenderState trader = c.mob(EntityTypes.WANDERING_TRADER, 1.1, 0.6, t < 1.0f ? FRONT : LEFT - 25);
			c.hold(trader, during(t, 1.6f, 2.6f) ? "minecraft:golden_apple" : during(t, 4.5f, 5.6f) ? "minecraft:iron_pickaxe" : null);

			double px = move(t, 0, 1.2f, -2.7f, -0.5f);
			AvatarRenderState you = c.you(px, 0.5, RIGHT);
			c.walk(you, t < 1.2f ? 0.9f : 0);
			String hand = t < 1.6f ? "minecraft:emerald" : t < 3.9f ? (t >= 2.6f ? "minecraft:golden_apple" : null) : t < 4.5f || t >= 5.6f ? "minecraft:iron_pickaxe" : null;
			c.hold(you, hand);
			c.swing(you, swingAt(t, 1.35f, 4.2f));

			if (during(t, 1.6f, 2.6f)) c.item("minecraft:emerald", move(t, 1.6f, 2.5f, -0.2f, 0.9f), 1.3, 0.5, 0.5f);
			if (during(t, 4.5f, 5.6f)) {
				float k = ease(t, 4.5f, 5.4f);
				c.item("minecraft:experience_bottle", -0.2 + 1.1 * k, 1.3 + Math.sin(k * Math.PI) * 0.4, 0.5, 0.5f);
			}
		}
	}

	/** «Алтарь проклятия»: touch it for a curse, x2 experience and more from the altar. */
	static final class Altar implements Scene {
		public float length() {
			return 8.5f;
		}

		public float[] beats() {
			return new float[] {0, 1.4f, 2.6f, 5.4f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Алтарь: риск по желанию", "The altar: a risk you choose"},
					{"Тронул: проклятие до конца зала", "Touch it: a curse for the hall"},
					{"Зато мобы дают x2 опыт", "But mobs give x2 experience"},
					{"После зачистки алтарь даёт опыт", "Hall cleared: the altar gives experience"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			c.block("minecraft:crying_obsidian", 0.6, 0, 1);
			ExplainerRenderer.Actor skull = c.item("minecraft:wither_skeleton_skull", 0.6, 1.25, 1, 0.9f);
			skull.spin = 200;

			double px = move(t, 0, 1.2f, -2.7f, -0.4f) - move(t, 2.4f, 2.9f, 0, 0.9f);
			AvatarRenderState you = c.you(px, 0.4, RIGHT);
			c.hold(you, t < 2.4f ? null : "minecraft:iron_sword");
			c.walk(you, t < 1.2f || during(t, 2.4f, 2.9f) ? 0.9f : 0);
			c.swing(you, swingAt(t, 1.3f, 3.3f, 3.7f, 4.1f, 4.5f, 4.9f));
			you.hasRedOverlay = during(t, 1.5f, 1.8f) || during(t, 1.95f, 2.2f);

			if (during(t, 2.6f, 5.6f)) {
				LivingEntityRenderState a = c.mob(EntityTypes.ZOMBIE, move(t, 2.6f, 3.2f, 3.0f, 0.6f), 0.3, LEFT);
				c.walk(a, t < 3.2f ? 0.8f : 0);
				a.hasRedOverlay = hurtAt(t, 3.3f, 3.7f);
				c.dead(a, t - 3.9f);
				a.isInvisible = t > 4.6f;
				LivingEntityRenderState b = c.mob(EntityTypes.ZOMBIE, move(t, 2.8f, 3.8f, 3.2f, 0.8f), 0.6, LEFT);
				c.walk(b, during(t, 2.8f, 3.8f) ? 0.8f : 0);
				b.hasRedOverlay = hurtAt(t, 4.1f, 4.5f, 4.9f);
				c.dead(b, t - 5.0f);
			}
			if (t >= 5.4f) {
				float k = ease(t, 5.4f, 6.2f);
				c.item("minecraft:experience_bottle", 0.3, 1.3 + k * 0.7, 0.6, 0.55f);
				c.item("minecraft:experience_bottle", 0.9, 1.2 + k * 0.9, 0.6, 0.55f);
				c.item("minecraft:experience_bottle", 0.6, 1.5 + k * 1.0, 0.6, 0.55f);
			}
		}
	}

	/** «На время»: a minute on the bar; when it runs out elite mobs come. */
	static final class Timer implements Scene {
		public float length() {
			return 8;
		}

		public float[] beats() {
			return new float[] {0, 1.0f, 5.0f};
		}

		public String[][] captions() {
			return new String[][] {
					{"Полоска сверху: одна минута", "The bar on top: one minute"},
					{"Зачисти зал, пока она не кончилась", "Clear the hall before it runs out"},
					{"Не успел: идут элитные мобы", "Too slow: elite mobs come"}};
		}

		public void play(Cast c, float t) {
			c.floor();
			c.bar = 1 - ease(t, 0.3f, 5.0f) * 0.999f + (t < 0.3f ? 0 : 0);
			if (t >= 5.0f) c.bar = 0;

			double px = move(t, 2.2f, 2.8f, -1.6f, -0.4f) - move(t, 5.4f, 6.2f, 0, 1.4f);
			AvatarRenderState you = c.you(px, 0.4, RIGHT);
			c.hold(you, "minecraft:iron_sword");
			c.walk(you, during(t, 2.2f, 2.8f) || during(t, 5.4f, 6.2f) ? 0.9f : 0);
			c.swing(you, swingAt(t, 1.0f, 1.4f, 1.8f, 3.0f, 3.8f, 4.6f));

			if (t < 2.8f) {
				LivingEntityRenderState a = c.mob(EntityTypes.ZOMBIE, move(t, 0, 0.8f, 2.8f, -0.2f), 0.4, LEFT);
				c.walk(a, t < 0.8f ? 0.8f : 0);
				a.hasRedOverlay = hurtAt(t, 1.0f, 1.4f, 1.8f);
				c.dead(a, t - 2.0f);
			}
			LivingEntityRenderState b = c.mob(EntityTypes.ZOMBIE, move(t, 0.4f, 1.6f, 2.8f, 1.0f), 0.7, LEFT);
			c.walk(b, during(t, 0.4f, 1.6f) ? 0.8f : 0);
			b.hasRedOverlay = hurtAt(t, 3.0f, 3.8f, 4.6f);

			if (t >= 5.0f) {
				for (int i = 0; i < 2; i++) {
					LivingEntityRenderState e = c.mob(EntityTypes.ZOMBIE, move(t, 5.3f + i * 0.2f, 6.4f, 3.0f, 1.6f + i * 0.6f), 0.2 + i * 0.7, LEFT);
					if (e instanceof HumanoidRenderState h) c.wear(h, "minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots");
					c.hold(e, "minecraft:diamond_sword");
					e.scale = 0.3f + 0.7f * ease(t, 5.0f, 5.5f);
					e.isInvisible = t < 5.4f;
					c.walk(e, during(t, 5.3f, 6.4f) ? 0.9f : 0);
				}
			}
		}
	}
}
