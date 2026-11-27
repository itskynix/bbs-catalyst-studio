package mchorse.bbs_mod.recap;

import com.mojang.authlib.GameProfile;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker;
import mchorse.bbs_mod.mixin.client.RecapLimbAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The recorded entities drawn: for each look of each track a copy of the entity made from the look
 * once, in no world's list (it never ticks, is never hit, never heard), placed and posed as the
 * track has it at the moment drawn and drawn by the game's own renderer for its kind.
 *
 * <p>An entity that cannot be made or drawn (a kind gone from the game, a renderer that needs what
 * a copy does not have) is left out from then on, once noted in the log; it never stops the others
 * or the picture.</p>
 */
public final class RecapGhosts
{
    private static final double DRAW_RANGE = 192D;

    private final Map<RecapEntities.Look, Entity> made = new IdentityHashMap<>();
    private final Set<RecapEntities.Track> broken = Collections.newSetFromMap(new IdentityHashMap<>());
    private final RecapEntities.Frame frame = new RecapEntities.Frame();
    private ClientWorld world;
    private boolean warned;

    public void clear()
    {
        this.made.clear();
        this.broken.clear();
        this.world = null;
    }

    /** Draws every track there at {@code source}, of the client's dimension. */
    public void render(RecapEntities entities, float source, boolean players, boolean others, Vec3d camera, MatrixStack matrices, VertexConsumerProvider consumers)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld current = mc.world;

        if (entities == null || current == null)
        {
            return;
        }

        if (current != this.world)
        {
            /* Copies are of a world: a new one makes them anew */
            this.clear();
            this.world = current;
        }

        String here = RecapWorldOverlay.dimensionOf(current);
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        int tick = (int) Math.floor(source);
        double range = DRAW_RANGE * DRAW_RANGE;

        for (RecapEntities.Track track : entities.getTracks())
        {
            if ((track.player ? !players : !others) || this.broken.contains(track))
            {
                continue;
            }

            if (track.dimension != null && here != null && !track.dimension.equals(here))
            {
                continue;
            }

            if (!track.sample(source, this.frame))
            {
                continue;
            }

            double dx = this.frame.x - camera.x, dy = this.frame.y - camera.y, dz = this.frame.z - camera.z;

            if (dx * dx + dy * dy + dz * dz > range)
            {
                continue;
            }

            RecapEntities.Look look = track.lookAt(tick);

            if (look == null)
            {
                continue;
            }

            Entity ghost = this.made.get(look);

            if (ghost == null && !this.made.containsKey(look))
            {
                ghost = this.make(current, track, look);
                this.made.put(look, ghost);
            }

            if (ghost == null)
            {
                this.broken.add(track);

                continue;
            }

            /* The renderer moves to the entity and back on its own; a stack of its own, so one that
             * fails half way leaves the world's as it was */
            try
            {
                MatrixStack own = new MatrixStack();

                own.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
                own.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
                pose(ghost, this.frame, tick);
                dispatcher.render(ghost, dx, dy, dz, this.frame.yaw, 1F, own, consumers, dispatcher.getLight(ghost, 1F));
            }
            catch (Throwable e)
            {
                this.broken.add(track);
                this.warn("Recap could not draw a recorded " + track.type, e);
            }
        }
    }

    private Entity make(ClientWorld world, RecapEntities.Track track, RecapEntities.Look look)
    {
        try
        {
            NbtCompound nbt = StringNbtReader.parse(look.data());
            Entity entity;

            if (track.player)
            {
                UUID uuid;

                try
                {
                    uuid = UUID.fromString(track.uuid);
                }
                catch (IllegalArgumentException e)
                {
                    uuid = UUID.randomUUID();
                }

                OtherClientPlayerEntity player = new OtherClientPlayerEntity(world, new GameProfile(uuid, track.name.isEmpty() ? "Player" : track.name));

                player.readNbt(nbt);
                entity = player;
            }
            else
            {
                nbt.putString("id", track.type);
                entity = EntityType.getEntityFromNbt(nbt, world).orElse(null);
            }

            if (entity != null)
            {
                entity.setSilent(true);
                entity.noClip = true;
            }

            return entity;
        }
        catch (Throwable e)
        {
            this.warn("Recap could not make a recorded " + track.type, e);

            return null;
        }
    }

    /** Puts the copy where, as turned and as moving as the frame has it, with no further transition. */
    private static void pose(Entity ghost, RecapEntities.Frame frame, int tick)
    {
        ghost.setPos(frame.x, frame.y, frame.z);
        ghost.prevX = ghost.lastRenderX = frame.x;
        ghost.prevY = ghost.lastRenderY = frame.y;
        ghost.prevZ = ghost.lastRenderZ = frame.z;
        ghost.setYaw(frame.yaw);
        ghost.prevYaw = frame.yaw;
        ghost.setPitch(frame.pitch);
        ghost.prevPitch = frame.pitch;
        ghost.age = Math.max(0, tick);

        EntityPose[] poses = EntityPose.values();

        ghost.setPose(frame.pose >= 0 && frame.pose < poses.length ? poses[frame.pose] : EntityPose.STANDING);

        RecapEntityFlagInvoker flags = (RecapEntityFlagInvoker) ghost;

        for (int bit = 0; bit < 8; bit++)
        {
            flags.bbs$setFlag(bit, (frame.flags & (1 << bit)) != 0);
        }

        if (ghost instanceof LivingEntity living)
        {
            RecapLimbAccessor limbs = (RecapLimbAccessor) living.limbAnimator;

            living.bodyYaw = living.prevBodyYaw = frame.body;
            living.headYaw = living.prevHeadYaw = frame.head;
            limbs.bbs$setPos(frame.limbPos);
            limbs.bbs$setSpeed(frame.limbSpeed);
            limbs.bbs$setPrevSpeed(frame.limbSpeed);
            living.handSwingProgress = living.lastHandSwingProgress = frame.swing;
            living.hurtTime = frame.hurt;
            living.deathTime = frame.death;
        }
        else
        {
            ghost.setHeadYaw(frame.head);
        }
    }

    private void warn(String message, Throwable e)
    {
        if (!this.warned)
        {
            this.warned = true;
            BBSMod.LOGGER.warn(message, e);
        }
    }
}
