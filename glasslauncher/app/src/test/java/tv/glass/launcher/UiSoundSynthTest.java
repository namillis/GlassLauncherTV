package tv.glass.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UiSoundSynthTest {

    private static final UiSoundSynth.Spec[] ALL = {
            UiSoundSynth.MOVE, UiSoundSynth.SELECT, UiSoundSynth.BACK};

    @Test public void render_lengthMatchesSpec() {
        for (UiSoundSynth.Spec s : ALL) {
            int expected = (int) Math.round(s.lengthMs * UiSoundSynth.SAMPLE_RATE / 1000.0);
            assertEquals(expected, UiSoundSynth.render(s).length);
        }
    }

    @Test public void render_startsAndEndsSilentSoThereIsNoPop() {
        for (UiSoundSynth.Spec s : ALL) {
            short[] pcm = UiSoundSynth.render(s);
            assertEquals(0, pcm[0]);
            assertEquals(0, pcm[pcm.length - 1]);
        }
    }

    @Test public void render_staysUnderItsPeakLevel() {
        for (UiSoundSynth.Spec s : ALL) {
            int limit = (int) Math.ceil(s.peak * Short.MAX_VALUE);
            int max = 0;
            for (short v : UiSoundSynth.render(s)) max = Math.max(max, Math.abs(v));
            assertTrue("peak " + max + " over " + limit, max <= limit);
            assertTrue("sound is silent", max > limit / 4);
        }
    }

    @Test public void moveTick_isQuieterAndShorterThanSelect() {
        assertTrue(UiSoundSynth.MOVE.peak < UiSoundSynth.SELECT.peak);
        assertTrue(UiSoundSynth.MOVE.lengthMs < UiSoundSynth.SELECT.lengthMs);
    }

    @Test public void wav_hasPcmMonoHeader() {
        short[] pcm = {1, -2, 3};
        byte[] wav = UiSoundSynth.wav(pcm);
        assertEquals(44 + 6, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4));
        assertEquals("WAVE", new String(wav, 8, 4));
        assertEquals("data", new String(wav, 36, 4));
        assertEquals(1, le16(wav, 20));                       // PCM
        assertEquals(1, le16(wav, 22));                       // mono
        assertEquals(UiSoundSynth.SAMPLE_RATE, le32(wav, 24));
        assertEquals(16, le16(wav, 34));
        assertEquals(6, le32(wav, 40));
        assertEquals(-2, (short) le16(wav, 46));
    }

    private static int le16(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int at) {
        return le16(b, at) | (le16(b, at + 2) << 16);
    }
}
