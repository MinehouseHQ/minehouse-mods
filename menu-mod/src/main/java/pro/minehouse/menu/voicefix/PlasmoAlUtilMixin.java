package pro.minehouse.menu.voicefix;

import org.lwjgl.openal.AL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Plasmo Voice from breaking OpenAL for the rest of the session when the game started without
 * sound.
 *
 * <p>If Windows has no working audio device while the game starts ({@code Failed to open OpenAL
 * device}), Minecraft turns sound off and no {@code ALCapabilities} exist yet. Plasmo Voice then opens
 * the microphone and first asks for the OpenAL Soft version ({@code AlUtil.getAlSoftVersion}, a plain
 * {@code alGetString}). In LWJGL that call initialises {@code AL$ICDStatic$WriteOnce}, which throws
 * "No ALCapabilities instance has been set", and a class whose static init failed stays broken in this
 * JVM. When the headphones come back, Minecraft reloads its sound engine, its first AL call hits the
 * broken class ({@code NoClassDefFoundError}) and the game crashes.
 *
 * <p>Without capabilities we answer "version unknown" (Plasmo then assumes the capture downmix works,
 * its default) and do not mark the version as resolved, so Plasmo asks again once sound is up. The
 * microphone itself is opened through ALC, which needs no capabilities.
 */
@Pseudo
@Mixin(targets = "su.plo.voice.client.audio.AlUtil", remap = false)
public abstract class PlasmoAlUtilMixin {
	@Inject(method = "getAlSoftVersion", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
	private static void minehouse$noCapabilitiesYet(CallbackInfoReturnable<Object> cir) {
		if (!hasAlCapabilities()) {
			cir.setReturnValue(null);
		}
	}

	private static boolean hasAlCapabilities() {
		try {
			// Reads the process/thread capabilities only, never the ICD that breaks.
			return AL.getCapabilities() != null;
		} catch (IllegalStateException e) {
			return false;
		}
	}
}
