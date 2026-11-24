package tv.glass.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Settings: the Control Center and the full-screen settings pages.
 *
 * <p>The first page (a {@link Page#controlCenter} page) is tvOS's Control
 * Center: separate glass tiles, rows and round buttons over the dimmed home
 * screen, with no card behind them (S1B). Every other page is a full-screen
 * list like tvOS Settings (S2B, S3C): a flat dark page, a gray title, a large
 * icon tile on the left that also describes the focused row, and the list on
 * the right. There are no key hints.
 *
 * <p>Every page is a list of {@link Row}s that the host rebuilds from current
 * state whenever the page is shown or refreshed, so adding a setting means
 * adding a row rather than new views and key handling. Consecutive
 * {@linkplain Row#tile tiles} share a two-column grid and consecutive
 * {@linkplain Row#button buttons} share one line, and the remote moves
 * between them in all four directions. The sheet owns the page stack (Back
 * returns to the row that opened a page), remote navigation and scrolling.
 *
 * <p>Geometry is in 1920 x 1080 design pixels (design/structure/cards/
 * INDEX.md), converted with {@link #px}: on a TV's 960 x 540 dp screen one
 * design pixel is half a dp.
 */
final class SettingsSheet {

    interface Host {
        FrameLayout root();
        Interpolator menuIn();
        Interpolator menuOut();
        boolean isLetThroughKey(int keyCode);
        /** Called as soon as the sheet starts closing. */
        void onSheetClosing();
        /** Called once the close animation has finished. */
        void onSheetHidden();
        /** A view that draws the wallpaper behind it, blurred, clipped to
         *  {@code cornerRadiusPx}. One per Control Center tile, row and
         *  button; may return null. */
        View createBackdrop(float cornerRadiusPx);
        /** Whether that backdrop has anything to draw right now (false while
         *  Android draws the system wallpaper). Asked on every open. */
        boolean canFrost();
        /** A background for white text that sits straight on the picture:
         *  darkens it softly only where it is too bright. May return null. */
        android.graphics.drawable.Drawable textScrim();
    }

    static final int VALUE_PLAIN = 0;
    static final int VALUE_ACCENT = 1;
    static final int VALUE_OFF = 2;
    static final int VALUE_LINK = 3;

    private static final int KIND_LIST = 0;
    private static final int KIND_TILE = 1;
    private static final int KIND_BUTTON = 2;
    private static final int KIND_SECTION = 3;

    // ── Control Center (S1B), design px ──────────────────────────────────
    /** Cluster x 1052-1868, so a focused tile grows to the 48 px margin. */
    private static final int CC_RIGHT_MARGIN = 52;
    /** M2 (design/structure/cc-compact): names only, the network and
     *  Bluetooth tiles keep one status word. Two 304 px half tiles fit
     *  "Audio connected" (disc 18+56+16, 194 px of text, 20). */
    private static final int CC_WIDTH = 624;
    /** 16 px under the status capsule (y 50-109). */
    private static final int CC_TOP = 125;
    private static final int CC_GAP = 16;
    private static final int CC_TILE_H = 88;
    private static final int CC_TILE_R = 43;
    private static final int CC_ROW_H = 66;
    private static final int CC_BUTTON = 96;
    /** Focus grows each side by this much (tvOS lift). */
    private static final int FOCUS_GROW = 4;

    /** Glass text on the Control Center: white or white-gray only. Gray
     *  values (SettingsPageColors.VALUE) would be about 2.2:1 here. */
    private static final int CC_LABEL = SettingsPageColors.CC_LABEL;
    private static final int CC_MUTED = SettingsPageColors.CC_MUTED;
    private static final int CC_SUB_FOCUSED = 0xFF3C3C42;
    /** M2: lighter than 0x2E so an off disc differs from the blue on disc
     *  by 14.6 L* over a bright picture (it was 7.3), and stays lighter
     *  than the tile glass. */
    private static final int CC_CHIP_IDLE = 0x14FFFFFF;
    /** No-network glyph: the Uninstall red, so it reads as a problem and
     *  not as a switch that is off; darker on the selected white tile. */
    private static final int CC_ALERT = TvGlass.MENU_DESTRUCTIVE_TEXT;
    private static final int CC_ALERT_SELECTED = 0xFFD63333;
    private static final int CC_CHIP_FOCUSED = 0x1E000000;
    /** Icon circle of a tile whose feature is on (network up, Bluetooth on). */
    static final int TILE_ICON_ACTIVE_BG = 0xFF3B82F6;
    /** Fill when there is no blurred wallpaper to draw (system wallpaper,
     *  first run): opaque enough for white text on any picture. */
    private static final int CC_SOLID = 0xF0262629;
    private static final int CC_BUTTON_LABEL_SHADOW = 0xA6000000;

    // ── Full-screen pages (S2B, S3C), design px ──────────────────────────
    private static final int FS_LIST_RIGHT = 1840;
    private static final int FS_LIST_WIDTH = 780;
    private static final int FS_LIST_TOP = 185;
    private static final int FS_ROW_H = 66;
    private static final int FS_ROW_GAP = 14;
    private static final int FS_TITLE_CY = 92;
    private static final int FS_PANE_CX = 525;
    private static final int FS_TILE = 540;
    private static final int FS_TILE_TOP = 237;
    private static final int FS_TILE_R = 110;
    private static final int FS_GLYPH = 220;
    private static final int FS_DESC_W = 640;
    /** Bottom of the list area: the 48 px safe margin. */
    private static final int FS_LIST_BOTTOM = 1032;

    // Inter (H3B); read per sheet, after the activity loaded the fonts.
    private final Typeface MEDIUM = InterFonts.medium();
    private final Typeface REGULAR = InterFonts.regular();
    private final Typeface BOLD = InterFonts.semiBold();

    /** One line of a page. */
    static final class Row {
        final String key;
        final CharSequence label;
        CharSequence description;
        CharSequence value;
        int valueStyle = VALUE_PLAIN;
        int valueColor;
        boolean dimmed;
        boolean info;
        int kind = KIND_LIST;
        int iconRes;
        boolean iconActive;
        /** Tile glyph in the problem red (no network), not the text color. */
        boolean iconAlert;
        /** What TalkBack says after the label when no second line shows
         *  it (M2: "Inputs, Apple TV, Apple TV, 5 more"). */
        CharSequence spoken;
        Bitmap icon;
        String iconKey;
        View iconView;
        int iconSizeDp = 22;
        Runnable activate;
        Runnable longActivate;
        IntConsumer step;

        Row(String key, CharSequence label) {
            this.key = key;
            this.label = label;
        }

        /** A line of muted text that the cursor skips. */
        static Row info(String key, CharSequence text) {
            Row row = new Row(key, text);
            row.info = true;
            return row;
        }

        /** A small caps heading over the rows that follow; the cursor skips it. */
        static Row section(String key, CharSequence text) {
            Row row = info(key, text);
            row.kind = KIND_SECTION;
            return row;
        }

        /** A Control Center tile in a two-column grid: icon in a circle,
         *  label, then the description as a status line. {@code active}
         *  fills the circle with the accent color. */
        static Row tile(String key, CharSequence label, int iconRes, boolean active) {
            Row row = new Row(key, label);
            row.kind = KIND_TILE;
            row.iconRes = iconRes;
            row.iconActive = active;
            return row;
        }

        /** A round icon button with its label underneath. Consecutive
         *  buttons share one line. */
        static Row button(String key, CharSequence label, int iconRes) {
            Row row = new Row(key, label);
            row.kind = KIND_BUTTON;
            row.iconRes = iconRes;
            return row;
        }

        Row description(CharSequence text) { description = text; return this; }
        Row spoken(CharSequence text) { spoken = text; return this; }
        Row iconAlert(boolean alert) { iconAlert = alert; return this; }
        Row value(CharSequence text, int style) { value = text; valueStyle = style; return this; }
        /** Kept for callers; values are gray, so a picked color no longer tints them. */
        Row valueColor(int color) { valueColor = color; return this; }
        /** Show the value faded, for a setting that has no effect right now. */
        Row dimmed(boolean faded) { dimmed = faded; return this; }
        /** App icon, or {@code null} with {@code packageKey} when it is still loading. */
        Row icon(Bitmap bitmap, String packageKey) { icon = bitmap; iconKey = packageKey; return this; }
        Row iconView(View view, int sizeDp) { iconView = view; iconSizeDp = sizeDp; return this; }
        /** A white vector icon, tinted with the row text. */
        Row iconRes(int res) { iconRes = res; return this; }
        Row onActivate(Runnable action) { activate = action; return this; }
        /** Hold-OK action. A row with one runs {@link #activate} when OK is
         *  released instead of when it goes down. */
        Row onLongActivate(Runnable action) { longActivate = action; return this; }
        /** Left/Right handler; OK also steps forward when there is no activate action. */
        Row onStep(IntConsumer handler) { step = handler; return this; }
    }

    /** A page: title, rows and an optional footer line. */
    static final class Page {
        final String id;
        final CharSequence title;
        /** Unused: settings screens have no key hints (S1B, S2B). */
        final CharSequence hint;
        final Supplier<List<Row>> rows;
        Supplier<CharSequence> footer;
        String initialKey;
        Runnable onLeave;
        /** Return true when the page handled Back itself. */
        BooleanSupplier onBack;
        /** Glyph in the full-screen page's icon tile. */
        int iconRes;
        /** Draw as the Control Center (glass tiles over the home), not as a
         *  full-screen list. */
        boolean controlCenter;

        Page(String id, CharSequence title, CharSequence hint, Supplier<List<Row>> rows) {
            this.id = id;
            this.title = title;
            this.hint = hint;
            this.rows = rows;
        }

        Page icon(int res) { iconRes = res; return this; }
    }

    private static final class Entry {
        final Page page;
        String selectedKey;

        Entry(Page page) { this.page = page; }
    }

    private static final class RowViews {
        View root;
        /** The child of the list holding this row (its grid line). */
        View line;
        TextView label;
        TextView description;
        TextView value;
        ImageView image;
        View iconView;
        ImageView glyph;
        View glyphCircle;
        /** Control Center glass: the blurred wallpaper and the rim. */
        View backdrop;
        Drawable rim;
        /** Fill or focus pill of the row, tile or button circle. */
        CellBackground bg;
        /** The view carrying {@link #bg} (the button's circle, else root). */
        View surface;
    }

    private final Activity activity;
    private final Host host;
    private final float density;
    private final List<Entry> stack = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<RowViews> views = new ArrayList<>();
    /** Grid position of each row: its line, its column and its line's width. */
    private final List<int[]> grid = new ArrayList<>();
    private int selected = -1;
    private boolean showing;
    /** Whether the current page is the Control Center. */
    private boolean ccMode;
    /** Whether the Control Center draws glass (else the solid fill). */
    private boolean frost;

    private FrameLayout overlay;
    private FrameLayout ccLayer;
    private LinearLayout ccColumn;
    private FrameLayout pageLayer;
    private TextView header;
    private ImageView pageGlyph;
    private TextView descTitle;
    private TextView descText;
    private ScrollView scroll;
    private LinearLayout column;
    private TextView footer;

    SettingsSheet(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    // ── Navigation ───────────────────────────────────────────────────────

    void show(Page root) {
        if (overlay == null) build();
        if (overlay == null) return;
        stack.clear();
        stack.add(new Entry(root));
        showing = true;
        frost = host.canFrost();
        ccMode = !root.controlCenter;   // force render() to set up the layers
        render(root.initialKey, 0);
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        overlay.requestFocus();
        overlay.animate().cancel();
        overlay.setAlpha(0f);
        overlay.animate()
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(host.menuIn())
                .start();
    }

    void hide() {
        if (!showing) return;
        showing = false;
        List<Entry> open = new ArrayList<>(stack);
        stack.clear();
        for (int i = open.size() - 1; i >= 0; i--) runLeave(open.get(i).page);
        host.onSheetClosing();
        final FrameLayout ov = overlay;
        ov.animate().cancel();
        ov.animate()
                .alpha(0f)
                .setDuration(160)
                .setInterpolator(host.menuOut())
                .withEndAction(() -> {
                    if (showing) return;
                    ov.setVisibility(View.GONE);
                    host.onSheetHidden();
                })
                .start();
    }

    void push(Page page) {
        if (!showing) return;
        Entry top = top();
        if (top != null) top.selectedKey = selectedKey();
        stack.add(new Entry(page));
        render(page.initialKey, 1);
    }

    void back() {
        Entry top = top();
        if (top == null) { hide(); return; }
        if (top.page.onBack != null && top.page.onBack.getAsBoolean()) return;
        if (stack.size() <= 1) { hide(); return; }
        stack.remove(stack.size() - 1);
        runLeave(top.page);
        Entry parent = top();
        if (parent != null) render(parent.selectedKey, -1);
    }

    /** Rebuild the current page's rows from state, keeping the selection. */
    void refresh() {
        Entry top = top();
        if (!showing || top == null) return;
        // A wallpaper change can add or remove the blur source while open.
        applySurface();
        List<Row> next = top.page.rows.get();
        String key = selectedKey();
        if (sameKeys(next)) {
            rows.clear();
            rows.addAll(next);
            for (int i = 0; i < rows.size(); i++) bind(views.get(i), rows.get(i));
            bindChrome(top.page);
            paint();
            return;
        }
        int oldIndex = selected;
        render(key, 0);
        if (indexOfKey(key) < 0 && !rows.isEmpty()) {
            selected = nearestSelectable(Math.min(Math.max(oldIndex, 0), rows.size() - 1));
            paint();
            scrollToSelected(false, 0);
        }
    }

    /** Re-pick glass or solid when the wallpaper changes under the open
     *  Control Center. Cheap when nothing changed. */
    void updateSurface() {
        if (showing && overlay != null) applySurface();
    }

    /** Show an app icon that finished loading after its row was drawn. */
    void deliverIcon(String packageKey, Bitmap bitmap) {
        if (!showing || packageKey == null || bitmap == null) return;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!packageKey.equals(row.iconKey)) continue;
            row.icon = bitmap;
            ImageView image = views.get(i).image;
            if (image != null) {
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE);
            }
        }
    }

    /** How long OK must be held on a row with a hold action; matches the
     *  home tiles' long press. */
    static final long LONG_PRESS_MS = 600;

    /** OK went down on a row with a hold action and hasn't fired yet. */
    private boolean okHoldArmed;

    /** Full key event entry point. Returns true when the sheet consumed it. */
    boolean handleKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (isOkKey(keyCode) && (okHoldArmed || selectedHasHoldAction())) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    okHoldArmed = true;
                } else if (okHoldArmed
                        && event.getEventTime() - event.getDownTime() >= LONG_PRESS_MS) {
                    okHoldArmed = false;
                    longActivate(selected);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                boolean shortPress = okHoldArmed;
                okHoldArmed = false;
                if (shortPress) activate(selected);
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) return handleKey(keyCode);
        return false;
    }

    private static boolean isOkKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private boolean selectedHasHoldAction() {
        return selected >= 0 && selected < rows.size() && rows.get(selected).longActivate != null;
    }

    boolean handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                step(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                step(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                activate(selected);
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                back();
                return true;
            default:
                return !host.isLetThroughKey(keyCode);
        }
    }

    // ── State for the activity and tests ─────────────────────────────────

    boolean isShowing() { return showing; }

    String pageId() {
        Entry top = top();
        return top != null ? top.page.id : null;
    }

    int rowCount() { return rows.size(); }

    String rowKey(int index) { return rows.get(index).key; }

    CharSequence rowLabel(int index) { return rows.get(index).label; }

    CharSequence rowValue(int index) { return rows.get(index).value; }

    CharSequence rowDescription(int index) { return rows.get(index).description; }

    int selectedIndex() { return selected; }

    String selectedKey() {
        return selected >= 0 && selected < rows.size() ? rows.get(selected).key : null;
    }

    int indexOfKey(String key) {
        if (key == null) return -1;
        for (int i = 0; i < rows.size(); i++) if (key.equals(rows.get(i).key)) return i;
        return -1;
    }

    /** Select the row with {@code key} and run it as if OK was pressed. */
    boolean activateKey(String key) {
        int index = indexOfKey(key);
        if (index < 0 || rows.get(index).info) return false;
        selected = index;
        paint();
        activate(index);
        return true;
    }

    /** The current page's title (the Control Center has none on screen). */
    CharSequence titleText() {
        Entry top = top();
        return top != null ? top.page.title : null;
    }

    /** Always null: settings screens show no key hints. */
    CharSequence hintText() { return null; }

    CharSequence footerText() {
        return footer != null && footer.getVisibility() == View.VISIBLE ? footer.getText() : null;
    }

    View sheetView() { return overlay; }

    View rowView(int index) { return views.get(index).root; }

    // ── Building ─────────────────────────────────────────────────────────

    private void build() {
        FrameLayout root = host.root();
        if (root == null) return;
        FrameLayout ov = new FrameLayout(activity) {
            @Override public boolean onTouchEvent(MotionEvent ev) {
                LinearLayout cc = ccColumn;
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ccMode && cc != null
                        && (ev.getX() < cc.getLeft() || ev.getY() > cc.getBottom())) {
                    hide();
                }
                return true;
            }
        };
        ov.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ov.setVisibility(View.GONE);
        ov.setClickable(true);
        ov.setFocusable(true);
        ov.setClipChildren(false);

        // Control Center: no card, the tiles float over the home that the
        // activity's shared backdrop dims.
        FrameLayout cc = new FrameLayout(activity);
        cc.setClipChildren(false);
        ov.addView(cc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout ccCol = new LinearLayout(activity);
        ccCol.setOrientation(LinearLayout.VERTICAL);
        ccCol.setClipChildren(false);
        FrameLayout.LayoutParams ccLp = new FrameLayout.LayoutParams(
                px(CC_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        ccLp.topMargin = px(CC_TOP);
        ccLp.rightMargin = px(CC_RIGHT_MARGIN);
        cc.addView(ccCol, ccLp);

        // Full-screen page: flat dark background, title, icon tile and the
        // focused row's description on the left, the list on the right.
        FrameLayout page = new FrameLayout(activity);
        page.setBackgroundColor(SettingsPageColors.PAGE_BG);
        page.setVisibility(View.GONE);
        ov.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(57, BOLD);
        title.setTextColor(SettingsPageColors.TITLE);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px(80), Gravity.TOP);
        titleLp.topMargin = px(FS_TITLE_CY - 40);
        titleLp.leftMargin = px(96);
        titleLp.rightMargin = px(96);
        page.addView(title, titleLp);

        FrameLayout tile = new FrameLayout(activity);
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setCornerRadius(px(FS_TILE_R));
        tileBg.setColor(SettingsPageColors.ICON_TILE);
        tile.setBackground(tileBg);
        ImageView glyph = new ImageView(activity);
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        glyph.setColorFilter(SettingsPageColors.ICON_GLYPH);
        tile.addView(glyph, new FrameLayout.LayoutParams(px(FS_GLYPH), px(FS_GLYPH), Gravity.CENTER));
        FrameLayout.LayoutParams tileLp = new FrameLayout.LayoutParams(px(FS_TILE), px(FS_TILE));
        tileLp.leftMargin = px(FS_PANE_CX - FS_TILE / 2);
        tileLp.topMargin = px(FS_TILE_TOP);
        page.addView(tile, tileLp);

        LinearLayout pane = new LinearLayout(activity);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView dTitle = text(36, MEDIUM);
        dTitle.setTextColor(SettingsPageColors.LABEL);
        dTitle.setSingleLine(true);
        dTitle.setEllipsize(TextUtils.TruncateAt.END);
        dTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        pane.addView(dTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView dText = text(28, REGULAR);
        dText.setTextColor(SettingsPageColors.MUTED);
        dText.setMaxLines(3);
        dText.setEllipsize(TextUtils.TruncateAt.END);
        dText.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dTextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dTextLp.topMargin = px(6);
        pane.addView(dText, dTextLp);
        TextView note = text(24, REGULAR);
        note.setTextColor(SettingsPageColors.MUTED);
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        note.setVisibility(View.GONE);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = px(24);
        pane.addView(note, noteLp);
        FrameLayout.LayoutParams paneLp = new FrameLayout.LayoutParams(
                px(FS_DESC_W), ViewGroup.LayoutParams.WRAP_CONTENT);
        paneLp.leftMargin = px(FS_PANE_CX - FS_DESC_W / 2);
        paneLp.topMargin = px(FS_TILE_TOP + FS_TILE + 26);
        page.addView(pane, paneLp);

        // The list scrolls inside x 1052-1848 so the focus pill's 4 px grow
        // is never clipped; rows sit at x 1060-1840 from y 185.
        int pad = 2 * FOCUS_GROW;
        // Wide enough sideways for the pill's soft shadow (3 sigma = 42).
        int side = 48;
        ScrollView sv = new ScrollView(activity);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(px(40));
        sv.setFocusable(false);
        sv.setClipToPadding(false);
        // The list keeps the rows' width; the pill grows past it.
        sv.setClipChildren(false);
        sv.setPadding(px(side), px(pad), px(side), px(pad));
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        // Its padding would clip the focus pill's grow to the row edges.
        col.setClipToPadding(false);
        sv.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int listTop = 140;
        col.setPadding(0, px(FS_LIST_TOP - listTop - pad), 0, 0);
        FrameLayout.LayoutParams svLp = new FrameLayout.LayoutParams(
                px(FS_LIST_WIDTH + 2 * side), px(FS_LIST_BOTTOM - listTop + pad), Gravity.END | Gravity.TOP);
        svLp.topMargin = px(listTop);
        svLp.rightMargin = px(1920 - FS_LIST_RIGHT - side);
        page.addView(sv, svLp);

        root.addView(ov);
        overlay = ov;
        ccLayer = cc;
        ccColumn = ccCol;
        pageLayer = page;
        header = title;
        pageGlyph = glyph;
        descTitle = dTitle;
        descText = dText;
        scroll = sv;
        column = col;
        footer = note;
    }

    /** Glass on the Control Center when the host can draw it, else the
     *  solid fill. Rechecked on every open and wallpaper change. */
    private void applySurface() {
        boolean next = host.canFrost();
        if (next != frost) {
            frost = next;
            if (ccMode) paint();
            return;
        }
        if (frost && ccMode) {
            for (RowViews v : views) if (v != null && v.backdrop != null) v.backdrop.invalidate();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────

    private void render(String selectKey, int direction) {
        Entry top = top();
        if (top == null || column == null) return;
        boolean wasCc = ccMode;
        ccMode = top.page.controlCenter;
        okHoldArmed = false;
        rows.clear();
        rows.addAll(top.page.rows.get());
        LinearLayout list = ccMode ? ccColumn : column;
        list.removeAllViews();
        views.clear();
        grid.clear();
        for (int i = 0; i < rows.size(); i++) views.add(null);
        int line = 0;
        for (int i = 0; i < rows.size(); ) {
            int kind = rows.get(i).kind;
            int end = i + 1;
            if (ccMode && kind == KIND_TILE) end = Math.min(i + 2, runEnd(i, KIND_TILE));
            else if (ccMode && kind == KIND_BUTTON) end = runEnd(i, KIND_BUTTON);
            View lineView;
            if (end - i == 1 && !(ccMode && kind == KIND_TILE)) {
                RowViews v = buildRow(rows.get(i));
                v.line = v.root;
                views.set(i, v);
                lineView = v.root;
            } else {
                LinearLayout strip = new LinearLayout(activity);
                strip.setOrientation(LinearLayout.HORIZONTAL);
                strip.setBaselineAligned(false);
                strip.setClipChildren(false);
                for (int k = i; k < end; k++) {
                    RowViews v = buildRow(rows.get(k));
                    v.line = strip;
                    views.set(k, v);
                    LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                            0, kind == KIND_TILE ? px(CC_TILE_H) : ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (k > i && kind == KIND_TILE) cellLp.setMarginStart(px(CC_GAP));
                    strip.addView(v.root, cellLp);
                }
                // A lone last tile keeps half the width, like the tiles above it.
                if (kind == KIND_TILE && end - i == 1) {
                    LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(0, px(CC_TILE_H), 1f);
                    padLp.setMarginStart(px(CC_GAP));
                    strip.addView(new View(activity), padLp);
                }
                lineView = strip;
            }
            for (int k = i; k < end; k++) grid.add(new int[]{ line, k - i, end - i });
            list.addView(lineView, lineParams(rows.get(i)));
            line++;
            i = end;
        }
        bindChrome(top.page);
        int index = indexOfKey(selectKey);
        selected = index >= 0 && !rows.get(index).info ? index : nearestSelectable(0);
        paint();
        if (!ccMode) {
            scroll.scrollTo(0, 0);
            scrollToSelected(false, 0);
        }
        switchLayers(wasCc, direction);
    }

    /** Height and spacing of one grid line. */
    private LinearLayout.LayoutParams lineParams(Row row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (ccMode) {
            switch (row.kind) {
                case KIND_SECTION:
                    lp.height = 0;
                    break;
                case KIND_TILE:
                    lp.height = px(CC_TILE_H);
                    lp.bottomMargin = px(CC_GAP);
                    break;
                case KIND_BUTTON:
                    break;
                default:
                    if (row.info) break;
                    lp.height = px(CC_ROW_H);
                    lp.bottomMargin = px(CC_GAP);
                    break;
            }
            return lp;
        }
        if (row.kind == KIND_SECTION) {
            // tvOS section head: 18 px above, cap line centered 14 px down.
            lp.height = px(28);
            lp.topMargin = px(18);
            lp.bottomMargin = px(22);
        } else {
            // Info lines can wrap to 2 lines: they take the height they need,
            // never less than a row (buildPageInfo sets the 66 px minimum),
            // so a one-line info row sits exactly where it did.
            lp.height = row.info ? ViewGroup.LayoutParams.WRAP_CONTENT : px(FS_ROW_H);
            lp.bottomMargin = px(FS_ROW_GAP);
        }
        return lp;
    }

    /** Show the layer the current page uses; fade the page over the
     *  Control Center (and back), slide between two pages. */
    private void switchLayers(boolean wasCc, int direction) {
        if (ccMode) {
            ccLayer.setVisibility(View.VISIBLE);
            if (pageLayer.getVisibility() == View.VISIBLE && direction != 0) {
                pageLayer.animate().cancel();
                pageLayer.animate().alpha(0f).setDuration(140).setInterpolator(host.menuOut())
                        .withEndAction(() -> { if (ccMode) pageLayer.setVisibility(View.GONE); })
                        .start();
            } else {
                pageLayer.animate().cancel();
                pageLayer.setVisibility(View.GONE);
            }
            return;
        }
        pageLayer.setVisibility(View.VISIBLE);
        if (wasCc && direction != 0) {
            pageLayer.animate().cancel();
            pageLayer.setAlpha(0f);
            pageLayer.animate().alpha(1f).setDuration(160).setInterpolator(host.menuIn())
                    .withEndAction(() -> { if (!ccMode) ccLayer.setVisibility(View.GONE); })
                    .start();
            return;
        }
        pageLayer.animate().cancel();
        pageLayer.setAlpha(1f);
        ccLayer.setVisibility(View.GONE);
        if (direction != 0) {
            column.animate().cancel();
            column.setAlpha(0f);
            column.setTranslationX(px(32) * direction);
            column.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(160)
                    .setInterpolator(host.menuIn())
                    .start();
        }
    }

    /** First index after {@code from} whose row is not of {@code kind}. */
    private int runEnd(int from, int kind) {
        int end = from;
        while (end < rows.size() && rows.get(end).kind == kind) end++;
        return end;
    }

    private void bindChrome(Page page) {
        if (ccMode) return;
        header.setText(page.title);
        pageGlyph.setImageResource(page.iconRes != 0 ? page.iconRes : R.drawable.ic_btp_settings);
        CharSequence note = page.footer != null ? page.footer.get() : null;
        footer.setText(note);
        footer.setVisibility(note == null || note.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private RowViews buildRow(Row row) {
        if (ccMode) {
            switch (row.kind) {
                case KIND_SECTION: return buildHidden(row);
                case KIND_TILE:    return buildCcTile(row);
                case KIND_BUTTON:  return buildCcButton(row);
                default:           return row.info ? buildCcInfo(row) : buildCcRow(row);
            }
        }
        if (row.kind == KIND_SECTION) return buildPageSection(row);
        if (row.info) return buildPageInfo(row);
        return buildPageRow(row);
    }

    // ── Control Center views ─────────────────────────────────────────────

    /** The S1B Control Center has no section heads. */
    private RowViews buildHidden(Row row) {
        RowViews v = new RowViews();
        TextView t = new TextView(activity);
        t.setVisibility(View.GONE);
        v.root = t;
        v.label = t;
        bind(v, row);
        return v;
    }

    private RowViews buildCcInfo(Row row) {
        RowViews v = new RowViews();
        TextView t = text(24, REGULAR);
        t.setTextColor(CC_MUTED);
        t.setShadowLayer(px(4), 0, px(1), CC_BUTTON_LABEL_SHADOW);
        v.root = t;
        v.label = t;
        bind(v, row);
        return v;
    }

    /** A piece of Control Center glass: the blurred wallpaper under it, the
     *  card rim on top, and a fill or focus pill behind both. */
    private void glass(RowViews v, FrameLayout cell, float radius) {
        v.bg = new CellBackground(radius, px(FOCUS_GROW));
        cell.setBackground(v.bg);
        cell.setClipChildren(false);
        View blur = host.createBackdrop(radius);
        if (blur != null) {
            cell.addView(blur, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        v.backdrop = blur;
        v.rim = new TvGlassRim(TvGlass.RIM_CARD, radius);
        v.surface = cell;
    }

    private RowViews buildCcTile(Row row) {
        RowViews v = new RowViews();
        FrameLayout cell = new FrameLayout(activity);
        glass(v, cell, px(CC_TILE_R));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        cell.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout circle = new FrameLayout(activity);
        circle.setBackground(oval(CC_CHIP_IDLE));
        ImageView glyph = new ImageView(activity);
        circle.addView(glyph, new FrameLayout.LayoutParams(px(32), px(32), Gravity.CENTER));
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(px(56), px(56));
        circleLp.setMarginStart(px(18));
        content.addView(circle, circleLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(30, MEDIUM);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setIncludeFontPadding(false);
        texts.addView(label);
        TextView desc = text(24, REGULAR);
        desc.setMaxLines(2);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setIncludeFontPadding(false);
        desc.setLineSpacing(px(26) - desc.getPaint().getFontMetricsInt(null), 1f);
        desc.setPadding(0, px(4), 0, 0);
        texts.addView(desc);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.setMarginStart(px(16));
        textsLp.setMarginEnd(px(20));
        content.addView(texts, textsLp);

        clickable(cell, row.key);
        v.root = cell;
        v.label = label;
        v.description = desc;
        v.glyph = glyph;
        v.glyphCircle = circle;
        bind(v, row);
        return v;
    }

    private RowViews buildCcRow(Row row) {
        RowViews v = new RowViews();
        FrameLayout cell = new FrameLayout(activity);
        glass(v, cell, px(CC_ROW_H) / 2f);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        cell.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView glyph = new ImageView(activity);
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams glyphLp = new LinearLayout.LayoutParams(px(36), px(36));
        glyphLp.setMarginStart(px(26));
        content.addView(glyph, glyphLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(30, MEDIUM);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setIncludeFontPadding(false);
        texts.addView(label);
        TextView desc = text(24, REGULAR);
        desc.setSingleLine(true);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setIncludeFontPadding(false);
        desc.setPadding(0, px(6), 0, 0);
        texts.addView(desc);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.setMarginStart(px(22));
        content.addView(texts, textsLp);

        TextView value = text(40, REGULAR);
        value.setSingleLine(true);
        value.setIncludeFontPadding(false);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.setMarginStart(px(16));
        valueLp.setMarginEnd(px(28));
        content.addView(value, valueLp);

        clickable(cell, row.key);
        v.root = cell;
        v.label = label;
        v.description = desc;
        v.value = value;
        v.glyph = glyph;
        bind(v, row);
        return v;
    }

    private RowViews buildCcButton(Row row) {
        RowViews v = new RowViews();
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);

        FrameLayout circle = new FrameLayout(activity);
        glass(v, circle, px(CC_BUTTON) / 2f);
        ImageView glyph = new ImageView(activity);
        circle.addView(glyph, new FrameLayout.LayoutParams(px(44), px(44), Gravity.CENTER));
        cell.addView(circle, new LinearLayout.LayoutParams(px(CC_BUTTON), px(CC_BUTTON)));

        TextView label = text(24, REGULAR);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setIncludeFontPadding(false);
        label.setPadding(0, px(8), 0, 0);
        // Names sit on the dimmed picture, not on glass. Over a bright
        // picture a soft scrim darkens it behind them (Gabriel, round 4).
        label.setShadowLayer(px(4), 0, px(1), CC_BUTTON_LABEL_SHADOW);
        label.setBackground(host.textScrim());
        cell.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        clickable(cell, row.key);
        v.root = cell;
        v.label = label;
        v.glyph = glyph;
        v.glyphCircle = circle;
        bind(v, row);
        return v;
    }

    // ── Full-screen page views ───────────────────────────────────────────

    private RowViews buildPageSection(Row row) {
        RowViews v = new RowViews();
        TextView t = text(28, BOLD);
        t.setTextColor(SettingsPageColors.VALUE);
        // setSingleLine replaces the transformation, so caps go after it.
        t.setSingleLine(true);
        t.setAllCaps(true);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(30), 0, px(30), 0);
        v.root = t;
        v.label = t;
        bind(v, row);
        return v;
    }

    private RowViews buildPageInfo(Row row) {
        RowViews v = new RowViews();
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if (row.iconRes != 0) {
            ImageView glyph = new ImageView(activity);
            glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
            glyph.setColorFilter(SettingsPageColors.VALUE);
            glyph.setImageResource(row.iconRes);
            LinearLayout.LayoutParams glyphLp = new LinearLayout.LayoutParams(px(34), px(34));
            glyphLp.setMarginStart(px(29));
            glyphLp.setMarginEnd(px(21));
            line.addView(glyph, glyphLp);
        }
        TextView t = text(30, REGULAR);
        t.setTextColor(SettingsPageColors.VALUE);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.iconRes == 0) tLp.setMarginStart(px(30));
        tLp.setMarginEnd(px(30));
        line.addView(t, tLp);
        // A row's height at least; more for a wrapped second line (D1).
        line.setMinimumHeight(px(FS_ROW_H));
        line.setPadding(0, px(6), 0, px(6));
        v.root = line;
        v.label = t;
        bind(v, row);
        return v;
    }

    private RowViews buildPageRow(Row row) {
        RowViews v = new RowViews();
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        v.bg = new CellBackground(px(FS_ROW_H) / 2f, px(FOCUS_GROW));
        line.setBackground(v.bg);
        v.surface = line;

        int textStart = 30;
        if (row.iconView != null) {
            FrameLayout slot = new FrameLayout(activity);
            slot.addView(row.iconView, new FrameLayout.LayoutParams(
                    dp(row.iconSizeDp), dp(row.iconSizeDp), Gravity.CENTER));
            LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(px(34), px(34));
            slotLp.setMarginStart(px(29));
            line.addView(slot, slotLp);
            v.iconView = row.iconView;
            textStart = 21;
        } else if (row.iconRes != 0) {
            ImageView glyph = new ImageView(activity);
            glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams glyphLp = new LinearLayout.LayoutParams(px(34), px(34));
            glyphLp.setMarginStart(px(29));
            line.addView(glyph, glyphLp);
            v.glyph = glyph;
            textStart = 21;
        } else if (row.icon != null || row.iconKey != null) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            image.setClipToOutline(true);
            LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(px(40), px(40));
            imageLp.setMarginStart(px(26));
            line.addView(image, imageLp);
            v.image = image;
            textStart = 18;
        }

        TextView label = text(38, REGULAR);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(px(textStart));
        line.addView(label, labelLp);

        TextView value = text(38, REGULAR);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMaxWidth(px(420));
        value.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLp.setMarginStart(px(24));
        valueLp.setMarginEnd(px(30));
        line.addView(value, valueLp);

        clickable(line, row.key);
        if (row.longActivate != null) {
            final String key = row.key;
            line.setOnLongClickListener(view -> {
                int index = indexOfKey(key);
                if (index < 0) return false;
                selected = index;
                paint();
                longActivate(index);
                return true;
            });
        }
        v.root = line;
        v.label = label;
        v.value = value;
        bind(v, row);
        return v;
    }

    private void clickable(View view, String key) {
        view.setClickable(true);
        view.setOnClickListener(clicked -> {
            int index = indexOfKey(key);
            if (index < 0) return;
            clicked.playSoundEffect(SoundEffectConstants.CLICK);
            selected = index;
            paint();
            activate(index);
        });
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        return bg;
    }

    private void bind(RowViews v, Row row) {
        v.label.setText(row.label);
        if (row.info) return;
        if (v.glyph != null) v.glyph.setImageResource(row.iconRes);
        if (v.description != null) {
            boolean hasDesc = row.description != null && row.description.length() > 0;
            v.description.setText(row.description);
            v.description.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        }
        // M2: a state no longer drawn as a second line is still spoken.
        boolean hasSpoken = row.spoken != null && row.spoken.length() > 0;
        v.root.setContentDescription(hasSpoken
                ? row.label + ", " + row.spoken.toString().replace(" · ", ", ") : null);
        if (v.value != null) {
            boolean hasValue = row.value != null && row.value.length() > 0;
            v.value.setVisibility(hasValue ? View.VISIBLE : View.GONE);
            v.value.setAlpha(row.dimmed ? 0.45f : 1f);
        }
        if (v.image != null) {
            v.image.setImageBitmap(row.icon);
            v.image.setVisibility(row.icon != null ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void paint() {
        for (int i = 0; i < views.size(); i++) {
            Row row = rows.get(i);
            if (row.info) continue;
            RowViews v = views.get(i);
            boolean sel = i == selected;
            // The remote's position, for accessibility and uiautomator checks.
            v.root.setSelected(sel);
            if (ccMode) paintCc(v, row, sel);
            else paintPage(v, row, sel);
        }
        if (!ccMode) paintDescription();
        raiseSelected();
    }

    /** Draw the focused row (and its grid line) after its neighbors, so
     *  the pill's shadow falls over the row below as in the pictures. Z
     *  only reorders drawing: the outline is removed, so Android casts no
     *  elevation shadow of its own. */
    private void raiseSelected() {
        View raisedRoot = null, raisedLine = null;
        if (selected >= 0 && selected < views.size() && views.get(selected) != null) {
            raisedRoot = views.get(selected).root;
            raisedLine = views.get(selected).line;
        }
        for (RowViews v : views) {
            if (v == null) continue;
            for (View z : new View[] {v.root, v.line}) {
                if (z == null) continue;
                if (z.getOutlineProvider() != null) z.setOutlineProvider(null);
                float want = z == raisedRoot || z == raisedLine ? 1f : 0f;
                if (z.getTranslationZ() != want) z.setTranslationZ(want);
            }
        }
    }

    private void paintSurface(RowViews v, boolean sel) {
        boolean glass = frost && v.backdrop != null;
        v.bg.set(sel, sel || glass ? 0 : CC_SOLID);
        if (v.backdrop != null) v.backdrop.setVisibility(glass && !sel ? View.VISIBLE : View.INVISIBLE);
        if (v.surface instanceof FrameLayout) {
            ((FrameLayout) v.surface).setForeground(glass && !sel ? v.rim : null);
        }
    }

    private void paintCc(RowViews v, Row row, boolean sel) {
        paintSurface(v, sel);
        int text = sel ? EdgeGlassStyle.SELECTED_TEXT : CC_LABEL;
        switch (row.kind) {
            case KIND_BUTTON:
                v.glyph.setColorFilter(text);
                v.label.setTextColor(CC_LABEL);
                return;
            case KIND_TILE:
                ((GradientDrawable) v.glyphCircle.getBackground()).setColor(row.iconActive
                        ? TILE_ICON_ACTIVE_BG : sel ? CC_CHIP_FOCUSED : CC_CHIP_IDLE);
                v.glyph.setColorFilter(row.iconActive ? 0xFFFFFFFF
                        : row.iconAlert ? (sel ? CC_ALERT_SELECTED : CC_ALERT) : text);
                break;
            default:
                if (v.glyph != null) v.glyph.setColorFilter(text);
                v.value.setText(row.value);
                v.value.setTextColor(sel ? EdgeGlassStyle.SELECTED_TEXT : CC_MUTED);
                break;
        }
        v.label.setTextColor(text);
        v.description.setTextColor(sel ? CC_SUB_FOCUSED : CC_MUTED);
    }

    private void paintPage(RowViews v, Row row, boolean sel) {
        v.bg.set(sel, sel ? 0 : SettingsPageColors.ROW_FILL);
        int text = sel ? SettingsPageColors.LABEL_FOCUSED : SettingsPageColors.LABEL;
        v.label.setTextColor(text);
        if (v.glyph != null) v.glyph.setColorFilter(text);
        if (v.iconView != null) v.iconView.setSelected(sel);
        CharSequence value = SettingsPageColors.pageValue(row.value, sel);
        v.value.setText(value);
        boolean check = value != null && "✓".contentEquals(value);
        v.value.setTextColor(check && !sel ? SettingsPageColors.CHECK
                : sel ? SettingsPageColors.VALUE_FOCUSED : SettingsPageColors.VALUE);
    }

    /** The left half describes the focused row: its name and description. */
    private void paintDescription() {
        Row row = selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
        descTitle.setText(row != null ? row.label : null);
        CharSequence desc = row != null ? row.description : null;
        descText.setText(desc);
        descText.setVisibility(desc == null || desc.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** UP/DOWN: the next grid line that has something to select, wrapping at
     *  the ends, landing on the column nearest the current one. */
    private void move(int direction) {
        int n = rows.size();
        if (n == 0 || selected < 0) return;
        int[] from = grid.get(selected);
        int lines = grid.get(n - 1)[0] + 1;
        for (int step = 1; step <= lines; step++) {
            int line = Math.floorMod(from[0] + direction * step, lines);
            int target = closestInLine(line, (from[1] + 0.5f) / from[2]);
            if (target >= 0) {
                selected = target;
                break;
            }
        }
        paint();
        scrollToSelected(true, 0);
    }

    /** Selectable row on {@code line} whose centre is nearest {@code position}
     *  (0 = left edge, 1 = right edge), or -1. */
    private int closestInLine(int line, float position) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            int[] cell = grid.get(i);
            if (cell[0] != line || rows.get(i).info) continue;
            float distance = Math.abs((cell[1] + 0.5f) / cell[2] - position);
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** LEFT/RIGHT: across a grid line, or the row's own step handler. */
    private void step(int direction) {
        if (selected < 0 || selected >= rows.size()) return;
        int[] cell = grid.get(selected);
        if (cell[2] > 1) {
            int column = cell[1] + direction;
            int target = selected + direction;
            if (column >= 0 && column < cell[2] && !rows.get(target).info) {
                selected = target;
                paint();
            }
            return;
        }
        IntConsumer handler = rows.get(selected).step;
        if (handler != null) handler.accept(direction);
    }

    private void activate(int index) {
        if (index < 0 || index >= rows.size()) return;
        Row row = rows.get(index);
        if (row.activate != null) row.activate.run();
        else if (row.step != null) row.step.accept(1);
    }

    private void longActivate(int index) {
        if (index < 0 || index >= rows.size()) return;
        Runnable action = rows.get(index).longActivate;
        if (action != null) action.run();
    }

    private void scrollToSelected(boolean smooth, int attempt) {
        if (ccMode || selected < 0 || selected >= views.size() || scroll == null) return;
        View row = views.get(selected).line;
        if (row.getHeight() == 0 || scroll.getHeight() == 0) {
            if (attempt < 3) scroll.post(() -> scrollToSelected(false, attempt + 1));
            return;
        }
        int margin = px(FS_ROW_H + FS_ROW_GAP);
        int top = row.getTop() - margin;
        int bottom = row.getBottom() + margin;
        int viewTop = scroll.getScrollY();
        int viewBottom = viewTop + scroll.getHeight();
        int target;
        if (top < viewTop) target = Math.max(0, top);
        else if (bottom > viewBottom) target = bottom - scroll.getHeight();
        else return;
        if (smooth) scroll.smoothScrollTo(0, target);
        else scroll.scrollTo(0, target);
    }

    private boolean sameKeys(List<Row> next) {
        if (next.size() != rows.size()) return false;
        for (int i = 0; i < next.size(); i++) {
            Row a = next.get(i);
            Row b = rows.get(i);
            if (!a.key.equals(b.key) || a.info != b.info || a.kind != b.kind) return false;
            if ((a.iconRes != 0) != (b.iconRes != 0)) return false;
            if ((a.iconView != null) != (b.iconView != null)) return false;
            if ((a.icon != null || a.iconKey != null) != (b.icon != null || b.iconKey != null)) return false;
        }
        return true;
    }

    private int nearestSelectable(int from) {
        for (int i = from; i < rows.size(); i++) if (!rows.get(i).info) return i;
        for (int i = from - 1; i >= 0; i--) if (!rows.get(i).info) return i;
        return -1;
    }

    private Entry top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    private static void runLeave(Page page) {
        if (page.onLeave != null) page.onLeave.run();
    }

    /** Text sized in design px (not sp, so the layout keeps its geometry). */
    private TextView text(int designPx, Typeface typeface) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(designPx));
        t.setTypeface(typeface);
        t.setIncludeFontPadding(true);
        return t;
    }

    /** 1920 x 1080 design pixels to device pixels (half a dp each). */
    private int px(int designPx) {
        return Math.round(designPx * density / 2f);
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    /** Fill of a row, tile or button: the solid fallback, nothing (glass
     *  shows through), or the focus pill grown {@code grow} px past every
     *  edge. The grow draws outside the view, so parents don't clip. */
    private final class CellBackground extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float radius;
        private final float grow;
        private boolean focused;
        private int fill;

        CellBackground(float radius, float grow) {
            this.radius = radius;
            this.grow = grow;
        }

        /** Shadow layer for the focused pill. Skia's blur radius r gives
         *  sigma = 0.57735 r + 0.5, so r = (sigma - 0.5) / 0.57735.
         *  shortcut: hardware draws shadow layers on shapes from API 28;
         *  on Android 8 the pill has no shadow. */
        private void shadow(boolean on) {
            if (!on) {
                paint.clearShadowLayer();
                return;
            }
            float scale = density / 2f;
            float sigma = SettingsPageColors.FOCUS_SHADOW_SIGMA * scale;
            paint.setShadowLayer(Math.max(0.1f, (sigma - 0.5f) / 0.57735f), 0f,
                    SettingsPageColors.FOCUS_SHADOW_DY * scale, SettingsPageColors.FOCUS_SHADOW);
        }

        void set(boolean focused, int fill) {
            if (this.focused == focused && this.fill == fill) return;
            this.focused = focused;
            this.fill = fill;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (focused) {
                rect.set(b.left - grow, b.top - grow, b.right + grow, b.bottom + grow);
                paint.setColor(SettingsPageColors.FOCUS_PILL);
                shadow(true);
                canvas.drawRoundRect(rect, radius + grow, radius + grow, paint);
                shadow(false);
            } else if (fill != 0) {
                rect.set(b);
                paint.setColor(fill);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
