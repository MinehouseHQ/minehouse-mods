package pro.minehouse.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class UiTreeTest {
	/** Every character 6 px wide (7 bold), lines broken at spaces. */
	static final UiTree.Measure MONO = new UiTree.Measure() {
		@Override
		public int width(String text, boolean bold) {
			return text.length() * (bold ? 7 : 6);
		}

		@Override
		public List<String> wrap(String text, boolean bold, int width) {
			List<String> out = new ArrayList<>();
			StringBuilder line = new StringBuilder();
			for (String word : text.split(" ")) {
				String next = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && width(next, bold) > width) {
					out.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(next);
				}
			}
			out.add(line.toString());
			return out;
		}
	};

	private static UiTree.Node parse(String json) {
		return UiTree.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	private static String tiles(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"t\":\"card\",\"pad\":2,\"gap\":1,\"click\":{\"a\":\"pick\",\"id\":\"c").append(i)
					.append("\"},\"children\":[{\"t\":\"model\",\"model\":\"x:c\",\"h\":32},{\"t\":\"text\",\"text\":\"100\",\"align\":\"center\"}]}");
		}
		return sb.toString();
	}

	@Test
	void gridShowsMoreRowsWhenTall() {
		String json = "{\"t\":\"col\",\"pad\":8,\"gap\":8,\"children\":[{\"t\":\"tabs\",\"children\":[{\"t\":\"tab\",\"text\":\"Head\",\"active\":true},"
				+ "{\"t\":\"tab\",\"text\":\"Back\"},{\"t\":\"tab\",\"text\":\"Wings\"},{\"t\":\"tab\",\"text\":\"Belt\"},{\"t\":\"tab\",\"text\":\"Pets\"}]},"
				+ "{\"t\":\"grid\",\"id\":\"g\",\"cols\":3,\"cw\":40,\"ch\":46,\"gap\":4,\"rows\":2,\"maxRows\":3,\"children\":[" + tiles(10) + "]},"
				+ "{\"t\":\"text\",\"text\":\"hint\"}]}";
		UiTree.Node tall = parse(json);
		int hTall = new UiTree(MONO, true).layout(tall, 0, 0, 144);
		UiTree.Node grid = tall.children.get(1);
		assertEquals(3, grid.visibleRows);
		assertEquals(4 * 50 - 4 - (3 * 50 - 4), grid.maxScroll);
		// pad + tabs + gap + grid + gap + text + pad, as the 1.24.0 wardrobe's left card.
		assertEquals(8 + 22 + 8 + (3 * 50 - 4) + 8 + 9 + 8, hTall);

		UiTree.Node shortOne = parse(json);
		new UiTree(MONO, false).layout(shortOne, 0, 0, 144);
		assertEquals(2, shortOne.children.get(1).visibleRows);

		// Tiles in cells of 40x46 from the panel's padding; the fifth one in the second row.
		UiTree.Node fifth = grid.children.get(4);
		assertEquals(8 + 44, fifth.x);
		assertEquals(8 + 22 + 8 + 50, fifth.y);
		assertEquals(40, fifth.w);
		assertEquals(46, fifth.h);
		// Its model fills the card inside its padding.
		assertEquals(36, fifth.children.get(0).w);
	}

	@Test
	void tabsAreIconsOnlyEvenWithRoomForTheName() {
		UiTree.Node tabs = parse("{\"t\":\"tabs\",\"children\":[{\"t\":\"tab\",\"text\":\"Head\",\"active\":true},{\"t\":\"tab\"},{\"t\":\"tab\"},{\"t\":\"tab\"},{\"t\":\"tab\"}]}");
		UiTree tree = new UiTree(MONO, true);
		tree.layout(tabs, 10, 0, 128);
		assertFalse(tree.labelled(tabs, tabs.children.get(0)));
		assertEquals(124 / 5, tabs.children.get(0).w);
		assertEquals(12, tabs.children.get(0).x);
	}

	@Test
	void rowSharesTheRestBetweenGrowingChildren() {
		UiTree.Node row = parse("{\"t\":\"row\",\"gap\":6,\"children\":[{\"t\":\"button\",\"text\":\"Wear\",\"grow\":1},"
				+ "{\"t\":\"button\",\"text\":\"Take all off\",\"style\":\"outline\",\"grow\":1}]}");
		int h = new UiTree(MONO, true).layout(row, 0, 0, 118);
		assertEquals(UiTree.BUTTON_H, h);
		assertEquals(56, row.children.get(0).w);
		assertEquals(56, row.children.get(1).w);
		assertEquals(62, row.children.get(1).x);

		// A spacer pushes the link to the right; the coin adds its icon to the width.
		UiTree.Node balance = parse("{\"t\":\"row\",\"children\":[{\"t\":\"text\",\"text\":\"0\",\"bold\":true,\"coin\":true},"
				+ "{\"t\":\"space\",\"grow\":1},{\"t\":\"button\",\"text\":\"Top up\",\"style\":\"link\"}]}");
		new UiTree(MONO, true).layout(balance, 0, 0, 118);
		assertEquals(7 + UiTree.COIN_GAP + UiTree.COIN, balance.children.get(0).w);
		assertEquals(118 - 40, balance.children.get(2).x);
	}

	@Test
	void textWrapsToItsColumn() {
		UiTree.Node col = parse("{\"t\":\"col\",\"pad\":8,\"children\":[{\"t\":\"text\",\"text\":\"Crown of the night sky\",\"bold\":true,\"wrap\":true}]}");
		int h = new UiTree(MONO, true).layout(col, 0, 0, 118);
		UiTree.Node text = col.children.get(0);
		assertEquals(List.of("Crown of the", "night sky"), text.lines);
		assertEquals(8 + 2 * UiTree.LINE - 1 + 8, h);
	}

	@Test
	void coloursByNameOrHex() {
		assertEquals(UiTree.Theme.ACCENT, UiTree.color("accent", 0));
		assertEquals(0xFF3F3F46, UiTree.color("#3f3f46", 0));
		assertEquals(0x00000000, UiTree.color("#00000000", 1));
		assertEquals(7, UiTree.color("nonsense", 7));
	}

	@Test
	void shiftMovesEverything() {
		UiTree.Node col = parse("{\"t\":\"col\",\"children\":[{\"t\":\"row\",\"children\":[{\"t\":\"item\",\"item\":\"minecraft:stone\"}]}]}");
		new UiTree(MONO, true).layout(col, 0, 0, 50);
		UiTree.shift(col, 10, 20);
		UiTree.Node item = col.children.get(0).children.get(0);
		assertEquals(10, item.x);
		assertEquals(20, item.y);
		assertTrue(item.over(12, 25));
	}
}
