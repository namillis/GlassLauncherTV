package tv.glass.launcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure, Android-free decision logic for the optional Home-button override
 * (issue #45). Keeping the logic out of {@link HomeButtonService} makes it
 * fully unit-testable on the JVM, the same pattern as {@link GoogleTvSetupPlan}.
 *
 * <p>The override lets a Google TV / Android TV user open Glass with the
 * remote's Home key WITHOUT the one-time ADB setup ({@link GoogleTvSetup}),
 * by filtering the Home key in an accessibility service and bringing Glass to
 * the front. It is a no-op whenever Glass is already the default Home app, so
 * the ADB setup (full replacement) and this service never fight each other.
 */
final class HomeOverridePlan {

    /** Google TV's launcher. Its window coming to the front means Home was
     *  pressed (or the system returned to it on boot / wake). */
    static final String GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx";
    /** Classic Android TV / leanback launcher (e.g. the AOSP TV emulator,
     *  many 2018-2021 boxes). The research for #45 flagged that this package
     *  was never referenced in the app and has to be handled here too. */
    static final String ANDROID_TV_LAUNCHER = "com.google.android.tvlauncher";
    /** Google TV setup wizard, which can also claim HOME on some firmwares. */
    static final String GOOGLE_TV_SETUP = "com.google.android.tungsten.setupwraith";

    private static final Set<String> STOCK_LAUNCHERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    GOOGLE_TV_LAUNCHER, ANDROID_TV_LAUNCHER, GOOGLE_TV_SETUP)));

    /** What the service should do with a key event. */
    enum KeyAction {
        /** Not ours — let the system handle it (service returns {@code false},
         *  so there is no filtering cost for any non-Home key). */
        PASS,
        /** A Home DOWN while the override is engaged — swallow it and wait for
         *  the UP to tell a short press from a long press. */
        CONSUME_HOLD,
        /** Home released as a short press — swallow it and bring Glass forward. */
        CONSUME_SHORT,
        /** Home released as a long press — swallow it and run the long-press
         *  action instead. */
        CONSUME_LONG
    }

    /** Status shown on the settings row. */
    enum Status {
        /** Service off; Home still goes to the stock launcher. */
        OFF,
        /** Service on and performing the override. */
        ON,
        /** Glass is already the default Home (e.g. after the ADB setup), so the
         *  Home key already opens Glass and the service is unnecessary. */
        ALREADY_HOME
    }

    private HomeOverridePlan() { }

    static boolean isStockLauncher(String packageName) {
        return packageName != null && STOCK_LAUNCHERS.contains(packageName);
    }

    /**
     * Decide what to do with a key event delivered to the service.
     *
     * @param isHomeKey   whether the key is KEYCODE_HOME (passed in so this
     *                    class needs no {@code android.view.KeyEvent})
     * @param isDown      true for ACTION_DOWN, false for ACTION_UP
     * @param active      the override is engaged (service on, Glass not the
     *                    default Home, key filtering not paused for volume)
     * @param heldMs      for an UP, how long Home was held (UP time - DOWN time)
     * @param longPressMs the long-press threshold
     */
    static KeyAction decideKey(boolean isHomeKey, boolean isDown, boolean active,
                               long heldMs, long longPressMs) {
        if (!active || !isHomeKey) return KeyAction.PASS;
        if (isDown) return KeyAction.CONSUME_HOLD;
        return heldMs >= longPressMs ? KeyAction.CONSUME_LONG : KeyAction.CONSUME_SHORT;
    }

    /**
     * Whether a window from {@code packageName} coming to the front should make
     * the service reclaim the foreground for Glass. This is the fallback path
     * for the cases a consumed key press cannot cover: boot, waking from
     * standby, and a HOME <em>intent</em> (not a key) that an app fires.
     *
     * @param packageName       the package whose window just appeared
     * @param active            override engaged (service on, Glass not Home)
     * @param sinceOwnLaunchMs  ms since the service itself last brought Glass
     *                          forward (debounce against our own transition)
     * @param sinceLongPressMs  ms since the last long-press action (so a
     *                          long-press that opens a stock-launcher panel is
     *                          not immediately bounced away)
     * @param suppressWindowMs  length of both suppression windows
     */
    static boolean shouldReclaim(String packageName, boolean active,
                                 long sinceOwnLaunchMs, long sinceLongPressMs,
                                 long suppressWindowMs) {
        if (!active) return false;
        if (!isStockLauncher(packageName)) return false;
        if (sinceOwnLaunchMs < suppressWindowMs) return false;
        if (sinceLongPressMs < suppressWindowMs) return false;
        return true;
    }

    /**
     * Parse {@code Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES} (a
     * colon-separated list of flattened component names) and report whether our
     * service is in it. Android may store the full {@code pkg/pkg.Inner.Class}
     * form or the short {@code pkg/.Class} form, so both are accepted.
     */
    static boolean isServiceEnabled(String enabledServicesSetting, String packageName,
                                    String className) {
        if (enabledServicesSetting == null || packageName == null || className == null) {
            return false;
        }
        String full = packageName + "/" + className;
        String shortForm = packageName + "/"
                + (className.startsWith(packageName)
                        ? className.substring(packageName.length())
                        : className);
        for (String entry : enabledServicesSetting.split(":")) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.equalsIgnoreCase(full) || e.equalsIgnoreCase(shortForm)) return true;
        }
        return false;
    }

    /** The status to show, given whether the service is enabled and whether
     *  Glass is already the default Home. */
    static Status status(boolean serviceEnabled, boolean glassIsHome) {
        if (glassIsHome) return Status.ALREADY_HOME;
        return serviceEnabled ? Status.ON : Status.OFF;
    }
}
