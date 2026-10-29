package tv.glass.launcher;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Disables and re-enables {@link TvDebloatCatalog} packages over local ADB.
 *
 * <p>{@link #setDisabled} and {@link #disableRecommended} connect to ADB and
 * must run off the main thread.
 */
final class TvDebloat {

    static final class InstalledEntry {
        final TvDebloatCatalog.Entry entry;
        final boolean enabled;

        InstalledEntry(TvDebloatCatalog.Entry entry, boolean enabled) {
            this.entry = entry;
            this.enabled = enabled;
        }
    }

    private TvDebloat() { }

    /** Catalog entries installed on this device, in catalog order. */
    static List<InstalledEntry> inspect(Context context) {
        PackageManager pm = context.getPackageManager();
        ArrayList<InstalledEntry> installed = new ArrayList<>();
        for (TvDebloatCatalog.Entry entry : TvDebloatCatalog.ENTRIES) {
            GoogleTvSetup.PackageState state = GoogleTvSetup.packageState(pm, entry.packageName);
            if (state.installed) installed.add(new InstalledEntry(entry, state.enabled));
        }
        return Collections.unmodifiableList(installed);
    }

    /** @return {@code null} on success, otherwise a message for the user. */
    static String setDisabled(Context context, String packageName, boolean disable) {
        String command = disable
                ? TvDebloatCatalog.disableCommand(packageName)
                : TvDebloatCatalog.enableCommand(packageName);
        return run(context, Collections.singletonList(command));
    }

    /** @return {@code null} on success, otherwise a message for the user. */
    static String disableRecommended(Context context) {
        ArrayList<String> enabledPackages = new ArrayList<>();
        for (InstalledEntry installed : inspect(context)) {
            if (installed.enabled) enabledPackages.add(installed.entry.packageName);
        }
        List<String> commands = TvDebloatCatalog.disableRecommendedCommands(enabledPackages);
        if (commands.isEmpty()) return null;
        return run(context, commands);
    }

    private static String run(Context context, List<String> commands) {
        String failure = LocalAdb.execute(context.getApplicationContext(), commands, false);
        GoogleTvSetup.pauseForPackageManager();
        return failure;
    }
}
