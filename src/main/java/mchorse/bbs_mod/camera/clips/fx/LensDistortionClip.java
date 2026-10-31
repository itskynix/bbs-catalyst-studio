package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Lens distortion: the frame bulges outward (barrel, positive) or pinches inward (pincushion,
 * negative), with an optional colour split that grows toward the edges.
 */
public class LensDistortionClip extends FxClip
{
    public final Channel distortion = this.channel("distortion", 0.3D);
    public final Channel chromaticAberration = this.channel("chromatic_aberration", 0D);
    public final Channel scale = this.channel("scale", 1D);

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        out.add(new FxOverlay(FxOverlay.LENS_DISTORTION)
            .set(0, this.distortion.get(t) * factor)
            .set(1, this.chromaticAberration.get(t) * factor)
            .set(2, this.scale.get(t)));
    }

    @Override
    protected Clip create()
    {
        return new LensDistortionClip();
    }
}
