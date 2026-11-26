package mchorse.bbs_mod.recap;

import java.util.Locale;

/** Game ticks written as time, twenty to the second. */
public final class RecapTime
{
    private RecapTime()
    {}

    /** {@code 00:00}, with hours in front past an hour. */
    public static String format(int ticks)
    {
        int seconds = Math.max(0, ticks) / 20;
        int hours = seconds / 3600;
        int minutes = (seconds / 60) % 60;
        int rest = seconds % 60;

        return hours > 0
            ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
            : String.format(Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    /** {@code 00:00.05}: the time and the tick within the second, for the editor's cursor. */
    public static String formatWithTicks(int ticks)
    {
        return format(ticks) + String.format(Locale.ROOT, ".%02d", Math.max(0, ticks) % 20);
    }
}
