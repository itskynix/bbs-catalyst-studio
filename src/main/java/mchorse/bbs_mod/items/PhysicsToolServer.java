package mchorse.bbs_mod.items;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The server's half of the physics and gravity guns in the world. A player's client says what it grabbed and,
 * every tick, where the hold point is; the server moves the thing there, since entities and blocks are the
 * server's to move. Three kinds can be held: any entity but a player, any block that can be broken (it comes out
 * of the ground as a falling block and lands back as a block), and a BBS model block, whose form is moved
 * inside its block.
 *
 * <p>Only a player in creative or with operator rights can do any of it: pulling blocks out of the world is not
 * a survival tool.</p>
 */
public final class PhysicsToolServer
{
    public static final byte GRAB = 0;
    public static final byte MOVE = 1;
    public static final byte RELEASE = 2;
    public static final byte FREEZE = 3;
    public static final byte LAUNCH = 4;

    public static final byte KIND_ENTITY = 0;
    public static final byte KIND_BLOCK = 1;
    public static final byte KIND_MODEL_BLOCK = 2;

    private static final double REACH = 64D;
    private static final double MAX_PULL = 3D;

    private static final class Hold
    {
        Entity entity;
        BlockPos model;
        Vec3d target;
        boolean hadGravity;
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    private PhysicsToolServer()
    {}

    public static boolean isHolding(PlayerEntity player, Hand hand)
    {
        return player != null && player.getStackInHand(hand).getItem() instanceof PhysicsToolItem;
    }

    public static boolean allowed(PlayerEntity player)
    {
        return player.isCreative() || player.hasPermissionLevel(2);
    }

    public static void register()
    {
        /* The tools never break, place, open or hit anything the vanilla way, on either side */
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isHolding(player, hand) ? ActionResult.FAIL : ActionResult.PASS);

        ServerTickEvents.END_SERVER_TICK.register(PhysicsToolServer::tick);
    }

    public static void handle(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        byte action = buf.readByte();

        switch (action)
        {
            case GRAB ->
            {
                byte kind = buf.readByte();
                int id = kind == KIND_ENTITY ? buf.readInt() : 0;
                BlockPos pos = kind == KIND_ENTITY ? null : buf.readBlockPos();

                server.execute(() -> grab(player, kind, id, pos));
            }
            case MOVE ->
            {
                Vec3d target = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                float yaw = buf.readFloat();
                float pitch = buf.readFloat();

                server.execute(() -> move(player, target, yaw, pitch));
            }
            case RELEASE ->
            {
                Vec3d velocity = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());

                server.execute(() -> release(player, velocity));
            }
            case FREEZE -> server.execute(() -> freeze(player));
            case LAUNCH ->
            {
                byte kind = buf.readByte();
                int id = kind == KIND_ENTITY ? buf.readInt() : 0;
                BlockPos pos = kind == KIND_ENTITY ? null : buf.readBlockPos();
                Vec3d velocity = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());

                server.execute(() -> launch(player, kind, id, pos, velocity));
            }
            default -> {}
        }
    }

    private static boolean ready(ServerPlayerEntity player)
    {
        return player.isAlive() && allowed(player) && (isHolding(player, Hand.MAIN_HAND) || isHolding(player, Hand.OFF_HAND));
    }

    private static Vec3d clampVelocity(Vec3d velocity)
    {
        double length = velocity.length();

        return length > MAX_PULL * 2D ? velocity.multiply(MAX_PULL * 2D / length) : velocity;
    }

    /** Takes hold of something: an entity as it is, a block torn out as a falling block, a model block's form */
    private static void grab(ServerPlayerEntity player, byte kind, int id, BlockPos pos)
    {
        if (!ready(player))
        {
            return;
        }

        release(player, Vec3d.ZERO);

        World world = player.getWorld();
        Hold hold = new Hold();

        if (kind == KIND_ENTITY)
        {
            Entity entity = world.getEntityById(id);

            if (entity == null || entity instanceof PlayerEntity || !entity.isAlive() || player.squaredDistanceTo(entity) > REACH * REACH)
            {
                return;
            }

            hold.entity = entity;
        }
        else if (kind == KIND_BLOCK)
        {
            Entity block = tearOut(world, player, pos);

            if (block == null)
            {
                return;
            }

            hold.entity = block;
        }
        else if (kind == KIND_MODEL_BLOCK)
        {
            if (!(world.getBlockEntity(pos) instanceof ModelBlockEntity) || player.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH * REACH)
            {
                return;
            }

            hold.model = pos;
        }
        else
        {
            return;
        }

        if (hold.entity != null)
        {
            hold.hadGravity = !hold.entity.hasNoGravity();
            hold.entity.setNoGravity(true);
            hold.target = hold.entity.getPos().add(0D, hold.entity.getHeight() / 2D, 0D);
        }
        else
        {
            hold.target = Vec3d.ofCenter(hold.model);
        }

        HOLDS.put(player.getUuid(), hold);
    }

    /** A block that can be broken, with no block entity of its own, comes out of the world as a falling block */
    private static FallingBlockEntity tearOut(World world, ServerPlayerEntity player, BlockPos pos)
    {
        if (pos == null || player.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH * REACH)
        {
            return null;
        }

        BlockState state = world.getBlockState(pos);
        BlockEntity entity = world.getBlockEntity(pos);

        if (state.isAir() || state.getHardness(world, pos) < 0F || entity != null || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.block.FluidBlock)
        {
            return null;
        }

        FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos, state);

        falling.dropItem = true;

        return falling;
    }

    private static void move(ServerPlayerEntity player, Vec3d target, float yaw, float pitch)
    {
        Hold hold = HOLDS.get(player.getUuid());

        if (hold == null || target.squaredDistanceTo(player.getEyePos()) > REACH * REACH)
        {
            return;
        }

        hold.target = target;

        if (yaw == 0F && pitch == 0F)
        {
            return;
        }

        if (hold.entity != null)
        {
            hold.entity.setYaw(hold.entity.getYaw() + yaw);
            hold.entity.setPitch(hold.entity.getPitch() + pitch);
            hold.entity.setHeadYaw(hold.entity.getHeadYaw() + yaw);
        }
        else if (player.getWorld().getBlockEntity(hold.model) instanceof ModelBlockEntity model)
        {
            Transform transform = model.getProperties().getTransform();

            transform.rotate.y += (float) Math.toRadians(-yaw);
            transform.rotate.x += (float) Math.toRadians(pitch);
            sync(player.getWorld(), hold.model);
        }
    }

    /** Lets go: the thing keeps the speed the hand gave it and falls again */
    private static void release(ServerPlayerEntity player, Vec3d velocity)
    {
        Hold hold = HOLDS.remove(player.getUuid());

        if (hold == null || hold.entity == null || hold.entity.isRemoved())
        {
            return;
        }

        hold.entity.setNoGravity(!hold.hadGravity);
        hold.entity.setVelocity(clampVelocity(velocity));
        hold.entity.velocityModified = true;
    }

    /** Leaves it in the air where it is: an entity floats still, a block is set back into the world right there */
    private static void freeze(ServerPlayerEntity player)
    {
        Hold hold = HOLDS.remove(player.getUuid());

        if (hold == null || hold.entity == null || hold.entity.isRemoved())
        {
            return;
        }

        if (hold.entity instanceof FallingBlockEntity falling)
        {
            World world = player.getWorld();
            BlockPos at = BlockPos.ofFloored(falling.getPos());

            if (world.getBlockState(at).isReplaceable())
            {
                world.setBlockState(at, falling.getBlockState(), Block.NOTIFY_ALL);
                falling.discard();

                return;
            }
        }

        hold.entity.setNoGravity(true);
        hold.entity.setVelocity(Vec3d.ZERO);
        hold.entity.velocityModified = true;
    }

    /** The gravity gun's shot: what is held flies off, or else what the player looked at */
    private static void launch(ServerPlayerEntity player, byte kind, int id, BlockPos pos, Vec3d velocity)
    {
        if (!ready(player))
        {
            return;
        }

        if (HOLDS.containsKey(player.getUuid()))
        {
            release(player, velocity);

            return;
        }

        World world = player.getWorld();
        Entity target = null;

        if (kind == KIND_ENTITY)
        {
            target = world.getEntityById(id);

            if (target instanceof PlayerEntity || target != null && player.squaredDistanceTo(target) > REACH * REACH)
            {
                target = null;
            }
        }
        else if (kind == KIND_BLOCK)
        {
            target = tearOut(world, player, pos);
        }

        if (target != null)
        {
            target.setVelocity(clampVelocity(velocity));
            target.velocityModified = true;
        }
    }

    private static void tick(MinecraftServer server)
    {
        Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<UUID, Hold> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Hold hold = entry.getValue();

            if (player == null || !ready(player))
            {
                if (hold.entity != null && !hold.entity.isRemoved())
                {
                    hold.entity.setNoGravity(!hold.hadGravity);
                }

                it.remove();

                continue;
            }

            if (hold.entity != null)
            {
                Entity entity = hold.entity;

                if (entity.isRemoved())
                {
                    it.remove();

                    continue;
                }

                /* Pulled towards the hold point a good share of the gap each tick: smooth, and it still bumps into things */
                Vec3d center = entity.getPos().add(0D, entity.getHeight() / 2D, 0D);
                Vec3d pull = hold.target.subtract(center).multiply(0.45D);

                entity.setVelocity(pull.length() > MAX_PULL ? pull.normalize().multiply(MAX_PULL) : pull);
                entity.velocityModified = true;
                entity.fallDistance = 0F;

                /* Every tick, not at the entity's own tracking rate (a falling block's is once a second): clients
                 * glide between these, so a held thing moves smoothly for everyone who sees it */
                if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld world)
                {
                    world.getChunkManager().sendToNearbyPlayers(entity, new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(entity));
                }

                if (entity instanceof FallingBlockEntity falling)
                {
                    /* A falling block that floats too long turns into an item; held, it never runs out of time */
                    falling.timeFalling = 1;
                }
            }
            else if (player.getWorld().getBlockEntity(hold.model) instanceof ModelBlockEntity model)
            {
                /* The form moves inside its block: the offset from the block's bottom centre to the hold point */
                Transform transform = model.getProperties().getTransform();
                Vec3d base = new Vec3d(hold.model.getX() + 0.5D, hold.model.getY(), hold.model.getZ() + 0.5D);
                Vec3d offset = hold.target.subtract(base);

                transform.translate.set((float) offset.x, (float) offset.y, (float) offset.z);
                sync(player.getWorld(), hold.model);
            }
            else
            {
                it.remove();
            }
        }
    }

    private static void sync(World world, BlockPos pos)
    {
        BlockState state = world.getBlockState(pos);

        world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        world.markDirty(pos);
    }
}
