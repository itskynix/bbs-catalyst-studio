package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Every other entity around the recorded player, tick by tick.
 *
 * <p>Version 2 deduplicates entity look NBT across all tracks through {@link RecapPool}, removes
 * the 64-look cap per entity, bounds concurrent active nearby entities ({@link #ACTIVE_MAX})
 * instead of lifetime total tracks, and uses a lightweight equipment/state signature so
 * {@code entity.writeNbt} only runs when visual state changed.</p>
 */
public class RecapEntities
{
    private static final int MAGIC = 0x52434e31;
    private static final int VERSION = 2;

    /** Range around the recorded player and maximum concurrent nearby entities tracked per tick. */
    public static final double RANGE = 96D;
    public static final int MAX = 512;
    public static final int ACTIVE_MAX = 512;

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    private static final Set<String> VOLATILE = Set.of("Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown",
        "HurtTime", "HurtByTimestamp", "DeathTime", "Brain", "Attributes", "Age", "Health", "AbsorptionAmount", "PickupDelay", "Time", "fuse", "Fuse",
        "life", "Life", "XpP", "XpLevel", "XpTotal", "XpSeed", "foodLevel", "foodSaturationLevel", "foodExhaustionLevel", "foodTickTimer", "Score",
        "abilities", "recipeBook", "EnderItems", "LastDeathLocation", "warden_spawn_tracker", "SleepTimer", "FallFlying", "TicksFrozen", "AngerTime",
        "AngryAt", "InLove", "ForcedAge", "Leash", "Passengers", "Motive", "Dimension", "playerGameType", "previousPlayerGameType", "seenCredits",
        "current_explosion_impact_pos", "ignore_fall_damage_from_current_explosion", "current_impulse_context_reset_grace_time", "SpawnX", "SpawnY", "SpawnZ");

    private final RecapPool pool = new RecapPool();
    private final List<Look> sharedLooks = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();

    /* Recording: the tracks of the entities seen last tick, by their id + dimension */
    private final Map<String, Track> live = new HashMap<>();
    private int sweep;

    /** One look of an entity from a tick on: its data as the game saves it, the changing part left out. */
    public record Look(int tick, String data)
    {}

    /** One entity from its first tick to its last. */
    public static final class Track
    {
        public final String uuid;
        public final String type;
        public final String dimension;
        public final String name;
        public final boolean player;
        public int first;
        public int last;
        public final List<Look> looks = new ArrayList<>();

        int[] lookTicks = new int[4];
        int[] lookPoolIds = new int[4];
        int lookCount;

        int[] ticks = new int[8];
        double[] x = new double[8];
        double[] y = new double[8];
        double[] z = new double[8];
        float[] yaw = new float[8];
        float[] pitch = new float[8];
        float[] head = new float[8];
        float[] body = new float[8];
        float[] limbPos = new float[8];
        float[] limbSpeed = new float[8];
        float[] swing = new float[8];
        byte[] pose = new byte[8];
        byte[] flags = new byte[8];
        byte[] hurt = new byte[8];
        byte[] death = new byte[8];
        int size;

        /* Recording only */
        int lastPoolId = -1;
        long lastSignature;
        int lastLookTick = Integer.MIN_VALUE;

        Track(String uuid, String type, String dimension, String name, boolean player, int first)
        {
            this.uuid = uuid;
            this.type = type;
            this.dimension = dimension;
            this.name = name;
            this.player = player;
            this.first = first;
            this.last = first;
        }

        public int frames()
        {
            return this.size;
        }

        void pushLook(int tick, int poolId, Look lookObject)
        {
            if (this.lookCount == this.lookTicks.length)
            {
                int cap = this.lookCount * 2;

                this.lookTicks = Arrays.copyOf(this.lookTicks, cap);
                this.lookPoolIds = Arrays.copyOf(this.lookPoolIds, cap);
            }

            this.lookTicks[this.lookCount] = tick;
            this.lookPoolIds[this.lookCount] = poolId;
            this.lookCount += 1;
            this.looks.add(lookObject);
            this.lastPoolId = poolId;
        }

        void push(int tick, double x, double y, double z, float yaw, float pitch, float head, float body, float limbPos, float limbSpeed, float swing, int pose, int flags, int hurt, int death)
        {
            if (this.size == this.ticks.length)
            {
                int capacity = this.size * 2;

                this.ticks = Arrays.copyOf(this.ticks, capacity);
                this.x = Arrays.copyOf(this.x, capacity);
                this.y = Arrays.copyOf(this.y, capacity);
                this.z = Arrays.copyOf(this.z, capacity);
                this.yaw = Arrays.copyOf(this.yaw, capacity);
                this.pitch = Arrays.copyOf(this.pitch, capacity);
                this.head = Arrays.copyOf(this.head, capacity);
                this.body = Arrays.copyOf(this.body, capacity);
                this.limbPos = Arrays.copyOf(this.limbPos, capacity);
                this.limbSpeed = Arrays.copyOf(this.limbSpeed, capacity);
                this.swing = Arrays.copyOf(this.swing, capacity);
                this.pose = Arrays.copyOf(this.pose, capacity);
                this.flags = Arrays.copyOf(this.flags, capacity);
                this.hurt = Arrays.copyOf(this.hurt, capacity);
                this.death = Arrays.copyOf(this.death, capacity);
            }

            int i = this.size++;

            this.ticks[i] = tick;
            this.x[i] = x;
            this.y[i] = y;
            this.z[i] = z;
            this.yaw[i] = yaw;
            this.pitch[i] = pitch;
            this.head[i] = head;
            this.body[i] = body;
            this.limbPos[i] = limbPos;
            this.limbSpeed[i] = limbSpeed;
            this.swing[i] = swing;
            this.pose[i] = (byte) pose;
            this.flags[i] = (byte) flags;
            this.hurt[i] = (byte) hurt;
            this.death[i] = (byte) death;
        }

        /** Copies frame {@code i} of another track, at {@code tick}. */
        void copy(Track from, int i, int tick)
        {
            this.push(tick, from.x[i], from.y[i], from.z[i], from.yaw[i], from.pitch[i], from.head[i], from.body[i], from.limbPos[i], from.limbSpeed[i],
                from.swing[i], from.pose[i], from.flags[i], from.hurt[i], from.death[i]);
        }

        private boolean same(int i, double x, double y, double z, float yaw, float pitch, float head, float body, float limbPos, float limbSpeed, float swing, int pose, int flags, int hurt, int death)
        {
            return this.x[i] == x && this.y[i] == y && this.z[i] == z && this.yaw[i] == yaw && this.pitch[i] == pitch && this.head[i] == head && this.body[i] == body
                && this.limbPos[i] == limbPos && this.limbSpeed[i] == limbSpeed && this.swing[i] == swing && this.pose[i] == pose && this.flags[i] == flags
                && this.hurt[i] == hurt && this.death[i] == death;
        }

        /** The last frame at or before {@code tick}, or -1. */
        int frameAt(int tick)
        {
            int low = 0;
            int high = this.size - 1;
            int found = -1;

            while (low <= high)
            {
                int middle = (low + high) >>> 1;

                if (this.ticks[middle] <= tick)
                {
                    found = middle;
                    low = middle + 1;
                }
                else
                {
                    high = middle - 1;
                }
            }

            return found;
        }

        /** Binary-searched look at {@code tick}: the last at or before it, else the first. */
        public Look lookAt(int tick)
        {
            if (this.looks.isEmpty())
            {
                return null;
            }

            int low = 0;
            int high = this.looks.size() - 1;
            int found = 0;

            while (low <= high)
            {
                int mid = (low + high) >>> 1;

                if (this.looks.get(mid).tick <= tick)
                {
                    found = mid;
                    low = mid + 1;
                }
                else
                {
                    high = mid - 1;
                }
            }

            return this.looks.get(found);
        }

        int lookIndexAt(int tick)
        {
            if (this.lookCount == 0)
            {
                return -1;
            }

            int low = 0;
            int high = this.lookCount - 1;
            int found = 0;

            while (low <= high)
            {
                int mid = (low + high) >>> 1;

                if (this.lookTicks[mid] <= tick)
                {
                    found = mid;
                    low = mid + 1;
                }
                else
                {
                    high = mid - 1;
                }
            }

            return found;
        }

        /** Whether the entity was there at {@code time}. */
        public boolean isAt(float time)
        {
            int tick = (int) Math.floor(time);

            return tick >= this.first && tick <= this.last && this.size > 0;
        }

        public boolean sample(float time, Frame out)
        {
            if (!this.isAt(time))
            {
                return false;
            }

            int tick = (int) Math.floor(time);
            int a = this.frameAt(tick);

            if (a < 0)
            {
                a = 0;
            }

            int b = a + 1 < this.size && this.ticks[a + 1] == this.ticks[a] + 1 && tick == this.ticks[a] ? a + 1 : a;
            float t = b == a ? 0F : time - tick;

            out.x = this.x[a] + (this.x[b] - this.x[a]) * t;
            out.y = this.y[a] + (this.y[b] - this.y[a]) * t;
            out.z = this.z[a] + (this.z[b] - this.z[a]) * t;
            out.yaw = turn(this.yaw[a], this.yaw[b], t);
            out.pitch = this.pitch[a] + (this.pitch[b] - this.pitch[a]) * t;
            out.head = turn(this.head[a], this.head[b], t);
            out.body = turn(this.body[a], this.body[b], t);
            out.limbPos = this.limbPos[a] + (this.limbPos[b] - this.limbPos[a]) * t;
            out.limbSpeed = this.limbSpeed[a] + (this.limbSpeed[b] - this.limbSpeed[a]) * t;
            out.swing = this.swing[b] >= this.swing[a] ? this.swing[a] + (this.swing[b] - this.swing[a]) * t : this.swing[a];
            out.pose = this.pose[t < 0.5F ? a : b];
            out.flags = this.flags[t < 0.5F ? a : b];
            out.hurt = this.hurt[a];
            out.death = this.death[a];

            return true;
        }
    }

    /** One moment of a track. */
    public static class Frame
    {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float head;
        public float body;
        public float limbPos;
        public float limbSpeed;
        public float swing;
        public int pose;
        public int flags;
        public int hurt;
        public int death;
    }

    private static float turn(float a, float b, float t)
    {
        float delta = b - a;

        delta -= 360F * Math.round(delta / 360F);

        return a + delta * t;
    }

    public List<Track> getTracks()
    {
        return this.tracks;
    }

    public boolean isEmpty()
    {
        return this.tracks.isEmpty();
    }

    public int size()
    {
        return this.tracks.size();
    }

    public void clear()
    {
        this.pool.clear();
        this.sharedLooks.clear();
        this.tracks.clear();
        this.live.clear();
    }

    /** Interns a look string in the pool, returning a canonical {@link Look} instance for that tick and pool entry. */
    private int internLook(String data)
    {
        int poolId = this.pool.putString(data == null || data.isEmpty() ? "{}" : data);

        while (this.sharedLooks.size() <= poolId)
        {
            int idx = this.sharedLooks.size();

            this.sharedLooks.add(new Look(0, this.pool.getString(idx)));
        }

        return poolId;
    }

    private void attachLook(Track track, int tick, int poolId)
    {
        if (poolId < 0)
        {
            poolId = this.internLook("{}");
        }

        /* When tick == 0 we can share the canonical Look object directly; otherwise wrap with the same pooled String */
        Look canonical = poolId < this.sharedLooks.size() ? this.sharedLooks.get(poolId) : new Look(0, this.pool.getString(poolId));
        Look look = tick == 0 ? canonical : new Look(tick, canonical.data);

        track.pushLook(tick, poolId, look);
    }

    /* Recording */

    private static String liveKey(String uuid, String dimension)
    {
        return dimension == null ? uuid : dimension + "|" + uuid;
    }

    public void record(Iterable<Entity> entities, Entity skip, Entity around, int tick, String dimension)
    {
        int active = 0;
        double range = RANGE * RANGE;
        this.sweep += 1;

        for (Entity entity : entities)
        {
            if (entity == skip || entity.isRemoved() || (around != null && entity.squaredDistanceTo(around) > range))
            {
                continue;
            }

            if (active >= ACTIVE_MAX)
            {
                break;
            }

            String uuid = entity.getUuidAsString();
            String key = liveKey(uuid, dimension);
            Track track = this.live.get(key);

            if (track == null)
            {
                String type = EntityType.getId(entity.getType()).toString();
                boolean player = entity instanceof PlayerEntity;

                track = new Track(uuid, type, dimension, player ? entity.getName().getString() : "", player, tick);
                this.tracks.add(track);
                this.live.put(key, track);
            }

            active += 1;
            track.last = tick;
            this.lookOf(track, entity, tick);
            this.frameOf(track, entity, tick);
        }

        /* Entities that left range or died close their live track without blocking future entities */
        Iterator<Track> it = this.live.values().iterator();

        while (it.hasNext())
        {
            if (it.next().last != tick)
            {
                it.remove();
            }
        }
    }

    /** Fast zero-allocation visual state signature to skip redundant full entity NBT writes. */
    private static long signatureOf(Entity entity)
    {
        long sig = 1469598103934665603L;

        sig = (sig ^ entity.getPose().ordinal()) * 1099511628211L;
        sig = (sig ^ (entity.isInvisible() ? 1L : 0L)) * 1099511628211L;
        sig = (sig ^ (entity.isGlowing() ? 2L : 0L)) * 1099511628211L;

        Text customName = entity.getCustomName();

        if (customName != null)
        {
            sig = (sig ^ customName.getString().hashCode()) * 1099511628211L;
        }

        if (entity instanceof LivingEntity living)
        {
            sig = (sig ^ (living.isBaby() ? 1L : 0L)) * 1099511628211L;

            for (EquipmentSlot slot : EQUIPMENT_SLOTS)
            {
                ItemStack stack = living.getEquippedStack(slot);

                if (!stack.isEmpty())
                {
                    sig = (sig ^ Item.getRawId(stack.getItem())) * 1099511628211L;
                    sig = (sig ^ stack.getCount()) * 1099511628211L;
                    sig = (sig ^ stack.getDamage()) * 1099511628211L;

                    NbtCompound tag = stack.getNbt();

                    if (tag != null)
                    {
                        sig = (sig ^ tag.hashCode()) * 1099511628211L;
                    }
                }
                else
                {
                    sig *= 1099511628211L;
                }
            }
        }

        return sig == 0L ? 1L : sig;
    }

    private void lookOf(Track track, Entity entity, int tick)
    {
        long signature = signatureOf(entity);
        boolean first = track.lookCount == 0;
        boolean changed = signature != track.lastSignature;
        boolean periodic = !first && tick - track.lastLookTick >= 200 && Math.floorMod(tick + entity.getId(), 40) == 0;

        if (!first && !changed && !periodic)
        {
            return;
        }

        /* Throttle rapid equipment flicker to at most once every 4 ticks */
        if (!first && changed && tick - track.lastLookTick < 4)
        {
            return;
        }

        String data;

        try
        {
            NbtCompound nbt = new NbtCompound();

            entity.writeNbt(nbt);

            for (String key : VOLATILE)
            {
                nbt.remove(key);
            }

            data = nbt.toString();
        }
        catch (Throwable e)
        {
            data = "{}";
        }

        int poolId = this.internLook(data);

        if (first || poolId != track.lastPoolId)
        {
            this.attachLook(track, tick, poolId);
        }

        track.lastSignature = signature;
        track.lastLookTick = tick;
    }

    private void frameOf(Track track, Entity entity, int tick)
    {
        float head = entity.getHeadYaw();
        float body = entity.getYaw();
        float limbPos = 0F, limbSpeed = 0F, swing = 0F;
        int hurt = 0, death = 0;

        if (entity instanceof LivingEntity living)
        {
            body = living.bodyYaw;
            limbPos = living.limbAnimator.getPos();
            limbSpeed = living.limbAnimator.getSpeed();
            swing = living.handSwingProgress;
            hurt = living.hurtTime;
            death = living.deathTime;
        }

        int flags = 0;
        RecapEntityFlagInvoker invoker = (RecapEntityFlagInvoker) entity;

        for (int bit = 0; bit < 8; bit++)
        {
            if (invoker.bbs$getFlag(bit))
            {
                flags |= 1 << bit;
            }
        }

        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        float yaw = entity.getYaw(), pitch = entity.getPitch();
        int pose = entity.getPose().ordinal();
        int last = track.size - 1;

        if (last >= 0 && track.same(last, x, y, z, yaw, pitch, head, body, limbPos, limbSpeed, swing, pose, flags, hurt, death))
        {
            return;
        }

        if (last >= 0 && track.ticks[last] < tick - 1)
        {
            track.copy(track, last, tick - 1);
        }

        track.push(tick, x, y, z, yaw, pitch, head, body, limbPos, limbSpeed, swing, pose, flags, hurt, death);
    }

    /** Merges another slice's tracks into this stream, continuing contiguous entity tracks seamlessly. */
    public void append(RecapEntities other, int tickOffset)
    {
        if (other == null || other.isEmpty())
        {
            return;
        }

        int[] remap = this.pool.mergeFrom(other.pool);

        while (this.sharedLooks.size() < this.pool.size())
        {
            int idx = this.sharedLooks.size();

            this.sharedLooks.add(new Look(0, this.pool.getString(idx)));
        }

        Map<String, Track> tailByKey = new HashMap<>();

        for (Track existing : this.tracks)
        {
            tailByKey.put(liveKey(existing.uuid, existing.dimension), existing);
        }

        for (Track src : other.tracks)
        {
            if (src.size == 0)
            {
                continue;
            }

            String key = liveKey(src.uuid, src.dimension);
            Track prev = tailByKey.get(key);
            int shiftedFirst = src.first + tickOffset;
            int shiftedLast = src.last + tickOffset;

            if (prev != null && prev.type.equals(src.type) && shiftedFirst <= prev.last + 2)
            {
                prev.last = Math.max(prev.last, shiftedLast);

                for (int i = 0; i < src.lookCount; i++)
                {
                    int mapped = RecapPool.remapId(remap, src.lookPoolIds[i]);

                    if (mapped >= 0 && mapped != prev.lastPoolId)
                    {
                        this.attachLook(prev, src.lookTicks[i] + tickOffset, mapped);
                    }
                }

                for (int i = 0; i < src.size; i++)
                {
                    prev.copy(src, i, src.ticks[i] + tickOffset);
                }
            }
            else
            {
                Track copy = new Track(src.uuid, src.type, src.dimension, src.name, src.player, shiftedFirst);

                copy.last = shiftedLast;

                for (int i = 0; i < src.lookCount; i++)
                {
                    int mapped = RecapPool.remapId(remap, src.lookPoolIds[i]);

                    this.attachLook(copy, src.lookTicks[i] + tickOffset, mapped);
                }

                if (copy.lookCount == 0 && !src.looks.isEmpty())
                {
                    for (Look look : src.looks)
                    {
                        this.attachLook(copy, look.tick + tickOffset, this.internLook(look.data));
                    }
                }

                for (int i = 0; i < src.size; i++)
                {
                    copy.copy(src, i, src.ticks[i] + tickOffset);
                }

                this.tracks.add(copy);
                tailByKey.put(key, copy);
            }
        }
    }

    /** The tracks from {@code from} on, their ticks counted from there; each starts as it was there. */
    public static RecapEntities tail(RecapEntities source, int from)
    {
        RecapEntities entities = new RecapEntities();

        for (Track track : source.tracks)
        {
            if (track.last < from || track.size == 0)
            {
                continue;
            }

            Track copy = new Track(track.uuid, track.type, track.dimension, track.name, track.player, Math.max(track.first, from) - from);

            copy.last = track.last - from;

            int firstIdx = track.lookIndexAt(from);

            if (firstIdx >= 0)
            {
                for (int i = firstIdx; i < track.lookCount; i++)
                {
                    String data = source.pool.getString(track.lookPoolIds[i]);

                    entities.attachLook(copy, Math.max(0, track.lookTicks[i] - from), entities.internLook(data));
                }
            }
            else
            {
                Look first = track.lookAt(from);

                for (Look look : track.looks)
                {
                    if (look == first || look.tick > from)
                    {
                        entities.attachLook(copy, Math.max(0, look.tick - from), entities.internLook(look.data));
                    }
                }
            }

            int start = Math.max(0, track.frameAt(from));

            for (int i = start; i < track.size; i++)
            {
                copy.copy(track, i, Math.max(0, track.ticks[i] - from));
            }

            entities.tracks.add(copy);
        }

        return entities;
    }

    /** Forgets what is before {@code oldest}, every few seconds, in one go. */
    public void dropBefore(int oldest)
    {
        if (this.sweep % 100 != 0 || oldest <= 0)
        {
            return;
        }

        RecapEntities kept = tail(this, oldest);

        for (Track track : kept.tracks)
        {
            track.first += oldest;
            track.last += oldest;

            for (int i = 0; i < track.size; i++)
            {
                track.ticks[i] += oldest;
            }

            for (int i = 0; i < track.lookCount; i++)
            {
                track.lookTicks[i] += oldest;
                Look look = track.looks.get(i);
                track.looks.set(i, new Look(look.tick + oldest, look.data));
            }
        }

        Map<String, Track> byKey = new HashMap<>();

        for (Track track : kept.tracks)
        {
            String key = liveKey(track.uuid, track.dimension);
            Track old = this.live.get(key);

            if (old != null && old.last == track.last)
            {
                track.lastSignature = old.lastSignature;
                track.lastLookTick = old.lastLookTick;
                byKey.put(key, track);
            }
        }

        this.pool.clear();
        this.sharedLooks.clear();
        int[] remap = this.pool.mergeFrom(kept.pool);

        while (this.sharedLooks.size() < this.pool.size())
        {
            int idx = this.sharedLooks.size();

            this.sharedLooks.add(new Look(0, this.pool.getString(idx)));
        }

        for (Track track : kept.tracks)
        {
            for (int i = 0; i < track.lookCount; i++)
            {
                track.lookPoolIds[i] = RecapPool.remapId(remap, track.lookPoolIds[i]);
            }

            track.lastPoolId = track.lookCount == 0 ? -1 : track.lookPoolIds[track.lookCount - 1];
        }

        this.tracks.clear();
        this.tracks.addAll(kept.tracks);
        this.live.clear();
        this.live.putAll(byKey);
    }

    /* Files */

    public boolean write(File file)
    {
        try (DataOutputStream raw = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
        {
            raw.writeInt(MAGIC);
            raw.writeInt(VERSION);

            Deflater deflater = new Deflater(Deflater.BEST_SPEED);

            try
            {
                DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 16);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 16));

                this.writeToStream(out);
                out.flush();
                packed.finish();
            }
            finally
            {
                deflater.end();
            }

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public void writeToStream(DataOutputStream out) throws IOException
    {
        this.pool.write(out);
        out.writeInt(this.tracks.size());

        for (Track track : this.tracks)
        {
            out.writeUTF(track.uuid);
            out.writeUTF(track.type);
            out.writeUTF(track.dimension == null ? "" : track.dimension);
            out.writeUTF(track.name);
            out.writeBoolean(track.player);
            out.writeInt(track.first);
            out.writeInt(track.last);
            RecapPool.writeVar(out, track.lookCount);

            for (int i = 0; i < track.lookCount; i++)
            {
                out.writeInt(track.lookTicks[i]);
                RecapPool.writeVar(out, track.lookPoolIds[i] + 1);
            }

            RecapPool.writeVar(out, track.size);

            for (int i = 0; i < track.size; i++)
            {
                out.writeInt(track.ticks[i]);
                out.writeDouble(track.x[i]);
                out.writeDouble(track.y[i]);
                out.writeDouble(track.z[i]);
                out.writeFloat(track.yaw[i]);
                out.writeFloat(track.pitch[i]);
                out.writeFloat(track.head[i]);
                out.writeFloat(track.body[i]);
                out.writeFloat(track.limbPos[i]);
                out.writeFloat(track.limbSpeed[i]);
                out.writeFloat(track.swing[i]);
                out.writeByte(track.pose[i]);
                out.writeByte(track.flags[i]);
                out.writeByte(track.hurt[i]);
                out.writeByte(track.death[i]);
            }
        }
    }

    public static RecapEntities read(File file)
    {
        if (!file.isFile())
        {
            return null;
        }

        try (DataInputStream raw = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
        {
            int version;

            if (raw.readInt() != MAGIC || (version = raw.readInt()) < 1 || version > VERSION)
            {
                return null;
            }

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 16), 1 << 16)))
            {
                return version == 1 ? readV1(in) : readFromStream(in);
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    private static RecapEntities readV1(DataInputStream in) throws IOException
    {
        RecapEntities entities = new RecapEntities();
        int count = in.readInt();

        if (count < 0 || count > 1 << 20)
        {
            return null;
        }

        for (int t = 0; t < count; t++)
        {
            String uuid = in.readUTF();
            String type = in.readUTF();
            String dimension = in.readUTF();
            String name = in.readUTF();
            boolean player = in.readBoolean();
            Track track = new Track(uuid, type, dimension.isEmpty() ? null : dimension, name, player, in.readInt());

            track.last = in.readInt();

            int looks = in.readInt();

            for (int i = 0; i < looks; i++)
            {
                int tick = in.readInt();
                String data = RecapGear.readLong(in);

                entities.attachLook(track, tick, entities.internLook(data));
            }

            int frames = in.readInt();

            for (int i = 0; i < frames; i++)
            {
                track.push(in.readInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readByte(), in.readByte(), in.readByte(), in.readByte());
            }

            entities.tracks.add(track);
        }

        return entities;
    }

    public static RecapEntities readFromStream(DataInputStream in) throws IOException
    {
        RecapEntities entities = new RecapEntities();
        RecapPool loadedPool = RecapPool.read(in);
        int[] remap = entities.pool.mergeFrom(loadedPool);

        while (entities.sharedLooks.size() < entities.pool.size())
        {
            int idx = entities.sharedLooks.size();

            entities.sharedLooks.add(new Look(0, entities.pool.getString(idx)));
        }

        int count = in.readInt();

        if (count < 0 || count > 1 << 20)
        {
            throw new IOException("Bad entity count");
        }

        for (int t = 0; t < count; t++)
        {
            String uuid = in.readUTF();
            String type = in.readUTF();
            String dimension = in.readUTF();
            String name = in.readUTF();
            boolean player = in.readBoolean();
            Track track = new Track(uuid, type, dimension.isEmpty() ? null : dimension, name, player, in.readInt());

            track.last = in.readInt();

            int looks = RecapPool.readVar(in);

            for (int i = 0; i < looks; i++)
            {
                int tick = in.readInt();
                int poolId = RecapPool.remapId(remap, RecapPool.readVar(in) - 1);

                entities.attachLook(track, tick, poolId);
            }

            int frames = RecapPool.readVar(in);

            for (int i = 0; i < frames; i++)
            {
                track.push(in.readInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readByte(), in.readByte(), in.readByte(), in.readByte());
            }

            entities.tracks.add(track);
        }

        return entities;
    }
}
