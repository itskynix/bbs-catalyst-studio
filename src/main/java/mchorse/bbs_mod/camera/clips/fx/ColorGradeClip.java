package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Colour grading over the finished frame: saturation, hue, brightness and contrast, then lift /
 * gamma / gain per channel, then a tint. Every channel reads as "no change" until keyframed.
 */
public class ColorGradeClip extends FxClip
{
    public final ValueInt overlayColor = new ValueInt("overlay_color", 0xFF6A1A);

    public final Channel overlayAlpha = this.channel("overlayAlpha", 0D);
    public final Channel saturation = this.channel("saturation", 1D);
    public final Channel hue = this.channel("hue", 0D);
    public final Channel brightness = this.channel("brightness", 0D);
    public final Channel contrast = this.channel("contrast", 1D);
    public final Channel liftR = this.channel("liftR", 0D);
    public final Channel liftG = this.channel("liftG", 0D);
    public final Channel liftB = this.channel("liftB", 0D);
    public final Channel gammaR = this.channel("gammaR", 1D);
    public final Channel gammaG = this.channel("gammaG", 1D);
    public final Channel gammaB = this.channel("gammaB", 1D);
    public final Channel gainR = this.channel("gainR", 1D);
    public final Channel gainG = this.channel("gainG", 1D);
    public final Channel gainB = this.channel("gainB", 1D);

    public ColorGradeClip()
    {
        super();

        this.add(this.overlayColor);

        /* A clip that is still neutral shows nothing, so it starts with a gentle push */
        this.saturation.channel.insert(0, 1.15D);
        this.contrast.channel.insert(0, 1.08D);
    }

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        FxOverlay overlay = new FxOverlay(FxOverlay.GRADE);

        overlay.rgb(0, this.overlayColor.get());
        overlay.set(3, this.overlayAlpha.get(t) * factor);

        overlay.set(4, 1D + (this.saturation.get(t) - 1D) * factor);
        overlay.set(5, this.hue.get(t) * factor);
        overlay.set(6, this.brightness.get(t) * factor);
        overlay.set(7, 1D + (this.contrast.get(t) - 1D) * factor);

        overlay.set(8, this.liftR.get(t) * factor).set(9, this.liftG.get(t) * factor).set(10, this.liftB.get(t) * factor);
        overlay.set(12, 1D + (this.gammaR.get(t) - 1D) * factor).set(13, 1D + (this.gammaG.get(t) - 1D) * factor).set(14, 1D + (this.gammaB.get(t) - 1D) * factor);
        overlay.set(16, 1D + (this.gainR.get(t) - 1D) * factor).set(17, 1D + (this.gainG.get(t) - 1D) * factor).set(18, 1D + (this.gainB.get(t) - 1D) * factor);

        out.add(overlay);
    }

    @Override
    protected Clip create()
    {
        return new ColorGradeClip();
    }
}
