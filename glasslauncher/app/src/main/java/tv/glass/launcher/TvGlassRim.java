package tv.glass.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * tvOS glass edge: one line 3 px wide, brightest at the outermost pixel,
 * lit from the top (sides about half, bottom weaker), with no dark band
 * inside it. Drawn as three 1 px rings that ADD light to whatever is under
 * them (PorterDuff ADD).
 *
 * <p>Each ring's strength follows a vertical gradient: the top value along
 * the top edge, the side value from one corner radius down to one radius
 * above the bottom, the bottom value along the bottom edge. A capsule has no
 * light on its round ends (sim2: left end +2).
 *
 * <p>Color (v2): tvOS's line takes the picture's color (over black it adds
 * the same to R, G and B; over Matterhorn it turns blue). For a
 * {@link TvGlass.Rim#colored} rim on the API 26-30 path, each ring's shader
 * is the pre-blurred wallpaper atlas under the line, masked by the ring's
 * strength gradient (ComposeShader DST_IN), through one
 * ColorMatrixColorFilter ({@link TvGlass#rimMatrix}: per-channel scale,
 * saturation and offset). The filter works on unpremultiplied color, so
 * the gradient alpha scales the whole term. Elsewhere (live blur path,
 * system wallpaper, no atlas yet) the rings add plain gray: the black row.
 * Every API level draws from the atlas now ({@link GlassPath#liveBlur} is
 * false outside the debug force_live_glass switch).
 *
 * <p>Top boost (round 3, X1): a rim with {@link TvGlass.Rim#topBoost} adds
 * a second colored pass along its top only (it fades out over the corner
 * radius, sides and bottom get none). Per channel it adds
 * boost x min(1, x_c / {@link TvGlass#TOP_BOOST_KNEE}), so it is 0 over
 * black (the black row stays tvOS's +43) and the full boost over any
 * picture, brightening the dock line toward tvOS's.
 *
 * <p>Stretch (round 3, X7): {@link #setStretch} widens what is drawn past
 * the bounds on the left and right (default 0 = off). The home row uses it
 * to grow 11 px toward a selected end app; the parent must not clip.
 *
 * <p>Bounds must sit on whole pixels; the rings are then exactly 1 px each.
 * Over a translucent layer (an alpha animation) ADD reads as gray light;
 * shortcut: fine for 100-200 ms fades, use a RenderNode with the backdrop if
 * a long fade shows it.
 */
final class TvGlassRim extends Drawable {
    /** Where colored rims read the blurred wallpaper. Set by the activity
     *  while it is alive (cleared in onDestroy). */
    interface Backdrop {
        /** The frosted atlas, or null when there is none to draw from. */
        Bitmap atlas();
        int screenW();
        int screenH();
        /** Size the atlas was laid out for ({@code frostedAtlasSize}). */
        int[] atlasLayoutSize();
    }

    static Backdrop backdrop;

    private final TvGlass.Rim rim;
    private final float radius;   // < 0: capsule (height / 2)
    private final Paint[] ring = new Paint[3];
    private final Paint[] colorRing = new Paint[3];
    /** Top-boost passes ({@link TvGlass#topBoostPasses}) and their rings,
     *  [pass][ring] (null when the rim has no boost). */
    private final int[][] boostPass;
    private final Paint[][] boostRing;
    private final RectF rect = new RectF();
    private int alpha = 255;
    private int stretchLeft, stretchRight;
    /** Bounds plus the stretch: what is actually drawn. */
    private final Rect drawn = new Rect();

    // Colored-shader cache key.
    private Bitmap shaderAtlas;
    private int shaderX = Integer.MIN_VALUE, shaderY, shaderAlpha = -1;
    private final Rect shaderBounds = new Rect();
    private final int[] loc = new int[2];
    private final Matrix local = new Matrix();
    private BitmapShader pic;
    /** Where this drawable's canvas origin sits inside its callback view,
     *  when the view draws it under a translate (the All apps plate). */
    private int offsetX, offsetY;

    /** The caller draws this rim under {@code canvas.translate(x, y)} in
     *  its callback view; the picture sample follows. Cheap to change. */
    void setCanvasOffset(int x, int y) {
        offsetX = x;
        offsetY = y;
    }

    TvGlassRim(TvGlass.Rim rim, float radius) {
        this.rim = rim;
        this.radius = radius;
        ColorMatrixColorFilter filter = rim.colored
                ? new ColorMatrixColorFilter(new ColorMatrix(TvGlass.rimMatrix(rim))) : null;
        for (int i = 0; i < 3; i++) {
            ring[i] = strokePaint();
            if (filter != null) {
                Paint p = strokePaint();
                p.setFilterBitmap(true);
                p.setColorFilter(filter);
                colorRing[i] = p;
            }
        }
        if (filter != null && rim.topBoost != null) {
            boostPass = TvGlass.topBoostPasses(rim.topBoost);
            boostRing = new Paint[boostPass.length][3];
            for (int k = 0; k < boostPass.length; k++) {
                int[] ps = boostPass[k];
                ColorMatrixColorFilter boost = new ColorMatrixColorFilter(new ColorMatrix(
                        TvGlass.topBoostMatrix(new boolean[] {ps[1] == 1, ps[2] == 1, ps[3] == 1})));
                for (int i = 0; i < 3; i++) {
                    Paint p = strokePaint();
                    p.setFilterBitmap(true);
                    p.setColorFilter(boost);
                    boostRing[k][i] = p;
                }
            }
        } else {
            boostPass = null;
            boostRing = null;
        }
    }

    /** Draw {@code left} / {@code right} px past the bounds (0 = off, the
     *  default). Whole pixels keep the rings 1 px each. */
    void setStretch(int left, int right) {
        left = Math.max(0, left);
        right = Math.max(0, right);
        if (left == stretchLeft && right == stretchRight) return;
        stretchLeft = left;
        stretchRight = right;
        Rect b = getBounds();
        if (!b.isEmpty()) buildShaders(drawnRect(b));
        invalidateSelf();
    }

    private Rect drawnRect(Rect b) {
        drawn.set(b.left - stretchLeft, b.top, b.right + stretchRight, b.bottom);
        return drawn;
    }

    /** Strength mask for the top-boost rings: alpha = boost / 255. */
    private static int boostMask(int level, int alpha) {
        int v = Math.max(0, Math.min(255, Math.round(level * alpha / 255f)));
        return (v << 24) | 0xFFFFFF;
    }

    private static Paint strokePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        return p;
    }

    private float radiusFor(Rect b) {
        return radius < 0f ? b.height() / 2f : radius;
    }

    private static int gray(int level, int alpha) {
        int v = Math.max(0, Math.min(255, Math.round(level * alpha / 255f)));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** Strength mask for the colored rings: alpha = level / a. */
    private int mask(int level, int alpha) {
        float w = rim.a > 0f ? level / rim.a : 0f;
        int v = Math.max(0, Math.min(255, Math.round(255f * w * alpha / 255f)));
        return (v << 24) | 0xFFFFFF;
    }

    private interface Level { int of(int level); }

    private LinearGradient profile(Rect b, int i, Level f) {
        return profile(b, rim.top[i], rim.side[i], rim.bottom[i], f);
    }

    private LinearGradient profile(Rect b, int top, int side, int bottom, Level f) {
        float h = Math.max(1f, b.height());
        float r = Math.min(radiusFor(b), h / 2f);
        int[] colors = {f.of(top), f.of(side), f.of(side), f.of(bottom)};
        float[] stops = rim.capsule ? new float[] {0f, 0.2f, 0.8f, 1f}
                : new float[] {0f, Math.min(0.49f, r / h), 1f - Math.min(0.49f, r / h), 1f};
        return new LinearGradient(0f, b.top, 0f, b.bottom, colors, stops,
                Shader.TileMode.CLAMP);
    }

    private void buildShaders(Rect b) {
        for (int i = 0; i < 3; i++) {
            ring[i].setShader(profile(b, i, level -> gray(level, alpha)));
        }
        shaderAtlas = null;   // colored shaders follow the new bounds
    }

    /** The atlas to color from, or null to draw gray. */
    private Bitmap colorAtlas() {
        if (!rim.colored || GlassPath.liveBlur()) return null;
        Backdrop src = backdrop;
        if (src == null || !(getCallback() instanceof View)) return null;
        Bitmap atlas = src.atlas();
        if (atlas == null || atlas.isRecycled() || src.screenW() <= 0 || src.screenH() <= 0) {
            return null;
        }
        return atlas;
    }

    /** Point the colored rings' shaders at the atlas pixels under the line:
     *  atlas region px -> screen px -> this drawable's canvas. */
    private void updateColorShaders(Bitmap atlas, Rect b) {
        Backdrop src = backdrop;
        ((View) getCallback()).getLocationOnScreen(loc);
        int x = loc[0] + offsetX, y = loc[1] + offsetY;
        boolean same = atlas == shaderAtlas && alpha == shaderAlpha && b.equals(shaderBounds);
        if (same && x == shaderX && y == shaderY) return;
        shaderX = x;
        shaderY = y;
        int sw = src.screenW(), sh = src.screenH();
        int[] r = TvGlass.region(rim.source, sw, sh);
        int[] lay = src.atlasLayoutSize();
        float sx = (float) atlas.getWidth() / lay[0], sy = (float) atlas.getHeight() / lay[1];
        float rl = r[0] * sx, rt = r[1] * sy;
        float rw = (r[2] - r[0]) * sx, rh = (r[3] - r[1]) * sy;
        local.setTranslate(-rl, -rt);
        local.postScale(sw / rw, sh / rh);
        local.postTranslate(-x, -y);
        if (same && pic != null) {
            // Only the position moved (a scroll, the stretch, a slide):
            // move the picture sample, keep every shader.
            pic.setLocalMatrix(local);
            return;
        }
        shaderAtlas = atlas;
        shaderAlpha = alpha;
        shaderBounds.set(b);
        pic = new BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        pic.setLocalMatrix(local);
        for (int i = 0; i < 3; i++) {
            colorRing[i].setShader(new ComposeShader(pic,
                    profile(b, i, level -> mask(level, alpha)), PorterDuff.Mode.DST_IN));
            if (boostRing != null) {
                for (int k = 0; k < boostPass.length; k++) {
                    int level = Math.round(boostPass[k][0] * TvGlass.TOP_BOOST_RING_WEIGHT[i]);
                    boostRing[k][i].setShader(new ComposeShader(pic,
                            profile(b, level, 0, 0, lv -> boostMask(lv, alpha)),
                            PorterDuff.Mode.DST_IN));
                }
            }
        }
    }

    @Override protected void onBoundsChange(Rect b) {
        super.onBoundsChange(b);
        if (!b.isEmpty()) buildShaders(drawnRect(b));
    }

    @Override public void draw(Canvas canvas) {
        if (getBounds().isEmpty() || alpha == 0 || GlassPath.hideGlass()) return;
        Rect b = drawnRect(getBounds());
        Bitmap atlas = colorAtlas();
        Paint[] paints = ring;
        if (atlas != null) {
            updateColorShaders(atlas, b);
            paints = colorRing;
        }
        float r = radiusFor(b);
        for (int i = 0; i < 3; i++) {
            float inset = i + 0.5f;
            rect.set(b);
            rect.inset(inset, inset);
            float rr = Math.max(0f, r - inset);
            canvas.drawRoundRect(rect, rr, rr, paints[i]);
            if (atlas != null && boostRing != null) {
                for (Paint[] pass : boostRing) canvas.drawRoundRect(rect, rr, rr, pass[i]);
            }
        }
    }

    @Override public void getOutline(Outline outline) {
        Rect b = getBounds();
        if (b.isEmpty()) outline.setEmpty();
        else outline.setRoundRect(drawnRect(b), radiusFor(b));
    }

    /** Alpha scales the added light (a fade dims the rim, never grays it). */
    @Override public void setAlpha(int a) {
        int next = Math.max(0, Math.min(255, a));
        if (next == alpha) return;
        alpha = next;
        if (!getBounds().isEmpty()) buildShaders(drawnRect(getBounds()));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) { /* light, not color */ }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
