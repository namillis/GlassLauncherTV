package tv.glass.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bluetooth pages of the settings sheet: paired devices with live
 *  connection state, a page per device (audio connect/disconnect and the
 *  system device page), an in-sheet scan-and-pair page on Android 12+, and a
 *  way out to system settings. Receivers and profile proxies live while the
 *  device list page is open. */
@SuppressLint("MissingPermission")
final class BluetoothPages {

    interface Host {
        SettingsSheet sheet();
        void closeSheet();
        void openBtSettings();
        void openBtPairing();
        void openSystemSettings();
        boolean hasBtSettingsScreen();
        boolean hasDeviceSettingsScreen();
        void openDeviceSettings(BluetoothDevice device, String name);
        void refreshPill();
        void toast(String text);
    }

    static final String PAGE_LIST = "bluetooth";
    static final String PAGE_DEVICE = "bluetooth_device";

    private static final int MAX_LISTED_DEVICES = 6;
    private static final int MAX_FOUND_DEVICES = 8;
    private static final long AUDIO_TIMEOUT_MS = 20_000L;
    private static final String UUID_A2DP_SINK = "0000110b-0000-1000-8000-00805f9b34fb";

    private final Activity activity;
    private final Host host;

    private boolean discovering;
    private BluetoothDevice pairing;
    private final Set<String> failedPairs = new HashSet<>();
    private final LinkedHashMap<String, BluetoothDevice> found = new LinkedHashMap<>();
    private final Set<String> aclConnected = new HashSet<>();
    private final Map<Integer, BluetoothProfile> proxies = new HashMap<>();
    private final Map<String, Integer> pendingAudio = new HashMap<>();
    private final Map<String, Runnable> audioTimeouts = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean btSettingsScreen;
    private boolean deviceSettingsScreen;
    /** Device whose page is open, or null. */
    private BluetoothDevice detail;
    private BroadcastReceiver receiver;

    BluetoothPages(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    /** The device list, ready to push. Opening it starts listening for
     *  Bluetooth changes; leaving it (Back or closing the sheet) stops. */
    SettingsSheet.Page listPage() {
        btSettingsScreen = host.hasBtSettingsScreen();
        deviceSettingsScreen = host.hasDeviceSettingsScreen();
        register();
        SettingsSheet.Page page = new SettingsSheet.Page(PAGE_LIST, str(R.string.bt_title),
                str(R.string.settings_hint_open), this::listRows);
        page.iconRes = R.drawable.ic_bt;
        page.onLeave = () -> {
            stopDiscovery();
            pairing = null;
            clearPendingAudio();
            unregister();
        };
        // S3C: like tvOS's Remotes and Devices, the list scans while it is
        // open and shows what it finds under Available to pair.
        found.clear();
        failedPairs.clear();
        pairing = null;
        BluetoothAdapter a = adapter();
        if (canScanInPanel() && a != null && isEnabled(a)) startDiscovery();
        return page;
    }

    void destroy() {
        stopDiscovery();
        clearPendingAudio();
        unregister();
    }

    // ── Pages ───────────────────────────────────────────────────────────

    private List<SettingsSheet.Row> listRows() {
        List<SettingsSheet.Row> rows = new ArrayList<>();
        BluetoothAdapter a = adapter();
        if (!hasConnectPermission()) {
            rows.add(SettingsSheet.Row.info("need_permission", str(R.string.bt_panel_need_permission)));
        } else if (a == null || !isEnabled(a)) {
            rows.add(SettingsSheet.Row.info("off", str(R.string.bt_panel_off)));
        } else {
            List<BluetoothDevice> connected = new ArrayList<>();
            List<BluetoothDevice> idle = new ArrayList<>();
            Set<String> live = connectedAddresses(a);
            for (BluetoothDevice d : bonded(a)) {
                if (live.contains(d.getAddress())) connected.add(d);
                else idle.add(d);
            }
            List<BluetoothDevice> devices = new ArrayList<>(connected);
            devices.addAll(idle);
            rows.add(SettingsSheet.Row.section("my_devices", str(R.string.bt_section_my_devices)));
            for (int i = 0; i < devices.size() && i < MAX_LISTED_DEVICES; i++) {
                BluetoothDevice d = devices.get(i);
                // S3C: the state as the row's gray value, like tvOS's
                // Remotes and Devices; the full summary describes it.
                rows.add(new SettingsSheet.Row("device:" + d.getAddress(), nameOf(d))
                        .iconRes(iconFor(d))
                        .value(activity.getString(R.string.settings_value_open,
                                status(d, i < connected.size())), SettingsSheet.VALUE_OFF)
                        .description(summary(d, i < connected.size()))
                        .onActivate(() -> openDevice(d)));
            }
            if (devices.isEmpty()) {
                rows.add(SettingsSheet.Row.info("no_devices", str(R.string.bt_panel_no_devices)));
            } else if (devices.size() > MAX_LISTED_DEVICES) {
                rows.add(SettingsSheet.Row.info("more", activity.getString(R.string.bt_panel_more,
                        devices.size() - MAX_LISTED_DEVICES)));
            }
            if (canScanInPanel()) {
                addAvailableRows(rows);
            } else {
                // Before Android 12 the launcher can't scan; Android's own
                // pairing screen does it.
                rows.add(SettingsSheet.Row.section("other_devices", str(R.string.bt_section_other)));
                rows.add(new SettingsSheet.Row("pair_new", str(R.string.bt_panel_pair_new))
                        .iconRes(R.drawable.ic_btp_add)
                        .description(str(R.string.bt_desc_pair_new))
                        .onActivate(this::openPairing));
            }
        }
        rows.add(settingsRow());
        return rows;
    }

    /** Available to pair: the scan's state, each device it found with Pair
     *  as its value, and Search again once the scan has ended. */
    private void addAvailableRows(List<SettingsSheet.Row> rows) {
        rows.add(SettingsSheet.Row.section("available", str(R.string.bt_panel_available)));
        if (discovering) {
            rows.add(SettingsSheet.Row.info("searching", str(R.string.bt_panel_searching))
                    .iconRes(R.drawable.ic_btp_searching));
        } else if (found.isEmpty()) {
            rows.add(SettingsSheet.Row.info("none_found", str(R.string.bt_panel_none_found)));
        }
        for (BluetoothDevice d : found.values()) {
            String status;
            int style = SettingsSheet.VALUE_OFF;
            if (sameDevice(d, pairing)) status = str(R.string.bt_status_pairing);
            else if (failedPairs.contains(d.getAddress())) status = str(R.string.bt_status_pair_failed);
            else {
                status = str(R.string.bt_status_pair);
                style = SettingsSheet.VALUE_ACCENT;
            }
            rows.add(new SettingsSheet.Row("found:" + d.getAddress(), nameOf(d))
                    .iconRes(iconFor(d))
                    .value(status, style)
                    .description(str(R.string.bt_desc_found))
                    .onActivate(() -> pair(d)));
        }
        if (!discovering) {
            rows.add(new SettingsSheet.Row("search_again", str(R.string.bt_panel_search_again))
                    .iconRes(R.drawable.ic_btp_searching)
                    .description(str(R.string.bt_desc_search_again))
                    .onActivate(() -> {
                        found.clear();
                        failedPairs.clear();
                        startDiscovery();
                    }));
        }
    }

    private SettingsSheet.Row settingsRow() {
        if (btSettingsScreen) {
            return new SettingsSheet.Row("bt_settings", str(R.string.bt_panel_settings))
                    .iconRes(R.drawable.ic_btp_settings)
                    .description(str(R.string.bt_desc_settings))
                    .onActivate(() -> {
                        host.closeSheet();
                        host.openBtSettings();
                    });
        }
        return new SettingsSheet.Row("bt_settings", str(R.string.bt_panel_open_settings))
                .iconRes(R.drawable.ic_btp_settings)
                .onActivate(() -> {
                    host.closeSheet();
                    host.openSystemSettings();
                });
    }

    /** "Connected · Audio", or the pending audio change while one runs. */
    private String summary(BluetoothDevice d, boolean connected) {
        return activity.getString(R.string.bt_device_summary, status(d, connected), kindOf(d));
    }

    /** "Connected", "Not connected", or the pending audio change. */
    private String status(BluetoothDevice d, boolean connected) {
        Integer target = pendingAudio.get(d.getAddress());
        return str(target != null
                ? (target == BluetoothProfile.STATE_CONNECTED
                        ? R.string.bt_status_connecting : R.string.bt_status_disconnecting)
                : connected ? R.string.bt_status_connected : R.string.bt_status_not_connected);
    }

    private void openDevice(BluetoothDevice d) {
        detail = d;
        SettingsSheet.Page page = new SettingsSheet.Page(PAGE_DEVICE, nameOf(d),
                str(R.string.settings_hint_open), () -> deviceRows(d));
        page.iconRes = iconFor(d);
        page.onLeave = () -> { if (sameDevice(detail, d)) detail = null; };
        host.sheet().push(page);
    }

    private List<SettingsSheet.Row> deviceRows(BluetoothDevice d) {
        List<SettingsSheet.Row> rows = new ArrayList<>();
        BluetoothAdapter a = adapter();
        boolean connected = a != null && connectedAddresses(a).contains(d.getAddress());
        rows.add(SettingsSheet.Row.info("status", summary(d, connected)));
        boolean any = false;
        Integer target = pendingAudio.get(d.getAddress());
        boolean a2dpUp = a2dpState(d) == BluetoothProfile.STATE_CONNECTED;
        if (target != null) {
            rows.add(new SettingsSheet.Row("audio", str(target == BluetoothProfile.STATE_CONNECTED
                    ? R.string.bt_status_connecting : R.string.bt_status_disconnecting))
                    .iconRes(R.drawable.ic_btp_searching)
                    .onActivate(() -> toggleAudio(d)));
            any = true;
        } else if (proxies.containsKey(BluetoothProfile.A2DP)
                && (a2dpUp || (!connected && isAudioSink(d)))) {
            rows.add(new SettingsSheet.Row("audio",
                    str(a2dpUp ? R.string.bt_action_disconnect : R.string.bt_action_connect))
                    .iconRes(a2dpUp ? R.drawable.ic_bt_off : R.drawable.ic_bt_connected)
                    .onActivate(() -> toggleAudio(d)));
            any = true;
        } else if (!connected && isInputDevice(d)) {
            rows.add(SettingsSheet.Row.info("press_button",
                    activity.getString(R.string.bt_detail_press_button, nameOf(d))));
        }
        if (deviceSettingsScreen) {
            rows.add(new SettingsSheet.Row("device_settings", str(R.string.bt_detail_rename_unpair))
                    .iconRes(R.drawable.ic_btp_settings)
                    .onActivate(() -> {
                        String name = nameOf(d);
                        host.closeSheet();
                        host.openDeviceSettings(d, name);
                    }));
            any = true;
        }
        if (!any) rows.add(settingsRow());
        return rows;
    }

    /** Pair new device, shown only where the list can't scan (before
     *  Android 12): Android's own pairing screen. */
    private void openPairing() {
        host.closeSheet();
        host.openBtPairing();
    }

    private String pageId() {
        SettingsSheet sheet = host.sheet();
        return sheet != null && sheet.isShowing() ? sheet.pageId() : null;
    }

    /** Back out of the device or pairing page to {@code pageId}. */
    private void backTo(String pageId) {
        SettingsSheet sheet = host.sheet();
        if (sheet == null) return;
        String current = pageId();
        while (current != null && !current.equals(pageId) && PAGE_DEVICE.equals(current)) {
            sheet.back();
            current = pageId();
        }
    }

    /** Re-read the open Bluetooth page, if one is open. */
    private void refresh() {
        String current = pageId();
        if (PAGE_LIST.equals(current) || PAGE_DEVICE.equals(current)) {
            host.sheet().refresh();
        }
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private void toggleAudio(BluetoothDevice d) {
        if (d == null) return;
        final String addr = d.getAddress();
        if (pendingAudio.containsKey(addr)) return;
        final boolean connect = a2dpState(d) != BluetoothProfile.STATE_CONNECTED;
        if (!invokeA2dp(proxies.get(BluetoothProfile.A2DP), connect, d)) {
            host.toast(activity.getString(connect ? R.string.toast_bt_connect_failed
                    : R.string.toast_bt_disconnect_failed, nameOf(d)));
            return;
        }
        final int target = connect ? BluetoothProfile.STATE_CONNECTED
                : BluetoothProfile.STATE_DISCONNECTED;
        pendingAudio.put(addr, target);
        Runnable timeout = () -> {
            audioTimeouts.remove(addr);
            if (pendingAudio.remove(addr) == null || receiver == null) return;
            if (a2dpState(d) != target) {
                host.toast(activity.getString(connect ? R.string.toast_bt_connect_failed
                        : R.string.toast_bt_disconnect_failed, nameOf(d)));
            }
            host.refreshPill();
            refresh();
        };
        audioTimeouts.put(addr, timeout);
        handler.postDelayed(timeout, AUDIO_TIMEOUT_MS);
        refresh();
    }

    private void onA2dpState(BluetoothDevice d, int state, int prev) {
        if (d == null) return;
        String addr = d.getAddress();
        Integer target = pendingAudio.get(addr);
        if (target == null) return;
        if (state == target) {
            clearPendingAudio(addr);
        } else if (target == BluetoothProfile.STATE_CONNECTED
                && state == BluetoothProfile.STATE_DISCONNECTED
                && prev == BluetoothProfile.STATE_CONNECTING) {
            clearPendingAudio(addr);
            host.toast(activity.getString(R.string.toast_bt_connect_failed, nameOf(d)));
        }
    }

    private void clearPendingAudio(String addr) {
        pendingAudio.remove(addr);
        Runnable t = audioTimeouts.remove(addr);
        if (t != null) handler.removeCallbacks(t);
    }

    private void clearPendingAudio() {
        for (Runnable t : audioTimeouts.values()) handler.removeCallbacks(t);
        audioTimeouts.clear();
        pendingAudio.clear();
    }

    /** BluetoothA2dp.connect/disconnect are hidden but on the unrestricted
     *  hidden-API list through Android 14, gated only by the normal Bluetooth
     *  permission. Any failure just reports false. */
    @SuppressLint({"DiscouragedPrivateApi", "PrivateApi"})
    private static boolean invokeA2dp(BluetoothProfile proxy, boolean connect, BluetoothDevice d) {
        if (!(proxy instanceof BluetoothA2dp)) return false;
        try {
            Object r = BluetoothA2dp.class
                    .getMethod(connect ? "connect" : "disconnect", BluetoothDevice.class)
                    .invoke(proxy, d);
            return Boolean.TRUE.equals(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private boolean canScanInPanel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        return activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED
                && hasConnectPermission();
    }

    private void startDiscovery() {
        BluetoothAdapter a = adapter();
        boolean started = false;
        if (a != null) {
            try {
                if (a.isDiscovering()) a.cancelDiscovery();
                started = a.startDiscovery();
            } catch (SecurityException ignored) {
            }
        }
        discovering = started;
        refresh();
    }

    private void stopDiscovery() {
        if (!discovering) return;
        discovering = false;
        BluetoothAdapter a = adapter();
        if (a == null) return;
        try {
            a.cancelDiscovery();
        } catch (SecurityException ignored) {
        }
    }

    private void pair(BluetoothDevice d) {
        if (d == null || pairing != null) return;
        stopDiscovery();
        failedPairs.remove(d.getAddress());
        boolean ok;
        try {
            ok = d.createBond();
        } catch (SecurityException e) {
            ok = false;
        }
        if (ok) pairing = d;
        else failedPairs.add(d.getAddress());
        refresh();
    }

    // ── Broadcasts and profile proxies ──────────────────────────────────

    private void register() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                onBroadcast(intent);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        try {
            activity.registerReceiver(receiver, f);
        } catch (RuntimeException e) {
            receiver = null;
        }
        openProxies();
    }

    private void unregister() {
        BroadcastReceiver r = receiver;
        receiver = null;
        if (r != null) {
            try { activity.unregisterReceiver(r); } catch (RuntimeException ignored) { }
        }
        BluetoothAdapter a = adapter();
        // closeProfileProxy can call onServiceDisconnected synchronously
        // (BluetoothHeadset does on Android 14), which removes from proxies.
        java.util.List<Map.Entry<Integer, BluetoothProfile>> open =
                new java.util.ArrayList<>(proxies.entrySet());
        proxies.clear();
        aclConnected.clear();
        if (a == null) return;
        for (Map.Entry<Integer, BluetoothProfile> e : open) {
            try { a.closeProfileProxy(e.getKey(), e.getValue()); } catch (RuntimeException ignored) { }
        }
    }

    private void openProxies() {
        BluetoothAdapter a = adapter();
        if (a == null || !hasConnectPermission()) return;
        int[] profiles = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? new int[] { BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.LE_AUDIO }
                : new int[] { BluetoothProfile.A2DP, BluetoothProfile.HEADSET };
        BluetoothProfile.ServiceListener l = new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (receiver == null) {
                    try { a.closeProfileProxy(profile, proxy); } catch (RuntimeException ignored) { }
                    return;
                }
                proxies.put(profile, proxy);
                refresh();
            }
            @Override public void onServiceDisconnected(int profile) {
                proxies.remove(profile);
            }
        };
        for (int p : profiles) {
            try { a.getProfileProxy(activity, l, p); } catch (RuntimeException ignored) { }
        }
    }

    private void onBroadcast(Intent intent) {
        if (receiver == null) return;
        String act = intent.getAction();
        if (act == null) return;
        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        switch (act) {
            case BluetoothDevice.ACTION_ACL_CONNECTED:
                if (d != null) aclConnected.add(d.getAddress());
                break;
            case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                if (d != null) aclConnected.remove(d.getAddress());
                break;
            case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                discovering = true;
                break;
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                discovering = false;
                break;
            case BluetoothDevice.ACTION_FOUND:
            case BluetoothDevice.ACTION_NAME_CHANGED:
                if (d != null && discovering && !found.containsKey(d.getAddress())
                        && found.size() < MAX_FOUND_DEVICES
                        && !TextUtils.isEmpty(safeName(d)) && !isBonded(d)) {
                    found.put(d.getAddress(), d);
                }
                break;
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                onBondChanged(d, intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE));
                break;
            case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                onA2dpState(d, intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1),
                        intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1));
                break;
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                        != BluetoothAdapter.STATE_ON) {
                    discovering = false;
                    backTo(PAGE_LIST);
                }
                break;
            default:
                break;
        }
        BluetoothDevice shown = detail;
        if (shown != null && !isBonded(shown)) backTo(PAGE_LIST);   // unpaired elsewhere
        host.refreshPill();
        refresh();
    }

    private void onBondChanged(BluetoothDevice d, int state) {
        if (d == null || !sameDevice(d, pairing)) return;
        if (state == BluetoothDevice.BOND_BONDED) {
            pairing = null;
            // It moves from Available to pair up to My devices.
            found.remove(d.getAddress());
            backTo(PAGE_LIST);
            host.toast(activity.getString(R.string.toast_bt_paired, nameOf(d)));
        } else if (state == BluetoothDevice.BOND_NONE) {
            pairing = null;
            failedPairs.add(d.getAddress());
        }
    }

    // ── Bluetooth reads ─────────────────────────────────────────────────

    private BluetoothAdapter adapter() {
        BluetoothManager bm = activity.getSystemService(BluetoothManager.class);
        return bm != null ? bm.getAdapter() : null;
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isEnabled(BluetoothAdapter a) {
        try {
            return a.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    private List<BluetoothDevice> bonded(BluetoothAdapter a) {
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            Set<BluetoothDevice> s = a.getBondedDevices();
            if (s != null) out.addAll(s);
        } catch (SecurityException ignored) {
        }
        return out;
    }

    private boolean isBonded(BluetoothDevice d) {
        try {
            return d.getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (SecurityException e) {
            return false;
        }
    }

    private int a2dpState(BluetoothDevice d) {
        BluetoothProfile p = proxies.get(BluetoothProfile.A2DP);
        if (p == null || d == null) return BluetoothProfile.STATE_DISCONNECTED;
        try {
            return p.getConnectionState(d);
        } catch (RuntimeException e) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    private boolean isAudioSink(BluetoothDevice d) {
        ParcelUuid[] uuids;
        try {
            uuids = d.getUuids();
        } catch (SecurityException e) {
            uuids = null;
        }
        if (uuids != null && uuids.length > 0) {
            for (ParcelUuid u : uuids) {
                if (u != null && UUID_A2DP_SINK.equalsIgnoreCase(u.toString())) return true;
            }
            return false;
        }
        BluetoothClass c = bluetoothClass(d);
        return c != null && c.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    private boolean isInputDevice(BluetoothDevice d) {
        BluetoothClass c = bluetoothClass(d);
        return c != null && c.getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL;
    }

    private static BluetoothClass bluetoothClass(BluetoothDevice d) {
        try {
            return d.getBluetoothClass();
        } catch (SecurityException e) {
            return null;
        }
    }

    private Set<String> connectedAddresses(BluetoothAdapter a) {
        Set<String> out = new HashSet<>(aclConnected);
        for (BluetoothProfile p : proxies.values()) {
            try {
                for (BluetoothDevice d : p.getConnectedDevices()) out.add(d.getAddress());
            } catch (RuntimeException ignored) {
            }
        }
        BluetoothManager bm = activity.getSystemService(BluetoothManager.class);
        if (bm != null) {
            try {
                for (BluetoothDevice d : bm.getConnectedDevices(BluetoothProfile.GATT)) {
                    out.add(d.getAddress());
                }
            } catch (RuntimeException ignored) {
            }
        }
        for (BluetoothDevice d : bonded(a)) {
            if (out.contains(d.getAddress())) continue;
            try {
                Object r = d.getClass().getMethod("isConnected").invoke(d);
                if (Boolean.TRUE.equals(r)) out.add(d.getAddress());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return out;
    }

    private String safeName(BluetoothDevice d) {
        try {
            return d.getName();
        } catch (SecurityException e) {
            return null;
        }
    }

    private String nameOf(BluetoothDevice d) {
        String n = safeName(d);
        return TextUtils.isEmpty(n) ? str(R.string.bt_unknown_device) : n;
    }

    private int iconFor(BluetoothDevice d) {
        BluetoothClass c = bluetoothClass(d);
        if (c == null) return R.drawable.ic_bt;
        int major = c.getMajorDeviceClass();
        int dev = c.getDeviceClass();
        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            if (dev == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
                    || dev == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO
                    || dev == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO
                    || dev == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) {
                return R.drawable.ic_btp_speaker;
            }
            return R.drawable.ic_btp_headphones;
        }
        if (major == BluetoothClass.Device.Major.PERIPHERAL) {
            int minor = dev & 0x3C;
            if ((dev & 0x40) != 0) return R.drawable.ic_btp_keyboard;
            if (minor == 0x08 || minor == 0x04) return R.drawable.ic_btp_gamepad;
            return R.drawable.ic_btp_remote;
        }
        return R.drawable.ic_bt;
    }

    /** Short device type shown after the status, from the same class bits
     *  as {@link #iconFor}. */
    private String kindOf(BluetoothDevice d) {
        int icon = iconFor(d);
        if (icon == R.drawable.ic_btp_speaker || icon == R.drawable.ic_btp_headphones) {
            return str(R.string.bt_kind_audio);
        }
        if (icon == R.drawable.ic_btp_keyboard) return str(R.string.bt_kind_keyboard);
        if (icon == R.drawable.ic_btp_gamepad) return str(R.string.bt_kind_gamepad);
        if (icon == R.drawable.ic_btp_remote) return str(R.string.bt_kind_remote);
        return str(R.string.bt_kind_device);
    }

    private static boolean sameDevice(BluetoothDevice a, BluetoothDevice b) {
        if (a == null || b == null) return a == b;
        return a.getAddress().equals(b.getAddress());
    }

    private String str(int res) { return activity.getString(res); }
}
