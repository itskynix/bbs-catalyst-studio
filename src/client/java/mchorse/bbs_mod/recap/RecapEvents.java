package mchorse.bbs_mod.recap;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened to the world's blocks during a recap: every block broken, placed or changed by
 * use (a door opened, a lever thrown), each with the tick it happened on, the dimension it
 * happened in, the block before and the block after. In the order they happened.
 *
 * <p>This is only the recording. Putting it on the world while Recap Studio shows the recap, and
 * taking it off again, is {@link RecapWorldOverlay}'s alone.</p>
 */
public class RecapEvents
{
    private static final int MAGIC = 0x52435731;
    private static final int VERSION = 2;

    private final List<Event> events = new ArrayList<>();
    private int revision;

    /**
     * One change of one block. The dimension is the id of the one it happened in, or null for a
     * change kept before dimensions were, which counts as happening in any.
     */
    public record Event(int tick, long pos, BlockState from, BlockState to, String dimension)
    {
        public boolean isIn(String id)
        {
            return this.dimension == null || id == null || this.dimension.equals(id);
        }
    }

    public int size()
    {
        return this.events.size();
    }

    public boolean isEmpty()
    {
        return this.events.isEmpty();
    }

    public List<Event> getEvents()
    {
        return this.events;
    }

    /** Goes up with every change, so what is built from the events knows to build again. */
    public int getRevision()
    {
        return this.revision;
    }

    public void clear()
    {
        this.events.clear();
        this.revision += 1;
    }

    public void add(int tick, BlockPos pos, BlockState from, BlockState to, String dimension)
    {
        if (from == to)
        {
            return;
        }

        this.events.add(new Event(tick, pos.asLong(), from, to, dimension));
        this.revision += 1;
    }

    /** The changes from {@code from} on, their ticks counted from there. */
    public static RecapEvents tail(RecapEvents source, int from)
    {
        RecapEvents events = new RecapEvents();

        for (Event event : source.events)
        {
            if (event.tick >= from)
            {
                events.events.add(new Event(event.tick - from, event.pos, event.from, event.to, event.dimension));
            }
        }

        return events;
    }

    /** Forgets the changes before {@code oldest}. */
    public void dropBefore(int oldest)
    {
        if (!this.events.isEmpty() && this.events.get(0).tick < oldest)
        {
            this.events.removeIf((event) -> event.tick < oldest);
            this.revision += 1;
        }
    }

    /* Files: the blocks are written as their names and properties and the dimensions as their
     * ids, each once in a table, so they read back in any world */

    public boolean write(File file)
    {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        List<String> dimensions = new ArrayList<>();

        for (Event event : this.events)
        {
            for (BlockState state : new BlockState[] {event.from, event.to})
            {
                if (!index.containsKey(state))
                {
                    index.put(state, palette.size());
                    palette.add(state);
                }
            }

            if (event.dimension != null && !dimensions.contains(event.dimension))
            {
                dimensions.add(event.dimension);
            }
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
        {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(palette.size());

            for (BlockState state : palette)
            {
                out.writeUTF(NbtHelper.fromBlockState(state).toString());
            }

            out.writeInt(dimensions.size());

            for (String dimension : dimensions)
            {
                out.writeUTF(dimension);
            }

            out.writeInt(this.events.size());

            for (Event event : this.events)
            {
                out.writeInt(event.tick);
                out.writeLong(event.pos);
                out.writeInt(index.get(event.from));
                out.writeInt(index.get(event.to));
                out.writeShort(event.dimension == null ? -1 : dimensions.indexOf(event.dimension));
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

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
        {
            int version;

            if (in.readInt() != MAGIC || (version = in.readInt()) < 1 || version > VERSION)
            {
                return null;
            }

            int paletteSize = in.readInt();

            if (paletteSize < 0 || paletteSize > 1 << 20)
            {
                return null;
            }

            BlockState[] palette = new BlockState[paletteSize];

            for (int i = 0; i < palette.length; i++)
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

            String[] dimensions = new String[0];

            if (version >= 2)
            {
                int count = in.readInt();

                if (count < 0 || count > Short.MAX_VALUE)
                {
                    return null;
                }

                dimensions = new String[count];

                for (int i = 0; i < count; i++)
                {
                    dimensions[i] = in.readUTF();
                }
            }

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

                if (from >= 0 && from < palette.length && to >= 0 && to < palette.length)
                {
                    String id = dimension >= 0 && dimension < dimensions.length ? dimensions[dimension] : null;

                    events.events.add(new Event(tick, pos, palette[from], palette[to], id));
                }
            }

            return events;
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
