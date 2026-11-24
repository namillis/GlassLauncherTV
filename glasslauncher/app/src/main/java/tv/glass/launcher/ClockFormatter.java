package tv.glass.launcher;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Encapsulates the home-screen clock's text formatting.
 *
 * <p>Visual brief: system-aware wall clock — 12-hour with a small "AM/PM"
 * suffix rendered at ~42% size in {@code sans-serif-thin} (modern
 * lock-screen style) when the device is in 12-hour mode; plain "HH:MM"
 * with no suffix when the device is in 24-hour mode.  The caller obtains
 * the current preference via
 * {@link android.text.format.DateFormat#is24HourFormat(android.content.Context)}
 * and passes it as {@code use24h} to {@link #format} and
 * {@link #shouldRepaint}.  No state about the preference is kept here —
 * the caller owns that decision so the formatter can be unit-tested
 * without a Context.
 *
 * <h3>Allocation discipline</h3>
 * The launcher's clock fires once per minute (not per second — see
 * {@code CLOCK_MS} in {@code LauncherActivity}). Even at that rate, the
 * naïve "build a fresh String + a fresh SpannableStringBuilder per tick"
 * pattern produces measurable GC pressure across a multi-day uptime. This
 * class amortises every reusable allocation:
 *
 * <ul>
 *   <li>One {@link Calendar} re-used across ticks (set the time-in-millis
 *       per call, never construct a new instance).</li>
 *   <li>One {@code char[8]} backing the digit composition.</li>
 *   <li>One {@link SpannableStringBuilder} cleared and re-filled per tick.</li>
 *   <li>Pre-built spans ({@link RelativeSizeSpan}, {@link InterFonts.Span},
 *       {@link ForegroundColorSpan}) re-applied per tick.</li>
 * </ul>
 *
 * <p>The only per-tick allocation is the tiny {@code String} produced by
 * {@link String#valueOf(char[], int, int)} when appending to the
 * {@link SpannableStringBuilder}. {@code SpannableStringBuilder} only
 * accepts {@link CharSequence} on append, so the only fully zero-alloc
 * path would be a wrapper class implementing {@link CharSequence} around
 * the digit buffer — not worth the complexity at one tick per minute.
 *
 * <p>This class is package-private and final because no consumer outside
 * the launcher needs it. Pulling it out drops ~50 lines from
 * {@code LauncherActivity} and makes the formatting logic independently
 * understandable (and trivially mockable for a future test pass).
 */
final class ClockFormatter {

    private final Calendar               cal   = Calendar.getInstance();
    private final char[]                 chars = new char[8];
    private final SpannableStringBuilder ssb   = new SpannableStringBuilder();
    private final RelativeSizeSpan       amPmSize  = new RelativeSizeSpan(0.42f);
    // Regular, not thin: at 0.42x the thin face's strokes never reached the
    // time's white on screen (about 3:1 on winter, review S3). Same white.
    // Inter Regular through a custom span (TypefaceSpan(Typeface) needs API
    // 28); made on first use, after the activity loaded the fonts.
    private InterFonts.Span              amPmFace;
    // Spans for the small, dim "day, date month" line rendered below the
    // time when showDate is on. Pre-built and re-applied per tick.
    private final RelativeSizeSpan       dateSize  = new RelativeSizeSpan(0.40f);
    private InterFonts.Span              dateFace;
    // White, as the approved H1B big clock draws its date.
    private final ForegroundColorSpan    dateColor = new ForegroundColorSpan(0xFFFFFFFF);

    private java.text.SimpleDateFormat dateFormat;
    private Locale dateLocale;
    private TimeZone dateZone;

    /** The long-date formatter for the current locale and this clock's zone. */
    private java.text.SimpleDateFormat longDate() {
        Locale locale = Locale.getDefault();
        TimeZone zone = cal.getTimeZone();
        if (dateFormat == null || !locale.equals(dateLocale) || !zone.equals(dateZone)) {
            String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd");
            dateFormat = new java.text.SimpleDateFormat(pattern, locale);
            dateFormat.setTimeZone(zone);
            dateLocale = locale;
            dateZone = zone;
        }
        return dateFormat;
    }

    /** -1 sentinel = "no minute paint yet". {@link #shouldRepaint} returns
     *  {@code true} on the first call after construction or a reset. */
    private int lastShownMinute = -1;

    /** Last day-of-year shown. Tracked so a midnight rollover with the
     *  {@code showDate} prefix on triggers a repaint even though the
     *  minute number cycles back to 0. */
    private int lastShownDayOfYear = -1;

    /** Whether the most recent {@link #format} call was rendered with
     *  the date prefix. A toggle of the user's "Show clock" preference
     *  flips this — {@link #shouldRepaint} compares the requested mode
     *  to the rendered mode so the next paint runs unconditionally. */
    private boolean lastShownWithDate = false;

    /** Whether the most recent {@link #format} call was rendered in
     *  24-hour mode. Tracked so a system time-format change (e.g. user
     *  goes to Settings → Date & time → Use 24-hour format and toggles
     *  it) triggers a repaint on the next tick even when the minute has
     *  not advanced. */
    private boolean lastShownAs24h = false;

    /** Whether 12-hour mode adds the AM/PM suffix. The tvOS-size clock
     *  bubble (H1B) shows "4:20" with no suffix, as tvOS does. */
    private boolean showAmPm = true;

    /** Turn the 12-hour AM/PM suffix on or off; a change forces a repaint. */
    void setShowAmPm(boolean show) {
        if (show != showAmPm) {
            showAmPm = show;
            reset();
        }
    }

    /**
     * Whether {@link #format(long, boolean, boolean)} would produce a
     * visibly different string than the one currently shown.
     *
     * <p>Four repaint triggers:
     * <ul>
     *   <li>The minute number changed since the last paint.</li>
     *   <li>The day-of-year rolled over.</li>
     *   <li>The {@code showDate} mode flipped.</li>
     *   <li>The {@code use24h} preference flipped (system setting changed).</li>
     * </ul>
     */
    boolean shouldRepaint(long ms, boolean showDate, boolean use24h) {
        cal.setTimeInMillis(ms);
        if (showDate != lastShownWithDate) return true;
        if (use24h   != lastShownAs24h)   return true;
        if (cal.get(Calendar.MINUTE)      != lastShownMinute)    return true;
        return cal.get(Calendar.DAY_OF_YEAR) != lastShownDayOfYear;
    }

    /**
     * Reset the "last shown" sentinels so the next call to
     * {@link #shouldRepaint(long, boolean, boolean)} returns {@code true}
     * unconditionally and {@link #format(long, boolean, boolean)} will
     * re-emit the spans even if nothing actually changed. Used after
     * configuration changes (RTL flip, font scale change, locale change)
     * so the next paint redraws against the new environment.
     */
    void reset() {
        lastShownMinute    = -1;
        lastShownDayOfYear = -1;
        // lastShownWithDate / lastShownAs24h intentionally left as-is.
        // shouldRepaint will still trigger a repaint because
        // lastShownMinute = -1 forces it.
    }

    /**
     * Update the timezone used to compute wall-clock values for all
     * subsequent {@link #shouldRepaint} / {@link #format} calls.
     *
     * <p>{@link #cal} captures a {@link TimeZone} reference once, at
     * construction ({@code Calendar.getInstance()}), and never re-queries
     * {@link TimeZone#getDefault()} on its own -- Calendar has no
     * auto-refresh behaviour. Without an explicit call to this method, a
     * real timezone change (a different Olson zone -- the device moved, or
     * a Google TV re-detected location) leaves the clock rendering in the
     * OLD zone indefinitely, even though {@link #reset} forces a repaint.
     * Routine DST transitions do NOT need this: they're computed
     * dynamically off the same {@link TimeZone} object based on the date,
     * so an existing Calendar already handles a spring-forward / fall-back
     * correctly with no help.
     *
     * <p>Callers should follow this with {@link #reset()} so the next
     * paint is unconditional and reflects the new zone immediately instead
     * of waiting for the minute to naturally roll over.
     *
     * @param tz new default timezone (typically {@code TimeZone.getDefault()}
     *           read fresh inside an {@code ACTION_TIMEZONE_CHANGED}
     *           handler). {@code null} is ignored -- {@link Calendar#setTimeZone}
     *           does not accept null and there's no sane fallback here.
     */
    void setTimeZone(TimeZone tz) {
        if (tz != null) cal.setTimeZone(tz);
    }

    /**
     * Build the visible clock {@link CharSequence} for the given absolute
     * time, optionally prefixed with the locale-aware short day-of-week.
     *
     * <p>When {@code use24h} is {@code false} (12-hour mode): renders
     * "h:mm AM" or "h:mm PM" with the AM/PM suffix at ~42% size in
     * {@code sans-serif-thin} — modern lock-screen style.
     *
     * <p>When {@code use24h} is {@code true} (24-hour mode): renders
     * "HH:MM" always two digits for the hour (00–23), no AM/PM suffix.
     *
     * <p>Optional date line under the time: the long weekday, month and day
     * when {@code showDate=true} ("Monday, November 23" in US English),
     * from the locale's best "EEEEMMMMd" pattern.
     *
     * <p>The returned object is the internally-pooled
     * {@link SpannableStringBuilder} — callers must not mutate it and
     * should pass {@link android.widget.TextView.BufferType#SPANNABLE}
     * so the platform copies the spans. Subsequent calls will reuse
     * the same builder and overwrite its contents.
     */
    CharSequence format(long ms, boolean showDate, boolean use24h) {
        cal.setTimeInMillis(ms);
        lastShownMinute    = cal.get(Calendar.MINUTE);
        lastShownDayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        lastShownWithDate  = showDate;
        lastShownAs24h     = use24h;

        int pos     = 0;
        int amStart = 0; // only used in 12-hour mode

        if (use24h) {
            // 24-hour: always two digits (00:00 – 23:59), no AM/PM.
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int min  = cal.get(Calendar.MINUTE);
            chars[pos++] = (char)('0' + hour / 10);
            chars[pos++] = (char)('0' + hour % 10);
            chars[pos++] = ':';
            chars[pos++] = (char)('0' + min / 10);
            chars[pos++] = (char)('0' + min % 10);
        } else {
            // 12-hour: leading zero suppressed (1:00 – 12:59), AM/PM suffix.
            int hour = cal.get(Calendar.HOUR);
            if (hour == 0) hour = 12;
            int min  = cal.get(Calendar.MINUTE);
            int ampm = cal.get(Calendar.AM_PM);
            if (hour >= 10) chars[pos++] = (char)('0' + hour / 10);
            chars[pos++] = (char)('0' + hour % 10);
            chars[pos++] = ':';
            chars[pos++] = (char)('0' + min / 10);
            chars[pos++] = (char)('0' + min % 10);
            if (showAmPm) {
                chars[pos++] = ' ';
                amStart = pos;
                chars[pos++] = ampm == Calendar.AM ? 'A' : 'P';
                chars[pos++] = 'M';
            }
        }

        ssb.clear();
        ssb.clearSpans();
        if (amPmFace == null) {
            amPmFace = new InterFonts.Span(InterFonts.regular());
            dateFace = new InterFonts.Span(InterFonts.regular());
        }

        // Big time line first.
        int timeStart = ssb.length();
        ssb.append(String.valueOf(chars, 0, pos));

        // Apply AM/PM spans only in 12-hour mode.
        if (!use24h && showAmPm) {
            ssb.setSpan(amPmSize,  timeStart + amStart, timeStart + pos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(amPmFace,  timeStart + amStart, timeStart + pos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Optional second line: the long weekday, month and day in the
        // device's locale, "Monday, November 23" in US English (H1B). The
        // formatter is rebuilt only when the locale or time zone changes;
        // the small String it returns once a minute is below GC pressure.
        if (showDate) {
            ssb.append('\n');
            int lineStart = ssb.length();
            ssb.append(longDate().format(cal.getTime()));
            int lineEnd = ssb.length();
            ssb.setSpan(dateSize,  lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(dateFace,  lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(dateColor, lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    /**
     * Compute the delay in ms from {@code now} until the next minute
     * boundary, plus a small 50 ms cushion so the tick lands just AFTER
     * {@code :00} rather than just before.
     */
    static long nextMinuteDelay(long now) {
        return 60_000L - (now % 60_000L) + 50L;
    }
}
