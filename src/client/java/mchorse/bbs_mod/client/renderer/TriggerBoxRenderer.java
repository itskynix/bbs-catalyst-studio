package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Bufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Draws a trigger box in the world only when someone is meant to see it. With the debug screen
 * (F3) open, its volume and its block get a solid outline, the way a model block's hitbox does;
 * while its editor is open, the volume gets a dashed outline. In plain play, and with the HUD hidden
 * (F1), nothing at all.
 */
public class TriggerBoxRenderer implements BlockEntityRenderer<TriggerBoxBlockEntity>
{
    /** The box the editor has open. */
    public static BlockPos editing;

    private static final float DASH = 0.3F;
    private static final float GAP = 0.2F;

    public TriggerBoxRenderer(BlockEntityRendererFactory.Context context)
    {}

    @Override
    public void render(TriggerBoxBlockEntity box, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.options.hudHidden || mc.player == null)
        {
            return;
        }

        BlockPos pos = box.getPos();
        boolean edited = pos.equals(editing);
        //? if >=1.20.4 {
        boolean debug = mc.getDebugHud().shouldShowDebugHud();
        //?} else {
        /*boolean debug = mc.options.debugEnabled;
        *///?}

        if (!edited && !debug)
        {
            return;
        }

        Box volume = box.getWorldBox().offset(-pos.getX(), -pos.getY(), -pos.getZ());

        RenderSystem.disableDepthTest();

        if (edited)
        {
            dashed(matrices, volume, 1F, 0.72F, 0.25F, 1F, 0.03F);
            Draw.renderBox(matrices, 0.3D, 0.3D, 0.3D, 0.4D, 0.4D, 0.4D, 1F, 0.72F, 0.25F, 0.9F);
        }
        else
        {
            Draw.renderBox(matrices, volume.minX, volume.minY, volume.minZ, volume.getLengthX(), volume.getLengthY(), volume.getLengthZ(), 1F, 0.72F, 0.25F, 1F);
            Draw.renderBox(matrices, 0D, 0D, 0D, 1D, 1D, 1D, 0F, 0.5F, 1F, 0.5F);
        }

        RenderSystem.enableDepthTest();
    }

    /** The twelve edges of the box as dashes of thin bars, each edge's dashes spread to end on its corners. */
    private static void dashed(MatrixStack matrices, Box b, float r, float g, float bl, float a, float t)
    {
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        double[][] corners = new double[8][];

        for (int i = 0; i < 8; i++)
        {
            corners[i] = new double[] {(i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ};
        }

        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

        for (int[] e : edges)
        {
            double[] p = corners[e[0]];
            double[] q = corners[e[1]];
            double length = Math.sqrt((q[0] - p[0]) * (q[0] - p[0]) + (q[1] - p[1]) * (q[1] - p[1]) + (q[2] - p[2]) * (q[2] - p[2]));
            int dashes = Math.max(1, (int) Math.round((length + GAP) / (DASH + GAP)));
            double step = length / dashes;
            double dash = step * DASH / (DASH + GAP);

            for (int i = 0; i < dashes; i++)
            {
                double s0 = (i * step + (step - dash) / 2D) / length;
                double s1 = s0 + dash / length;
                float x0 = (float) (p[0] + (q[0] - p[0]) * s0);
                float y0 = (float) (p[1] + (q[1] - p[1]) * s0);
                float z0 = (float) (p[2] + (q[2] - p[2]) * s0);
                float x1 = (float) (p[0] + (q[0] - p[0]) * s1);
                float y1 = (float) (p[1] + (q[1] - p[1]) * s1);
                float z1 = (float) (p[2] + (q[2] - p[2]) * s1);

                Draw.fillBox(builder, matrices, Math.min(x0, x1) - t, Math.min(y0, y1) - t, Math.min(z0, z1) - t, Math.max(x0, x1) + t, Math.max(y0, y1) + t, Math.max(z0, z1) + t, r, g, bl, a);
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        Bufs.draw(builder);
    }

    @Override
    public boolean rendersOutsideBoundingBox(TriggerBoxBlockEntity box)
    {
        return true;
    }

    @Override
    public int getRenderDistance()
    {
        return 256;
    }
}
