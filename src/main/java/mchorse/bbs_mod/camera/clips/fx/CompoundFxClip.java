package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.List;

/**
 * Compound FX Clip: a nesting container (like DaVinci Resolve Compound Clip / Premiere Nest)
 * combining multiple cinematic post-processing effect clips (ColorGradeClip, VignetteClip,
 * GrainClip, LensDistortionClip, MotionBlurClip, etc.) into a single unified clip and track
 * with full envelope scaling, nesting sub-timeline, and preset import/export capabilities.
 */
public class CompoundFxClip extends FxClip
{
    /* Nested child clips container */
    public final Clips children;

    /* Standalone effect channels for backwards-compatibility and direct keyframing */
    /* Color Grade */
    public final ValueBoolean enableColorGrade = new ValueBoolean("enable_color_grade", false);
    public final ValueInt tintColor = new ValueInt("tint_color", 0xFFFFFF);
    public final Channel tintAlpha = this.channel("cg_tint_alpha", 0D);
    public final Channel saturation = this.channel("cg_saturation", 1D);
    public final Channel brightness = this.channel("cg_brightness", 0D);
    public final Channel contrast = this.channel("cg_contrast", 1D);
    public final Channel liftR = this.channel("cg_lift_r", 0D);
    public final Channel liftG = this.channel("cg_lift_g", 0D);
    public final Channel liftB = this.channel("cg_lift_b", 0D);
    public final Channel gammaR = this.channel("cg_gamma_r", 1D);
    public final Channel gammaG = this.channel("cg_gamma_g", 1D);
    public final Channel gammaB = this.channel("cg_gamma_b", 1D);
    public final Channel gainR = this.channel("cg_gain_r", 1D);
    public final Channel gainG = this.channel("cg_gain_g", 1D);
    public final Channel gainB = this.channel("cg_gain_b", 1D);

    /* Lens Distortion */
    public final ValueBoolean enableDistortion = new ValueBoolean("enable_distortion", false);
    public final Channel distortion = this.channel("ld_distortion", 0D);
    public final Channel distortionScale = this.channel("ld_scale", 1D);

    /* Chromatic Aberration */
    public final ValueBoolean enableChroma = new ValueBoolean("enable_chroma", false);
    public final Channel chromaIntensity = this.channel("ca_intensity", 0D);
    public final Channel chromaAngle = this.channel("ca_angle", 0D);
    public final Channel chromaRadius = this.channel("ca_radius", 0.5D);

    /* Vignette */
    public final ValueBoolean enableVignette = new ValueBoolean("enable_vignette", false);
    public final ValueInt vignetteColor = new ValueInt("vignette_color", 0x000000);
    public final Channel vignetteIntensity = this.channel("vg_intensity", 0D);
    public final Channel vignetteRadius = this.channel("vg_radius", 0.75D);
    public final Channel vignetteSoftness = this.channel("vg_softness", 0.5D);

    /* Grain */
    public final ValueBoolean enableGrain = new ValueBoolean("enable_grain", false);
    public final Channel grainStrength = this.channel("gr_strength", 0D);
    public final Channel grainSize = this.channel("gr_size", 1D);

    /* Motion Blur */
    public final ValueBoolean enableMotionBlur = new ValueBoolean("enable_motion_blur", false);
    public final ValueFloat motionBlurShutter = new ValueFloat("mb_shutter", 0.5F, 0F, 2F);
    public final Channel motionBlurIntensity = this.channel("mb_intensity", 0D);

    public CompoundFxClip()
    {
        super();

        this.children = new Clips("children", BBSMod.getFactoryCameraClips());
        this.add(this.children);

        this.add(this.enableColorGrade);
        this.add(this.tintColor);

        this.add(this.enableDistortion);

        this.add(this.enableChroma);

        this.add(this.enableVignette);
        this.add(this.vignetteColor);

        this.add(this.enableGrain);

        this.add(this.enableMotionBlur);
        this.add(this.motionBlurShutter);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        /* 1. Execute nested child clips (DaVinci/Premiere Compound Clip logic) */
        int localTick = (int) t;

        for (Clip child : this.children.get())
        {
            if (!child.enabled.get())
            {
                continue;
            }

            if (child.isInside(localTick) || child.isGlobal())
            {
                if (child instanceof FxClip fxChild)
                {
                    float childRelativeTick = t - fxChild.tick.get();
                    float childFactor = fxChild.envelope.factorEnabled(fxChild.duration.get(), childRelativeTick);

                    if (childFactor > 0F)
                    {
                        fxChild.collect(childRelativeTick, factor * childFactor, out);
                    }
                }
            }
        }

        /* 2. Direct standalone channels (if enabled) */
        if (this.enableColorGrade.get())
        {
            FxOverlay grade = new FxOverlay(FxOverlay.GRADE);

            grade.rgb(0, this.tintColor.get());
            grade.set(3, this.tintAlpha.get(t) * factor);
            grade.set(4, 1D + (this.saturation.get(t) - 1D) * factor);
            grade.set(5, 0D);
            grade.set(6, this.brightness.get(t) * factor);
            grade.set(7, 1D + (this.contrast.get(t) - 1D) * factor);

            grade.set(8, this.liftR.get(t) * factor).set(9, this.liftG.get(t) * factor).set(10, this.liftB.get(t) * factor);
            grade.set(12, 1D + (this.gammaR.get(t) - 1D) * factor).set(13, 1D + (this.gammaG.get(t) - 1D) * factor).set(14, 1D + (this.gammaB.get(t) - 1D) * factor);
            grade.set(16, 1D + (this.gainR.get(t) - 1D) * factor).set(17, 1D + (this.gainG.get(t) - 1D) * factor).set(18, 1D + (this.gainB.get(t) - 1D) * factor);

            out.add(grade);
        }

        if (this.enableDistortion.get())
        {
            double dist = this.distortion.get(t) * factor;

            if (Math.abs(dist) > 0.001D)
            {
                out.add(new FxOverlay(FxOverlay.LENS_DISTORTION)
                    .set(0, dist)
                    .set(1, 0D)
                    .set(2, this.distortionScale.get(t)));
            }
        }

        if (this.enableChroma.get())
        {
            double chroma = this.chromaIntensity.get(t) * factor;

            if (Math.abs(chroma) > 0.001D)
            {
                out.add(new FxOverlay(FxOverlay.CHROMATIC)
                    .set(0, chroma).set(1, this.chromaAngle.get(t)).set(2, 0D)
                    .set(3, this.chromaRadius.get(t)).set(4, 0.5D).set(5, 0D)
                    .set(6, 0.5D).set(7, 0.5D)
                    .set(8, 0D).set(9, 0D));
            }
        }

        if (this.enableVignette.get())
        {
            double vig = this.vignetteIntensity.get(t) * factor;

            if (vig > 0.001D)
            {
                out.add(new FxOverlay(FxOverlay.VIGNETTE)
                    .rgb(0, this.vignetteColor.get())
                    .set(3, vig)
                    .set(4, this.vignetteRadius.get(t))
                    .set(5, this.vignetteSoftness.get(t)));
            }
        }

        if (this.enableGrain.get())
        {
            double grain = this.grainStrength.get(t) * factor;

            if (grain > 0.001D)
            {
                out.add(new FxOverlay(FxOverlay.GRAIN)
                    .set(0, grain)
                    .set(1, this.grainSize.get(t))
                    .set(2, t / 20D));
            }
        }

        if (this.enableMotionBlur.get())
        {
            double mb = this.motionBlurIntensity.get(t) * factor;

            if (mb > 0.001D)
            {
                out.add(new FxOverlay(FxOverlay.MOTION_BLUR)
                    .set(0, this.motionBlurShutter.get())
                    .set(1, 1D)
                    .set(2, 0D)
                    .set(3, mb));
            }
        }
    }

    @Override
    public Clip copy()
    {
        CompoundFxClip clip = (CompoundFxClip) super.copy();

        for (Clip child : this.children.get())
        {
            clip.children.addClip(child.copy());
        }

        clip.children.sortLayers();

        return clip;
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        if (original instanceof CompoundFxClip compound)
        {
            for (Clip child : compound.children.get())
            {
                Clip copy = child.copy();

                copy.tick.set(copy.tick.get() - offset);
                this.children.addClip(copy);
            }

            this.children.sortLayers();
        }
    }

    @Override
    public BaseType toData()
    {
        BaseType base = super.toData();

        if (base.isMap())
        {
            base.asMap().put("children", this.children.toData());
        }

        return base;
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        if (data.isMap() && data.asMap().has("children"))
        {
            this.children.fromData(data.asMap().get("children"));
        }
    }

    @Override
    protected Clip create()
    {
        return new CompoundFxClip();
    }
}
