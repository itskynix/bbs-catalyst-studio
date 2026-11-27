package mchorse.bbs_mod.recap;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
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
import java.util.zip.InflaterInputStream;

/**
 * What happened to the world's blocks during a recap: every block broken, placed or changed by
 * use (a door opened, a lever thrown), each with the tick it happened on, the dimension it
 * happened in, the block before and the block after, in the order of their ticks.
 *
 * <p><b>Columns.</b> The changes are kept in plain arrays, one a field (tick, position, block
 * before, block after, dimension), the blocks and dimensions as places in a table of their own:
 * about twenty two bytes a change and no object per change, so a recap with millions of changes
 * stays small and a lookup by tick is a binary search. Changes made out of order are put in order
 * ({@link #sort}) before anything reads them by tick.</p>
 *
 * <p>This is only the recording. Putting it on the world while Recap Studio shows the recap, and
 * taking it off again, is {@link RecapWorldOverlay}'s alone.</p>
 */
public class RecapEvents
{
    private static final int MAGIC = 0x52435731;
    private static final int VERSION = 3;

    /* The columns; the changes are the ones from head to head + size */
    private int[] ticks = new int[256];
    private long[] positions = new long[256];
    private int[] froms = new int[256];
    private int[] tos = new int[256];
    private short[] dims = new short[256];
    private int head;
    private int size;
    private boolean sorted = true;

    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
    private final List<String> dimensions = new ArrayList<>();

    private int revision;

    public RecapEvents()
    {
        this.paletteIndex.defaultReturnValue(-1);
    }

    public int size()
    {
        return this.size;
    }

    public boolean isEmpty()
    {
        return this.size == 0;
    }

    /** Goes up with every change, so what is built from the events knows to build again. */
    public int getRevision()
    {
        return this.revision;
    }

    /* Reading change i, in tick order once sorted */

    public int tick(int i)
    {
        return this.ticks[this.head + i];
    }

    public long pos(int i)
    {
        return this.positions[this.head + i];
    }

    public BlockState from(int i)
    {
        return this.palette.get(this.froms[this.head + i]);
    }

    public BlockState to(int i)
    {
        return this.palette.get(this.tos[this.head + i]);
    }

    /** The id of the dimension of change i, or null for one kept before dimensions were, which counts as in any. */
    public String dimension(int i)
    {
        int dim = this.dims[this.head + i];

        return dim < 0 ? null : this.dimensions.get(dim);
    }

    /** Whether change i happened in {@code id}: always for a change without a dimension, or with no id asked. */
    public boolean isIn(int i, String id)
    {
        int dim = this.dims[this.head + i];

        return dim < 0 || id == null || this.dimensions.get(dim).equals(id);
    }

    public void clear()
    {
        this.head = 0;
        this.size = 0;
        this.sorted = true;
        this.palette.clear();
        this.paletteIndex.clear();
        this.dimensions.clear();
        this.revision += 1;
    }

    private int block(BlockState state)
    {
        int index = this.paletteIndex.getInt(state);

        if (index < 0)
        {
            index = this.palette.size();
            this.palette.add(state);
            this.paletteIndex.put(state, index);
        }

        return index;
    }

    private short dimensionIndex(String id)
    {
        if (id == null)
        {
            return -1;
        }

        int index = this.dimensions.indexOf(id);

        if (index < 0)
        {
            index = this.dimensions.size();
            this.dimensions.add(id);
        }

        return (short) index;
    }

    public void add(int tick, BlockPos pos, BlockState from, BlockState to, String dimension)
    {
        if (from == to)
        {
            return;
        }

        this.push(tick, pos.asLong(), this.block(from), this.block(to), this.dimensionIndex(dimension));
    }

    private void push(int tick, long pos, int from, int to, short dim)
    {
        if (this.head + this.size == this.ticks.length)
        {
            this.grow();
        }

        int i = this.head + this.size;

        if (this.size > 0 && tick < this.ticks[i - 1])
        {
            this.sorted = false;
        }

        this.ticks[i] = tick;
        this.positions[i] = pos;
        this.froms[i] = from;
        this.tos[i] = to;
        this.dims[i] = dim;
        this.size += 1;
        this.revision += 1;
    }

    /** Room for more: the dropped front is reused first, the arrays double only when that is not enough. */
    private void grow()
    {
        if (this.head > 0 && this.head >= this.ticks.length / 4)
        {
            System.arraycopy(this.ticks, this.head, this.ticks, 0, this.size);
            System.arraycopy(this.positions, this.head, this.positions, 0, this.size);
            System.arraycopy(this.froms, this.head, this.froms, 0, this.size);
            System.arraycopy(this.tos, this.head, this.tos, 0, this.size);
            System.arraycopy(this.dims, this.head, this.dims, 0, this.size);
            this.head = 0;

            return;
        }

        int capacity = Math.max(256, this.ticks.length * 2);

        this.ticks = Arrays.copyOf(this.ticks, capacity);
        this.positions = Arrays.copyOf(this.positions, capacity);
        this.froms = Arrays.copyOf(this.froms, capacity);
        this.tos = Arrays.copyOf(this.tos, capacity);
        this.dims = Arrays.copyOf(this.dims, capacity);
    }

    /** Puts the changes in the order of their ticks, changes of one tick in the order they happened. */
    public void sort()
    {
        if (this.sorted)
        {
            return;
        }

        int[] order = new int[this.size];

        for (int i = 0; i < order.length; i++)
        {
            order[i] = this.head + i;
        }

        int[] keys = this.ticks;

        IntArrays.mergeSort(order, (a, b) -> Integer.compare(keys[a], keys[b]));

        int[] ticks = new int[Math.max(256, this.size)];
        long[] positions = new long[ticks.length];
        int[] froms = new int[ticks.length];
        int[] tos = new int[ticks.length];
        short[] dims = new short[ticks.length];

        for (int i = 0; i < order.length; i++)
        {
            int at = order[i];

            ticks[i] = this.ticks[at];
            positions[i] = this.positions[at];
            froms[i] = this.froms[at];
            tos[i] = this.tos[at];
            dims[i] = this.dims[at];
        }

        this.ticks = ticks;
        this.positions = positions;
        this.froms = froms;
        this.tos = tos;
        this.dims = dims;
        this.head = 0;
        this.sorted = true;
        this.revision += 1;
    }

    /** The first change with a tick at or after {@code tick}, the changes being sorted. */
    public int firstAtOrAfter(int tick)
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

    /** The changes from {@code from} on, their ticks counted from there. */
    public static RecapEvents tail(RecapEvents source, int from)
    {
        source.sort();

        RecapEvents events = new RecapEvents();
        int first = source.firstAtOrAfter(from);

        events.palette.addAll(source.palette);
        events.paletteIndex.putAll(source.paletteIndex);
        events.dimensions.addAll(source.dimensions);

        for (int i = first; i < source.size; i++)
        {
            int at = source.head + i;

            events.push(source.ticks[at] - from, source.positions[at], source.froms[at], source.tos[at], source.dims[at]);
        }

        return events;
    }

    /** Forgets the changes before {@code oldest}; the front is only moved on, not copied. */
    public void dropBefore(int oldest)
    {
        if (this.size == 0 || this.ticks[this.head] >= oldest)
        {
            return;
        }

        this.sort();
        this.dropFirst(this.firstAtOrAfter(oldest));
    }

    /** Forgets the {@code count} oldest changes. */
    public void dropFirst(int count)
    {
        count = Math.max(0, Math.min(this.size, count));

        if (count == 0)
        {
            return;
        }

        this.head += count;
        this.size -= count;
        this.revision += 1;

        if (this.size == 0)
        {
            this.head = 0;
        }
    }

    /* Files: a small head, then everything packed. The blocks are written as their names and
     * properties and the dimensions as their ids, each once in a table, so they read back in any
     * world; the ticks as the steps between them */

    public boolean write(File file)
    {
        this.sort();

        try (DataOutputStream raw = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
        {
            raw.writeInt(MAGIC);
            raw.writeInt(VERSION);

            Deflater deflater = new Deflater(Deflater.BEST_SPEED);

            try
            {
                DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 16);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 16));

                out.writeInt(this.palette.size());

                for (BlockState state : this.palette)
                {
                    out.writeUTF(NbtHelper.fromBlockState(state).toString());
                }

                out.writeInt(this.dimensions.size());

                for (String dimension : this.dimensions)
                {
                    out.writeUTF(dimension);
                }

                out.writeInt(this.size);

                int last = 0;

                for (int i = 0; i < this.size; i++)
                {
                    int at = this.head + i;

                    writeVar(out, this.ticks[at] - last);
                    last = this.ticks[at];
                    out.writeLong(this.positions[at]);
                    writeVar(out, this.froms[at]);
                    writeVar(out, this.tos[at]);
                    writeVar(out, this.dims[at] + 1);
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

    public static RecapEvents read(File file)
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

            if (version < 3)
            {
                return readPlain(raw, version);
            }

            try (InflaterInputStream packed = new InflaterInputStream(raw, new java.util.zip.Inflater(), 1 << 16))
            {
                return readPacked(new DataInputStream(new BufferedInputStream(packed, 1 << 16)));
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    private static BlockState[] readPalette(DataInput in) throws IOException
    {
        int count = in.readInt();

        if (count < 0 || count > 1 << 20)
        {
            throw new IOException("Bad palette");
        }

        BlockState[] palette = new BlockState[count];

        for (int i = 0; i < count; i++)
        {
            String text = in.readUTF();

            try
            {
                palette[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), StringNbtReader.parse(text));
            }
            catch (Exception e)
            {
                palette[i] = Blocks.AIR.getDefaultState();
            }
        }

        return palette;
    }

    private static String[] readDimensions(DataInput in) throws IOException
    {
        int count = in.readInt();

        if (count < 0 || count > Short.MAX_VALUE)
        {
            throw new IOException("Bad dimensions");
        }

        String[] dimensions = new String[count];

        for (int i = 0; i < count; i++)
        {
            dimensions[i] = in.readUTF();
        }

        return dimensions;
    }

    /** Version 1 and 2: plain, every field at full width. */
    private static RecapEvents readPlain(DataInputStream in, int version) throws IOException
    {
        BlockState[] palette = readPalette(in);
        String[] dimensions = version >= 2 ? readDimensions(in) : new String[0];
        int count = in.readInt();

        if (count < 0)
        {
            return null;
        }

        RecapEvents events = new RecapEvents();

        for (int i = 0; i < count; i++)
        {
            int tick = in.readInt();
            long pos = in.readLong();
            int from = in.readInt();
            int to = in.readInt();
            int dimension = version >= 2 ? in.readShort() : -1;

            events.load(palette, dimensions, tick, pos, from, to, dimension);
        }

        events.sort();

        return events;
    }

    private static RecapEvents readPacked(DataInputStream in) throws IOException
    {
        BlockState[] palette = readPalette(in);
        String[] dimensions = readDimensions(in);
        int count = in.readInt();

        if (count < 0)
        {
            return null;
        }

        RecapEvents events = new RecapEvents();
        int tick = 0;

        for (int i = 0; i < count; i++)
        {
            tick += readVar(in);

            long pos = in.readLong();
            int from = readVar(in);
            int to = readVar(in);
            int dimension = readVar(in) - 1;

            events.load(palette, dimensions, tick, pos, from, to, dimension);
        }

        events.sort();

        return events;
    }

    private void load(BlockState[] palette, String[] dimensions, int tick, long pos, int from, int to, int dimension)
    {
        if (from < 0 || from >= palette.length || to < 0 || to >= palette.length)
        {
            return;
        }

        String id = dimension >= 0 && dimension < dimensions.length ? dimensions[dimension] : null;

        this.push(tick, pos, this.block(palette[from]), this.block(palette[to]), this.dimensionIndex(id));
    }

    private static void writeVar(DataOutput out, int value) throws IOException
    {
        while ((value & ~0x7F) != 0)
        {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        out.writeByte(value);
    }

    private static int readVar(DataInput in) throws IOException
    {
        int value = 0;

        for (int shift = 0; shift < 35; shift += 7)
        {
            int b = in.readByte();

            value |= (b & 0x7F) << shift;

            if ((b & 0x80) == 0)
            {
                return value;
            }
        }

        throw new IOException("Bad number");
    }
}
