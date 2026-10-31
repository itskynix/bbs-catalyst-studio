package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Blurs the frame along the way the camera is turning. How far is not stated here: the renderer
 * measures how much the camera turned since the previous frame and stretches that by the numbers
 * below, so a whip pan smears hard and a camera at rest, or turning slowly, is left alone apart
 * from the base blur.
 */
public class MotionBlurClip extends FxClip
{
    /** How much of a frame's turn the shutter stays open for: 0.5 is the usual 180 degree shutter. */
    public final ValueFloat shutter = new ValueFloat("shutter", 0.5F, 0F, 2F);
    /** How much the camera's turning counts for, on top of the shutter. */
    public final ValueFloat rotationalMultiplier = new ValueFloat("rotational_multiplier", 1F, 0F, 5F);
    /** A horizontal blur that is always there, as a fraction of the frame's width. */
    public final ValueFloat baseBlur = new ValueFloat("base_blur", 0F, 0F, 0.05F);
    /** How much harder a snap blurs: 1 leaves whip pans as they are, 3 triples the smear at full snap. */
    public final ValueFloat whipPanMultiplier = new ValueFloat("whip_pan_multiplier", 1F, 1F, 5F);
    /** Turning speed, in degrees per second, from which a pan counts as a whip pan / snap. */
    public final ValueFloat snapThreshold = new ValueFloat("snap_threshold", 180F, 10F, 2000F);

    public final Channel intensity = this.channel("intensity", 1D);

    public MotionBlurClip()
    {
        super();

        this.add(this.shutter);
        this.add(this.rotationalMultiplier);
        this.add(this.baseBlur);
        this.add(this.whipPanMultiplier);
        this.add(this.snapThreshold);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        double scale = this.intensity.get(t) * factor;

        if (scale <= 0.001D)
        {
            return;
        }

        /* Parameters 4 and 5 are left for the renderer, which knows how the camera is turning */
        out.add(new FxOverlay(FxOverlay.MOTION_BLUR)
            .set(0, this.shutter.get() * scale)
            .set(1, this.rotationalMultiplier.get())
            .set(2, this.baseBlur.get() * scale)
            .set(6, this.whipPanMultiplier.get())
            .set(7, this.snapThreshold.get()));
    }

    @Override
    protected Clip create()
    {
        return new MotionBlurClip();
    }
}
