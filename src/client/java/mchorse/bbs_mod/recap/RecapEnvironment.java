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
 * The world around the recorded player, one frame a game tick, like the camera and the movement:
 * the time of day (the world's own count of ticks, so the moon goes through its phases too), and
 * how hard it rained and thundered.
 *
 * <p>Read straight from the world's own values, never through what Recap Studio or a film puts
 * over them, so a recording keeps the real sky. Played back only as what the sky is drawn with,
 * the world itself is never told another time or weather ({@link RecapScene#timeOfDay}).</p>
 */
public class RecapEnvironment
{
    private static final int MAGIC = 0x52434531;
    private static final int VERSION = 1;

    private long[] time = new long[64];
    private float[] rain = new float[64];
    private float[] thunder = new float[64];
    private int size;

    /** One moment of the world around. */
    public static class Sample
    {
        public long time;
        public float rain;
        public float thunder;
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

    public void add(long time, float rain, float thunder)
    {
        if (this.size == this.time.length)
        {
            int capacity = this.size * 2;

            this.time = Arrays.copyOf(this.time, capacity);
            this.rain = Arrays.copyOf(this.rain, capacity);
            this.thunder = Arrays.copyOf(this.thunder, capacity);
        }

        this.time[this.size] = time;
        this.rain[this.size] = rain;
        this.thunder[this.size] = thunder;
        this.size += 1;
    }

    /** The world around at {@code at}, in ticks, between the frames around it; false when empty. */
    public boolean sample(float at, Sample out)
    {
        if (this.size == 0)
        {
            return false;
        }

        float clamped = Math.max(0F, Math.min(this.size - 1, at));
        int a = (int) Math.floor(clamped);
        int b = Math.min(this.size - 1, a + 1);
        float t = clamped - a;
        long step = this.time[b] - this.time[a];

        /* A clock that jumped (a command, a night slept through) jumps, it does not sweep */
        out.time = step >= 0 && step <= 2 ? this.time[a] + (long) Math.floor(step * t) : (t < 0.5F ? this.time[a] : this.time[b]);
        out.rain = this.rain[a] + (this.rain[b] - this.rain[a]) * t;
        out.thunder = this.thunder[a] + (this.thunder[b] - this.thunder[a]) * t;

        return true;
    }

    /** The last {@code count} frames of another. */
    public static RecapEnvironment tail(RecapEnvironment from, int count)
    {
        RecapEnvironment environment = new RecapEnvironment();

        for (int i = Math.max(0, from.size - count); i < from.size; i++)
        {
            environment.add(from.time[i], from.rain[i], from.thunder[i]);
        }

        return environment;
    }

    /** Drops the oldest frames so no more than about {@code limit} are kept, in chunks. */
    public void trim(int limit)
    {
        if (this.size < limit + Math.max(20, limit / 8))
        {
            return;
        }

        RecapEnvironment kept = tail(this, limit);

        this.time = kept.time;
        this.rain = kept.rain;
        this.thunder = kept.thunder;
        this.size = kept.size;
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

                out.writeInt(this.size);

                long last = 0;

                for (int i = 0; i < this.size; i++)
                {
                    /* The steps of the clock, nearly always one, pack to almost nothing */
                    out.writeLong(this.time[i] - last);
                    last = this.time[i];
                    out.writeFloat(this.rain[i]);
                    out.writeFloat(this.thunder[i]);
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

    public static RecapEnvironment read(File file)
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

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 15), 1 << 15)))
            {
                int size = in.readInt();

                if (size < 0 || size > 20 * 60 * 60 * 6)
                {
                    return null;
                }

                RecapEnvironment environment = new RecapEnvironment();
                long time = 0;

                for (int i = 0; i < size; i++)
                {
                    time += in.readLong();
                    environment.add(time, in.readFloat(), in.readFloat());
                }

                return environment;
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }
}
