package tv.glass.launcher;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;

/**
 * Runs fixed shell commands over the device's own ADB daemon at 127.0.0.1:5555.
 *
 * <p>Callers must run {@link #execute} off the main thread and pass only
 * commands built from fixed plans ({@link GoogleTvSetupPlan},
 * {@link TvDebloatCatalog}); this class has no arbitrary-shell entry point
 * of its own.
 */
final class LocalAdb {

    private static final String LOCAL_ADB_HOST = "127.0.0.1";
    private static final int LOCAL_ADB_PORT = 5555;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    // adbd sends nothing when the debugging prompt is cancelled, so a cancel
    // only surfaces when this timeout expires. Keep it long enough to reach
    // Allow with a remote, short enough that a cancel fails promptly.
    private static final int AUTH_TIMEOUT_MS = 20_000;

    private LocalAdb() { }

    /**
     * @param ignoreFirstFailure lets a caller mark its first command as best effort.
     * @return the first required-command failure, or {@code null}.
     */
    static String execute(Context context, List<String> commands, boolean ignoreFirstFailure) {
        try (Dadb dadb = connect(context)) {
            for (int i = 0; i < commands.size(); i++) {
                String command = commands.get(i);
                AdbShellResponse response = dadb.shell(command);
                String output = response.getAllOutput() == null
                        ? "" : response.getAllOutput().trim();
                if (!GoogleTvSetupPlan.responseLooksSuccessful(response.getExitCode(), output)
                        && !(ignoreFirstFailure && i == 0)) {
                    return "Command failed: " + command
                            + (output.isEmpty() ? "" : "\n" + output);
                }
            }
            return null;
        } catch (ConnectException e) {
            return "Could not connect to local ADB at 127.0.0.1:5555. Enable Network "
                    + "debugging in Developer options, then try again.";
        } catch (SocketTimeoutException e) {
            return "The debugging prompt was cancelled or timed out. Try again and "
                    + "choose Allow on the TV's debugging prompt.";
        } catch (Exception e) {
            return "ADB command failed: " + usefulMessage(e);
        }
    }

    private static Dadb connect(Context context) throws IOException {
        File privateKey = new File(context.getNoBackupFilesDir(), "glasslauncher_adbkey");
        File publicKey = new File(context.getNoBackupFilesDir(), "glasslauncher_adbkey.pub");
        if (!privateKey.isFile() || !publicKey.isFile()) {
            AdbKeyPair.generate(privateKey, publicKey);
        }
        AdbKeyPair keyPair = AdbKeyPair.read(privateKey, publicKey);
        return Dadb.create(LOCAL_ADB_HOST, LOCAL_ADB_PORT, keyPair,
                CONNECT_TIMEOUT_MS, AUTH_TIMEOUT_MS);
    }

    private static String usefulMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }
}
