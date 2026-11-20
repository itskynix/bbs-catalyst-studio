package mchorse.bbs_mod.forms.renderers.utils;

/**
 * Whether an artificial space scene is in the world right now, and its colour of empty space. The
 * vanilla sky, its sun and moon, the clouds, the weather and the blue of the horizon are drawn before
 * any form is, so they are switched off by what the frames just before drew: a scene drawn within the
 * last moment keeps them off, and once it is gone (not drawn for that long) the vanilla sky is back.
 */
public final class FakeSpaceSky
{
    private static final long HOLD_NANOS = 300_000_000L;

    private static long lastDrawn;
    private static float red;
    private static float green;
    private static float blue;

    private FakeSpaceSky()
    {}

    /** Called by a space scene drawn in the world */
    public static void drawn(float r, float g, float b)
    {
        lastDrawn = System.nanoTime();
        red = r;
        green = g;
        blue = b;
    }

    public static boolean isActive()
    {
        return lastDrawn != 0L && System.nanoTime() - lastDrawn < HOLD_NANOS;
    }

    public static float red()
    {
        return red;
    }

    public static float green()
    {
        return green;
    }

    public static float blue()
    {
        return blue;
    }
}
