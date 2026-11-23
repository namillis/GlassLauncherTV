package tv.glass.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * tvOS 27 focused-card highlight: a top-lit rim 3 screen px wide that adds
 * light (top +43, bottom +25, sides +4 L; {@link TvGlass#RIM_FOCUS}), with
 * no white glare over the art.
 *
 * <p>Drawn by the cell itself over its banner, so it scales with the focus
 * animation. Each ring is 1 / {@link TvGlass#FOCUS_SCALE} card px wide, so
 * it is 1 screen px at full focus. Shaders are built once per card size;
 * drawing allocates nothing.
 */
final class FocusSheen {

    private final Paint[] ring = new Paint[3];
    private final RectF rect = new RectF();
    private final float width;
    private final float height;
    private final float corner;
    private final float ringWidth;

    FocusSheen(float width, float height, float corner, float density) {
        this.width = width;
        this.height = height;
        this.corner = corner;
        this.ringWidth = 1f / TvGlass.FOCUS_SCALE;
        TvGlass.Rim rim = TvGlass.RIM_FOCUS;
        float s = height > 0f ? Math.min(0.49f, corner / height) : 0.25f;
        for (int i = 0; i < 3; i++) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ringWidth);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            p.setShader(new LinearGradient(0f, 0f, 0f, Math.max(1f, height),
                    new int[] {gray(rim.top[i]), gray(rim.side[i]), gray(rim.side[i]),
                            gray(rim.bottom[i])},
                    new float[] {0f, s, 1f - s, 1f}, Shader.TileMode.CLAMP));
            ring[i] = p;
        }
    }

    private static int gray(int v) {
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** Draw over a card whose top-left corner is at ({@code left}, {@code top}). */
    void draw(Canvas canvas, float left, float top) {
        if (width <= 0f || height <= 0f || GlassPath.hideGlass()) return;
        int save = canvas.save();
        try {
            canvas.translate(left, top);
            for (int i = 0; i < 3; i++) {
                float inset = ringWidth * (i + 0.5f);
                rect.set(0f, 0f, width, height);
                rect.inset(inset, inset);
                float r = Math.max(0f, corner - inset);
                canvas.drawRoundRect(rect, r, r, ring[i]);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
