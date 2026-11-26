package pro.minehouse.menu;

import java.util.Locale;

import net.minecraft.client.Minecraft;

/**
 * Our screens' texts in the game's language: Russian when the client is set to Russian,
 * English otherwise. Asked each time a text is built, so switching the language takes effect
 * on the next frame or screen.
 */
public final class Tr {
	private Tr() {
	}

	/** True when the game's language is Russian (or not known yet, as on the first frames). */
	public static boolean russian() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return true;
		}
		String code = mc.options.languageCode;
		return code == null || code.toLowerCase(Locale.ROOT).startsWith("ru");
	}

	/** The Russian or the English text, by the game's language. */
	public static String s(String ru, String en) {
		return russian() ? ru : en;
	}
}
