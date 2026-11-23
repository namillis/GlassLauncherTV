package tv.glass.launcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure-JVM checks of the glass color matrices (no Android drawing). */
public class LiquidGlassTest {

    private static double luminance(int r, int g, int b) {
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    }

    private static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static int over(int top, int alpha, int bottom) {
        return Math.round((top * alpha + bottom * (255 - alpha)) / 255f);
    }

    @Test public void saturationOne_isIdentity() {
        assertArrayEquals(new int[] {40, 120, 200},
                LiquidGlass.applyRgb(LiquidGlass.saturation(1f), 40, 120, 200));
    }

    @Test public void saturation_keepsGraysGray() {
        assertArrayEquals(new int[] {128, 128, 128},
                LiquidGlass.applyRgb(LiquidGlass.saturation(1.55f), 128, 128, 128));
    }

    @Test public void saturation_pushesColorsApart() {
        int[] out = LiquidGlass.applyRgb(LiquidGlass.saturation(1.35f), 80, 140, 200);
        assertTrue(out[0] < 80);
        assertTrue(out[2] > 200);
    }

    @Test public void adaptiveDarken_hitsBrightMoreThanDark() {
        float[] m = LiquidGlass.adaptiveDarken(0.55f);
        int[] white = LiquidGlass.applyRgb(m, 255, 255, 255);
        int[] dark = LiquidGlass.applyRgb(m, 30, 30, 30);
        assertEquals(115, white[0]);
        assertEquals(0, LiquidGlass.applyRgb(m, 0, 0, 0)[0]);
        assertTrue(dark[0] == 13 || dark[0] == 14);   // 13.5 before rounding
        // White loses 140 levels, dark gray only 16.
        assertTrue(255 - white[0] > 8 * (30 - dark[0]));
    }

    @Test public void then_appliesFirstMatrixFirst() {
        float[] m = LiquidGlass.then(LiquidGlass.scale(0.5f), LiquidGlass.scale(0.5f));
        assertArrayEquals(new int[] {50, 50, 50}, LiquidGlass.applyRgb(m, 200, 200, 200));
    }

    /** Worst case for the settings card: a pure white backdrop. The muted
     *  subtitle text (0xD9FFFFFF) on the lightest card tint (0x8C over the
     *  darkened backdrop, plus the 0x14 white row fill) must stay above
     *  4.5:1 (WCAG AA). */
    @Test public void card_mutedTextContrastOnWhiteBackdrop() {
        int backdrop = LiquidGlass.applyRgb(LiquidGlass.cardMatrix(), 255, 255, 255)[1];
        int card = over(0x1C, 0x8C, backdrop);
        int row = over(0xFF, 0x14, card);
        int text = over(0xFF, 0xD9, row);
        double ratio = (luminance(text, text, text) + 0.05) / (luminance(row, row, row) + 0.05);
        assertTrue("contrast " + ratio, ratio >= 4.5);
    }

    @Test public void grid_dimsBrightBackdrop() {
        int[] out = LiquidGlass.applyRgb(LiquidGlass.gridMatrix(), 240, 240, 240);
        assertTrue(out[0] < 240);
    }
}
