package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.ModelViewStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
//? if >=1.21
/*import net.minecraft.client.util.BufferAllocator;*/
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A solid, one-piece 3D body for a piece of text: the glyphs are drawn once into a scratch
 * framebuffer, the pixels that came out opaque are read back, and the faces are built from that
 * mask the way {@link mchorse.bbs_mod.graphics.texture.TextureExtruder} builds them from a texture -
 * a face front and back, and a wall wherever a solid pixel has an empty one beside it. Runs of
 * pixels are merged, so there are no seams between the pixels and no slices along the depth.
 *
 * <p>The mesh is flat data in the text's own units (x right, y down, in text pixels, origin at the
 * top left of the text) with depth normalized: the front is at 0 and the back at -1, so one mesh
 * serves any thickness. It is cached by what it was made from, so only the first frame a piece of
 * text is seen pays for the scratch drawing.</p>
 *
 * <p>If the scratch drawing ever fails, {@link #get} reports it once and answers null from then on,
 * and the caller falls back to something that needs no pixels.</p>
 */
public class TextDepthMesh
{
    /** Floats per face: four corners (x, y, z), the colour (r, g, b) and how lit the face is. */
    public static final int STRIDE = 16;

    private static final float SHADE_FRONT = 1F;
    private static final float SHADE_BACK = 0.55F;
    private static final float SHADE_TOP = 0.9F;
    private static final float SHADE_BOTTOM = 0.5F;
    private static final float SHADE_LEFT = 0.65F;
    private static final float SHADE_RIGHT = 0.78F;

    private static final int MAX_SIDE = 2048;

    private static final int MIN_DETAIL = 4;
    private static final int MAX_DETAIL = 16;
    private static final int SMOOTH_HEIGHT = 96;

    /**
     * How many mask pixels (and face floats, counted alike) the cache holds at most. The masks are
     * kept for the effects, and the finer drawing of small text makes them big: by count alone, 256
     * meshes of a long line could hold hundreds of megabytes.
     */
    private static final long CACHE_BUDGET = 24_000_000L;
    private static long cached;

    private static final Map<String, TextDepthMesh> CACHE = new LinkedHashMap<>(64, 0.75F, true)
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TextDepthMesh> eldest)
        {
            /* The least recently used go until the rest fits (always keeping the one just added) */
            java.util.Iterator<Map.Entry<String, TextDepthMesh>> iterator = this.entrySet().iterator();

            while ((cached > CACHE_BUDGET || this.size() > 256) && this.size() > 1 && iterator.hasNext())
            {
                cached -= iterator.next().getValue().weight();
                iterator.remove();
            }

            return false;
        }
    };

    private static boolean failed;

    public final float[] faces;
    public final int count;

    /** The pixels the faces came from, ARGB, first row on top: width, height, pixels per text unit, and the margin (in text units) around the text. */
    public final int[] mask;
    public final int maskWidth;
    public final int maskHeight;
    public final int maskScale;
    public final int maskMargin;

    private TextDepthMesh(float[] faces, int[] mask, int maskWidth, int maskHeight, int maskScale, int maskMargin)
    {
        this.faces = faces;
        this.count = faces.length / STRIDE;
        this.mask = mask;
        this.maskWidth = maskWidth;
        this.maskHeight = maskHeight;
        this.maskScale = maskScale;
        this.maskMargin = maskMargin;
    }

    public static void clear()
    {
        CACHE.clear();
        cached = 0L;
    }

    /** What this mesh costs the cache. */
    private long weight()
    {
        return (long) this.mask.length + this.faces.length;
    }

    /**
     * The mesh of {@code text}, drawn the way {@code renderer} draws it. {@code key} stands for the
     * font and the text together and must change whenever either does.
     *
     * @param smooth whether the glyphs are outlines worth sampling finer than a text pixel
     * @return the mesh, or null when it cannot be made
     */
    public static TextDepthMesh get(String key, TextRenderer renderer, String text, float width, int fontHeight, boolean smooth)
    {
        return get(key, renderer, text, width, fontHeight, smooth ? detail(fontHeight) : 1);
    }

    /**
     * The same, drawn at exactly {@code detail} pixels per text unit - for the effects, which work
     * per pixel and want their own fineness (see {@link mchorse.bbs_mod.forms.renderers.LabelFormRenderer}).
     * {@code key} must tell the detail apart too.
     */
    public static TextDepthMesh get(String key, TextRenderer renderer, String text, float width, int fontHeight, int detail)
    {
        if (failed)
        {
            return null;
        }

        TextDepthMesh mesh = CACHE.get(key);

        if (mesh != null)
        {
            return mesh;
        }

        try
        {
            mesh = build(renderer, text, width, fontHeight, Math.max(1, detail));
        }
        catch (Throwable e)
        {
            failed = true;

            System.err.println("[BBS] Solid text depth failed and is switched off for this session:");
            e.printStackTrace();

            return null;
        }

        cached += mesh.weight();
        CACHE.put(key, mesh);

        return mesh;
    }

    /**
     * Pixels per text unit for an outline font: the text is drawn this many times bigger and the
     * mesh made from that, then laid back out at the text's own size (a pixel is 1 / detail of a
     * unit). Small text used to be drawn at three pixels a unit - a size 5 font a dozen pixels
     * tall - and its walls came out as big steps. Now at least {@link #MIN_DETAIL}, and enough for
     * the text to be {@link #SMOOTH_HEIGHT} pixels tall, up to {@link #MAX_DETAIL}. The atlas the
     * glyphs come from is rasterized finer the smaller the font, so there is detail to draw.
     */
    private static int detail(int fontHeight)
    {
        int wanted = (int) Math.ceil(SMOOTH_HEIGHT / (float) Math.max(fontHeight, 1));

        return Math.max(MIN_DETAIL, Math.min(MAX_DETAIL, wanted));
    }

    /** Whether making a mesh has failed, for a caller that wants to know before it commits. */
    public static boolean hasFailed()
    {
        return failed;
    }

    /**
     * The width and the font height are only the layout's idea of the text: a TrueType glyph sits
     * on the game's fixed baseline (7 units down, whatever the size), so a big font reaches well
     * above and below that box, and slanted or swashed letters past their advance. The scratch
     * canvas therefore starts with a wide margin, and whenever something was drawn on its very
     * border (i.e. cut off by it) it is drawn again on a bigger one.
     */
    private static TextDepthMesh build(TextRenderer renderer, String text, float width, int fontHeight, int detail)
    {
        int margin = 2 + fontHeight / 2;

        for (int attempt = 0; ; attempt++)
        {
            int units = (int) Math.ceil(width) + margin * 2;
            int unitsH = fontHeight + margin * 2;
            int scale = detail;

            while (scale > 1 && (units * scale > MAX_SIDE || unitsH * scale > MAX_SIDE))
            {
                scale--;
            }

            int pw = Math.max(1, Math.min(units * scale, MAX_SIDE));
            int ph = Math.max(1, Math.min(unitsH * scale, MAX_SIDE));

            int[] argb = rasterize(renderer, text, margin, pw, ph, scale);
            boolean maxed = units * scale >= MAX_SIDE || unitsH * scale >= MAX_SIDE;

            if (attempt >= 3 || maxed || !touchesBorder(argb, pw, ph))
            {
                return new TextDepthMesh(mesh(argb, pw, ph, scale, margin), argb, pw, ph, scale, margin);
            }

            margin = margin * 2 + fontHeight;
        }
    }

    /** Whether anything was drawn on the outermost pixels, i.e. the canvas cut the text off. */
    private static boolean touchesBorder(int[] argb, int w, int h)
    {
        for (int x = 0; x < w; x++)
        {
            if ((argb[x] >>> 24) != 0 || (argb[(h - 1) * w + x] >>> 24) != 0)
            {
                return true;
            }
        }

        for (int y = 0; y < h; y++)
        {
            if ((argb[y * w] >>> 24) != 0 || (argb[y * w + w - 1] >>> 24) != 0)
            {
                return true;
            }
        }

        return false;
    }

    /** The text, white on clear, as ARGB pixels in the order of the text: first row on top. */
    private static int[] rasterize(TextRenderer renderer, String text, int margin, int pw, int ph, int scale)
    {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        /* This runs in the middle of drawing a label, so all it touches is put back as it was, not to
         * a default: a scissor the interface left on (the film preview's) would cut the glyphs out of
         * the scratch buffer - and with them the body, the dissolve and the shatter made from it -
         * and the texture unit, the depth test and the blending belong to the draw it interrupted. */
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int activeTexture = GlStateManager._getActiveTexture();

        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorter = RenderSystem.getVertexSorting();
        SimpleFramebuffer buffer = null;

        /* Off before the framebuffer is made: making it clears it, and a clear keeps to the scissor */
        if (scissor)
        {
            GlStateManager._disableScissorTest();
        }

        ModelViewStack.push();
        ModelViewStack.loadIdentity();
        RenderSystem.applyModelViewMatrix();

        try
        {
            buffer = new SimpleFramebuffer(pw, ph, false, MinecraftClient.IS_SYSTEM_MAC);
            buffer.setClearColor(0F, 0F, 0F, 0F);
            buffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            buffer.beginWrite(true);

            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, pw / (float) scale, ph / (float) scale, 0F, -100F, 100F), VertexSorter.BY_Z);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            //? if >=1.21 {
            /*BufferAllocator allocator = new BufferAllocator(2048);
            VertexConsumerProvider.Immediate provider = VertexConsumerProvider.immediate(allocator);
            *///?} else {
            VertexConsumerProvider.Immediate provider = VertexConsumerProvider.immediate(new BufferBuilder(2048));
            //?}

            renderer.draw(
                text, margin, margin, 0xFFFFFFFF, false, new Matrix4f(), provider,
                TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
            provider.draw();
            //? if >=1.21
            /*allocator.close();*/

            NativeImage image = new NativeImage(pw, ph, false);

            try
            {
                /* Read through unit 0, whatever unit the label was drawing with */
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                RenderSystem.bindTexture(buffer.getColorAttachment());
                image.loadFromTextureImage(0, false);

                int[] pixels = new int[pw * ph];

                /* The image comes out bottom row first, and in ABGR */
                for (int y = 0; y < ph; y++)
                {
                    for (int x = 0; x < pw; x++)
                    {
                        int abgr = image.getColor(x, ph - 1 - y);
                        int a = abgr >>> 24;
                        int r = abgr & 0xFF;
                        int g = (abgr >> 8) & 0xFF;
                        int b = (abgr >> 16) & 0xFF;

                        /* Blended onto clear black, the colour of a soft edge is pre-multiplied: undo it */
                        if (a > 0 && a < 255)
                        {
                            r = Math.min(255, r * 255 / a);
                            g = Math.min(255, g * 255 / a);
                            b = Math.min(255, b * 255 / a);
                        }

                        pixels[y * pw + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }

                return pixels;
            }
            finally
            {
                image.close();
            }
        }
        finally
        {
            if (buffer != null)
            {
                buffer.delete();
            }

            ModelViewStack.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorter);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            if (scissor)
            {
                GlStateManager._enableScissorTest();
            }

            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();

            RenderSystem.activeTexture(activeTexture);
        }
    }

    private static boolean solid(int[] argb, int w, int h, int x, int y)
    {
        return x >= 0 && y >= 0 && x < w && y < h && (argb[y * w + x] >>> 24) >= 128;
    }

    /** Same colour, give or take what a soft edge does to it - good enough to share a face. */
    private static boolean sameColor(int a, int b)
    {
        return ((a ^ b) & 0xF0F0F0) == 0;
    }

    private static float[] mesh(int[] argb, int w, int h, int scale, int margin)
    {
        List<Float> out = new ArrayList<>();
        float unit = 1F / scale;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                if (!solid(argb, w, h, x, y))
                {
                    continue;
                }

                int color = argb[y * w + x];

                /* Front and back: a run along the row of pixels of one colour */
                int end = x + 1;

                while (end < w && solid(argb, w, h, end, y) && sameColor(argb[y * w + end], color))
                {
                    end++;
                }

                run(out, x * unit - margin, y * unit - margin, end * unit - margin, (y + 1) * unit - margin, 0F, color, SHADE_FRONT);
                run(out, x * unit - margin, y * unit - margin, end * unit - margin, (y + 1) * unit - margin, -1F, color, SHADE_BACK);

                x = end - 1;
            }
        }

        /* Walls along the rows: the top and bottom edges, merged along the run that has them */
        for (int y = 0; y < h; y++)
        {
            for (int pass = 0; pass < 2; pass++)
            {
                int dy = pass == 0 ? -1 : 1;

                for (int x = 0; x < w; x++)
                {
                    if (!solid(argb, w, h, x, y) || solid(argb, w, h, x, y + dy))
                    {
                        continue;
                    }

                    int color = argb[y * w + x];
                    int end = x + 1;

                    while (end < w && solid(argb, w, h, end, y) && !solid(argb, w, h, end, y + dy) && sameColor(argb[y * w + end], color))
                    {
                        end++;
                    }

                    float edge = (pass == 0 ? y : y + 1) * unit - margin;

                    wall(out, x * unit - margin, edge, end * unit - margin, edge, color, pass == 0 ? SHADE_TOP : SHADE_BOTTOM);

                    x = end - 1;
                }
            }
        }

        /* Walls along the columns: the left and right edges */
        for (int x = 0; x < w; x++)
        {
            for (int pass = 0; pass < 2; pass++)
            {
                int dx = pass == 0 ? -1 : 1;

                for (int y = 0; y < h; y++)
                {
                    if (!solid(argb, w, h, x, y) || solid(argb, w, h, x + dx, y))
                    {
                        continue;
                    }

                    int color = argb[y * w + x];
                    int end = y + 1;

                    while (end < h && solid(argb, w, h, x, end) && !solid(argb, w, h, x + dx, end) && sameColor(argb[end * w + x], color))
                    {
                        end++;
                    }

                    float edge = (pass == 0 ? x : x + 1) * unit - margin;

                    wall(out, edge, y * unit - margin, edge, end * unit - margin, color, pass == 0 ? SHADE_LEFT : SHADE_RIGHT);

                    y = end - 1;
                }
            }
        }

        float[] faces = new float[out.size()];

        for (int i = 0; i < faces.length; i++)
        {
            faces[i] = out.get(i);
        }

        return faces;
    }

    /** A face across the text at depth z. */
    private static void run(List<Float> out, float x1, float y1, float x2, float y2, float z, int color, float shade)
    {
        face(out, x1, y1, z, x2, y1, z, x2, y2, z, x1, y2, z, color, shade);
    }

    /** A wall standing on the line from (x1, y1) to (x2, y2), reaching from the front to the back. */
    private static void wall(List<Float> out, float x1, float y1, float x2, float y2, int color, float shade)
    {
        face(out, x1, y1, 0F, x2, y2, 0F, x2, y2, -1F, x1, y1, -1F, color, shade);
    }

    private static void face(List<Float> out, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int color, float shade)
    {
        out.add(x1);
        out.add(y1);
        out.add(z1);
        out.add(x2);
        out.add(y2);
        out.add(z2);
        out.add(x3);
        out.add(y3);
        out.add(z3);
        out.add(x4);
        out.add(y4);
        out.add(z4);
        out.add(((color >> 16) & 0xFF) / 255F);
        out.add(((color >> 8) & 0xFF) / 255F);
        out.add((color & 0xFF) / 255F);
        out.add(shade);
    }
}
