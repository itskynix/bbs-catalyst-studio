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
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * The camera of a recap: one base sample per game tick plus optional sub-tick frame samples for
 * high-refresh first-person motion, stored compressed in version 2 while keeping version 1 readable.
 */
public class RecapTrack
{
    private static final int MAGIC = 0x52435431;
    private static final int VERSION = 2;
    private static final int MAX_SAMPLES = 20 * 60 * 60 * 6;
    private static final int SUB_PER_TICK = 4;

    private double[] x = new double[64];
    private double[] y = new double[64];
    private double[] z = new double[64];
    private float[] yaw = new float[64];
    private float[] pitch = new float[64];
    private float[] fov = new float[64];
    private int size;

    /* Optional sub-tick camera curve (monotonic fractional tick times) */
    private float[] subTime = new float[64];
    private double[] subX = new double[64];
    private double[] subY = new double[64];
    private double[] subZ = new double[64];
    private float[] subYaw = new float[64];
    private float[] subPitch = new float[64];
    private float[] subFov = new float[64];
    private int subSize;
    private int subCountedTick = Integer.MIN_VALUE;
    private int subInTick;

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
        this.subSize = 0;
        this.subCountedTick = Integer.MIN_VALUE;
        this.subInTick = 0;
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

    /** Records an intra-tick camera pose when the view actually moved between render frames. */
    public void addSubSample(float time, double x, double y, double z, float yaw, float pitch, float fov)
    {
        if (Float.isNaN(time) || time < 0F)
        {
            return;
        }

        int whole = (int) Math.floor(time);

        if (whole != this.subCountedTick)
        {
            this.subCountedTick = whole;
            this.subInTick = 0;
        }

        if (this.subInTick >= SUB_PER_TICK)
        {
            return;
        }

        if (this.subSize > 0)
        {
            int last = this.subSize - 1;

            if (time <= this.subTime[last] + 0.04F)
            {
                return;
            }

            double dx = x - this.subX[last];
            double dy = y - this.subY[last];
            double dz = z - this.subZ[last];
            float dYaw = shortTurn(this.subYaw[last], yaw);
            float dPitch = pitch - this.subPitch[last];

            if (dx * dx + dy * dy + dz * dz < 1e-6D && Math.abs(dYaw) < 0.05F && Math.abs(dPitch) < 0.05F)
            {
                return;
            }
        }

        this.pushSub(time, x, y, z, yaw, pitch, fov);
        this.subInTick += 1;
    }

    private void pushSub(float time, double x, double y, double z, float yaw, float pitch, float fov)
    {
        if (this.subSize == this.subTime.length)
        {
            int cap = this.subSize * 2;

            this.subTime = Arrays.copyOf(this.subTime, cap);
            this.subX = Arrays.copyOf(this.subX, cap);
            this.subY = Arrays.copyOf(this.subY, cap);
            this.subZ = Arrays.copyOf(this.subZ, cap);
            this.subYaw = Arrays.copyOf(this.subYaw, cap);
            this.subPitch = Arrays.copyOf(this.subPitch, cap);
            this.subFov = Arrays.copyOf(this.subFov, cap);
        }

        int i = this.subSize++;

        this.subTime[i] = time;
        this.subX[i] = x;
        this.subY[i] = y;
        this.subZ[i] = z;
        this.subYaw[i] = yaw;
        this.subPitch[i] = pitch;
        this.subFov[i] = fov;
    }

    private static float shortTurn(float from, float to)
    {
        float turn = to - from;

        return turn - 360F * Math.round(turn / 360F);
    }

    /**
     * The camera at {@code time}, in ticks: uses sub-tick samples when closely bracketing {@code time},
     * else interpolates between the two surrounding tick samples.
     */
    public boolean sample(float time, Sample out)
    {
        if (this.size == 0)
        {
            return false;
        }

        float clamped = Math.max(0F, Math.min(this.size - 1, time));

        if (this.subSize >= 2 && this.sampleSub(clamped, out))
        {
            return true;
        }

        int a = (int) Math.floor(clamped);
        int b = Math.min(this.size - 1, a + 1);
        float t = clamped - a;
        float turn = shortTurn(this.yaw[a], this.yaw[b]);

        out.x = this.x[a] + (this.x[b] - this.x[a]) * t;
        out.y = this.y[a] + (this.y[b] - this.y[a]) * t;
        out.z = this.z[a] + (this.z[b] - this.z[a]) * t;
        out.yaw = this.yaw[a] + turn * t;
        out.pitch = this.pitch[a] + (this.pitch[b] - this.pitch[a]) * t;
        out.fov = this.fov[a] + (this.fov[b] - this.fov[a]) * t;

        return true;
    }

    private boolean sampleSub(float time, Sample out)
    {
        int low = 0;
        int high = this.subSize - 1;

        if (time < this.subTime[0] || time > this.subTime[high])
        {
            return false;
        }

        while (low < high)
        {
            int mid = (low + high + 1) >>> 1;

            if (this.subTime[mid] <= time)
            {
                low = mid;
            }
            else
            {
                high = mid - 1;
            }
        }

        int a = low;
        int b = Math.min(this.subSize - 1, a + 1);
        float span = this.subTime[b] - this.subTime[a];

        /* Only use sub-tick samples when consecutive frames actually bracket this tick */
        if (span <= 0F || span > 1.25F)
        {
            return false;
        }

        float t = Math.max(0F, Math.min(1F, (time - this.subTime[a]) / span));
        float turn = shortTurn(this.subYaw[a], this.subYaw[b]);

        out.x = this.subX[a] + (this.subX[b] - this.subX[a]) * t;
        out.y = this.subY[a] + (this.subY[b] - this.subY[a]) * t;
        out.z = this.subZ[a] + (this.subZ[b] - this.subZ[a]) * t;
        out.yaw = this.subYaw[a] + turn * t;
        out.pitch = this.subPitch[a] + (this.subPitch[b] - this.subPitch[a]) * t;
        out.fov = this.subFov[a] + (this.subFov[b] - this.subFov[a]) * t;

        return true;
    }

    /** Appends another slice's samples after this track. */
    public void append(RecapTrack other)
    {
        if (other == null || other.isEmpty())
        {
            return;
        }

        int offset = this.size;

        for (int i = 0; i < other.size; i++)
        {
            this.add(other.x[i], other.y[i], other.z[i], other.yaw[i], other.pitch[i], other.fov[i]);
        }

        for (int i = 0; i < other.subSize; i++)
        {
            this.pushSub(other.subTime[i] + offset, other.subX[i], other.subY[i], other.subZ[i], other.subYaw[i], other.subPitch[i], other.subFov[i]);
        }
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

        for (int i = 0; i < from.subSize; i++)
        {
            if (from.subTime[i] >= start)
            {
                track.pushSub(from.subTime[i] - start, from.subX[i], from.subY[i], from.subZ[i], from.subYaw[i], from.subPitch[i], from.subFov[i]);
            }
        }

        return track;
    }

    /** Drops the oldest samples so no more than {@code limit} are kept. */
    public void trim(int limit)
    {
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

        if (this.subSize > 0)
        {
            int firstSub = 0;

            while (firstSub < this.subSize && this.subTime[firstSub] < drop)
            {
                firstSub += 1;
            }

            int keptSub = this.subSize - firstSub;

            for (int i = 0; i < keptSub; i++)
            {
                this.subTime[i] = this.subTime[firstSub + i] - drop;
            }

            if (firstSub > 0 && keptSub > 0)
            {
                System.arraycopy(this.subX, firstSub, this.subX, 0, keptSub);
                System.arraycopy(this.subY, firstSub, this.subY, 0, keptSub);
                System.arraycopy(this.subZ, firstSub, this.subZ, 0, keptSub);
                System.arraycopy(this.subYaw, firstSub, this.subYaw, 0, keptSub);
                System.arraycopy(this.subPitch, firstSub, this.subPitch, 0, keptSub);
                System.arraycopy(this.subFov, firstSub, this.subFov, 0, keptSub);
            }

            this.subSize = keptSub;
        }
    }

    public boolean write(File file)
    {
        try (DataOutputStream raw = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
        {
            raw.writeInt(MAGIC);
            raw.writeInt(VERSION);

            Deflater deflater = new Deflater(Deflater.BEST_SPEED);

            try
            {
                DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 15);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 15));

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

        out.writeInt(this.subSize);

        for (int i = 0; i < this.subSize; i++)
        {
            out.writeFloat(this.subTime[i]);
            out.writeDouble(this.subX[i]);
            out.writeDouble(this.subY[i]);
            out.writeDouble(this.subZ[i]);
            out.writeFloat(this.subYaw[i]);
            out.writeFloat(this.subPitch[i]);
            out.writeFloat(this.subFov[i]);
        }
    }

    /** The track in a file, or null when there is none or it cannot be read. */
    public static RecapTrack read(File file)
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

            if (version == 1)
            {
                int size = raw.readInt();

                if (size < 0 || size > MAX_SAMPLES)
                {
                    return null;
                }

                RecapTrack track = new RecapTrack();

                for (int i = 0; i < size; i++)
                {
                    track.add(raw.readDouble(), raw.readDouble(), raw.readDouble(), raw.readFloat(), raw.readFloat(), raw.readFloat());
                }

                return track;
            }

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 15), 1 << 15)))
            {
                return readFromStream(in);
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    public static RecapTrack readFromStream(DataInputStream in) throws IOException
    {
        int size = in.readInt();

        if (size < 0 || size > MAX_SAMPLES)
        {
            throw new IOException("Bad track size");
        }

        RecapTrack track = new RecapTrack();

        for (int i = 0; i < size; i++)
        {
            track.add(in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readFloat());
        }

        int sub = in.readInt();

        if (sub < 0 || sub > MAX_SAMPLES * SUB_PER_TICK)
        {
            throw new IOException("Bad sub-track size");
        }

        for (int i = 0; i < sub; i++)
        {
            track.pushSub(in.readFloat(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat(), in.readFloat());
        }

        return track;
    }
}
