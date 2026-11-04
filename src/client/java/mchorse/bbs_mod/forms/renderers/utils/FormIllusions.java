package mchorse.bbs_mod.forms.renderers.utils;

/**
 * What the draws of a form's illusions share: whether one is being drawn right now, so a copy does
 * not breed copies of its own, and the shiver the copies are shaken by.
 */
public final class FormIllusions
{
    private static int drawing;

    private FormIllusions()
    {}

    public static void begin()
    {
        drawing += 1;
    }

    public static void end()
    {
        drawing = Math.max(0, drawing - 1);
    }

    /** Whether an illusion is being drawn; the copies of it are not drawn again */
    public static boolean isDrawing()
    {
        return drawing > 0;
    }

    /** A steady random number from -1 to 1 for a copy, an axis and a moment: the same inputs give the same shiver */
    public static float noise(int copy, int axis, long moment)
    {
        long n = copy * 374761393L + axis * 668265263L + moment * 2246822519L;

        n = (n ^ (n >>> 13)) * 1274126177L;
        n ^= n >>> 16;

        return ((n & 0xFFFFL) / (float) 0xFFFF) * 2F - 1F;
    }
}
