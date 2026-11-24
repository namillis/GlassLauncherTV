package tv.glass.launcher;

/**
 * Colors of the full-screen settings pages (S2B, S3C): a flat dark page like
 * tvOS Settings, not glass. Plain ints so the unit tests can check every
 * text against the surface it sits on (SettingsPageColorsTest).
 */
final class SettingsPageColors {
    private SettingsPageColors() {}

    /** The page itself, tvOS Settings' dark background (28, 28, 30). */
    static final int PAGE_BG = 0xFF1C1C1E;
    /** Unfocused row fill: the page plus a 0x14 white lift (46, 46, 48). */
    static final int ROW_FILL = 0xFF2E2E30;
    /** Focus pill fill, as the approved pictures draw it (242 gray; the
     *  shared EdgeGlassStyle pill is 239). */
    static final int FOCUS_PILL = 0xFFF2F2F2;
    /** Soft shadow under the focus pill (pictures a-d): the grown pill's
     *  shape blurred with sigma 14 design px, 40% black, 12 px down. */
    static final int FOCUS_SHADOW = 0x66000000;
    static final float FOCUS_SHADOW_SIGMA = 14f;
    static final float FOCUS_SHADOW_DY = 12f;
    /** Page title, gray bold (128, 128, 132). Large text (57 px bold). */
    static final int TITLE = 0xFF808084;
    /** Row labels and the description title. */
    static final int LABEL = 0xF2FFFFFF;
    /** Focused row label. */
    static final int LABEL_FOCUSED = EdgeGlassStyle.SELECTED_TEXT;
    /** Description under the icon tile, footer note. */
    static final int MUTED = 0xD9FFFFFF;
    /** Values, section heads and info lines: Apple's settings gray.
     *  The approved (142, 142, 147) reaches 5.2:1 on the page but only
     *  4.16:1 on a row; (152, 152, 157) is the nearest gray that keeps
     *  4.7:1 (TvGlass.CHECK_CONTRAST) on the row fill. Departure from the
     *  picture by 10 levels, like the 0xD9 "Off" value. */
    static final int VALUE = 0xFF98989D;
    /** Value on the focused (white) row. */
    static final int VALUE_FOCUSED = 0xFF606066;
    /** Check marks are glyphs, so they keep the accent. */
    static final int CHECK = TvGlass.CARD_ACCENT_TEXT;
    /** Icon tile on the left half and the glyph cut into it. */
    static final int ICON_TILE = 0xFF555558;
    static final int ICON_GLYPH = PAGE_BG;

    // Control Center glass (S1B): text stays white or white-gray there.
    /** Tile and row labels on the glass. */
    static final int CC_LABEL = 0xF2FFFFFF;
    /** Tile status lines, row descriptions, chevrons: the card's muted white. */
    static final int CC_MUTED = TvGlass.CARD_MUTED_TEXT;

    /** "< 6 >" steppers show "‹ 6 ›" on the focused row and "6" elsewhere,
     *  as tvOS Settings does. Other values pass through. */
    static CharSequence pageValue(CharSequence value, boolean focused) {
        if (value == null) return null;
        String s = value.toString();
        if (s.length() >= 4 && s.startsWith("< ") && s.endsWith(" >")) {
            String inner = s.substring(2, s.length() - 2);
            return focused ? "‹ " + inner + " ›" : inner;
        }
        return value;
    }
}
