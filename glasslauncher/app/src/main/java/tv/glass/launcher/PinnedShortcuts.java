package tv.glass.launcher;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shortcuts other apps pin to the launcher through
 * {@code ShortcutManager.requestPinShortcut} (Shortcut Maker and similar).
 * {@link PinShortcutActivity} accepts the request; from then on the platform
 * keeps the shortcut pinned for us, and this class lists, launches, draws and
 * unpins it. Every call needs the shortcut-host permission, which Android
 * gives only to the default Home app, so all of them are no-ops otherwise.
 */
final class PinnedShortcuts {

    private PinnedShortcuts() { /* no instances */ }

    /** Identity of a shortcut the user just pinned. The next app-list
     *  reconcile that sees it puts it at the end of the home row. Same
     *  process, so a static handoff is enough. */
    static volatile String pendingHomeIdentity;

    static List<AppInfo> enumerate(Context ctx) {
        List<AppInfo> out = new ArrayList<>();
        LauncherApps launcherApps = host(ctx);
        if (launcherApps == null) return out;
        try {
            List<ShortcutInfo> pinned = launcherApps.getShortcuts(
                    new LauncherApps.ShortcutQuery()
                            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
                    Process.myUserHandle());
            if (pinned == null) return out;
            for (ShortcutInfo info : pinned) {
                if (info == null || !info.isEnabled()) continue;
                out.add(AppInfo.pinnedShortcut(info.getPackage(), info.getId(), label(info)));
            }
        } catch (RuntimeException ignored) {
            out.clear();   // SecurityException when we just lost the Home role
        }
        return out;
    }

    static boolean launch(Context ctx, AppInfo shortcut, Rect sourceBounds, Bundle options) {
        LauncherApps launcherApps = host(ctx);
        if (launcherApps == null) return false;
        try {
            launcherApps.startShortcut(shortcut.shortcutPackage, shortcut.shortcutId,
                    sourceBounds, options, Process.myUserHandle());
            return true;
        } catch (RuntimeException e) {
            return false;   // ActivityNotFound, disabled shortcut, lost Home role
        }
    }

    static Drawable loadIcon(Context ctx, AppInfo shortcut, int densityDpi) {
        LauncherApps launcherApps = host(ctx);
        ShortcutInfo info = find(launcherApps, shortcut.shortcutPackage, shortcut.shortcutId);
        if (info == null) return null;
        try {
            return launcherApps.getShortcutIconDrawable(info, densityDpi);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Unpin {@code shortcut}, keeping the owner's other pinned shortcuts. */
    static boolean unpin(Context ctx, AppInfo shortcut) {
        LauncherApps launcherApps = host(ctx);
        if (launcherApps == null) return false;
        try {
            List<ShortcutInfo> pinned = launcherApps.getShortcuts(
                    new LauncherApps.ShortcutQuery()
                            .setPackage(shortcut.shortcutPackage)
                            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
                    Process.myUserHandle());
            List<String> keep = new ArrayList<>();
            if (pinned != null) {
                for (ShortcutInfo info : pinned) {
                    if (!info.getId().equals(shortcut.shortcutId)) keep.add(info.getId());
                }
            }
            launcherApps.pinShortcuts(shortcut.shortcutPackage, keep, Process.myUserHandle());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String label(ShortcutInfo info) {
        CharSequence label = info.getShortLabel();
        if (label == null || label.length() == 0) label = info.getLongLabel();
        String text = label != null && label.length() > 0 ? label.toString() : info.getId();
        // One line: the app-list cache stores labels line by line.
        return text.replace('\n', ' ').replace('\r', ' ');
    }

    private static ShortcutInfo find(LauncherApps launcherApps, String ownerPackage, String id) {
        if (launcherApps == null) return null;
        try {
            List<ShortcutInfo> found = launcherApps.getShortcuts(
                    new LauncherApps.ShortcutQuery()
                            .setPackage(ownerPackage)
                            .setShortcutIds(Collections.singletonList(id))
                            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
                    Process.myUserHandle());
            return found == null || found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** {@link LauncherApps} when we hold the shortcut-host permission. */
    private static LauncherApps host(Context ctx) {
        if (ctx == null) return null;
        try {
            LauncherApps launcherApps = ctx.getSystemService(LauncherApps.class);
            return launcherApps != null && launcherApps.hasShortcutHostPermission()
                    ? launcherApps : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
