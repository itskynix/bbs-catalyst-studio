package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Segmented background streaming ({@link Session}) and zero-shift ring buffering ({@link Ring})
 * for Recap recordings, paired with PID-locked crash recovery journals.
 *
 * <p><b>Why.</b> Instead of holding hours of recording in heap arrays and duplicating them on
 * {@code save()}, active sessions swap out sealed 5-minute ({@code 6000}-tick) slices in {@code O(1)}
 * on the game thread and stream them to {@code bbs_cs/recaps/.active/<id>/slice_NNNN.dat} on
 * {@link RecapWriter}'s data thread with {@code FileChannel.force(true)} durability.</p>
 */
public final class RecapSpool
{
    public static final int SLICE_TICKS = 20 * 60 * 5;
    public static final int SLICE_EVENT_CAP = 500_000;
    public static final int RING_SLICE_TICKS = 20 * 30;
    private static final int SLICE_MAGIC = 0x5243534C;
    private static final int SLICE_VERSION = 1;

    private RecapSpool()
    {}

    /** One contiguous time slice of all Recap recording channels, starting at local tick {@code 0}. */
    public static final class Slice
    {
        public int startTick;
        public int ticks;
        public final RecapTrack track = new RecapTrack();
        public final RecapMotion motion = new RecapMotion();
        public final RecapEvents events = new RecapEvents();
        public final RecapEnvironment environment = new RecapEnvironment();
        public final RecapGear gear = new RecapGear();
        public final RecapEntities entities = new RecapEntities();
        public final RecapSounds sounds = new RecapSounds();
        public final RecapParticles particles = new RecapParticles();
        public final RecapHudStream hud = new RecapHudStream();
        public final RecapBaseline baseline = new RecapBaseline();

        public Slice(int startTick)
        {
            this.startTick = startTick;
        }

        public void clear(int nextStartTick)
        {
            this.startTick = nextStartTick;
            this.ticks = 0;
            this.track.clear();
            this.motion.clear();
            this.events.clear();
            this.environment.clear();
            this.gear.clear();
            this.entities.clear();
            this.sounds.clear();
            this.particles.clear();
            this.hud.clear();
            this.baseline.clear();
        }

        public RecapData toData()
        {
            RecapData data = new RecapData();

            data.track = this.track;
            data.motion = this.motion;
            data.events = this.events;
            data.environment = this.environment;
            data.gear = this.gear;
            data.entities = this.entities;
            data.sounds = this.sounds;
            data.particles = this.particles;
            data.hud = this.hud;
            data.baseline = this.baseline;

            return data;
        }

        public boolean write(File file)
        {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");

            try
            {
                if (file.getParentFile() != null)
                {
                    file.getParentFile().mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(tmp))
                {
                    DataOutputStream raw = new DataOutputStream(new BufferedOutputStream(fos));

                    raw.writeInt(SLICE_MAGIC);
                    raw.writeInt(SLICE_VERSION);
                    raw.writeInt(this.startTick);
                    raw.writeInt(this.ticks);

                    Deflater deflater = new Deflater(Deflater.BEST_SPEED);

                    try
                    {
                        DeflaterOutputStream packed = new DeflaterOutputStream(raw, deflater, 1 << 16);
                        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(packed, 1 << 16));

                        this.track.writeToStream(out);
                        this.motion.writeToStream(out);
                        this.events.writeToStream(out);
                        this.environment.writeToStream(out);
                        this.gear.writeToStream(out);
                        this.entities.writeToStream(out);
                        this.sounds.writeToStream(out);
                        this.particles.writeToStream(out);
                        this.hud.writeToStream(out);
                        this.baseline.writeToStream(out);

                        out.flush();
                        packed.finish();
                    }
                    finally
                    {
                        deflater.end();
                    }

                    raw.flush();
                    fos.getChannel().force(true);
                }

                moveAtomically(tmp, file);

                return true;
            }
            catch (Throwable e)
            {
                BBSMod.LOGGER.error("Recap could not write slice " + file.getName(), e);
                tmp.delete();

                return false;
            }
        }

        public static Slice read(File file)
        {
            if (!file.isFile())
            {
                return null;
            }

            try (DataInputStream raw = new DataInputStream(new BufferedInputStream(new FileInputStream(file))))
            {
                if (raw.readInt() != SLICE_MAGIC || raw.readInt() != SLICE_VERSION)
                {
                    return null;
                }

                int startTick = raw.readInt();
                int ticks = raw.readInt();

                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw, new Inflater(), 1 << 16), 1 << 16)))
                {
                    Slice slice = new Slice(startTick);

                    slice.ticks = ticks;
                    slice.track.append(RecapTrack.readFromStream(in));
                    slice.motion.append(RecapMotion.readFromStream(in));
                    slice.events.append(RecapEvents.readFromStream(in), 0);
                    slice.environment.append(RecapEnvironment.readFromStream(in));
                    slice.gear.append(RecapGear.readFromStream(in));
                    slice.entities.append(RecapEntities.readFromStream(in), 0);
                    slice.sounds.append(RecapSounds.readFromStream(in), 0);
                    slice.particles.append(RecapParticles.readFromStream(in), 0);
                    slice.hud.append(RecapHudStream.readFromStream(in), 0);
                    slice.baseline.append(RecapBaseline.readFromStream(in));

                    return slice;
                }
            }
            catch (Throwable e)
            {
                BBSMod.LOGGER.warn("Recap slice could not be read: {}", file.getName(), e);

                return null;
            }
        }
    }

    /** Zero-shift ring of 30-second slices for Instant Recap. */
    public static final class Ring
    {
        private final Deque<Slice> buckets = new ArrayDeque<>();
        private Slice recycled;
        private Slice current;

        public void clear()
        {
            this.buckets.clear();
            this.recycled = null;
            this.current = null;
        }

        public Slice active(int globalTick, int maxHistoryTicks)
        {
            if (this.current == null || this.current.ticks >= RING_SLICE_TICKS)
            {
                Slice prev = this.current;
                Slice next = this.recycled != null ? this.recycled : new Slice(globalTick);

                this.recycled = null;
                next.clear(globalTick);

                if (prev != null)
                {
                    next.gear.seedFrom(prev.gear);
                }

                this.buckets.addLast(next);
                this.current = next;

                /* Recycle expired buckets outside the maximum history window in O(1) */
                while (this.buckets.size() > 2)
                {
                    Slice oldest = this.buckets.peekFirst();

                    if (oldest != null && oldest.startTick + oldest.ticks < globalTick - maxHistoryTicks)
                    {
                        this.recycled = this.buckets.removeFirst();
                    }
                    else
                    {
                        break;
                    }
                }
            }

            return this.current;
        }

        public Slice current()
        {
            return this.current;
        }

        public int availableTicks(int globalTick)
        {
            if (this.buckets.isEmpty())
            {
                return 0;
            }

            Slice oldest = this.buckets.peekFirst();

            return Math.max(0, globalTick - oldest.startTick);
        }

        /** Extracts a merged {@link RecapData} for the last {@code length} ticks without mutating the active bucket. */
        public RecapData extractTail(int globalTick, int length)
        {
            int fromTick = Math.max(0, globalTick - length);
            RecapData merged = new RecapData();

            merged.track = new RecapTrack();
            merged.motion = new RecapMotion();
            merged.events = new RecapEvents();
            merged.environment = new RecapEnvironment();
            merged.gear = new RecapGear();
            merged.entities = new RecapEntities();
            merged.sounds = new RecapSounds();
            merged.particles = new RecapParticles();
            merged.hud = new RecapHudStream();
            merged.baseline = new RecapBaseline();

            int writtenTicks = 0;

            for (Slice bucket : this.buckets)
            {
                int bucketEnd = bucket.startTick + bucket.ticks;

                if (bucketEnd <= fromTick || bucket.ticks <= 0)
                {
                    continue;
                }

                if (bucket.startTick < fromTick)
                {
                    int keep = bucketEnd - fromTick;
                    int localFrom = fromTick - bucket.startTick;

                    merged.track.append(RecapTrack.tail(bucket.track, keep));
                    merged.motion.append(RecapMotion.tail(bucket.motion, keep));
                    merged.events.append(RecapEvents.tail(bucket.events, localFrom), writtenTicks);
                    merged.environment.append(RecapEnvironment.tail(bucket.environment, keep));
                    merged.gear.append(RecapGear.tail(bucket.gear, keep));
                    merged.entities.append(RecapEntities.tail(bucket.entities, localFrom), writtenTicks);
                    merged.sounds.append(RecapSounds.tail(bucket.sounds, localFrom), writtenTicks);
                    merged.particles.append(RecapParticles.tail(bucket.particles, localFrom), writtenTicks);
                    merged.hud.append(RecapHudStream.tail(bucket.hud, localFrom), writtenTicks);
                    merged.baseline.append(bucket.baseline);
                    writtenTicks += keep;
                }
                else
                {
                    merged.track.append(bucket.track);
                    merged.motion.append(bucket.motion);
                    merged.events.append(bucket.events, writtenTicks);
                    merged.environment.append(bucket.environment);
                    merged.gear.append(bucket.gear);
                    merged.entities.append(bucket.entities, writtenTicks);
                    merged.sounds.append(bucket.sounds, writtenTicks);
                    merged.particles.append(bucket.particles, writtenTicks);
                    merged.hud.append(bucket.hud, writtenTicks);
                    merged.baseline.append(bucket.baseline);
                    writtenTicks += bucket.ticks;
                }
            }

            return merged;
        }
    }

    /** Active manual recording session backed by {@code .active/<id>/}. */
    public static final class Session
    {
        public final String id;
        public final RecapWorlds.Info world;
        public final long created;
        public final File dir;
        private int nextSliceIndex;

        public Session(String id, RecapWorlds.Info world)
        {
            this.id = id;
            this.world = world;
            this.created = System.currentTimeMillis();
            this.dir = new File(activeRoot(), id);
            this.dir.mkdirs();
        }

        public File journalFile()
        {
            return new File(this.dir, "journal.json");
        }

        public static File sliceFile(File dir, int index)
        {
            return new File(dir, String.format(Locale.ROOT, "slice_%04d.dat", index));
        }

        public int committedCount()
        {
            return this.nextSliceIndex;
        }

        /** Queues a sealed slice to disk and updates {@code journal.json} atomically on the data writer. */
        public void spoolSlice(Slice sealed, int totalTicks, List<Integer> markers, RecapDimensions dimensions, boolean cleanStop)
        {
            int index = this.nextSliceIndex++;
            File target = sliceFile(this.dir, index);
            MapType journalData = buildJournal(this.id, this.world, this.created, totalTicks, index + 1, markers, dimensions, cleanStop);

            RecapWriter.writeData(target, () ->
            {
                if (!sealed.write(target))
                {
                    return false;
                }

                return writeJournalAtomically(this.journalFile(), journalData);
            });
        }

        /** Finalizes the session into permanent {@link RecapStore} files on the background data thread. */
        public void finalizeToStore(RecapRecord record, Slice tailSlice, boolean fastExit)
        {
            if (this.nextSliceIndex == 0 && !fastExit)
            {
                /* Single-slice session (<= 5 min): write standard sidecars straight from memory, zero disk round-trip */
                File sessionDir = this.dir;

                RecapStore.writeAllDirect(record, tailSlice.toData(), () -> deleteDirectory(sessionDir));

                return;
            }

            if (tailSlice != null && tailSlice.ticks > 0)
            {
                List<Integer> markersCopy = new ArrayList<>(record.markers);
                RecapDimensions dimsCopy = new RecapDimensions();

                dimsCopy.copy(record.dimensions);
                this.spoolSlice(tailSlice, record.ticks, markersCopy, dimsCopy, true);
            }
            else
            {
                MapType journalData = buildJournal(this.id, this.world, this.created, record.ticks, this.nextSliceIndex, record.markers, record.dimensions, true);

                RecapWriter.writeData(this.journalFile(), () -> writeJournalAtomically(this.journalFile(), journalData));
            }

            if (fastExit)
            {
                /* Game is closing: slices and clean_stop journal are already queued; startup finishes merging instantly */
                return;
            }

            int totalSlices = this.nextSliceIndex;
            File sessionDir = this.dir;

            RecapStore.consolidateSessionAsync(record, sessionDir, totalSlices);
        }

        public void discard()
        {
            File sessionDir = this.dir;

            RecapWriter.writeData(new File(sessionDir, ".discard"), () ->
            {
                deleteDirectory(sessionDir);

                return true;
            });
        }
    }

    public static File activeRoot()
    {
        return new File(RecapStore.folder(), ".active");
    }

    public static MapType buildJournal(String id, RecapWorlds.Info world, long created, int ticks, int slices, List<Integer> markers, RecapDimensions dimensions, boolean cleanStop)
    {
        MapType map = new MapType();
        ListType markerList = new ListType();

        for (int m : markers)
        {
            markerList.addInt(m);
        }

        map.putString("id", id);
        map.putLong("pid", currentPid());
        map.putString("world_key", world.key());
        map.putString("world_name", world.name());
        map.putString("world_target", world.target());
        map.putBool("singleplayer", world.singleplayer());
        map.putLong("created", created);
        map.putInt("ticks", ticks);
        map.putInt("slices", slices);
        map.putBool("clean_stop", cleanStop);
        map.put("markers", markerList);

        if (!dimensions.isEmpty())
        {
            map.put("dimensions", dimensions.toData());
        }

        return map;
    }

    public static boolean writeJournalAtomically(File journal, MapType data)
    {
        File folder = journal.getParentFile();
        File tmp = new File(folder, journal.getName() + ".tmp");
        File bak = new File(folder, journal.getName() + ".bak");

        try
        {
            if (folder != null)
            {
                folder.mkdirs();
            }

            byte[] bytes = DataToString.toString(data, true).getBytes(StandardCharsets.UTF_8);

            try (FileOutputStream fos = new FileOutputStream(tmp))
            {
                fos.write(bytes);
                fos.flush();
                fos.getChannel().force(true);
            }

            if (journal.isFile())
            {
                try
                {
                    Files.copy(journal.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                catch (IOException ignored)
                {}
            }

            moveAtomically(tmp, journal);

            return true;
        }
        catch (Throwable e)
        {
            BBSMod.LOGGER.warn("Recap journal could not be written", e);
            tmp.delete();

            return false;
        }
    }

    public static MapType readMapWithBackup(File primary)
    {
        MapType map = tryReadMap(primary);

        if (map != null)
        {
            return map;
        }

        File bak = new File(primary.getParentFile(), primary.getName() + ".bak");

        return tryReadMap(bak);
    }

    private static MapType tryReadMap(File file)
    {
        if (file == null || !file.isFile())
        {
            return null;
        }

        try
        {
            BaseType data = DataToString.read(file);

            if (data != null && data.isMap())
            {
                return data.asMap();
            }
        }
        catch (Exception ignored)
        {}

        return null;
    }

    public static long currentPid()
    {
        try
        {
            return ProcessHandle.current().pid();
        }
        catch (Throwable e)
        {
            return -1L;
        }
    }

    public static boolean isOtherProcessAlive(long pid)
    {
        if (pid <= 0L || pid == currentPid())
        {
            return false;
        }

        try
        {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    static void moveAtomically(File from, File to) throws IOException
    {
        try
        {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteDirectory(File dir)
    {
        if (dir == null || !dir.exists())
        {
            return;
        }

        File[] children = dir.listFiles();

        if (children != null)
        {
            for (File child : children)
            {
                if (child.isDirectory())
                {
                    deleteDirectory(child);
                }
                else
                {
                    child.delete();
                }
            }
        }

        dir.delete();
    }
}
