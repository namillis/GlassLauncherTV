package tv.glass.launcher;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Debug-only home switches, read once when the app starts:
 *  <ul>
 *  <li>{@code files/force_network_lost}: show the clock bubble's
 *      network-lost badge while a network is up (the emulator's Ethernet
 *      cannot be taken down from adb).</li>
 *  <li>{@code files/show_message}: show its text as a message (H4B) once,
 *      1.5 s after the home screen is built, for captures.</li>
 *  </ul>
 *  Create one with {@code adb shell run-as tv.glass.launcher.debug ...} and
 *  restart the app. The release copy has no switches. */
final class HomeDebug {
    private HomeDebug() {}

    /** The debug app's files dir, read before any Context exists. */
    private static final String DIR = new File(android.os.Environment.getDataDirectory(),
            "data/tv.glass.launcher.debug/files").getPath() + "/";
    private static final boolean NETWORK_LOST = new File(DIR + "force_network_lost").exists();

    static boolean forceNetworkLost() {
        return NETWORK_LOST;
    }

    /** {@code files/force_cc_<state>} (offline, bt_off, bt_audio) draws that
     *  Control Center tile state, for captures: the emulator cannot drop
     *  its network or connect a Bluetooth headset. Read on every build of
     *  the tiles, so no restart is needed. */
    static boolean forceCc(String state) {
        return new File(DIR + "force_cc_" + state).exists();
    }

    /** The text of files/show_message, or null. */
    static String startupMessage() {
        File f = new File(DIR + "show_message");
        if (!f.exists()) return null;
        try {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? null : s.replace("\\n", "\n");
        } catch (Exception e) {
            return null;
        }
    }
}
