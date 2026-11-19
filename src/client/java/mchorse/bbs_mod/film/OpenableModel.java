package mchorse.bbs_mod.film;

import mchorse.bbs_mod.mixin.client.ChestBlockEntityAccessor;
import mchorse.bbs_mod.mixin.client.ChestLidAnimatorAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a door, trapdoor, fence gate or chest swung open a share of the way - what a block form with
 * an openness shows. A door, a trapdoor and a gate are their closed model turned about the hinge: the
 * hinge is the edge the closed and the open shapes of the block share, and the way open is the turn
 * that takes the one onto the other. A gate's two leaves turn each on its own post. A chest is its own
 * block entity with the lid raised.
 *
 * <p>With the pair, the other half of a door is drawn a block above (its hinge is on the same line, so
 * one turn serves both) and the other side of a double chest is drawn beside it.</p>
 */
public final class OpenableModel
{
    public enum Kind
    {
        DOOR, TRAPDOOR, GATE, CHEST
    }

    private static final Random RANDOM = Random.create();

    /** The hinge of each closed state, worked out once */
    private static final Map<BlockState, float[]> HINGES = new HashMap<>();

    /** Block entities for drawing chests, per state */
    private static final Map<BlockState, ChestBlockEntity> CHESTS = new HashMap<>();

    private OpenableModel()
    {}

    public static Kind kindOf(BlockState state)
    {
        if (state.isIn(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock)
        {
            return Kind.DOOR;
        }

        if (state.isIn(BlockTags.WOODEN_TRAPDOORS) && state.getBlock() instanceof TrapdoorBlock)
        {
            return Kind.TRAPDOOR;
        }

        if (state.isIn(BlockTags.FENCE_GATES) && state.getBlock() instanceof FenceGateBlock)
        {
            return Kind.GATE;
        }

        if (state.getBlock() instanceof ChestBlock)
        {
            return Kind.CHEST;
        }

        return null;
    }

    /** Where the other half of a door or the other side of a double chest is from this block, or null */
    public static BlockPos partnerOffset(BlockState state)
    {
        Kind kind = kindOf(state);

        if (kind == Kind.DOOR)
        {
            return state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? BlockPos.ORIGIN.up() : BlockPos.ORIGIN.down();
        }

        if (kind == Kind.CHEST && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
        {
            return BlockPos.ORIGIN.offset(ChestBlock.getFacing(state));
        }

        return null;
    }

    /**
     * Draws the block at {@code openness} of the way open, by {@code degrees} at full. Returns false when
     * the state is not an openable, and the caller draws it as it would any block.
     */
    public static boolean render(BlockState state, float openness, float degrees, boolean pair, MatrixStack stack, VertexConsumerProvider consumers, int light, int overlay)
    {
        Kind kind = kindOf(state);

        if (kind == null)
        {
            return false;
        }

        if (kind == Kind.CHEST)
        {
            renderChest(state, openness, stack, consumers, light, overlay);

            if (pair && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
            {
                BlockPos offset = partnerOffset(state);
                BlockState other = state.with(ChestBlock.CHEST_TYPE, state.get(ChestBlock.CHEST_TYPE).getOpposite());

                stack.push();
                stack.translate(offset.getX(), offset.getY(), offset.getZ());
                renderChest(other, openness, stack, consumers, light, overlay);
                stack.pop();
            }

            return true;
        }

        BlockState closed = state.with(Properties.OPEN, false);
        float radians = (float) Math.toRadians(Math.max(0F, Math.min(1F, openness)) * degrees);
        VertexConsumer consumer = consumers.getBuffer(RenderLayers.getEntityBlockLayer(closed, false));

        if (kind == Kind.GATE)
        {
            boolean alongX = closed.get(FenceGateBlock.FACING).getAxis() == Direction.Axis.Z;

            for (int half = 0; half < 2; half++)
            {
                float post = half == 0 ? 1F / 16F : 15F / 16F;
                float cx = alongX ? post : 0.5F;
                float cz = alongX ? 0.5F : post;

                stack.push();
                stack.translate(cx, 0F, cz);
                stack.multiply(new Quaternionf().rotateY((half == 0 ? 1F : -1F) * radians));
                stack.translate(-cx, 0F, -cz);
                emit(closed, consumer, stack, light, overlay, alongX, half);
                stack.pop();
            }

            return true;
        }

        float[] hinge = hinge(closed, kind);
        Vector3f axis = new Vector3f(hinge[3] == 0 ? 1F : 0F, hinge[3] == 1 ? 1F : 0F, hinge[3] == 2 ? 1F : 0F);

        stack.push();
        stack.translate(hinge[0], hinge[1], hinge[2]);
        stack.multiply(new Quaternionf().rotateAxis(hinge[4] * radians, axis));
        stack.translate(-hinge[0], -hinge[1], -hinge[2]);
        emit(closed, consumer, stack, light, overlay, false, -1);

        if (pair && kind == Kind.DOOR)
        {
            BlockPos offset = partnerOffset(closed);
            BlockState other = closed.with(DoorBlock.HALF, closed.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);

            /* The hinge is a vertical line through both halves, so the same turn carries the other one */
            stack.translate(offset.getX(), offset.getY(), offset.getZ());
            emit(other, consumer, stack, light, overlay, false, -1);
        }

        stack.pop();

        return true;
    }

    private static void renderChest(BlockState state, float openness, MatrixStack stack, VertexConsumerProvider consumers, int light, int overlay)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ChestBlockEntity chest = CHESTS.get(state);

        if (chest == null)
        {
            BlockEntity created = ((ChestBlock) state.getBlock()).createBlockEntity(BlockPos.ORIGIN, state);

            if (!(created instanceof ChestBlockEntity made))
            {
                return;
            }

            chest = made;
            CHESTS.put(state, chest);
        }

        if (chest.getWorld() != mc.world)
        {
            chest.setWorld(mc.world);
        }

        float value = Math.max(0F, Math.min(1F, openness));
        ChestLidAnimatorAccessor lid = (ChestLidAnimatorAccessor) ((ChestBlockEntityAccessor) chest).bbs$getLidAnimator();

        lid.bbs$setProgress(value);
        lid.bbs$setLastProgress(value);
        mc.getBlockEntityRenderDispatcher().renderEntity(chest, stack, consumers, light, overlay);
    }

    /**
     * The hinge of a door or trapdoor: x, y, z of a point on it in block units, the axis (0 x, 1 y, 2 z)
     * and the turn's sign.
     */
    private static float[] hinge(BlockState closed, Kind kind)
    {
        float[] cached = HINGES.get(closed);

        if (cached != null)
        {
            return cached;
        }

        Box c = closed.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBox();
        Box o = closed.with(Properties.OPEN, true).getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBox();
        int axis = kind == Kind.DOOR ? 1 : (closed.get(TrapdoorBlock.FACING).getAxis() == Direction.Axis.Z ? 0 : 2);
        double[] cmin = {c.minX, c.minY, c.minZ};
        double[] cmax = {c.maxX, c.maxY, c.maxZ};
        double[] omin = {o.minX, o.minY, o.minZ};
        double[] omax = {o.maxX, o.maxY, o.maxZ};
        float[] pivot = new float[3];

        for (int i = 0; i < 3; i++)
        {
            if (i == axis)
            {
                pivot[i] = (float) (cmin[i] + cmax[i]) / 2F;

                continue;
            }

            boolean low = Math.abs(cmin[i] - omin[i]) < 1.0E-4D || Math.abs(cmin[i] - omax[i]) < 1.0E-4D;
            boolean high = Math.abs(cmax[i] - omin[i]) < 1.0E-4D || Math.abs(cmax[i] - omax[i]) < 1.0E-4D;

            pivot[i] = (float) (high && !low ? cmax[i] : (low && !high ? cmin[i] : (cmax[i] + cmin[i]) / 2D));
        }

        Vector3f middle = new Vector3f((float) (cmin[0] + cmax[0]) / 2F, (float) (cmin[1] + cmax[1]) / 2F, (float) (cmin[2] + cmax[2]) / 2F);
        Vector3f goal = new Vector3f((float) (omin[0] + omax[0]) / 2F, (float) (omin[1] + omax[1]) / 2F, (float) (omin[2] + omax[2]) / 2F);
        Vector3f turnAxis = new Vector3f(axis == 0 ? 1F : 0F, axis == 1 ? 1F : 0F, axis == 2 ? 1F : 0F);
        Vector3f p = new Vector3f(pivot[0], pivot[1], pivot[2]);
        Vector3f plus = new Vector3f(middle).sub(p).rotate(new Quaternionf().rotateAxis((float) Math.PI / 2F, turnAxis)).add(p);
        Vector3f minus = new Vector3f(middle).sub(p).rotate(new Quaternionf().rotateAxis(-(float) Math.PI / 2F, turnAxis)).add(p);
        float sign = plus.distanceSquared(goal) <= minus.distanceSquared(goal) ? 1F : -1F;

        cached = new float[] {pivot[0], pivot[1], pivot[2], axis, sign};
        HINGES.put(closed, cached);

        return cached;
    }

    /** The model's quads, or for a gate one half of them by where they are along it */
    private static void emit(BlockState state, VertexConsumer consumer, MatrixStack stack, int light, int overlay, boolean alongX, int half)
    {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        List<Direction> sides = new ArrayList<>();

        sides.add(null);

        for (Direction direction : Direction.values())
        {
            sides.add(direction);
        }

        for (Direction side : sides)
        {
            for (BakedQuad quad : model.getQuads(state, side, RANDOM))
            {
                if (half >= 0)
                {
                    int[] data = quad.getVertexData();
                    float sum = 0F;

                    for (int v = 0; v < 4; v++)
                    {
                        sum += Float.intBitsToFloat(data[v * 8 + (alongX ? 0 : 2)]);
                    }

                    if ((sum / 4F < 0.5F) != (half == 0))
                    {
                        continue;
                    }
                }

                consumer.quad(stack.peek(), quad, 1F, 1F, 1F, light, overlay);
            }
        }
    }
}
