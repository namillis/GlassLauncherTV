package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Settings text colors (G3, S1B, S2B, S3C) against the surface each one
 *  sits on: the Control Center's card glass at its contrast cap, and the
 *  flat full-screen pages. */
public class SettingsColorsTest {

    private static double[] rgb(int argb) {
        return new double[] {(argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff};
    }

    /** {@code argb} composited over the opaque color {@code under}. */
    private static double[] over(int argb, double[] under) {
        double a = (argb >>> 24) / 255.0;
        double[] c = rgb(argb), out = new double[3];
        for (int i = 0; i < 3; i++) out[i] = c[i] * a + under[i] * (1 - a);
        return out;
    }

    private static double contrast(int text, double[] surface) {
        return TvGlass.ratio(over(text, surface), surface);
    }

    private static void assertAtLeast(String what, double min, double ratio) {
        assertTrue(what + ": " + ratio, ratio >= min);
    }

    /** The brightest fill the card cap allows: a gray of that luminance. */
    private static double[] capFill() {
        double target = TvGlass.maxFillLuminance(TvGlass.CARD);
        double lo = 0, hi = 255;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (TvGlass.relLum(mid, mid, mid) <= target) lo = mid;
            else hi = mid;
        }
        return new double[] {lo, lo, lo};
    }

    /** Card glass over a white picture, exactly as TvGlass.bake draws it. */
    private static double[] fillOverWhite() {
        int[] px = {0xFFFFFFFF};
        TvGlass.bake(TvGlass.CARD, px, 1);
        return rgb(px[0]);
    }

    // ── G3: values on glass stay white-gray ──────────────────────────────

    @Test public void glassValues_areWhiteGray_noTint() {
        for (int c : new int[] {TvGlass.CARD_VALUE_TEXT, TvGlass.CARD_OFF_TEXT}) {
            assertEquals(0xFFFFFF, c & 0xFFFFFF);
            assertTrue("alpha " + (c >>> 24), (c >>> 24) >= 0xD9);
        }
    }

    @Test public void controlCenterText_keeps4_7to1_onTheGlassAtItsCap() {
        double[][] fills = {capFill(), fillOverWhite()};
        int[] lifts = {TvGlass.CARD_TILE_LIFT, TvGlass.CARD_ROW_LIFT};
        int[] texts = {SettingsPageColors.CC_LABEL, SettingsPageColors.CC_MUTED,
                TvGlass.CARD_VALUE_TEXT, TvGlass.CARD_OFF_TEXT};
        for (double[] fill : fills) {
            for (int lift : lifts) {
                double[] surface = over(lift, fill);
                for (int t : texts) {
                    assertAtLeast(Integer.toHexString(t) + " on lift " + Integer.toHexString(lift)
                            + " over gray " + Math.round(fill[1]), TvGlass.CHECK_CONTRAST,
                            contrast(t, surface));
                }
            }
        }
    }

    /** Why glass keeps white: the page gray would fail on the card. */
    @Test public void pageGray_wouldFailOnTheGlass() {
        double[] surface = over(TvGlass.CARD_TILE_LIFT, capFill());
        double ratio = contrast(0xFF8E8E93, surface);
        assertTrue("gray 142 on the card: " + ratio, ratio < TvGlass.CHECK_CONTRAST);
    }

    // ── S2B / S3C: the flat pages ────────────────────────────────────────

    @Test public void pageText_keeps4_7to1() {
        double[] page = rgb(SettingsPageColors.PAGE_BG);
        double[] row = rgb(SettingsPageColors.ROW_FILL);
        double[] pill = rgb(SettingsPageColors.FOCUS_PILL);
        double min = TvGlass.CHECK_CONTRAST;
        assertAtLeast("label on row", min, contrast(SettingsPageColors.LABEL, row));
        assertAtLeast("label on page", min, contrast(SettingsPageColors.LABEL, page));
        // The value gray (152, 152, 157): at least WCAG 4.5:1 on the rows
        // and the page, and the repo's 4.7:1 check on top.
        assertAtLeast("value on row, WCAG", 4.5, contrast(SettingsPageColors.VALUE, row));
        assertAtLeast("value on page, WCAG", 4.5, contrast(SettingsPageColors.VALUE, page));
        assertAtLeast("value on row", min, contrast(SettingsPageColors.VALUE, row));
        assertAtLeast("section / info on page", min, contrast(SettingsPageColors.VALUE, page));
        assertAtLeast("description on page", min, contrast(SettingsPageColors.MUTED, page));
        assertAtLeast("check mark on row", min, contrast(SettingsPageColors.CHECK, row));
        assertAtLeast("focused label", min, contrast(SettingsPageColors.LABEL_FOCUSED, pill));
        assertAtLeast("focused value", min, contrast(SettingsPageColors.VALUE_FOCUSED, pill));
    }

    /** The 57 px bold title is large text: WCAG's 3:1. */
    @Test public void pageTitle_isLargeText_3to1() {
        assertAtLeast("title on page", 3.0,
                contrast(SettingsPageColors.TITLE, rgb(SettingsPageColors.PAGE_BG)));
    }

    @Test public void pageValue_isGray_andNeutral() {
        int v = SettingsPageColors.VALUE;
        int r = (v >> 16) & 0xff, g = (v >> 8) & 0xff, b = v & 0xff;
        assertEquals(r, g);
        assertTrue("blue lift " + (b - r), b - r >= 0 && b - r <= 6);
    }

    @Test public void pageValue_stepperArrowsOnlyWhenFocused() {
        assertEquals("6", SettingsPageColors.pageValue("< 6 >", false).toString());
        assertEquals("‹ 6 ›", SettingsPageColors.pageValue("< 6 >", true).toString());
        assertEquals("Row and grid",
                SettingsPageColors.pageValue("< Row and grid >", false).toString());
        assertEquals("None  ›", SettingsPageColors.pageValue("None  ›", true).toString());
        assertEquals("<>", SettingsPageColors.pageValue("<>", true).toString());
        assertNull(SettingsPageColors.pageValue(null, true));
    }
}
