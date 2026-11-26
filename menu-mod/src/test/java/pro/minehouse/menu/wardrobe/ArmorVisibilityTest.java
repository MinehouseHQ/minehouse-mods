package pro.minehouse.menu.wardrobe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmorVisibilityTest {
	private ArmorVisibility read(String json) {
		return ArmorVisibility.read(JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	void onlyAnExplicitBooleanAdvertisesTheControl() {
		for (String json : new String[]{"{}", "{\"armorHidden\":null}", "{\"armorHidden\":\"true\"}", "{\"armorHidden\":1}"}) {
			assertEquals(ArmorVisibility.UNAVAILABLE, read(json));
		}
	}

	@Test
	void openAndLivePacketsAreAuthoritativeSnapshots() {
		assertEquals(ArmorVisibility.VISIBLE, read("{\"open\":true,\"armorHidden\":false}"));
		assertEquals(ArmorVisibility.HIDDEN, read("{\"open\":false,\"armorHidden\":true}"));
	}
}
