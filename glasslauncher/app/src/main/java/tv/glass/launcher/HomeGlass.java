package tv.glass.launcher;

/** Home screen geometry at tvOS sizes (design/structure/home/INDEX.md, picks
 *  H1B, H2B and H4B). Pixel values in the comments are for a 1920 x 1080
 *  screen at 320 dpi (2 px per dp). */
final class HomeGlass {
    private HomeGlass() {}

    // ── H1B clock bubble: x 1692-1869, y 50-109 ──────────────────────────
    static final int BUBBLE_HEIGHT_DP = 30;        // 60 px
    static final int BUBBLE_TOP_DP = 25;           // y 50
    static final int BUBBLE_END_DP = 25;           // right edge 1870
    static final int BUBBLE_MIN_WIDTH_DP = 89;     // 178 px with the time
    static final int BUBBLE_TIME_START_DP = 11;    // 22 px before the time
    static final int BUBBLE_TIME_GAP_DP = 7;       // 14 px between time and gear
    static final float BUBBLE_TIME_SP = 19f;       // Medium 38 px
    static final int GEAR_DP = 24;                 // 48 px round button
    static final int GEAR_INSET_DP = 3;            // 6 px to the bubble edge
    /** Network-lost badge: a 32 px glyph, 46 px step before the time. */
    static final int BADGE_DP = 16;
    static final int BADGE_STEP_DP = 23;
    /** Amber on the glyph only; the time stays white. */
    static final int BADGE_COLOR = 0xFFFFBE50;

    // ── H2B All apps: 250 x 150 tiles, pitch 298, rows 360/620/880 ───────
    /** Top of the favorites tiles (y 100, centered on the plate 60-290). */
    static final int GRID_FIRST_TILE_TOP_DP = 50;
    /** Tile top to tile top between rows (260 px). */
    static final int GRID_ROW_PITCH_DP = 130;
    /** Focused label, 31 px on screen after the 1.224 focus scale. */
    static final float GRID_LABEL_PX_ON_SCREEN = 31f;
    /** Label top 14 px under the scaled tile. */
    static final float GRID_LABEL_GAP_PX_ON_SCREEN = 14f;

    // ── H4B messages: 560 x 104 at x 1309-1869, y 126-230 ───────────────
    static final int TOAST_WIDTH_DP = 280;
    static final int TOAST_HEIGHT_DP = 52;
    /** 16 px under the bubble. */
    static final int TOAST_GAP_DP = 8;
    static final int TOAST_DOT_DP = 20;            // 40 px
    static final int TOAST_DOT_START_DP = 16;      // dot at x +32
    static final int TOAST_TEXT_START_DP = 46;     // text at x +92
    static final int TOAST_TEXT_END_DP = 20;
    static final float TOAST_TITLE_SP = 15.5f;     // Medium 31 px
    static final float TOAST_DETAIL_SP = 12.5f;    // Medium 25 px
    /** Second line gray, like Apple's notices. Counted by TvGlass TOAST. */
    static final int TOAST_DETAIL_TEXT = 0xFFD9D9D9;
    /** The launcher's accent, on the dot glyph only. */
    static final int TOAST_DOT_COLOR = 0xFF7DD3FC;
    static final int TOAST_SHOW_MS = 3000;
    static final int TOAST_ANIM_MS = 220;

    /** Top of the message box: under the bubble plus the gap. */
    static int toastTopDp() {
        return BUBBLE_TOP_DP + BUBBLE_HEIGHT_DP + TOAST_GAP_DP;
    }

    /** Splits a message into a title and an optional detail line at its
     *  first line break. Returns {title, detail-or-null}. */
    static String[] splitMessage(String msg) {
        if (msg == null) return new String[] {"", null};
        int nl = msg.indexOf('\n');
        if (nl < 0) return new String[] {msg.trim(), null};
        String detail = msg.substring(nl + 1).trim();
        return new String[] {msg.substring(0, nl).trim(), detail.isEmpty() ? null : detail};
    }
}
