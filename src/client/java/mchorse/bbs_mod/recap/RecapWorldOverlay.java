package mchorse.bbs_mod.recap;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import mchorse.bbs_mod.BBSMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;

/**
 * The one owner of the blocks Recap Studio puts on the world: while a recap is shown, every block
 * it changed stands as it was at the cursor, and when it is no longer shown every one of them is
 * as the world really has it now, never as the recording started.
 *
 * <p><b>Client only.</b> The blocks are set in the client's own world and nowhere else; the server,
 * and in single player the world on disk, never hear of them. Nothing of it can outlive the client
 * world it was put on: that world going (a world left, a dimension changed) takes it along, and the
 * server sends the real blocks of the next one.</p>
 *
 * <p><b>The real blocks.</b> What each block the recap touched really is, is taken the first time
 * the overlay sets it, and kept up to date after: a change the server sends for one of them while
 * the recap is shown (another player, a piston, an explosion) becomes its real state, and the
 * recap's block goes back over it; a chunk let go of forgets its real blocks, a chunk sent again
 * brings them back. Putting them back ({@link #detach}) therefore gives the world as it is.</p>
 *
 * <p><b>Dimensions.</b> Only the changes of the dimension the client is in are put on the world;
 * a change made in another dimension is never put at the same coordinates here.</p>
 *
 * <p><b>Cost.</b> The first moment shown sets every block the recap touched; after that, moving
 * the cursor from one tick to another only sets the blocks changed between the two, so scrubbing
 * a long recap with thousands of changes costs what changes between the two moments.</p>
 */
public final class RecapWorldOverlay
{
    private static RecapEvents source;
    private static int sourceRevision;

    /* The client world the blocks are on, its dimension, and the index of its changes */
    private static ClientWorld world;
    private static String dimension;
    private static Index index;

    /** The real state of every block set, by position. */
    private static final Long2ObjectOpenHashMap<BlockState> live = new Long2ObjectOpenHashMap<>();
    /** Blocks the recap's state must be put on again at the same tick: a real change came over them. */
    private static final LongOpenHashSet dirty = new LongOpenHashSet();
    private static final LongOpenHashSet changed = new LongOpenHashSet();
    private static int appliedTick = Integer.MIN_VALUE;

    private static boolean applying;
    private static boolean warned;

    private RecapWorldOverlay()
    {}

    /** Whether the overlay itself is setting blocks right now, which is not play and not the server. */
    public static boolean isApplying()
    {
        return applying;
    }

    public static boolean isAttached()
    {
        return source != null;
    }

    /** The world the blocks are on, or null. */
    public static ClientWorld getWorld()
    {
        return world;
    }

    /** Shows these changes; the ones shown before are taken off first, unless they are these. */
    public static void attach(RecapEvents events)
    {
        if (events == source)
        {
            return;
        }

        detach();

        source = events;
        sourceRevision = events == null ? 0 : events.getRevision();
    }

    /**
     * Takes everything off: the blocks go back to what the world really has, when the world they
     * were put on is still the one the client is in; else they are simply let go of.
     */
    public static void detach()
    {
        ClientWorld current = MinecraftClient.getInstance().world;

        if (world != null && world == current)
        {
            writeBack();
        }

        release();
        source = null;
    }

    /** Lets go of everything without setting a block: for a world that is gone or going. */
    public static void drop()
    {
        release();
        source = null;
    }

    private static void release()
    {
        world = null;
        dimension = null;
        index = null;
        live.clear();
        dirty.clear();
        changed.clear();
        appliedTick = Integer.MIN_VALUE;
    }

    /** Once a client tick: the world the blocks were put on gone, they go with it, unset. */
    public static void tick(MinecraftClient mc)
    {
        if (world != null && mc.world != world)
        {
            release();
        }
    }

    public static String dimensionOf(ClientWorld world)
    {
        return world == null ? null : world.getRegistryKey().getValue().toString();
    }

    /** Puts the recap's blocks of {@code tick} on {@code into}, the client's world. */
    public static void update(ClientWorld into, int tick)
    {
        if (source == null || into == null)
        {
            return;
        }

        if (into != world)
        {
            /* Another world than the one the blocks are on: the last one is gone, so no write back */
            release();
            world = into;
            dimension = dimensionOf(into);
        }

        if (index == null || sourceRevision != source.getRevision())
        {
            sourceRevision = source.getRevision();
            index = new Index(source.getEvents(), dimension);
            appliedTick = Integer.MIN_VALUE;
        }

        if (index.isEmpty())
        {
            return;
        }

        applying = true;

        try
        {
            if (appliedTick == Integer.MIN_VALUE)
            {
                /* The first moment: every block of this dimension the recap touched */
                for (Long2ObjectMap.Entry<long[]> entry : index.byChunk.long2ObjectEntrySet())
                {
                    if (isLoaded(entry.getLongKey()))
                    {
                        for (long pos : entry.getValue())
                        {
                            put(pos, tick);
                        }
                    }
                }

                dirty.clear();
            }
            else if (tick != appliedTick)
            {
                /* Only the blocks changed between the last moment and this one */
                index.changedBetween(appliedTick, tick, changed);

                LongIterator it = changed.iterator();

                while (it.hasNext())
                {
                    long pos = it.nextLong();

                    if (isLoaded(chunkOf(pos)))
                    {
                        put(pos, tick);
                    }
                }

                changed.clear();
            }

            if (!dirty.isEmpty())
            {
                LongIterator it = dirty.iterator();

                while (it.hasNext())
                {
                    long pos = it.nextLong();

                    if (isLoaded(chunkOf(pos)))
                    {
                        put(pos, tick);
                    }
                }

                dirty.clear();
            }

            appliedTick = tick;
        }
        finally
        {
            applying = false;
        }
    }

    /** Sets one block to the recap's state at {@code tick}, its real state kept first. */
    private static void put(long packed, int tick)
    {
        BlockState wanted = index.stateAt(packed, tick);

        if (wanted == null)
        {
            return;
        }

        BlockPos pos = BlockPos.fromLong(packed);
        BlockState current = world.getBlockState(pos);

        live.putIfAbsent(packed, current);

        if (wanted != current)
        {
            set(pos, wanted);
        }
    }

    private static void set(BlockPos pos, BlockState state)
    {
        try
        {
            world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        catch (RuntimeException e)
        {
            /* One block the world will not take does not stop the others */
            if (!warned)
            {
                warned = true;
                BBSMod.LOGGER.warn("Recap could not set a block at " + pos.toShortString(), e);
            }
        }
    }

    private static void writeBack()
    {
        if (live.isEmpty())
        {
            return;
        }

        applying = true;

        try
        {
            for (Long2ObjectMap.Entry<BlockState> entry : live.long2ObjectEntrySet())
            {
                long packed = entry.getLongKey();

                if (!isLoaded(chunkOf(packed)))
                {
                    continue;
                }

                BlockPos pos = BlockPos.fromLong(packed);

                if (world.getBlockState(pos) != entry.getValue())
                {
                    set(pos, entry.getValue());
                }
            }
        }
        finally
        {
            applying = false;
        }
    }

    /* What comes from outside while the recap is shown */

    /**
     * A block of a client world changed, not by the overlay: when it is one the recap touched, the
     * change is what the world really has there now, and the recap's state goes back over it.
     */
    public static void onBlockChanged(ClientWorld in, BlockPos pos, BlockState to)
    {
        if (applying || in != world || index == null)
        {
            return;
        }

        long packed = pos.asLong();

        if (index.touches(packed))
        {
            live.put(packed, to);
            dirty.add(packed);
        }
    }

    /** A chunk came in: its blocks are the real ones, so the recap's go over them again. */
    public static void onChunkLoad(ClientWorld in, WorldChunk chunk)
    {
        if (in != world || index == null)
        {
            return;
        }

        long[] positions = index.byChunk.get(chunk.getPos().toLong());

        if (positions == null)
        {
            return;
        }

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (long packed : positions)
        {
            live.put(packed, chunk.getBlockState(pos.set(packed)));
            dirty.add(packed);
        }
    }

    /** A chunk was let go of: its real blocks will come with it, if it comes again. */
    public static void onChunkUnload(ClientWorld in, WorldChunk chunk)
    {
        if (in != world || index == null)
        {
            return;
        }

        long[] positions = index.byChunk.get(chunk.getPos().toLong());

        if (positions == null)
        {
            return;
        }

        for (long packed : positions)
        {
            live.remove(packed);
            dirty.remove(packed);
        }
    }

    private static long chunkOf(long packed)
    {
        return ChunkPos.toLong(BlockPos.unpackLongX(packed) >> 4, BlockPos.unpackLongZ(packed) >> 4);
    }

    private static boolean isLoaded(long chunk)
    {
        return world.getChunkManager().isChunkLoaded(ChunkPos.getPackedX(chunk), ChunkPos.getPackedZ(chunk));
    }

    /**
     * The changes of one dimension, laid out for quick lookups: each block's changes in the order
     * of their ticks, the blocks of each chunk, and all the changes by tick.
     */
    static final class Index
    {
        private final List<RecapEvents.Event> events;
        /** Indices of the dimension's changes, in the order of their ticks. */
        private final int[] order;
        private final int[] orderTicks;
        private final Long2ObjectOpenHashMap<int[]> byPos = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<long[]> byChunk = new Long2ObjectOpenHashMap<>();

        Index(List<RecapEvents.Event> events, String dimension)
        {
            this.events = events;

            IntArrayList kept = new IntArrayList();
            boolean sorted = true;
            int last = Integer.MIN_VALUE;

            for (int i = 0; i < events.size(); i++)
            {
                RecapEvents.Event event = events.get(i);

                if (event.isIn(dimension))
                {
                    kept.add(i);
                    sorted &= event.tick() >= last;
                    last = Math.max(last, event.tick());
                }
            }

            this.order = kept.toIntArray();

            if (!sorted)
            {
                /* Stable by tick, so changes of one tick keep the order they happened in */
                it.unimi.dsi.fastutil.ints.IntArrays.mergeSort(this.order, (a, b) -> Integer.compare(events.get(a).tick(), events.get(b).tick()));
            }

            this.orderTicks = new int[this.order.length];

            Long2ObjectOpenHashMap<IntArrayList> positions = new Long2ObjectOpenHashMap<>();
            Long2ObjectOpenHashMap<LongArrayList> chunks = new Long2ObjectOpenHashMap<>();

            for (int i = 0; i < this.order.length; i++)
            {
                RecapEvents.Event event = events.get(this.order[i]);
                IntArrayList list = positions.get(event.pos());

                this.orderTicks[i] = event.tick();

                if (list == null)
                {
                    list = new IntArrayList(2);
                    positions.put(event.pos(), list);
                    chunks.computeIfAbsent(chunkOf(event.pos()), (key) -> new LongArrayList()).add(event.pos());
                }

                list.add(this.order[i]);
            }

            for (Long2ObjectMap.Entry<IntArrayList> entry : positions.long2ObjectEntrySet())
            {
                this.byPos.put(entry.getLongKey(), entry.getValue().toIntArray());
            }

            for (Long2ObjectMap.Entry<LongArrayList> entry : chunks.long2ObjectEntrySet())
            {
                this.byChunk.put(entry.getLongKey(), entry.getValue().toLongArray());
            }
        }

        boolean isEmpty()
        {
            return this.order.length == 0;
        }

        boolean touches(long pos)
        {
            return this.byPos.containsKey(pos);
        }

        /** The block at {@code pos} at {@code tick}: its last change at or before it, else what it was before the first. */
        BlockState stateAt(long pos, int tick)
        {
            int[] list = this.byPos.get(pos);

            if (list == null || list.length == 0)
            {
                return null;
            }

            int low = 0;
            int high = list.length - 1;
            int found = -1;

            while (low <= high)
            {
                int middle = (low + high) >>> 1;

                if (this.events.get(list[middle]).tick() <= tick)
                {
                    found = middle;
                    low = middle + 1;
                }
                else
                {
                    high = middle - 1;
                }
            }

            return found < 0 ? this.events.get(list[0]).from() : this.events.get(list[found]).to();
        }

        /** The blocks with a change after the earlier of two ticks and up to the later one. */
        void changedBetween(int a, int b, LongOpenHashSet out)
        {
            int from = Math.min(a, b);
            int to = Math.max(a, b);

            for (int i = firstAfter(from); i < this.order.length && this.orderTicks[i] <= to; i++)
            {
                out.add(this.events.get(this.order[i]).pos());
            }
        }

        /** The first change with a tick after {@code tick}. */
        private int firstAfter(int tick)
        {
            int low = 0;
            int high = this.orderTicks.length;

            while (low < high)
            {
                int middle = (low + high) >>> 1;

                if (this.orderTicks[middle] <= tick)
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
    }
}
