package tv.glass.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional Home-button override for Google TV / Android TV (issue #45).
 *
 * <p>On Google TV the remote's Home key always goes to Google's launcher, and
 * the only full fix is the one-time ADB setup ({@link GoogleTvSetup}). This
 * service is the no-ADB alternative: it filters the Home key and brings Glass
 * to the front, and as a fallback it reclaims the foreground when a stock
 * launcher window appears on boot, on wake, or from a HOME intent.
 *
 * <p>Design notes, all load-bearing:
 * <ul>
 *   <li><b>No-op when Glass is already Home.</b> If Glass is the default Home
 *       (e.g. after the ADB setup) the override does nothing, so the two
 *       approaches never fight.</li>
 *   <li><b>{@link #onKeyEvent} stays trivial.</b> Every key on the device
 *       passes through it while filtering is on. Any non-Home key returns
 *       immediately with no allocation and no binder call; Android disables a
 *       service that throws from a callback, so everything is wrapped.</li>
 *   <li><b>Volume long-press.</b> On TV, key filtering makes the system handle
 *       volume itself and drop the event, which kills key auto-repeat (holding
 *       volume stops ramping). Matching Projectivy / CleanYourTV, a
 *       VOLUME_CHANGED broadcast pauses key filtering for a few seconds so a
 *       hold repeats; the window-state reclaim still works during the pause.</li>
 *   <li><b>No screen content.</b> {@code canRetrieveWindowContent=false} and
 *       {@code isAccessibilityTool=false} in the config XML; only the Home key
 *       and the foreground launcher package are read.</li>
 * </ul>
 */
public final class HomeButtonService extends AccessibilityService {

    private static final String TAG = "GlassHomeBtn";

    /** Hold time at or beyond which a Home press counts as a long press. */
    private static final long LONG_PRESS_MS = 500L;
    /** How long key filtering is paused after a volume change (see class doc). */
    private static final long VOLUME_PAUSE_MS = 4_000L;
    /** Reclaim suppression window after our own launch or a long press. */
    private static final long SUPPRESS_MS = 1_500L;
    /** AudioService's volume broadcast. Not a public constant, but a stable
     *  string; the receiver only flips a timer, so a rename degrades to "no
     *  volume pause", never a crash. */
    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Cached "is Glass the default Home" — refreshed on connect and whenever a
     *  launcher window changes, never from {@link #onKeyEvent}. */
    private volatile boolean glassIsHome = false;
    /** True while key filtering is paused after a volume change. */
    private volatile boolean filteringPaused = false;

    private long lastForwardTime = 0L;
    private long lastLongPressTime = 0L;

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            onVolumeChanged();
        }
    };

    private final Runnable resumeFiltering = new Runnable() {
        @Override public void run() {
            filteringPaused = false;
            applyKeyFiltering(true);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            refreshGlassIsHome();
            registerVolumeReceiver();
            applyKeyFiltering(true);
        } catch (Throwable t) {
            Log.w(TAG, "onServiceConnected", t);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        try {
            if (event == null) return false;
            int keyCode = event.getKeyCode();
            // Fast path: anything that is not Home is none of our business.
            if (keyCode != KeyEvent.KEYCODE_HOME) return false;

            boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
            long heldMs = isDown ? 0L : event.getEventTime() - event.getDownTime();
            HomeOverridePlan.KeyAction decision =
                    HomeOverridePlan.decideKey(true, isDown, isActive(), heldMs, LONG_PRESS_MS);
            switch (decision) {
                case CONSUME_SHORT:
                    bringGlassForward();
                    return true;
                case CONSUME_LONG:
                    doLongPressHome();
                    return true;
                case CONSUME_HOLD:
                    return true;
                case PASS:
                default:
                    return false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "onKeyEvent", t);
            return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event == null
                    || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return;
            }
            CharSequence pkg = event.getPackageName();
            String pkgName = pkg == null ? null : pkg.toString();
            // A launcher window changing is exactly when the default-Home
            // answer can change, so keep the cache fresh here (never on a key).
            refreshGlassIsHome();
            long now = SystemClock.uptimeMillis();
            if (HomeOverridePlan.shouldReclaim(pkgName, isActive(),
                    now - lastForwardTime, now - lastLongPressTime, SUPPRESS_MS)) {
                bringGlassForward();
            }
        } catch (Throwable t) {
            Log.w(TAG, "onAccessibilityEvent", t);
        }
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt — this service renders no feedback.
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    /** Engaged only when Glass is not already Home and filtering is not paused. */
    private boolean isActive() {
        return !glassIsHome && !filteringPaused;
    }

    /** Bring Glass to the front with a real MAIN+HOME intent so the activity's
     *  own onNewIntent handling — including the "Home opens all apps" option —
     *  behaves exactly as it does when Glass is the default Home. */
    private void bringGlassForward() {
        lastForwardTime = SystemClock.uptimeMillis();
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(this, LauncherActivity.class))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            Log.w(TAG, "bringGlassForward", t);
        }
    }

    /** Long-press Home. A consumed DOWN cannot be handed back to the system, so
     *  the stock launcher's long-press panel cannot be reproduced directly;
     *  Recents is a useful, documented stand-in. This is the most likely thing
     *  to tweak after on-device review. */
    private void doLongPressHome() {
        lastLongPressTime = SystemClock.uptimeMillis();
        try {
            if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
                bringGlassForward();
            }
        } catch (Throwable t) {
            bringGlassForward();
        }
    }

    private void onVolumeChanged() {
        filteringPaused = true;
        applyKeyFiltering(false);
        handler.removeCallbacks(resumeFiltering);
        handler.postDelayed(resumeFiltering, VOLUME_PAUSE_MS);
    }

    /** Toggle FLAG_REQUEST_FILTER_KEY_EVENTS at runtime. Dropping it during the
     *  volume window takes the service off the key-event path entirely, which
     *  is what restores volume auto-repeat. */
    private void applyKeyFiltering(boolean on) {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) return;
            if (on) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            } else {
                info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            }
            setServiceInfo(info);
        } catch (Throwable t) {
            Log.w(TAG, "applyKeyFiltering", t);
        }
    }

    private void refreshGlassIsHome() {
        try {
            PackageManager pm = getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            String pkg = (ri != null && ri.activityInfo != null)
                    ? ri.activityInfo.packageName : null;
            glassIsHome = getPackageName().equals(pkg);
        } catch (Throwable t) {
            // Keep the last known value; never let this crash a callback.
        }
    }

    private void registerVolumeReceiver() {
        try {
            IntentFilter filter = new IntentFilter(ACTION_VOLUME_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(volumeReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(volumeReceiver, filter);
            }
        } catch (Throwable t) {
            Log.w(TAG, "registerVolumeReceiver", t);
        }
    }

    private void cleanup() {
        try {
            handler.removeCallbacks(resumeFiltering);
        } catch (Throwable ignored) {
            // no-op
        }
        try {
            unregisterReceiver(volumeReceiver);
        } catch (Throwable ignored) {
            // not registered yet, or already removed
        }
    }
}
