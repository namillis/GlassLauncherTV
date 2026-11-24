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
    /** Gap constant of the label position: with Inter's cap height the
     *  text top lands 23 px under the scaled tile, as tvOS's does
     *  (references/grid section 5; 14 gave 21). */
    static final float GRID_LABEL_GAP_PX_ON_SCREEN = 16f;
    /** tvOS grid scroll (references/grid section 6): the focused row top
     *  stays between y 180 and y 750 (1080p px). */
    static final int GRID_SCROLL_TOP_DP = 90;
    static final int GRID_SCROLL_BOTTOM_DP = 375;

    /** Scroll for a focused row whose content-space top is {@code top}:
     *  just enough to keep it between {@code bandTop} and {@code bandBottom}
     *  on screen. {@code plateRow} (grid row 1 under the favorites) always
     *  scrolls to 0. Departure: tvOS leaves the dock 180 px up when focus
     *  comes back to row 1, but Glass's favorites plate takes no focus from
     *  the keys, so row 1 is the only place it can be seen whole. The
     *  caller clamps to the content. */
    static int gridScrollTarget(int top, int scrollY, int bandTop, int bandBottom,
                                boolean plateRow) {
        if (plateRow) return 0;
        if (top - scrollY < bandTop) return top - bandTop;
        if (top - scrollY > bandBottom) return top - bandBottom;
        return scrollY;
    }

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
    /** The dot (a glyph; text stays white and gray): green as the approved
     *  H4B picture draws it (design/structure/home/src/make.py), red for a
     *  message that says something failed (Gabriel, 2026-11-23), the same
     *  red as the Uninstall glyph. */
    static final int TOAST_DOT_COLOR = 0xFF34C759;
    static final int TOAST_DOT_ERROR = TvGlass.MENU_DESTRUCTIVE_TEXT;

    static int toastDotColor(boolean error) {
        return error ? TOAST_DOT_ERROR : TOAST_DOT_COLOR;
    }
    static final int TOAST_SHOW_MS = 3000;
    static final int TOAST_ANIM_MS = 220;

    /** Top of the message box: under the bubble plus the gap. */
    static int toastTopDp() {
        return BUBBLE_TOP_DP + BUBBLE_HEIGHT_DP + TOAST_GAP_DP;
    }

    /** Each message line wraps to at most this many lines; the box grows
     *  past its 104 px only for text that needs it. */
    static final int TOAST_MAX_LINES = 2;
    /** Space above and below the text inside a grown box (12 px). */
    static final int TOAST_PAD_V_DP = 6;

    /** Splits a message into a title and an optional detail line: at its
     *  first line break, else after its first sentence (". "), so
     *  "Couldn't connect to X. Try again from system Settings." keeps its
     *  instruction as the detail. Returns {title, detail-or-null}. */
    static String[] splitMessage(String msg) {
        if (msg == null) return new String[] {"", null};
        // cut = where the title ends; the one character after it (the line
        // break, or the space after the period) is dropped.
        int cut = msg.indexOf('\n');
        if (cut < 0) {
            int dot = msg.indexOf(". ");
            if (dot >= 0) cut = dot + 1;   // the period stays on the title
        }
        if (cut < 0) return new String[] {msg.trim(), null};
        String title = msg.substring(0, cut).trim();
        String detail = msg.substring(cut + 1).trim();
        if (title.isEmpty()) return new String[] {detail, null};
        return new String[] {title, detail.isEmpty() ? null : detail};
    }
}
