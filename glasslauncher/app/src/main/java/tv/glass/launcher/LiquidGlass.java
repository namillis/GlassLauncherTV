package tv.glass.launcher;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

/**
 * Color treatments that make the blurred wallpaper behind a glass surface
 * read as clear tvOS-style glass instead of a dark tint.
 *
 * <p>Every treatment is a single 4x5 color matrix, so it costs one
 * ColorFilter on the paint (API 26-30) or one RenderEffect stage (API 31+).
 * Nothing runs per pixel on the CPU.
 *
 * <p>The matrix math lives in plain float arrays so the JVM unit tests can
 * check it without Android framework classes.
 */
final class LiquidGlass {

    private LiquidGlass() { /* no instances */ }

    // Rec. 709 luma weights, applied to sRGB values. Close enough for a
    // darkening curve and keeps the matrix linear.
    static final float LUMA_R = 0.2126f;
    static final float LUMA_G = 0.7152f;
    static final float LUMA_B = 0.0722f;

    /** Home favorites plate and status capsule: richer color, slight lift. */
    static final float PLATE_SATURATION = 1.35f;
    static final float PLATE_BRIGHTNESS = 1.05f;

    /** Settings card: a little more color, then darken by brightness so a
     *  bright sky turns mid-gray while a dark scene stays nearly untouched. */
    static final float CARD_SATURATION = 1.25f;
    static final float CARD_ADAPTIVE_DARKEN = 0.55f;

    /** All apps grid backdrop: vivid but dimmed so labels stay readable. */
    static final float GRID_SATURATION = 1.55f;
    static final float GRID_BRIGHTNESS = 0.85f;

    /** 4x5 saturation matrix (row-major, ColorMatrix layout). */
    static float[] saturation(float s) {
        float ir = (1f - s) * LUMA_R;
        float ig = (1f - s) * LUMA_G;
        float ib = (1f - s) * LUMA_B;
        return new float[] {
                ir + s, ig,     ib,     0f, 0f,
                ir,     ig + s, ib,     0f, 0f,
                ir,     ig,     ib + s, 0f, 0f,
                0f,     0f,     0f,     1f, 0f,
        };
    }

    /** Uniform RGB scale. */
    static float[] scale(float k) {
        return new float[] {
                k,  0f, 0f, 0f, 0f,
                0f, k,  0f, 0f, 0f,
                0f, 0f, k,  0f, 0f,
                0f, 0f, 0f, 1f, 0f,
        };
    }

    /** c' = c - k * luma(c). Bright pixels lose the most; black is unchanged.
     *  White maps to (1 - k), so the brightest possible backdrop is capped. */
    static float[] adaptiveDarken(float k) {
        float dr = k * LUMA_R, dg = k * LUMA_G, db = k * LUMA_B;
        return new float[] {
                1f - dr, -dg,     -db,     0f, 0f,
                -dr,     1f - dg, -db,     0f, 0f,
                -dr,     -dg,     1f - db, 0f, 0f,
                0f,      0f,      0f,      1f, 0f,
        };
    }

    /** {@code second} applied after {@code first}. Both are 4x5 matrices. */
    static float[] then(float[] first, float[] second) {
        float[] out = new float[20];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                float v = col == 4 ? second[row * 5 + 4] : 0f;
                for (int k = 0; k < 4; k++) {
                    v += second[row * 5 + k] * first[k * 5 + col];
                }
                out[row * 5 + col] = v;
            }
        }
        return out;
    }

    /** Apply a 4x5 matrix to one 0-255 RGB triple, clamped. For tests. */
    static int[] applyRgb(float[] m, int r, int g, int b) {
        int[] out = new int[3];
        for (int row = 0; row < 3; row++) {
            float v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b
                    + m[row * 5 + 3] * 255f + m[row * 5 + 4];
            out[row] = Math.max(0, Math.min(255, Math.round(v)));
        }
        return out;
    }

    /** Keeps the plate visible as gray glass over white clouds or snow. */
    static final float PLATE_ADAPTIVE_DARKEN = 0.14f;

    static float[] plateMatrix() {
        return then(then(saturation(PLATE_SATURATION), scale(PLATE_BRIGHTNESS)),
                adaptiveDarken(PLATE_ADAPTIVE_DARKEN));
    }

    /** Status capsule: the plate look, with bright skies toned down so the
     *  white time and icons on it stay readable. */
    static final float CAPSULE_ADAPTIVE_DARKEN = 0.3f;

    static float[] capsuleMatrix() {
        return then(plateMatrix(), adaptiveDarken(CAPSULE_ADAPTIVE_DARKEN));
    }

    static float[] cardMatrix() {
        return then(saturation(CARD_SATURATION), adaptiveDarken(CARD_ADAPTIVE_DARKEN));
    }

    static float[] gridMatrix() {
        return then(saturation(GRID_SATURATION), scale(GRID_BRIGHTNESS));
    }

    static ColorMatrixColorFilter filter(float[] matrix) {
        return new ColorMatrixColorFilter(new ColorMatrix(matrix));
    }
}
