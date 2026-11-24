package tv.glass.launcher;

/**
 * tvOS 27.2 Liquid Glass materials, fitted on the tvOS Simulator over
 * Gabriel's wallpapers (prototype, round 2).
 *
 * <p>Every surface is one per-channel model: out = gain x blur(backdrop) +
 * offset, drawn as one ColorMatrix on the pre-blurred wallpaper bitmap
 * (API 26-30) or one RenderEffect color stage after the blur (API 31+).
 * The blur itself comes from the bitmap the surface samples
 * ({@link #SOURCE_COARSE} etc., built by WallpaperController).
 *
 * <p>Contrast wins over the tvOS fit: {@link #textSafeScale} finds the
 * extra darken k (0 &lt; k &lt;= 1) that keeps the surface's text at 4.5:1
 * over the brightest backdrop pixel under the surface. k is 1 (no change)
 * whenever the backdrop is dark enough, so the material only departs from
 * tvOS where it has to.
 *
 * <p>Pure Java, so the JVM unit tests check exactly what is drawn.
 */
final class TvGlass {
    private TvGlass() {}

    // ── Blur sources (regions of one atlas bitmap, see WallpaperController) ──
    /** 1/8 screen (240x135 at 1080p), box r2 x 5 passes: sigma ~3.2 px of
     *  its own = ~25 screen px. The favorites plate (tvOS dock, sigma 26). */
    static final int SOURCE_COARSE = 0;
    /** 1/4 screen (480x270), box r1 x 3: sigma 1.41 of its own = ~5.7 screen
     *  px. The status capsule (tvOS sigma 6). */
    static final int SOURCE_CAPSULE = 1;
    /** 1/4 screen (480x270), box r2 x 3: sigma 2.45 = ~9.8 screen px. The
     *  settings card, its tiles, the menu (tvOS CC tile sigma 10). */
    static final int SOURCE_CARD = 2;

    static final int[] SOURCE_DIVISOR = {8, 4, 4};
    static final int[] SOURCE_BOX_RADIUS = {2, 1, 2};
    static final int[] SOURCE_PASSES = {5, 3, 3};
    /** API 31+ RenderEffect.createBlurEffect radius for the same sigma:
     *  hwui uses sigma = 0.57735 x radius + 0.5, so radius = (sigma - 0.5) /
     *  0.57735 for sigma 26 / 6 / 10 (1080p px). */
    static final float[] LIVE_BLUR_RADIUS_PX = {44.2f, 9.5f, 16.5f};
    /** Rows of edge padding between atlas regions so bilinear sampling at a
     *  region's edge never reads its neighbor. */
    static final int ATLAS_GAP = 2;

    /** Atlas layout for a screen: {w, h, y0, y1, y2} (regions stacked). */
    static int[] atlasLayout(int screenW, int screenH) {
        int w0 = Math.max(120, screenW / 8), h0 = Math.max(68, screenH / 8);
        int w1 = Math.max(240, screenW / 4), h1 = Math.max(135, screenH / 4);
        int y1 = h0 + ATLAS_GAP;
        int y2 = y1 + h1 + ATLAS_GAP;
        return new int[] {Math.max(w0, w1), y2 + h1, 0, y1, y2};
    }

    /** {left, top, right, bottom} of a source region inside the atlas. */
    static int[] region(int source, int screenW, int screenH) {
        int[] a = atlasLayout(screenW, screenH);
        if (source == SOURCE_COARSE) {
            return new int[] {0, 0, Math.max(120, screenW / 8), Math.max(68, screenH / 8)};
        }
        int w = Math.max(240, screenW / 4), h = Math.max(135, screenH / 4);
        int y = source == SOURCE_CAPSULE ? a[3] : a[4];
        return new int[] {0, y, w, y + h};
    }

    // ── Materials ────────────────────────────────────────────────────────
    static final int PLATE = 0, CAPSULE = 1, CARD = 2, MENU = 3, GRID = 4, TOAST = 5;

    /** tvOS dims the home to 0.70x behind Control Center and the menu. */
    static final float OVERLAY_DIM = 0.70f;

    static final class Material {
        final String name;
        final int source;
        final float[] gain;     // per channel, dim folded in
        final float[] offset;   // per channel, 0-255
        /** Texts drawn on the surface (ARGB, alpha over what is under it),
         *  each with the white lift alpha it sits on (0 = straight on the
         *  fill). Every one must reach 4.5:1. */
        final int[] texts;
        final int[] lifts;
        /** Alpha of the first text, 0 when the surface has no text. */
        final int textAlpha;
        Material(String name, int source, float dim, float[] gain, float[] offset,
                 int[] texts, int[] lifts) {
            this.name = name;
            this.source = source;
            this.gain = new float[] {gain[0] * dim, gain[1] * dim, gain[2] * dim};
            this.offset = offset;
            this.texts = texts;
            this.lifts = lifts;
            this.textAlpha = texts.length > 0 ? texts[0] >>> 24 : 0;
        }
    }

    /** Settings card text: muted subtitles (0xD9 white) on a tile (0x1A white). */
    static final int CARD_MUTED_TEXT = 0xD9FFFFFF;
    static final int CARD_TILE_LIFT = 0x1AFFFFFF;
    /** Settings accent (glyphs, toggles, focus marks only). */
    static final int CARD_ACCENT_TEXT = 0xFF7DD3FC;
    /** Value text on settings glass (G3): white-gray 0xD9, no tint. Values
     *  on the flat settings pages are gray (SettingsPageColors.VALUE); gray
     *  142 on this glass would be about 2.2:1, so glass keeps white. */
    static final int CARD_VALUE_TEXT = 0xD9FFFFFF;
    static final int CARD_ROW_LIFT = 0x14FFFFFF;
    /** "Off" values on glass: the same 0xD9 white (0xB3 would cap the card at 72). */
    static final int CARD_OFF_TEXT = 0xD9FFFFFF;
    /** Menu rows 0xCC white; Uninstall red is on its glyph only. */
    static final int MENU_TEXT = 0xCCFFFFFF;
    static final int MENU_DESTRUCTIVE_TEXT = 0xFFFF6B6B;
    private static final int[] NONE = {};

    static final Material[] MATERIALS = {
            // Dock, sim2 joint model A0 (over the sharp picture: total blur).
            new Material("plate", SOURCE_COARSE, 1f,
                    new float[] {1.034f, 1.039f, 1.045f}, new float[] {17.8f, 17.9f, 16.3f},
                    NONE, NONE),
            // Capsule, sim2 joint: sigma 6. White time text straight on it.
            new Material("capsule", SOURCE_CAPSULE, 1f,
                    new float[] {0.821f, 0.792f, 0.790f}, new float[] {25.4f, 22.9f, 23.2f},
                    new int[] {0xFFFFFFFF}, new int[] {0}),
            // Control Center tile, sim2 joint: sigma 10 over the 0.70x home.
            new Material("card", SOURCE_CARD, OVERLAY_DIM,
                    new float[] {0.914f, 0.795f, 0.763f}, new float[] {18.9f, 23.0f, 19.9f},
                    new int[] {CARD_MUTED_TEXT, CARD_VALUE_TEXT, CARD_OFF_TEXT},
                    new int[] {CARD_TILE_LIFT >>> 24, CARD_ROW_LIFT >>> 24,
                            CARD_ROW_LIFT >>> 24}),
            // Context menu: no valid tvOS model (it lenses). Fallback: the
            // sim2 joint fit (sigma 10 over the 0.70x home); 0xCC white rows.
            new Material("menu", SOURCE_CARD, OVERLAY_DIM,
                    new float[] {0.708f, 0.592f, 0.659f}, new float[] {77.9f, 92.6f, 83.4f},
                    new int[] {MENU_TEXT}, new int[] {0}),
            // All apps grid: not measured. Borrows the dock material; labels
            // are white straight on it (their drop shadow is not counted).
            new Material("grid", SOURCE_COARSE, 1f,
                    new float[] {1.034f, 1.039f, 1.045f}, new float[] {17.8f, 17.9f, 16.3f},
                    new int[] {0xFFFFFFFF}, new int[] {0}),
            // Toast (H4B): CC tile material, no dim; white title, gray detail.
            new Material("toast", SOURCE_CARD, 1f,
                    new float[] {0.914f, 0.795f, 0.763f}, new float[] {18.9f, 23.0f, 19.9f},
                    new int[] {0xFFFFFFFF, HomeGlass.TOAST_DETAIL_TEXT}, new int[] {0, 0}),
    };

    /** 4x5 ColorMatrix: gain/offset per channel, times the extra darken k. */
    static float[] matrix(int surface, float k) {
        Material m = MATERIALS[surface];
        return new float[] {
                m.gain[0] * k, 0f, 0f, 0f, m.offset[0] * k,
                0f, m.gain[1] * k, 0f, 0f, m.offset[1] * k,
                0f, 0f, m.gain[2] * k, 0f, m.offset[2] * k,
                0f, 0f, 0f, 1f, 0f,
        };
    }

    /** The fill the matrix draws over one backdrop pixel, 0-255 per channel. */
    static float[] fill(int surface, float k, int r, int g, int b) {
        Material m = MATERIALS[surface];
        int[] c = {r, g, b};
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = Math.max(0f, Math.min(255f, k * (m.gain[i] * c[i] + m.offset[i])));
        }
        return out;
    }

    // ── Contrast (WCAG 2) ────────────────────────────────────────────────
    static final double MIN_CONTRAST = 4.5;
    /** What the unit tests require: 0.2 over 4.5 so hometv's 1-2 L offset
     *  from the emulator cannot push text under 4.5:1 (review S4). */
    static final double CHECK_CONTRAST = 4.7;
    /** What the cap aims for: the check plus margin for 8-bit rounding. */
    static final double TARGET_CONTRAST = 4.8;

    static double lin(double c255) {
        double s = c255 / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    static double relLum(double r, double g, double b) {
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    }

    static double ratio(double[] a, double[] b) {
        double la = relLum(a[0], a[1], a[2]), lb = relLum(b[0], b[1], b[2]);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double over(int alpha, double top, double bottom) {
        return (top * alpha + bottom * (255 - alpha)) / 255.0;
    }

    /** Contrast of the surface's worst text over one fill color. */
    static double textContrast(int surface, float[] fill) {
        Material m = MATERIALS[surface];
        double worst = 21;
        for (int t = 0; t < m.texts.length; t++) {
            int c = m.texts[t], a = c >>> 24;
            int[] tc = {(c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff};
            double[] bg = new double[3], text = new double[3];
            for (int i = 0; i < 3; i++) {
                bg[i] = over(m.lifts[t], 255, fill[i]);
                text[i] = over(a, tc[i], bg[i]);
            }
            worst = Math.min(worst, ratio(text, bg));
        }
        return worst;
    }

    /** Largest k in (0, 1] keeping the surface's text at
     *  {@link #TARGET_CONTRAST} over the backdrop pixel (r, g, b). Surfaces
     *  without text return 1. */
    static float textSafeScale(int surface, int r, int g, int b) {
        Material m = MATERIALS[surface];
        if (m.textAlpha == 0) return 1f;
        if (textContrast(surface, fill(surface, 1f, r, g, b)) >= TARGET_CONTRAST) return 1f;
        float lo = 0f, hi = 1f;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) / 2f;
            if (textContrast(surface, fill(surface, mid, r, g, b)) >= TARGET_CONTRAST) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    // ── Per-pixel text-safe fill (what the API 26-30 path draws) ───────────
    private static final float[] LIN = new float[257];
    static {
        for (int i = 0; i <= 256; i++) LIN[i] = (float) lin(Math.min(255, i));
    }

    private static float linF(float c) {
        if (c <= 0f) return 0f;
        if (c >= 255f) return LIN[255];
        int i = (int) c;
        float t = c - i;
        return LIN[i] + (LIN[i + 1] - LIN[i]) * t;
    }

    private static float relLumF(float r, float g, float b) {
        return 0.2126f * linF(r) + 0.7152f * linF(g) + 0.0722f * linF(b);
    }

    private static final float[] MAX_FILL_LUM = new float[MATERIALS.length];
    static {
        for (int s = 0; s < MATERIALS.length; s++) {
            if (MATERIALS[s].textAlpha == 0) {
                MAX_FILL_LUM[s] = 2f;   // no text: never capped
                continue;
            }
            float lo = 0f, hi = 255f;
            for (int i = 0; i < 30; i++) {
                float mid = (lo + hi) / 2f;
                if (textContrast(s, new float[] {mid, mid, mid}) >= TARGET_CONTRAST) lo = mid;
                else hi = mid;
            }
            MAX_FILL_LUM[s] = (float) relLum(lo, lo, lo);
        }
    }

    /** Gray level of the brightest fill the surface's text allows (for SPEC). */
    static float maxFillLuminance(int surface) {
        return MAX_FILL_LUM[surface];
    }

    /** The fill drawn over one backdrop pixel: the tvOS fit, scaled down
     *  only when that pixel is too bright for the surface's text. The cap is
     *  per pixel on an already-blurred backdrop, so it is smooth. out[3] is
     *  the scale applied (1 = the tvOS fit). */
    static float[] textSafeFill(int surface, int r, int g, int b, float[] out) {
        Material m = MATERIALS[surface];
        float fr = Math.max(0f, Math.min(255f, m.gain[0] * r + m.offset[0]));
        float fg = Math.max(0f, Math.min(255f, m.gain[1] * g + m.offset[1]));
        float fb = Math.max(0f, Math.min(255f, m.gain[2] * b + m.offset[2]));
        float max = MAX_FILL_LUM[surface];
        float k = 1f;
        for (int i = 0; i < 3; i++) {
            float y = relLumF(fr * k, fg * k, fb * k);
            if (y <= max) break;
            k *= (float) Math.pow(max / y, 1.0 / 2.2) * 0.995f;
        }
        out[0] = fr * k;
        out[1] = fg * k;
        out[2] = fb * k;
        out[3] = k;
        return out;
    }

    /** Bake the material into backdrop pixels in place (ARGB, opaque).
     *  Returns {lowest scale, fraction of pixels scaled below 0.99}. */
    static float[] bake(int surface, int[] argb, int count) {
        float[] f = new float[4];
        float minK = 1f;
        int capped = 0;
        for (int i = 0; i < count; i++) {
            int c = argb[i];
            textSafeFill(surface, (c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff, f);
            if (f[3] < minK) minK = f[3];
            if (f[3] < 0.99f) capped++;
            argb[i] = 0xFF000000 | (Math.round(f[0]) << 16) | (Math.round(f[1]) << 8)
                    | Math.round(f[2]);
        }
        return new float[] {minK, count > 0 ? (float) capped / count : 0f};
    }

    /** Index of the pixel whose fill is brightest (the worst case for white
     *  text) among ARGB pixels; -1 when empty. */
    static int brightestFill(int surface, int[] argb, int count) {
        Material m = MATERIALS[surface];
        int best = -1;
        double bestL = -1;
        for (int i = 0; i < count; i++) {
            int c = argb[i];
            double l = 0.2126 * m.gain[0] * ((c >> 16) & 0xff)
                    + 0.7152 * m.gain[1] * ((c >> 8) & 0xff)
                    + 0.0722 * m.gain[2] * (c & 0xff);
            if (l > bestL) {
                bestL = l;
                best = i;
            }
        }
        return best;
    }

    // ── Rims: additive light, outside -> inside, 3 px (L levels) ──────────
    // Measured on black (sim2 tables.md mid-top / mid-left / mid-bottom).
    // A constant add, not a white overlay, so the rim does not get brighter
    // over the brightest wallpaper.
    static final class Rim {
        /** Light added over black, L levels per ring (outside -> inside). */
        final int[] top, side, bottom;
        /** True for a capsule: no rim on its round ends. */
        final boolean capsule;
        /** Backdrop color term (v2, fitted on the sim2 stills; see
         *  design/material/v2/INDEX.md). The line adds, per channel c,
         *  w x (a + b x x_c + s x (x_c - Y(x))), where x = dim x the blurred
         *  backdrop at the line (atlas region {@link #source}) and w = the
         *  ring's level / a. Over black it is the gray {@link #top} etc.;
         *  over a blue picture it turns blue. colored = false: gray only. */
        final boolean colored;
        final float a, b, s, dim;
        final int source;
        /** Extra light along the top only, R G B on the outermost ring
         *  (inner rings x {@link #TOP_BOOST_RING_WEIGHT}), at full strength
         *  over any non-black picture; null = none. See {@link #topBoostAdd}.
         *  Colored rims only. */
        final int[] topBoost;
        Rim(int[] top, int[] side, int[] bottom, boolean capsule) {
            this(top, side, bottom, capsule, false, 0f, 0f, 0f, 1f, SOURCE_COARSE);
        }
        Rim(int[] top, int[] side, int[] bottom, boolean capsule, boolean colored,
            float a, float b, float s, float dim, int source) {
            this(top, side, bottom, capsule, colored, a, b, s, dim, source, null);
        }
        /** Optional 4x5 matrix applied to the atlas pixel before the color
         *  term (x = pre(atlas)); null = the atlas as it is. */
        final float[] pre;
        Rim(int[] top, int[] side, int[] bottom, boolean capsule, boolean colored,
            float a, float b, float s, float dim, int source, int[] topBoost) {
            this(top, side, bottom, capsule, colored, a, b, s, dim, source, topBoost, null);
        }
        Rim(int[] top, int[] side, int[] bottom, boolean capsule, boolean colored,
            float a, float b, float s, float dim, int source, int[] topBoost, float[] pre) {
            this.pre = pre;
            this.topBoost = topBoost;
            this.top = top;
            this.side = side;
            this.bottom = bottom;
            this.capsule = capsule;
            this.colored = colored;
            this.a = a;
            this.b = b;
            this.s = s;
            this.dim = dim;
            this.source = source;
        }
    }

    // v2 (sim2 per-channel fit, out+sat, clean rows only): plate a 41.0,
    // b 0.156, s 1.253 over the coarse blur; capsule 42.0 / 0.222 / 2.41;
    // CC tile 44.5 / 0.246 / 2.483 over the 0.70x dimmed home. Ring levels
    // are a x the fitted weight per ring and side.
    // X1 (round 3): the plate top also gets a top boost, Gabriel's
    // "brighter app-row top edge" (approval picture 3 item 1).
    static final Rim RIM_PLATE = new Rim(new int[] {41, 31, 20},
            new int[] {20, 17, 9}, new int[] {16, 9, 0}, false,
            // Top boost R G B on the outer ring. On the emulator (legacy
            // path, x 1300-1800) the line minus the glass under it was
            // Matterhorn +17 +29 +83 and Yosemite +48 +53 +42 against
            // tvOS's +12 +52 +109 and +51 +71 +82: both short by about
            // -4 +20 +27 and -3 +18 +40. Over black it already matches
            // tvOS (+41 vs +43), so the boost switches off near black.
            true, 41.0f, 0.156f, 1.253f, 1f, SOURCE_COARSE, new int[] {5, 18, 35});

    /** Top-boost strength per ring, outside -> inside (the plate top's
     *  fitted weights). */
    static final float[] TOP_BOOST_RING_WEIGHT = {1f, 0.75f, 0.49f};

    /** Backdrop level (per channel, 0-255) at which the top boost reaches
     *  full strength; below it the boost falls linearly to 0 at black. */
    static final float TOP_BOOST_KNEE = 15f;

    /** 4x5 ColorMatrix of one top-boost pass: per enabled channel
     *  255 x x_c / knee, clamped to 255 by the filter (so it saturates);
     *  the ring mask (level / 255) scales it. */
    static float[] topBoostMatrix(boolean[] channels) {
        float g = 255f / TOP_BOOST_KNEE;
        float[] m = new float[20];
        for (int c = 0; c < 3; c++) m[c * 6] = channels[c] ? g : 0f;
        m[18] = 1f;
        return m;
    }

    /** The boost drawn as stacked passes, because one ColorMatrix clamps
     *  every channel at the same 255: {level, R on, G on, B on} per pass
     *  (level = the outer ring's add), lowest channel first. The passes
     *  sum to {@link Rim#topBoost} per channel. */
    static int[][] topBoostPasses(int[] boost) {
        int[][] out = new int[3][];
        int n = 0, prev = 0;
        int[] sorted = boost.clone();
        java.util.Arrays.sort(sorted);
        for (int lv : sorted) {
            if (lv <= prev) continue;
            out[n++] = new int[] {lv - prev, boost[0] >= lv ? 1 : 0,
                    boost[1] >= lv ? 1 : 0, boost[2] >= lv ? 1 : 0};
            prev = lv;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** What the top boost adds on one ring over one backdrop pixel. */
    static float[] topBoostAdd(Rim r, int ring, int red, int green, int blue) {
        float[] out = new float[3];
        if (r.topBoost == null) return out;
        int[] x = {red, green, blue};
        for (int c = 0; c < 3; c++) {
            out[c] = r.topBoost[c] * TOP_BOOST_RING_WEIGHT[ring]
                    * Math.min(1f, Math.max(0f, x[c] / TOP_BOOST_KNEE));
        }
        return out;
    }

    // ── Big clock scrim (round 4) ────────────────────────────────────────
    /** White text straight on the wallpaper (the big clock) gets a soft
     *  black scrim only where the picture is too bright for it: the same
     *  per-pixel cap the glass uses, aiming for {@link #SCRIM_TARGET_CONTRAST}. */
    /** The scrim reads the blurred atlas (sigma about 10 px), but the text
     *  sits over the sharp picture, whose highlights (snow on branches)
     *  are much brighter than its blur. So it aims at 7:1 on the blur to
     *  keep 4.5:1 or more on those highlights; over a dark picture it is
     *  still 0 (the Matterhorn sky is about 0.02 relative luminance). */
    static final double SCRIM_TARGET_CONTRAST = 7.0;
    static final float SCRIM_MAX_LUM = (float) (1.05 / SCRIM_TARGET_CONTRAST - 0.05);

    /** Black overlay alpha (0-1) that brings one backdrop pixel down to
     *  {@link #SCRIM_MAX_LUM}; 0 when it is already dark enough. */
    static float scrimAlpha(int r, int g, int b) {
        return scrimAlpha(r, g, b, SCRIM_MAX_LUM);
    }

    /** {@link #scrimAlpha(int, int, int)} for any brightest allowed
     *  relative luminance. */
    static float scrimAlpha(int r, int g, int b, float maxLum) {
        float y = (float) relLum(r, g, b);
        if (y <= maxLum) return 0f;
        // Darkening by k scales each sRGB value; luminance follows ~k^2.2.
        float k = (float) Math.pow(maxLum / y, 1.0 / 2.2) * 0.99f;
        return Math.max(0f, Math.min(1f, 1f - k));
    }

    /** Declared before every static initializer that reads it. */
    private static final float[] LUMA = {0.2126f, 0.7152f, 0.0722f};

    // ── All apps, tvOS grid (references/grid/INDEX.md, round 4) ──────────
    /** Backdrop (section 2, sigma 60 joint fit): per channel
     *  out = gain x (Y + sat x (x_c - Y)) + offset over the blurred
     *  picture. No text-safe cap: the focused label is the only text on it
     *  and carries its own scrim ({@link #gridLabelScrimAlpha}). */
    static final float GRID_BACKDROP_GAIN = 0.67f;
    static final float GRID_BACKDROP_SAT = 2.0f;
    static final float[] GRID_BACKDROP_OFFSET = {87.8f, 87.5f, 87.2f};

    /** Plate interior (section 3): the grid backdrop under it x gain +
     *  offset, +22 over gray 86 and +26 to +32 over brighter pictures. */
    static final float GRID_PLATE_LIFT_GAIN = 1.08f;
    static final float GRID_PLATE_LIFT_OFFSET = 15f;
    /** Plate corner (section 3: diagonal inset 20, r about 68 px). The home
     *  plate keeps {@link #PLATE_RADIUS_DP}. */
    static final float GRID_PLATE_RADIUS_DP = 34f;

    /** 4x5 ColorMatrix of the grid backdrop. */
    static float[] gridBackdropMatrix() {
        float[] m = new float[20];
        for (int c = 0; c < 3; c++) {
            for (int j = 0; j < 3; j++) {
                float d = c == j ? 1f : 0f;
                m[c * 5 + j] = GRID_BACKDROP_GAIN
                        * (LUMA[j] + GRID_BACKDROP_SAT * (d - LUMA[j]));
            }
            m[c * 5 + 4] = GRID_BACKDROP_OFFSET[c];
        }
        m[18] = 1f;
        return m;
    }

    /** 4x5 ColorMatrix of the plate lift, applied to grid backdrop pixels. */
    static float[] gridPlateLiftMatrix() {
        float g = GRID_PLATE_LIFT_GAIN, o = GRID_PLATE_LIFT_OFFSET;
        return new float[] {
                g, 0f, 0f, 0f, o,
                0f, g, 0f, 0f, o,
                0f, 0f, g, 0f, o,
                0f, 0f, 0f, 1f, 0f,
        };
    }

    /** a x b for 4x5 color matrices (b first, then a), as
     *  ColorMatrix.postConcat would give, without the Android class. */
    static float[] concat(float[] a, float[] b) {
        float[] out = new float[20];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                float v = c == 4 ? a[r * 5 + 4] : 0f;
                for (int k = 0; k < 4; k++) v += a[r * 5 + k] * b[k * 5 + c];
                out[r * 5 + c] = v;
            }
        }
        return out;
    }

    /** One opaque ARGB pixel through a 4x5 matrix, clamped per channel. */
    static int applyMatrix(float[] m, int c) {
        int[] x = {(c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff};
        int out = 0xFF000000;
        for (int r = 0; r < 3; r++) {
            float v = m[r * 5 + 4];
            for (int j = 0; j < 3; j++) v += m[r * 5 + j] * x[j];
            out |= Math.max(0, Math.min(255, Math.round(v))) << (16 - 8 * r);
        }
        return out;
    }

    /** Bake the grid backdrop into blurred picture pixels in place. */
    static void bakeGridBackdrop(int[] argb, int count) {
        float[] m = gridBackdropMatrix();
        for (int i = 0; i < count; i++) argb[i] = applyMatrix(m, argb[i]);
    }

    /** Share of a tile that must be see-through (alpha &lt; 200) before its
     *  light ink is guarded. A card's own rounded corners are about 2%. */
    static final float SEE_THROUGH_MIN_SHARE = 0.10f;

    /** For a tile's ARGB pixels: {left, top, right, bottom} (pixels, right
     *  and bottom exclusive) of its light ink (alpha &gt;= 128, every channel
     *  &gt;= 190) when at least {@link #SEE_THROUGH_MIN_SHARE} of it is
     *  see-through; null for an opaque card or a logo with no light ink.
     *  The All apps grid puts the label scrim behind that box. */
    static float[] seeThroughInk(int[] argb, int w, int h) {
        if (argb == null || w <= 0 || h <= 0 || argb.length < w * h) return null;
        int clear = 0, l = w, t = h, r = -1, b = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                int a = c >>> 24;
                if (a < 200) clear++;
                if (a >= 128 && ((c >> 16) & 0xff) >= 190 && ((c >> 8) & 0xff) >= 190
                        && (c & 0xff) >= 190) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > b) b = y;
                }
            }
        }
        if (clear < SEE_THROUGH_MIN_SHARE * w * h || r < 0) return null;
        return new float[] {l, t, r + 1, b + 1};
    }

    /** The plate's left and right rims, fitted on the pictures (grid-check
     *  §4), not on gray. Over winter and yosemite tvOS's sides are as bright
     *  as its top and stay bright over a bright fill, so they add a constant
     *  on top of the mix toward white: ring_c = fill_c + w (310 - Y), w =
     *  level / 310 (about +17 plus 0.30 of the headroom on ring 1). Equal
     *  adds keep the fill's color. Over gray they read 170 150 128, brighter
     *  than tvOS's 142 131 118: the pictures win. */
    static final Rim RIM_GRID_PLATE_SIDES = new Rim(new int[] {0, 0, 0},
            new int[] {93, 62, 29}, new int[] {0, 0, 0}, false,
            true, 310f, -1f, 1f, 1f, SOURCE_COARSE, null,
            concat(gridPlateLiftMatrix(), gridBackdropMatrix()));

    /** What white label text needs: 4.8:1 (the check plus rounding margin). */
    static final float GRID_LABEL_MAX_LUM = (float) (1.05 / TARGET_CONTRAST - 0.05);

    /** Black scrim alpha (0-1) behind the focused label so white text keeps
     *  {@link #TARGET_CONTRAST} over the brightest backdrop pixel under it;
     *  0 over most of a dark picture, so it shows only where tvOS's own
     *  label falls under 4.5:1 (Gabriel's readable-text rule). */
    static float gridLabelScrimAlpha(int r, int g, int b) {
        return scrimAlpha(r, g, b, GRID_LABEL_MAX_LUM);
    }

    /** All apps plate rim (section 3, over the lifted fill): each ring mixes
     *  toward white by its level / 255, so it adds level/255 x (255 - Y) to
     *  every channel of the fill under it. Equal adds keep the fill's color,
     *  so the line takes the picture's color, and it adds less over a bright
     *  picture, as tvOS's does (over gray 86 raw top 178 158 139, sides 142
     *  131 118, bottom 135 124 around a 108 fill). The atlas it reads is the
     *  bare blurred picture, so {@link Rim#pre} first turns it into that
     *  fill (grid backdrop, then lift). */
    static final Rim RIM_GRID_PLATE = new Rim(new int[] {121, 87, 54},
            // Sides: RIM_GRID_PLATE_SIDES, drawn on top (the two gradients
            // still sum to one top-to-side blend through the corners).
            new int[] {0, 0, 0}, new int[] {47, 28, 10}, false,
            true, 255f, -1f, 1f, 1f, SOURCE_COARSE, null,
            concat(gridPlateLiftMatrix(), gridBackdropMatrix()));

    // ── Home row stretch (X7) ────────────────────────────────────────────
    /** tvOS grows the dock 11 px toward a selected end app (sim2: x 39 with
     *  the first app selected, 50 otherwise; 1880 with the last). */
    static final int PLATE_STRETCH_PX_AT_2X = 11;

    /** {left, right} px the home row grows: toward the first app when it
     *  is focused, toward the last when it is focused, nothing for a middle
     *  app or when the row has no focus. One app grows both ways. */
    static int[] plateStretch(int focused, int count, boolean rowFocused, int px) {
        if (!rowFocused || count <= 0 || focused < 0 || focused >= count) {
            return new int[] {0, 0};
        }
        return new int[] {focused == 0 ? px : 0, focused == count - 1 ? px : 0};
    }
    static final Rim RIM_CAPSULE = new Rim(new int[] {42, 25, 0},
            new int[] {0, 0, 0}, new int[] {16, 0, 0}, true,
            true, 42.0f, 0.222f, 2.41f, 1f, SOURCE_CAPSULE);
    /** Settings card and its tiles (tvOS Control Center tile). */
    static final Rim RIM_CARD = new Rim(new int[] {45, 35, 22},
            new int[] {20, 15, 7}, new int[] {16, 10, 0}, false,
            true, 44.5f, 0.246f, 2.483f, OVERLAY_DIM, SOURCE_CARD);


    /** 4x5 ColorMatrix (0-255 offsets) of a colored rim at weight 1: the
     *  light the line adds over one backdrop pixel. */
    static float[] rimMatrix(Rim r) {
        float[] m = new float[20];
        for (int c = 0; c < 3; c++) {
            for (int j = 0; j < 3; j++) {
                float d = c == j ? 1f : 0f;
                m[c * 5 + j] = r.dim * (r.b * d + r.s * (d - LUMA[j]));
            }
            m[c * 5 + 4] = r.a;
        }
        m[18] = 1f;
        return r.pre == null ? m : concat(m, r.pre);
    }

    /** What {@link #rimMatrix} adds over one backdrop pixel at weight w,
     *  clamped to 0-255 per channel as the color filter does. */
    static float[] rimAdd(Rim r, float w, int red, int green, int blue) {
        float[] m = rimMatrix(r);
        int[] x = {red, green, blue};
        float[] out = new float[3];
        for (int c = 0; c < 3; c++) {
            float v = m[c * 5 + 4];
            for (int j = 0; j < 3; j++) v += m[c * 5 + j] * x[j];
            out[c] = w * Math.max(0f, Math.min(255f, v));
        }
        return out;
    }
    /** Menu on black: raw top 248, 218, 185 over interior 131, left 228, 211,
     *  199 over 190 (sim2). Excess kept, monotonic at the bottom. */
    static final Rim RIM_MENU = new Rim(new int[] {117, 87, 54},
            new int[] {38, 21, 9}, new int[] {30, 20, 9}, false);
    /** Focused app card: top +43, bottom +25, sides +4 (sim/ measure1). */
    static final Rim RIM_FOCUS = new Rim(new int[] {43, 30, 17},
            new int[] {4, 3, 1}, new int[] {25, 17, 9}, false);

    // ── Geometry (1080p px at density 2.0; every resting edge whole px) ────
    static final int PLATE_MARGIN_DP = 25;      // x 50-1869 px
    static final int PLATE_HEIGHT_DP = 115;     // y 784-1013 px (230 tall)
    static final int PLATE_BOTTOM_DP = 33;      // 1080 - 1014 = 66 px
    static final float PLATE_RADIUS_DP = 30.5f; // 61 px
    static final int TILE_WIDTH_DP = 125;       // tvOS icon at rest, 250x150 px
    static final int TILE_GAP_DP = 24;          // pitch 298 px
    static final float FOCUS_SCALE = 1.224f;
    static final int FOCUS_DURATION_MS = 170;
    /** OvershootInterpolator tension: peak = 4T^3 / (27 (T+1)^2) of the
     *  scale step = 0.090 x 0.224 = +0.020, i.e. 1.6% of 1.224 (tvOS 1.5-2%). */
    static final float FOCUS_OVERSHOOT_TENSION = 1.6f;
}
