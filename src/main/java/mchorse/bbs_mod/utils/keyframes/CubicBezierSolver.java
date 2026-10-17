package mchorse.bbs_mod.utils.keyframes;

/**
 * A pure, deterministic cubic Bezier segment solver for keyframe curves.
 *
 * <p>A segment is a cubic Bezier in (time, value): its end points are the two keyframes and its
 * inner control points are the first one's out handle and the second one's in handle. Time is
 * normalized to 0..1 across the segment and the two inner x control points are kept inside 0..1,
 * which makes x(t) monotonic - so a time has exactly one parameter {@code t}, and the value is
 * the curve's y there.</p>
 *
 * <p>The parameter is found by Newton-Raphson held inside a shrinking bracket: a Newton step that
 * leaves the bracket, or a vanishing slope (a flat start or end, where x(t) has a double root),
 * falls back to bisection. The bracket shrinks on every iteration and the iteration count is
 * capped, so it always terminates, and with no state and no random input it returns the same
 * double for the same arguments. The value is computed on the absolute keyframe values, so a flat
 * segment (equal values) needs no special case.</p>
 */
public final class CubicBezierSolver
{
    private static final int MAX_ITERATIONS = 64;
    private static final double TOLERANCE = 1.0e-12;
    private static final double MIN_SLOPE = 1.0e-9;

    private CubicBezierSolver()
    {}

    /**
     * The curve parameter {@code t} in 0..1 whose x equals {@code x}, for the x control points
     * (0, {@code x1}, {@code x2}, 1). {@code x1} and {@code x2} are clamped into 0..1.
     */
    public static double solveT(double x1, double x2, double x)
    {
        if (!(x > 0D)) return 0D;
        if (x >= 1D) return 1D;

        x1 = clamp01(x1);
        x2 = clamp01(x2);

        double lo = 0D;
        double hi = 1D;
        double t = x;

        for (int i = 0; i < MAX_ITERATIONS; i++)
        {
            double error = bezier(0D, x1, x2, 1D, t) - x;

            if (Math.abs(error) < TOLERANCE)
            {
                return t;
            }

            /* x(t) is monotonic: the sign of the error tells which side of the root t is on. */
            if (error < 0D) lo = t;
            else hi = t;

            double slope = derivative(0D, x1, x2, 1D, t);
            double next = Math.abs(slope) > MIN_SLOPE ? t - error / slope : Double.NaN;

            /* A Newton step that is undefined or leaves the bracket is replaced by its midpoint. */
            if (!(next > lo && next < hi))
            {
                next = 0.5D * (lo + hi);
            }

            if (hi - lo < TOLERANCE)
            {
                return next;
            }

            t = next;
        }

        return t;
    }

    /**
     * The value of the segment at normalized time {@code x} (0..1), from absolute values and ticks.
     *
     * @param aRx the out handle's time offset of the first key (ticks, to the right)
     * @param aRy the out handle's value offset of the first key
     * @param bLx the in handle's time offset of the second key (ticks, to the left)
     * @param bLy the in handle's value offset of the second key
     */
    public static double value(double aValue, double bValue, double aTick, double bTick, double aRx, double aRy, double bLx, double bLy, double x)
    {
        if (x <= 0D) return aValue;
        if (x >= 1D) return bValue;

        double w = bTick - aTick;

        if (!(w > 0D)) return aValue;

        double t = solveT(aRx / w, (w - bLx) / w, x);

        return bezier(aValue, aValue + aRy, bValue + bLy, bValue, t);
    }

    public static double bezier(double p0, double p1, double p2, double p3, double t)
    {
        double u = 1D - t;

        return u * u * u * p0 + 3D * u * u * t * p1 + 3D * u * t * t * p2 + t * t * t * p3;
    }

    public static double derivative(double p0, double p1, double p2, double p3, double t)
    {
        double u = 1D - t;

        return 3D * u * u * (p1 - p0) + 6D * u * t * (p2 - p1) + 3D * t * t * (p3 - p2);
    }

    private static double clamp01(double value)
    {
        return value < 0D ? 0D : Math.min(value, 1D);
    }
}
