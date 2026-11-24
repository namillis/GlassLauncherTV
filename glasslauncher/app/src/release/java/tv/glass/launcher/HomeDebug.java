package tv.glass.launcher;

/** Release copy: no switches. See the debug source set. */
final class HomeDebug {
    private HomeDebug() {}

    static boolean forceNetworkLost() {
        return false;
    }

    static String startupMessage() {
        return null;
    }
}
