package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.triggers.FlowGraph;
import mchorse.bbs_mod.triggers.FlowNodeTypes;
import mchorse.bbs_mod.triggers.FlowRuntime;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A trigger volume: a box around the block, who it reacts to, and the flow it runs. The server
 * watches the box every tick of the world: an entity found inside that was not there a tick ago has
 * entered, one still inside stays, one gone has left. Every step is a game tick, nothing is timed by
 * the wall clock.
 */
public class TriggerBoxBlockEntity extends BlockEntity
{
    public static final String TARGET_ALL_LIVING = "all_living";
    public static final String TARGET_PLAYER_ONLY = "player_only";
    public static final String TARGET_ACTOR_ID = "actor_id";

    public static final String ACTOR_PREFIX = "prefix";
    public static final String ACTOR_FILM = "film";

    /** The boxes ticking on the server right now, for the events that come from elsewhere: a use, a break. */
    private static final Set<TriggerBoxBlockEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    public float boxWidth = 3F;
    public float boxHeight = 2F;
    public float boxDepth = 3F;
    public float offsetX;
    public float offsetY;
    public float offsetZ;
    public boolean showOutline = true;
    public boolean showBox = true;
    public String target = TARGET_ALL_LIVING;
    /** One Actor: by name or UUID prefix, or as an actor of a film (and replay) */
    public String actorMode = ACTOR_PREFIX;
    public String actorId = "";
    public String actorFilm = "";
    public String actorReplay = "";
    public FlowGraph graph = FlowGraph.createDefault();

    /* Server state */
    private final FlowRuntime runtime = new FlowRuntime();
    private final Map<UUID, Integer> inside = new HashMap<>();
    private final Map<UUID, Float> health = new HashMap<>();
    private boolean runtimeDirty = true;

    public TriggerBoxBlockEntity(BlockPos pos, BlockState state)
    {
        super(BBSMod.TRIGGER_BOX_ENTITY, pos, state);
    }

    public static List<TriggerBoxBlockEntity> getActive(World world)
    {
        List<TriggerBoxBlockEntity> list = new ArrayList<>();

        synchronized (ACTIVE)
        {
            for (TriggerBoxBlockEntity box : ACTIVE)
            {
                if (box.getWorld() == world && !box.isRemoved())
                {
                    list.add(box);
                }
            }
        }

        return list;
    }

    /** The volume in the world: centred on the block across, standing on its floor, moved by the offset. */
    public Box getWorldBox()
    {
        double cx = this.pos.getX() + 0.5D + this.offsetX;
        double cz = this.pos.getZ() + 0.5D + this.offsetZ;
        double y = this.pos.getY() + this.offsetY;
        double hw = Math.max(0.05D, this.boxWidth) / 2D;
        double hd = Math.max(0.05D, this.boxDepth) / 2D;

        return new Box(cx - hw, y, cz - hd, cx + hw, y + Math.max(0.05D, this.boxHeight), cz + hd);
    }

    public boolean accepts(Entity entity)
    {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity.isSpectator())
        {
            return false;
        }

        return switch (this.target)
        {
            case TARGET_PLAYER_ONLY -> entity instanceof PlayerEntity;
            case TARGET_ACTOR_ID -> this.actorMode.equals(ACTOR_FILM) ? this.acceptsFilmActor(entity) : this.acceptsPrefix(entity);
            default -> true;
        };
    }

    /** Prefix mode: the UUID itself, or a name or custom name that starts with the text. */
    private boolean acceptsPrefix(Entity entity)
    {
        String prefix = this.actorId.trim();

        if (prefix.isEmpty())
        {
            return false;
        }

        return prefix.equals(entity.getUuidAsString()) || entity.getName().getString().startsWith(prefix)
            || (entity.getCustomName() != null && entity.getCustomName().getString().startsWith(prefix));
    }

    /** Film mode: a BBS actor of that film, and of that replay when one is picked. */
    private boolean acceptsFilmActor(Entity entity)
    {
        if (!(entity instanceof mchorse.bbs_mod.entity.ActorEntity actor) || this.actorFilm.isEmpty())
        {
            return false;
        }

        return this.actorFilm.equals(actor.getFilmId()) && (this.actorReplay.isEmpty() || this.actorReplay.equals(actor.getReplayId()));
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, TriggerBoxBlockEntity box)
    {
        if (world instanceof ServerWorld serverWorld)
        {
            box.tickServer(serverWorld);
        }
    }

    private void tickServer(ServerWorld world)
    {
        synchronized (ACTIVE)
        {
            ACTIVE.add(this);
        }

        if (this.runtimeDirty)
        {
            this.runtime.reset(this.graph, this.pos);
            this.runtimeDirty = false;
        }

        this.runtime.tick(world);

        List<LivingEntity> found = world.getEntitiesByClass(LivingEntity.class, this.getWorldBox(), this::accepts);
        Set<UUID> now = new HashSet<>();

        for (LivingEntity entity : found)
        {
            UUID uuid = entity.getUuid();
            Integer ticks = this.inside.get(uuid);

            now.add(uuid);

            if (ticks == null)
            {
                this.inside.put(uuid, 0);
                this.health.put(uuid, entity.getHealth());
                this.runtime.fire(world, FlowNodeTypes.ON_ENTER, entity, 0);
            }
            else
            {
                this.inside.put(uuid, ticks + 1);
                this.runtime.fire(world, FlowNodeTypes.ON_STAY, entity, ticks + 1);

                Float last = this.health.put(uuid, entity.getHealth());

                if (entity instanceof PlayerEntity && last != null && entity.getHealth() < last)
                {
                    this.runtime.fire(world, FlowNodeTypes.ON_PLAYER_HURT, entity, 0);
                }
            }
        }

        if (this.inside.size() > now.size())
        {
            for (UUID uuid : new ArrayList<>(this.inside.keySet()))
            {
                if (!now.contains(uuid))
                {
                    this.inside.remove(uuid);
                    this.health.remove(uuid);
                    this.runtime.fire(world, FlowNodeTypes.ON_EXIT, world.getEntity(uuid), 0);
                }
            }
        }
    }

    /** A player used a block or broke one: every box the spot is in, with that player inside, hears of it. */
    public static void onWorldEvent(World world, PlayerEntity player, BlockPos at, String event)
    {
        if (!(world instanceof ServerWorld serverWorld))
        {
            return;
        }

        Vec3d point = Vec3d.ofCenter(at);

        for (TriggerBoxBlockEntity box : getActive(world))
        {
            if (box.runtime.hasEvent(event) && box.accepts(player) && (box.getWorldBox().contains(point) || box.getWorldBox().intersects(player.getBoundingBox())))
            {
                box.runtime.fire(serverWorld, event, player, 0);
            }
        }
    }

    @Override
    public void markRemoved()
    {
        super.markRemoved();

        synchronized (ACTIVE)
        {
            ACTIVE.remove(this);
        }
    }

    /* Data */

    public MapType toData()
    {
        MapType data = new MapType();

        data.putFloat("box_width", this.boxWidth);
        data.putFloat("box_height", this.boxHeight);
        data.putFloat("box_depth", this.boxDepth);
        data.putFloat("offset_x", this.offsetX);
        data.putFloat("offset_y", this.offsetY);
        data.putFloat("offset_z", this.offsetZ);
        data.putBool("show_outline", this.showOutline);
        data.putBool("show_box", this.showBox);
        data.putString("target", this.target);
        data.putString("actor_id", this.actorId);
        data.putString("actor_mode", this.actorMode);
        data.putString("actor_film", this.actorFilm);
        data.putString("actor_replay", this.actorReplay);
        data.put("flow", this.graph.toData());

        return data;
    }

    public void fromData(MapType data)
    {
        this.boxWidth = clampSize(data.getFloat("box_width", 3F));
        this.boxHeight = clampSize(data.getFloat("box_height", 2F));
        this.boxDepth = clampSize(data.getFloat("box_depth", 3F));
        this.offsetX = data.getFloat("offset_x", 0F);
        this.offsetY = data.getFloat("offset_y", 0F);
        this.offsetZ = data.getFloat("offset_z", 0F);
        this.showOutline = data.getBool("show_outline", true);
        this.showBox = data.getBool("show_box", true);
        this.target = data.getString("target", TARGET_ALL_LIVING);
        this.actorId = data.getString("actor_id", "");
        this.actorMode = data.getString("actor_mode", ACTOR_PREFIX);
        this.actorFilm = data.getString("actor_film", "");
        this.actorReplay = data.getString("actor_replay", "");

        if (data.has("flow"))
        {
            this.graph.fromData(data.getMap("flow"));
        }

        this.runtimeDirty = true;
    }

    public static float clampSize(float size)
    {
        return Math.max(0.1F, Math.min(64F, size));
    }

    /** The editor's save, on the server: taken, stored, and sent back out to everyone watching. */
    public void apply(MapType data)
    {
        this.fromData(data);
        this.markDirty();

        if (this.world != null)
        {
            BlockState state = this.world.getBlockState(this.pos);

            this.world.updateListeners(this.pos, state, state, Block.NOTIFY_LISTENERS);
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket()
    {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    //? if >=1.21 {
    /*@Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup)
    {
        return createNbt(registryLookup);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup)
    {
        super.writeNbt(nbt, registryLookup);
    *///?} else {
    @Override
    public NbtCompound toInitialChunkDataNbt()
    {
        return createNbt();
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);
    //?}

        DataStorageUtils.writeToNbtCompound(nbt, "TriggerBox", this.toData());
    }

    //? if >=1.21 {
    /*@Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup)
    {
        super.readNbt(nbt, registryLookup);
    *///?} else {
    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);
    //?}

        BaseType data = DataStorageUtils.readFromNbtCompound(nbt, "TriggerBox");

        if (data != null && data.isMap())
        {
            this.fromData(data.asMap());
        }
    }
}
