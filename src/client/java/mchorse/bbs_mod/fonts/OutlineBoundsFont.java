package mchorse.bbs_mod.fonts;

//? if <1.21 {
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.font.Font;
import net.minecraft.client.font.Glyph;
import net.minecraft.client.font.GlyphRenderer;
import net.minecraft.client.font.RenderableGlyph;
import net.minecraft.client.font.TrueTypeFont;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.stb.STBTTBitmap;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTVertex;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Function;

/**
 * A TrueType font laid out and rasterized exactly like the game's own ({@link TrueTypeFont}, which
 * it wraps and hands its lifetime to), except for the box each glyph is drawn into.
 *
 * <p>The game sizes that box with stb's {@code GetGlyphBitmapBox}, which for a TrueType outline is
 * the bounding box the font file states in the glyph's header - and stb rasterizes clipped to it.
 * Fonts made with care keep that box right; plenty of script and handwriting fonts don't, and their
 * swashes, tails and joining strokes run past it, so every such glyph came out cut along a straight
 * edge (the joins between script letters went missing in particular). Here the box is taken from
 * the outline's own points instead (the control points of the curves included, so it can only be
 * too big, never too small), never smaller than the stated one, and the glyph is rasterized into it
 * with stb's rasterizer directly.</p>
 *
 * <p>Since 1.21 the game uses FreeType, which measures the outline itself, so this is 1.20 only.</p>
 */
public class OutlineBoundsFont implements Font
{
    private final TrueTypeFont font;
    private final STBTTFontinfo info;
    private final float oversample;
    private final float scale;
    private final float ascent;

    public OutlineBoundsFont(TrueTypeFont font, STBTTFontinfo info, float size, float oversample)
    {
        this.font = font;
        this.info = info;
        this.oversample = oversample;

        /* What TrueTypeFont works out in its constructor, with no shift */
        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(info, size * oversample);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer ascent = stack.mallocInt(1);
            IntBuffer descent = stack.mallocInt(1);
            IntBuffer gap = stack.mallocInt(1);

            STBTruetype.stbtt_GetFontVMetrics(info, ascent, descent, gap);

            this.ascent = ascent.get(0) * this.scale;
        }
    }

    @Override
    public Glyph getGlyph(int codePoint)
    {
        try
        {
            return this.makeGlyph(codePoint);
        }
        catch (RuntimeException e)
        {
            /* Whatever the outline holds that this cannot read, the game's glyph still stands */
            return this.font.getGlyph(codePoint);
        }
    }

    private Glyph makeGlyph(int codePoint)
    {
        int index = STBTruetype.stbtt_FindGlyphIndex(this.info, codePoint);

        if (index == 0)
        {
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            IntBuffer advance = stack.mallocInt(1);
            IntBuffer bearing = stack.mallocInt(1);

            STBTruetype.stbtt_GetGlyphHMetrics(this.info, index, advance, bearing);
            STBTruetype.stbtt_GetGlyphBitmapBoxSubpixel(this.info, index, this.scale, this.scale, 0F, 0F, x0, y0, x1, y1);

            int[] box = {x0.get(0), y0.get(0), x1.get(0), y1.get(0)};
            float advanceWidth = advance.get(0) * this.scale;

            this.growToOutline(index, box);

            if (box[2] - box[0] <= 0 || box[3] - box[1] <= 0)
            {
                float empty = advanceWidth / this.oversample;

                return (Glyph.EmptyGlyph) () -> empty;
            }

            return new OutlineGlyph(index, box, advanceWidth, bearing.get(0) * this.scale);
        }
    }

    /** Widens the box (x0, y0, x1, y1 in pixels, y down) to every point of the glyph's outline. */
    private void growToOutline(int index, int[] box)
    {
        STBTTVertex.Buffer vertices = STBTruetype.stbtt_GetGlyphShape(this.info, index);

        if (vertices == null)
        {
            return;
        }

        try
        {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;

            for (int i = 0; i < vertices.remaining(); i++)
            {
                STBTTVertex vertex = vertices.get(i);
                int type = vertex.type();

                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxY = Math.max(maxY, vertex.y());

                if (type == STBTruetype.STBTT_vcurve || type == STBTruetype.STBTT_vcubic)
                {
                    minX = Math.min(minX, vertex.cx());
                    maxX = Math.max(maxX, vertex.cx());
                    minY = Math.min(minY, vertex.cy());
                    maxY = Math.max(maxY, vertex.cy());
                }

                if (type == STBTruetype.STBTT_vcubic)
                {
                    minX = Math.min(minX, vertex.cx1());
                    maxX = Math.max(maxX, vertex.cx1());
                    minY = Math.min(minY, vertex.cy1());
                    maxY = Math.max(maxY, vertex.cy1());
                }
            }

            if (minX > maxX)
            {
                return;
            }

            /* The font's y goes up, the bitmap's down */
            box[0] = Math.min(box[0], (int) Math.floor(minX * this.scale));
            box[1] = Math.min(box[1], (int) Math.floor(-maxY * this.scale));
            box[2] = Math.max(box[2], (int) Math.ceil(maxX * this.scale));
            box[3] = Math.max(box[3], (int) Math.ceil(-minY * this.scale));
        }
        finally
        {
            STBTruetype.stbtt_FreeShape(this.info, vertices);
        }
    }

    @Override
    public IntSet getProvidedGlyphs()
    {
        return this.font.getProvidedGlyphs();
    }

    @Override
    public void close()
    {
        this.font.close();
    }

    /** The same numbers TrueTypeFont's glyph keeps, worked out from the wider box. */
    private class OutlineGlyph implements Glyph
    {
        private final int index;
        private final int x0;
        private final int y0;
        private final int width;
        private final int height;
        private final float advance;
        private final float bearingX;
        private final float glyphAscent;

        public OutlineGlyph(int index, int[] box, float advance, float leftSideBearing)
        {
            this.index = index;
            this.x0 = box[0];
            this.y0 = box[1];
            this.width = box[2] - box[0];
            this.height = box[3] - box[1];
            this.advance = advance / oversample;
            /* As the game has it, side bearing and box edge both */
            this.bearingX = (leftSideBearing + box[0]) / oversample;
            this.glyphAscent = (ascent + box[1]) / oversample;
        }

        @Override
        public float getAdvance()
        {
            return this.advance;
        }

        @Override
        public GlyphRenderer bake(Function<RenderableGlyph, GlyphRenderer> function)
        {
            return function.apply(new RenderableGlyph()
            {
                @Override
                public int getWidth()
                {
                    return OutlineGlyph.this.width;
                }

                @Override
                public int getHeight()
                {
                    return OutlineGlyph.this.height;
                }

                @Override
                public float getOversample()
                {
                    return oversample;
                }

                @Override
                public float getBearingX()
                {
                    return OutlineGlyph.this.bearingX;
                }

                @Override
                public float getAscent()
                {
                    return OutlineGlyph.this.glyphAscent;
                }

                @Override
                public void upload(int x, int y)
                {
                    OutlineGlyph.this.upload(x, y);
                }

                @Override
                public boolean hasColor()
                {
                    return false;
                }
            });
        }

        /** Rasterizes the outline into its box and puts it in the atlas page bound right now, at (x, y). */
        private void upload(int x, int y)
        {
            int w = this.width;
            int h = this.height;
            ByteBuffer pixels = MemoryUtil.memCalloc(w * h);
            STBTTVertex.Buffer vertices = STBTruetype.stbtt_GetGlyphShape(info, this.index);

            /* The upload closes the image itself, as the game's glyphs have it */
            NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, w, h, false);
            boolean uploaded = false;

            try
            {
                if (vertices != null)
                {
                    try (STBTTBitmap bitmap = STBTTBitmap.calloc())
                    {
                        bitmap.w(w).h(h).stride(w).pixels(pixels);

                        /* What stbtt_MakeGlyphBitmapSubpixel does, only with this box's corner as the origin */
                        STBTruetype.stbtt_Rasterize(bitmap, 0.35F, vertices, scale, scale, 0F, 0F, this.x0, this.y0, true);
                    }
                }

                for (int j = 0; j < h; j++)
                {
                    for (int i = 0; i < w; i++)
                    {
                        image.setLuminance(i, j, pixels.get(j * w + i));
                    }
                }

                image.upload(0, x, y, 0, 0, w, h, false, true);
                uploaded = true;
            }
            finally
            {
                if (!uploaded)
                {
                    image.close();
                }

                if (vertices != null)
                {
                    STBTruetype.stbtt_FreeShape(info, vertices);
                }

                MemoryUtil.memFree(pixels);
            }
        }
    }
}
//?}
