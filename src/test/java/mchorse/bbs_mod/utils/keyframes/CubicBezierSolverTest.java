package mchorse.bbs_mod.utils.keyframes;

import java.util.Random;

/** Standalone checks (no test framework): run its main. */
public class CubicBezierSolverTest
{
    private static int failures;
    private static int checks;

    /** The previous implementation, verbatim, as the compatibility reference. */
    private static double legacy(double aValue, double bValue, float aTick, float bTick, float aRx, float aRy, float bLx, float bLy, float x)
    {
        if (x <= 0) return aValue;
        if (x >= 1) return bValue;

        double w = bTick - aTick;
        double h = bValue - aValue;

        if (h == 0) h = 0.00001;

        double x1 = aRx / w;
        double y1 = aRy / h;
        double x2 = (w - bLx) / w;
        double y2 = (h + bLy) / h;
        double e = 0.0005;

        e = h == 0 ? e : Math.max(Math.min(e, 1 / h * e), 0.00001);
        x1 = Math.max(0, Math.min(1, x1));
        x2 = Math.max(0, Math.min(1, x2));

        return bez(0, y1, y2, 1, bezierX(x1, x2, x, e)) * h + aValue;
    }

    private static double bezierX(double x1, double x2, double t, double epsilon)
    {
        double x = t;
        double init = bez(0, x1, x2, 1, t);
        double factor = Math.copySign(0.1F, t - init);

        while (Math.abs(t - init) > epsilon)
        {
            double oldFactor = factor;

            x += factor;
            init = bez(0, x1, x2, 1, x);

            if (Math.copySign(factor, t - init) != oldFactor)
            {
                factor *= -0.25F;
            }
        }

        return x;
    }

    private static double bez(double a, double b, double c, double d, double t)
    {
        double t1 = a + (b - a) * t;
        double t2 = b + (c - b) * t;
        double t3 = c + (d - c) * t;
        double t4 = t1 + (t2 - t1) * t;
        double t5 = t2 + (t3 - t2) * t;

        return t4 + (t5 - t4) * t;
    }

    private static void check(boolean ok, String what)
    {
        checks++;

        if (!ok)
        {
            failures++;

            if (failures < 20)
            {
                System.out.println("FAIL: " + what);
            }
        }
    }

    public static void main(String[] args)
    {
        Random random = new Random(1234);
        double[] diffs = new double[20000];

        /* 1. Same curve as the legacy solver: segments with ordinary handles. */
        for (int i = 0; i < 20000; i++)
        {
            float aTick = random.nextInt(50);
            float bTick = aTick + 1 + random.nextInt(120);
            double a = (random.nextDouble() - 0.5) * 200;
            double b = random.nextInt(10) == 0 ? a : (random.nextDouble() - 0.5) * 200;
            float w = bTick - aTick;
            float aRx = random.nextFloat() * w;
            float bLx = random.nextFloat() * w;
            float aRy = (float) ((random.nextDouble() - 0.5) * 100);
            float bLy = (float) ((random.nextDouble() - 0.5) * 100);
            float x = random.nextFloat();

            double expected = legacy(a, b, aTick, bTick, aRx, aRy, bLx, bLy, x);
            double actual = BezierUtils.get(a, b, aTick, bTick, aRx, aRy, bLx, bLy, x);
            double scale = Math.max(1, Math.max(Math.abs(b - a), Math.max(Math.abs(aRy), Math.abs(bLy))));

            diffs[i] = Math.abs(expected - actual) / scale;
        }

        java.util.Arrays.sort(diffs);

        double median = diffs[diffs.length / 2];
        double p99 = diffs[diffs.length * 99 / 100];
        double worstScaled = diffs[diffs.length - 1];

        /* The legacy root finder stops at x within 5e-4 (and its epsilon was scaled by the segment height), which
         * strong value handles amplify; the new curve is the exact one. So: typical and 99th-percentile deviation
         * must be tiny, the worst case is only reported. */
        System.out.println("deviation from legacy, relative to the segment range: median " + median + ", p99 " + p99 + ", worst " + worstScaled);
        check(median < 1.0e-4 && p99 < 2.0e-3, "matches legacy within its own tolerance");

        /* 2. Solver accuracy: x(t) == x to machine precision, including flat ends and extreme handles. */
        double[][] cases = {{0, 1}, {0, 0}, {1, 1}, {0.33, 0.67}, {1, 0}, {0.0001, 0.9999}, {0, 1e-9}, {1, 1 - 1e-9}, {-3, 4}};

        for (double[] c : cases)
        {
            for (int i = 0; i <= 200; i++)
            {
                double x = i / 200D;
                double t = CubicBezierSolver.solveT(c[0], c[1], x);
                double back = CubicBezierSolver.bezier(0, Math.max(0, Math.min(1, c[0])), Math.max(0, Math.min(1, c[1])), 1, t);

                check(t >= 0 && t <= 1, "t in range " + c[0] + "," + c[1] + " x=" + x);
                check(Math.abs(back - x) < 1e-9, "x(t)==x for " + c[0] + "," + c[1] + " x=" + x + " got " + back);
            }
        }

        /* 3. Finite, and no overshoot when there are no value handles (flat segments included). */
        for (int run = 0; run < 200; run++)
        {
            double a = run % 3 == 0 ? 5 : random.nextDouble() * 10;
            double b = run % 3 == 0 ? 5 : random.nextDouble() * 10;
            float aRx = random.nextFloat() * 20;
            float bLx = random.nextFloat() * 20;

            for (int i = 0; i <= 100; i++)
            {
                double v = BezierUtils.get(a, b, 0, 20, aRx, 0, bLx, 0, i / 100F);

                check(!Double.isNaN(v) && !Double.isInfinite(v), "finite");
                check(v >= Math.min(a, b) - 1e-9 && v <= Math.max(a, b) + 1e-9, "no overshoot without value handles");
            }
        }

        /* 4. A flat segment with value handles keeps the handle shape. */
        double mid = BezierUtils.get(3, 3, 0, 10, 4, 6, 4, 6, 0.5F);

        check(mid > 3, "flat segment bulges with handles: " + mid);

        /* 5. Determinism. */
        double first = BezierUtils.get(1, 9, 0, 17, 5, 3, 7, -2, 0.37F);

        for (int i = 0; i < 100; i++)
        {
            check(BezierUtils.get(1, 9, 0, 17, 5, 3, 7, -2, 0.37F) == first, "same input, same output");
        }

        /* 6. Degenerate segment. */
        check(BezierUtils.get(2, 8, 5, 5, 1, 1, 1, 1, 0.5F) == 2, "zero-width segment yields its start");

        System.out.println(checks + " checks, " + failures + " failures");

        if (failures > 0)
        {
            System.exit(1);
        }
    }
}
