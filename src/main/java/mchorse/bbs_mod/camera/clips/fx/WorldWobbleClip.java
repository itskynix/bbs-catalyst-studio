package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Cry of Fear style world wobble: the surroundings ripple and bend with how far away they are, while
 * the sky stays put. The pass reads the frame's depth, so a wall right next to the camera and a far
 * corridor wave out of step with each other.
 *
 * <p>The ripple runs on the clip's own time, not the wall clock, so a scrub or an export wobbles the
 * same frame the same way every time.</p>
 */
public class WorldWobbleClip extends FxClip
{
    public static final int WAVE = 0;
    public static final int TWIRL = 1;

    /** In twirl mode the amplitude is a turn in radians; this brings it onto the same scale as the wave's frame fractions. */
    private static final double TWIRL_GAIN = 25D;

    public final ValueInt mode = new ValueInt("mode", WAVE, WAVE, TWIRL);
    /** Where the twirl turns around, in frame fractions */
    public final ValueFloat centerX = new ValueFloat("center_x", 0.5F, 0F, 1F);
    public final ValueFloat centerY = new ValueFloat("center_y", 0.5F, 0F, 1F);

    /** Ripples across the frame height */
    public final Channel frequency = this.channel("ripples", 4D);
    /** Peak displacement as a fraction of the frame height */
    public final Channel amplitude = this.channel("amplitude", 0.02D);
    /** Ripple speed in cycles per second */
    public final Channel speed = this.channel("speed", 1.2D);
    /** Distance in blocks up to which the bend is at full strength; past it the bend fades out, and the sky stays still */
    public final Channel depthCutoff = this.channel("depth_cutoff", 32D);

    public WorldWobbleClip()
    {
        super();

        this.add(this.mode);
        this.add(this.centerX);
        this.add(this.centerY);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        out.add(new FxOverlay(FxOverlay.WORLD_WOBBLE)
            .set(0, this.frequency.get(t))
            .set(1, this.amplitude.get(t) * factor * (this.mode.get() == TWIRL ? TWIRL_GAIN : 1D))
            .set(2, this.speed.get(t))
            .set(3, Math.max(this.depthCutoff.get(t), 0.1D))
            .set(4, t / 20D)
            .set(8, this.mode.get())
            .set(9, this.centerX.get())
            .set(10, this.centerY.get()));
    }

    @Override
    protected Clip create()
    {
        return new WorldWobbleClip();
    }
}
