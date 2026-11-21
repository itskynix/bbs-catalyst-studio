package mchorse.bbs_mod.nav;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.colors.Color;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/**
 * What the navigation wand shows in the world while it is held, half see-through and over
 * everything: the box being selected with the face the wheel would push, the boxes already saved,
 * the blocks of the path as flat markers with a line to each neighbour they join to - and a bigger
 * marker where three or more ways meet, which is where an actor will have a choice to make.
 */
public final class NavigationWandRenderer
{
    private static final float[] PAINT = {0.22F, 0.82F, 1F};
    private static final float[] SAVED = {0.29F, 0.87F, 0.5F};

    private static final float FACE_HOVER = 0.28F;
    private static final double RANGE = 96D;

    /** The saved network, read now and then and not every frame */
    private static NavigationNetwork saved = new NavigationNetwork();
    private static long refreshed = 0L;

    private static final Color COLOR = new Color();

    private NavigationWandRenderer()
    {}

    /** Reads the saved network again at the next frame, after it changed */
    public static void refresh()
    {
        refreshed = 0L;
    }

    private static NavigationNetwork saved()
    {
        long now = System.currentTimeMillis();

        if (now - refreshed > 750L)
        {
            saved = NavigationNetworks.sceneNetwork(NavigationNetworks.activeFilm());
            refreshed = now;
        }

        return saved;
    }

    public static void renderWorld(WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        boolean holding = mc.player != null && NavigationWandController.isHolding(mc.player);

        if (mc.player == null || mc.world == null || (!holding && !NavPreview.isVisible()))
        {
            NavigationWandController.setView(null, null);

            return;
        }

        if (!holding)
        {
            /* The preview key alone: what is saved, and the volumes, nothing of the wand's own */
            NavigationWandController.setView(null, null);
            beginOverlay();
            renderSaved(mc.world, context.matrixStack(), context.camera().getPos(), saved());
            endOverlay();

            return;
        }

        float tickDelta = context./*$ wtd*/tickDelta()/**/;
        Vec3d camera = context.camera().getPos();
        Box box = NavigationSelection.getBox();
        boolean boxMode = NavigationWandController.getMode() == NavigationWandController.Mode.BOX;
        BlockPos pick = boxMode ? NavigationWandController.pickBox(mc) : NavigationWandController.pickBlock(mc);
        Direction face = box == null || !boxMode ? null : findFace(mc.player.getCameraPosVec(tickDelta), mc.player.getRotationVec(tickDelta), box);

        NavigationWandController.setView(face, pick);

        MatrixStack stack = context.matrixStack();
        NavigationNetwork network = saved();

        beginOverlay();
        renderSaved(mc.world, stack, camera, network);

        if (box != null)
        {
            renderSelection(stack, camera, box, face);
        }

        BlockPos a = NavigationSelection.getA();
        BlockPos b = NavigationSelection.getB();

        if (a != null)
        {
            renderBlock(stack, camera, a, 0.31F, 0.71F, 1F, 0.3F);
        }

        if (b != null)
        {
            renderBlock(stack, camera, b, 1F, 0.69F, 0.29F, 0.3F);
        }

        renderPath(mc.world, stack, camera, NavigationSelection.getPath(), PAINT, 1F);

        if (pick != null && !pick.equals(a) && !pick.equals(b))
        {
            stack.push();
            stack.translate(pick.getX() - camera.x, pick.getY() - camera.y, pick.getZ() - camera.z);
            Draw.renderBox(stack, 0, 0, 0, 1, 1, 1, 1F, 1F, 1F, 0.35F);
            stack.pop();
        }

        endOverlay();
    }

    private static void beginOverlay()
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
    }

    private static void endOverlay()
    {
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * What is saved for the scene: each box as a clear emerald frame with a faint fill, the saved path
     * with its lines, and the box of the navigation volume of the film, in its own colour.
     */
    private static void renderSaved(ClientWorld world, MatrixStack stack, Vec3d camera, NavigationNetwork network)
    {
        for (NavigationNetwork.Box saved : network.boxes)
        {
            stack.push();
            stack.translate(saved.x() - camera.x, saved.y() - camera.y, saved.z() - camera.z);

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            Draw.fillBox(builder, stack, 0, 0, 0, saved.sx(), saved.sy(), saved.sz(), SAVED[0], SAVED[1], SAVED[2], 0.07F);
            Bufs.draw(builder);
            Draw.renderBox(stack, 0, 0, 0, saved.sx(), saved.sy(), saved.sz(), SAVED[0], SAVED[1], SAVED[2], 1F, 0.04F);
            stack.pop();
        }

        renderPath(world, stack, camera, network.path, SAVED, 0.9F);

        AiSimulation.Volume volume = AiSimulation.findVolume(NavigationNetworks.activeFilm());

        if (volume != null && volume.form() != null)
        {
            float w = volume.form().width.get();
            float h = volume.form().height.get();
            float l = volume.form().length.get();

            stack.push();
            stack.translate(volume.x() - w / 2D - camera.x, volume.y() - camera.y, volume.z() - l / 2D - camera.z);
            Draw.renderBox(stack, 0, 0, 0, w, h, l, 0.3F, 0.9F, 1F, 0.9F, 0.04F);
            stack.pop();
        }
    }

    private static void renderSelection(MatrixStack stack, Vec3d camera, Box box, Direction face)
    {
        float w = (float) (box.maxX - box.minX);
        float h = (float) (box.maxY - box.minY);
        float d = (float) (box.maxZ - box.minZ);

        COLOR.set(BBSSettings.primaryColor.get());

        stack.push();
        stack.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);

        if (face != null)
        {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            fillFace(builder, stack, face, w, h, d, COLOR.r, COLOR.g, COLOR.b, FACE_HOVER);
            Bufs.draw(builder);
        }

        Draw.renderBox(stack, 0, 0, 0, w, h, d, COLOR.r, COLOR.g, COLOR.b, 1F);

        if (face != null)
        {
            boolean positive = face.getDirection() == Direction.AxisDirection.POSITIVE;
            Direction.Axis axis = face.getAxis();
            float x = axis == Direction.Axis.X && positive ? w : 0;
            float y = axis == Direction.Axis.Y && positive ? h : 0;
            float z = axis == Direction.Axis.Z && positive ? d : 0;

            Draw.renderBox(stack, x, y, z, axis == Direction.Axis.X ? 0 : w, axis == Direction.Axis.Y ? 0 : h, axis == Direction.Axis.Z ? 0 : d, 1F, 1F, 1F, 0.9F);
        }

        stack.pop();
    }

    private static void fillFace(BufferBuilder builder, MatrixStack stack, Direction side, float w, float h, float d, float r, float g, float b, float a)
    {
        switch (side)
        {
            case WEST -> Draw.fillQuad(builder, stack, 0, 0, 0, 0, 0, d, 0, h, d, 0, h, 0, r, g, b, a);
            case EAST -> Draw.fillQuad(builder, stack, w, 0, 0, w, 0, d, w, h, d, w, h, 0, r, g, b, a);
            case DOWN -> Draw.fillQuad(builder, stack, 0, 0, 0, w, 0, 0, w, 0, d, 0, 0, d, r, g, b, a);
            case UP -> Draw.fillQuad(builder, stack, 0, h, 0, w, h, 0, w, h, d, 0, h, d, r, g, b, a);
            case NORTH -> Draw.fillQuad(builder, stack, 0, 0, 0, w, 0, 0, w, h, 0, 0, h, 0, r, g, b, a);
            case SOUTH -> Draw.fillQuad(builder, stack, 0, 0, d, w, 0, d, w, h, d, 0, h, d, r, g, b, a);
        }
    }

    private static void renderBlock(MatrixStack stack, Vec3d camera, BlockPos pos, float r, float g, float b, float fill)
    {
        stack.push();
        stack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        Draw.fillBox(builder, stack, 0, 0, 0, 1, 1, 1, r, g, b, fill);
        Bufs.draw(builder);
        Draw.renderBox(stack, 0, 0, 0, 1, 1, 1, r, g, b, 1F);
        stack.pop();
    }

    /** The ray's first face of a box, or the one it leaves by from inside it; null when it misses */
    private static Direction findFace(Vec3d origin, Vec3d direction, Box box)
    {
        double tNear = Double.NEGATIVE_INFINITY;
        double tFar = Double.POSITIVE_INFINITY;
        Direction near = null;
        Direction far = null;

        for (Direction.Axis axis : Direction.Axis.values())
        {
            double o = origin.getComponentAlongAxis(axis);
            double d = direction.getComponentAlongAxis(axis);
            double lo = box.getMin(axis);
            double hi = box.getMax(axis);

            if (Math.abs(d) < 1E-9)
            {
                if (o < lo || o > hi)
                {
                    return null;
                }

                continue;
            }

            double tLo = (lo - o) / d;
            double tHi = (hi - o) / d;
            Direction loFace = Direction.from(axis, Direction.AxisDirection.NEGATIVE);
            Direction hiFace = Direction.from(axis, Direction.AxisDirection.POSITIVE);
            double tEnter = Math.min(tLo, tHi);
            double tExit = Math.max(tLo, tHi);

            if (tEnter > tNear)
            {
                tNear = tEnter;
                near = tLo < tHi ? loFace : hiFace;
            }

            if (tExit < tFar)
            {
                tFar = tExit;
                far = tLo < tHi ? hiFace : loFace;
            }
        }

        if (tNear > tFar || tFar < 0)
        {
            return null;
        }

        return tNear > 0 ? near : far;
    }

    /** The height the marks of a path are drawn at over the block it is: just over its top face */
    private static final double TOP = 1.03D;

    /** The four ways a path joins: north, south, east, west */
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * The blocks of a path as flat markers on their top face, and a line from each to the neighbours it
     * joins in the four directions, a step up or down at most; drawn from the top middle of the one
     * block, (x + 0.5, y + 1.03, z + 0.5), to the top middle of the other. A block that joins three
     * ways or four is a junction and gets a bigger, yellow marker.
     */
    private static void renderPath(ClientWorld world, MatrixStack stack, Vec3d camera, Set<BlockPos> blocks, float[] color, float alpha)
    {
        if (blocks.isEmpty())
        {
            return;
        }

        List<BlockPos> shown = new ArrayList<>();
        Map<Long, List<BlockPos>> columns = new HashMap<>();

        for (BlockPos pos : blocks)
        {
            if (pos.getSquaredDistance(camera.x, camera.y, camera.z) > RANGE * RANGE)
            {
                continue;
            }

            shown.add(pos);
            columns.computeIfAbsent(column(pos.getX(), pos.getZ()), (k) -> new ArrayList<>()).add(pos);
        }

        stack.push();
        stack.translate(-camera.x, -camera.y, -camera.z);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (BlockPos pos : shown)
        {
            int ways = 0;

            for (int[] side : SIDES)
            {
                BlockPos other = joined(columns, pos, side[0], side[1]);

                if (other == null)
                {
                    continue;
                }

                ways++;

                /* each pair once: from the one to the east or south of the other */
                if (side[0] > 0 || side[1] > 0)
                {
                    line(builder, stack, pos, other, color, alpha * 0.85F);
                }
            }

            boolean junction = ways >= 3;
            float half = junction ? 0.28F : 0.16F;
            float x = pos.getX() + 0.5F;
            float y = (float) (pos.getY() + TOP);
            float z = pos.getZ() + 0.5F;

            Draw.fillBox(builder, stack, x - half, y - 0.02F, z - half, x + half, y + 0.04F, z + half,
                junction ? 1F : color[0], junction ? 0.85F : color[1], junction ? 0.3F : color[2], alpha * 0.9F);
        }

        Bufs.draw(builder);
        stack.pop();
    }

    private static long column(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** The block of the path next to one the given way that it joins: in the next column, a block higher or lower at most */
    private static BlockPos joined(Map<Long, List<BlockPos>> columns, BlockPos pos, int dx, int dz)
    {
        List<BlockPos> column = columns.get(column(pos.getX() + dx, pos.getZ() + dz));

        if (column == null)
        {
            return null;
        }

        BlockPos best = null;

        for (BlockPos other : column)
        {
            int dy = Math.abs(other.getY() - pos.getY());

            if (dy <= 1 && (best == null || dy < Math.abs(best.getY() - pos.getY())))
            {
                best = other;
            }
        }

        return best;
    }

    /**
     * A line from the top middle of one block to the top middle of another, as two thin ribbons crossed
     * along it - one lying flat, one standing - so it reads from above and from the side alike.
     */
    private static void line(BufferBuilder builder, MatrixStack stack, BlockPos from, BlockPos to, float[] color, float alpha)
    {
        float x1 = from.getX() + 0.5F;
        float y1 = (float) (from.getY() + TOP);
        float z1 = from.getZ() + 0.5F;
        float x2 = to.getX() + 0.5F;
        float y2 = (float) (to.getY() + TOP);
        float z2 = to.getZ() + 0.5F;
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4F)
        {
            return;
        }

        /* Across the line, flat on the ground */
        float px = -dz / length * 0.05F;
        float pz = dx / length * 0.05F;
        float h = 0.05F;

        Draw.fillQuad(builder, stack, x1 + px, y1, z1 + pz, x2 + px, y2, z2 + pz, x2 - px, y2, z2 - pz, x1 - px, y1, z1 - pz, color[0], color[1], color[2], alpha);
        Draw.fillQuad(builder, stack, x1, y1 - h, z1, x2, y2 - h, z2, x2, y2 + h, z2, x1, y1 + h, z1, color[0], color[1], color[2], alpha);
    }
}
