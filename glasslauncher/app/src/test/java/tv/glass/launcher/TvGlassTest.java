package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Checks the tvOS glass materials exactly as the prototype draws them:
 *  TvGlass.matrix(surface, k) with k = TvGlass.textSafeScale(surface, worst
 *  backdrop pixel), and the text and lift alphas the views use. */
public class TvGlassTest {

    private static final int[] TEXT_SURFACES = {
            TvGlass.CAPSULE, TvGlass.CARD, TvGlass.MENU, TvGlass.GRID, TvGlass.TOAST};

    /** Pure white, then the brightest blurred pixels measured on the three
     *  calibration wallpapers (design/material/INDEX.md), then black. */
    private static final int[][] BACKDROPS = {
            {255, 255, 255}, {255, 251, 240}, {205, 226, 246}, {120, 140, 170}, {0, 0, 0}};

    /** The exact 8-bit pixel TvGlass.bake writes (what the view draws). */
    private static float[] drawn(int surface, int[] c) {
        int[] px = {0xFF000000 | (c[0] << 16) | (c[1] << 8) | c[2]};
        TvGlass.bake(surface, px, 1);
        return new float[] {(px[0] >> 16) & 0xff, (px[0] >> 8) & 0xff, px[0] & 0xff};
    }

    @Test public void everyTextSurface_keeps4_7to1_onEveryBackdrop() {
        for (int s : TEXT_SURFACES) {
            for (int[] c : BACKDROPS) {
                double ratio = TvGlass.textContrast(s, drawn(s, c));
                assertTrue(TvGlass.MATERIALS[s].name + " over " + c[0] + "," + c[1] + ","
                        + c[2] + ": " + ratio, ratio >= TvGlass.CHECK_CONTRAST);
            }
        }
    }

    /** Saturated and mixed colors too, every 51 levels per channel. */
    @Test public void everyTextSurface_keeps4_7to1_overAColorCube() {
        for (int s : TEXT_SURFACES) {
            for (int r = 0; r <= 255; r += 51) {
                for (int g = 0; g <= 255; g += 51) {
                    for (int b = 0; b <= 255; b += 51) {
                        double ratio = TvGlass.textContrast(s, drawn(s, new int[] {r, g, b}));
                        assertTrue(TvGlass.MATERIALS[s].name + " " + r + "," + g + "," + b
                                + ": " + ratio, ratio >= TvGlass.CHECK_CONTRAST);
                    }
                }
            }
        }
    }

    @Test public void darkPixels_areNotCapped() {
        float[] f = new float[4];
        TvGlass.textSafeFill(TvGlass.CARD, 20, 20, 30, f);
        assertEquals(1f, f[3], 0f);
        TvGlass.textSafeFill(TvGlass.CAPSULE, 255, 255, 255, f);
        assertTrue("capsule over white is darkened, k " + f[3], f[3] < 0.7f);
    }

    @Test public void capsuleTime_onPureWhite_isDarkenedNotLeftAtTvosFit() {
        // tvOS fit alone: 0.8 x 255 + 24 = L ~228, white text ~1.3:1.
        float[] fit = TvGlass.fill(TvGlass.CAPSULE, 1f, 255, 255, 255);
        assertTrue(TvGlass.textContrast(TvGlass.CAPSULE, fit) < 2.0);
        float k = TvGlass.textSafeScale(TvGlass.CAPSULE, 255, 255, 255);
        assertTrue("k " + k, k < 0.6f && k > 0.3f);
    }

    @Test public void darkBackdrop_keepsTheTvosFit() {
        assertEquals(1f, TvGlass.textSafeScale(TvGlass.CAPSULE, 0, 0, 0), 0f);
        assertEquals(1f, TvGlass.textSafeScale(TvGlass.CARD, 10, 10, 20), 0f);
        assertEquals(1f, TvGlass.textSafeScale(TvGlass.PLATE, 255, 255, 255), 0f);
    }

    @Test public void plate_isNeutralLift_noTint() {
        float[] f = TvGlass.fill(TvGlass.PLATE, 1f, 0, 0, 0);
        assertEquals(17.8f, f[0], 0.01f);
        float[] g = TvGlass.fill(TvGlass.PLATE, 1f, 100, 100, 100);
        assertTrue(g[0] > 100 && g[1] > 100 && g[2] > 100);
    }

    @Test public void card_foldsTheTvosDim() {
        float[] f = TvGlass.fill(TvGlass.CARD, 1f, 100, 100, 100);
        assertEquals(0.914f * 0.70f * 100 + 18.9f, f[0], 0.01f);
    }

    @Test public void brightestFill_picksTheWorstPixel() {
        int[] px = {0xFF101010, 0xFFF0F0F0, 0xFF808080};
        assertEquals(1, TvGlass.brightestFill(TvGlass.CAPSULE, px, px.length));
    }

    /** Over black every colored rim adds the same gray to R, G and B: its
     *  black-row top value. */
    @Test public void coloredRim_overBlack_isNeutral() {
        TvGlass.Rim[] rims = {TvGlass.RIM_PLATE, TvGlass.RIM_CAPSULE, TvGlass.RIM_CARD};
        for (TvGlass.Rim r : rims) {
            float[] add = TvGlass.rimAdd(r, 1f, 0, 0, 0);
            assertEquals(r.a, add[0], 0.01f);
            assertEquals(r.a, add[1], 0.01f);
            assertEquals(r.a, add[2], 0.01f);
            assertEquals(r.top[0], r.a, 0.6f);
        }
    }

    /** sim2 card top over Matterhorn: outside 2 2 26 (dimmed home), tvOS
     *  p1 - fill +40 +42 +109. The card samples the undimmed atlas. */
    @Test public void coloredRim_overBlue_turnsBlue() {
        float d = TvGlass.OVERLAY_DIM;
        float[] add = TvGlass.rimAdd(TvGlass.RIM_CARD, 1f,
                Math.round(2 / d), Math.round(2 / d), Math.round(26 / d));
        assertEquals(40f, add[0], 5f);
        assertEquals(42f, add[1], 5f);
        assertEquals(109f, add[2], 8f);
    }

    /** The filter clamps: a saturated warm picture cannot subtract blue. */
    @Test public void coloredRim_neverSubtracts() {
        float[] add = TvGlass.rimAdd(TvGlass.RIM_CARD, 1f, 255, 120, 0);
        for (float v : add) assertTrue("add " + v, v >= 0f && v <= 255f);
    }

    @Test public void atlasRegions_doNotOverlap() {
        int[] a = TvGlass.atlasLayout(1920, 1080);
        assertEquals(480, a[0]);
        int[] c0 = TvGlass.region(TvGlass.SOURCE_COARSE, 1920, 1080);
        int[] c1 = TvGlass.region(TvGlass.SOURCE_CAPSULE, 1920, 1080);
        int[] c2 = TvGlass.region(TvGlass.SOURCE_CARD, 1920, 1080);
        assertEquals(135, c0[3]);
        assertTrue(c1[1] >= c0[3] + TvGlass.ATLAS_GAP);
        assertTrue(c2[1] >= c1[3] + TvGlass.ATLAS_GAP);
        assertEquals(a[1], c2[3]);
    }

    // X1: the plate's top boost.
    @Test public void plateTopBoost_isZeroOverBlack_soTheBlackRowStaysTvOs() {
        for (int ring = 0; ring < 3; ring++) {
            float[] add = TvGlass.topBoostAdd(TvGlass.RIM_PLATE, ring, 0, 0, 0);
            for (float v : add) assertEquals(0f, v, 0f);
        }
    }

    @Test public void plateTopBoost_isFullOverAnyPicture_outermostBrightest() {
        // Coarse-atlas pixels at the plate top over Matterhorn and Yosemite.
        int[][] pics = {{16, 30, 75}, {60, 62, 55}};
        int[] boost = TvGlass.RIM_PLATE.topBoost;
        for (int[] p : pics) {
            float[] prev = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            for (int ring = 0; ring < 3; ring++) {
                float[] add = TvGlass.topBoostAdd(TvGlass.RIM_PLATE, ring, p[0], p[1], p[2]);
                for (int c = 0; c < 3; c++) {
                    assertEquals(boost[c] * TvGlass.TOP_BOOST_RING_WEIGHT[ring], add[c], 0.01f);
                    assertTrue(add[c] <= prev[c]);
                    prev[c] = add[c];
                }
            }
        }
        // Only the plate is boosted.
        assertEquals(null, TvGlass.RIM_CARD.topBoost);
        assertEquals(null, TvGlass.RIM_CAPSULE.topBoost);
    }

    /** The stacked passes add exactly the per-channel boost. */
    @Test public void plateTopBoost_passesSumToTheBoost() {
        int[] boost = TvGlass.RIM_PLATE.topBoost;
        int[] sum = new int[3];
        for (int[] pass : TvGlass.topBoostPasses(boost)) {
            assertTrue(pass[0] > 0);
            for (int c = 0; c < 3; c++) sum[c] += pass[0] * pass[1 + c];
        }
        for (int c = 0; c < 3; c++) assertEquals(boost[c], sum[c]);
        int[] gray = TvGlass.topBoostPasses(new int[] {9, 9, 9})[0];
        assertEquals(9, gray[0]);
    }

    /** Big clock scrim: nothing over a dark picture; over white and the
     *  brightest wallpaper pixels white text reaches 4.7:1. */
    @Test public void clockScrim_onlyDarkensBrightPictures() {
        assertEquals(0f, TvGlass.scrimAlpha(0, 0, 0), 0f);
        assertEquals(0f, TvGlass.scrimAlpha(20, 30, 70), 0f);   // Matterhorn sky
        for (int[] c : new int[][] {{255, 255, 255}, {255, 251, 240}, {205, 226, 246},
                {230, 190, 150}, {255, 0, 0}, {0, 255, 0}}) {
            float k = 1f - TvGlass.scrimAlpha(c[0], c[1], c[2]);
            double ratio = TvGlass.ratio(new double[] {255, 255, 255},
                    new double[] {c[0] * k, c[1] * k, c[2] * k});
            assertTrue(c[0] + "," + c[1] + "," + c[2] + ": " + ratio,
                    ratio >= TvGlass.CHECK_CONTRAST);
        }
    }

    // X7: the home row grows 11 px toward a selected end app.
    @Test public void plateStretch_firstApp_growsLeftOnly() {
        int[] s = TvGlass.plateStretch(0, 4, true, 11);
        assertEquals(11, s[0]);
        assertEquals(0, s[1]);
    }

    @Test public void plateStretch_lastApp_growsRightOnly() {
        int[] s = TvGlass.plateStretch(3, 4, true, 11);
        assertEquals(0, s[0]);
        assertEquals(11, s[1]);
    }

    @Test public void plateStretch_middleApp_orNoFocus_doesNotMove() {
        int[] mid = TvGlass.plateStretch(1, 4, true, 11);
        assertEquals(0, mid[0]);
        assertEquals(0, mid[1]);
        int[] none = TvGlass.plateStretch(0, 4, false, 11);
        assertEquals(0, none[0]);
        assertEquals(0, none[1]);
    }
}
