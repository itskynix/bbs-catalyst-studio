package mchorse.bbs_mod.recap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

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
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * The sounds of the world while a recap was recorded, each with its tick: blocks broken and put,
 * steps, hits, mobs, deaths, explosions, doors, everything the game played at a place in the world,
 * with how loud, at what pitch and how far it carries.
 *
 * <p>Music, the menus' clicks, the weather and the background are left out: the game plays those
 * live anyway. Played back only while the recap plays forward, each sound once as its tick is
 * passed; a scrubbed moment plays none, so the cursor dragged back and forth never stacks them.</p>
 */
public class RecapSounds
{
    private static final int MAGIC = 0x52435331;
    private static final int VERSION = 1;

    /** Set while a recap's own sounds are played, so neither the recorder nor the muting takes them. */
    public static boolean replaying;

    private int[] ticks = new int[64];
    private int[] ids = new int[64];
    private byte[] categories = new byte[64];
    private double[] xs = new double[64];
    private double[] ys = new double[64];
    private double[] zs = new double[64];
    private float[] volumes = new float[64];
    private float[] pitches = new float[64];
    /** Bit 0 relative to the listener, bit 1 heard everywhere alike. */
    private byte[] flags = new byte[64];
    private short[] dims = new short[64];
    private int head;
    private int size;

    private final List<String> names = new ArrayList<>();
    private final List<String> dimensions = new ArrayList<>();

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
        this.head = 0;
        this.size = 0;
        this.names.clear();
        this.dimensions.clear();
    }

    /** Whether a sound is one a recap keeps: a sound of the world, at a place in it. */
    public static boolean isKept(SoundInstance sound)
    {
        if (replaying || sound == null || sound instanceof net.minecraft.client.sound.TickableSoundInstance)
        {
            return false;
        }

        SoundCategory category = sound.getCategory();

        return category != SoundCategory.MASTER && category != SoundCategory.MUSIC && category != SoundCategory.AMBIENT && category != SoundCategory.WEATHER;
    }

    private static int index(List<String> list, String value)
    {
        int index = list.indexOf(value);

        if (index < 0)
        {
            index = list.size();
            list.add(value);
        }

        return index;
    }

    public void add(int tick, SoundInstance sound, float volume, float pitch, String dimension)
    {
        int flag = (sound.isRelative() ? 1 : 0) | (sound.getAttenuationType() == SoundInstance.AttenuationType.NONE ? 2 : 0);

        this.push(tick, index(this.names, sound.getId().toString()), sound.getCategory().ordinal(), sound.getX(), sound.getY(), sound.getZ(),
            volume, pitch, flag, dimension == null ? -1 : index(this.dimensions, dimension));
    }

    private void push(int tick, int id, int category, double x, double y, double z, float volume, float pitch, int flag, int dim)
    {
        if (this.head + this.size == this.ticks.length)
        {
            if (this.head > 0 && this.head >= this.ticks.length / 4)
            {
                this.compact();
            }
            else
            {
                int capacity = Math.max(64, this.ticks.length * 2);

                this.ticks = Arrays.copyOf(this.ticks, capacity);
                this.ids = Arrays.copyOf(this.ids, capacity);
                this.categories = Arrays.copyOf(this.categories, capacity);
                this.xs = Arrays.copyOf(this.xs, capacity);
                this.ys = Arrays.copyOf(this.ys, capacity);
                this.zs = Arrays.copyOf(this.zs, capacity);
                this.volumes = Arrays.copyOf(this.volumes, capacity);
                this.pitches = Arrays.copyOf(this.pitches, capacity);
                this.flags = Arrays.copyOf(this.flags, capacity);
                this.dims = Arrays.copyOf(this.dims, capacity);
            }
        }

        int i = this.head + this.size++;

        this.ticks[i] = tick;
        this.ids[i] = id;
        this.categories[i] = (byte) category;
        this.xs[i] = x;
        this.ys[i] = y;
        this.zs[i] = z;
        this.volumes[i] = volume;
        this.pitches[i] = pitch;
        this.flags[i] = (byte) flag;
        this.dims[i] = (short) dim;
    }

    private void compact()
    {
        System.arraycopy(this.ticks, this.head, this.ticks, 0, this.size);
        System.arraycopy(this.ids, this.head, this.ids, 0, this.size);
        System.arraycopy(this.categories, this.head, this.categories, 0, this.size);
        System.arraycopy(this.xs, this.head, this.xs, 0, this.size);
        System.arraycopy(this.ys, this.head, this.ys, 0, this.size);
        System.arraycopy(this.zs, this.head, this.zs, 0, this.size);
        System.arraycopy(this.volumes, this.head, this.volumes, 0, this.size);
        System.arraycopy(this.pitches, this.head, this.pitches, 0, this.size);
        System.arraycopy(this.flags, this.head, this.flags, 0, this.size);
        System.arraycopy(this.dims, this.head, this.dims, 0, this.size);
        this.head = 0;
    }

    /** The first sound with a tick at or after {@code tick}; they are kept in the order of their ticks. */
    private int firstAtOrAfter(int tick)
    {
        int low = 0;
        int high = this.size;

        while (low < high)
        {
            int middle = (low + high) >>> 1;

            if (this.ticks[this.head + middle] < tick)
            {
                low = middle + 1;
            }
            else
            {
                high = middle;
            }
        }

        return low;
    }

    /** Forgets the sounds before {@code oldest}; the front only moves on. */
    public void dropBefore(int oldest)
    {
        int first = this.firstAtOrAfter(oldest);

        this.head += first;
        this.size -= first;

        if (this.size == 0)
        {
            this.head = 0;
        }
    }

    /** The sounds from {@code from} on, their ticks counted from there. */
    public static RecapSounds tail(RecapSounds source, int from)
    {
        RecapSounds sounds = new RecapSounds();

        sounds.names.addAll(source.names);
        sounds.dimensions.addAll(source.dimensions);

        for (int i = source.firstAtOrAfter(from); i < source.size; i++)
        {
            int at = source.head + i;

            sounds.push(source.ticks[at] - from, source.ids[at], source.categories[at], source.xs[at], source.ys[at], source.zs[at],
                source.volumes[at], source.pitches[at], source.flags[at], source.dims[at]);
        }

        return sounds;
    }

    /**
     * Plays the sounds of the source ticks after {@code from} and up to {@code to}, of the
     * dimension given, once each, as the game would have: at their place, loud and pitched as then.
     */
    public void play(int from, int to, String dimension)
    {
        if (to <= from || this.size == 0)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        SoundCategory[] categories = SoundCategory.values();
        int played = 0;

        replaying = true;

        try
        {
            for (int i = this.firstAtOrAfter(from + 1); i < this.size && played < 64; i++)
            {
                int at = this.head + i;

                if (this.ticks[at] > to)
                {
                    break;
                }

                int dim = this.dims[at];

                if (dim >= 0 && dimension != null && !this.dimensions.get(dim).equals(dimension))
                {
                    continue;
                }

                Identifier id = Identifier.tryParse(this.names.get(this.ids[at]));
                int category = this.categories[at];

                if (id == null || category < 0 || category >= categories.length)
                {
                    continue;
                }

                boolean relative = (this.flags[at] & 1) != 0;
                SoundInstance.AttenuationType attenuation = (this.flags[at] & 2) != 0 ? SoundInstance.AttenuationType.NONE : SoundInstance.AttenuationType.LINEAR;
                /* The same variant every time the recap plays: the seed is the sound's place in it */
                Random random = Random.create(31L * this.ticks[at] + i);

                mc.getSoundManager().play(new PositionedSoundInstance(id, categories[category], this.volumes[at], this.pitches[at], random,
                    false, 0, attenuation, this.xs[at], this.ys[at], this.zs[at], relative));
                played += 1;
            }
        }
        catch (RuntimeException e)
        {
            /* A sound that is not there any more never stops the picture */
        }
        finally
        {
            replaying = false;
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

                out.writeInt(this.names.size());

                for (String name : this.names)
                {
                    out.writeUTF(name);
                }

                out.writeInt(this.dimensions.size());

                for (String dimension : this.dimensions)
                {
                    out.writeUTF(dimension);
                }

                out.writeInt(this.size);

                for (int i = 0; i < this.size; i++)
                {
                    int at = this.head + i;

                    out.writeInt(this.ticks[at]);
                    out.writeInt(this.ids[at]);
                    out.writeByte(this.categories[at]);
                    out.writeDouble(this.xs[at]);
                    out.writeDouble(this.ys[at]);
                    out.writeDouble(this.zs[at]);
                    out.writeFloat(this.volumes[at]);
                    out.writeFloat(this.pitches[at]);
                    out.writeByte(this.flags[at]);
                    out.writeShort(this.dims[at]);
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

    public static RecapSounds read(File file)
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
                RecapSounds sounds = new RecapSounds();
                int names = in.readInt();

                for (int i = 0; i < names; i++)
                {
                    sounds.names.add(in.readUTF());
                }

                int dimensions = in.readInt();

                for (int i = 0; i < dimensions; i++)
                {
                    sounds.dimensions.add(in.readUTF());
                }

                int count = in.readInt();

                for (int i = 0; i < count; i++)
                {
                    int tick = in.readInt();
                    int id = in.readInt();
                    int category = in.readByte();
                    double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                    float volume = in.readFloat(), pitch = in.readFloat();
                    int flag = in.readByte();
                    int dim = in.readShort();

                    if (id >= 0 && id < names && dim < dimensions)
                    {
                        sounds.push(tick, id, category, x, y, z, volume, pitch, flag, dim);
                    }
                }

                return sounds;
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }
}
