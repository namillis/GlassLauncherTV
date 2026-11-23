package tv.glass.launcher;

import android.os.Build;

import java.io.File;

/** Debug-only calibration switches, read once when the app starts.
 *  <ul>
 *  <li>Every API level draws the pre-blurred glass (the path an Android 11
 *      TV draws), as the release build does. {@code files/force_legacy_glass}
 *      is still read so old capture scripts keep working; it changes
 *      nothing now.</li>
 *  <li>{@code files/force_live_glass}: an API 31+ debug build takes the old
 *      RenderEffect blur path instead, for before/after captures only. That
 *      path has no per-pixel text cap and gray rims.</li>
 *  <li>{@code files/hide_glass}: draw only the wallpaper, exactly as the app
 *      draws it, over everything (for glass-free reference captures).</li>
 *  </ul>
 *  All are off by default; turn one on with
 *  {@code adb shell run-as tv.glass.launcher.debug touch files/<name>} and
 *  restart the app. The release copy of this class has no switches. */
final class GlassPath {
    private GlassPath() {}

    private static final String DIR = "/data/data/tv.glass.launcher.debug/files/";
    private static final boolean FORCE_LIVE = new File(DIR + "force_live_glass").exists();
    private static final boolean HIDE_GLASS = new File(DIR + "hide_glass").exists();

    /** True when the live RenderEffect blur path is used. */
    static boolean liveBlur() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && FORCE_LIVE;
    }

    /** True when every surface is covered by the bare wallpaper. */
    static boolean hideGlass() {
        return HIDE_GLASS;
    }
}
