package tv.glass.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Clear "liquid glass" surface shared by the home favorites plate, the
 * drawer favorites row and the home status capsule.
 *
 * <p>The blurred, color-boosted wallpaper is drawn by a separate view
 * underneath (see {@link LiquidGlass}); this drawable adds what makes it read
 * as a slab of glass:
 * <ul>
 *   <li>a faint white body, brighter at the top, instead of a dark tint;</li>
 *   <li>a specular rim lit from the top-left, with a weaker bounce light at
 *       the bottom-right, like tvOS;</li>
 *   <li>a soft inner glow band just inside the rim, where real glass bends
 *       the most light.</li>
 * </ul>
 * No per-draw allocation; shaders are rebuilt only when the bounds change.
 */
final class FavoritesGlassDrawable extends Drawable {

    static final int STYLE_HOME = 0;
    static final int STYLE_GRID = 1;
    static final int STYLE_CAPSULE = 2;

    /** Pass as the corner radius for a full capsule (radius = height / 2). */
    static final float CAPSULE = -1f;

    private static final int[] HOME_FILL = {0x2EFFFFFF, 0x12FFFFFF, 0x0A000000};
    private static final int[] GRID_FILL = {0x24FFFFFF, 0x0EFFFFFF, 0x0A000000};
    private static final int[] CAPSULE_FILL = {0x33FFFFFF, 0x1AFFFFFF, 0x0F000000};
    private static final float[] FILL_STOPS = {0f, 0.45f, 1f};

    // Specular rim along the top-left to bottom-right diagonal: hot corner,
    // fading through the sides, then a weaker bounce at the far corner.
    private static final int[] HOME_RIM = {0xE6FFFFFF, 0x40FFFFFF, 0x1AFFFFFF, 0x73FFFFFF};
    private static final int[] GRID_RIM = {0xB3FFFFFF, 0x33FFFFFF, 0x14FFFFFF, 0x59FFFFFF};
    private static final float[] RIM_STOPS = {0f, 0.32f, 0.68f, 1f};

    // Inner glow just inside the rim, top-heavy.
    private static final int[] HOME_GLOW = {0x4DFFFFFF, 0x0DFFFFFF, 0x26FFFFFF};
    private static final int[] GRID_GLOW = {0x33FFFFFF, 0x0AFFFFFF, 0x1AFFFFFF};
    private static final float[] GLOW_STOPS = {0f, 0.5f, 1f};

    private final float cornerRadius;
    private final float rimWidth;
    private final float glowWidth;
    private final int[] fillColors;
    private final int[] rimColors;
    private final int[] glowColors;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Faint dark hairline on the very edge: the white rim alone vanishes
     *  over a white sky, this keeps the glass outlined there. */
    private final Paint edgeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawBounds = new RectF();
    private int drawableAlpha = 255;

    FavoritesGlassDrawable(float cornerRadius, float density, boolean quietGridStyle) {
        this(cornerRadius, density, quietGridStyle ? STYLE_GRID : STYLE_HOME);
    }

    FavoritesGlassDrawable(float cornerRadius, float density, int style) {
        this.cornerRadius = cornerRadius;
        this.rimWidth = Math.max(1f, density * 1.25f);
        this.glowWidth = Math.max(2f, density * 3f);
        this.fillColors = style == STYLE_GRID ? GRID_FILL
                : style == STYLE_CAPSULE ? CAPSULE_FILL : HOME_FILL;
        this.rimColors = style == STYLE_GRID ? GRID_RIM : HOME_RIM;
        this.glowColors = style == STYLE_GRID ? GRID_GLOW : HOME_GLOW;

        fill.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(glowWidth);
        edgeLine.setStyle(Paint.Style.STROKE);
        edgeLine.setStrokeWidth(Math.max(1f, density * 0.75f));
        edgeLine.setColor(0x2E000000);
    }

    private float radiusFor(Rect bounds) {
        return cornerRadius < 0f ? bounds.height() / 2f : cornerRadius;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.isEmpty()) return;
        fill.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                fillColors, FILL_STOPS, Shader.TileMode.CLAMP));
        // Light comes from the top-left: the rim is hottest at that corner
        // and picks up a weaker bounce at the bottom-right one. The
        // top-heavy inner glow below supplies the top-versus-bottom contrast.
        rim.setShader(new LinearGradient(bounds.left, bounds.top,
                bounds.right, bounds.bottom, rimColors, RIM_STOPS,
                Shader.TileMode.CLAMP));
        glow.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                glowColors, GLOW_STOPS, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float radius = radiusFor(bounds);
        drawBounds.set(bounds);
        int save = drawableAlpha < 255
                ? canvas.saveLayerAlpha(drawBounds, drawableAlpha)
                : canvas.save();
        try {
            canvas.drawRoundRect(drawBounds, radius, radius, fill);

            float glowInset = edgeLine.getStrokeWidth() + rimWidth + glowWidth / 2f;
            drawBounds.inset(glowInset, glowInset);
            float gr = Math.max(0f, radius - glowInset);
            canvas.drawRoundRect(drawBounds, gr, gr, glow);

            drawBounds.set(bounds);
            float edgeInset = edgeLine.getStrokeWidth() / 2f;
            drawBounds.inset(edgeInset, edgeInset);
            float er = Math.max(0f, radius - edgeInset);
            canvas.drawRoundRect(drawBounds, er, er, edgeLine);

            drawBounds.set(bounds);
            float rimInset = edgeLine.getStrokeWidth() + rimWidth / 2f;
            drawBounds.inset(rimInset, rimInset);
            float rr = Math.max(0f, radius - rimInset);
            canvas.drawRoundRect(drawBounds, rr, rr, rim);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override public void getOutline(Outline outline) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) outline.setEmpty();
        else outline.setRoundRect(bounds, radiusFor(bounds));
    }

    @Override public void setAlpha(int alpha) {
        drawableAlpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        rim.setColorFilter(colorFilter);
        glow.setColorFilter(colorFilter);
        edgeLine.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
