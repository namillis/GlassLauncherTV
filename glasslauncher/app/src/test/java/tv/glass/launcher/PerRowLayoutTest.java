package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** PerRowLayout against the P1 table (glass-tvos/design/perrow/VALUES.md),
 *  1920 x 1080 at density 2.0, default icon roundness. */
public class PerRowLayoutTest {

    private static PerRowLayout at(int cols) {
        return PerRowLayout.of(cols, 1920, 2f, LayoutOptions.DEFAULT_CORNER_PERCENT);
    }

    /** {tile w, tile h, gap, pad, plate h, pitch, tile corner, home r, grid r,
     *  first All apps tile top} */
    private static void assertTable(int cols, int[] v) {
        PerRowLayout g = at(cols);
        assertEquals("tile w", v[0], g.tileW);
        assertEquals("tile h", v[1], g.tileH);
        assertEquals("gap", v[2], g.gap);
        assertEquals("pad", v[3], g.pad);
        assertEquals("plate h", v[4], g.plateH);
        assertEquals("pitch", v[5], g.rowPitch);
        assertEquals("tile corner", v[6], g.tileCorner);
        assertEquals("home radius", v[7], g.homeRadius);
        assertEquals("grid radius", v[8], g.gridRadius);
        assertEquals("first tile top", v[9], g.gridRowTop(0));
        // Same plate box at every count: x 50-1869, All apps top 60, home
        // bottom 66 px above the screen bottom.
        assertEquals(50, g.plateLeft);
        assertEquals(1820, g.plateW);
        assertEquals(60, g.gridPlateTop);
        assertEquals(66, g.homePlateBottomMargin);
    }

    @Test public void fivePerRow() {
        assertTable(5, new int[] {300, 180, 56, 48, 276, 312, 36, 75, 82, 108});
        assertEquals(420, at(5).gridRowTop(1));
    }

    @Test public void sixPerRowIsMainsTvOsLayout() {
        assertTable(6, new int[] {250, 150, 48, 40, 230, 260, 30, 61, 68, 100});
        PerRowLayout g = at(6);
        assertEquals(360, g.gridRowTop(1));
        assertEquals(298, g.stride());
        // main: cellH = 150 + dp(28), rowGap = 260 - cellH, bottom pad dp(28)
        assertEquals(54, g.rowGap);
        assertEquals(56, g.gridBottomPad);
        assertEquals(206, g.cellH);
        // main's constants are the 6 per row values
        assertEquals(Math.round(TvGlass.PLATE_HEIGHT_DP * 2f), g.plateH);
        assertEquals(Math.round(TvGlass.PLATE_RADIUS_DP * 2f), g.homeRadius);
        assertEquals(Math.round(TvGlass.GRID_PLATE_RADIUS_DP * 2f), g.gridRadius);
        assertEquals(HomeGlass.GRID_FIRST_TILE_TOP_DP * 2, g.gridRowTop(0));
    }

    @Test public void sevenPerRow() {
        assertTable(7, new int[] {212, 127, 44, 36, 199, 224, 25, 52, 59, 96});
        assertEquals(320, at(7).gridRowTop(1));
    }

    @Test public void fullRowsFillThePlateWithEqualPadding() {
        for (int cols = LayoutOptions.MIN_COLUMNS; cols <= LayoutOptions.MAX_COLUMNS; cols++) {
            PerRowLayout g = at(cols);
            // Home shelf is the plate's width; the tiles of a full row sit
            // pad from both plate sides.
            int left = g.rowLeft(g.plateW, cols) + g.gap / 2;
            int right = g.plateW - (left + cols * g.tileW + (cols - 1) * g.gap);
            assertEquals("left pad at " + cols, g.pad, left);
            assertTrue("right pad at " + cols, Math.abs(right - g.pad) <= 2);
            assertEquals("top = bottom = side pad", g.pad, (g.plateH - g.tileH) / 2);
            // All apps is the screen's width; same tile columns.
            assertEquals(g.plateLeft + left, g.rowLeft(1920, cols) + g.gap / 2);
            // The focused tile (1.224x) stays at least 12 px inside the plate,
            // sideways and (28 / 23 / 22 px at 5 / 6 / 7) vertically.
            float grow = g.tileW * (TvGlass.FOCUS_SCALE - 1f) / 2f;
            assertTrue("focus clearance at " + cols, g.pad - grow >= 12f);
            float growH = g.tileH * (TvGlass.FOCUS_SCALE - 1f) / 2f;
            int[] vertical = {28, 23, 22};
            assertEquals("vertical focus clearance at " + cols,
                    vertical[cols - LayoutOptions.MIN_COLUMNS], Math.round(g.pad - growH));
        }
    }

    @Test public void singleRowStartAndScrollEdgeScaleFromMainsSix() {
        // 6 per row is main's dp(24) start and dp(48) edge buffer; 5 and 7
        // are one and two of the count's gap.
        int[] start = {56, 48, 44}, edge = {112, 96, 88};
        for (int cols = LayoutOptions.MIN_COLUMNS; cols <= LayoutOptions.MAX_COLUMNS; cols++) {
            PerRowLayout g = at(cols);
            int i = cols - LayoutOptions.MIN_COLUMNS;
            assertEquals("start at " + cols, start[i], g.rowLeft(g.plateW, 40));
            assertEquals("start at " + cols, start[i], g.singleRowStart());
            assertEquals("edge at " + cols, edge[i], g.scrollEdge());
            // First tile at open: x 134 / 122 / 116 on screen; at 6 that is main.
            assertEquals(new int[] {134, 122, 116}[i],
                    g.plateLeft + g.rowLeft(g.plateW, 40) + g.gap / 2);
            // Focused first tile and a focused tile held edge px from the
            // shelf end both stay at least 12 px inside the plate.
            float grow = g.tileW * (TvGlass.FOCUS_SCALE - 1f) / 2f;
            assertTrue(g.singleRowStart() + g.gap / 2 - grow >= 12f);
            assertTrue(g.scrollEdge() - grow >= 12f);
        }
    }

    @Test public void rowThatExactlyFillsTheContainerIsCentered() {
        // main's 7 per row: 7 x 260 = 1820 took the overflow branch.
        PerRowLayout g = at(7);
        int width = 7 * g.stride();
        assertEquals(0, g.rowLeft(width, 7));
    }

    @Test public void fewerFavoritesAreCenteredInTheFullPlate() {
        PerRowLayout g = at(7);
        int left = g.rowLeft(g.plateW, 3);
        assertEquals((1820 - 3 * 256) / 2, left);
    }

    @Test public void savedFourAndOutOfRangeCountsReadAsFiveOrSeven() {
        assertEquals(5, PerRowLayout.of(4, 1920, 2f, 20).columns);
        assertEquals(300, PerRowLayout.of(4, 1920, 2f, 20).tileW);
        assertEquals(7, PerRowLayout.of(9, 1920, 2f, 20).columns);
    }

    @Test public void plateCornersIgnoreIconRoundness() {
        PerRowLayout square = PerRowLayout.of(6, 1920, 2f, 0);
        assertEquals(0, square.tileCorner);
        assertEquals(61, square.homeRadius);
        assertEquals(68, square.gridRadius);
    }

    @Test public void narrowScreenScalesDownToFit() {
        PerRowLayout g = PerRowLayout.of(7, 1280, 2f, 20);
        int need = 7 * g.tileW + 6 * g.gap + 2 * g.pad;
        assertTrue(need <= g.plateW);
    }

    @Test public void seventwentyPAtTvDpiMatchesTheSameDpLayout() {
        PerRowLayout g = PerRowLayout.of(6, 1280, 1.3333f, 20);
        assertEquals(167, g.tileW);
        assertEquals(33, g.plateLeft);
    }
}
