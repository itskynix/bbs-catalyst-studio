package mchorse.bbs_mod.recap;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
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
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

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
 * brings them back. Putting them back ({@link #detach}) therefore gives the world as it is, all at
 * once.</p>
 *
 * <p><b>Dimensions.</b> Only the changes of the dimension the client is in are put on the world;
 * a change made in another dimension is never put at the same coordinates here.</p>
 *
 * <p><b>Cost.</b> Moving the cursor from one tick to another only touches the blocks changed
 * between the two, in either direction. Those blocks wait in a queue, by chunk section, and a
 * frame sets no more than {@link #BUDGET} of them, whole sections at a time so each section is
 * rebuilt once: a tick that blew up fifty thousand blocks comes in over a few frames instead of
 * stopping one. What a queued block is set to is read when it is set, from the latest cursor, so
 * the queue is never behind the picture by more than its length.</p>
 *
 * <p><b>Video.</b> While a video is being recorded every frame is whole: the queue is emptied with
 * no budget, sections are rebuilt on the spot and the light is brought up to date before the
 * frame is drawn, and a frame is not taken until all of it is done ({@link #isSettled}).</p>
 */
public final class RecapWorldOverlay
{
    /** How many blocks a frame sets while playing or scrubbing. */
    public static final int BUDGET = 2000;

    private static RecapEvents source;
    private static int sourceRevision;

    /* The client world the blocks are on, its dimension, and the index of its changes */
    private static ClientWorld world;
    private static String dimension;
    private static Index index;

    /** The real state of every block set, by position. */
    private static final Long2ObjectOpenHashMap<BlockState> live = new Long2ObjectOpenHashMap<>();
    /** The blocks waiting to be set to the cursor's state, by chunk section, oldest section first. */
    private static final Long2ObjectLinkedOpenHashMap<LongOpenHashSet> queue = new Long2ObjectLinkedOpenHashMap<>();
    private static int queued;
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

    /** How many blocks still wait to be set. */
    public static int getQueued()
    {
        return queued;
    }

    /**
     * Whether the world shows the cursor's blocks fully: none waiting, and the client's light done
     * with them. A video frame is only taken then.
     */
    public static boolean isSettled()
    {
        if (source == null || world == null)
        {
            return true;
        }

        return queued == 0 && !world.getLightingProvider().hasUpdates();
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
     * Takes everything off: the blocks go back to what the world really has, all at once, when the
     * world they were put on is still the one the client is in; else they are simply let go of.
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
        queue.clear();
        queued = 0;
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

    private static boolean isExporting()
    {
        return mchorse.bbs_mod.BBSModClient.getVideoRecorder().isRecording();
    }

    /**
     * Once a frame: puts the recap's blocks of {@code tick} on {@code into}, the client's world, as
     * many as the frame's budget allows, all of them while a video is being recorded.
     */
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
            index = new Index(source, dimension);
            sourceRevision = source.getRevision();
            appliedTick = Integer.MIN_VALUE;
        }

        if (index.isEmpty())
        {
            return;
        }

        if (appliedTick == Integer.MIN_VALUE)
        {
            /* The first moment: every block of this dimension the recap touched, chunk by chunk */
            for (Long2ObjectMap.Entry<long[]> entry : index.byChunk.long2ObjectEntrySet())
            {
                if (isLoaded(entry.getLongKey()))
                {
                    for (long pos : entry.getValue())
                    {
                        enqueue(pos);
                    }
                }
            }
        }
        else if (tick != appliedTick)
        {
            /* Only the blocks changed between the last moment and this one */
            index.changedBetween(appliedTick, tick, changed);

            LongIterator it = changed.iterator();

            while (it.hasNext())
            {
                enqueue(it.nextLong());
            }

            changed.clear();
        }

        appliedTick = tick;

        boolean exporting = isExporting();

        drain(exporting ? Integer.MAX_VALUE : BUDGET, exporting);

        if (exporting)
        {
            settleLight();
        }
    }

    private static void enqueue(long pos)
    {
        if (queue.computeIfAbsent(ChunkSectionPos.fromBlockPos(pos), (key) -> new LongOpenHashSet()).add(pos))
        {
            queued += 1;
        }
    }

    /** Sets queued blocks, whole sections at a time, until {@code budget} blocks were looked at. */
    private static void drain(int budget, boolean onTheSpot)
    {
        if (queue.isEmpty())
        {
            return;
        }

        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | (onTheSpot ? Block.REDRAW_ON_MAIN_THREAD : 0);
        int done = 0;

        applying = true;

        try
        {
            while (!queue.isEmpty() && done < budget)
            {
                LongOpenHashSet section = queue.removeFirst();

                queued -= section.size();

                LongIterator it = section.iterator();

                while (it.hasNext())
                {
                    long pos = it.nextLong();

                    if (isLoaded(chunkOf(pos)))
                    {
                        put(pos, appliedTick, flags);
                    }

                    done += 1;
                }
            }
        }
        finally
        {
            applying = false;
        }
    }

    /** Brings the client's light up to date with the blocks set, before the frame is drawn. */
    private static void settleLight()
    {
        LightingProvider light = world.getLightingProvider();

        for (int i = 0; i < 64 && light.hasUpdates(); i++)
        {
            light.doLightUpdates();
        }
    }

    /** Sets one block to the recap's state at {@code tick}, its real state kept first. */
    private static void put(long packed, int tick, int flags)
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
            set(pos, wanted, flags);
        }
    }

    private static void set(BlockPos pos, BlockState state, int flags)
    {
        try
        {
            world.setBlockState(pos, state, flags);
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

    /** Every block set goes back to its real state, all at once, with no budget. */
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
                    set(pos, entry.getValue(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
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
            enqueue(packed);
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
            enqueue(packed);
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

            long key = ChunkSectionPos.fromBlockPos(packed);
            LongOpenHashSet section = queue.get(key);

            if (section != null && section.remove(packed))
            {
                queued -= 1;

                if (section.isEmpty())
                {
                    queue.remove(key);
                }
            }
        }
    }

    static long chunkOf(long packed)
    {
        return ChunkPos.toLong(BlockPos.unpackLongX(packed) >> 4, BlockPos.unpackLongZ(packed) >> 4);
    }

    private static boolean isLoaded(long chunk)
    {
        return world.getChunkManager().isChunkLoaded(ChunkPos.getPackedX(chunk), ChunkPos.getPackedZ(chunk));
    }

    /**
     * The changes of one dimension, laid out for quick lookups: all of them by tick, each block's
     * changes in the order of their ticks, and the blocks of each chunk.
     */
    static final class Index
    {
        private final RecapEvents events;
        /** The dimension's changes, as places in the (sorted) events. */
        private final int[] order;
        private final int[] orderTicks;
        private final Long2ObjectOpenHashMap<int[]> byPos = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<long[]> byChunk = new Long2ObjectOpenHashMap<>();

        Index(RecapEvents events, String dimension)
        {
            events.sort();

            this.events = events;

            IntArrayList kept = new IntArrayList();

            for (int i = 0; i < events.size(); i++)
            {
                if (events.isIn(i, dimension))
                {
                    kept.add(i);
                }
            }

            this.order = kept.toIntArray();
            this.orderTicks = new int[this.order.length];

            Long2ObjectOpenHashMap<IntArrayList> positions = new Long2ObjectOpenHashMap<>();
            Long2ObjectOpenHashMap<LongArrayList> chunks = new Long2ObjectOpenHashMap<>();

            for (int i = 0; i < this.order.length; i++)
            {
                int at = this.order[i];
                long pos = events.pos(at);
                IntArrayList list = positions.get(pos);

                this.orderTicks[i] = events.tick(at);

                if (list == null)
                {
                    list = new IntArrayList(2);
                    positions.put(pos, list);
                    chunks.computeIfAbsent(chunkOf(pos), (key) -> new LongArrayList()).add(pos);
                }

                list.add(at);
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

                if (this.events.tick(list[middle]) <= tick)
                {
                    found = middle;
                    low = middle + 1;
                }
                else
                {
                    high = middle - 1;
                }
            }

            return found < 0 ? this.events.from(list[0]) : this.events.to(list[found]);
        }

        /** The blocks with a change after the earlier of two ticks and up to the later one: what differs between the two. */
        void changedBetween(int a, int b, LongOpenHashSet out)
        {
            int from = Math.min(a, b);
            int to = Math.max(a, b);

            for (int i = firstAfter(from); i < this.order.length && this.orderTicks[i] <= to; i++)
            {
                out.add(this.events.pos(this.order[i]));
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
