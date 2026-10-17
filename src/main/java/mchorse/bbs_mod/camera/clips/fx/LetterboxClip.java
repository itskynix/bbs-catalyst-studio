package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Cinematic bars: everything outside a window of the frame is covered in a colour. The window is
 * given by an aspect ratio / bar thickness (the fields) or, with keyframes switched on, by the
 * channels, which take over for as long as they hold keyframes.
 */
public class LetterboxClip extends FxClip
{
    public final ValueInt color = new ValueInt("color", 0x000000);
    public final ValueBoolean useKeyframes = new ValueBoolean("use_keyframes", false);

    /* Tilt: how the window is turned and shaped */
    public final ValueFloat tiltAngle = new ValueFloat("tilt_angle", 0F);
    public final ValueFloat tiltAspect = new ValueFloat("tilt_aspect", 2.39F);
    public final ValueFloat tiltThickness = new ValueFloat("tilt_thickness", 0F);
    public final ValueFloat tiltFeather = new ValueFloat("tilt_feather", 0F);

    public final ValueFloat offsetX = new ValueFloat("offset_x", 0F);
    public final ValueFloat offsetY = new ValueFloat("offset_y", 0F);

    public final Channel colorR = this.channel("colorR", 0D);
    public final Channel colorG = this.channel("colorG", 0D);
    public final Channel colorB = this.channel("colorB", 0D);
    public final Channel width = this.channel("width", 1D);
    public final Channel height = this.channel("height", -1D);
    public final Channel smoothness = this.channel("smoothness", 0D);
    public final Channel rotation = this.channel("rotation", 0D);
    public final Channel zoom = this.channel("zoom", 1D);
    public final Channel channelOffsetX = this.channel("offsetX", 0D);
    public final Channel channelOffsetY = this.channel("offsetY", 0D);

    public LetterboxClip()
    {
        super();

        this.add(this.color);
        this.add(this.useKeyframes);
        this.add(this.tiltAngle);
        this.add(this.tiltAspect);
        this.add(this.tiltThickness);
        this.add(this.tiltFeather);
        this.add(this.offsetX);
        this.add(this.offsetY);
    }

    private double pick(Channel channel, float t, double fallback)
    {
        return this.useKeyframes.get() && !channel.channel.isEmpty() ? channel.channel.interpolate(t) : fallback;
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        FxOverlay overlay = new FxOverlay(FxOverlay.LETTERBOX);

        overlay.rgb(0, this.color.get());

        if (this.useKeyframes.get())
        {
            if (!this.colorR.channel.isEmpty()) overlay.set(0, this.colorR.get(t));
            if (!this.colorG.channel.isEmpty()) overlay.set(1, this.colorG.get(t));
            if (!this.colorB.channel.isEmpty()) overlay.set(2, this.colorB.get(t));
        }

        overlay.set(3, factor);
        overlay.set(4, this.pick(this.width, t, 1D));
        overlay.set(5, this.pick(this.height, t, -1D));
        overlay.set(6, this.pick(this.smoothness, t, 0D) + this.tiltFeather.get());
        overlay.set(7, this.pick(this.rotation, t, 0D) + this.tiltAngle.get());
        overlay.set(8, this.pick(this.zoom, t, 1D));
        overlay.set(9, this.pick(this.channelOffsetX, t, 0D) + this.offsetX.get());
        overlay.set(10, this.pick(this.channelOffsetY, t, 0D) + this.offsetY.get());
        overlay.set(11, this.tiltAspect.get());
        overlay.set(12, this.tiltThickness.get());

        out.add(overlay);
    }

    @Override
    protected Clip create()
    {
        return new LetterboxClip();
    }
}
