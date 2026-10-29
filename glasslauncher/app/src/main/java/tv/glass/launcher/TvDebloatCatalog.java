package tv.glass.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fixed list of preinstalled TV packages that the debloat page can disable.
 *
 * <p>Entries come from the Universal Android Debloater list, limited to TV
 * packages it rates Recommended or Advanced. Anything tied to HDMI inputs,
 * volume, settings, launchers or casting is left out. Disabling uses
 * {@code pm disable-user}, which is fully reversible and never uninstalls.
 *
 * <p>Commands are only ever built for packages in this list, so no
 * device-supplied or user-supplied value reaches the shell.
 */
final class TvDebloatCatalog {

    static final class Entry {
        final String packageName;
        final String label;
        /** What stops working once the package is disabled. */
        final String effectWhenDisabled;
        /** Safe to disable with no visible feature loss; included in "Disable recommended". */
        final boolean recommended;

        Entry(String packageName, String label, String effectWhenDisabled, boolean recommended) {
            if (!isSafePackageName(packageName)) {
                throw new IllegalArgumentException("Invalid package name: " + packageName);
            }
            this.packageName = packageName;
            this.label = label;
            this.effectWhenDisabled = effectWhenDisabled;
            this.recommended = recommended;
        }
    }

    static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            // Google
            new Entry("com.google.android.apps.tv.netoscope", "Connectivity Diagnostics",
                    "Removes Google's network diagnostics tool.", false),
            new Entry("com.google.android.feedback", "Google Feedback",
                    "Stops sending crash feedback to Google.", true),
            new Entry("com.google.android.tv.bugreportsender", "Bug Report Sender",
                    "Stops sending bug reports to Google.", true),
            new Entry("com.google.android.apps.tv.dreamx", "Ambient Mode",
                    "Removes the Google TV screensaver and the content it promotes.", false),
            new Entry("com.google.android.backdrop", "Backdrop",
                    "Removes the older Android TV photo screensaver.", false),
            new Entry("com.google.android.katniss", "Google Assistant",
                    "Voice search and the remote's microphone button stop working.", false),
            new Entry("com.google.android.tv.remote.service", "Android TV Remote Service",
                    "Phones can no longer be used as a remote for this TV.", false),
            new Entry("com.google.android.play.games", "Google Play Games",
                    "Removes the Play Games app.", false),
            new Entry("com.google.android.videos", "Google TV app",
                    "Removes Google's movie and show store and purchased library.", false),
            new Entry("com.google.android.youtube.tvmusic", "YouTube Music",
                    "Removes the YouTube Music app.", false),
            // Viewing-data and auto-install services shipped by TV makers
            new Entry("tv.alphonso.alphonso_eula", "Alphonso",
                    "Stops Alphonso's viewing-data recommendations service.", true),
            new Entry("android.autoinstalls.config.TCL.PAI", "TCL Auto Installs",
                    "Stops partner apps from installing during setup or factory reset.", true),
            // Sony
            new Entry("com.sony.dtv.promos", "Sony Promotions",
                    "Removes Sony's offers and promotions.", true),
            new Entry("com.sony.dtv.sonyselect", "Sony Select",
                    "Removes Sony's app and subscription recommendations.", true),
            new Entry("com.sony.dtv.notificationcenter", "Sony Notification Center",
                    "Stops reminders to use Sony's apps.", true),
            new Entry("com.sony.dtv.smarthelp", "Sony Help Guide",
                    "Removes the built-in TV manual.", false),
            new Entry("com.sony.dtv.livingfit", "Sony Ambient Pictures",
                    "Removes the picture-frame display mode.", false),
            new Entry("com.sony.dtv.seeds.iot", "Sony Smart Speaker Control",
                    "Smart speakers such as Alexa can no longer control the TV.", false),
            new Entry("com.sony.dtv.recapp", "Sony Reminders",
                    "Removes the reminder and timer viewer.", false),
            // Xiaomi
            new Entry("com.miui.tv.analytics", "Mi TV Analytics",
                    "Stops Xiaomi's analytics service.", true),
            new Entry("com.mitv.tvhome.michannel", "Mi Channel",
                    "Removes Xiaomi's recommended-content channel.", false)
    ));

    private TvDebloatCatalog() { }

    static Entry find(String packageName) {
        for (Entry entry : ENTRIES) {
            if (entry.packageName.equals(packageName)) return entry;
        }
        return null;
    }

    static String disableCommand(String packageName) {
        return "pm disable-user --user 0 " + requireCatalogPackage(packageName);
    }

    static String enableCommand(String packageName) {
        return "pm enable --user 0 " + requireCatalogPackage(packageName);
    }

    /** Disable commands for every recommended entry in {@code installedEnabledPackages}. */
    static List<String> disableRecommendedCommands(List<String> installedEnabledPackages) {
        ArrayList<String> commands = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.recommended && installedEnabledPackages.contains(entry.packageName)) {
                commands.add(disableCommand(entry.packageName));
            }
        }
        return Collections.unmodifiableList(commands);
    }

    private static String requireCatalogPackage(String packageName) {
        if (find(packageName) == null) {
            throw new IllegalArgumentException("Not a debloat catalog package: " + packageName);
        }
        return packageName;
    }

    static boolean isSafePackageName(String packageName) {
        return packageName != null && packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    }
}
