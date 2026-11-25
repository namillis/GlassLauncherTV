package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeOverridePlanTest {

    private static final String PKG = "tv.glass.launcher.debug";
    private static final String CLASS = "tv.glass.launcher.HomeButtonService";
    private static final long LONG_PRESS = 500L;
    private static final long SUPPRESS = 1_500L;

    // --- isStockLauncher -----------------------------------------------------

    @Test public void isStockLauncher_recognisesTheThreeStockPackages() {
        assertTrue(HomeOverridePlan.isStockLauncher(HomeOverridePlan.GOOGLE_TV_LAUNCHER));
        assertTrue(HomeOverridePlan.isStockLauncher(HomeOverridePlan.ANDROID_TV_LAUNCHER));
        assertTrue(HomeOverridePlan.isStockLauncher(HomeOverridePlan.GOOGLE_TV_SETUP));
    }

    @Test public void isStockLauncher_rejectsOthersAndNull() {
        assertFalse(HomeOverridePlan.isStockLauncher("tv.glass.launcher.debug"));
        assertFalse(HomeOverridePlan.isStockLauncher("com.example.other"));
        assertFalse(HomeOverridePlan.isStockLauncher(null));
    }

    // --- decideKey -----------------------------------------------------------

    @Test public void decideKey_nonHomeKeyAlwaysPasses() {
        assertEquals(HomeOverridePlan.KeyAction.PASS,
                HomeOverridePlan.decideKey(false, true, true, 0, LONG_PRESS));
        assertEquals(HomeOverridePlan.KeyAction.PASS,
                HomeOverridePlan.decideKey(false, false, true, 9_999, LONG_PRESS));
    }

    @Test public void decideKey_inactivePassesEvenForHome() {
        assertEquals(HomeOverridePlan.KeyAction.PASS,
                HomeOverridePlan.decideKey(true, true, false, 0, LONG_PRESS));
        assertEquals(HomeOverridePlan.KeyAction.PASS,
                HomeOverridePlan.decideKey(true, false, false, 10, LONG_PRESS));
    }

    @Test public void decideKey_homeDownIsHeld() {
        assertEquals(HomeOverridePlan.KeyAction.CONSUME_HOLD,
                HomeOverridePlan.decideKey(true, true, true, 0, LONG_PRESS));
    }

    @Test public void decideKey_shortReleaseBringsGlassForward() {
        assertEquals(HomeOverridePlan.KeyAction.CONSUME_SHORT,
                HomeOverridePlan.decideKey(true, false, true, 0, LONG_PRESS));
        assertEquals(HomeOverridePlan.KeyAction.CONSUME_SHORT,
                HomeOverridePlan.decideKey(true, false, true, LONG_PRESS - 1, LONG_PRESS));
    }

    @Test public void decideKey_longReleaseRunsLongPress() {
        assertEquals(HomeOverridePlan.KeyAction.CONSUME_LONG,
                HomeOverridePlan.decideKey(true, false, true, LONG_PRESS, LONG_PRESS));
        assertEquals(HomeOverridePlan.KeyAction.CONSUME_LONG,
                HomeOverridePlan.decideKey(true, false, true, 2_000, LONG_PRESS));
    }

    // --- shouldReclaim -------------------------------------------------------

    @Test public void shouldReclaim_stockLauncherWhileActiveOutsideWindows() {
        assertTrue(HomeOverridePlan.shouldReclaim(HomeOverridePlan.GOOGLE_TV_LAUNCHER,
                true, SUPPRESS + 1, SUPPRESS + 1, SUPPRESS));
        assertTrue(HomeOverridePlan.shouldReclaim(HomeOverridePlan.ANDROID_TV_LAUNCHER,
                true, 10_000, 10_000, SUPPRESS));
    }

    @Test public void shouldReclaim_falseWhenInactive() {
        assertFalse(HomeOverridePlan.shouldReclaim(HomeOverridePlan.GOOGLE_TV_LAUNCHER,
                false, 10_000, 10_000, SUPPRESS));
    }

    @Test public void shouldReclaim_falseForNonStockWindow() {
        assertFalse(HomeOverridePlan.shouldReclaim("com.netflix.ninja",
                true, 10_000, 10_000, SUPPRESS));
        assertFalse(HomeOverridePlan.shouldReclaim(null,
                true, 10_000, 10_000, SUPPRESS));
    }

    @Test public void shouldReclaim_suppressedRightAfterOwnLaunch() {
        assertFalse(HomeOverridePlan.shouldReclaim(HomeOverridePlan.GOOGLE_TV_LAUNCHER,
                true, 10, 10_000, SUPPRESS));
    }

    @Test public void shouldReclaim_suppressedRightAfterLongPress() {
        assertFalse(HomeOverridePlan.shouldReclaim(HomeOverridePlan.GOOGLE_TV_LAUNCHER,
                true, 10_000, 10, SUPPRESS));
    }

    // --- isServiceEnabled ----------------------------------------------------

    @Test public void isServiceEnabled_matchesFullComponentForm() {
        String setting = "com.other/.Svc:" + PKG + "/" + CLASS;
        assertTrue(HomeOverridePlan.isServiceEnabled(setting, PKG, CLASS));
    }

    @Test public void isServiceEnabled_matchesShortComponentForm() {
        // When the class lives under the application id, Android stores pkg/.Class.
        String underAppId = PKG + ".HomeButtonService";
        String setting = PKG + "/.HomeButtonService";
        assertTrue(HomeOverridePlan.isServiceEnabled(setting, PKG, underAppId));
    }

    @Test public void isServiceEnabled_caseInsensitiveAndTrimmed() {
        String setting = "  " + PKG.toUpperCase() + "/" + CLASS.toUpperCase() + "  ";
        assertTrue(HomeOverridePlan.isServiceEnabled(setting, PKG, CLASS));
    }

    @Test public void isServiceEnabled_falseWhenAbsentOrNull() {
        assertFalse(HomeOverridePlan.isServiceEnabled("com.other/.Svc", PKG, CLASS));
        assertFalse(HomeOverridePlan.isServiceEnabled("", PKG, CLASS));
        assertFalse(HomeOverridePlan.isServiceEnabled(null, PKG, CLASS));
        assertFalse(HomeOverridePlan.isServiceEnabled("a:b:c", null, CLASS));
        assertFalse(HomeOverridePlan.isServiceEnabled("a:b:c", PKG, null));
    }

    @Test public void isServiceEnabled_ignoresEmptyEntries() {
        String setting = ":" + PKG + "/" + CLASS + "::";
        assertTrue(HomeOverridePlan.isServiceEnabled(setting, PKG, CLASS));
    }

    // --- wantsKeyFiltering ---------------------------------------------------

    @Test public void wantsKeyFiltering_onlyWhenGlassIsNotHomeAndNoVolumePause() {
        assertTrue(HomeOverridePlan.wantsKeyFiltering(false, false));
    }

    @Test public void wantsKeyFiltering_offWhenGlassIsAlreadyHome() {
        // The ADB setup case: Home already reaches Glass, so filtering would
        // only cost volume holds.
        assertFalse(HomeOverridePlan.wantsKeyFiltering(true, false));
        assertFalse(HomeOverridePlan.wantsKeyFiltering(true, true));
    }

    @Test public void wantsKeyFiltering_offDuringVolumePause() {
        assertFalse(HomeOverridePlan.wantsKeyFiltering(false, true));
    }

    @Test public void shouldReclaim_ignoresTheVolumePause() {
        // The service passes "Glass is not Home" here, never the filtering
        // state, so the reclaim keeps working while filtering is paused.
        boolean glassIsHome = false;
        boolean volumePaused = true;
        assertFalse(HomeOverridePlan.wantsKeyFiltering(glassIsHome, volumePaused));
        assertTrue(HomeOverridePlan.shouldReclaim(HomeOverridePlan.GOOGLE_TV_LAUNCHER,
                !glassIsHome, 10_000, 10_000, SUPPRESS));
    }

    // --- status --------------------------------------------------------------

    @Test public void status_alreadyHomeWinsRegardlessOfEnabled() {
        assertEquals(HomeOverridePlan.Status.ALREADY_HOME,
                HomeOverridePlan.status(true, true));
        assertEquals(HomeOverridePlan.Status.ALREADY_HOME,
                HomeOverridePlan.status(false, true));
    }

    @Test public void status_onWhenEnabledAndNotHome() {
        assertEquals(HomeOverridePlan.Status.ON, HomeOverridePlan.status(true, false));
    }

    @Test public void status_offWhenDisabledAndNotHome() {
        assertEquals(HomeOverridePlan.Status.OFF, HomeOverridePlan.status(false, false));
    }
}
