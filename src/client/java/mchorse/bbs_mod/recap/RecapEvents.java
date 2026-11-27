package mchorse.bbs_mod.recap;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * What happened to the world's blocks during a recap: block state changes, block entity NBT
 * snapshots (signs, chests, banners, skulls, campfires) deduplicated via {@link RecapPool}, and
 * synced block actions (chest lids, bells, pistons, note blocks).
 *
 * <p>Version 4 adds the deduplicated block entity NBT columns and block action stream while
 * keeping versions 1, 2 and 3 readable.</p>
 */
public class RecapEvents
{
    private static final int MAGIC = 0x52435731;
    private static final int VERSION = 4;

    private int[] ticks = new int[256];
    private long[] positions = new long[256];
    private int[] froms = new int[256];
    private int[] tos = new int[256];
    private int[] fromNbts = new int[256];
    private int[] toNbts = new int[256];
    private short[] dims = new short[256];
    private int head;
    private int size;
    private boolean sorted = true;

    /* Synced block actions (chest open count, piston event, note block, bell) */
    private int[] actionTicks = new int[32];
    private long[] actionPositions = new long[32];
    private int[] actionBlocks = new int[32];
    private byte[] actionTypes = new byte[32];
    private byte[] actionDatas = new byte[32];
    private short[] actionDims = new short[32];
    private int actionSize;
    private final List<String> blockIds = new ArrayList<>();

    private final RecapPool nbtPool = new RecapPool();
    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
    private final List<String> dimensions = new ArrayList<>();

    private int revision;

    public RecapEvents()
    {
        this.paletteIndex.defaultReturnValue(-1);
        Arrays.fill(this.fromNbts, -1);
        Arrays.fill(this.toNbts, -1);
    }

    public int size()
    {
        return this.size;
    }

    public int actionSize()
    {
        return this.actionSize;
    }

    public boolean isEmpty()
    {
        return this.size == 0 && this.actionSize == 0;
    }

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

    public String fromNbt(int i)
    {
        return this.nbtPool.getString(this.fromNbts[this.head + i]);
    }

    public String toNbt(int i)
    {
        return this.nbtPool.getString(this.toNbts[this.head + i]);
    }

    public int fromNbtId(int i)
    {
        return this.fromNbts[this.head + i];
    }

    public int toNbtId(int i)
    {
        return this.toNbts[this.head + i];
    }

    public String nbtString(int poolId)
    {
        return this.nbtPool.getString(poolId);
    }

    public String dimension(int i)
    {
        int dim = this.dims[this.head + i];

        return dim < 0 ? null : this.dimensions.get(dim);
    }

    public boolean isIn(int i, String id)
    {
        int dim = this.dims[this.head + i];

        return dim < 0 || id == null || this.dimensions.get(dim).equals(id);
    }

    /* Block actions */

    public int actionTick(int i)
    {
        return this.actionTicks[i];
    }

    public long actionPos(int i)
    {
        return this.actionPositions[i];
    }

    public Block actionBlock(int i)
    {
        int idx = this.actionBlocks[i];

        if (idx < 0 || idx >= this.blockIds.size())
        {
            return Blocks.AIR;
        }

        Identifier id = Identifier.tryParse(this.blockIds.get(idx));

        return id == null ? Blocks.AIR : Registries.BLOCK.get(id);
    }

    public int actionType(int i)
    {
        return this.actionTypes[i] & 0xFF;
    }

    public int actionData(int i)
    {
        return this.actionDatas[i] & 0xFF;
    }

    public boolean isActionIn(int i, String dimension)
    {
        int dim = this.actionDims[i];

        return dim < 0 || dimension == null || this.dimensions.get(dim).equals(dimension);
    }

    public int firstActionAtOrAfter(int tick)
    {
        int low = 0;
        int high = this.actionSize;

        while (low < high)
        {
            int mid = (low + high) >>> 1;

            if (this.actionTicks[mid] < tick)
            {
                low = mid + 1;
            }
            else
            {
                high = mid;
            }
        }

        return low;
    }

    public void clear()
    {
        this.head = 0;
        this.size = 0;
        this.sorted = true;
        this.actionSize = 0;
        this.blockIds.clear();
        this.nbtPool.clear();
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

    private int blockIdIndex(String id)
    {
        int index = this.blockIds.indexOf(id);

        if (index < 0)
        {
            index = this.blockIds.size();
            this.blockIds.add(id);
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
        this.add(tick, pos, from, to, null, null, dimension);
    }

    /** Records a block change along with optional before/after BlockEntity NBT payloads. */
    public void add(int tick, BlockPos pos, BlockState from, BlockState to, NbtCompound fromNbt, NbtCompound toNbt, String dimension)
    {
        int fNbt = fromNbt == null || fromNbt.isEmpty() ? -1 : this.nbtPool.putString(fromNbt.toString());
        int tNbt = toNbt == null || toNbt.isEmpty() ? -1 : this.nbtPool.putString(toNbt.toString());

        if (from == to && fNbt == tNbt)
        {
            return;
        }

        this.push(tick, pos.asLong(), this.block(from), this.block(to), fNbt, tNbt, this.dimensionIndex(dimension));
    }

    /** Records a synced block action (chest lid open/close, piston move, note block, bell). */
    public void addBlockAction(int tick, BlockPos pos, Block block, int type, int data, String dimension)
    {
        if (block == null || pos == null)
        {
            return;
        }

        Identifier id = Registries.BLOCK.getId(block);

        if (id == null)
        {
            return;
        }

        this.pushAction(tick, pos.asLong(), this.blockIdIndex(id.toString()), type, data, this.dimensionIndex(dimension));
    }

    private void pushAction(int tick, long pos, int blockIdx, int type, int data, short dim)
    {
        if (this.actionSize == this.actionTicks.length)
        {
            int cap = this.actionSize * 2;

            this.actionTicks = Arrays.copyOf(this.actionTicks, cap);
            this.actionPositions = Arrays.copyOf(this.actionPositions, cap);
            this.actionBlocks = Arrays.copyOf(this.actionBlocks, cap);
            this.actionTypes = Arrays.copyOf(this.actionTypes, cap);
            this.actionDatas = Arrays.copyOf(this.actionDatas, cap);
            this.actionDims = Arrays.copyOf(this.actionDims, cap);
        }

        int i = this.actionSize++;

        this.actionTicks[i] = tick;
        this.actionPositions[i] = pos;
        this.actionBlocks[i] = blockIdx;
        this.actionTypes[i] = (byte) type;
        this.actionDatas[i] = (byte) data;
        this.actionDims[i] = dim;
    }

    private void push(int tick, long pos, int from, int to, int fromNbt, int toNbt, short dim)
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
        this.fromNbts[i] = fromNbt;
        this.toNbts[i] = toNbt;
        this.dims[i] = dim;
        this.size += 1;
        this.revision += 1;
    }

    private void grow()
    {
        if (this.head > 0 && this.head >= this.ticks.length / 4)
        {
            System.arraycopy(this.ticks, this.head, this.ticks, 0, this.size);
            System.arraycopy(this.positions, this.head, this.positions, 0, this.size);
            System.arraycopy(this.froms, this.head, this.froms, 0, this.size);
            System.arraycopy(this.tos, this.head, this.tos, 0, this.size);
            System.arraycopy(this.fromNbts, this.head, this.fromNbts, 0, this.size);
            System.arraycopy(this.toNbts, this.head, this.toNbts, 0, this.size);
            System.arraycopy(this.dims, this.head, this.dims, 0, this.size);
            this.head = 0;

            return;
        }

        int capacity = Math.max(256, this.ticks.length * 2);

        this.ticks = Arrays.copyOf(this.ticks, capacity);
        this.positions = Arrays.copyOf(this.positions, capacity);
        this.froms = Arrays.copyOf(this.froms, capacity);
        this.tos = Arrays.copyOf(this.tos, capacity);
        this.fromNbts = Arrays.copyOf(this.fromNbts, capacity);
        this.toNbts = Arrays.copyOf(this.toNbts, capacity);
        this.dims = Arrays.copyOf(this.dims, capacity);
    }

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
        int[] fromNbts = new int[ticks.length];
        int[] toNbts = new int[ticks.length];
        short[] dims = new short[ticks.length];

        for (int i = 0; i < order.length; i++)
        {
            int at = order[i];

            ticks[i] = this.ticks[at];
            positions[i] = this.positions[at];
            froms[i] = this.froms[at];
            tos[i] = this.tos[at];
            fromNbts[i] = this.fromNbts[at];
            toNbts[i] = this.toNbts[at];
            dims[i] = this.dims[at];
        }

        this.ticks = ticks;
        this.positions = positions;
        this.froms = froms;
        this.tos = tos;
        this.fromNbts = fromNbts;
        this.toNbts = toNbts;
        this.dims = dims;
        this.head = 0;
        this.sorted = true;
        this.revision += 1;
    }

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

    /** Appends another slice's block changes and block actions shifted by {@code tickOffset}. */
    public void append(RecapEvents other, int tickOffset)
    {
        if (other == null || other.isEmpty())
        {
            return;
        }

        other.sort();

        int[] nbtRemap = this.nbtPool.mergeFrom(other.nbtPool);

        for (int i = 0; i < other.size; i++)
        {
            int at = other.head + i;
            String dim = other.dimension(i);

            this.push(other.ticks[at] + tickOffset, other.positions[at],
                this.block(other.palette.get(other.froms[at])),
                this.block(other.palette.get(other.tos[at])),
                RecapPool.remapId(nbtRemap, other.fromNbts[at]),
                RecapPool.remapId(nbtRemap, other.toNbts[at]),
                this.dimensionIndex(dim));
        }

        for (int i = 0; i < other.actionSize; i++)
        {
            int bIdx = other.actionBlocks[i];
            String blockName = bIdx >= 0 && bIdx < other.blockIds.size() ? other.blockIds.get(bIdx) : "minecraft:air";
            int dIdx = other.actionDims[i];
            String dim = dIdx >= 0 && dIdx < other.dimensions.size() ? other.dimensions.get(dIdx) : null;

            this.pushAction(other.actionTicks[i] + tickOffset, other.actionPositions[i],
                this.blockIdIndex(blockName), other.actionTypes[i] & 0xFF, other.actionDatas[i] & 0xFF, this.dimensionIndex(dim));
        }
    }

    public static RecapEvents tail(RecapEvents source, int from)
    {
        source.sort();

        RecapEvents events = new RecapEvents();
        int first = source.firstAtOrAfter(from);
        int[] nbtRemap = events.nbtPool.mergeFrom(source.nbtPool);

        events.palette.addAll(source.palette);
        events.paletteIndex.putAll(source.paletteIndex);
        events.dimensions.addAll(source.dimensions);
        events.blockIds.addAll(source.blockIds);

        for (int i = first; i < source.size; i++)
        {
            int at = source.head + i;

            events.push(source.ticks[at] - from, source.positions[at], source.froms[at], source.tos[at],
                RecapPool.remapId(nbtRemap, source.fromNbts[at]), RecapPool.remapId(nbtRemap, source.toNbts[at]), source.dims[at]);
        }

        for (int i = source.firstActionAtOrAfter(from); i < source.actionSize; i++)
        {
            events.pushAction(source.actionTicks[i] - from, source.actionPositions[i], source.actionBlocks[i],
                source.actionTypes[i] & 0xFF, source.actionDatas[i] & 0xFF, source.actionDims[i]);
        }

        return events;
    }

    public static RecapEvents copy(RecapEvents source)
    {
        source.sort();

        RecapEvents events = new RecapEvents();
        int[] nbtRemap = events.nbtPool.mergeFrom(source.nbtPool);

        events.palette.addAll(source.palette);
        events.paletteIndex.putAll(source.paletteIndex);
        events.dimensions.addAll(source.dimensions);
        events.blockIds.addAll(source.blockIds);

        int from = source.head;
        int to = source.head + Math.max(source.size, 1);

        events.ticks = Arrays.copyOfRange(source.ticks, from, to);
        events.positions = Arrays.copyOfRange(source.positions, from, to);
        events.froms = Arrays.copyOfRange(source.froms, from, to);
        events.tos = Arrays.copyOfRange(source.tos, from, to);
        events.fromNbts = new int[to - from];
        events.toNbts = new int[to - from];

        for (int i = 0; i < source.size; i++)
        {
            events.fromNbts[i] = RecapPool.remapId(nbtRemap, source.fromNbts[from + i]);
            events.toNbts[i] = RecapPool.remapId(nbtRemap, source.toNbts[from + i]);
        }

        events.dims = Arrays.copyOfRange(source.dims, from, to);
        events.size = source.size;

        if (source.actionSize > 0)
        {
            events.actionTicks = Arrays.copyOf(source.actionTicks, source.actionSize);
            events.actionPositions = Arrays.copyOf(source.actionPositions, source.actionSize);
            events.actionBlocks = Arrays.copyOf(source.actionBlocks, source.actionSize);
            events.actionTypes = Arrays.copyOf(source.actionTypes, source.actionSize);
            events.actionDatas = Arrays.copyOf(source.actionDatas, source.actionSize);
            events.actionDims = Arrays.copyOf(source.actionDims, source.actionSize);
            events.actionSize = source.actionSize;
        }

        events.revision += 1;

        return events;
    }

    public void dropBefore(int oldest)
    {
        if (this.size > 0 && this.ticks[this.head] < oldest)
        {
            this.sort();
            this.dropFirst(this.firstAtOrAfter(oldest));
        }

        if (this.actionSize > 0 && this.actionTicks[0] < oldest)
        {
            int drop = this.firstActionAtOrAfter(oldest);

            if (drop > 0)
            {
                int left = this.actionSize - drop;

                if (left > 0)
                {
                    System.arraycopy(this.actionTicks, drop, this.actionTicks, 0, left);
                    System.arraycopy(this.actionPositions, drop, this.actionPositions, 0, left);
                    System.arraycopy(this.actionBlocks, drop, this.actionBlocks, 0, left);
                    System.arraycopy(this.actionTypes, drop, this.actionTypes, 0, left);
                    System.arraycopy(this.actionDatas, drop, this.actionDatas, 0, left);
                    System.arraycopy(this.actionDims, drop, this.actionDims, 0, left);
                }

                this.actionSize = left;
            }
        }
    }

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
        this.sort();

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

        this.nbtPool.write(out);
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
            writeVar(out, this.fromNbts[at] + 1);
            writeVar(out, this.toNbts[at] + 1);
            writeVar(out, this.dims[at] + 1);
        }

        writeVar(out, this.blockIds.size());

        for (String blockId : this.blockIds)
        {
            out.writeUTF(blockId);
        }

        writeVar(out, this.actionSize);

        for (int i = 0; i < this.actionSize; i++)
        {
            out.writeInt(this.actionTicks[i]);
            out.writeLong(this.actionPositions[i]);
            writeVar(out, this.actionBlocks[i]);
            out.writeByte(this.actionTypes[i]);
            out.writeByte(this.actionDatas[i]);
            writeVar(out, this.actionDims[i] + 1);
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

            try (InflaterInputStream packed = new InflaterInputStream(raw, new Inflater(), 1 << 16))
            {
                DataInputStream in = new DataInputStream(new BufferedInputStream(packed, 1 << 16));

                return version == 3 ? readV3(in) : readFromStream(in);
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

            events.load(palette, dimensions, tick, pos, from, to, -1, -1, dimension);
        }

        events.sort();

        return events;
    }

    private static RecapEvents readV3(DataInputStream in) throws IOException
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

            events.load(palette, dimensions, tick, pos, from, to, -1, -1, dimension);
        }

        events.sort();

        return events;
    }

    public static RecapEvents readFromStream(DataInputStream in) throws IOException
    {
        BlockState[] palette = readPalette(in);
        String[] dimensions = readDimensions(in);
        RecapPool loadedPool = RecapPool.read(in);
        int count = in.readInt();

        if (count < 0)
        {
            throw new IOException("Bad event count");
        }

        RecapEvents events = new RecapEvents();
        int[] nbtRemap = events.nbtPool.mergeFrom(loadedPool);
        int tick = 0;

        for (int i = 0; i < count; i++)
        {
            tick += readVar(in);

            long pos = in.readLong();
            int from = readVar(in);
            int to = readVar(in);
            int fNbt = RecapPool.remapId(nbtRemap, readVar(in) - 1);
            int tNbt = RecapPool.remapId(nbtRemap, readVar(in) - 1);
            int dimension = readVar(in) - 1;

            events.load(palette, dimensions, tick, pos, from, to, fNbt, tNbt, dimension);
        }

        int blockCount = readVar(in);
        String[] blocks = new String[Math.max(0, Math.min(1 << 16, blockCount))];

        for (int i = 0; i < blockCount; i++)
        {
            String name = in.readUTF();

            if (i < blocks.length)
            {
                blocks[i] = name;
            }
        }

        int actions = readVar(in);

        for (int i = 0; i < actions; i++)
        {
            int aTick = in.readInt();
            long aPos = in.readLong();
            int bIdx = readVar(in);
            int aType = in.readByte() & 0xFF;
            int aData = in.readByte() & 0xFF;
            int dim = readVar(in) - 1;

            if (bIdx >= 0 && bIdx < blocks.length)
            {
                String dimId = dim >= 0 && dim < dimensions.length ? dimensions[dim] : null;

                events.pushAction(aTick, aPos, events.blockIdIndex(blocks[bIdx]), aType, aData, events.dimensionIndex(dimId));
            }
        }

        events.sort();

        return events;
    }

    private void load(BlockState[] palette, String[] dimensions, int tick, long pos, int from, int to, int fNbt, int tNbt, int dimension)
    {
        if (from < 0 || from >= palette.length || to < 0 || to >= palette.length)
        {
            return;
        }

        String id = dimension >= 0 && dimension < dimensions.length ? dimensions[dimension] : null;

        this.push(tick, pos, this.block(palette[from]), this.block(palette[to]), fNbt, tNbt, this.dimensionIndex(id));
    }

    private static void writeVar(DataOutput out, int value) throws IOException
    {
        RecapPool.writeVar(out, value);
    }

    private static int readVar(DataInput in) throws IOException
    {
        return RecapPool.readVar(in);
    }
}
