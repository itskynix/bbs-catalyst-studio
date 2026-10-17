package mchorse.bbs_mod.utils.keyframes;

public class BezierUtils
{
    /**
     * The value of a Bezier segment at normalized time {@code x}; the solving is
     * {@link CubicBezierSolver}'s. Same arguments and same curve as before: only the root finding
     * changed (exact instead of approximate), and the flat-segment workaround is gone.
     */
    public static double get(double aValue, double bValue, float aTick, float bTick, float aRx, float aRy, float bLx, float bLy, float x)
    {
        return CubicBezierSolver.value(aValue, bValue, aTick, bTick, aRx, aRy, bLx, bLy, x);
    }
}
