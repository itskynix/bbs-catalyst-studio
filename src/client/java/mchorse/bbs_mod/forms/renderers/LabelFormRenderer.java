package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.Bufs;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.TextDepthMesh;
import mchorse.bbs_mod.forms.renderers.utils.TextFx;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import mchorse.bbs_mod.forms.renderers.utils.SliceClipVertexConsumer;
import mchorse.bbs_mod.forms.FormMasking;
import java.util.function.Function;
import org.joml.Vector4f;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class LabelFormRenderer extends FormRenderer<LabelForm>
{
    public static void fillQuad(VertexConsumer builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).texture(0F, 0F)/*$ nx*/.next()/**/;
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a).texture(0F, 0F)/*$ nx*/.next()/**/;
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a).texture(0F, 0F)/*$ nx*/.next()/**/;
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).texture(0F, 0F)/*$ nx*/.next()/**/;
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a).texture(0F, 0F)/*$ nx*/.next()/**/;
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a).texture(0F, 0F)/*$ nx*/.next()/**/;
    }

    /** How far apart (in text pixels) the slices of the layered depth are, and how many there may be at most. */
    private static final float LAYER_STEP = 0.2F;
    private static final int LAYERS_MAX = 64;

    /** How far (in text pixels) the solid body's front stands ahead of where the flat text would be. */
    private static final float FRONT_BIAS = 0.02F;

    /** The plane of the slice the label is being drawn through (in the space vertices reach the consumers in), or null */
    private Vector4f slicePlane;

    /** How many pixels tall the effects' pixels make the text at least, and how fine they get at most (per text unit) */
    private static final int FX_HEIGHT = 40;
    private static final int FX_MAX_DETAIL = 8;

    /** The font size the game's own font is drawn at as it is (LabelForm's default size) */
    private static final int VANILLA_SIZE = 9;

    public LabelFormRenderer(LabelForm form)
    {
        super(form);
    }

    /**
     * The label's own font, or the default one when it doesn't have (or can't load) one.
     * The scale says how many screen pixels a unit of the layout covers where it's about
     * to be drawn - see {@link FontManager#get(Link, int, float)}.
     */
    private FontRenderer getFont(float scale)
    {
        FontRenderer font = BBSModClient.getFonts().get(this.form.font.get(), this.form.fontSize.get(), scale);

        return font == null ? Batcher2D.getDefaultTextRenderer() : font;
    }

    private int getLineHeight(FontRenderer font)
    {
        int lineHeight = this.form.lineHeight.get();

        return lineHeight > 0 ? lineHeight : font.getLineHeight();
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int color = this.form.color.get().getARGBColor();
        String text = StringUtils.processColoredText(this.form.text.get());
        /* The interface draws a unit of the layout over as many pixels as it is scaled by. */
        FontRenderer font = this.getFont((float) MinecraftClient.getInstance().getWindow().getScaleFactor());
        FontRenderer previous = context.batcher.setFont(font);

        try
        {
            List<String> wrap = font.wrap(text, x2 - x1 - 4);

            int th = font.getHeight();
            int lineHeight = th + 4;
            int h = th + (wrap.size() - 1) * lineHeight;
            int y = (y2 + y1) / 2 - h / 2;

            for (String s : wrap)
            {
                context.batcher.textShadow(s, x1 + 2, y, color);

                y += lineHeight;
            }
        }
        finally
        {
            context.batcher.setFont(previous);
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        context.stack.push();

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = context.stack.peek().getPositionMatrix();
            Vector3f scale = Vectors.TEMP_3F;

            modelMatrix.getScale(scale);

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            modelMatrix.scale(scale);

            context.stack.peek().getNormalMatrix().identity();
        }

        FontRenderer font = this.getFont(FontManager.MAX_DETAIL);
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        float scale = 1F / 16F;
        int light = context.light;
        boolean occlusion = this.form.depthOcclusion.get();

        MatrixStackUtils.scaleStack(context.stack, scale, -scale, scale);

        /* The game's own font (no font picked, or the picked one failed to load) has one size: the
         * font size used to do nothing to it. It is scaled to the size instead, the default size
         * being the font as it is. A picked font is laid out at the size by FontManager. */
        if (font == Batcher2D.getDefaultTextRenderer())
        {
            float size = this.form.fontSize.get() / (float) VANILLA_SIZE;

            if (size > 0F && size != 1F)
            {
                MatrixStackUtils.scaleStack(context.stack, size, size, size);
            }
        }

        /* A 3D slice: text is drawn by programs that don't know the plane, so the glyphs and the
         * body are cut on the CPU. The vertices reach the consumers already through the stack, so
         * the plane is taken back from eye space only through the applied model-view. */
        Vector4f eyePlane = context.isPicking() ? null : FormMasking.getSlicePlane();
        Function<VertexConsumer, VertexConsumer> previousSubstitute = consumers.getSubstitute();

        this.slicePlane = eyePlane == null ? null : new Matrix4f(RenderSystem.getModelViewMatrix()).transpose().transform(eyePlane);

        if (this.slicePlane != null)
        {
            Vector4f plane = this.slicePlane;

            consumers.setSubstitute((buffer) -> new SliceClipVertexConsumer(previousSubstitute == null ? buffer : previousSubstitute.apply(buffer), plane, 4));
        }

        RenderSystem.disableCull();

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
            });

            light = 0;
        }

        /* The whole label (text + background) records as ONE deferred group: its parts rely on
         * each other's depth (glyphs over the background quad), so they replay together in
         * original order, while the group as a whole sorts against other translucent forms. */
        boolean grouped = !context.isPicking() && FormTranslucentQueue.isActive();

        if (!context.isPicking() && !grouped)
        {
            /* Drawn on the spot: with the depth test off the text sits on top of what is already there,
             * and with it on it is pulled a little toward the camera against flicker on a surface. */
            if (occlusion)
            {
                RenderSystem.enablePolygonOffset();
                RenderSystem.polygonOffset(-1F, -2F);
            }
            else
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) -> RenderSystem.disableDepthTest());
                RenderSystem.disableDepthTest();
            }
        }

        if (grouped)
        {
            Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.beginGroup(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin), false);
            FormTranslucentQueue.configureGroup(!occlusion, occlusion);
        }

        if (this.isAdvanced())
        {
            this.renderAdvanced(context, consumers, font, light);
        }
        else if (this.form.max.get() <= 10)
        {
            this.renderString(context, consumers, font, light);
        }
        else
        {
            this.renderLimitedString(context, consumers, font, light);
        }

        if (grouped)
        {
            FormTranslucentQueue.endGroup();
        }

        CustomVertexConsumerProvider.clearRunnables();

        if (this.slicePlane != null)
        {
            consumers.setSubstitute(previousSubstitute);
            this.slicePlane = null;
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();

        context.stack.pop();
    }

    private void renderString(FormRenderingContext context, CustomVertexConsumerProvider consumers, FontRenderer font, int light)
    {
        TextRenderer renderer = font.getRenderer();
        String content = StringUtils.processColoredText(this.form.text.get());
        float transition = context.getTransition();
        int w = renderer.getWidth(content) - 1;
        int h = font.getHeight();
        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());

        Color shadowColor = this.form.shadowColor.get().copy();
        Color color = new Color().set(context.color, true);

        FormColorBlend.blend(color, this.form.color.get());
        /* Text is a flat fill, so the CPU mix is exactly what the overlay texture would do. */
        OverlayBlend.apply(color, this.form.overlayColor.get());
        shadowColor.mul(context.color);

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);
            renderer.draw(
                content,
                x + this.form.shadowX.get(),
                y + this.form.shadowY.get(),
                shadowColor.getARGBColor(), false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                light
            );
            context.stack.pop();
        }

        renderer.draw(
            content,
            x,
            y,
            color.getARGBColor(), false,
            context.stack.peek().getPositionMatrix(),
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            light
        );

        RenderSystem.enableDepthTest();

        consumers.draw();

        this.renderShadow(context, x, y, w, h);
    }

    private void renderLimitedString(FormRenderingContext context, CustomVertexConsumerProvider consumers, FontRenderer font, int light)
    {
        TextRenderer renderer = font.getRenderer();
        int lineHeight = this.getLineHeight(font);
        float transition = context.getTransition();
        int w = 0;
        int h = font.getHeight();
        String content = StringUtils.processColoredText(this.form.text.get());
        List<String> lines = FontRenderer.wrap(renderer, content, this.form.max.get());

        if (lines.size() <= 1)
        {
            this.renderString(context, consumers, font, light);

            return;
        }

        for (int i = 0; i < lines.size(); i++)
        {
            lines.set(i, lines.get(i).trim());
        }

        for (String line : lines)
        {
            w = Math.max(renderer.getWidth(line) - 1, w);
            h += lineHeight;
        }

        h -= lineHeight;

        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());
        int y2 = y;

        Color shadowColor = this.form.shadowColor.get().copy();

        shadowColor.mul(context.color);

        if (shadowColor.a > 0)
        {
            context.stack.push();
            context.stack.translate(0F, 0F, -0.1F);

            for (String line : lines)
            {
                int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

                renderer.draw(
                    line,
                    x2 + this.form.shadowX.get(),
                    y2 + this.form.shadowY.get(),
                    shadowColor.getARGBColor(), false,
                    context.stack.peek().getPositionMatrix(),
                    consumers,
                    TextRenderer.TextLayerType.NORMAL,
                    0,
                    light
                );

                y2 += lineHeight;
            }

            context.stack.pop();

            y2 = y;
        }

        Color cColor = new Color().set(context.color, true);

        FormColorBlend.blend(cColor, this.form.color.get());
        OverlayBlend.apply(cColor, this.form.overlayColor.get());

        int color = cColor.getARGBColor();

        for (String line : lines)
        {
            int x2 = x + (this.form.anchorLines.get() ? (int) ((w - renderer.getWidth(line)) * this.form.anchorX.get()) : 0);

            renderer.draw(
                line,
                x2,
                y2,
                color, false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                light
            );

            y2 += lineHeight;
        }

        consumers.draw();

        RenderSystem.enableDepthTest();

        this.renderShadow(context, x, y, w, h);
    }

    /* Advanced text: reveal, bend, depth and outline. Anything left at its default takes the plain path above. */

    /** One drawn piece of the text: a whole line, or one character with the formatting in force at it. */
    private static class Glyph
    {
        final String text;
        final float x;
        final float width;
        final int line;
        final int reveal;
        final float lineCenter;
        final float lineWidth;

        Glyph(String text, float x, float width, int line, int reveal, float lineCenter, float lineWidth)
        {
            this.text = text;
            this.x = x;
            this.width = width;
            this.line = line;
            this.reveal = reveal;
            this.lineCenter = lineCenter;
            this.lineWidth = lineWidth;
        }
    }

    private boolean isAdvanced()
    {
        boolean revealing = this.form.animationMode.get() != LabelForm.ANIMATION_NONE && this.form.revealProgress.get() != 1F;

        return revealing
            || this.form.extrude.get() > 0F
            || this.form.arc.get() != 0F
            || this.form.outlineWidth.get() > 0F
            || this.form.fillOpacity.get() < 1F
            || this.form.dissolveProgress.get() > 0F
            || this.form.shatterProgress.get() > 0F;
    }

    private static int withAlpha(Color color, float alpha)
    {
        return new Color(color.r, color.g, color.b, Math.max(0F, Math.min(1F, color.a * alpha))).getARGBColor();
    }

    private void renderAdvanced(FormRenderingContext context, CustomVertexConsumerProvider consumers, FontRenderer font, int light)
    {
        TextRenderer renderer = font.getRenderer();
        int mode = this.form.animationMode.get();
        float arc = this.form.arc.get();
        int lineHeight = this.getLineHeight(font);
        boolean occlusion = this.form.depthOcclusion.get();
        int fontHeight = font.getHeight();
        String content = StringUtils.processColoredText(this.form.text.get());
        List<String> lines = new ArrayList<>();

        if (this.form.max.get() > 10)
        {
            for (String line : FontRenderer.wrap(renderer, content, this.form.max.get()))
            {
                lines.add(line.trim());
            }
        }

        if (lines.isEmpty())
        {
            lines.add(content);
        }

        int w = 0;

        for (String line : lines)
        {
            w = Math.max(renderer.getWidth(line) - 1, w);
        }

        int h = fontHeight + (lines.size() - 1) * lineHeight;
        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());

        /* Cut the text into glyphs. Characters only when something needs them one by one. */
        boolean perCharacter = arc != 0F || mode != LabelForm.ANIMATION_NONE;
        List<Glyph> glyphs = new ArrayList<>();
        int characters = 0;
        int words = 0;

        for (int li = 0; li < lines.size(); li++)
        {
            String line = lines.get(li);
            float lineWidth = renderer.getWidth(line);
            float lineX = x + (this.form.anchorLines.get() ? (w - lineWidth) * this.form.anchorX.get() : 0F);
            float lineCenter = lineX + lineWidth / 2F;

            if (!perCharacter)
            {
                glyphs.add(new Glyph(line, lineX, lineWidth, li, 0, lineCenter, lineWidth));

                continue;
            }

            /* The formatting codes in force, repeated in front of each character: the game reads them in
             * order, so a character drawn alone with them in front of it looks as it did in the line. */
            StringBuilder active = new StringBuilder();
            boolean inWord = false;

            for (int i = 0; i < line.length(); i++)
            {
                char c = line.charAt(i);

                if (c == '§' && i + 1 < line.length())
                {
                    char code = line.charAt(i + 1);

                    if (code == 'r' || code == 'R')
                    {
                        active.setLength(0);
                    }
                    else
                    {
                        active.append(c).append(code);
                    }

                    i++;

                    continue;
                }

                if (Character.isWhitespace(c))
                {
                    inWord = false;

                    continue;
                }

                if (!inWord)
                {
                    inWord = true;
                    words++;
                }

                characters++;

                String piece = active.toString() + c;
                int reveal = mode == LabelForm.ANIMATION_CHARACTER ? characters - 1 : (mode == LabelForm.ANIMATION_WORD ? words - 1 : (mode == LabelForm.ANIMATION_LINE ? li : 0));

                glyphs.add(new Glyph(piece, lineX + renderer.getWidth(line.substring(0, i)), renderer.getWidth(piece), li, reveal, lineCenter, lineWidth));
            }
        }

        int units = mode == LabelForm.ANIMATION_CHARACTER ? characters : (mode == LabelForm.ANIMATION_WORD ? words : (mode == LabelForm.ANIMATION_LINE ? lines.size() : 1));
        float progress = this.form.revealProgress.get();
        float stagger = this.form.stagger.get();
        float window = Math.max(1F - stagger, 0.01F);

        Color fill = new Color().set(context.color, true);

        FormColorBlend.blend(fill, this.form.color.get());
        OverlayBlend.apply(fill, this.form.overlayColor.get());

        Color side = new Color(fill.r * 0.6F, fill.g * 0.6F, fill.b * 0.6F, fill.a);
        Color outline = this.form.outlineColor.get().copy();

        /* Picked without alpha, the colour used to be stored with none at all - and the outline went
         * invisible for good. How see-through it is, is the opacity slider's business. */
        outline.a = 1F;
        Color shadow = this.form.shadowColor.get().copy();

        outline.mul(context.color);
        shadow.mul(context.color);

        float fillOpacity = this.form.fillOpacity.get();
        float outlineOpacity = this.form.outlineOpacity.get();
        float outlineWidth = this.form.outlineWidth.get();
        float depth = this.form.extrude.get() * fontHeight * 0.25F;
        /* Slices close enough that they read as one side rather than a comb of copies, even at a slant */
        int layers = depth > 0F ? Math.max(1, Math.min(LAYERS_MAX, (int) Math.ceil(depth / LAYER_STEP))) : 0;
        int ring = outlineWidth <= 0F || outline.a * outlineOpacity <= 0F ? 0 : (outlineWidth <= 1.5F ? 8 : 16);
        float back = -0.3F - depth;

        Matrix4f base = context.stack.peek().getPositionMatrix();
        Matrix4f matrix = new Matrix4f();

        /* Depth: one solid body per glyph instead of stacked slices. It needs the glyph pixels, so it is
         * not available while picking, nor after the scratch drawing has failed - slices take over. */
        boolean solid = this.form.extrudeMode.get() == LabelForm.EXTRUDE_DEPTH && depth > 0F && !context.isPicking();
        Map<String, TextDepthMesh> meshes = new HashMap<>();
        boolean smooth = this.form.font.get() != null;

        if (solid)
        {
            for (Glyph glyph : glyphs)
            {
                String key = this.form.font.get() + "|" + this.form.fontSize.get() + "|" + glyph.text;
                TextDepthMesh mesh = meshes.containsKey(glyph.text) ? meshes.get(glyph.text) : TextDepthMesh.get(key, renderer, glyph.text, glyph.width, fontHeight, smooth);

                if (mesh == null)
                {
                    solid = false;

                    break;
                }

                meshes.put(glyph.text, mesh);
            }
        }

        /* Dissolve and shatter work on the pixels of the glyphs too (taken at one pixel to the text unit - the
         * effect is as coarse as that anyway). They take the place of the fill, the slices and the solid body;
         * if the pixels cannot be had, the text is drawn as it would be without them. */
        float shatterProgress = this.form.shatterProgress.get();
        float dissolveProgress = this.form.dissolveProgress.get();
        boolean shattering = shatterProgress > 0F;
        boolean dissolving = !shattering && dissolveProgress > 0F;
        Map<String, TextDepthMesh> fxMeshes = new HashMap<>();
        /* The burning and the breaking work pixel by pixel: one pixel to a text unit made a small
         * outline font burn away in blocks a quarter of a letter wide. An outline font is drawn
         * finer for them, enough for the text to be about FX_HEIGHT pixels tall (the game's pixel
         * font stays at its own pixels, which are its shape) */
        int fxDetail = smooth ? Math.max(1, Math.min(FX_MAX_DETAIL, (int) Math.ceil(FX_HEIGHT / (float) Math.max(fontHeight, 1)))) : 1;

        if ((shattering || dissolving) && !context.isPicking())
        {
            for (Glyph glyph : glyphs)
            {
                String key = this.form.font.get() + "|" + this.form.fontSize.get() + "|fx" + fxDetail + "|" + glyph.text;
                TextDepthMesh mesh = fxMeshes.containsKey(glyph.text) ? fxMeshes.get(glyph.text) : TextDepthMesh.get(key, renderer, glyph.text, glyph.width, fontHeight, fxDetail);

                if (mesh == null)
                {
                    fxMeshes.clear();

                    break;
                }

                fxMeshes.put(glyph.text, mesh);
            }
        }

        boolean fx = !fxMeshes.isEmpty();
        float fade = fx ? 1F - Math.max(0F, Math.min(1F, shattering ? shatterProgress : dissolveProgress)) : 1F;
        float thickness = depth > 0F ? depth : fontHeight * 0.15F;
        Matrix4f scratch = new Matrix4f();
        int glyphIndex = 0;

        if (fx)
        {
            solid = false;
            layers = 0;
        }

        Bufs.Owned bodyOwner = solid || fx ? new Bufs.Owned(4096, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR) : null;
        BufferBuilder body = bodyOwner == null ? null : bodyOwner.builder();
        /* What the body and the effects are written through: cut by the slice, when there is one */
        VertexConsumer bodyOut = body == null || this.slicePlane == null ? body : new SliceClipVertexConsumer(body, this.slicePlane, 3);
        float brightness = 0.2F + 0.8F * Math.max(LightmapTextureManager.getBlockLightCoordinates(light), LightmapTextureManager.getSkyLightCoordinates(light)) / 15F;

        for (Glyph glyph : glyphs)
        {
            float u = 1F;
            float opacity = 1F;

            if (mode != LabelForm.ANIMATION_NONE)
            {
                float start = units > 1 ? stagger * glyph.reveal / (float) units : 0F;

                float local = (progress - start) / window;

                /* Not yet its turn (or no number at all): nothing of it is drawn, not even in its place */
                if (!(local > 0F))
                {
                    continue;
                }

                /* Without a spring: the glyph's own stretch of the progress runs from 0 to exactly 1 by the time
                 * the progress is 1, so every glyph sits in its place there; what the progress carries past 1
                 * is added on top as it is, so an overshooting curve moves them all past their place and back. */
                float spring = this.form.revealOvershoot.get();

                if (spring > 0F)
                {
                    /* Back-out ease on the glyph's own stretch, which stagger staggers: it overshoots its place
                     * around the tail of the stretch and is exactly in place (and opaque) from its end on. */
                    float t = Math.min(1F, local);
                    float s = spring * 1.70158F;
                    float k = t - 1F;

                    u = t >= 1F ? 1F : 1F + (s + 1F) * k * k * k + s * k * k;
                    opacity = t;
                }
                else
                {
                    u = Math.min(1F, local) + Math.max(0F, progress - 1F);
                    opacity = Math.min(1F, local);
                }
            }

            /* The position follows u as it comes (it may pass 1 - the spring, or a curve overshooting); only the
             * opacity stops at full. */
            float gy = y + glyph.line * lineHeight + (1F - u) * this.form.revealOffset.get();
            float drawX;
            float drawY;

            matrix.set(base);

            if (arc != 0F)
            {
                /* Round the circle the line's width makes at this angle; each glyph turns with it */
                float radius = glyph.lineWidth / (float) Math.toRadians(arc);
                float theta = (glyph.x + glyph.width / 2F - glyph.lineCenter) / radius;
                float cy = gy + fontHeight / 2F;

                matrix.translate(glyph.lineCenter + radius * (float) Math.sin(theta), cy + radius - radius * (float) Math.cos(theta), 0F);
                matrix.rotateZ(theta);

                drawX = -glyph.width / 2F;
                drawY = -fontHeight / 2F;
            }
            else
            {
                drawX = glyph.x;
                drawY = gy;
            }

            /* The text parts, nearest first: with the depth test on, every layer further back only shows where
             * the ones in front left it bare. With it off nothing keeps them apart, so they go back to front. */
            List<Runnable> parts = new ArrayList<>();
            float px = drawX;
            float py = drawY;
            float po = opacity * fade;
            float fo = opacity;
            Matrix4f placed = new Matrix4f(matrix);

            glyphIndex++;

            if (fx)
            {
                float alpha = fill.a * fillOpacity * opacity;

                if (alpha >= 1F / 255F)
                {
                    TextDepthMesh mesh = fxMeshes.get(glyph.text);

                    if (shattering)
                    {
                        TextFx.shatter(bodyOut, mesh, matrix, drawX, drawY, thickness, fill, alpha, brightness, shatterProgress, this.form.shatterSpread.get(), glyphIndex, scratch);
                    }
                    else
                    {
                        float lineStart = glyph.lineCenter - glyph.lineWidth / 2F;

                        TextFx.dissolve(
                            bodyOut, mesh, matrix, drawX, drawY, depth, fill, alpha, brightness,
                            this.form.dissolveMode.get(), dissolveProgress, this.form.dissolveEdge.get(), this.form.dissolveBurnColor.get(), 2.7F / Math.max(fontHeight, 1),
                            glyph.x, y + glyph.line * lineHeight, (glyph.x - lineStart) / Math.max(glyph.lineWidth, 1F), glyph.lineWidth
                        );
                    }
                }
            }
            else if (solid)
            {
                float alpha = fill.a * fillOpacity * opacity;

                if (alpha >= 1F / 255F)
                {
                    this.emitBody(bodyOut, meshes.get(glyph.text), matrix, drawX, drawY, depth, fill, alpha, brightness, occlusion ? -1 : 0);

                    if (!occlusion)
                    {
                        this.emitBody(bodyOut, meshes.get(glyph.text), matrix, drawX, drawY, depth, fill, alpha, brightness, 1);
                        this.emitBody(bodyOut, meshes.get(glyph.text), matrix, drawX, drawY, depth, fill, alpha, brightness, 2);
                    }
                }
            }
            else if (fillOpacity * fill.a > 0F)
            {
                parts.add(() -> this.drawGlyph(renderer, consumers, placed, glyph.text, px, py, 0F, withAlpha(fill, fillOpacity * fo), light));
            }

            for (int k = 0; k < ring; k++)
            {
                double angle = Math.PI * 2D * k / ring;
                float rx = px + (float) Math.cos(angle) * outlineWidth;
                float ry = py + (float) Math.sin(angle) * outlineWidth;
                float rz = -0.05F - k * 0.01F;

                parts.add(() -> this.drawGlyph(renderer, consumers, placed, glyph.text, rx, ry, rz, withAlpha(outline, outlineOpacity * po), light));
            }

            for (int j = 1; j <= (solid ? 0 : layers); j++)
            {
                float lz = -0.3F - depth * j / layers;

                parts.add(() -> this.drawGlyph(renderer, consumers, placed, glyph.text, px, py, lz, withAlpha(side, po), light));
            }

            if (shadow.a > 0F)
            {
                parts.add(() -> this.drawGlyph(
                    renderer, consumers, placed, glyph.text,
                    px + this.form.shadowX.get(), py + this.form.shadowY.get(),
                    back - 0.1F, withAlpha(shadow, po), light
                ));
            }

            if (!occlusion)
            {
                Collections.reverse(parts);
            }

            for (Runnable part : parts)
            {
                part.run();
            }
        }

        consumers.draw();

        RenderSystem.enableDepthTest();

        if (body != null)
        {
            this.flush(body);
            bodyOwner.close();
        }

        this.renderShadow(context, x, y, w, h, back - 0.2F);
    }

    /** Put a glyph's solid body into the buffer: its mesh, moved to where the glyph is and stretched to the depth. */
    private void emitBody(VertexConsumer builder, TextDepthMesh mesh, Matrix4f matrix, float dx, float dy, float depth, Color color, float alpha, float brightness, int pass)
    {
        int[] order = {0, 1, 2, 0, 2, 3};

        for (int i = 0; i < mesh.count; i++)
        {
            int o = i * TextDepthMesh.STRIDE;

            /* Without a depth test the faces have to be put down back to front: pass 0 is the back,
             * 1 the walls, 2 the front; -1 takes them all (the depth test sorts them out). */
            if (pass >= 0)
            {
                float z = mesh.faces[o + 2];
                boolean flat = z == mesh.faces[o + 5] && z == mesh.faces[o + 8] && z == mesh.faces[o + 11];
                int kind = !flat ? 1 : (z == 0F ? 2 : 0);

                if (kind != pass)
                {
                    continue;
                }
            }

            float shade = mesh.faces[o + 15] * brightness;
            float r = mesh.faces[o + 12] * color.r * shade;
            float g = mesh.faces[o + 13] * color.g * shade;
            float b = mesh.faces[o + 14] * color.b * shade;

            for (int corner : order)
            {
                int c = o + corner * 3;

                /* The front, and the front edges of the walls with it, a hair ahead of the rest: nothing
                 * else of the text may share its depth and flicker through it */
                float z = mesh.faces[c + 2] == 0F ? FRONT_BIAS : mesh.faces[c + 2] * depth;

                builder.vertex(matrix, dx + mesh.faces[c], dy + mesh.faces[c + 1], z).color(r, g, b, alpha)/*$ nx*/.next()/**/;
            }
        }
    }

    private void drawGlyph(TextRenderer renderer, CustomVertexConsumerProvider consumers, Matrix4f matrix, String text, float x, float y, float z, int color, int light)
    {
        /* The game reads an alpha below 4/255 as "none given" and draws the text fully opaque, so a glyph
         * that is all but invisible must not be drawn at all - it would flash solid for a few frames at
         * the start of a reveal, in the place it is about to slide in from. */
        if ((color >>> 24) < 4)
        {
            return;
        }

        Matrix4f layer = new Matrix4f(matrix).translate(0F, 0F, z);

        renderer.draw(text, x, y, color, false, layer, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h)
    {
        this.renderShadow(context, x, y, w, h, -0.2F);
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h, float z)
    {
        float offset = this.form.offset.get();
        Color color = this.form.background.get().copy();

        color.mul(context.color);

        if (color.a <= 0)
        {
            return;
        }

        context.stack.push();
        context.stack.translate(0, 0, z);

        BufferBuilder builder;

        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats./*$ pct*/POSITION_COLOR_TEXTURE/**/);

        fillQuad(
            this.slicePlane == null ? builder : new SliceClipVertexConsumer(builder, this.slicePlane, 3), context.stack,
            x + w + offset, y - offset, 0,
            x - offset, y - offset, 0,
            x - offset, y + h + offset, 0,
            x + w + offset, y + h + offset, 0,
            color.r, color.g, color.b, color.a
        );

        this.flush(builder);

        context.stack.pop();
    }

    /** Draw a finished flat-coloured buffer now, or hand it to the sorted queue when a group is open. */
    private void flush(BufferBuilder builder)
    {
        RenderSystem.enableBlend();

        if (this.form.depthOcclusion.get())
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
        else
        {
            RenderSystem.disableDepthTest();
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        /* The body is wound for no side in particular (the text's y is flipped on the way to the
         * world, which turns every face around), so it must never meet back-face culling. The
         * label switches culling off at its start, but a render layer drawn in between (the glyph
         * text, its background) switches it back on as it ends - here, and again while the group
         * replays, where children don't get a cull state of their own. With it on, the front and
         * back faces and half the walls were thrown away: what was left of a big glyph looked cut
         * off, and the thin walls of the pixel font were all that was left of it. */
        RenderSystem.disableCull();

        if (FormTranslucentQueue.isGroupOpen())
        {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            Bufs.upload(buffer, builder);
            VertexBuffer.unbind();

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                buffer, GameRenderer::getPositionColorProgram, null,
                new Matrix4f(RenderSystem.getModelViewMatrix()),
                null, new Vector3f(FormTranslucentQueue.getSortOrigin()), null, false, RenderSystem::disableCull, null
            ));
        }
        else
        {
            Bufs.draw(builder);
        }
    }
}