package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;

/**
 * The pop-in and pop-out of the modal windows, worked out from the appearance settings: how far
 * along the window is, as a scale and an opacity. The overlay only asks for those two numbers.
 */
public class OverlayAnimation
{
    public static final int LINEAR = 0;
    public static final int EASE_OUT = 1;
    public static final int EASE_IN_OUT = 2;
    public static final int BACK_OUT = 3;

    /** Zoom-in: grows from nothing. Zoom-out: settles down from a larger size. */
    public static final int ZOOM_IN = 0;
    public static final int ZOOM_OUT = 1;

    private static final double OUT_START_SCALE = 1.6D;
    private static final double MIN_SCALE = 0.001D;

    public static boolean isEnabled()
    {
        return BBSSettings.popupAnimations != null && BBSSettings.popupAnimations.get();
    }

    /** The length of the animation in nanoseconds */
    public static long duration()
    {
        int millis = BBSSettings.popupAnimationDuration == null ? 200 : BBSSettings.popupAnimationDuration.get();

        return Math.max(millis, 1) * 1_000_000L;
    }

    /**
     * How shown the window is, 0 (gone) to 1 (settled), after {@code time} of its animation.
     * Closing runs the way back, so the same curve is used mirrored; the back curve gets its
     * wind-up side on the way out, which is what makes it pop.
     */
    public static double progress(double time, boolean closing)
    {
        double t = Math.min(Math.max(time, 0D), 1D);
        int curve = BBSSettings.popupAnimationCurve == null ? EASE_OUT : BBSSettings.popupAnimationCurve.get();

        if (closing)
        {
            return 1D - (curve == BACK_OUT ? backIn(t) : ease(curve, t));
        }

        return ease(curve, t);
    }

    /** The scale at a progress; a window shown 0 is as small (or as large) as the direction says */
    public static double scale(double progress)
    {
        int direction = BBSSettings.popupAnimationDirection == null ? ZOOM_IN : BBSSettings.popupAnimationDirection.get();

        if (direction == ZOOM_OUT)
        {
            return 1D + (OUT_START_SCALE - 1D) * (1D - progress);
        }

        return Math.max(progress, MIN_SCALE);
    }

    private static double ease(int curve, double t)
    {
        switch (curve)
        {
            case LINEAR:
                return t;
            case EASE_IN_OUT:
                return t < 0.5D ? 4D * t * t * t : 1D - Math.pow(-2D * t + 2D, 3D) / 2D;
            case BACK_OUT:
                double c1 = 1.70158D;
                double c3 = c1 + 1D;

                return 1D + c3 * Math.pow(t - 1D, 3D) + c1 * Math.pow(t - 1D, 2D);
            case EASE_OUT:
            default:
                return 1D - Math.pow(1D - t, 3D);
        }
    }

    private static double backIn(double t)
    {
        double c1 = 1.70158D;

        return (c1 + 1D) * t * t * t - c1 * t * t;
    }
}
