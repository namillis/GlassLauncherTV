package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** All apps at the tvOS grid's numbers (references/grid/INDEX.md): scroll
 *  band, backdrop, plate lift and rim, label scrim. 1080p px. */
public class TvGridTest {

    private static final int BAND_TOP = 180, BAND_BOTTOM = 750;

    /** Content-space row tops: favorites 100, grid rows 360 + 260 k. */
    private static int scrollAfter(int top, int scroll, boolean row1) {
        return Math.max(0, HomeGlass.gridScrollTarget(top, scroll, BAND_TOP, BAND_BOTTOM, row1));
    }

    @Test public void scrollFollowsTvosBandDownAndBackUp() {
        int s = 0;
        s = scrollAfter(360, s, true);   assertEquals(0, s);     // r1
        s = scrollAfter(620, s, false);  assertEquals(0, s);     // r2
        s = scrollAfter(880, s, false);  assertEquals(130, s);   // r3 at 750
        s = scrollAfter(1140, s, false); assertEquals(390, s);   // r4 at 750
        s = scrollAfter(880, s, false);  assertEquals(390, s);   // up r3 at 490
        s = scrollAfter(620, s, false);  assertEquals(390, s);   // up r2 at 230
        s = scrollAfter(360, s, true);   assertEquals(0, s);     // up r1: plate whole
    }

    @Test public void scrollWithoutFavoritesUsesTheBandOnly() {
        assertEquals(180, scrollAfter(360, 390, false));
    }

    private static int[] px(int r, int g, int b) {
        return new int[] {0xFF000000 | (r << 16) | (g << 8) | b};
    }

    private static int[] rgb(int c) {
        return new int[] {(c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff};
    }

    @Test public void backdropIsGainSaturationOffset() {
        int[] black = px(0, 0, 0);
        TvGlass.bakeGridBackdrop(black, 1);
        assertEquals(88, rgb(black[0])[0]);
        assertEquals(88, rgb(black[0])[1]);
        assertEquals(87, rgb(black[0])[2]);
        // Gray stays gray (saturation does not tint it): 0.67 x 100 + 87.5.
        int[] gray = px(100, 100, 100);
        TvGlass.bakeGridBackdrop(gray, 1);
        int[] g = rgb(gray[0]);
        assertTrue(Math.abs(g[1] - 154) <= 1);
        // Color doubles its distance from gray.
        int[] red = px(150, 100, 100);
        TvGlass.bakeGridBackdrop(red, 1);
        int[] r = rgb(red[0]);
        double y = 0.2126 * 150 + 0.7152 * 100 + 0.0722 * 100;
        assertEquals(0.67 * (y + 2 * (150 - y)) + 87.8, r[0], 1.0);
    }

    @Test public void plateLiftOverGrayIsTvos() {
        int c = TvGlass.applyMatrix(TvGlass.gridPlateLiftMatrix(), 0xFF565656);   // 86
        assertEquals(108, rgb(c)[1], 1);   // tvOS fill 108 over gray 86
    }

    @Test public void concatAppliesRightMatrixFirst() {
        float[] m = TvGlass.concat(TvGlass.gridPlateLiftMatrix(), TvGlass.gridBackdropMatrix());
        int seq = TvGlass.applyMatrix(TvGlass.gridPlateLiftMatrix(),
                TvGlass.applyMatrix(TvGlass.gridBackdropMatrix(), 0xFF204060));
        int once = TvGlass.applyMatrix(m, 0xFF204060);
        for (int i = 0; i < 3; i++) assertEquals(rgb(seq)[i], rgb(once)[i], 1);
    }

    /** The rim over the black-art backdrop (gray 86 after the material):
     *  raw top 178 158 139, sides 142 131 118, bottom 135 124 (section 3). */
    @Test public void rimOverGrayMatchesTvos() {
        TvGlass.Rim r = TvGlass.RIM_GRID_PLATE;
        // Atlas pixel that the grid material turns into gray 86: black art.
        int fill = TvGlass.applyMatrix(TvGlass.concat(TvGlass.gridPlateLiftMatrix(),
                TvGlass.gridBackdropMatrix()), 0xFF000000);
        int[][] want = {{178, 158, 139}, {142, 131, 118}, {135, 124, 0}};
        int[][] levels = {r.top, r.side, r.bottom};
        for (int s = 0; s < 3; s++) {
            for (int ring = 0; ring < (s == 2 ? 2 : 3); ring++) {
                float[] add = TvGlass.rimAdd(r, levels[s][ring] / 255f, 0, 0, 0);
                double got = rgb(fill)[1] + add[1];
                assertEquals("side " + s + " ring " + ring, want[s][ring], got, 6);
                assertEquals(add[0], add[1], 0.01f);   // equal adds keep the color
            }
        }
    }

    @Test public void labelScrimOnlyWhereNeeded() {
        assertEquals(0f, TvGlass.gridLabelScrimAlpha(88, 88, 87), 0f);   // black art
        float a = TvGlass.gridLabelScrimAlpha(255, 255, 255);
        assertTrue(a > 0.3f);
        double k = 1 - a;
        double bg = TvGlass.relLum(255 * k, 255 * k, 255 * k);
        assertTrue((1.05) / (bg + 0.05) >= TvGlass.CHECK_CONTRAST);
    }
}
