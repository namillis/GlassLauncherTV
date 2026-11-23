package tv.glass.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * tvOS-style highlight on the focused app card: a soft glare across the top
 * of the card and a bright glass rim that fades toward the bottom.
 *
 * <p>Drawn by the cell itself over its banner, so it scales with the focus
 * animation for free. Shaders are built once per card size; drawing
 * allocates nothing.
 */
final class FocusSheen {

    private static final int[] GLARE = {0x3DFFFFFF, 0x14FFFFFF, 0x00FFFFFF};
    private static final float[] GLARE_STOPS = {0f, 0.32f, 0.6f};
    private static final int[] RIM = {0xCCFFFFFF, 0x47FFFFFF, 0x24FFFFFF};
    private static final float[] RIM_STOPS = {0f, 0.55f, 1f};

    private final Paint glare = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float width;
    private final float height;
    private final float corner;
    private final float rimWidth;

    FocusSheen(float width, float height, float corner, float density) {
        this.width = width;
        this.height = height;
        this.corner = corner;
        this.rimWidth = Math.max(1f, density * 1.5f);
        glare.setStyle(Paint.Style.FILL);
        glare.setShader(new LinearGradient(0f, 0f, 0f, height,
                GLARE, GLARE_STOPS, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
        rim.setShader(new LinearGradient(0f, 0f, 0f, height,
                RIM, RIM_STOPS, Shader.TileMode.CLAMP));
    }

    /** Draw over a card whose top-left corner is at ({@code left}, {@code top}). */
    void draw(Canvas canvas, float left, float top) {
        if (width <= 0f || height <= 0f) return;
        int save = canvas.save();
        try {
            canvas.translate(left, top);
            rect.set(0f, 0f, width, height);
            canvas.drawRoundRect(rect, corner, corner, glare);
            float inset = rimWidth / 2f;
            rect.inset(inset, inset);
            float r = Math.max(0f, corner - inset);
            canvas.drawRoundRect(rect, r, r, rim);
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
