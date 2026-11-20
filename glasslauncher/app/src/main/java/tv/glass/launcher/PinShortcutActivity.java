package tv.glass.launcher;

import android.app.Activity;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Handles {@code CONFIRM_PIN_SHORTCUT}: Android starts this when an app asks
 * the default Home app to pin a shortcut. The user already chose to add it
 * in that app, so it's accepted straight away with a toast instead of a
 * second confirmation. No UI of its own; it finishes immediately.
 */
public final class PinShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String message = getString(R.string.toast_shortcut_not_added);
        try {
            LauncherApps launcherApps = getSystemService(LauncherApps.class);
            LauncherApps.PinItemRequest request =
                    launcherApps != null ? launcherApps.getPinItemRequest(getIntent()) : null;
            ShortcutInfo info = request != null
                    && request.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT
                    && request.isValid() ? request.getShortcutInfo() : null;
            if (info != null) {
                // Set before accept(): accepting fires the shortcut-changed
                // callback that reloads the app list.
                String identity = AppInfo.shortcutIdentity(info.getPackage(), info.getId());
                PinnedShortcuts.pendingHomeIdentity = identity;
                if (request.accept()) {
                    message = getString(R.string.toast_shortcut_added, PinnedShortcuts.label(info));
                } else if (identity.equals(PinnedShortcuts.pendingHomeIdentity)) {
                    PinnedShortcuts.pendingHomeIdentity = null;
                }
            }
        } catch (RuntimeException ignored) { /* invalid or stale request */ }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }
}
