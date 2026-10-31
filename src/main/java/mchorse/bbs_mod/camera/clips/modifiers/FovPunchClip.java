package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.ClipContext;

/**
 * A single kick of the field of view, for beats and hits: at the first tick of the clip the FOV is
 * thrown off its value by {@link #intensity} degrees and then settles back by itself over
 * {@link #punchDuration} ticks, along the decay curve picked. A negative intensity narrows the
 * view (a zoom-in punch), a positive one widens it (a zoom-out).
 *
 * <p>It works on top of whatever the clips under it made of the camera, so no curve has to be drawn:
 * drop the clip on the beat and set the numbers.</p>
 */
public class FovPunchClip extends CameraClip
{
    /** Fast at the start and gentle at the end, never crossing the base FOV. */
    public static final int DECAY_EXPONENTIAL = 0;
    /** Overshoots the base FOV and wobbles around it, losing strength as it settles. */
    public static final int DECAY_ELASTIC = 1;

    public static final int DECAYS = 2;

    public final ValueFloat intensity = new ValueFloat("punch_intensity", 0F, -30F, 30F);
    public final ValueFloat punchDuration = new ValueFloat("punch_duration", 8F, 1F, 200F);
    public final ValueInt decay = new ValueInt("punch_decay", DECAY_EXPONENTIAL, 0, DECAYS - 1);

    public FovPunchClip()
    {
        super();

        this.add(this.intensity);
        this.add(this.punchDuration);
        this.add(this.decay);
    }

    /**
     * How much of the punch is left {@code progress} of the way through it: 1 at the start,
     * 0 at the end (progress 0..1).
     */
    public static float shape(int decay, float progress)
    {
        if (progress <= 0F)
        {
            return 1F;
        }
        else if (progress >= 1F)
        {
            return 0F;
        }

        if (decay == DECAY_ELASTIC)
        {
            float left = 1F - progress;

            return left * left * (float) Math.cos(progress * 3F * Math.PI);
        }

        /* Exponential out, shifted so it lands exactly on zero at the end */
        float k = 6F;
        float end = (float) Math.exp(-k);

        return ((float) Math.exp(-k * progress) - end) / (1F - end);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        float tick = context.relativeTick + context.transition;
        float length = Math.max(this.punchDuration.get(), 0.001F);
        float envelope = this.envelope.factorEnabled(this.duration.get(), tick);

        if (tick < 0F || tick >= length || envelope <= 0F)
        {
            return;
        }

        float punch = this.intensity.get() * envelope * shape(this.decay.get(), tick / length);

        position.angle.fov = Math.max(1F, Math.min(179F, position.angle.fov + punch));
    }

    @Override
    protected CameraClip create()
    {
        return new FovPunchClip();
    }
}
