package pro.minehouse.menu.tab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TabDataTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Test
	void parsesTheServerFormat() {
		TabData d = TabData.parse("{\"v\":1,\"online\":2,\"max\":100,\"tps\":19.8,\"players\":{\"" + A
				+ "\":{\"r\":\"admin\",\"c\":\"#a855f7\",\"l\":true},\"" + B + "\":{},\"junk\":{}}}");
		assertEquals(2, d.online());
		assertEquals(100, d.max());
		assertEquals(19.8, d.tps());
		assertEquals("admin", d.meta(A).role());
		assertEquals(0xA855F7, d.meta(A).color());
		assertTrue(d.meta(A).live());
		assertNull(d.meta(B).role());
		assertFalse(d.meta(UUID.randomUUID()).live());
		assertNull(TabData.parse("not json"));
	}

	@Test
	void staffFirstBotsAndSpectatorsLast() {
		List<TabData.Row<String>> rows = new ArrayList<>();
		rows.add(new TabData.Row<>("bot", "Bot", false, new TabData.Meta("bot", null, false)));
		rows.add(new TabData.Row<>("zed", "zed", false, new TabData.Meta(null, null, false)));
		rows.add(new TabData.Row<>("spec", "Aaa", true, new TabData.Meta("admin", null, false)));
		rows.add(new TabData.Row<>("adam", "Adam", false, new TabData.Meta(null, null, false)));
		rows.add(new TabData.Row<>("mod", "Mod", false, new TabData.Meta("moderator", null, false)));
		rows.add(new TabData.Row<>("admin", "Zz", false, new TabData.Meta("admin", null, false)));
		TabData.sort(rows);
		assertEquals(List.of("admin", "mod", "adam", "zed", "bot", "spec"), rows.stream().map(TabData.Row::info).toList());
	}

	@Test
	void gridSpreadsRowsOverColumns() {
		assertArrayEquals(new int[] {1, 0}, TabData.grid(0));
		assertArrayEquals(new int[] {1, 7}, TabData.grid(7));
		assertArrayEquals(new int[] {1, 20}, TabData.grid(20));
		assertArrayEquals(new int[] {2, 11}, TabData.grid(21));
		assertArrayEquals(new int[] {4, 20}, TabData.grid(80));
	}

	@Test
	void pingAndTpsColours() {
		assertEquals(4, TabData.pingBars(30));
		assertEquals(1, TabData.pingBars(900));
		assertEquals(0, TabData.pingBars(-1));
		assertEquals(0xFF4ADE80, TabData.tpsColor(20));
		assertEquals(0xFFF87171, TabData.tpsColor(10));
	}
}
