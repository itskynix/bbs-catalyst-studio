package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.Arrays;
import java.util.List;

/**
 * Compiled piecewise time map for a recap timeline's {@link RecapClips.SpeedClip} and
 * {@link RecapClips.FreezeClip} tools.
 *
 * <p>Instead of summing tick rates from tick {@code 0} on every query, clip boundaries and fade
 * ramps are compiled once per timeline edit into sorted knot intervals with closed-form integrals.
 * Evaluating {@link #sourceTime(float)} or {@link #rateAt(float)} at any {@code ticks + transition}
 * then takes {@code O(log K)} binary search and gives identical results forward, backward and
 * across every export frame rate.</p>
 */
public final class RecapTimeMap
{
    public static final RecapTimeMap IDENTITY = new RecapTimeMap(new int[0], new double[0], new float[0], new float[0], 0L, false);

    private final int[] knots;
    private final double[] sourceAt;
    private final float[] startRate;
    private final float[] endRate;
    private final long stamp;
    private final boolean active;

    private RecapTimeMap(int[] knots, double[] sourceAt, float[] startRate, float[] endRate, long stamp, boolean active)
    {
        this.knots = knots;
        this.sourceAt = sourceAt;
        this.startRate = startRate;
        this.endRate = endRate;
        this.stamp = stamp;
        this.active = active;
    }

    /** Fingerprint of all enabled time clips on {@code clips}, zero when none are active. */
    public static long stampOf(Clips clips)
    {
        if (clips == null)
        {
            return 0L;
        }

        long hash = 1469598103934665603L;
        boolean any = false;

        for (Clip clip : clips.get())
        {
            if (!clip.enabled.get())
            {
                continue;
            }

            if (clip instanceof RecapClips.SpeedClip speed)
            {
                any = true;
                hash = mix(hash, 1L);
                hash = mix(hash, clip.tick.get());
                hash = mix(hash, clip.duration.get());
                hash = mix(hash, clip.layer.get());
                hash = mix(hash, Float.floatToRawIntBits(speed.speed.get()));
                hash = mix(hash, speed.fade.get());
                hash = mix(hash, speed.keysStamp());
            }
            else if (clip instanceof RecapClips.FreezeClip freeze)
            {
                any = true;
                hash = mix(hash, 2L);
                hash = mix(hash, clip.tick.get());
                hash = mix(hash, clip.duration.get());
                hash = mix(hash, clip.layer.get());
                hash = mix(hash, freeze.fade.get());
            }
        }

        return any ? (hash == 0L ? 1L : hash) : 0L;
    }

    private static long mix(long hash, long value)
    {
        hash ^= value;
        hash *= 1099511628211L;

        return hash ^ (hash >>> 32);
    }

    public boolean matches(long currentStamp)
    {
        return this.stamp == currentStamp;
    }

    public boolean isActive()
    {
        return this.active;
    }

    /** Compiles the time clips on {@code clips} into a fast lookup map. */
    public static RecapTimeMap compile(Clips clips)
    {
        long stamp = stampOf(clips);

        if (stamp == 0L || clips == null)
        {
            return IDENTITY;
        }

        List<Clip> list = clips.get();
        int keyed = 0;

        /* A keyed speed changes every tick: a knot at each of them */
        for (Clip clip : list)
        {
            if (clip.enabled.get() && clip instanceof RecapClips.SpeedClip speed && !speed.speedKeys.isEmpty())
            {
                keyed += Math.max(0, clip.duration.get());
            }
        }

        int[] raw = new int[list.size() * 4 + 2 + keyed];
        int count = 0;

        raw[count++] = 0;

        for (Clip clip : list)
        {
            if (!clip.enabled.get())
            {
                continue;
            }

            if (!(clip instanceof RecapClips.SpeedClip) && !(clip instanceof RecapClips.FreezeClip))
            {
                continue;
            }

            int start = Math.max(0, clip.tick.get());
            int duration = Math.max(0, clip.duration.get());
            int end = start + duration;

            if (end <= start)
            {
                continue;
            }

            int fade = clip instanceof RecapClips.SpeedClip s ? s.fade.get() : ((RecapClips.FreezeClip) clip).fade.get();
            int ramp = Math.max(0, Math.min(fade, duration / 2));

            raw[count++] = start;
            raw[count++] = end;

            if (clip instanceof RecapClips.SpeedClip s && !s.speedKeys.isEmpty())
            {
                for (int t = start + 1; t < end; t++)
                {
                    raw[count++] = t;
                }
            }

            if (ramp > 0 && !(clip instanceof RecapClips.SpeedClip k && !k.speedKeys.isEmpty()))
            {
                raw[count++] = start + ramp;
                raw[count++] = end - ramp;
            }
        }

        if (count <= 1)
        {
            return new RecapTimeMap(new int[] {0}, new double[] {0D}, new float[] {1F}, new float[] {1F}, stamp, false);
        }

        Arrays.sort(raw, 0, count);

        int unique = 0;

        for (int i = 0; i < count; i++)
        {
            if (i == 0 || raw[i] != raw[i - 1])
            {
                raw[unique++] = raw[i];
            }
        }

        int[] knots = Arrays.copyOf(raw, unique);
        double[] sourceAt = new double[unique];
        float[] startRate = new float[unique];
        float[] endRate = new float[unique];
        double total = 0D;

        for (int i = 0; i < unique; i++)
        {
            sourceAt[i] = total;

            int a = knots[i];
            int b = i + 1 < unique ? knots[i + 1] : a + 1;

            if (b <= a)
            {
                startRate[i] = 1F;
                endRate[i] = 1F;

                continue;
            }

            /* Evaluate near the interval bounds to capture smooth fade transitions accurately */
            float r0 = evalRate(list, a + 1e-4F);
            float r1 = i + 1 < unique ? evalRate(list, b - 1e-4F) : r0;

            startRate[i] = r0;
            endRate[i] = r1;

            if (i + 1 < unique)
            {
                total += integrateInterval(a, b, r0, r1);
            }
        }

        return new RecapTimeMap(knots, sourceAt, startRate, endRate, stamp, true);
    }

    /**
     * Exact integral of the smoothstep transition from {@code r0} to {@code r1} across {@code [a, b]}.
     * Since the symmetric smoothstep {@code 3u^2 - 2u^3} integrates to {@code 0.5} on {@code [0, 1]},
     * the full interval integral equals {@code (b - a) * (r0 + r1) * 0.5}, and when {@code r0 == r1}
     * it reduces to {@code (b - a) * r0} with zero drift.
     */
    private static double integrateInterval(int a, int b, float r0, float r1)
    {
        int span = b - a;

        if (Math.abs(r1 - r0) < 1e-6F)
        {
            return (double) span * r0;
        }

        return span * ((double) r0 + (double) r1) * 0.5D;
    }

    /** Evaluates the instantaneous playback rate at timeline {@code time}. */
    private static float evalRate(List<Clip> clips, float time)
    {
        float baseRate = 1F;
        int speedLayer = Integer.MIN_VALUE;
        float freezeFactor = 1F;

        for (int i = 0, n = clips.size(); i < n; i++)
        {
            Clip clip = clips.get(i);

            if (!clip.enabled.get())
            {
                continue;
            }

            int start = clip.tick.get();
            int duration = clip.duration.get();
            int end = start + duration;

            if (duration <= 0 || time < start || time >= end)
            {
                continue;
            }

            if (clip instanceof RecapClips.FreezeClip freeze)
            {
                float weight = envelope(time, start, end, freeze.fade.get());

                freezeFactor = Math.min(freezeFactor, 1F - weight);
            }
            else if (clip instanceof RecapClips.SpeedClip speed && clip.layer.get() >= speedLayer)
            {
                float target = speed.speedAt(time - start);
                float weight = envelope(time, start, end, speed.fade.get());

                speedLayer = clip.layer.get();
                baseRate = 1F + (target - 1F) * weight;
            }
        }

        return Math.max(0F, baseRate * freezeFactor);
    }

    private static float envelope(float time, int start, int end, int fadeTicks)
    {
        int ramp = Math.max(0, Math.min(fadeTicks, (end - start) / 2));

        if (ramp <= 0)
        {
            return 1F;
        }

        if (time < start + ramp)
        {
            float u = Math.max(0F, Math.min(1F, (time - start) / (float) ramp));

            return u * u * (3F - 2F * u);
        }

        if (time > end - ramp)
        {
            float u = Math.max(0F, Math.min(1F, (end - time) / (float) ramp));

            return u * u * (3F - 2F * u);
        }

        return 1F;
    }

    /** Instantaneous rate at timeline {@code time}. */
    public float rateAt(float time)
    {
        if (!this.active || this.knots.length == 0 || time < 0F)
        {
            return 1F;
        }

        int idx = this.intervalOf(time);
        float r0 = this.startRate[idx];
        float r1 = this.endRate[idx];

        if (idx + 1 >= this.knots.length || Math.abs(r1 - r0) < 1e-6F)
        {
            return r0;
        }

        int a = this.knots[idx];
        int b = this.knots[idx + 1];
        float u = Math.max(0F, Math.min(1F, (time - a) / (float) (b - a)));

        return r0 + (r1 - r0) * (u * u * (3F - 2F * u));
    }

    /** Source recording tick at timeline {@code time} ({@code cursor + transition}). */
    public float sourceTime(float time)
    {
        if (time <= 0F)
        {
            return 0F;
        }

        if (!this.active || this.knots.length == 0)
        {
            return time;
        }

        int idx = this.intervalOf(time);
        int a = this.knots[idx];
        double base = this.sourceAt[idx];
        float r0 = this.startRate[idx];
        float r1 = this.endRate[idx];
        double dt = time - a;

        if (idx + 1 >= this.knots.length || Math.abs(r1 - r0) < 1e-6F)
        {
            return (float) (base + dt * r0);
        }

        int span = this.knots[idx + 1] - a;
        double u = Math.max(0D, Math.min(1D, dt / span));
        /* Closed-form antiderivative of r0 + (r1 - r0) * (3u^2 - 2u^3) over [0, u] */
        double integral = span * (r0 * u + (r1 - r0) * (u * u * u - 0.5D * u * u * u * u));

        return (float) (base + integral);
    }

    private int intervalOf(float time)
    {
        int low = 0;
        int high = this.knots.length - 1;

        while (low < high)
        {
            int mid = (low + high + 1) >>> 1;

            if (this.knots[mid] <= time)
            {
                low = mid;
            }
            else
            {
                high = mid - 1;
            }
        }

        return low;
    }
}
