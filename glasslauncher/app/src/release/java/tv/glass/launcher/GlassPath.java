package tv.glass.launcher;

/** Release copy: no switches. The calibration switches exist only in the
 *  debug source set. */
final class GlassPath {
    private GlassPath() {}

    /** False: every API level draws the pre-blurred tvOS glass. The API 31+
     *  RenderEffect path cannot cap the fill per pixel for text contrast
     *  (RuntimeShader needs API 33) and cannot color the rims, so Android
     *  12+ TVs draw the same glass as Android 11 ones.
     *  shortcut: the RenderEffect branches stay in the code, reachable only
     *  from the debug force_live_glass switch; delete them or move them to a
     *  RuntimeShader once API 33+ is worth a second path. */
    static boolean liveBlur() {
        return false;
    }

    /** Never in a release build. */
    static boolean hideGlass() {
        return false;
    }
}
