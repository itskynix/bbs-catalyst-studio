package mchorse.bbs_mod.recap;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.structure.BakedStructure;
import mchorse.bbs_mod.forms.structure.StructureRenderData;
import mchorse.bbs_mod.forms.structure.StructureRenderWorld;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Deduplicated local section baseline for cross-dimension playback in multiplayer and altered worlds.
 *
 * <p>When the recorded player enters a dimension, nearby {@code 16x16x16} sections are sampled
 * incrementally ({@code 8} sections per tick so recording never stutters) and stored in a
 * {@link RecapPool}. When Recap Studio views a stretch whose dimension differs from the live
 * multiplayer world, {@link #renderIsolated} bakes and draws the recorded stage through
 * {@link BakedStructure} with zero server travel required.</p>
 */
public class RecapBaseline
{
    private static final int MAGIC = 0x52434231;
    private static final int VERSION = 1;
    private static final int CHUNK_RADIUS = 2;
    private static final int VERTICAL_BELOW = 2;
    private static final int VERTICAL_ABOVE = 2;
    private static final int SECTIONS_PER_TICK = 8;

    private final RecapPool sectionPool = new RecapPool();
    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
    private final Map<String, Long2IntOpenHashMap> byDimension = new HashMap<>();

    /* Incremental capture queue on the game thread */
    private String pendingDimension;
    private final LongArrayList pendingSections = new LongArrayList();
    private int pendingCursor;

    /* Cached isolated stage mesh for playback */
    private transient String cachedDimension;
    private transient int cachedEventState = Integer.MIN_VALUE;
    private transient BlockPos cachedOrigin = BlockPos.ORIGIN;
    private transient StructureRenderWorld cachedWorld;
    private transient BakedStructure cachedBaked;

    public RecapBaseline()
    {
        this.paletteIndex.defaultReturnValue(-1);
    }

    private void ensureAir()
    {
        if (this.palette.isEmpty())
        {
            BlockState air = Blocks.AIR.getDefaultState();

            this.palette.add(air);
            this.paletteIndex.put(air, 0);
        }
    }

    public boolean isEmpty()
    {
        return this.byDimension.isEmpty();
    }

    public boolean hasDimension(String dimension)
    {
        Long2IntOpenHashMap map = dimension == null ? null : this.byDimension.get(dimension);

        return map != null && !map.isEmpty();
    }

    public void clear()
    {
        this.sectionPool.clear();
        this.palette.clear();
        this.paletteIndex.clear();
        this.byDimension.clear();
        this.pendingDimension = null;
        this.pendingSections.clear();
        this.pendingCursor = 0;
        this.invalidateCache();
    }

    public void invalidateCache()
    {
        this.cachedDimension = null;
        this.cachedEventState = Integer.MIN_VALUE;
        this.cachedWorld = null;
        this.cachedBaked = null;
    }

    /** Queues nearby sections around {@code around} the first time {@code dimension} is visited, draining a few per tick. */
    public void recordStep(ClientWorld world, Entity around, String dimension)
    {
        if (world == null || around == null || dimension == null || dimension.isEmpty())
        {
            return;
        }

        if (!this.byDimension.containsKey(dimension) && !dimension.equals(this.pendingDimension))
        {
            this.pendingDimension = dimension;
            this.pendingSections.clear();
            this.pendingCursor = 0;

            int cx = around.getBlockX() >> 4;
            int cy = around.getBlockY() >> 4;
            int cz = around.getBlockZ() >> 4;
            int minSec = world.getBottomSectionCoord();
            int maxSec = world.getTopSectionCoord() - 1;

            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++)
            {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++)
                {
                    for (int dy = -VERTICAL_BELOW; dy <= VERTICAL_ABOVE; dy++)
                    {
                        int sy = cy + dy;

                        if (sy >= minSec && sy <= maxSec)
                        {
                            this.pendingSections.add(ChunkSectionPos.asLong(cx + dx, sy, cz + dz));
                        }
                    }
                }
            }
        }

        if (dimension.equals(this.pendingDimension) && this.pendingCursor < this.pendingSections.size())
        {
            Long2IntOpenHashMap map = this.byDimension.computeIfAbsent(dimension, (k) ->
            {
                Long2IntOpenHashMap m = new Long2IntOpenHashMap();

                m.defaultReturnValue(-1);

                return m;
            });

            int limit = Math.min(this.pendingSections.size(), this.pendingCursor + SECTIONS_PER_TICK);

            while (this.pendingCursor < limit)
            {
                long secKey = this.pendingSections.getLong(this.pendingCursor++);

                this.captureSection(world, secKey, map);
            }

            if (this.pendingCursor >= this.pendingSections.size())
            {
                this.pendingDimension = null;
                this.pendingSections.clear();
            }
        }
    }

    private void captureSection(ClientWorld world, long secKey, Long2IntOpenHashMap into)
    {
        int sx = ChunkSectionPos.unpackX(secKey);
        int sy = ChunkSectionPos.unpackY(secKey);
        int sz = ChunkSectionPos.unpackZ(secKey);

        if (!world.getChunkManager().isChunkLoaded(sx, sz))
        {
            return;
        }

        WorldChunk chunk = world.getChunk(sx, sz);
        int sectionIdx = world.sectionCoordToIndex(sy);
        ChunkSection[] sections = chunk.getSectionArray();

        if (sectionIdx < 0 || sectionIdx >= sections.length)
        {
            return;
        }

        ChunkSection section = sections[sectionIdx];

        if (section == null || section.isEmpty())
        {
            return;
        }

        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(512);
            DataOutputStream out = new DataOutputStream(bos);
            boolean anySolid = false;

            for (int y = 0; y < 16; y++)
            {
                for (int z = 0; z < 16; z++)
                {
                    for (int x = 0; x < 16; x++)
                    {
                        BlockState state = section.getBlockState(x, y, z);

                        if (!state.isAir())
                        {
                            anySolid = true;
                        }

                        RecapPool.writeVar(out, this.paletteId(state));
                    }
                }
            }

            if (anySolid)
            {
                out.flush();
                into.put(secKey, this.sectionPool.put(bos.toByteArray()));
            }
        }
        catch (IOException ignored)
        {}
    }

    private int paletteId(BlockState state)
    {
        this.ensureAir();

        int idx = this.paletteIndex.getInt(state);

        if (idx < 0)
        {
            idx = this.palette.size();
            this.palette.add(state);
            this.paletteIndex.put(state, idx);
        }

        return idx;
    }

    public void append(RecapBaseline other)
    {
        if (other == null || other.isEmpty())
        {
            return;
        }

        int[] palRemap = new int[other.palette.size()];

        for (int i = 0; i < palRemap.length; i++)
        {
            palRemap[i] = this.paletteId(other.palette.get(i));
        }

        for (Map.Entry<String, Long2IntOpenHashMap> entry : other.byDimension.entrySet())
        {
            if (this.byDimension.containsKey(entry.getKey()))
            {
                continue;
            }

            Long2IntOpenHashMap dst = new Long2IntOpenHashMap();

            dst.defaultReturnValue(-1);

            for (Long2IntOpenHashMap.Entry sec : entry.getValue().long2IntEntrySet())
            {
                byte[] remapped = remapSectionBytes(other.sectionPool.get(sec.getIntValue()), palRemap);

                if (remapped != null)
                {
                    dst.put(sec.getLongKey(), this.sectionPool.put(remapped));
                }
            }

            if (!dst.isEmpty())
            {
                this.byDimension.put(entry.getKey(), dst);
            }
        }
    }

    private static byte[] remapSectionBytes(byte[] src, int[] palRemap)
    {
        if (src == null || src.length == 0)
        {
            return null;
        }

        try
        {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(src));
            ByteArrayOutputStream bos = new ByteArrayOutputStream(src.length);
            DataOutputStream out = new DataOutputStream(bos);

            for (int i = 0; i < 4096; i++)
            {
                int oldId = RecapPool.readVar(in);
                int newId = oldId >= 0 && oldId < palRemap.length ? palRemap[oldId] : 0;

                RecapPool.writeVar(out, newId);
            }

            out.flush();

            return bos.toByteArray();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /** Atmosphere fog color for an isolated cross-dimension stage. */
    public static int atmosphereColor(String dimension)
    {
        if (dimension == null)
        {
            return 0xc0d8ff;
        }

        return switch (dimension)
        {
            case "minecraft:the_nether" -> 0x330808;
            case "minecraft:the_end" -> 0x100c1c;
            default -> 0xc0d8ff;
        };
    }

    /** Draws the isolated cross-dimension terrain stage when the client is in another dimension on a server. */
    public void renderIsolated(WorldRenderContext context, RecapEvents events, String dimension, int sourceTick)
    {
        Long2IntOpenHashMap sections = dimension == null ? null : this.byDimension.get(dimension);

        if (sections == null || sections.isEmpty() || context.matrixStack() == null)
        {
            return;
        }

        int eventStamp = eventStampAt(events, dimension, sourceTick);

        if (!dimension.equals(this.cachedDimension) || eventStamp != this.cachedEventState || this.cachedBaked == null
            || this.cachedWorld == null || !this.cachedBaked.isValidFor(this.cachedWorld))
        {
            this.rebuildStage(sections, events, dimension, sourceTick, eventStamp);
        }

        if (this.cachedBaked == null)
        {
            return;
        }

        Vec3d cam = context.camera().getPos();
        MatrixStack stack = context.matrixStack();
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int skyLight = "minecraft:the_nether".equals(dimension) || "minecraft:the_end".equals(dimension)
            ? LightmapTextureManager.pack(8, 7)
            : LightmapTextureManager.MAX_LIGHT_COORDINATE;

        stack.push();

        try
        {
            stack.translate(this.cachedOrigin.getX() - cam.x, this.cachedOrigin.getY() - cam.y, this.cachedOrigin.getZ() - cam.z);
            this.cachedBaked.render(stack.peek(), consumers, skyLight, 0xFFFFFFFF);
            consumers.draw();
        }
        finally
        {
            stack.pop();
        }
    }

    private static int eventStampAt(RecapEvents events, String dimension, int tick)
    {
        if (events == null || events.isEmpty())
        {
            return 0;
        }

        events.sort();

        int end = events.firstAtOrAfter(tick + 1);
        int hash = 17;

        for (int i = 0; i < end; i++)
        {
            if (events.isIn(i, dimension))
            {
                hash = hash * 31 + i;
            }
        }

        return hash;
    }

    private void rebuildStage(Long2IntOpenHashMap sections, RecapEvents events, String dimension, int tick, int stamp)
    {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (long secKey : sections.keySet())
        {
            int bx = ChunkSectionPos.unpackX(secKey) << 4;
            int by = ChunkSectionPos.unpackY(secKey) << 4;
            int bz = ChunkSectionPos.unpackZ(secKey) << 4;

            minX = Math.min(minX, bx);
            minY = Math.min(minY, by);
            minZ = Math.min(minZ, bz);
            maxX = Math.max(maxX, bx + 16);
            maxY = Math.max(maxY, by + 16);
            maxZ = Math.max(maxZ, bz + 16);
        }

        if (minX >= maxX)
        {
            return;
        }

        Map<BlockPos, BlockState> worldBlocks = new LinkedHashMap<>();

        for (Long2IntOpenHashMap.Entry sec : sections.long2IntEntrySet())
        {
            byte[] bytes = this.sectionPool.get(sec.getIntValue());

            if (bytes.length == 0)
            {
                continue;
            }

            int bx = ChunkSectionPos.unpackX(sec.getLongKey()) << 4;
            int by = ChunkSectionPos.unpackY(sec.getLongKey()) << 4;
            int bz = ChunkSectionPos.unpackZ(sec.getLongKey()) << 4;

            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes)))
            {
                for (int y = 0; y < 16; y++)
                {
                    for (int z = 0; z < 16; z++)
                    {
                        for (int x = 0; x < 16; x++)
                        {
                            int id = RecapPool.readVar(in);

                            if (id > 0 && id < this.palette.size())
                            {
                                BlockState state = this.palette.get(id);

                                if (!state.isAir())
                                {
                                    worldBlocks.put(new BlockPos(bx + x - minX, by + y - minY, bz + z - minZ), state);
                                }
                            }
                        }
                    }
                }
            }
            catch (IOException ignored)
            {}
        }

        if (events != null && !events.isEmpty())
        {
            events.sort();

            for (int i = 0; i < events.size() && events.tick(i) <= tick; i++)
            {
                if (!events.isIn(i, dimension))
                {
                    continue;
                }

                BlockPos wp = BlockPos.fromLong(events.pos(i));

                if (wp.getX() >= minX && wp.getX() < maxX && wp.getY() >= minY && wp.getY() < maxY && wp.getZ() >= minZ && wp.getZ() < maxZ)
                {
                    BlockPos local = new BlockPos(wp.getX() - minX, wp.getY() - minY, wp.getZ() - minZ);
                    BlockState to = events.to(i);

                    if (to.isAir())
                    {
                        worldBlocks.remove(local);
                    }
                    else
                    {
                        worldBlocks.put(local, to);
                    }
                }
            }
        }

        String biome = "minecraft:the_nether".equals(dimension) ? "minecraft:nether_wastes"
            : ("minecraft:the_end".equals(dimension) ? "minecraft:the_end" : "minecraft:plains");
        Vec3i size = new Vec3i(maxX - minX, maxY - minY, maxZ - minZ);
        StructureRenderData data = StructureRenderData.create("recap_stage", size, worldBlocks, Collections.emptyMap());

        this.cachedOrigin = new BlockPos(minX, minY, minZ);
        this.cachedDimension = dimension;
        this.cachedEventState = stamp;
        this.cachedWorld = new StructureRenderWorld(data, biome);
        this.cachedBaked = BakedStructure.bake(data, this.cachedWorld);
    }

    public boolean write(File file)
    {
        if (this.isEmpty())
        {
            return true;
        }

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
        this.ensureAir();
        RecapPool.writeVar(out, this.palette.size());

        for (BlockState state : this.palette)
        {
            out.writeUTF(NbtHelper.fromBlockState(state).toString());
        }

        this.sectionPool.write(out);
        RecapPool.writeVar(out, this.byDimension.size());

        for (Map.Entry<String, Long2IntOpenHashMap> entry : this.byDimension.entrySet())
        {
            out.writeUTF(entry.getKey());
            Long2IntOpenHashMap map = entry.getValue();
            RecapPool.writeVar(out, map.size());

            for (Long2IntOpenHashMap.Entry sec : map.long2IntEntrySet())
            {
                out.writeLong(sec.getLongKey());
                RecapPool.writeVar(out, sec.getIntValue());
            }
        }
    }

    public static RecapBaseline read(File file)
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

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 16), 1 << 16)))
            {
                return readFromStream(in);
            }
        }
        catch (IOException | RuntimeException e)
        {
            return null;
        }
    }

    public static RecapBaseline readFromStream(DataInputStream in) throws IOException
    {
        RecapBaseline baseline = new RecapBaseline();

        baseline.palette.clear();
        baseline.paletteIndex.clear();

        int palSize = RecapPool.readVar(in);

        if (palSize < 0 || palSize > 1 << 18)
        {
            throw new IOException("Bad baseline palette");
        }

        for (int i = 0; i < palSize; i++)
        {
            String text = in.readUTF();
            BlockState state;

            try
            {
                NbtCompound nbt = StringNbtReader.parse(text);
                state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt);
            }
            catch (Exception e)
            {
                state = Blocks.AIR.getDefaultState();
            }

            baseline.palette.add(state);
            baseline.paletteIndex.putIfAbsent(state, i);
        }

        RecapPool loadedSections = RecapPool.read(in);
        int[] secRemap = baseline.sectionPool.mergeFrom(loadedSections);
        int dims = RecapPool.readVar(in);

        for (int d = 0; d < dims; d++)
        {
            String dim = in.readUTF();
            int count = RecapPool.readVar(in);
            Long2IntOpenHashMap map = new Long2IntOpenHashMap();

            map.defaultReturnValue(-1);

            for (int i = 0; i < count; i++)
            {
                long secKey = in.readLong();
                int poolId = RecapPool.remapId(secRemap, RecapPool.readVar(in));

                if (poolId >= 0)
                {
                    map.put(secKey, poolId);
                }
            }

            if (!map.isEmpty())
            {
                baseline.byDimension.put(dim, map);
            }
        }

        return baseline;
    }
}
