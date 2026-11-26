package pro.minehouse.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When the mod installed by hand (without the pack) updates itself, see {@link PackUpdater#forMod}. */
class StandaloneUpdateTest {
	@Test
	void updatesOnlyToNewerVersions() {
		assertTrue(PackUpdater.newer("1.22.0", "1.21.0"));
		assertTrue(PackUpdater.newer("1.21.1", "1.21.0"));
		assertTrue(PackUpdater.newer("1.10.0", "1.9.9"));
		assertTrue(PackUpdater.newer("2.0", "1.99.99"));
		assertFalse(PackUpdater.newer("1.21.0", "1.21.0"));
		assertFalse(PackUpdater.newer("1.21.0", "1.21"));
		assertFalse(PackUpdater.newer("1.20.0", "1.21.0"));
		assertFalse(PackUpdater.newer("1.21.0", "1.21.0+local"));
		// Unparsable: like a changed hash, any other version counts.
		assertTrue(PackUpdater.newer("1.22.0", "dev"));
		assertFalse(PackUpdater.newer("dev", "dev"));
	}

	@Test
	void matchesMinecraftVersion() {
		assertTrue(PackUpdater.sameMinecraft("26.3", "26.3"));
		assertTrue(PackUpdater.sameMinecraft("26.3", "26.3.1"));
		assertTrue(PackUpdater.sameMinecraft(null, "26.3"));
		assertFalse(PackUpdater.sameMinecraft("26.3", "26.4"));
		assertFalse(PackUpdater.sameMinecraft("26.3", "26.30"));
	}
}
