package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.mixin.client.ChestBlockEntityAccessor;
import mchorse.bbs_mod.mixin.client.ChestLidAnimatorAccessor;
import mchorse.bbs_mod.network.ClientNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interactive openables: a door, trapdoor, fence gate or chest that is opened a share of the way at a
 * time instead of at once.
 *
 * <p><b>While recording</b>, with the replay's setting on, holding use on one takes the mouse: moving
 * it forward opens it, back closes it, in proportion. Letting go finishes the job - open past the
 * threshold and it swings the rest of the way open, short of it and it swings shut - and only then is
 * the ordinary click sent, so the world, the server and the recording see one plain interaction. The
 * hold itself is told to the server first, so the recorded click carries it (see
 * {@code OpenableSwings}).</p>
 *
 * <p><b>While playing</b>, a recorded click that carries a hold swings the openable the same way
 * before the click lands.</p>
 *
 * <p>Drawing: the vanilla block is taken out of the client's world for the length of the swing (the
 * server is not told; the real block is put back, or replaced by the server's, when it ends) and its
 * closed model is drawn turned about its hinge by the angle. A chest is not hidden: its lid is driven
 * instead. The angle only goes up to a chest's 90 degrees.</p>
 */
public final class OpenableAnimator
{
    /** Pixels of mouse movement that open or close it all the way */
    private static final float PIXELS_FULL = 260F;

    /** Ticks a finished swing takes to settle onto the vanilla angle */
    private static final int SETTLE_TICKS = 6;

    /** Ticks to wait for the server to show the new state before putting the old one back */
    private static final int ARRIVAL_TICKS = 20;

    private static final int CHEST_HOLD_TICKS = 8;

    private static final Map<BlockPos, Anim> ANIMS = new HashMap<>();
    private static final Random RANDOM = Random.create();

    private static Drag drag;

    private enum Kind
    {
        DOOR, TRAPDOOR, GATE, CHEST
    }

    private static class Anim
    {
        BlockPos pos;
        Kind kind;
        BlockState original;
        boolean hidden;

        float prevOpenness;
        float openness;
        /** The angle in degrees at full openness */
        float degrees = 90F;

        boolean playback;
        boolean touched;
        /** Ticks since the swing ended, while it waits for the server or for the vanilla chest to take over; -1 while it goes on */
        int finishing = -1;

        /* The hinge, in block coordinates: where the axis passes, which axis (0 x, 1 y, 2 z) and which way is open */
        float px;
        float py;
        float pz;
        int axis;
        float sign = 1F;
    }

    private static class Drag
    {
        BlockHitResult hit;
        List<Anim> anims = new ArrayList<>();
        boolean startOpen;
        float start;
        float openness;
        float threshold;
        float angle;
        int ticks;
        /** 0 while the hand moves it, then the ticks into the settle */
        int settle = -1;
        float from;
        float target;
    }

    private OpenableAnimator()
    {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register((mc) -> tick());
        WorldRenderEvents.START.register((context) -> driveChests(context./*$ wtd*/tickDelta()/**/));
    }

    /* What can be opened this way */

    private static Kind kindOf(BlockState state)
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

    /** The replay being recorded right now, or null */
    private static Replay recordingReplay()
    {
        mchorse.bbs_mod.film.Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder == null || recorder.film == null)
        {
            return null;
        }

        List<Replay> replays = recorder.film.replays.getList();

        return recorder.exception >= 0 && recorder.exception < replays.size() ? replays.get(recorder.exception) : null;
    }

    /* Live: the hand */

    /** Called before the game handles a use click; true when it was taken */
    public static boolean onUse()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (drag != null)
        {
            return true;
        }

        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null)
        {
            return false;
        }

        Replay replay = recordingReplay();

        if (replay == null || !replay.interactiveOpenables.get())
        {
            return false;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
        {
            return false;
        }

        /* Sneaking with something in hand places it, as ever */
        if (mc.player.shouldCancelInteraction() && (!mc.player.getMainHandStack().isEmpty() || !mc.player.getOffHandStack().isEmpty()))
        {
            return false;
        }

        BlockState state = mc.world.getBlockState(hit.getBlockPos());

        if (kindOf(state) == null)
        {
            return false;
        }

        List<Anim> anims = begin(mc.world, hit.getBlockPos(), false);

        if (anims.isEmpty())
        {
            return false;
        }

        Drag next = new Drag();

        next.hit = hit;
        next.anims = anims;
        next.startOpen = isOpen(state);
        next.start = next.startOpen ? 1F : 0F;
        next.openness = next.start;
        next.threshold = replay.openableThreshold.get();
        next.angle = replay.openableAngle.get();

        for (Anim anim : anims)
        {
            anim.degrees = next.angle;
            anim.openness = anim.prevOpenness = next.openness;
        }

        drag = next;

        return true;
    }

    /** The mouse, while a hold is on: forward opens, back closes. True when it was taken */
    public static boolean consumeLook(double dx, double dy)
    {
        Drag current = drag;

        if (current == null || current.settle >= 0)
        {
            return current != null;
        }

        current.openness = Math.max(0F, Math.min(1F, current.openness - (float) dy / PIXELS_FULL));

        for (Anim anim : current.anims)
        {
            anim.prevOpenness = anim.openness = current.openness;
        }

        return true;
    }

    private static boolean isOpen(BlockState state)
    {
        if (state.getBlock() instanceof ChestBlock)
        {
            return false;
        }

        return state.contains(Properties.OPEN) && state.get(Properties.OPEN);
    }

    private static void tick()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null)
        {
            clear();

            return;
        }

        if (drag != null)
        {
            tickDrag(mc);
        }

        tickAnims(mc.world);
    }

    private static void tickDrag(MinecraftClient mc)
    {
        Drag current = drag;

        current.ticks += 1;

        if (mc.currentScreen != null || !current.anims.stream().allMatch((a) -> a.hidden || a.kind == Kind.CHEST))
        {
            cancelDrag(mc.world);

            return;
        }

        if (current.settle < 0)
        {
            if (mc.options.useKey.isPressed())
            {
                return;
            }

            /* Let go: past the threshold it opens, short of it it closes */
            current.settle = 0;
            current.from = current.openness;
            current.target = current.openness >= current.threshold ? 1F : 0F;
        }

        current.settle += 1;

        float t = Math.min(1F, current.settle / (float) SETTLE_TICKS);
        float eased = t * t * (3F - 2F * t);
        float openness = current.from + (current.target - current.from) * eased;

        for (Anim anim : current.anims)
        {
            anim.prevOpenness = anim.openness;
            anim.openness = openness;
            /* The swing ends on the angle the real block has */
            anim.degrees = current.angle + (90F - current.angle) * eased;
        }

        current.openness = openness;

        if (current.settle >= SETTLE_TICKS)
        {
            finish(mc, current);
        }
    }

    /** The hold is over: the real block is put back and, if it has to change, the one ordinary click is sent */
    private static void finish(MinecraftClient mc, Drag current)
    {
        drag = null;

        boolean wantOpen = current.target >= 0.5F;
        boolean chest = current.anims.get(0).kind == Kind.CHEST;
        boolean change = chest ? wantOpen : wantOpen != current.startOpen;

        for (Anim anim : current.anims)
        {
            show(mc.world, anim);
        }

        if (change)
        {
            ClientNetwork.sendOpenableSwing(current.hit.getBlockPos(), current.start, current.target, current.ticks, current.angle);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, current.hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        for (Anim anim : current.anims)
        {
            if (chest)
            {
                /* The chest's own lid takes over once the server has opened it */
                anim.finishing = change ? 0 : CHEST_HOLD_TICKS;
            }
            else
            {
                ANIMS.remove(anim.pos);
            }
        }
    }

    private static void cancelDrag(ClientWorld world)
    {
        Drag current = drag;

        drag = null;

        if (current == null)
        {
            return;
        }

        for (Anim anim : current.anims)
        {
            show(world, anim);
            ANIMS.remove(anim.pos);
        }
    }

    /* Playing: what a recorded click carries */

    /** Called every tick for a replay while a film plays or is scrubbed; swings what the replay's recorded clicks swung */
    public static void playback(Replay replay, int ticks)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || replay == null)
        {
            return;
        }

        for (InteractBlockActionClip clip : replay.actions.getClips(InteractBlockActionClip.class))
        {
            if (!clip.enabled.get() || clip.swingFrom.get() < 0F || clip.swingTicks.get() <= 0)
            {
                continue;
            }

            int end = clip.tick.get();
            int start = end - clip.swingTicks.get();

            if (ticks < start || ticks > end)
            {
                continue;
            }

            BlockPos pos = clip.hit.getBlockPos();
            float p = (ticks - start) / (float) Math.max(1, clip.swingTicks.get());
            float eased = p * p * (3F - 2F * p);
            float openness = clip.swingFrom.get() + (clip.swingTo.get() - clip.swingFrom.get()) * eased;
            float angle = clip.swingAngle.get();

            Anim anim = ANIMS.get(pos);

            if (anim == null)
            {
                List<Anim> made = begin(mc.world, pos, true);

                if (made.isEmpty())
                {
                    continue;
                }

                for (Anim a : made)
                {
                    a.openness = a.prevOpenness = openness;
                }

                anim = ANIMS.get(pos);
            }

            if (anim == null || !anim.playback)
            {
                continue;
            }

            for (Anim a : partsOf(anim))
            {
                a.prevOpenness = a.openness;
                a.openness = openness;
                a.degrees = angle + (90F - angle) * p * p * p * p;
                a.touched = true;
                a.finishing = -1;
            }
        }
    }

    private static List<Anim> partsOf(Anim anim)
    {
        List<Anim> parts = new ArrayList<>();

        parts.add(anim);

        if (anim.kind == Kind.DOOR)
        {
            Anim other = ANIMS.get(anim.original.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? anim.pos.up() : anim.pos.down());

            if (other != null)
            {
                parts.add(other);
            }
        }
        else if (anim.kind == Kind.CHEST && anim.original.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
        {
            Anim other = ANIMS.get(anim.pos.offset(ChestBlock.getFacing(anim.original)));

            if (other != null)
            {
                parts.add(other);
            }
        }

        return parts;
    }

    /* Lifetimes */

    private static void tickAnims(ClientWorld world)
    {
        Iterator<Anim> it = ANIMS.values().iterator();

        while (it.hasNext())
        {
            Anim anim = it.next();

            if (anim.playback)
            {
                if (anim.touched)
                {
                    anim.touched = false;

                    continue;
                }

                if (anim.finishing < 0)
                {
                    anim.finishing = 0;
                }
            }

            if (anim.finishing < 0)
            {
                continue;
            }

            anim.finishing += 1;

            if (anim.kind == Kind.CHEST)
            {
                if (anim.finishing > CHEST_HOLD_TICKS)
                {
                    it.remove();
                }

                continue;
            }

            /* The server has shown the block's new state once the client's world has something where the air was */
            boolean arrived = anim.hidden && !world.getBlockState(anim.pos).isAir();

            if (arrived)
            {
                anim.hidden = false;
                it.remove();
            }
            else if (anim.finishing > ARRIVAL_TICKS)
            {
                show(world, anim);
                it.remove();
            }
        }
    }

    /** Forgets everything, putting back what was taken out of the world */
    public static void clear()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        drag = null;

        if (mc.world != null)
        {
            for (Anim anim : ANIMS.values())
            {
                show(mc.world, anim);
            }
        }

        ANIMS.clear();
    }

    /* The block */

    /** Starts swinging the block at {@code pos} and its other half; the animations made, empty when it cannot be */
    private static List<Anim> begin(ClientWorld world, BlockPos pos, boolean playback)
    {
        List<Anim> made = new ArrayList<>();
        BlockState state = world.getBlockState(pos);
        Kind kind = kindOf(state);

        if (kind == null)
        {
            return made;
        }

        List<BlockPos> positions = new ArrayList<>();

        positions.add(pos);

        if (kind == Kind.DOOR)
        {
            positions.add(state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down());
        }
        else if (kind == Kind.CHEST && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
        {
            positions.add(pos.offset(ChestBlock.getFacing(state)));
        }

        for (BlockPos at : positions)
        {
            BlockState current = world.getBlockState(at);

            if (kindOf(current) != kind || ANIMS.containsKey(at))
            {
                continue;
            }

            Anim anim = new Anim();

            anim.pos = at;
            anim.kind = kind;
            anim.original = current;
            anim.playback = playback;

            if (kind != Kind.CHEST)
            {
                hinge(world, anim);
            }

            ANIMS.put(at, anim);
            made.add(anim);
        }

        /* Out of the world once they are all registered, so a half cannot be mistaken for something else */
        for (Anim anim : made)
        {
            if (anim.kind != Kind.CHEST)
            {
                world.setBlockState(anim.pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                anim.hidden = true;
            }
        }

        return made;
    }

    private static void show(ClientWorld world, Anim anim)
    {
        if (world != null && anim.hidden)
        {
            if (world.getBlockState(anim.pos).isAir())
            {
                world.setBlockState(anim.pos, anim.original, Block.NOTIFY_LISTENERS);
            }

            anim.hidden = false;
        }
    }

    /**
     * Where a block turns and which way: for a door and a trapdoor, from the two shapes the block takes
     * closed and open - the hinge is the edge both share; the way open is the turn that takes the closed
     * leaf onto the open one.
     */
    private static void hinge(ClientWorld world, Anim anim)
    {
        BlockState closed = anim.original.with(Properties.OPEN, false);
        BlockState open = anim.original.with(Properties.OPEN, true);

        if (anim.kind == Kind.GATE)
        {
            /* Two leaves, each on its own post; the halves are told apart when drawn */
            anim.axis = 1;
            anim.sign = 1F;

            return;
        }

        Box c = closed.getOutlineShape(world, anim.pos).getBoundingBox();
        Box o = open.getOutlineShape(world, anim.pos).getBoundingBox();

        if (anim.kind == Kind.DOOR)
        {
            anim.axis = 1;
        }
        else
        {
            anim.axis = anim.original.get(TrapdoorBlock.FACING).getAxis() == Direction.Axis.Z ? 0 : 2;
        }

        double[] cmin = {c.minX, c.minY, c.minZ};
        double[] cmax = {c.maxX, c.maxY, c.maxZ};
        double[] omin = {o.minX, o.minY, o.minZ};
        double[] omax = {o.maxX, o.maxY, o.maxZ};
        double[] pivot = new double[3];

        for (int i = 0; i < 3; i++)
        {
            if (i == anim.axis)
            {
                pivot[i] = (cmin[i] + cmax[i]) / 2D;

                continue;
            }

            boolean lowShared = Math.abs(cmin[i] - omin[i]) < 1.0E-4D || Math.abs(cmin[i] - omax[i]) < 1.0E-4D;
            boolean highShared = Math.abs(cmax[i] - omin[i]) < 1.0E-4D || Math.abs(cmax[i] - omax[i]) < 1.0E-4D;

            pivot[i] = highShared && !lowShared ? cmax[i] : (lowShared && !highShared ? cmin[i] : (cmax[i] + cmin[i]) / 2D);
        }

        anim.px = (float) pivot[0];
        anim.py = (float) pivot[1];
        anim.pz = (float) pivot[2];

        /* Which way takes the closed leaf's middle nearest the open leaf's */
        Vector3f middle = new Vector3f((float) (cmin[0] + cmax[0]) / 2F, (float) (cmin[1] + cmax[1]) / 2F, (float) (cmin[2] + cmax[2]) / 2F);
        Vector3f goal = new Vector3f((float) (omin[0] + omax[0]) / 2F, (float) (omin[1] + omax[1]) / 2F, (float) (omin[2] + omax[2]) / 2F);
        Vector3f axis = new Vector3f(anim.axis == 0 ? 1F : 0F, anim.axis == 1 ? 1F : 0F, anim.axis == 2 ? 1F : 0F);
        Vector3f pivotPoint = new Vector3f(anim.px, anim.py, anim.pz);
        Vector3f plus = new Vector3f(middle).sub(pivotPoint).rotate(new Quaternionf().rotateAxis((float) Math.PI / 2F, axis)).add(pivotPoint);
        Vector3f minus = new Vector3f(middle).sub(pivotPoint).rotate(new Quaternionf().rotateAxis(-(float) Math.PI / 2F, axis)).add(pivotPoint);

        anim.sign = plus.distanceSquared(goal) <= minus.distanceSquared(goal) ? 1F : -1F;
    }

    /* Drawing */

    private static void driveChests(float delta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || ANIMS.isEmpty())
        {
            return;
        }

        for (Anim anim : ANIMS.values())
        {
            if (anim.kind != Kind.CHEST || anim.finishing > 0 && drag == null && !anim.playback && anim.finishing > CHEST_HOLD_TICKS)
            {
                continue;
            }

            BlockEntity entity = mc.world.getBlockEntity(anim.pos);

            if (entity instanceof ChestBlockEntity chest)
            {
                float value = anim.prevOpenness + (anim.openness - anim.prevOpenness) * delta;
                ChestLidAnimatorAccessor lid = (ChestLidAnimatorAccessor) ((ChestBlockEntityAccessor) chest).bbs$getLidAnimator();

                lid.bbs$setProgress(value);
                lid.bbs$setLastProgress(value);
            }
        }
    }

    /** Draws what is swinging; called after the world's entities */
    public static void render(WorldRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || ANIMS.isEmpty() || context.consumers() == null)
        {
            return;
        }

        float delta = context./*$ wtd*/tickDelta()/**/;
        Vec3d camera = context.camera().getPos();
        MatrixStack stack = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        RenderLayer layer = RenderLayer.getEntityCutout(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        boolean drew = false;

        for (Anim anim : ANIMS.values())
        {
            if (anim.kind == Kind.CHEST || !anim.hidden)
            {
                continue;
            }

            float openness = anim.prevOpenness + (anim.openness - anim.prevOpenness) * delta;
            float radians = (float) Math.toRadians(openness * anim.degrees);
            BlockState closed = anim.original.with(Properties.OPEN, false);
            BakedModel model = mc.getBlockRenderManager().getModel(closed);
            int light = WorldRenderer.getLightmapCoordinates(mc.world, anim.pos);
            VertexConsumer consumer = consumers.getBuffer(layer);

            stack.push();
            stack.translate(anim.pos.getX() - camera.x, anim.pos.getY() - camera.y, anim.pos.getZ() - camera.z);

            if (anim.kind == Kind.GATE)
            {
                boolean alongX = closed.get(FenceGateBlock.FACING).getAxis() == Direction.Axis.Z;

                for (int half = 0; half < 2; half++)
                {
                    stack.push();

                    float post = half == 0 ? 1F / 16F : 15F / 16F;
                    float turn = (half == 0 ? 1F : -1F) * radians;
                    float cx = alongX ? post : 0.5F;
                    float cz = alongX ? 0.5F : post;

                    stack.translate(cx, 0F, cz);
                    stack.multiply(new Quaternionf().rotateY(turn));
                    stack.translate(-cx, 0F, -cz);
                    emit(model, closed, consumer, stack, light, alongX, half);
                    stack.pop();
                }
            }
            else
            {
                Vector3f axis = new Vector3f(anim.axis == 0 ? 1F : 0F, anim.axis == 1 ? 1F : 0F, anim.axis == 2 ? 1F : 0F);

                stack.translate(anim.px, anim.py, anim.pz);
                stack.multiply(new Quaternionf().rotateAxis(anim.sign * radians, axis));
                stack.translate(-anim.px, -anim.py, -anim.pz);
                emit(model, closed, consumer, stack, light, false, -1);
            }

            stack.pop();
            drew = true;
        }

        if (drew && consumers instanceof VertexConsumerProvider.Immediate immediate)
        {
            immediate.draw(layer);
        }
    }

    /** The model's quads, or one half of them (by where they are along the gate) for a gate */
    private static void emit(BakedModel model, BlockState state, VertexConsumer consumer, MatrixStack stack, int light, boolean alongX, int half)
    {
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

                consumer.quad(stack.peek(), quad, 1F, 1F, 1F, light, OverlayTexture.DEFAULT_UV);
            }
        }
    }
}
