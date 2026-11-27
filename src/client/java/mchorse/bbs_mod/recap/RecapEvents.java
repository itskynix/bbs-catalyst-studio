package mchorse.bbs_mod.recap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What happened to the world's blocks during a recap: every block broken, placed or changed by
 * use (a door opened, a lever thrown), each with the tick it happened on, the block before and the
 * block after.
 *
 * <p><b>Played back</b> on the client only, and only while Recap Studio shows the recap: every
 * block the recap touched is set to what it was at the cursor, the last change at or before it,
 * or what it was before its first change. The blocks as they really are were taken down when the
 * recap was first shown and are put back when it is no longer ({@link #restore}); the server never
 * hears of any of it. What a block is at a tick depends on the tick alone, so a scrubbed moment
 * and a played one look the same.</p>
 */
public class RecapEvents
{
    private static final int MAGIC = 0x52435731;
    private static final int VERSION = 1;

    /** Set while the recap's own blocks are being put in, so the recorder does not take them for play. */
    public static boolean applying;

    private final List<Event> events = new ArrayList<>();

    /* Playback */
    private Map<Long, List<Event>> byPosition;
    private final Map<Long, BlockState> live = new HashMap<>();
    private ClientWorld appliedWorld;
    private int appliedTick = Integer.MIN_VALUE;

    public record Event(int tick, long pos, BlockState from, BlockState to)
    {}

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

    public void clear()
    {
        this.events.clear();
        this.byPosition = null;
    }

    public void add(int tick, BlockPos pos, BlockState from, BlockState to)
    {
        if (from == to)
        {
            return;
        }

        this.events.add(new Event(tick, pos.asLong(), from, to));
        this.byPosition = null;
    }

    /** The changes from {@code from} on, their ticks counted from there. */
    public static RecapEvents tail(RecapEvents source, int from)
    {
        RecapEvents events = new RecapEvents();

        for (Event event : source.events)
        {
            if (event.tick >= from)
            {
                events.events.add(new Event(event.tick - from, event.pos, event.from, event.to));
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
            this.byPosition = null;
        }
    }

    /* Playback */

    private Map<Long, List<Event>> byPosition()
    {
        if (this.byPosition == null)
        {
            this.byPosition = new LinkedHashMap<>();

            for (Event event : this.events)
            {
                this.byPosition.computeIfAbsent(event.pos, (key) -> new ArrayList<>()).add(event);
            }
        }

        return this.byPosition;
    }

    /** What the block at a position was at {@code tick}: the last change at or before it, else before the first. */
    public BlockState stateAt(long pos, int tick)
    {
        List<Event> list = this.byPosition().get(pos);

        if (list == null || list.isEmpty())
        {
            return null;
        }

        BlockState state = list.get(0).from;

        for (Event event : list)
        {
            if (event.tick <= tick)
            {
                state = event.to;
            }
            else
            {
                break;
            }
        }

        return state;
    }

    /** Sets every block the recap touched to what it was at {@code tick}, once per tick. */
    public void apply(ClientWorld world, int tick)
    {
        if (world == null || this.events.isEmpty())
        {
            return;
        }

        if (world != this.appliedWorld)
        {
            this.live.clear();
            this.appliedWorld = world;
            this.appliedTick = Integer.MIN_VALUE;
        }

        if (tick == this.appliedTick)
        {
            return;
        }

        this.appliedTick = tick;
        applying = true;

        try
        {
            for (Map.Entry<Long, List<Event>> entry : this.byPosition().entrySet())
            {
                BlockPos pos = BlockPos.fromLong(entry.getKey());

                if (!world.isChunkLoaded(pos))
                {
                    continue;
                }

                BlockState wanted = this.stateAt(entry.getKey(), tick);
                BlockState current = world.getBlockState(pos);

                this.live.putIfAbsent(entry.getKey(), current);

                if (wanted != null && wanted != current)
                {
                    world.setBlockState(pos, wanted, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
            }
        }
        finally
        {
            applying = false;
        }
    }

    /** Puts back the blocks as they really were before the recap was shown. */
    public void restore()
    {
        ClientWorld world = this.appliedWorld;

        this.appliedWorld = null;
        this.appliedTick = Integer.MIN_VALUE;

        if (world == null || world != net.minecraft.client.MinecraftClient.getInstance().world)
        {
            this.live.clear();

            return;
        }

        applying = true;

        try
        {
            for (Map.Entry<Long, BlockState> entry : this.live.entrySet())
            {
                BlockPos pos = BlockPos.fromLong(entry.getKey());

                if (world.isChunkLoaded(pos) && world.getBlockState(pos) != entry.getValue())
                {
                    world.setBlockState(pos, entry.getValue(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
            }
        }
        finally
        {
            applying = false;
            this.live.clear();
        }
    }

    /* Files: the blocks are written as their names and properties, so they read back in any world */

    public boolean write(File file)
    {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();

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

            out.writeInt(this.events.size());

            for (Event event : this.events)
            {
                out.writeInt(event.tick);
                out.writeLong(event.pos);
                out.writeInt(index.get(event.from));
                out.writeInt(index.get(event.to));
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
            if (in.readInt() != MAGIC || in.readInt() != VERSION)
            {
                return null;
            }

            int paletteSize = in.readInt();
            BlockState[] palette = new BlockState[Math.max(0, paletteSize)];

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

            int count = in.readInt();
            RecapEvents events = new RecapEvents();

            for (int i = 0; i < count; i++)
            {
                int tick = in.readInt();
                long pos = in.readLong();
                int from = in.readInt();
                int to = in.readInt();

                if (from >= 0 && from < palette.length && to >= 0 && to < palette.length)
                {
                    events.events.add(new Event(tick, pos, palette[from], palette[to]));
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
