package tv.glass.launcher;

import android.content.ComponentName;
import android.content.pm.ResolveInfo;

/**
 * Record describing one launchable app on the home shelf.
 *
 * <p>The identity fields ({@link #packageName}, {@link #sourceLabel},
 * {@link #component}) are immutable. {@link #label} is the effective
 * user-visible label and may be replaced by a local launcher override;
 * {@link #ri} and {@link #displayLabel} are mutable per-instance caches.
 *
 * <p>Hot-path access pattern:
 * <ul>
 *   <li>{@link #packageName} — keys for icon caches, the keymap
 *       SparseArray, the hidden-apps ArraySet, and the app-list
 *       persisted-order map.</li>
 *   <li>{@link #label} — drawn directly under each cell's icon.</li>
 *   <li>{@link #component} — fast-path launch via
 *       {@code Intent.setComponent} (skips PackageManager resolution
 *       binder hops; saves 50-200 ms per launch on slow ROMs).</li>
 *   <li>{@link #ri} — used by {@code preWarmIcon} / {@code loadIconAsync}
 *       to call {@link ResolveInfo#loadIcon}. May be {@code null} for
 *       AppInfo instances reconstructed from the on-disk
 *       {@link AppListCache} — those entries fall back to
 *       {@code PackageManager.getActivityIcon(component)}.</li>
 * </ul>
 *
 * <p>This class moved out of {@link LauncherActivity} in v1.4.0 so cache
 * helpers can construct entries without reaching into an activity-owned
 * nested type. It remains package-private because only launcher internals
 * consume it.
 */
final class AppInfo {
    static final String TV_INPUT_PREFIX = "tvinput://";
    static final String SHORTCUT_PREFIX = "shortcut://";

    final String        packageName;
    /** Label supplied by PackageManager or the TV Input Framework. */
    final String        sourceLabel;
    /** Effective launcher label; source label unless the user renamed this entry. */
    volatile String     label;
    final ComponentName component;
    /**
     * The platform {@link ResolveInfo} for this app, when known.
     *
     * <p>Non-final and {@code volatile} so the {@link LauncherActivity}
     * reconcile path can graft a fresh {@code ri} onto a cache-sourced
     * AppInfo that was reconstructed from {@link AppListCache} (which
     * cannot serialise ResolveInfo and therefore stores it as
     * {@code null}). Without the graft, every icon load for that
     * AppInfo falls through to the slower
     * {@link android.content.pm.PackageManager#getActivityIcon(android.content.ComponentName)}
     * binder path forever — even after the post-launch PM scan has
     * resolved the activity.
     *
     * <p>Volatile because the field is read on the
     * {@code iconExecutor} worker thread (inside
     * {@link LauncherActivity}'s {@code resolveIconDrawable}) and
     * written on the UI thread (inside the {@code loadApps} reconcile).
     * The volatile semantics give the worker an immediate visibility
     * guarantee on the upgraded value; absent it, a worker thread
     * already running could observe the stale {@code null} for an
     * arbitrarily long window after the upgrade and still hit the slow
     * fallback. Worst case is identical to the pre-graft behaviour
     * (slow path), but volatile makes the upgrade reliable rather than
     * timing-dependent.
     */
    volatile ResolveInfo ri;

    /**
     * Memoised, ellipsised label as drawn under the shelf cell — lazily
     * computed and cached the first time any {@code CellView} binds this
     * app, then reused by every subsequent bind.
     *
     * <p>The truncation result is a pure function of the effective label,
     * shelf cell's width budget, label text size, and typeface. These inputs
     * stay fixed between rename/configuration events, and both events clear
     * this memo before cells repaint. There is
     * therefore no reason for a recycled cell that scrolls back onto a
     * previously-seen app during a fast fling to re-run
     * {@link android.text.TextUtils#ellipsize} (which allocates a
     * {@code CharSequence} + a {@code String}) or even
     * {@code Paint.measureText}. Caching the result on the model collapses
     * that to one measure + at-most-one ellipsize per app for the whole
     * session.
     *
     * <p>{@code null} means "not computed yet". For live entries the field
     * is read and written only on the UI thread by cell binding, rename, and
     * configuration-change paths. Background scans may clear it only on new
     * AppInfo objects before those objects are published to the UI, so no
     * synchronization is required — unlike {@link #ri}, which workers read
     * after publication.
     */
    String displayLabel;

    /**
     * Non-null only for a TV-input "app" (HDMI 1, AV, tuner, …) surfaced from
     * the platform TV Input Framework. When set, this entry is launched by
     * switching to the passthrough input via {@code TvContract} rather than by
     * starting an activity, its banner tile is a generated input glyph, and
     * the "Uninstall" / "App info" context-menu rows are suppressed (an input
     * is not an installed package). Everything else — ordering, home/drawer
     * placement, reorder, hide, remote-key binding — treats it exactly like a
     * normal app. {@code null} for ordinary apps. See {@link TvInputs}.
     */
    final String tvInputId;

    /**
     * Non-null only for a shortcut another app pinned to the launcher (for
     * example one made with Shortcut Maker). {@link #shortcutPackage} owns
     * it and {@link #shortcutId} is its id there. It launches and unpins
     * through {@code LauncherApps}, and "App info" is suppressed. Everything
     * else treats it like an app. See {@link PinnedShortcuts}.
     */
    final String shortcutPackage;
    final String shortcutId;

    AppInfo(String pkg, String lbl, ComponentName cmp, ResolveInfo r) {
        this(pkg, lbl, cmp, r, null, null, null);
    }

    private AppInfo(String pkg, String lbl, ComponentName cmp, ResolveInfo r, String tvInput,
                    String shortcutOwner, String shortcutIdInOwner) {
        packageName     = pkg;
        sourceLabel     = lbl;
        label           = lbl;
        component       = cmp;
        ri              = r;
        tvInputId       = tvInput;
        shortcutPackage = shortcutOwner;
        shortcutId      = shortcutIdInOwner;
    }

    /** Not an installed package: a TV input or a pinned shortcut. */
    boolean isSynthetic() {
        return tvInputId != null || shortcutId != null;
    }

    /** Apply a sanitized local name, or restore the source label when null. */
    void setCustomLabel(String customLabel) {
        label = customLabel != null ? customLabel : sourceLabel;
        displayLabel = null;
    }

    /** Build a TV-input entry. The synthetic {@link #packageName}
     *  ({@code "tvinput://" + inputId}) is the stable key used everywhere a
     *  package name is — order persistence, the hidden set, the icon/banner
     *  caches, remote-key bindings — so an input round-trips through all of
     *  them like any other app. It can never collide with a real package
     *  (real package names are never of this shape) and contains no comma, so
     *  it is safe inside the comma-separated persisted order string. */
    static AppInfo tvInput(String inputId, String label) {
        return new AppInfo(TV_INPUT_PREFIX + inputId, label, null, null, inputId, null, null);
    }

    /** Build a pinned-shortcut entry keyed by {@link #shortcutIdentity}. */
    static AppInfo pinnedShortcut(String ownerPackage, String shortcutId, String label) {
        return new AppInfo(shortcutIdentity(ownerPackage, shortcutId), label, null, null, null,
                ownerPackage, shortcutId);
    }

    /** Rebuild a pinned-shortcut entry from its identity (the app-list cache
     *  stores only identity and label). {@code null} if it isn't one. */
    static AppInfo pinnedShortcutFromIdentity(String identity, String label) {
        if (!isShortcutIdentity(identity)) return null;
        String rest = identity.substring(SHORTCUT_PREFIX.length());
        int slash = rest.indexOf('/');
        String id = decodeShortcutId(rest.substring(slash + 1));
        return id == null ? null : pinnedShortcut(rest.substring(0, slash), id, label);
    }

    /** {@code "shortcut://" + package + "/" + id}, with the id
     *  percent-encoded. Shortcut ids are any string the owning app likes, so
     *  encoding keeps commas, '=' and slashes out of the comma-separated
     *  order, hidden-app and remote-key strings this identity is stored in. */
    static String shortcutIdentity(String ownerPackage, String shortcutId) {
        return SHORTCUT_PREFIX + ownerPackage + "/" + encodeShortcutId(shortcutId);
    }

    static boolean isShortcutIdentity(String identity) {
        if (identity == null || !identity.startsWith(SHORTCUT_PREFIX)) return false;
        int slash = identity.indexOf('/', SHORTCUT_PREFIX.length());
        return slash > SHORTCUT_PREFIX.length() && slash < identity.length() - 1;
    }

    private static String encodeShortcutId(String shortcutId) {
        StringBuilder out = new StringBuilder(shortcutId.length() + 8);
        for (byte b : shortcutId.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                   .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }

    private static String decodeShortcutId(String encoded) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c != '%') { bytes.write(c); continue; }
            if (i + 2 >= encoded.length()) return null;
            int hi = Character.digit(encoded.charAt(i + 1), 16);
            int lo = Character.digit(encoded.charAt(i + 2), 16);
            if (hi < 0 || lo < 0) return null;
            bytes.write((hi << 4) | lo);
            i += 2;
        }
        return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    static boolean isTvInputIdentity(String identity) {
        return identity != null && identity.startsWith(TV_INPUT_PREFIX)
                && identity.length() > TV_INPUT_PREFIX.length();
    }
}
