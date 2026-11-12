package tv.glass.launcher;

/**
 * Builds the launcher's remote feedback sounds as 16-bit mono PCM.
 *
 * <p>The sounds are synthesized instead of shipped as audio files so they stay
 * tiny, license-free and easy to tune here. Each one is a short sine "pluck":
 * a few milliseconds of fade-in, an exponential decay, an optional pitch glide
 * and a soft second partial, with a final fade so the last sample is silent.
 * Pure Java with no Android dependencies, so it is covered by unit tests.
 */
final class UiSoundSynth {

    static final int SAMPLE_RATE = 44_100;

    /** One sound's shape. Frequencies in Hz, times in milliseconds, peak 0-1. */
    static final class Spec {
        final double startHz;
        final double endHz;
        final double partialRatio;
        final double partialLevel;
        final double attackMs;
        final double decayMs;
        final double lengthMs;
        final double peak;

        Spec(double startHz, double endHz, double partialRatio, double partialLevel,
             double attackMs, double decayMs, double lengthMs, double peak) {
            this.startHz = startHz;
            this.endHz = endHz;
            this.partialRatio = partialRatio;
            this.partialLevel = partialLevel;
            this.attackMs = attackMs;
            this.decayMs = decayMs;
            this.lengthMs = lengthMs;
            this.peak = peak;
        }
    }

    /** Focus moved: a very short, high, quiet tick. */
    static final Spec MOVE = new Spec(1650, 1500, 2.0, 0.12, 1.2, 6, 32, 0.20);
    /** OK pressed: a rounder, slightly longer "tock" with a fifth above. */
    static final Spec SELECT = new Spec(1040, 1000, 1.5, 0.30, 1.8, 24, 105, 0.28);
    /** Back pressed: a soft tone that falls in pitch. */
    static final Spec BACK = new Spec(880, 600, 1.5, 0.18, 1.8, 18, 85, 0.22);

    private static final double TAIL_FADE_MS = 6;

    private UiSoundSynth() {}

    static short[] render(Spec s) {
        int n = (int) Math.round(s.lengthMs * SAMPLE_RATE / 1000.0);
        short[] out = new short[n];
        double attack = Math.max(1, s.attackMs * SAMPLE_RATE / 1000.0);
        double decay = s.decayMs * SAMPLE_RATE / 1000.0;
        double tail = Math.min(n, TAIL_FADE_MS * SAMPLE_RATE / 1000.0);
        double norm = 1.0 / (1.0 + s.partialLevel);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = n > 1 ? i / (double) (n - 1) : 0;
            // Exponential glide keeps the pitch change even to the ear.
            double hz = s.startHz * Math.pow(s.endHz / s.startHz, t);
            phase += 2 * Math.PI * hz / SAMPLE_RATE;
            double env = Math.exp(-i / decay);
            if (i < attack) env *= Math.sin(0.5 * Math.PI * i / attack);
            int fromEnd = n - 1 - i;
            if (fromEnd < tail) env *= fromEnd / tail;
            double v = (Math.sin(phase) + s.partialLevel * Math.sin(phase * s.partialRatio)) * norm;
            out[i] = (short) Math.round(v * env * s.peak * Short.MAX_VALUE);
        }
        return out;
    }

    /** Wrap PCM in a minimal RIFF/WAVE header that SoundPool can load. */
    static byte[] wav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        ascii(b, 0, "RIFF");
        le32(b, 4, 36 + dataLen);
        ascii(b, 8, "WAVE");
        ascii(b, 12, "fmt ");
        le32(b, 16, 16);              // fmt chunk size
        le16(b, 20, 1);               // PCM
        le16(b, 22, 1);               // mono
        le32(b, 24, SAMPLE_RATE);
        le32(b, 28, SAMPLE_RATE * 2); // byte rate
        le16(b, 32, 2);               // block align
        le16(b, 34, 16);              // bits per sample
        ascii(b, 36, "data");
        le32(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) le16(b, 44 + i * 2, pcm[i]);
        return b;
    }

    private static void ascii(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] b, int at, int v) {
        le16(b, at, v);
        le16(b, at + 2, v >> 16);
    }
}
