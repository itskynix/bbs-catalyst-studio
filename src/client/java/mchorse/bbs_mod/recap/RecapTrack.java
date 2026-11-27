package mchorse.bbs_mod.recap;

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
 * The camera of a recap, one sample a game tick: where the eye was, where it looked and how wide.
 * Sample {@code i} is the camera at tick {@code i} of the recap, so the view at any moment is
 * {@link #sample} at {@code ticks + transition}, the same on every machine and in every frame of an
 * export.
 *
 * <p>It is kept next to the recap's own file as a small binary file, as it grows by a sample every
 * tick and the list of recaps never needs to read it.</p>
 */
public class RecapTrack
{
    private static final int MAGIC = 0x52435431;
    private static final int VERSION = 1;

    private double[] x = new double[64];
    private double[] y = new double[64];
    private double[] z = new double[64];
    private float[] yaw = new float[64];
    private float[] pitch = new float[64];
    private float[] fov = new float[64];
    private int size;

    /** One interpolated moment of the track. */
    public static class Sample
    {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float fov;
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

    public void add(double x, double y, double z, float yaw, float pitch, float fov)
    {
        if (this.size == this.x.length)
        {
            int capacity = this.size * 2;

            this.x = Arrays.copyOf(this.x, capacity);
            this.y = Arrays.copyOf(this.y, capacity);
            this.z = Arrays.copyOf(this.z, capacity);
            this.yaw = Arrays.copyOf(this.yaw, capacity);
            this.pitch = Arrays.copyOf(this.pitch, capacity);
            this.fov = Arrays.copyOf(this.fov, capacity);
        }

        int i = this.size++;

        this.x[i] = x;
        this.y[i] = y;
        this.z[i] = z;
        this.yaw[i] = yaw;
        this.pitch[i] = pitch;
        this.fov[i] = fov;
    }

    /**
     * The camera at {@code time}, in ticks, between the two samples around it. The yaw takes the
     * short way round, so a turn across north does not spin the view. False when empty.
     */
    public boolean sample(float time, Sample out)
    {
        if (this.size == 0)
        {
            return false;
        }

        float clamped = Math.max(0F, Math.min(this.size - 1, time));
        int a = (int) Math.floor(clamped);
        int b = Math.min(this.size - 1, a + 1);
        float t = clamped - a;
        float turn = this.yaw[b] - this.yaw[a];

        turn -= 360F * Math.round(turn / 360F);

        out.x = this.x[a] + (this.x[b] - this.x[a]) * t;
        out.y = this.y[a] + (this.y[b] - this.y[a]) * t;
        out.z = this.z[a] + (this.z[b] - this.z[a]) * t;
        out.yaw = this.yaw[a] + turn * t;
        out.pitch = this.pitch[a] + (this.pitch[b] - this.pitch[a]) * t;
        out.fov = this.fov[a] + (this.fov[b] - this.fov[a]) * t;

        return true;
    }

    /** The last {@code count} samples of another track, as a track of its own. */
    public static RecapTrack tail(RecapTrack from, int count)
    {
        RecapTrack track = new RecapTrack();
        int start = Math.max(0, from.size - count);

        for (int i = start; i < from.size; i++)
        {
            track.add(from.x[i], from.y[i], from.z[i], from.yaw[i], from.pitch[i], from.fov[i]);
        }

        return track;
    }

    /** Drops the oldest samples so no more than {@code limit} are kept. */
    public void trim(int limit)
    {
        if (this.size <= limit)
        {
            return;
        }

        /* Trimmed in chunks, so a full buffer is not shifted on every tick */
        if (this.size < limit + Math.max(20, limit / 8))
        {
            return;
        }

        int drop = this.size - limit;

        System.arraycopy(this.x, drop, this.x, 0, limit);
        System.arraycopy(this.y, drop, this.y, 0, limit);
        System.arraycopy(this.z, drop, this.z, 0, limit);
        System.arraycopy(this.yaw, drop, this.yaw, 0, limit);
        System.arraycopy(this.pitch, drop, this.pitch, 0, limit);
        System.arraycopy(this.fov, drop, this.fov, 0, limit);
        this.size = limit;
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
                out.writeFloat(this.yaw[i]);
                out.writeFloat(this.pitch[i]);
                out.writeFloat(this.fov[i]);
            }

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /** The track in a file, or null when there is none or it cannot be read. */
    public static RecapTrack read(File file)
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

            RecapTrack track = new RecapTrack();

            for (int i = 0; i < size; i++)
            {
                track.add(in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readFloat());
            }

            return track;
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
