package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/** A gradient from the edges of the frame toward its centre, in a colour of its own. */
public class VignetteClip extends FxClip
{
    public final ValueInt color = new ValueInt("color", 0x000000);

    public final Channel intensity = this.channel("intensity", 0.6D);
    public final Channel radius = this.channel("radius", 0.75D);
    public final Channel softness = this.channel("softness", 0.5D);

    public VignetteClip()
    {
        super();

        this.add(this.color);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        out.add(new FxOverlay(FxOverlay.VIGNETTE)
            .rgb(0, this.color.get())
            .set(3, this.intensity.get(t) * factor)
            .set(4, this.radius.get(t))
            .set(5, this.softness.get(t)));
    }

    @Override
    protected Clip create()
    {
        return new VignetteClip();
    }
}
