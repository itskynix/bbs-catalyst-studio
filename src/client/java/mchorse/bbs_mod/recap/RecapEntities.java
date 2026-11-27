package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

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
 * Every other entity around the recorded player, tick by tick: mobs, animals, other players,
 * dropped items, arrows, minecarts, boats, armor stands, falling blocks, lit TNT and the rest, each
 * as a track of its own from the tick it was first seen to the last.
 *
 * <p><b>What a track keeps.</b> The kind of entity and its look, the entity's own data as the game
 * saves it with what changes every tick taken out (its colour, its variant, its items, its name),
 * kept again only when it changes; and where it was, how it was turned, its stride, its arm swing,
 * its pose, whether it burned, crouched, sprinted, swam, glided, glowed or hid, how hurt and how
 * far into dying it was, kept only on the ticks that changed something, so a sleeping cat costs one
 * frame, not one a tick.</p>
 *
 * <p><b>Played back</b> by Recap Studio as copies made from the look, in no world's list of
 * entities, placed and drawn where and as the track has them at the moment drawn
 * ({@link RecapScene#renderEntities}).</p>
 */
public class RecapEntities
{
    private static final int MAGIC = 0x52434e31;
    private static final int VERSION = 1;

    /** How far from the recorded player entities are kept, and how many at most. */
    public static final double RANGE = 96D;
    public static final int MAX = 384;

    /** The entity's own data that changes all the time and says nothing of how it looks. */
    private static final Set<String> VOLATILE = Set.of("Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown",
        "HurtTime", "HurtByTimestamp", "DeathTime", "Brain", "Attributes", "Age", "Health", "AbsorptionAmount", "PickupDelay", "Time", "fuse", "Fuse",
        "life", "Life", "XpP", "XpLevel", "XpTotal", "XpSeed", "foodLevel", "foodSaturationLevel", "foodExhaustionLevel", "foodTickTimer", "Score",
        "abilities", "recipeBook", "EnderItems", "LastDeathLocation", "warden_spawn_tracker", "SleepTimer", "FallFlying", "TicksFrozen", "AngerTime",
        "AngryAt", "InLove", "ForcedAge", "Leash", "Passengers", "Motive", "Dimension", "playerGameType", "previousPlayerGameType", "seenCredits",
        "current_explosion_impact_pos", "ignore_fall_damage_from_current_explosion", "current_impulse_context_reset_grace_time", "SpawnX", "SpawnY", "SpawnZ");

    private final List<Track> tracks = new ArrayList<>();

    /* Recording: the tracks of the entities seen last tick, by their id */
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
        int lookHash;
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

        /** The look at {@code tick}: the last at or before it, else the first. */
        public Look lookAt(int tick)
        {
            Look found = this.looks.isEmpty() ? null : this.looks.get(0);

            for (Look look : this.looks)
            {
                if (look.tick <= tick)
                {
                    found = look;
                }
                else
                {
                    break;
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

        /**
         * The entity at {@code time}: between two frames of consecutive ticks it is between them,
         * after a frame with none on the next tick it stays as that frame had it. False when it was
         * not there.
         */
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
        this.tracks.clear();
        this.live.clear();
    }

    /* Recording */

    /**
     * The entities around {@code around} at {@code tick}: a frame for those that changed, a new
     * track for those new, the end of the track of those gone. {@code skip} is left out: the player
     * recorded on its own.
     */
    public void record(Iterable<Entity> entities, Entity skip, Entity around, int tick, String dimension)
    {
        int count = 0;
        double range = RANGE * RANGE;
        this.sweep += 1;

        for (Entity entity : entities)
        {
            if (entity == skip || entity.isRemoved() || around != null && entity.squaredDistanceTo(around) > range)
            {
                continue;
            }

            String uuid = entity.getUuidAsString();
            Track track = this.live.get(uuid);

            if (track == null)
            {
                if (count >= MAX || this.live.size() >= MAX)
                {
                    continue;
                }

                String type = EntityType.getId(entity.getType()).toString();
                boolean player = entity instanceof PlayerEntity;

                track = new Track(uuid, type, dimension, player ? entity.getName().getString() : "", player, tick);
                this.tracks.add(track);
                this.live.put(uuid, track);
            }

            count += 1;
            track.last = tick;
            this.lookOf(track, entity, tick);
            this.frameOf(track, entity, tick);
        }

        /* Those not seen this tick are gone: their tracks end on the last tick they were seen */
        Iterator<Track> it = this.live.values().iterator();

        while (it.hasNext())
        {
            Track track = it.next();

            if (track.last != tick)
            {
                it.remove();
            }
        }
    }

    /** The entity's look, kept again when it changed, no more than once in five seconds. */
    private void lookOf(Track track, Entity entity, int tick)
    {
        boolean due = track.looks.isEmpty() || (tick - track.lastLookTick >= 100 && (tick + track.uuid.hashCode()) % 20 == 0);

        if (!due || track.looks.size() >= 64)
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

        int hash = data.hashCode();

        if (track.looks.isEmpty() || hash != track.lookHash)
        {
            track.looks.add(new Look(tick, data));
            track.lookHash = hash;
        }

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

        /* Still since an earlier tick: that stillness is kept up to the tick before, so the move starts now */
        if (last >= 0 && track.ticks[last] < tick - 1)
        {
            track.copy(track, last, tick - 1);
        }

        track.push(tick, x, y, z, yaw, pitch, head, body, limbPos, limbSpeed, swing, pose, flags, hurt, death);
    }

    /* Buffers */

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

            Look first = track.lookAt(from);

            for (Look look : track.looks)
            {
                if (look == first || look.tick > from)
                {
                    copy.looks.add(new Look(Math.max(0, look.tick - from), look.data));
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

        /* Ticks stay counted from the start: the kept tracks are moved back by as much */
        for (Track track : kept.tracks)
        {
            track.first += oldest;
            track.last += oldest;

            for (int i = 0; i < track.size; i++)
            {
                track.ticks[i] += oldest;
            }

            for (int i = 0; i < track.looks.size(); i++)
            {
                Look look = track.looks.get(i);

                track.looks.set(i, new Look(look.tick + oldest, look.data));
            }
        }

        Map<String, Track> byId = new HashMap<>();

        for (Track track : kept.tracks)
        {
            if (this.live.containsKey(track.uuid) && this.live.get(track.uuid).last == track.last)
            {
                Track old = this.live.get(track.uuid);

                track.lookHash = old.lookHash;
                track.lastLookTick = old.lastLookTick;
                byId.put(track.uuid, track);
            }
        }

        this.tracks.clear();
        this.tracks.addAll(kept.tracks);
        this.live.clear();
        this.live.putAll(byId);
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
                    out.writeInt(track.looks.size());

                    for (Look look : track.looks)
                    {
                        out.writeInt(look.tick);
                        RecapGear.writeLong(out, look.data);
                    }

                    out.writeInt(track.size);

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

    public static RecapEntities read(File file)
    {
        if (!file.isFile())
        {
            return null;
        }

        try (DataInputStream raw = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
        {
            if (raw.readInt() != MAGIC || raw.readInt() != VERSION)
            {
                return null;
            }

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 16), 1 << 16)))
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

                        track.looks.add(new Look(tick, RecapGear.readLong(in)));
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
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }
}
