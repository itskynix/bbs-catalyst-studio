package mchorse.bbs_mod.camera.clips.fx;

import mchorse.bbs_mod.utils.clips.Clip;

import java.util.List;

/**
 * Shakes the picture itself - an offset and a small turn of the finished frame - and leaves the
 * camera's own rotation alone. The motion is smooth noise of the clip's time, so the same tick
 * always shakes the same way, which keeps exports repeatable.
 */
public class ScreenShakeClip extends FxClip
{
    /** Most the frame is moved at an amplitude of 1, as a fraction of its size */
    private static final double MAX_OFFSET = 0.04D;
    /** Most the frame is turned at an amplitude of 1, in degrees */
    private static final double MAX_ROLL = 2D;

    public final Channel frequency = this.channel("frequency", 12D);
    /** Moves the frame sideways and up and down; 0 leaves only the turn */
    public final Channel amplitude = this.channel("amplitude", 0.5D);
    /** Turns the frame about the screen's Z axis, apart from the amplitude; 0 leaves only the move */
    public final Channel rotationalAmplitude = this.channel("rotational_amplitude", 0.5D);
    /** How much of the shake dies out by the end of the clip (0 keeps it, 1 fades it to nothing) */
    public final Channel fade = this.channel("fade", 0D);

    @Override
    protected void collect(float t, float factor, List<FxOverlay> out)
    {
        double duration = Math.max(this.duration.get(), 1);
        double progress = Math.min(Math.max(t / duration, 0D), 1D);
        double left = 1D - Math.min(Math.max(this.fade.get(t), 0D), 1D) * progress;
        double envelope = factor * left * left;
        double amp = Math.max(this.amplitude.get(t), 0D) * envelope;
        double rotAmp = Math.max(this.rotationalAmplitude.get(t), 0D) * envelope;

        if (amp <= 0D && rotAmp <= 0D)
        {
            return;
        }

        /* Seconds of the clip, so the rate means shakes per second whatever the tick rate */
        double time = t / 20D * this.frequency.get(t);
        double x = noise(time, 11) * amp * MAX_OFFSET;
        double y = noise(time, 37) * amp * MAX_OFFSET;
        double roll = noise(time, 71) * rotAmp * MAX_ROLL;

        /* Zoom in just enough for the moved and turned frame to still cover the screen */
        double zoom = 1D + 2D * Math.max(Math.abs(x), Math.abs(y)) * 2D + Math.toRadians(Math.abs(roll)) * 1.2D;

        out.add(new FxOverlay(FxOverlay.SCREEN_SHAKE)
            .set(0, x)
            .set(1, y)
            .set(2, roll)
            .set(3, zoom));
    }

    /** Smooth value noise in -1..1: a random value on every whole step, eased between them. */
    private static double noise(double x, int seed)
    {
        double floor = Math.floor(x);
        double f = x - floor;
        int i = (int) floor;

        f = f * f * (3D - 2D * f);

        return lattice(i, seed) * (1D - f) + lattice(i + 1, seed) * f;
    }

    private static double lattice(int i, int seed)
    {
        int h = i * 374761393 + seed * 668265263;

        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;

        return (h & 0xFFFF) / 32767.5D - 1D;
    }

    @Override
    protected Clip create()
    {
        return new ScreenShakeClip();
    }
}
