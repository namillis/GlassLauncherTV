package tv.glass.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/**
 * The launcher's typeface, Inter 4.1 (SIL Open Font License 1.1, see
 * Inter-LICENSE.txt and NOTICE.md), in the four weights the tvOS design
 * uses: Light, Regular, Medium and SemiBold (for every bold use).
 *
 * <p>Loaded once from res/font by {@link #init} (the activity's onCreate);
 * until then, or if a font fails to load, each getter returns the matching
 * system sans-serif. TextViews without an explicit typeface get Inter from
 * the theme's android:fontFamily.
 */
final class InterFonts {
    private InterFonts() {}

    private static Typeface light, regular, medium, semiBold;

    static void init(Context context) {
        if (regular != null) return;
        Resources res = context.getResources();
        light = load(res, R.font.inter_light);
        regular = load(res, R.font.inter_regular);
        medium = load(res, R.font.inter_medium);
        semiBold = load(res, R.font.inter_semibold);
    }

    private static Typeface load(Resources res, int id) {
        try {
            return res.getFont(id);
        } catch (RuntimeException e) {   // NotFoundException, a bad font file
            return null;
        }
    }

    static Typeface light() {
        return light != null ? light : Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    static Typeface regular() {
        return regular != null ? regular : Typeface.create("sans-serif", Typeface.NORMAL);
    }

    static Typeface medium() {
        return medium != null ? medium : Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static Typeface semiBold() {
        return semiBold != null ? semiBold : Typeface.create("sans-serif", Typeface.BOLD);
    }

    /** A span that sets one Typeface. TypefaceSpan(Typeface) needs API 28;
     *  this works from API 26 (the clock's AM/PM and date line). */
    static final class Span extends MetricAffectingSpan {
        private final Typeface face;

        Span(Typeface face) {
            this.face = face;
        }

        @Override public void updateDrawState(TextPaint tp) {
            apply(tp);
        }

        @Override public void updateMeasureState(TextPaint tp) {
            apply(tp);
        }

        private void apply(Paint p) {
            p.setTypeface(face);
        }
    }
}
