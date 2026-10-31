package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.VertexUtils;
import mchorse.bbs_mod.utils.Bufs;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.FormFx;
import mchorse.bbs_mod.forms.renderers.utils.ShatterField;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.forms.renderers.utils.FramebufferDebug;
import mchorse.bbs_mod.utils.colors.OverlayBlend;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL13;

import java.util.function.Supplier;

public class BillboardFormRenderer <T extends BillboardForm> extends FormRenderer<T>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private static final Matrix4f matrix = new Matrix4f();

    public BillboardFormRenderer(T form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 1.5F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;

        this.renderModel(format, GameRenderer::getRenderTypeEntityTranslucentProgram,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition(), false
        );

        stack.pop();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        /* A dissolve is the shaded model shader's, so the format has to be the shaded one */
        if (BBSRendering.isIrisShadersEnabled() || (!context.isPicking() && this.form.fx.isDissolving() && FormFx.supportsDissolve()))
        {
            shading = true;
        }

        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
        Supplier<ShaderProgram> shader = this.getShader(context,
            shading ? GameRenderer::getRenderTypeEntityTranslucentProgram : /*$ ptl*/GameRenderer::getPositionTexLightmapColorProgram/**/,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(format, shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    /**
     * The texture the quad wears. The video form's renderer swaps this for a
     * decoded video frame; everything else about the quad stays shared.
     */
    protected Texture getTexture()
    {
        Link t = this.form.texture.get();

        return t == null ? null : BBSModClient.getTextures().getTexture(t);
    }

    private void renderModel(VertexFormat format, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Texture texture = this.getTexture();

        if (texture == null)
        {
            return;
        }

        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = this.form.crop.get();
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        float uvFinalTLx = uvTLx;
        float uvFinalTLy = uvTLy;
        float uvFinalBRx = uvBRx;
        float uvFinalBRy = uvBRy;

        if (this.form.resizeCrop.get())
        {
            uvFinalTLx = uvFinalTLy = 0F;
            uvFinalBRx = uvFinalBRy = 1F;

            w = w - crop.x - crop.z;
            h = h - crop.y - crop.w;
        }

        /* Calculate quad's size (vertices, not UV) */
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvFinalTLx - 0.5F) * ratioY;
        float TLy = -(uvFinalTLy - 0.5F) * ratioX;
        float BRx = (uvFinalBRx - 0.5F) * ratioY;
        float BRy = -(uvFinalBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        float offsetX = this.form.offsetX.get();
        float offsetY = this.form.offsetY.get();
        float rotation = this.form.rotation.get();

        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (ow - crop.z)) / 2F / ow;
            float centerY = (crop.y + (oh - crop.w)) / 2F / ow;

            matrix.identity()
                .translate(centerX, centerY, 0)
                .rotateZ(MathUtils.toRad(rotation))
                .translate(offsetX / ow, offsetY / oh, 0)
                .translate(-centerX, -centerY, 0);

            uvQuad.transform(matrix);
        }

        this.renderQuad(format, texture, shader, matrices, overlay, light, overlayColor, transition, defer);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        BufferBuilder builder;
        Color color = new Color().set(overlayColor, true);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        FormColorBlend.blend(color, this.form.color.get());

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            Vector3f scale = Vectors.TEMP_3F;

            modelMatrix.getScale(scale);

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            modelMatrix.scale(scale);

            matrices.peek().getNormalMatrix().identity();
        }

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        /* The color overlay rides the overlay-texture channel, so it needs the shaded format
         * (the no-shading format has no overlay UV) and steps aside for a hurt flash. Picker
         * programs don't sample unit 1, so this is inert while picking. */
        Color formOverlay = this.form.overlayColor.get();
        boolean overlayActive = format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
            && overlay == OverlayTexture.DEFAULT_UV
            && OverlayBlend.isActive(formOverlay);
        int previousOverlayTexture = overlayActive ? FormOverlay.bind(formOverlay) : 0;

        if (overlayActive)
        {
            overlay = 0;
        }

        /* Dissolve and shatter are for the visible draw (defer), never the editor's preview or picking.
         * A dissolving image burns in the model shader and draws right now: the deferred pass would
         * replay it after the dissolve was ended. */
        FormFxValues fx = this.form.fx;
        boolean shatter = defer && fx.isShattering();
        boolean dissolve = defer && !shatter && fx.isDissolving() && FormFx.supportsDissolve()
            && format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
            && FormFx.beginDissolve(fx, new Matrix4f(matrix).invert(), quad.p3.y, quad.p1.y - quad.p3.y);
        ShaderProgram finalShader = dissolve ? BBSShaders.getModel() : shader.get();

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(() -> finalShader);

        if (FramebufferDebug.inside())
        {
            FramebufferDebug.log("billboard", "shader=" + FramebufferDebug.shader(finalShader)
                + " shaded=" + (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
                + " texture=" + texture.id + "/translucent=" + texture.hasTranslucency() + " alpha=" + color.a
                + " light=" + light + " overlayActive=" + overlayActive + " defer=" + defer
                + " | " + FramebufferDebug.bindings());
        }

        /* Filter parameters go to whichever texture is bound on the ACTIVE unit, and nothing
         * promises that unit is 0 here: under a shader pack it is whatever unit Iris touched
         * last (unit 2 in practice). A raw bind there put this texture over the lightmap's slot
         * behind GlStateManager's back - its cache still said the lightmap was bound, so the
         * draw never rebound it, and the quad was lit by a texel of its own skin. Going through
         * RenderSystem keeps the real binding and the cache in step, on unit 0, on purpose. */
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(texture.id);
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());
        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, format);
        if (shatter)
        {
            this.fillShattered(format, builder, matrix, color, overlay, light, normal, fx);
        }
        else
        {
            this.fillWhole(format, builder, matrix, color, overlay, light, normal);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        boolean linear = this.form.linear.get();
        boolean mipmap = this.form.mipmap.get();
        boolean translucent = texture.hasTranslucency() || color.a < 1F || linear || mipmap;

        if (defer && translucent && !dissolve && FormTranslucentQueue.isActive())
        {
            /* The whole quad defers to the end-of-frame translucent pass: depth testing still
             * hides it behind opaque geometry, while the far-to-near sort blends it correctly
             * against other translucent forms — and it never occludes models behind it.
             * Linear/mipmap sampling manufactures fractional alpha at the edges even from a
             * binary-alpha texture, hence those flags count as translucency. */
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

            buffer.bind();
            Bufs.upload(buffer, builder);
            VertexBuffer.unbind();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));
            Vector3f planeNormal = FormTranslucentQueue.quadPlaneNormal(modelView, matrix);

            FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
                buffer, () -> finalShader, texture, modelView, null, origin, planeNormal, true,
                () ->
                {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    RenderSystem.bindTexture(texture.id);
                    texture.setFilterMipmap(linear, mipmap);
                },
                () ->
                {
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    RenderSystem.bindTexture(texture.id);
                    texture.setFilterMipmap(false, false);
                }
            ).overlayColor(overlayActive ? formOverlay : null));
        }
        else
        {
            Bufs.draw(builder);
        }

        if (dissolve)
        {
            FormFx.endDissolve();
        }

        if (FramebufferDebug.inside())
        {
            FramebufferDebug.log("billboard", "after draw | " + FramebufferDebug.bindings());
            FramebufferDebug.log("billboard", "after draw | " + FramebufferDebug.glState());
            FramebufferDebug.log("billboard", "after draw | " + FramebufferDebug.samplers());
        }

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(texture.id);
        texture.setFilterMipmap(false, false);

        if (overlayActive)
        {
            FormOverlay.unbind(previousOverlayTexture);
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    /** The image as the two sides of one quad. */
    private void fillWhole(VertexFormat format, BufferBuilder builder, Matrix4f matrix, Color color, int overlay, int light, Matrix3f normal)
    {
        float h = this.getHalfThickness();

        /* Front */
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, h, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, h, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, normal, h, 1F)/*$ nx*/.next()/**/;

        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, h, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, normal, h, 1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, h, 1F)/*$ nx*/.next()/**/;

        /* Back */
        this.fill(format, builder, matrix, quad.p1.x, quad.p1.y, color, uvQuad.p1.x, uvQuad.p1.y, overlay, light, normal, -h, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, -h, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, -h, -1F)/*$ nx*/.next()/**/;

        this.fill(format, builder, matrix, quad.p2.x, quad.p2.y, color, uvQuad.p2.x, uvQuad.p2.y, overlay, light, normal, -h, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p4.x, quad.p4.y, color, uvQuad.p4.x, uvQuad.p4.y, overlay, light, normal, -h, -1F)/*$ nx*/.next()/**/;
        this.fill(format, builder, matrix, quad.p3.x, quad.p3.y, color, uvQuad.p3.x, uvQuad.p3.y, overlay, light, normal, -h, -1F)/*$ nx*/.next()/**/;

        if (h > 0F)
        {
            this.fillSides(format, builder, matrix, color, overlay, light, normal, quad, uvQuad, h);
        }
    }

    /**
     * How far the front and back faces stand off the middle: 0 for a flat image, which
     * is what a billboard is. The video form extrudes into a solid by raising it.
     */
    protected float getHalfThickness()
    {
        return 0F;
    }

    /**
     * What goes between the two faces when they stand apart: the walls of an extrusion.
     * The faces' corners are in quad (positions, p1 top left, p2 top right, p3 bottom
     * left, p4 bottom right) and uvQuad (their texture coordinates).
     */
    protected void fillSides(VertexFormat format, BufferBuilder builder, Matrix4f matrix, Color color, int overlay, int light, Matrix3f normal, Quad quad, Quad uvQuad, float halfThickness)
    {}

    /**
     * The image cut into a grid of tiles, each of which flies off with its chunk of the grid. The
     * tiles keep their piece of the picture, and are about a tenth of the long side.
     */
    private void fillShattered(VertexFormat format, BufferBuilder builder, Matrix4f matrix, Color color, int overlay, int light, Matrix3f normal, FormFxValues fx)
    {
        float width = Math.abs(quad.p2.x - quad.p1.x);
        float height = Math.abs(quad.p1.y - quad.p3.y);
        float cell = Math.max(width, height) / 10F;
        ShatterField field = new ShatterField(fx, new Matrix4f(), cell, true);
        int tilesX = Math.max(1, Math.round(width / cell));
        int tilesY = Math.max(1, Math.round(height / cell));
        Vector3f[] corners = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        float[] us = new float[4];
        float[] vs = new float[4];

        for (int j = 0; j < tilesY; j++)
        {
            for (int i = 0; i < tilesX; i++)
            {
                float s0 = i / (float) tilesX;
                float s1 = (i + 1) / (float) tilesX;
                float t0 = j / (float) tilesY;
                float t1 = (j + 1) / (float) tilesY;

                /* Top left, top right, bottom right, bottom left */
                float[] ss = {s0, s1, s1, s0};
                float[] ts = {t0, t0, t1, t1};
                float centerX = this.across(quad.p1.x, quad.p2.x, quad.p3.x, quad.p4.x, (s0 + s1) / 2F, (t0 + t1) / 2F);
                float centerY = this.across(quad.p1.y, quad.p2.y, quad.p3.y, quad.p4.y, (s0 + s1) / 2F, (t0 + t1) / 2F);
                Matrix4f move = field.at(centerX, centerY, 0F);

                if (move == null)
                {
                    continue;
                }

                for (int k = 0; k < 4; k++)
                {
                    move.transformPosition(
                        this.across(quad.p1.x, quad.p2.x, quad.p3.x, quad.p4.x, ss[k], ts[k]),
                        this.across(quad.p1.y, quad.p2.y, quad.p3.y, quad.p4.y, ss[k], ts[k]),
                        0F, corners[k]
                    );

                    us[k] = this.across(uvQuad.p1.x, uvQuad.p2.x, uvQuad.p3.x, uvQuad.p4.x, ss[k], ts[k]);
                    vs[k] = this.across(uvQuad.p1.y, uvQuad.p2.y, uvQuad.p3.y, uvQuad.p4.y, ss[k], ts[k]);
                }

                Vector3f front = move.transformDirection(0F, 0F, 1F, new Vector3f());

                if (front.lengthSquared() > 0.00001F)
                {
                    front.normalize();
                }

                /* Front: bottom left, top right, top left, then bottom left, bottom right, top right */
                int[] order = {3, 1, 0, 3, 2, 1};

                for (int k = 0; k < 6; k++)
                {
                    this.fillAt(format, builder, matrix, corners[order[k]], color, us[order[k]], vs[order[k]], overlay, light, normal, front, 1F);
                }

                /* Back: top left, top right, bottom left, then top right, bottom right, bottom left */
                int[] back = {0, 1, 3, 1, 2, 3};

                for (int k = 0; k < 6; k++)
                {
                    this.fillAt(format, builder, matrix, corners[back[k]], color, us[back[k]], vs[back[k]], overlay, light, normal, front, -1F);
                }
            }
        }
    }

    /** A value at (s, t) of a quad whose corners hold a (top left), b (top right), c (bottom left) and d (bottom right). */
    private float across(float a, float b, float c, float d, float s, float t)
    {
        float top = a + (b - a) * s;
        float bottom = c + (d - c) * s;

        return top + (bottom - top) * t;
    }

    private void fillAt(VertexFormat format, BufferBuilder builder, Matrix4f matrix, Vector3f p, Color color, float u, float v, int overlay, int light, Matrix3f normal, Vector3f facing, float side)
    {
        if (format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR)
        {
            builder.vertex(matrix, p.x, p.y, p.z).texture(u, v).light(light).color(color.r, color.g, color.b, color.a)/*$ nx*/.next()/**/;

            return;
        }

        VertexUtils.normal(builder.vertex(matrix, p.x, p.y, p.z).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(overlay).light(light), normal, facing.x * side, facing.y * side, facing.z * side)/*$ nx*/.next()/**/;
    }

    private VertexConsumer fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, Color color, float u, float v, int overlay, int light, Matrix3f normal, float z, float nz)
    {
        return this.vertex(format, consumer, matrix, x, y, z, color.r, color.g, color.b, color.a, u, v, overlay, light, normal, 0F, 0F, nz);
    }

    /** One vertex in either of the formats a billboard draws with; the caller ends it. */
    protected VertexConsumer vertex(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, Matrix3f normal, float nx, float ny, float nz)
    {
        if (format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR)
        {
            return consumer.vertex(matrix, x, y, z).texture(u, v).light(light).color(r, g, b, a);
        }

        return VertexUtils.normal(consumer.vertex(matrix, x, y, z).color(r, g, b, a).texture(u, v).overlay(overlay).light(light), normal, nx, ny, nz);
    }
}
