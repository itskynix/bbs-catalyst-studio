package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.utils.VertexUtils;
import mchorse.bbs_mod.utils.Bufs;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.FormFx;
import mchorse.bbs_mod.forms.renderers.utils.ShatterField;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public class ExtrudedFormRenderer extends FormRenderer<ExtrudedForm>
{
    public ExtrudedFormRenderer(ExtrudedForm form)
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
        stack.scale(1.5F, 1.5F, 4F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Shading fix */
        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        this.renderModel(BBSShaders::getModel,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition(), false
        );
        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
        Supplier<ShaderProgram> normal = shading && !BBSRendering.isIrisShadersEnabled()
            ? BBSShaders::getModel
            : (shading ? GameRenderer::getRenderTypeEntityTranslucentProgram : /*$ ptl*/GameRenderer::getPositionTexLightmapColorProgram/**/);
        Supplier<ShaderProgram> shader = this.getShader(context,
            normal,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), !context.isPicking());
    }

    /**
     * The extrusion grows from its middle: the depth axis is scaled, so the faces on its sides
     * follow it and the front and back stay where the picture is.
     */
    private void renderModel(Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        float depth = this.form.extrudeDepth.get();

        if (depth == 1F)
        {
            this.renderExtrusion(shader, matrices, overlay, light, overlayColor, transition, defer);

            return;
        }

        matrices.push();
        /* Flat is not zero: a zero scale would have no normals left */
        matrices.scale(1F, 1F, Math.max(depth, 0.001F));
        this.renderExtrusion(shader, matrices, overlay, light, overlayColor, transition, defer);
        matrices.pop();
    }

    private void renderExtrusion(Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer)
    {
        Link texture = this.form.texture.get();
        ModelVAO data = BBSModClient.getTextures().getExtruder().get(texture);

        if (data != null)
        {
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

            Color color = Colors.COLOR.set(overlayColor, true);
            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
            Color formColor = this.form.color.get();

            FormColorBlend.blend(color, formColor);

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);

            BBSModClient.getTextures().bindTexture(textureObject);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();

            /* Dissolve and shatter are for the visible draw (defer), never the editor's preview or picking */
            FormFxValues fx = this.form.fx;

            if (defer && fx.isShattering())
            {
                this.renderShattered(texture, matrices, color, light, overlay, new ShatterField(fx, new Matrix4f(), 0.1F, true));

                RenderSystem.disableBlend();

                gameRenderer.getLightmapTextureManager().disable();
                gameRenderer.getOverlayTexture().teardownOverlayColor();

                return;
            }

            boolean dissolve = defer && fx.isDissolving() && FormFx.supportsDissolve()
                && FormFx.beginDissolveVao(fx, ModelVAORenderer.captureModelView(matrices), -0.5F, 1F);
            boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

            /* Same law as model forms under Iris: the draw always runs right now, in the phase
             * its program is meant for (the end-of-frame replay lands after a deferred pack's
             * shading composite — Photon never shades it), and texture translucency at full
             * colour degrades to alpha cutout — transparent texels become holes, semi-transparent
             * ones draw solid. */
            boolean cutout = defer && irisWorld && textureObject != null && textureObject.hasTranslucency()
                && color.a >= 1F;

            /* A dissolving form burns in the model shader, and draws right now - the deferred pass
             * would replay it after the dissolve was ended */
            ShaderProgram finalShader = dissolve ? BBSShaders.getModel() : (cutout ? GameRenderer.getRenderTypeEntityCutoutProgram() : shader.get());
            boolean wasActive = false;

            if (irisWorld)
            {
                wasActive = FormTranslucentQueue.suspend();
            }

            if (cutout)
            {
                RenderSystem.disableBlend();
            }

            try
            {
                if (defer && !dissolve && FormTranslucentQueue.needsSplit(finalShader, null, textureObject, color.a))
                {
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.peek().getNormalMatrix());

                    FormTranslucentQueue.setPassMode(finalShader, FormTranslucentQueue.PASS_OPAQUE);
                    ModelVAORenderer.render(finalShader, data, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay);
                    FormTranslucentQueue.setPassMode(finalShader, FormTranslucentQueue.PASS_SINGLE);

                    FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(data, textureObject, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, true));
                }
                else if (defer && !dissolve && FormTranslucentQueue.needsWholeDefer(finalShader, null, color.a))
                {
                    /* A uniform colour fade defers the whole draw into the sorted end-of-frame
                     * pass with depth kept on, so the faded form still self-occludes. */
                    Matrix4f modelView = ModelVAORenderer.captureModelView(matrices);
                    Matrix3f normalMat = new Matrix3f(matrices.peek().getNormalMatrix());

                    FormTranslucentQueue.add(new FormTranslucentQueue.ModelVAOCommand(data, () -> finalShader, FormTranslucentQueue.PASS_SINGLE, true, textureObject, modelView, normalMat, color.r, color.g, color.b, color.a, light, overlay, true));
                }
                else
                {
                    ModelVAORenderer.render(finalShader, data, matrices, color.r, color.g, color.b, color.a, light, overlay);
                }
            }
            finally
            {
                if (irisWorld)
                {
                    FormTranslucentQueue.restore(wasActive);
                }

                if (dissolve)
                {
                    FormFx.endDissolve();
                }
            }

            RenderSystem.disableBlend();

            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }

    /**
     * The extrusion broken into pieces on the CPU: the triangles it is made of, cut down until each is
     * no bigger than a chunk, every one moved with the chunk it falls in. The texture stays on them, so
     * the pieces are bits of the picture, front, back and sides together.
     */
    private void renderShattered(Link texture, MatrixStack matrices, Color color, int light, int overlay, ShatterField field)
    {
        ModelVAOData source = BBSModClient.getTextures().getExtruder().getSource(texture);

        if (source == null)
        {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        BufferBuilder builder;
        float[] vertices = source.vertices();
        float[] normals = source.normals();
        float[] uvs = source.texCoords();
        float[] p = new float[9];
        float[] n = new float[9];
        float[] t = new float[6];

        builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        for (int i = 0; i + 8 < vertices.length; i += 9)
        {
            System.arraycopy(vertices, i, p, 0, 9);
            System.arraycopy(normals, i, n, 0, 9);
            System.arraycopy(uvs, i / 3 * 2, t, 0, 6);

            this.shatterTriangle(builder, matrix, normal, field, color, light, overlay, p, n, t, 0);
        }

        RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentProgram);
        Bufs.draw(builder);
    }

    /** One triangle: split in four while it is bigger than a chunk, then moved as part of its chunk. */
    private void shatterTriangle(BufferBuilder builder, Matrix4f matrix, Matrix3f normalMatrix, ShatterField field, Color color, int light, int overlay, float[] p, float[] n, float[] t, int depth)
    {
        float longest = 0F;

        for (int i = 0; i < 3; i++)
        {
            int j = (i + 1) % 3;
            float dx = p[i * 3] - p[j * 3];
            float dy = p[i * 3 + 1] - p[j * 3 + 1];
            float dz = p[i * 3 + 2] - p[j * 3 + 2];

            longest = Math.max(longest, dx * dx + dy * dy + dz * dz);
        }

        if (depth < 6 && longest > field.getCell() * field.getCell())
        {
            /* The corners and the middle of each edge make four triangles */
            float[] mp = new float[9];
            float[] mn = new float[9];
            float[] mt = new float[6];

            for (int i = 0; i < 3; i++)
            {
                int j = (i + 1) % 3;

                for (int k = 0; k < 3; k++)
                {
                    mp[i * 3 + k] = (p[i * 3 + k] + p[j * 3 + k]) / 2F;
                    mn[i * 3 + k] = (n[i * 3 + k] + n[j * 3 + k]) / 2F;
                }

                for (int k = 0; k < 2; k++)
                {
                    mt[i * 2 + k] = (t[i * 2 + k] + t[j * 2 + k]) / 2F;
                }
            }

            /* Edge midpoints: 0 is between corners 0 and 1, 1 between 1 and 2, 2 between 2 and 0 */
            this.shatterTriangle(builder, matrix, normalMatrix, field, color, light, overlay, this.pick(p, 0, mp, 0, mp, 2), this.pick(n, 0, mn, 0, mn, 2), this.pick2(t, 0, mt, 0, mt, 2), depth + 1);
            this.shatterTriangle(builder, matrix, normalMatrix, field, color, light, overlay, this.pick(p, 1, mp, 1, mp, 0), this.pick(n, 1, mn, 1, mn, 0), this.pick2(t, 1, mt, 1, mt, 0), depth + 1);
            this.shatterTriangle(builder, matrix, normalMatrix, field, color, light, overlay, this.pick(p, 2, mp, 2, mp, 1), this.pick(n, 2, mn, 2, mn, 1), this.pick2(t, 2, mt, 2, mt, 1), depth + 1);
            this.shatterTriangle(builder, matrix, normalMatrix, field, color, light, overlay, mp, mn, mt, depth + 1);

            return;
        }

        Matrix4f move = field.at((p[0] + p[3] + p[6]) / 3F, (p[1] + p[4] + p[7]) / 3F, (p[2] + p[5] + p[8]) / 3F);

        if (move == null)
        {
            return;
        }

        Vector3f position = new Vector3f();
        Vector3f direction = new Vector3f();

        for (int i = 0; i < 3; i++)
        {
            move.transformPosition(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], position);
            move.transformDirection(n[i * 3], n[i * 3 + 1], n[i * 3 + 2], direction);

            if (direction.lengthSquared() > 0.00001F)
            {
                direction.normalize();
            }

            VertexUtils.normal(builder.vertex(matrix, position.x, position.y, position.z)
                .color(color.r, color.g, color.b, color.a)
                .texture(t[i * 2], t[i * 2 + 1])
                .overlay(overlay)
                .light(light), normalMatrix, direction.x, direction.y, direction.z)
                /*$ nx*/.next()/**/;
        }
    }

    /** The triangle made of corner a of the first array and entries b and c of the other two, three numbers each. */
    private float[] pick(float[] corners, int a, float[] first, int b, float[] second, int c)
    {
        return new float[] {
            corners[a * 3], corners[a * 3 + 1], corners[a * 3 + 2],
            first[b * 3], first[b * 3 + 1], first[b * 3 + 2],
            second[c * 3], second[c * 3 + 1], second[c * 3 + 2]
        };
    }

    /** The same, for texture coordinates: two numbers each. */
    private float[] pick2(float[] corners, int a, float[] first, int b, float[] second, int c)
    {
        return new float[] {
            corners[a * 2], corners[a * 2 + 1],
            first[b * 2], first[b * 2 + 1],
            second[c * 2], second[c * 2 + 1]
        };
    }
}
