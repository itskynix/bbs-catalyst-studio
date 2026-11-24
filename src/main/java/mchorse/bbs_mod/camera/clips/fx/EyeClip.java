package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Eyelids: the frame is covered in a colour outside an elliptical opening that closes and opens
 * with the blink channel. A channel with keyframes replaces the field of the same name.
 */
public class EyeClip extends FxClip
{
    public final ValueInt color = new ValueInt("color", 0x000000);

    /** Opacity of the cover, 0 - 100. */
    public final ValueFloat opacity = new ValueFloat("opacity", 100F);

    public final ValueFloat tiltAngle = new ValueFloat("tilt_angle", 0F);
    public final ValueFloat tiltAspect = new ValueFloat("tilt_aspect", 1F);
    public final ValueFloat tiltThickness = new ValueFloat("tilt_thickness", 0F);
    public final ValueFloat tiltFeather = new ValueFloat("tilt_feather", 0F);

    public final ValueFloat offsetX = new ValueFloat("offset_x", 0F);
    public final ValueFloat offsetY = new ValueFloat("offset_y", 0F);

    /** How hard a saccade closes the lids: the blink is pulled down by this much at the pulse's peak. */
    public final ValueFloat saccadeIntensity = new ValueFloat("saccade_intensity", 0.65F, 0F, 2F);

    /** How long the pulse takes to die away from its peak, in ticks. The recorder writes its keys with it. */
    public final ValueInt saccadeDuration = new ValueInt("saccade_duration", 4, 1, 15);

    public final Channel colorR = this.channel("eyeR", 0D);
    public final Channel colorG = this.channel("eyeG", 0D);
    public final Channel colorB = this.channel("eyeB", 0D);
    public final Channel colorOpacity = this.channel("colorOpacity", 100D);
    public final Channel width = this.channel("width", 1D);
    public final Channel blink = this.channel("blink", 1D);
    public final Channel smoothness = this.channel("smoothness", 0.15D);
    public final Channel rotation = this.channel("rotation", 0D);
    public final Channel size = this.channel("size", 1D);

    /**
     * The pulse channel of the first saccade build, 0 - 1. No longer listed or written: a saccade is
     * keyed straight into the blink channel now, one lid channel instead of two. It stays loaded and
     * played so a film recorded with it looks the same.
     */
    public final Channel saccade = this.channel("saccade", 0D);

    public EyeClip()
    {
        super();

        this.add(this.color);
        this.add(this.opacity);
        this.add(this.tiltAngle);
        this.add(this.tiltAspect);
        this.add(this.tiltThickness);
        this.add(this.tiltFeather);
        this.add(this.offsetX);
        this.add(this.offsetY);
        this.add(this.saccadeIntensity);
        this.add(this.saccadeDuration);

        this.fxChannels.remove(this.saccade);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        FxOverlay overlay = new FxOverlay(FxOverlay.EYE);

        overlay.rgb(0, this.color.get());

        if (!this.colorR.channel.isEmpty()) overlay.set(0, this.colorR.get(t));
        if (!this.colorG.channel.isEmpty()) overlay.set(1, this.colorG.get(t));
        if (!this.colorB.channel.isEmpty()) overlay.set(2, this.colorB.get(t));

        double opacity = this.colorOpacity.channel.isEmpty() ? this.opacity.get() : this.colorOpacity.get(t);

        overlay.set(3, opacity / 100D * factor);
        overlay.set(4, this.width.get(t) * this.tiltAspect.get());
        /* The saccade only ever pulls the lids down, from keys on the timeline and nothing else */
        double pulse = this.saccade.channel.isEmpty() ? 0D : Math.max(0D, Math.min(1D, this.saccade.get(t)));

        overlay.set(5, Math.max(0D, Math.min(1D, this.blink.get(t) - this.saccadeIntensity.get() * pulse)));
        overlay.set(6, this.smoothness.get(t) + this.tiltFeather.get());
        overlay.set(7, this.rotation.get(t) + this.tiltAngle.get());
        overlay.set(8, this.size.get(t));
        overlay.set(9, this.offsetX.get());
        overlay.set(10, this.offsetY.get());
        overlay.set(11, this.tiltThickness.get());

        out.add(overlay);
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        this.saccade.channel.moveX(-offset);
    }

    @Override
    protected Clip create()
    {
        return new EyeClip();
    }
}
