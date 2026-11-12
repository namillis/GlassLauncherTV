package tv.glass.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Plays the launcher's quiet remote feedback: a tick when the selection moves,
 * a click on OK and a softer falling tone on Back.
 *
 * <p>The sounds come from {@link UiSoundSynth}. They are written to the cache
 * directory and loaded into a {@link SoundPool} on a background thread, so
 * the first presses after a cold start may be silent while that finishes.
 * Playback is fire-and-forget on the sonification stream, which follows the
 * TV's system volume. Held D-pad repeats are throttled so a fast scroll
 * ticks steadily instead of buzzing.
 */
final class UiSounds {

    static final int MOVE = 0;
    static final int SELECT = 1;
    static final int BACK = 2;

    private static final long MOVE_MIN_GAP_MS = 60;
    private static final String[] NAMES = {"move", "select", "back"};
    private static final UiSoundSynth.Spec[] SPECS = {
            UiSoundSynth.MOVE, UiSoundSynth.SELECT, UiSoundSynth.BACK};

    private final SoundPool pool;
    private final int[] ids = {0, 0, 0};
    private final boolean[] ready = new boolean[3];
    private volatile boolean released;
    private boolean enabled;
    private long lastMoveAt;

    UiSounds(Context context, boolean enabled) {
        this.enabled = enabled;
        pool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            if (status != 0) return;
            synchronized (ready) {
                for (int i = 0; i < ids.length; i++) if (ids[i] == sampleId) ready[i] = true;
            }
        });
        File dir = new File(context.getCacheDir(), "ui-sounds");
        Thread loader = new Thread(() -> load(dir), "ui-sounds");
        loader.setPriority(Thread.MIN_PRIORITY);
        loader.start();
    }

    private void load(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        for (int i = 0; i < SPECS.length && !released; i++) {
            // Rewritten on every start so a tuning change in UiSoundSynth
            // always takes effect; each file is only a few kilobytes.
            File f = new File(dir, NAMES[i] + ".wav");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(UiSoundSynth.wav(UiSoundSynth.render(SPECS[i])));
            } catch (Exception e) {
                continue;
            }
            synchronized (ready) {
                if (released) return;
                ids[i] = pool.load(f.getAbsolutePath(), 1);
            }
        }
    }

    boolean isEnabled() { return enabled; }

    void setEnabled(boolean on) { enabled = on; }

    void play(int which) {
        if (!enabled || released) return;
        if (which == MOVE) {
            long now = SystemClock.uptimeMillis();
            if (now - lastMoveAt < MOVE_MIN_GAP_MS) return;
            lastMoveAt = now;
        }
        int id;
        synchronized (ready) {
            if (!ready[which]) return;
            id = ids[which];
        }
        pool.play(id, 1f, 1f, 1, 0, 1f);
    }

    void release() {
        synchronized (ready) {
            released = true;
            pool.release();
        }
    }
}
