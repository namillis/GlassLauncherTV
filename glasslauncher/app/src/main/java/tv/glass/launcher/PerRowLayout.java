package tv.glass.launcher;

/** Home row and All apps geometry for 5, 6 or 7 icons per row (P1 "same-width
 *  bar", glass-tvos/design/perrow/VALUES.md). The favorites plate keeps one
 *  box at every count, x 50-1869 on a 1920 px screen; tiles, gaps and padding
 *  scale together in tvOS's proportions, with the same padding on all four
 *  sides of the plate. 6 per row is tvOS's own layout (250 x 150 tiles, gap
 *  48, padding 40, pitch 260 at 1080p).
 *
 *  <p>Pure: {@code RecyclingShelfView}, {@code AppDrawer} and
 *  {@code computeTileDims} all read one instance, so no view keeps its own
 *  copy of a size. Values are dp in the tables and px in the fields. */
final class PerRowLayout {

    // Per count: tile width, gap, padding, row pitch (dp; 1080p px = 2x).
    //                     5    6                             7
    private static final int[] TILE_DP  = {150, TvGlass.TILE_WIDTH_DP, 106};
    private static final int[] GAP_DP   = {28, TvGlass.TILE_GAP_DP, 22};
    private static final int[] PAD_DP   = {24, 20, 18};
    private static final int[] PITCH_DP = {156, HomeGlass.GRID_ROW_PITCH_DP, 112};

    /** All apps plate top (y 60 px), the same at every count. */
    static final int GRID_PLATE_TOP_DP = 30;
    /** Label room under an All apps tile, and space under the last row. */
    static final int GRID_LABEL_AREA_DP = 28;
    static final int GRID_BOTTOM_PAD_DP = 28;
    /** Plate corners: concentric (tile corner + padding) less tvOS's own
     *  tightening, so 6 per row keeps the home dock's 61 px and the All apps
     *  plate's 68 px (CORRECTIONS 13, references/grid section 3). */
    static final float HOME_RADIUS_TRIM_DP = 4.5f;
    static final float GRID_RADIUS_TRIM_DP = 1f;
    /** Plate corners follow the default icon roundness, not the user's, so
     *  the plate does not change shape with the Icon roundness setting. */
    static final int PLATE_CORNER_PERCENT = LayoutOptions.DEFAULT_CORNER_PERCENT;

    final int columns;
    final int tileW, tileH, tileCorner;
    final int gap, pad;
    /** Plate box, shared by the home row and the All apps favorites row. */
    final int plateLeft, plateW, plateH;
    final int homePlateBottomMargin;   // from the screen bottom
    final int gridPlateTop;            // content space, scroll 0
    final int homeRadius, gridRadius;
    /** All apps rows. */
    final int rowPitch, cellH, rowGap, gridFirstTileTop, gridBottomPad;

    private PerRowLayout(int columns, int screenW, float density, int cornerPercent) {
        this.columns = LayoutOptions.sanitizeColumns(columns);
        int i = this.columns - LayoutOptions.MIN_COLUMNS;
        int t = px(TILE_DP[i], density), g = px(GAP_DP[i], density), p = px(PAD_DP[i], density);
        int pitch = px(PITCH_DP[i], density);
        int margin = px(TvGlass.PLATE_MARGIN_DP, density);
        int box = Math.max(0, screenW - 2 * margin);
        int need = this.columns * t + (this.columns - 1) * g + 2 * p;
        if (need > box && need > 0) {
            // shortcut: a screen narrower than 960 dp scales everything down
            // evenly; no shipping TV has one (1080p and 720p are both 960 dp).
            float s = (float) box / need;
            t = Math.round(t * s); g = Math.round(g * s / 2f) * 2;
            p = Math.round(p * s); pitch = Math.round(pitch * s);
        }
        tileW = t;
        tileH = At4kHomeLayout.tileHeightPx(t);
        tileCorner = At4kHomeLayout.cornerRadiusPx(tileH, cornerPercent);
        gap = g;
        pad = p;
        plateLeft = margin;
        plateW = box;
        plateH = tileH + 2 * p;
        homePlateBottomMargin = px(TvGlass.PLATE_BOTTOM_DP, density);
        gridPlateTop = px(GRID_PLATE_TOP_DP, density);
        int plateCorner = At4kHomeLayout.cornerRadiusPx(tileH, PLATE_CORNER_PERCENT) + p;
        homeRadius = Math.round(plateCorner - HOME_RADIUS_TRIM_DP * density);
        gridRadius = Math.round(plateCorner - GRID_RADIUS_TRIM_DP * density);
        rowPitch = pitch;
        cellH = tileH + px(GRID_LABEL_AREA_DP, density);
        rowGap = pitch - cellH;
        gridFirstTileTop = gridPlateTop + p;
        gridBottomPad = px(GRID_BOTTOM_PAD_DP, density);
    }

    static PerRowLayout of(int columns, int screenW, float density, int cornerPercent) {
        return new PerRowLayout(columns, screenW, density, cornerPercent);
    }

    /** Column pitch: tile plus gap. */
    int stride() {
        return tileW + gap;
    }

    /** Single-row mode, where the row scrolls past the plate ends: the first
     *  cell's left edge inside the shelf (one gap) and the buffer kept between
     *  a focused tile and the shelf edge when the row scrolls (two gaps). At 6
     *  per row these are main's dp(24) and dp(48); 5 and 7 scale with the
     *  count's gap, the same way padding does. */
    int singleRowStart() {
        return gap;
    }

    int scrollEdge() {
        return 2 * gap;
    }

    /** Left edge of the first cell (cells are {@code stride} wide, the tile
     *  centered in each) for a row of {@code count} cells centered in a
     *  container {@code width} wide. A row that fits is centered, including
     *  one exactly as wide as the container. One that does not fit (single
     *  row mode) starts at {@link #singleRowStart()}. */
    int rowLeft(int width, int count) {
        int total = count * stride();
        if (total <= width) return (width - total) / 2;
        return singleRowStart();
    }

    /** Content-space top of All apps row {@code row} (0 = favorites). */
    int gridRowTop(int row) {
        return gridFirstTileTop + row * rowPitch;
    }

    private static int px(float dp, float density) {
        return Math.round(dp * density);
    }
}
