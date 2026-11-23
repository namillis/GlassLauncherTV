package tv.glass.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * tvOS glow under the selected home-row app (round 3, X6; approval
 * picture 3 item 6): the app's own color, soft, around its card. Over black
 * tvOS reads about 9 59 58 one px outside a teal card and is back to the
 * background (13 12 12) by about 19 px.
 *
 * <p>Drawn as one ALPHA_8 mask (the card's rounded rect, box-blurred to a
 * Gaussian of {@link #SIGMA_SCREEN_PX} screen px) tinted with the card's
 * mean color at {@link #PEAK_ALPHA}. The mask is built once per card size;
 * drawing allocates nothing. Home row only: All apps keeps its plain focus.
 */
final class FocusGlow {
    /** Gaussian sigma of the glow on screen (1080p px): erfc falloff puts
     *  it near 0 at 19 px and about 0.46 of the peak at 1 px. */
    static final float SIGMA_SCREEN_PX = 9.2f;
    /** Glow alpha at the card edge before the blur halves it: 0.80 x 0.5 =
     *  0.40 just outside, which over black gives tvOS's 9 59 58 for a
     *  teal card of about 3 129 127. */
    static final int PEAK_ALPHA = 204;
    static final int BOX_PASSES = 3;

    /** Box radius (card px) whose {@link #BOX_PASSES} passes approximate
     *  the sigma at the focus scale: sigma^2 = passes ((2r+1)^2 - 1) / 12. */
    static int boxRadius(float focusScale) {
        double sigma = SIGMA_SCREEN_PX / focusScale;
        double side = Math.sqrt(12.0 * sigma * sigma / BOX_PASSES + 1.0);
        return Math.max(1, (int) Math.round((side - 1.0) / 2.0));
    }

    /** Mean color of the opaque pixels (alpha >= 128), opaque ARGB;
     *  mid gray when there are none. */
    static int glowColor(int[] argb, int count) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            int c = argb[i];
            if ((c >>> 24) < 128) continue;
            r += (c >> 16) & 0xff;
            g += (c >> 8) & 0xff;
            b += c & 0xff;
            n++;
        }
        if (n == 0) return 0xFF808080;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private final Bitmap mask;
    private final float margin;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    FocusGlow(int cardW, int cardH, float corner, float focusScale) {
        int r = boxRadius(focusScale);
        int m = r * BOX_PASSES + 2;
        margin = m;
        int w = cardW + 2 * m, h = cardH + 2 * m;
        Bitmap argb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFFFFFF);
        new Canvas(argb).drawRoundRect(new RectF(m, m, m + cardW, m + cardH),
                corner, corner, fill);
        int[] px = new int[w * h];
        argb.getPixels(px, 0, w, 0, 0, w, h);
        LegacyGridBlur.blurArgb(px, w, h, r, BOX_PASSES);
        argb.setPixels(px, 0, w, 0, 0, w, h);
        mask = argb.extractAlpha();
        argb.recycle();
    }

    /** Mean color of a card bitmap, sampled on a coarse grid. */
    static int glowColor(Bitmap card) {
        if (card == null || card.isRecycled() || card.getWidth() <= 0) return 0xFF808080;
        int sw = Math.min(32, card.getWidth()), sh = Math.min(20, card.getHeight());
        int[] px = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                px[y * sw + x] = card.getPixel(x * card.getWidth() / sw + card.getWidth() / (2 * sw),
                        y * card.getHeight() / sh + card.getHeight() / (2 * sh));
            }
        }
        return glowColor(px, px.length);
    }

    /** Draw around a card whose top-left corner is at ({@code left}, {@code top}). */
    void draw(Canvas canvas, float left, float top, int color) {
        if (GlassPath.hideGlass()) return;
        paint.setColor(color);
        paint.setAlpha(PEAK_ALPHA);
        canvas.drawBitmap(mask, left - margin, top - margin, paint);
    }
}
