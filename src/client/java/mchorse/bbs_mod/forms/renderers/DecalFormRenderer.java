package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.DecalForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.VertexUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class DecalFormRenderer extends FormRenderer<DecalForm>
{
    public DecalFormRenderer(DecalForm form)
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

        Texture texture = this.getTexture();

        if (texture != null)
        {
            this.renderFlatQuad(stack, texture, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE, false);
        }

        stack.pop();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        Texture texture = this.getTexture();

        if (texture == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || context.ui)
        {
            int light = this.form.glow.get() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : context.light;

            this.renderFlatQuad(context.stack, texture, light, context.color, !context.isPicking());

            return;
        }

        this.renderBoxProjection(context, texture);
    }

    protected Texture getTexture()
    {
        Link t = this.form.texture.get();

        return t == null ? null : BBSModClient.getTextures().getTexture(t);
    }

    private void renderFlatQuad(MatrixStack matrices, Texture texture, int light, int overlayColor, boolean defer)
    {
        float w = this.form.width.get();
        float h = this.form.height.get();
        float halfW = w / 2F;
        float halfH = h / 2F;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        Color color = this.form.color.get().copy();

        color.mul(overlayColor);
        color.a *= this.form.opacity.get();

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
        Supplier<ShaderProgram> shader = GameRenderer::getRenderTypeEntityTranslucentProgram;
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(shader);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, format);

        /* Front face */
        this.vertex(builder, matrix, normal, -halfW, -halfH, 0F, 0F, 1F, color, light, 1F);
        this.vertex(builder, matrix, normal, halfW, -halfH, 0F, 1F, 1F, color, light, 1F);
        this.vertex(builder, matrix, normal, halfW, halfH, 0F, 1F, 0F, color, light, 1F);

        this.vertex(builder, matrix, normal, -halfW, -halfH, 0F, 0F, 1F, color, light, 1F);
        this.vertex(builder, matrix, normal, halfW, halfH, 0F, 1F, 0F, color, light, 1F);
        this.vertex(builder, matrix, normal, -halfW, halfH, 0F, 0F, 0F, color, light, 1F);

        if (this.form.doubleSided.get())
        {
            this.vertex(builder, matrix, normal, halfW, -halfH, 0F, 1F, 1F, color, light, -1F);
            this.vertex(builder, matrix, normal, -halfW, -halfH, 0F, 0F, 1F, color, light, -1F);
            this.vertex(builder, matrix, normal, -halfW, halfH, 0F, 0F, 0F, color, light, -1F);

            this.vertex(builder, matrix, normal, halfW, -halfH, 0F, 1F, 1F, color, light, -1F);
            this.vertex(builder, matrix, normal, -halfW, halfH, 0F, 0F, 0F, color, light, -1F);
            this.vertex(builder, matrix, normal, halfW, halfH, 0F, 1F, 0F, color, light, -1F);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        if (defer && FormTranslucentQueue.isActive())
        {
            this.uploadTranslucent(builder, texture, matrix);
        }
        else
        {
            Bufs.draw(builder);
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    private void renderBoxProjection(FormRenderingContext context, Texture texture)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrices = context.stack;
        Matrix4f worldMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix4f invMatrix = new Matrix4f(worldMatrix).invert();

        float halfW = this.form.width.get() / 2F;
        float halfH = this.form.height.get() / 2F;
        float halfD = this.form.depth.get() / 2F;
        float offset = this.form.offset.get();
        float thresholdCos = (float) Math.cos(Math.toRadians(this.form.normalThreshold.get()));
        boolean doubleSided = this.form.doubleSided.get();
        boolean glow = this.form.glow.get();
        float opacity = this.form.opacity.get();

        Vector3f origin = new Vector3f();
        worldMatrix.getTranslation(origin);

        float radius = (float) Math.sqrt(halfW * halfW + halfH * halfH + halfD * halfD);
        int minX = MathHelper.floor(origin.x - radius);
        int maxX = MathHelper.ceil(origin.x + radius);
        int minY = MathHelper.floor(origin.y - radius);
        int maxY = MathHelper.ceil(origin.y + radius);
        int minZ = MathHelper.floor(origin.z - radius);
        int maxZ = MathHelper.ceil(origin.z + radius);

        Vector3f forward = new Vector3f(0F, 0F, -1F);
        worldMatrix.transformDirection(forward);
        forward.normalize();

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
        Supplier<ShaderProgram> shader = GameRenderer::getRenderTypeEntityTranslucentProgram;
        GameRenderer gameRenderer = mc.gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        BBSModClient.getTextures().bindTexture(texture);
        RenderSystem.setShader(shader);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, format);
        Color baseColor = this.form.color.get().copy();
        baseColor.mul(context.color);
        baseColor.a *= opacity;

        Matrix3f normalMat = matrices.peek().getNormalMatrix();
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        Vector3f p0 = new Vector3f();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();
        Vector3f p3 = new Vector3f();
        Vector3f norm = new Vector3f();

        for (int x = minX; x <= maxX; x++)
        {
            for (int y = minY; y <= maxY; y++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    mpos.set(x, y, z);
                    BlockState state = mc.world.getBlockState(mpos);

                    if (state.isAir())
                    {
                        continue;
                    }

                    VoxelShape shape = state.getOutlineShape(mc.world, mpos);

                    if (shape.isEmpty())
                    {
                        continue;
                    }

                    int light = glow ? LightmapTextureManager.MAX_LIGHT_COORDINATE : WorldRenderer.getLightmapCoordinates(mc.world, state, mpos);

                    for (Box box : shape.getBoundingBoxes())
                    {
                        double bMinX = x + box.minX;
                        double bMaxX = x + box.maxX;
                        double bMinY = y + box.minY;
                        double bMaxY = y + box.maxY;
                        double bMinZ = z + box.minZ;
                        double bMaxZ = z + box.maxZ;

                        for (Direction dir : Direction.values())
                        {
                            norm.set(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ());
                            float dot = norm.dot(forward);

                            if (doubleSided)
                            {
                                dot = Math.abs(dot);
                            }

                            if (dot < thresholdCos)
                            {
                                continue;
                            }

                            /* Quad vertices in world space */
                            this.getFaceVertices(dir, bMinX, bMaxX, bMinY, bMaxY, bMinZ, bMaxZ, p0, p1, p2, p3);

                            /* Project into decal space */
                            Vector3f l0 = invMatrix.transformPosition(new Vector3f(p0));
                            Vector3f l1 = invMatrix.transformPosition(new Vector3f(p1));
                            Vector3f l2 = invMatrix.transformPosition(new Vector3f(p2));
                            Vector3f l3 = invMatrix.transformPosition(new Vector3f(p3));

                            /* Check if within decal bounds */
                            if (!this.isInBounds(l0, l1, l2, l3, halfW, halfH, halfD))
                            {
                                continue;
                            }

                            /* UV computation */
                            float u0 = (l0.x / (halfW * 2F)) + 0.5F;
                            float v0 = 0.5F - (l0.y / (halfH * 2F));

                            float u1 = (l1.x / (halfW * 2F)) + 0.5F;
                            float v1 = 0.5F - (l1.y / (halfH * 2F));

                            float u2 = (l2.x / (halfW * 2F)) + 0.5F;
                            float v2 = 0.5F - (l2.y / (halfH * 2F));

                            float u3 = (l3.x / (halfW * 2F)) + 0.5F;
                            float v3 = 0.5F - (l3.y / (halfH * 2F));

                            /* Apply normal offset for z-fighting prevention */
                            p0.add(norm.x * offset, norm.y * offset, norm.z * offset);
                            p1.add(norm.x * offset, norm.y * offset, norm.z * offset);
                            p2.add(norm.x * offset, norm.y * offset, norm.z * offset);
                            p3.add(norm.x * offset, norm.y * offset, norm.z * offset);

                            /* Vertex to local stack space */
                            Vector3f v0Pos = invMatrix.transformPosition(new Vector3f(p0));
                            Vector3f v1Pos = invMatrix.transformPosition(new Vector3f(p1));
                            Vector3f v2Pos = invMatrix.transformPosition(new Vector3f(p2));
                            Vector3f v3Pos = invMatrix.transformPosition(new Vector3f(p3));

                            /* Draw 2 triangles */
                            this.vertex(builder, worldMatrix, normalMat, v0Pos.x, v0Pos.y, v0Pos.z, u0, v0, baseColor, light, 1F);
                            this.vertex(builder, worldMatrix, normalMat, v1Pos.x, v1Pos.y, v1Pos.z, u1, v1, baseColor, light, 1F);
                            this.vertex(builder, worldMatrix, normalMat, v2Pos.x, v2Pos.y, v2Pos.z, u2, v2, baseColor, light, 1F);

                            this.vertex(builder, worldMatrix, normalMat, v0Pos.x, v0Pos.y, v0Pos.z, u0, v0, baseColor, light, 1F);
                            this.vertex(builder, worldMatrix, normalMat, v2Pos.x, v2Pos.y, v2Pos.z, u2, v2, baseColor, light, 1F);
                            this.vertex(builder, worldMatrix, normalMat, v3Pos.x, v3Pos.y, v3Pos.z, u3, v3, baseColor, light, 1F);
                        }
                    }
                }
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        if (!context.isPicking() && FormTranslucentQueue.isActive())
        {
            this.uploadTranslucent(builder, texture, worldMatrix);
        }
        else
        {
            Bufs.draw(builder);
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
    }

    private void getFaceVertices(Direction dir, double minX, double maxX, double minY, double maxY, double minZ, double maxZ, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3)
    {
        switch (dir)
        {
            case UP:
                p0.set((float) minX, (float) maxY, (float) minZ);
                p1.set((float) minX, (float) maxY, (float) maxZ);
                p2.set((float) maxX, (float) maxY, (float) maxZ);
                p3.set((float) maxX, (float) maxY, (float) minZ);
                break;
            case DOWN:
                p0.set((float) minX, (float) minY, (float) maxZ);
                p1.set((float) minX, (float) minY, (float) minZ);
                p2.set((float) maxX, (float) minY, (float) minZ);
                p3.set((float) maxX, (float) minY, (float) maxZ);
                break;
            case NORTH:
                p0.set((float) maxX, (float) maxY, (float) minZ);
                p1.set((float) maxX, (float) minY, (float) minZ);
                p2.set((float) minX, (float) minY, (float) minZ);
                p3.set((float) minX, (float) maxY, (float) minZ);
                break;
            case SOUTH:
                p0.set((float) minX, (float) maxY, (float) maxZ);
                p1.set((float) minX, (float) minY, (float) maxZ);
                p2.set((float) maxX, (float) minY, (float) maxZ);
                p3.set((float) maxX, (float) maxY, (float) maxZ);
                break;
            case WEST:
                p0.set((float) minX, (float) maxY, (float) minZ);
                p1.set((float) minX, (float) minY, (float) minZ);
                p2.set((float) minX, (float) minY, (float) maxZ);
                p3.set((float) minX, (float) maxY, (float) maxZ);
                break;
            case EAST:
                p0.set((float) maxX, (float) maxY, (float) maxZ);
                p1.set((float) maxX, (float) minY, (float) maxZ);
                p2.set((float) maxX, (float) minY, (float) minZ);
                p3.set((float) maxX, (float) maxY, (float) minZ);
                break;
        }
    }

    private boolean isInBounds(Vector3f l0, Vector3f l1, Vector3f l2, Vector3f l3, float halfW, float halfH, float halfD)
    {
        float minX = Math.min(Math.min(l0.x, l1.x), Math.min(l2.x, l3.x));
        float maxX = Math.max(Math.max(l0.x, l1.x), Math.max(l2.x, l3.x));
        float minY = Math.min(Math.min(l0.y, l1.y), Math.min(l2.y, l3.y));
        float maxY = Math.max(Math.max(l0.y, l1.y), Math.max(l2.y, l3.y));
        float minZ = Math.min(Math.min(l0.z, l1.z), Math.min(l2.z, l3.z));
        float maxZ = Math.max(Math.max(l0.z, l1.z), Math.max(l2.z, l3.z));

        return !(maxX < -halfW || minX > halfW || maxY < -halfH || minY > halfH || maxZ < -halfD || minZ > halfD);
    }

    private void vertex(BufferBuilder builder, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v, Color color, int light, float nz)
    {
        VertexUtils.normal(builder.vertex(matrix, x, y, z).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light), normal, 0F, 0F, nz).next();
    }

    private void uploadTranslucent(BufferBuilder builder, Texture texture, Matrix4f matrix)
    {
        ShaderProgram finalShader = RenderSystem.getShader();
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

        buffer.bind();
        Bufs.upload(buffer, builder);
        VertexBuffer.unbind();

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        Vector3f origin = modelView.transformPosition(matrix.getTranslation(new Vector3f()));
        Vector3f planeNormal = FormTranslucentQueue.quadPlaneNormal(modelView, matrix);

        FormTranslucentQueue.add(new FormTranslucentQueue.VertexBufferCommand(
            buffer, () -> finalShader, texture, modelView, null, origin, planeNormal, true, null, null
        ));
    }
}
