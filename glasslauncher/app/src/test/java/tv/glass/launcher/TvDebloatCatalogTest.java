package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class TvDebloatCatalogTest {

    @Test public void catalog_hasNoDuplicatePackages() {
        Set<String> seen = new HashSet<>();
        for (TvDebloatCatalog.Entry entry : TvDebloatCatalog.ENTRIES) {
            assertTrue("duplicate " + entry.packageName, seen.add(entry.packageName));
        }
    }

    @Test public void catalog_neverTouchesHomeSetupOrCorePackages() {
        for (String protectedPackage : Arrays.asList(
                GoogleTvSetupPlan.GOOGLE_TV_LAUNCHER,
                GoogleTvSetupPlan.GOOGLE_TV_SETUP,
                "com.google.android.gms",
                "com.android.vending",
                "com.android.tv.settings",
                "com.google.android.apps.mediashell",
                "com.sony.dtv.tvx")) {
            assertNull(TvDebloatCatalog.find(protectedPackage));
        }
    }

    @Test public void commands_useDisableUserAndEnableForUserZero() {
        assertEquals("pm disable-user --user 0 com.google.android.feedback",
                TvDebloatCatalog.disableCommand("com.google.android.feedback"));
        assertEquals("pm enable --user 0 com.google.android.feedback",
                TvDebloatCatalog.enableCommand("com.google.android.feedback"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void disableCommand_rejectsPackagesOutsideCatalog() {
        TvDebloatCatalog.disableCommand("com.google.android.gms");
    }

    @Test(expected = IllegalArgumentException.class)
    public void enableCommand_rejectsShellInjection() {
        TvDebloatCatalog.enableCommand("com.google.android.feedback; reboot");
    }

    @Test public void disableRecommendedCommands_onlyIncludesInstalledEnabledRecommendedEntries() {
        assertEquals(Arrays.asList(
                "pm disable-user --user 0 com.google.android.feedback",
                "pm disable-user --user 0 com.sony.dtv.promos"
        ), TvDebloatCatalog.disableRecommendedCommands(Arrays.asList(
                "com.sony.dtv.promos",
                "com.google.android.katniss",
                "com.google.android.feedback",
                "com.example.not.in.catalog")));
    }

    @Test public void disableRecommendedCommands_emptyWhenNothingInstalled() {
        assertTrue(TvDebloatCatalog.disableRecommendedCommands(Collections.emptyList()).isEmpty());
    }

    @Test public void featureLosingEntries_areNotRecommended() {
        assertFalse(TvDebloatCatalog.find("com.google.android.katniss").recommended);
        assertFalse(TvDebloatCatalog.find("com.google.android.tv.remote.service").recommended);
        assertFalse(TvDebloatCatalog.find("com.google.android.apps.tv.dreamx").recommended);
        assertFalse(TvDebloatCatalog.find("com.sony.dtv.smarthelp").recommended);
        assertFalse(TvDebloatCatalog.find("com.google.android.apps.tv.netoscope").recommended);
    }

    @Test public void isSafePackageName_rejectsShellMetacharacters() {
        assertTrue(TvDebloatCatalog.isSafePackageName("android.autoinstalls.config.TCL.PAI"));
        assertFalse(TvDebloatCatalog.isSafePackageName("com.example --user 10"));
        assertFalse(TvDebloatCatalog.isSafePackageName("com.example;reboot"));
        assertFalse(TvDebloatCatalog.isSafePackageName("nodots"));
        assertFalse(TvDebloatCatalog.isSafePackageName(null));
    }
}
