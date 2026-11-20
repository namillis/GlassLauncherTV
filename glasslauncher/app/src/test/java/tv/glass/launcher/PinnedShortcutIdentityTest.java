package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PinnedShortcutIdentityTest {

    @Test public void identity_keepsSeparatorsOutOfStoredStrings() {
        String identity = AppInfo.shortcutIdentity("com.example.maker", "id,with=odd/chars é");

        assertTrue(identity.startsWith("shortcut://com.example.maker/"));
        assertEquals(-1, identity.indexOf(',', 0));
        assertEquals(-1, identity.indexOf('='));
        assertEquals(identity.indexOf('/', "shortcut://".length()), identity.lastIndexOf('/'));
    }

    @Test public void fromIdentity_roundTripsPackageIdAndLabel() {
        String id = "id,with=odd/chars é";
        AppInfo shortcut = AppInfo.pinnedShortcutFromIdentity(
                AppInfo.shortcutIdentity("com.example.maker", id), "Netflix profile");

        assertNotNull(shortcut);
        assertEquals("com.example.maker", shortcut.shortcutPackage);
        assertEquals(id, shortcut.shortcutId);
        assertEquals("Netflix profile", shortcut.label);
        assertTrue(shortcut.isSynthetic());
    }

    @Test public void fromIdentity_rejectsOtherIdentities() {
        assertNull(AppInfo.pinnedShortcutFromIdentity("com.example.app", "App"));
        assertNull(AppInfo.pinnedShortcutFromIdentity("tvinput://oem/HW5", "HDMI"));
        assertNull(AppInfo.pinnedShortcutFromIdentity("shortcut://com.example/", "x"));
        assertNull(AppInfo.pinnedShortcutFromIdentity("shortcut://com.example/bad%zz", "x"));
        assertFalse(AppInfo.isShortcutIdentity("shortcut:///id"));
    }

    @Test public void identity_survivesOrderAndHiddenAppStorage() {
        String identity = AppInfo.shortcutIdentity("com.example.maker", "a,b");

        assertEquals(Integer.valueOf(1), AppOrder.parse(
                AppOrder.serialize(Arrays.asList("com.example.app", identity))).get(identity));
        StringBuilder hidden = new StringBuilder();
        KeymapStore.parseHiddenApps(KeymapStore.serializeHiddenApps(
                Collections.singletonList(identity)), hidden::append);
        assertEquals(identity, hidden.toString());
    }

    @Test public void customIconStem_isFlatForShortcuts() {
        String stem = CustomIconStore.fileStemForIdentity(
                AppInfo.shortcutIdentity("com.example.maker", "a/b"));

        assertNotNull(stem);
        assertTrue(stem.startsWith("@shortcut_"));
        assertEquals(-1, stem.indexOf('/'));
    }
}
