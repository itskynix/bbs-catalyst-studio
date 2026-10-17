package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/** Film grain: animated noise laid over the frame. */
public class GrainClip extends FxClip
{
    public final Channel strength = this.channel("strength", 0.35D);
    public final Channel size = this.channel("size", 1D);

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        out.add(new FxOverlay(FxOverlay.GRAIN)
            .set(0, this.strength.get(t) * factor)
            .set(1, this.size.get(t))
            .set(2, t / 20D));
    }

    @Override
    protected Clip create()
    {
        return new GrainClip();
    }
}
