package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** X6: the home-row focus glow's color and blur size. */
public class FocusGlowTest {

    @Test public void glowColor_isTheMeanOfOpaquePixels() {
        int[] px = {0xFF00807E, 0xFF06827F, 0x00FF0000, 0x40FFFFFF};
        int c = FocusGlow.glowColor(px, px.length);
        assertEquals(0xFF03817E, c);
    }

    @Test public void glowColor_withNoOpaquePixels_isGray() {
        int[] px = {0x00000000, 0x10FFFFFF};
        assertEquals(0xFF808080, FocusGlow.glowColor(px, px.length));
    }

    /** Three box passes of the chosen radius land within 10% of the sigma
     *  (screen px) at the focus scale. */
    @Test public void boxRadius_matchesTheSigma() {
        int r = FocusGlow.boxRadius(TvGlass.FOCUS_SCALE);
        double sigmaCard = Math.sqrt(FocusGlow.BOX_PASSES * ((2.0 * r + 1) * (2.0 * r + 1) - 1) / 12.0);
        double sigmaScreen = sigmaCard * TvGlass.FOCUS_SCALE;
        assertTrue("sigma " + sigmaScreen,
                Math.abs(sigmaScreen - FocusGlow.SIGMA_SCREEN_PX) < 0.1 * FocusGlow.SIGMA_SCREEN_PX);
    }
}
