package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.mixin.client.RecapEntityFlagInvoker;
import mchorse.bbs_mod.mixin.client.RecapLivingAccessor;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * How the recorded player moved, one frame a game tick: where its feet were, how its body, head
 * and look were turned, where its legs were in their stride and how fast, its arm swing, its
 * swimming lean, its pose (standing, crouching, swimming, gliding, sleeping) and a few flags
 * (sneaking, sprinting, swimming, gliding, on the ground, swinging).
 *
 * <p>Frame {@code i} is the player at tick {@code i} of the recap; {@link #sample} gives the
 * player between two of them, so the walk is as smooth as the picture. It is kept next to the
 * recap as a small binary file, like the camera.</p>
 */
public class RecapMotion
{
    private static final int MAGIC = 0x52434d31;
    private static final int VERSION = 1;

    public static final int SNEAKING = 1;
    public static final int SPRINTING = 2;
    public static final int SWIMMING = 4;
    public static final int GLIDING = 8;
    public static final int ON_GROUND = 16;
    public static final int SWINGING = 32;

    private double[] x = new double[64];
    private double[] y = new double[64];
    private double[] z = new double[64];
    private float[] bodyYaw = new float[64];
    private float[] headYaw = new float[64];
    private float[] pitch = new float[64];
    private float[] limbPos = new float[64];
    private float[] limbSpeed = new float[64];
    private float[] swing = new float[64];
    private float[] lean = new float[64];
    private byte[] pose = new byte[64];
    private byte[] flags = new byte[64];
    private int size;

    /** One moment of the recorded player. */
    public static class Frame
    {
        public double x;
        public double y;
        public double z;
        public float bodyYaw;
        public float headYaw;
        public float pitch;
        public float limbPos;
        public float limbSpeed;
        public float swing;
        public float lean;
        public EntityPose pose = EntityPose.STANDING;
        public int flags;

        public boolean is(int flag)
        {
            return (this.flags & flag) != 0;
        }
    }

    public int size()
    {
        return this.size;
    }

    public boolean isEmpty()
    {
        return this.size == 0;
    }

    public void clear()
    {
        this.size = 0;
    }

    /** Keeps the player as it is this tick. */
    public void add(PlayerEntity player)
    {
        int flags = (player.isSneaking() ? SNEAKING : 0)
            | (player.isSprinting() ? SPRINTING : 0)
            | (player.isSwimming() ? SWIMMING : 0)
            | (player.isFallFlying() ? GLIDING : 0)
            | (player.isOnGround() ? ON_GROUND : 0)
            | (player.handSwinging ? SWINGING : 0);

        this.add(player.getX(), player.getY(), player.getZ(), player.bodyYaw, player.headYaw, player.getPitch(),
            player.limbAnimator.getPos(), player.limbAnimator.getSpeed(), player.handSwingProgress,
            ((RecapLivingAccessor) player).bbs$getLeaningPitch(), player.getPose().ordinal(), flags);
    }

    public void add(double x, double y, double z, float bodyYaw, float headYaw, float pitch, float limbPos, float limbSpeed, float swing, float lean, int pose, int flags)
    {
        if (this.size == this.x.length)
        {
            int capacity = this.size * 2;

            this.x = Arrays.copyOf(this.x, capacity);
            this.y = Arrays.copyOf(this.y, capacity);
            this.z = Arrays.copyOf(this.z, capacity);
            this.bodyYaw = Arrays.copyOf(this.bodyYaw, capacity);
            this.headYaw = Arrays.copyOf(this.headYaw, capacity);
            this.pitch = Arrays.copyOf(this.pitch, capacity);
            this.limbPos = Arrays.copyOf(this.limbPos, capacity);
            this.limbSpeed = Arrays.copyOf(this.limbSpeed, capacity);
            this.swing = Arrays.copyOf(this.swing, capacity);
            this.lean = Arrays.copyOf(this.lean, capacity);
            this.pose = Arrays.copyOf(this.pose, capacity);
            this.flags = Arrays.copyOf(this.flags, capacity);
        }

        int i = this.size++;

        this.x[i] = x;
        this.y[i] = y;
        this.z[i] = z;
        this.bodyYaw[i] = bodyYaw;
        this.headYaw[i] = headYaw;
        this.pitch[i] = pitch;
        this.limbPos[i] = limbPos;
        this.limbSpeed[i] = limbSpeed;
        this.swing[i] = swing;
        this.lean[i] = lean;
        this.pose[i] = (byte) pose;
        this.flags[i] = (byte) flags;
    }

    private static float turn(float a, float b, float t)
    {
        float delta = b - a;

        delta -= 360F * Math.round(delta / 360F);

        return a + delta * t;
    }

    /** The player at {@code time}, in ticks, between the frames around it; false when empty. */
    public boolean sample(float time, Frame out)
    {
        if (this.size == 0)
        {
            return false;
        }

        float clamped = Math.max(0F, Math.min(this.size - 1, time));
        int a = (int) Math.floor(clamped);
        int b = Math.min(this.size - 1, a + 1);
        float t = clamped - a;

        out.x = this.x[a] + (this.x[b] - this.x[a]) * t;
        out.y = this.y[a] + (this.y[b] - this.y[a]) * t;
        out.z = this.z[a] + (this.z[b] - this.z[a]) * t;
        out.bodyYaw = turn(this.bodyYaw[a], this.bodyYaw[b], t);
        out.headYaw = turn(this.headYaw[a], this.headYaw[b], t);
        out.pitch = this.pitch[a] + (this.pitch[b] - this.pitch[a]) * t;
        out.limbPos = this.limbPos[a] + (this.limbPos[b] - this.limbPos[a]) * t;
        out.limbSpeed = this.limbSpeed[a] + (this.limbSpeed[b] - this.limbSpeed[a]) * t;
        /* The swing starts over at nought: no blend across the restart */
        out.swing = this.swing[b] >= this.swing[a] ? this.swing[a] + (this.swing[b] - this.swing[a]) * t : this.swing[a];
        out.lean = this.lean[a] + (this.lean[b] - this.lean[a]) * t;

        int pose = this.pose[t < 0.5F ? a : b];
        EntityPose[] poses = EntityPose.values();

        out.pose = pose >= 0 && pose < poses.length ? poses[pose] : EntityPose.STANDING;
        out.flags = this.flags[t < 0.5F ? a : b];

        return true;
    }

    /** The last {@code count} frames of another motion. */
    public static RecapMotion tail(RecapMotion from, int count)
    {
        RecapMotion motion = new RecapMotion();

        for (int i = Math.max(0, from.size - count); i < from.size; i++)
        {
            motion.add(from.x[i], from.y[i], from.z[i], from.bodyYaw[i], from.headYaw[i], from.pitch[i], from.limbPos[i],
                from.limbSpeed[i], from.swing[i], from.lean[i], from.pose[i], from.flags[i]);
        }

        return motion;
    }

    /** Drops the oldest frames so no more than about {@code limit} are kept, in chunks. */
    public void trim(int limit)
    {
        if (this.size < limit + Math.max(20, limit / 8))
        {
            return;
        }

        RecapMotion kept = tail(this, limit);

        this.x = kept.x;
        this.y = kept.y;
        this.z = kept.z;
        this.bodyYaw = kept.bodyYaw;
        this.headYaw = kept.headYaw;
        this.pitch = kept.pitch;
        this.limbPos = kept.limbPos;
        this.limbSpeed = kept.limbSpeed;
        this.swing = kept.swing;
        this.lean = kept.lean;
        this.pose = kept.pose;
        this.flags = kept.flags;
        this.size = kept.size;
    }

    public boolean write(File file)
    {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
        {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(this.size);

            for (int i = 0; i < this.size; i++)
            {
                out.writeDouble(this.x[i]);
                out.writeDouble(this.y[i]);
                out.writeDouble(this.z[i]);
                out.writeFloat(this.bodyYaw[i]);
                out.writeFloat(this.headYaw[i]);
                out.writeFloat(this.pitch[i]);
                out.writeFloat(this.limbPos[i]);
                out.writeFloat(this.limbSpeed[i]);
                out.writeFloat(this.swing[i]);
                out.writeFloat(this.lean[i]);
                out.writeByte(this.pose[i]);
                out.writeByte(this.flags[i]);
            }

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public static RecapMotion read(File file)
    {
        if (!file.isFile())
        {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
        {
            if (in.readInt() != MAGIC || in.readInt() != VERSION)
            {
                return null;
            }

            int size = in.readInt();

            if (size < 0 || size > 20 * 60 * 60 * 6)
            {
                return null;
            }

            RecapMotion motion = new RecapMotion();

            for (int i = 0; i < size; i++)
            {
                motion.add(in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readByte(), in.readByte());
            }

            return motion;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /** Used by the flags read back: the gliding flag index of an entity. */
    public static final int GLIDE_FLAG = 7;

    static boolean isGliding(PlayerEntity player)
    {
        return ((RecapEntityFlagInvoker) player).bbs$getFlag(GLIDE_FLAG);
    }
}
