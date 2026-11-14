package mchorse.bbs_mod.camera.clips.fx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalogue of the post-process effect library: what each effect is called, which category it is
 * filed under, the shader mode that draws it, and its (up to four) parameters with their defaults and
 * ranges. A {@link PostProcessClip} layer names an effect by its id; everything else is looked up here.
 *
 * <p>The mode is the effect's place in this list, and it is what the {@code postfx_lib} shader branches
 * on, so effects are only ever appended: inserting one in the middle would renumber the ones after it.</p>
 */
public final class PostEffect
{
    public static final String OPTICS = "optics";
    public static final String GLITCH = "glitch";
    public static final String ARTISTIC = "artistic";
    public static final String GEOMETRIC = "geometric";
    public static final String ATMOSPHERE = "atmosphere";
    public static final String PSYCHOLOGICAL = "psychological";

    public static final String[] CATEGORIES = {OPTICS, GLITCH, ARTISTIC, GEOMETRIC, ATMOSPHERE, PSYCHOLOGICAL};

    /** No colour parameter */
    public static final int NO_COLOR = -1;

    /** One parameter: its name (a language key suffix), default and range */
    public record Param(String name, float value, float min, float max) {}

    private static final List<PostEffect> ALL = new ArrayList<>();
    private static final Map<String, PostEffect> BY_ID = new HashMap<>();

    public final String id;
    public final String category;
    public final int mode;
    /** Whether the effect reads the world's depth; without it, it falls back to a screen space look */
    public final boolean depth;
    /** The default colour, or {@link #NO_COLOR} when the effect has none */
    public final int color;
    public final Param[] params;

    private PostEffect(String id, String category, int mode, boolean depth, int color, Param[] params)
    {
        this.id = id;
        this.category = category;
        this.mode = mode;
        this.depth = depth;
        this.color = color;
        this.params = params;
    }

    public boolean hasColor()
    {
        return this.color != NO_COLOR;
    }

    public static List<PostEffect> all()
    {
        return Collections.unmodifiableList(ALL);
    }

    public static PostEffect get(String id)
    {
        return BY_ID.get(id);
    }

    public static List<PostEffect> inCategory(String category)
    {
        List<PostEffect> list = new ArrayList<>();

        for (PostEffect effect : ALL)
        {
            if (effect.category.equals(category))
            {
                list.add(effect);
            }
        }

        return list;
    }

    private static Param p(String name, float value, float min, float max)
    {
        return new Param(name, value, min, max);
    }

    private static void def(String category, String id, boolean depth, int color, Param... params)
    {
        PostEffect effect = new PostEffect(id, category, ALL.size(), depth, color, params);

        ALL.add(effect);
        BY_ID.put(id, effect);
    }

    static
    {
        /* Cinematic and optics */
        def(OPTICS, "anamorphic_flares", false, 0x6fa8ff, p("threshold", 0.7F, 0F, 1F), p("length", 0.6F, 0F, 2F));
        def(OPTICS, "bleach_bypass", false, NO_COLOR, p("contrast", 1.2F, 0.5F, 2F));
        def(OPTICS, "hazy_lights", false, NO_COLOR, p("threshold", 0.6F, 0F, 1F), p("radius", 0.02F, 0F, 0.1F));
        def(OPTICS, "iridescent", false, NO_COLOR, p("scale", 3F, 0F, 10F), p("speed", 0.2F, -5F, 5F));
        def(OPTICS, "screen_drops", false, NO_COLOR, p("density", 12F, 1F, 60F), p("size", 1F, 0.1F, 3F));
        def(OPTICS, "mojo_tone", false, NO_COLOR, p("warmth", 0.6F, 0F, 2F));
        def(OPTICS, "selective_color", false, 0xd02020, p("tolerance", 0.08F, 0F, 0.5F), p("softness", 0.08F, 0F, 0.5F));
        def(OPTICS, "halation", false, NO_COLOR, p("threshold", 0.7F, 0F, 1F), p("radius", 0.012F, 0F, 0.1F));
        def(OPTICS, "day_for_night", false, NO_COLOR, p("darkness", 0.6F, 0F, 1F));

        /* Glitch and signal */
        def(GLITCH, "glitch", false, NO_COLOR, p("amount", 0.5F, 0F, 1F), p("speed", 8F, 0F, 60F));
        def(GLITCH, "world_glitch", true, NO_COLOR, p("band", 4F, 0.1F, 64F), p("shift", 0.03F, 0F, 0.3F), p("speed", 6F, 0F, 60F));
        def(GLITCH, "cyber_scan", false, 0x22e6ff, p("speed", 0.5F, -5F, 5F), p("width", 0.03F, 0.001F, 0.5F), p("grid", 40F, 0F, 200F));
        def(GLITCH, "monitor", false, NO_COLOR, p("curvature", 0.15F, 0F, 1F), p("scanlines", 0.5F, 0F, 1F));
        def(GLITCH, "digitize", false, 0x33ff77, p("cells", 60F, 4F, 400F), p("threshold", 0.5F, 0F, 1F));
        def(GLITCH, "pixel_dissolve", false, 0x000000, p("progress", 0.5F, 0F, 1F), p("cell", 8F, 1F, 128F));
        def(GLITCH, "channel_swapper", false, NO_COLOR, p("swap", 1F, 0F, 5F));
        def(GLITCH, "channel_clamper", false, 0xffffff, p("low", 0F, 0F, 1F), p("high", 0.8F, 0F, 1F));
        def(GLITCH, "hue_panner", false, NO_COLOR, p("hue", 90F, -360F, 360F), p("speed", 0F, -360F, 360F));
        def(GLITCH, "colorize", false, 0xff9a3c, p("brightness", 1.2F, 0F, 3F));
        def(GLITCH, "invert", false, NO_COLOR);

        /* Artistic and drawing */
        def(ARTISTIC, "toon_shading", false, NO_COLOR, p("levels", 4F, 2F, 16F), p("edge", 0.3F, 0F, 2F));
        def(ARTISTIC, "sketch", false, NO_COLOR, p("density", 1F, 0.1F, 4F));
        def(ARTISTIC, "comic_halftone", false, NO_COLOR, p("dot", 6F, 2F, 40F));
        def(ARTISTIC, "drawing", false, 0x1a1a24, p("thickness", 1F, 0.5F, 4F));
        def(ARTISTIC, "kuwahara", false, NO_COLOR, p("radius", 4F, 1F, 6F));
        def(ARTISTIC, "emboss", false, NO_COLOR, p("strength", 2F, 0F, 10F));
        def(ARTISTIC, "hard_emboss", false, NO_COLOR, p("strength", 4F, 0F, 20F));
        def(ARTISTIC, "edge_detect", false, NO_COLOR, p("threshold", 0.05F, 0F, 1F), p("colorful", 0F, 0F, 1F));
        def(ARTISTIC, "neon", false, NO_COLOR, p("glow", 1.5F, 0F, 5F), p("threshold", 0.1F, 0F, 1F));
        def(ARTISTIC, "dither", false, NO_COLOR, p("levels", 4F, 2F, 16F), p("scale", 1F, 1F, 16F));
        def(ARTISTIC, "posterize", false, NO_COLOR, p("levels", 5F, 2F, 32F));

        /* Mosaic and geometric */
        def(GEOMETRIC, "mosaic", false, NO_COLOR, p("cell", 16F, 2F, 256F));
        def(GEOMETRIC, "circles", false, NO_COLOR, p("cell", 12F, 3F, 128F), p("size", 0.9F, 0.1F, 1.5F));
        def(GEOMETRIC, "squares", false, NO_COLOR, p("cell", 12F, 3F, 128F), p("gap", 0.15F, 0F, 0.9F));
        def(GEOMETRIC, "tiles", false, NO_COLOR, p("cell", 32F, 4F, 256F), p("grout", 0.06F, 0F, 0.45F));
        def(GEOMETRIC, "kaleidoscope", false, NO_COLOR, p("segments", 6F, 2F, 32F), p("rotation", 0F, -360F, 360F));
        def(GEOMETRIC, "pyramid", false, NO_COLOR, p("cell", 40F, 4F, 256F), p("depth", 0.5F, 0F, 1F));
        def(GEOMETRIC, "grid_cell", false, NO_COLOR, p("cell", 20F, 4F, 128F), p("border", 0.5F, 0F, 1F));
        def(GEOMETRIC, "wired", false, 0x40ff90, p("sensitivity", 1.5F, 0F, 10F), p("grid", 40F, 0F, 200F));
        def(GEOMETRIC, "mirror", false, NO_COLOR, p("mode", 0F, 0F, 2F));

        /* Atmosphere and nature */
        def(ATMOSPHERE, "frost", false, NO_COLOR, p("amount", 0.6F, 0F, 1F), p("scale", 1F, 0.1F, 5F));
        def(ATMOSPHERE, "snow", false, NO_COLOR, p("density", 1F, 0F, 3F), p("speed", 1F, 0F, 5F));
        def(ATMOSPHERE, "screen_fire", false, NO_COLOR, p("height", 0.35F, 0F, 1F), p("speed", 1F, 0F, 5F));
        def(ATMOSPHERE, "scratches", false, NO_COLOR, p("density", 0.5F, 0F, 1F), p("speed", 24F, 0F, 60F));
        def(ATMOSPHERE, "screen_decals", false, 0x6b5a3a, p("amount", 0.5F, 0F, 1F), p("scale", 1F, 0.1F, 5F));
        def(ATMOSPHERE, "world_splash", true, NO_COLOR, p("distance", 6F, 0.5F, 64F), p("waves", 1F, 0F, 5F), p("speed", 1F, 0F, 5F));
        def(ATMOSPHERE, "screen_waves", false, NO_COLOR, p("amplitude", 0.01F, 0F, 0.1F), p("frequency", 10F, 0F, 100F), p("speed", 1F, -10F, 10F));
        def(ATMOSPHERE, "distance_distortion", true, NO_COLOR, p("start", 16F, 0F, 256F), p("strength", 0.006F, 0F, 0.05F), p("speed", 1F, 0F, 10F));
        def(ATMOSPHERE, "underwater", true, NO_COLOR, p("murk", 0.5F, 0F, 1F), p("wobble", 0.006F, 0F, 0.05F));

        /* Psychological and tension */
        def(PSYCHOLOGICAL, "alarm", false, 0xff1a1a, p("rate", 1.5F, 0F, 10F));
        def(PSYCHOLOGICAL, "alcohol", false, NO_COLOR, p("amount", 0.02F, 0F, 0.1F), p("speed", 1F, 0F, 5F));
        def(PSYCHOLOGICAL, "drug", false, NO_COLOR, p("speed", 1F, 0F, 5F), p("warp", 0.02F, 0F, 0.1F));
        def(PSYCHOLOGICAL, "infected", false, 0x5aff3a, p("spread", 0.5F, 0F, 1F));
        def(PSYCHOLOGICAL, "haunted", false, NO_COLOR, p("darkness", 0.6F, 0F, 1F), p("flicker", 0.5F, 0F, 1F));
        def(PSYCHOLOGICAL, "melted_world", false, NO_COLOR, p("amount", 0.08F, 0F, 0.5F), p("speed", 0.3F, 0F, 5F));
        def(PSYCHOLOGICAL, "screen_damage", false, 0x8a0000, p("amount", 0.5F, 0F, 1F));
        def(PSYCHOLOGICAL, "sonar", true, 0x30d0ff, p("speed", 20F, 0F, 200F), p("width", 2F, 0.1F, 20F), p("range", 64F, 1F, 512F));
        def(PSYCHOLOGICAL, "speed_lines", false, NO_COLOR, p("density", 80F, 4F, 400F), p("length", 0.5F, 0F, 1F));
        def(PSYCHOLOGICAL, "pulse", false, 0x700000, p("rate", 1.1F, 0F, 5F), p("strength", 0.03F, 0F, 0.2F));
        def(PSYCHOLOGICAL, "night_vision", false, NO_COLOR, p("noise", 0.3F, 0F, 1F), p("gain", 2F, 0F, 8F));
        def(PSYCHOLOGICAL, "thermal_vision", false, NO_COLOR, p("contrast", 1F, 0.1F, 4F));
    }
}
