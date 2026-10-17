package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * A bag of film and lens effects, each driven by its own keyframe channel: a channel at zero (or
 * without keyframes) leaves its effect off, so only what was keyframed costs anything to draw.
 */
public class CinematicEffectsClip extends FxClip
{
    public final Channel vhs = this.channel("vhs", 0D);
    public final Channel vintage = this.channel("vintage", 0D);
    public final Channel radialBlur = this.channel("radial_blur", 0D);
    public final Channel lensDirt = this.channel("lens_dirt", 0D);
    public final Channel dust = this.channel("dust", 0D);
    public final Channel lightLeak = this.channel("light_leak", 0D);

    public final Channel chromaIntensity = this.channel("chroma_intensity", 0D);
    public final Channel chromaAngle = this.channel("chroma_angle", 0D);
    public final Channel chromaDirectional = this.channel("chroma_directional", 0D);
    public final Channel chromaRadius = this.channel("chroma_radius", 0.5D);
    public final Channel chromaHardness = this.channel("chroma_hardness", 0.5D);
    public final Channel chromaBalance = this.channel("chroma_balance", 0D);
    public final Channel chromaCenterX = this.channel("chroma_center_x", 0.5D);
    public final Channel chromaCenterY = this.channel("chroma_center_y", 0.5D);
    public final Channel chromaGreenShift = this.channel("chroma_green_shift", 0D);
    public final Channel chromaSpectrum = this.channel("chroma_spectrum", 0D);

    public final Channel fishIntensity = this.channel("fish_intensity", 0D);
    public final Channel fishDistance = this.channel("fish_distance", 1D);
    public final Channel fishRadius = this.channel("fish_radius", 1D);
    public final Channel fishHardness = this.channel("fish_hardness", 0.5D);
    public final Channel fishSharpening = this.channel("fish_sharpening", 0D);

    public final Channel heatStrength = this.channel("heat_strength", 0D);
    public final Channel heatSpeed = this.channel("heat_speed", 1D);
    public final Channel heatScale = this.channel("heat_scale", 1D);

    public CinematicEffectsClip()
    {
        super();

        /* Starts on the most useful effect, so a fresh clip is visibly doing something */
        this.chromaIntensity.channel.insert(0, 0.4D);
    }

    private void simple(List<FxOverlay> out, int kind, Channel channel, float t, float factor)
    {
        double value = channel.get(t) * factor;

        if (value > 0.001D)
        {
            out.add(new FxOverlay(kind).set(0, value).set(1, t / 20D));
        }
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        this.simple(out, FxOverlay.VHS, this.vhs, t, factor);
        this.simple(out, FxOverlay.VINTAGE, this.vintage, t, factor);
        this.simple(out, FxOverlay.RADIAL_BLUR, this.radialBlur, t, factor);
        this.simple(out, FxOverlay.LENS_DIRT, this.lensDirt, t, factor);
        this.simple(out, FxOverlay.DUST, this.dust, t, factor);
        this.simple(out, FxOverlay.LIGHT_LEAK, this.lightLeak, t, factor);

        double chroma = this.chromaIntensity.get(t) * factor;

        if (Math.abs(chroma) > 0.001D)
        {
            out.add(new FxOverlay(FxOverlay.CHROMATIC)
                .set(0, chroma).set(1, this.chromaAngle.get(t)).set(2, this.chromaDirectional.get(t))
                .set(3, this.chromaRadius.get(t)).set(4, this.chromaHardness.get(t)).set(5, this.chromaBalance.get(t))
                .set(6, this.chromaCenterX.get(t)).set(7, this.chromaCenterY.get(t))
                .set(8, this.chromaGreenShift.get(t)).set(9, this.chromaSpectrum.get(t)));
        }

        double fish = this.fishIntensity.get(t) * factor;

        if (Math.abs(fish) > 0.001D)
        {
            out.add(new FxOverlay(FxOverlay.FISH_EYE)
                .set(0, fish).set(1, this.fishDistance.get(t)).set(2, this.fishRadius.get(t))
                .set(3, this.fishHardness.get(t)).set(4, this.fishSharpening.get(t)));
        }

        double heat = this.heatStrength.get(t) * factor;

        if (heat > 0.001D)
        {
            out.add(new FxOverlay(FxOverlay.HEAT)
                .set(0, heat).set(1, this.heatSpeed.get(t)).set(2, this.heatScale.get(t)).set(3, t / 20D));
        }
    }

    @Override
    protected Clip create()
    {
        return new CinematicEffectsClip();
    }
}
