package mchorse.bbs_mod.ui.framework.elements.overlay;

/**
 * How far an interface part that slides in from an edge is shown: a small state machine driven by
 * the frames, timed and eased by the interface animation settings ({@link OverlayAnimation}).
 *
 * <p>The target is 1 (shown) or 0 (gone); every {@link #update()} moves the progress toward it by
 * the time since the last one over the duration, so a slide turned around halfway carries on from
 * where it was. With the animations off the progress is the target at once.</p>
 */
public class SlideState
{
    /** Linear: the share of the duration covered, toward shown */
    private double progress;
    private double target;
    private long last;

    public SlideState(double progress, double target)
    {
        this.progress = progress;
        this.target = target;
        this.last = System.nanoTime();
    }

    public static SlideState opening()
    {
        return new SlideState(0D, 1D);
    }

    public static SlideState closing()
    {
        return new SlideState(1D, 0D);
    }

    public void setTarget(double target)
    {
        if (this.target != target)
        {
            this.target = target;
            this.last = System.nanoTime();
        }
    }

    public boolean isClosing()
    {
        return this.target < 0.5D;
    }

    /** Advance by the time since the last call; true once the target is reached. */
    public boolean update()
    {
        long now = System.nanoTime();

        if (!OverlayAnimation.isEnabled())
        {
            this.progress = this.target;
        }
        else
        {
            double step = (now - this.last) / (double) OverlayAnimation.duration();

            this.progress = this.target > this.progress
                ? Math.min(this.target, this.progress + step)
                : Math.max(this.target, this.progress - step);
        }

        this.last = now;

        return this.progress == this.target;
    }

    /** How shown it is, eased: 0 off its edge, 1 in place (a back curve passes 1 a little). */
    public double shown()
    {
        if (this.isClosing())
        {
            return OverlayAnimation.progress(1D - this.progress, true);
        }

        return OverlayAnimation.progress(this.progress, false);
    }
}
