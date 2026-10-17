package mchorse.bbs_mod.camera.clips.fx;

/**
 * One full-screen effect pass a clip asks for this frame. The clip only fills in numbers; what a
 * kind looks like is the business of the renderer that draws it. The parameters travel as a flat
 * array so a kind can be added without touching anything but its clip and the shader.
 */
public class FxOverlay
{
    public static final int GRADE = 1;
    public static final int VHS = 2;
    public static final int VINTAGE = 3;
    public static final int RADIAL_BLUR = 4;
    public static final int LENS_DIRT = 5;
    public static final int DUST = 6;
    public static final int LIGHT_LEAK = 7;
    public static final int CHROMATIC = 8;
    public static final int FISH_EYE = 9;
    public static final int HEAT = 10;
    public static final int VIGNETTE = 11;
    public static final int LETTERBOX = 12;
    public static final int GRAIN = 13;
    public static final int EYE = 14;

    public static final int PARAMS = 24;

    public final int kind;
    public final float[] params = new float[PARAMS];

    public FxOverlay(int kind)
    {
        this.kind = kind;
    }

    public FxOverlay set(int index, double value)
    {
        this.params[index] = (float) value;

        return this;
    }

    /** Packs 0xRRGGBB into three consecutive parameters starting at {@code index}. */
    public FxOverlay rgb(int index, int color)
    {
        this.params[index] = ((color >> 16) & 0xFF) / 255F;
        this.params[index + 1] = ((color >> 8) & 0xFF) / 255F;
        this.params[index + 2] = (color & 0xFF) / 255F;

        return this;
    }
}
