package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Draws a trigger box in the world: the volume as a faint fill when Show Box is on, its outline when
 * Show Outline is on or the box is aimed at or being edited, and the block itself to creative players.
 * Nothing at all while the HUD is hidden, so F1 shots stay clean.
 */
public class TriggerBoxRenderer implements BlockEntityRenderer<TriggerBoxBlockEntity>
{
    /** The box the editor has open, drawn bright. */
    public static BlockPos editing;

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
        boolean aimed = mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
        boolean creative = mc.player.isCreative();
        Box volume = box.getWorldBox().offset(-pos.getX(), -pos.getY(), -pos.getZ());

        if (box.showBox)
        {
            WorldRenderer.renderFilledBox(matrices, consumers.getBuffer(RenderLayer.getDebugFilledBox()),
                volume.minX, volume.minY, volume.minZ, volume.maxX, volume.maxY, volume.maxZ,
                1F, 0.71F, 0.28F, edited ? 0.16F : 0.09F);
        }

        if (box.showOutline || aimed || edited)
        {
            float bright = edited || aimed ? 1F : 0.8F;

            WorldRenderer.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), volume, bright, 0.71F * bright, 0.28F * bright, 1F);
        }

        if (creative)
        {
            WorldRenderer.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), new Box(0.3D, 0.3D, 0.3D, 0.7D, 0.7D, 0.7D), 1F, 0.85F, 0.4F, 0.9F);
        }
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
