package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Per-pixel and per-voxel text effects, built on the pixels {@link TextDepthMesh} reads back from a
 * glyph: the burning-away of a letter ({@link #dissolve}) and its breaking into flying cubes
 * ({@link #shatter}). Both write flat-coloured triangles into a buffer the caller draws; nothing here
 * touches the graphics state.
 */
public class TextFx
{
    public static final int DISSOLVE_LINEAR = 0;
    public static final int DISSOLVE_PERLIN = 1;
    public static final int DISSOLVE_LUMA = 2;

    /* Noise */

    private static int hash(int a, int b, int c)
    {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9e3779b1;

        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;

        return h;
    }

    /** A number from 0 to 1 that is the same every time for the same seed and slot. */
    public static float random(int seed, int x, int y, int slot)
    {
        return (hash(seed + slot * 7919, x, y) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float lattice(int x, int y)
    {
        return (hash(x, y, 31) & 0xFFFF) / 65535F;
    }

    public static float noise(float x, float y)
    {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;

        fx = fx * fx * (3F - 2F * fx);
        fy = fy * fy * (3F - 2F * fy);

        float a = lattice(ix, iy) + (lattice(ix + 1, iy) - lattice(ix, iy)) * fx;
        float b = lattice(ix, iy + 1) + (lattice(ix + 1, iy + 1) - lattice(ix, iy + 1)) * fx;

        return a + (b - a) * fy;
    }

    /** Three octaves of value noise, 0 to 1. */
    public static float fbm(float x, float y)
    {
        return noise(x, y) * 0.55F + noise(x * 2.1F + 17F, y * 2.1F + 5F) * 0.3F + noise(x * 4.3F + 3F, y * 4.3F + 29F) * 0.15F;
    }

    /* Dissolve */

    /**
     * The front of a glyph with its pixels burning away. Each pixel has a value from 0 to 1 - by its
     * place along the line, by a noise map, or by its brightness - and goes when the progress passes it;
     * the ones the progress has only just reached glow in the burn colour for a stretch of {@code edge}.
     *
     * @param worldX where the glyph starts, in text units, so the noise runs on across glyphs
     * @param worldY where its line starts
     * @param start how far along its line the glyph starts, 0 to 1
     * @param span the width of the line, in text units
     */
    public static void dissolve(VertexConsumer builder, TextDepthMesh mesh, Matrix4f matrix, float dx, float dy, float depth, Color fill, float alpha, float brightness, int mode, float progress, float edge, Color burn, float frequency, float worldX, float worldY, float start, float span)
    {
        float threshold = progress * (1F + edge);
        float unit = 1F / mesh.maskScale;
        int w = mesh.maskWidth;
        float lumaMin = 1F;
        float lumaMax = 0F;

        if (mode == DISSOLVE_LUMA)
        {
            for (int k = 0; k < mesh.mask.length; k++)
            {
                if ((mesh.mask[k] >>> 24) >= 128)
                {
                    float luma = luma(mesh.mask[k], fill);

                    lumaMin = Math.min(lumaMin, luma);
                    lumaMax = Math.max(lumaMax, luma);
                }
            }
        }

        /* How much the brightness of this glyph has to say: none for a single colour (the noise
         * alone then spreads it over the whole run), all of it once its shades are far apart */
        float contrast = Math.max(0F, Math.min(1F, (lumaMax - lumaMin) / 0.5F));
        int h = mesh.maskHeight;

        /* Which pixels are still there, worked out before any is drawn: with depth, a pixel's sides
         * are drawn only where its neighbour has gone (or never was) */
        if (alive.length < w * h)
        {
            alive = new byte[w * h];
        }

        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                int argb = mesh.mask[j * w + i];

                alive[j * w + i] = 0;

                if ((argb >>> 24) < 128)
                {
                    continue;
                }

                float lx = i * unit - mesh.maskMargin;
                float ly = j * unit - mesh.maskMargin;
                float value;

                if (mode == DISSOLVE_PERLIN)
                {
                    value = (fbm((worldX + lx) * frequency, (worldY + ly) * frequency) - 0.2F) / 0.6F;
                }
                else if (mode == DISSOLVE_LUMA)
                {
                    /* The brightest burn first, by the glyph's own shades as the fill colours them. Text
                     * is mostly one colour: taken as it was (luma of the raw pixel against white),
                     * every pixel had the same brightness and the whole glyph was gone a fifth of the
                     * way in, the rest of the run showing nothing */
                    float noise = Math.max(0F, Math.min(1F, (fbm((worldX + lx) * frequency, (worldY + ly) * frequency) - 0.2F) / 0.6F));
                    float shade = lumaMax > lumaMin ? (luma(argb, fill) - lumaMin) / (lumaMax - lumaMin) : 0.5F;

                    value = noise + ((1F - shade) * 0.7F + noise * 0.3F - noise) * contrast;
                }
                else
                {
                    value = start + lx / Math.max(span, 1F);
                }

                value = Math.max(0F, Math.min(0.999F, value));

                if (value < threshold - edge)
                {
                    continue;
                }

                alive[j * w + i] = (byte) (value < threshold ? 2 : 1);
            }
        }

        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                byte state = alive[j * w + i];

                if (state == 0)
                {
                    continue;
                }

                int argb = mesh.mask[j * w + i];
                float lx = dx + i * unit - mesh.maskMargin;
                float ly = dy + j * unit - mesh.maskMargin;
                boolean burning = state == 2;
                float pr = burning ? burn.r : ((argb >> 16) & 0xFF) / 255F * fill.r * brightness;
                float pg = burning ? burn.g : ((argb >> 8) & 0xFF) / 255F * fill.g * brightness;
                float pb = burning ? burn.b : (argb & 0xFF) / 255F * fill.b * brightness;
                /* The burn is a colour, not a fade: an alpha saved with it (a transparent one, from
                 * the picker that had none) must not hide the burning edge */
                float pa = alpha;
                float x2 = lx + unit;
                float y2 = ly + unit;

                /* The front (and back) of a run of pixels in the same state and colour is one face:
                 * finer pixels must not mean as many more faces */
                if (i == 0 || alive[j * w + i - 1] != state || mesh.mask[j * w + i - 1] != argb)
                {
                    int end = i + 1;

                    while (end < w && alive[j * w + end] == state && mesh.mask[j * w + end] == argb)
                    {
                        end++;
                    }

                    float rx = dx + end * unit - mesh.maskMargin;

                    quad(builder, matrix, lx, ly, 0F, rx, ly, 0F, rx, y2, 0F, lx, y2, 0F, pr, pg, pb, pa);

                    if (depth > 0F)
                    {
                        quad(builder, matrix, lx, ly, -depth, rx, ly, -depth, rx, y2, -depth, lx, y2, -depth, pr * 0.55F, pg * 0.55F, pb * 0.55F, pa);
                    }
                }

                if (depth <= 0F)
                {
                    continue;
                }

                /* The text's depth stays while it burns: each pixel left is a little block, its
                 * sides where nothing is next to it (the way the solid body shades) */
                float z = -depth;

                if (!isAlive(i - 1, j, w, h))
                {
                    quad(builder, matrix, lx, ly, 0F, lx, y2, 0F, lx, y2, z, lx, ly, z, pr * 0.65F, pg * 0.65F, pb * 0.65F, pa);
                }

                if (!isAlive(i + 1, j, w, h))
                {
                    quad(builder, matrix, x2, ly, 0F, x2, y2, 0F, x2, y2, z, x2, ly, z, pr * 0.78F, pg * 0.78F, pb * 0.78F, pa);
                }

                if (!isAlive(i, j - 1, w, h))
                {
                    quad(builder, matrix, lx, ly, 0F, x2, ly, 0F, x2, ly, z, lx, ly, z, pr * 0.9F, pg * 0.9F, pb * 0.9F, pa);
                }

                if (!isAlive(i, j + 1, w, h))
                {
                    quad(builder, matrix, lx, y2, 0F, x2, y2, 0F, x2, y2, z, lx, y2, z, pr * 0.5F, pg * 0.5F, pb * 0.5F, pa);
                }
            }
        }
    }

    /** Per pixel of the glyph being dissolved: 0 gone, 1 there, 2 burning. Render thread only, reused. */
    private static byte[] alive = new byte[0];

    private static boolean isAlive(int i, int j, int w, int h)
    {
        return i >= 0 && j >= 0 && i < w && j < h && alive[j * w + i] != 0;
    }

    /** The brightness of a glyph pixel as it is drawn: its colour times the fill. */
    private static float luma(int argb, Color fill)
    {
        float r = ((argb >> 16) & 0xFF) / 255F * fill.r;
        float g = ((argb >> 8) & 0xFF) / 255F * fill.g;
        float b = (argb & 0xFF) / 255F * fill.b;

        return 0.299F * r + 0.587F * g + 0.114F * b;
    }

    /* Shatter */

    /**
     * A glyph as a pile of small cubes that fly apart: each takes its own direction, drifts, falls,
     * spins, and shrinks away to nothing as the progress reaches 1. At 0 the cubes tile the glyph
     * exactly. Cubes start a little apart from each other, so it is not one blast.
     *
     * @param thickness the depth of the cubes, in text units
     * @param spread how far, in text units, a cube flies by the end
     * @param seed tells one glyph's cubes from another's
     * @param temp a matrix to work in
     */
    public static void shatter(VertexConsumer builder, TextDepthMesh mesh, Matrix4f matrix, float dx, float dy, float thickness, Color fill, float alpha, float brightness, float progress, float spread, int seed, Matrix4f temp)
    {
        int w = mesh.maskWidth;
        int h = mesh.maskHeight;
        int solid = 0;

        for (int i = 0; i < mesh.mask.length; i++)
        {
            solid += (mesh.mask[i] >>> 24) >= 128 ? 1 : 0;
        }

        /* A glyph is never more than a few dozen cubes, however fine its pixels */
        int cell = Math.max(1, (int) Math.round(Math.sqrt(solid / 48D)));
        float unit = 1F / mesh.maskScale;
        float half = cell * unit / 2F;

        for (int cy = 0; cy < h; cy += cell)
        {
            for (int cx = 0; cx < w; cx += cell)
            {
                int color = 0;
                boolean any = false;

                for (int j = cy; j < Math.min(h, cy + cell) && !any; j++)
                {
                    for (int i = cx; i < Math.min(w, cx + cell); i++)
                    {
                        int argb = mesh.mask[j * w + i];

                        if ((argb >>> 24) >= 128)
                        {
                            color = argb;
                            any = true;

                            break;
                        }
                    }
                }

                if (!any)
                {
                    continue;
                }

                float delay = random(seed, cx, cy, 4) * 0.35F;
                float local = Math.max(0F, Math.min(1F, (progress - delay) / 0.65F));
                float scale = 1F - local * local;

                if (scale <= 0.001F)
                {
                    continue;
                }

                float fx = random(seed, cx, cy, 0) * 2F - 1F;
                float fy = random(seed, cx, cy, 1) * 2F - 1F - 0.3F;
                float fz = random(seed, cx, cy, 2) * 2F - 1F;
                float centerX = cx * unit - mesh.maskMargin + half;
                float centerY = cy * unit - mesh.maskMargin + half;

                temp.set(matrix).translate(
                    dx + centerX + fx * spread * local,
                    dy + centerY + fy * spread * local + spread * 1.2F * local * local,
                    -thickness / 2F + fz * spread * local
                );
                temp.rotateZ((random(seed, cx, cy, 3) * 2F - 1F) * 3.5F * local);
                temp.rotateX((random(seed, cx, cy, 5) * 2F - 1F) * 3.5F * local);
                temp.scale(scale);

                float r = ((color >> 16) & 0xFF) / 255F * fill.r * brightness;
                float g = ((color >> 8) & 0xFF) / 255F * fill.g * brightness;
                float b = (color & 0xFF) / 255F * fill.b * brightness;
                float a = alpha * (1F - local * local * local * local);
                float hz = thickness / 2F;

                box(builder, temp, half, hz, r, g, b, a);
            }
        }
    }

    /** A box around the origin, each face lit by the way it faces. */
    private static void box(VertexConsumer builder, Matrix4f m, float h, float hz, float r, float g, float b, float a)
    {
        quad(builder, m, -h, -h, hz, h, -h, hz, h, h, hz, -h, h, hz, r, g, b, a);
        quad(builder, m, -h, -h, -hz, h, -h, -hz, h, h, -hz, -h, h, -hz, r * 0.55F, g * 0.55F, b * 0.55F, a);
        quad(builder, m, -h, -h, hz, h, -h, hz, h, -h, -hz, -h, -h, -hz, r * 0.9F, g * 0.9F, b * 0.9F, a);
        quad(builder, m, -h, h, hz, h, h, hz, h, h, -hz, -h, h, -hz, r * 0.5F, g * 0.5F, b * 0.5F, a);
        quad(builder, m, -h, -h, hz, -h, h, hz, -h, h, -hz, -h, -h, -hz, r * 0.65F, g * 0.65F, b * 0.65F, a);
        quad(builder, m, h, -h, hz, h, h, hz, h, h, -hz, h, -h, -hz, r * 0.78F, g * 0.78F, b * 0.78F, a);
    }

    private static void quad(VertexConsumer builder, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        r = Math.min(1F, r);
        g = Math.min(1F, g);
        b = Math.min(1F, b);
        a = Math.max(0F, Math.min(1F, a));

        builder.vertex(m, x1, y1, z1).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(m, x2, y2, z2).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(m, x3, y3, z3).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(m, x1, y1, z1).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(m, x3, y3, z3).color(r, g, b, a)/*$ nx*/.next()/**/;
        builder.vertex(m, x4, y4, z4).color(r, g, b, a)/*$ nx*/.next()/**/;
    }
}
