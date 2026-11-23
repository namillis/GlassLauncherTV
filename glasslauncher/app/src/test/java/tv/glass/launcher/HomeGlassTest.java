package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Home screen picks H1B, H2B and H4B: text contrast on the bubble and the
 *  message glass, exactly as TvGlass.bake draws them, and the geometry the
 *  home index states (1920 x 1080 at 2 px per dp). */
public class HomeGlassTest {

    private static final int DP = 2;

    /** Pure white, the brightest blurred pixels of the calibration
     *  wallpapers (Matterhorn sky, winter snow), a mid blue, black. */
    private static final int[][] BACKDROPS = {
            {255, 255, 255}, {255, 251, 240}, {205, 226, 246}, {120, 140, 170}, {0, 0, 0}};

    private static double[] drawn(int surface, int r, int g, int b) {
        int[] px = {0xFF000000 | (r << 16) | (g << 8) | b};
        TvGlass.bake(surface, px, 1);
        return new double[] {(px[0] >> 16) & 0xff, (px[0] >> 8) & 0xff, px[0] & 0xff};
    }

    /** WCAG ratio of an ARGB text color drawn straight on the fill. */
    private static double contrast(int argb, double[] fill) {
        int a = argb >>> 24;
        double[] text = new double[3];
        int[] c = {(argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff};
        for (int i = 0; i < 3; i++) text[i] = (c[i] * a + fill[i] * (255 - a)) / 255.0;
        return TvGlass.ratio(text, fill);
    }

    private static void assertReadable(String what, int surface, int text) {
        for (int[] c : BACKDROPS) {
            double r = contrast(text, drawn(surface, c[0], c[1], c[2]));
            assertTrue(what + " over " + c[0] + "," + c[1] + "," + c[2] + ": " + r,
                    r >= TvGlass.CHECK_CONTRAST);
        }
        for (int red = 0; red <= 255; red += 51) {
            for (int g = 0; g <= 255; g += 51) {
                for (int b = 0; b <= 255; b += 51) {
                    double r = contrast(text, drawn(surface, red, g, b));
                    assertTrue(what + " " + red + "," + g + "," + b + ": " + r,
                            r >= TvGlass.CHECK_CONTRAST);
                }
            }
        }
    }

    @Test public void bubbleTime_white_keeps4_7to1_everywhere() {
        assertReadable("bubble time", TvGlass.CAPSULE, 0xFFFFFFFF);
    }

    @Test public void messageTitle_white_keeps4_7to1_everywhere() {
        assertReadable("message title", TvGlass.TOAST, 0xFFFFFFFF);
    }

    @Test public void messageDetail_gray_keeps4_7to1_everywhere() {
        assertReadable("message detail", TvGlass.TOAST, HomeGlass.TOAST_DETAIL_TEXT);
    }

    @Test public void messageDetail_isGrayNotTinted() {
        int c = HomeGlass.TOAST_DETAIL_TEXT;
        assertEquals((c >> 16) & 0xff, c & 0xff);
        assertEquals((c >> 8) & 0xff, c & 0xff);
        assertTrue((c & 0xff) < 0xFF);
    }

    @Test public void bubble_matchesTvosBox() {
        // x 1692-1869, y 50-109 on a 1920 px screen.
        assertEquals(178, HomeGlass.BUBBLE_MIN_WIDTH_DP * DP);
        assertEquals(60, HomeGlass.BUBBLE_HEIGHT_DP * DP);
        assertEquals(50, HomeGlass.BUBBLE_TOP_DP * DP);
        assertEquals(1920 - 1870, HomeGlass.BUBBLE_END_DP * DP);
        // Gear only: 6 + 48 + 6 = 60 px, a round bubble.
        assertEquals(60, (2 * HomeGlass.GEAR_INSET_DP + HomeGlass.GEAR_DP) * DP);
    }

    @Test public void message_sits16pxUnderTheBubble() {
        assertEquals(126, HomeGlass.toastTopDp() * DP);
        assertEquals(230, (HomeGlass.toastTopDp() + HomeGlass.TOAST_HEIGHT_DP) * DP);
        assertEquals(560, HomeGlass.TOAST_WIDTH_DP * DP);
    }

    @Test public void grid_rowsAt100_360_620_880() {
        int top = HomeGlass.GRID_FIRST_TILE_TOP_DP * DP;
        int pitch = HomeGlass.GRID_ROW_PITCH_DP * DP;
        assertEquals(100, top);
        assertEquals(360, top + pitch);
        assertEquals(620, top + 2 * pitch);
        assertEquals(880, top + 3 * pitch);
        // Plate 230 tall centered on the 150 px tiles: y 60-290.
        int plateTop = At4kHomeLayout.favoritesPlateTop(top, 0,
                TvGlass.PLATE_HEIGHT_DP * DP, 150);
        assertEquals(60, plateTop);
        assertEquals(290, plateTop + TvGlass.PLATE_HEIGHT_DP * DP);
    }

    @Test public void splitMessage_titleAndDetail() {
        String[] p = HomeGlass.splitMessage("Aerial Views moved\nNow second in the home row");
        assertEquals("Aerial Views moved", p[0]);
        assertEquals("Now second in the home row", p[1]);
        String[] one = HomeGlass.splitMessage("Cannot open settings");
        assertEquals("Cannot open settings", one[0]);
        assertNull(one[1]);
        assertNull(HomeGlass.splitMessage("Title\n  ")[1]);
        assertEquals("", HomeGlass.splitMessage(null)[0]);
    }
}
