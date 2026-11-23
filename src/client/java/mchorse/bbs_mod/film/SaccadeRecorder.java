package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.fx.EyeClip;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches the view of a take for sudden turns, the jumps of a real eye, and writes each one as a
 * saccadic pulse onto the film's {@code bbs:eye} clips when the take ends.
 *
 * <p>Detection happens once per recorded tick, on the take's own tick count and nothing else: the
 * shortest turn between the last tick's angles and this tick's, over a threshold and past the
 * cooldown, is a saccade. What is written are keyframes on the clip's saccade channel, so playback,
 * scrubbing and video export read the very same timeline and nothing is triggered a second time.</p>
 */
public class SaccadeRecorder
{
    private boolean has;
    private float yaw;
    private float pitch;
    private float roll;
    private int lastTick = Integer.MIN_VALUE;
    private int lastSaccade = Integer.MIN_VALUE / 2;

    /** The ticks of the pulses detected, in the film's time. */
    private final List<Integer> ticks = new ArrayList<>();

    public void clear()
    {
        this.ticks.clear();
        this.has = false;
        this.lastTick = Integer.MIN_VALUE;
        this.lastSaccade = Integer.MIN_VALUE / 2;
    }

    /** Fed by the editor's own take: the controlled actor's view, on the cursor tick. */
    public boolean isEmpty()
    {
        return this.ticks.isEmpty();
    }

    public void record(int tick, float yaw, float pitch, float roll)
    {
        if (!BBSSettings.autoSaccadicMask.get())
        {
            this.has = false;

            return;
        }

        if (tick <= this.lastTick)
        {
            return;
        }

        this.lastTick = tick;

        if (this.has)
        {
            double dy = MathHelper.wrapDegrees(yaw - this.yaw);
            double dp = MathHelper.wrapDegrees(pitch - this.pitch);
            double dr = MathHelper.wrapDegrees(roll - this.roll);
            double jump = Math.sqrt(dy * dy + dp * dp + dr * dr);

            if (jump >= BBSSettings.saccadicAngleThreshold.get() && tick - this.lastSaccade >= Math.max(1, BBSSettings.saccadicCooldown.get()))
            {
                this.ticks.add(tick);

                this.lastSaccade = tick;
            }
        }

        this.has = true;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    /**
     * Writes the pulses onto the film. Each goes on an eye clip that covers its tick; ticks no eye
     * clip covers share one new clip, set so that it is fully open at rest.
     */
    public void commitTo(Film film)
    {
        if (this.ticks.isEmpty())
        {
            return;
        }

        List<Integer> uncovered = new ArrayList<>();

        for (int tick : this.ticks)
        {
            EyeClip clip = this.cover(film, tick);

            if (clip == null)
            {
                uncovered.add(tick);
            }
            else
            {
                write(clip, tick);
            }
        }

        if (!uncovered.isEmpty())
        {
            EyeClip clip = new EyeClip();
            int first = uncovered.get(0);
            int last = uncovered.get(uncovered.size() - 1);

            clip.tick.set(Math.max(0, first - 2));
            clip.duration.set(last + clip.saccadeDuration.get() + 3 - clip.tick.get());
            clip.title.set("Saccade");

            /* Wide open at rest, so the cover only shows while a pulse pulls the lids in */
            clip.size.channel.insert(0, 1.6D);
            clip.layer.set(film.camera.findFreeLayer(clip));

            for (int tick : uncovered)
            {
                write(clip, tick);
            }

            film.camera.addClip(clip);
        }

        this.ticks.clear();
    }

    private EyeClip cover(Film film, int tick)
    {
        for (Clip clip : film.camera.get())
        {
            if (clip instanceof EyeClip eye && eye.isInside(tick))
            {
                return eye;
            }
        }

        return null;
    }

    /** Rest just before, peak on the tick, rest again when the decay is over. */
    private static void write(EyeClip clip, int tick)
    {
        float local = tick - clip.tick.get();
        float end = local + clip.saccadeDuration.get();
        var channel = clip.saccade.channel;

        if (channel.isEmpty() || channel.interpolate(Math.max(0F, local - 1F)) < 0.001D)
        {
            line(channel.get(channel.insert(Math.max(0F, local - 1F), 0D)));
        }

        line(channel.get(channel.insert(local, 1D)));
        line(channel.get(channel.insert(end, 0D)));
    }

    private static void line(Keyframe<Double> key)
    {
        key.getInterpolation().setInterp(Interpolations.LINEAR);
    }
}
