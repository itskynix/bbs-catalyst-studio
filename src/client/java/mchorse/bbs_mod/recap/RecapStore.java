package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The recaps on this computer, one file each in {@code <game>/bbs_cs/recaps}, with their camera,
 * movement, blocks and sky in files of the same name next to it.
 *
 * <p><b>Saving</b> takes a copy of what is saved on the game thread and leaves the writing to
 * {@link RecapWriter}, in the background and whole or not at all; reading a file waits for its
 * write first, so a recap opened right after it was saved reads back as saved.</p>
 */
public final class RecapStore
{
    private static final String EXTENSION = ".json";

    private RecapStore()
    {}

    public static File folder()
    {
        File folder = new File(MinecraftClient.getInstance().runDirectory, "bbs_cs/recaps");

        folder.mkdirs();

        return folder;
    }

    /** Every recap that can be read, the newest first. */
    public static List<RecapRecord> list()
    {
        RecapWriter.awaitAll((file) -> file.getName().endsWith(EXTENSION));

        List<RecapRecord> records = new ArrayList<>();
        File[] files = folder().listFiles((dir, name) -> name.endsWith(EXTENSION));

        if (files == null)
        {
            return records;
        }

        for (File file : files)
        {
            RecapRecord record = load(file);

            if (record != null)
            {
                records.add(record);
            }
        }

        records.sort(Comparator.comparingLong((RecapRecord r) -> r.created).reversed().thenComparing((r) -> r.id));

        return records;
    }

    public static RecapRecord get(String id)
    {
        File file = file(id);
        File bak = new File(folder(), id + EXTENSION + ".bak");

        RecapWriter.await(file);

        return file.isFile() || bak.isFile() ? load(file) : null;
    }

    private static RecapRecord load(File file)
    {
        try
        {
            MapType map = RecapSpool.readMapWithBackup(file);

            if (map != null)
            {
                String name = file.getName();

                return RecapRecord.fromData(name.substring(0, name.length() - EXTENSION.length()), map);
            }
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("A recap file could not be read: {}", file.getName(), e);
        }

        return null;
    }

    /** Saves the recap's own file in the background; false only when it has no name to be saved under. */
    public static boolean save(RecapRecord record)
    {
        return save(record, null);
    }

    /** As {@link #save(RecapRecord)}, {@code done} hearing on the game thread whether it was written. */
    public static boolean save(RecapRecord record, Consumer<Boolean> done)
    {
        if (record.id.isEmpty())
        {
            do
            {
                record.id = UUID.randomUUID().toString().substring(0, 8);
            }
            while (file(record.id).exists() || RecapWriter.isPending(file(record.id)));
        }

        MapType data = record.toData();

        RecapWriter.write(file(record.id), (into) ->
        {
            Files.writeString(into.toPath(), DataToString.toString(data, true), StandardCharsets.UTF_8);

            return true;
        }, done);

        return true;
    }

    public static boolean delete(RecapRecord record)
    {
        if (record.id.isEmpty())
        {
            return false;
        }

        File[] files = {trackFile(record.id), side(record.id, ".motion"), side(record.id, ".world"), side(record.id, ".env"), side(record.id, ".gear"),
            side(record.id, ".mobs"), side(record.id, ".sounds"), side(record.id, ".particles"), side(record.id, ".hud"), side(record.id, ".base"),
            side(record.id, ".pov"),
            new File(folder(), record.id + EXTENSION + ".bak"), file(record.id)};

        /* A write still on its way would bring a deleted file back */
        for (File file : files)
        {
            RecapWriter.await(file);
        }

        for (int i = 0; i < files.length - 1; i++)
        {
            files[i].delete();
        }

        return file(record.id).delete();
    }

    /** Writes the camera of a saved recap next to its file, from a copy taken now. */
    public static boolean saveTrack(RecapRecord record, RecapTrack track)
    {
        if (record.id.isEmpty() || track == null)
        {
            return false;
        }

        RecapTrack copy = RecapTrack.tail(track, track.size());

        RecapWriter.write(trackFile(record.id), copy::write, null);

        return true;
    }

    /** The camera of a recap, or null when it has none: a recap made before the camera was kept. */
    public static RecapTrack loadTrack(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapTrack.read(waited(trackFile(record.id)));
    }

    /** Writes how the recorded player moved, next to the recap, from a copy taken now. */
    public static boolean saveMotion(RecapRecord record, RecapMotion motion)
    {
        if (record.id.isEmpty() || motion == null)
        {
            return false;
        }

        RecapMotion copy = RecapMotion.tail(motion, motion.size());

        RecapWriter.write(side(record.id, ".motion"), copy::write, null);

        return true;
    }

    public static RecapMotion loadMotion(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapMotion.read(waited(side(record.id, ".motion")));
    }

    /** Writes what happened to the world's blocks during the recap, next to it, from a copy taken now. */
    public static boolean saveEvents(RecapRecord record, RecapEvents events)
    {
        if (record.id.isEmpty() || events == null)
        {
            return false;
        }

        RecapEvents copy = RecapEvents.copy(events);

        RecapWriter.write(side(record.id, ".world"), copy::write, null);

        return true;
    }

    public static RecapEvents loadEvents(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapEvents.read(waited(side(record.id, ".world")));
    }

    /** Writes the time of day and the weather of every tick, next to the recap, from a copy taken now. */
    public static boolean saveEnvironment(RecapRecord record, RecapEnvironment environment)
    {
        if (record.id.isEmpty() || environment == null)
        {
            return false;
        }

        RecapEnvironment copy = RecapEnvironment.tail(environment, environment.size());

        RecapWriter.write(side(record.id, ".env"), copy::write, null);

        return true;
    }

    public static RecapEnvironment loadEnvironment(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapEnvironment.read(waited(side(record.id, ".env")));
    }

    /** What the player had on it and its interface's numbers, from a copy taken now. */
    public static boolean saveGear(RecapRecord record, RecapGear gear)
    {
        if (record.id.isEmpty() || gear == null)
        {
            return false;
        }

        RecapGear copy = RecapGear.tail(gear, gear.size());

        RecapWriter.write(side(record.id, ".gear"), copy::write, null);

        return true;
    }

    public static RecapGear loadGear(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapGear.read(waited(side(record.id, ".gear")));
    }

    /** The other entities around, from a copy taken now. */
    public static boolean saveEntities(RecapRecord record, RecapEntities entities)
    {
        if (record.id.isEmpty() || entities == null)
        {
            return false;
        }

        RecapEntities copy = RecapEntities.tail(entities, 0);

        RecapWriter.write(side(record.id, ".mobs"), copy::write, null);

        return true;
    }

    public static RecapEntities loadEntities(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapEntities.read(waited(side(record.id, ".mobs")));
    }

    /** The world's sounds, from a copy taken now. */
    public static boolean saveSounds(RecapRecord record, RecapSounds sounds)
    {
        if (record.id.isEmpty() || sounds == null)
        {
            return false;
        }

        RecapSounds copy = RecapSounds.tail(sounds, 0);

        RecapWriter.write(side(record.id, ".sounds"), copy::write, null);

        return true;
    }

    public static RecapSounds loadSounds(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapSounds.read(waited(side(record.id, ".sounds")));
    }

    /** The world's particles, from a copy taken now. */
    public static boolean saveParticles(RecapRecord record, RecapParticles particles)
    {
        if (record.id.isEmpty() || particles == null)
        {
            return false;
        }

        RecapParticles copy = RecapParticles.tail(particles, 0);

        RecapWriter.write(side(record.id, ".particles"), copy::write, null);

        return true;
    }

    public static RecapParticles loadParticles(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapParticles.read(waited(side(record.id, ".particles")));
    }

    /** Recorded first-person HUD stream, from a copy taken now. */
    public static boolean saveHud(RecapRecord record, RecapHudStream hud)
    {
        if (record.id.isEmpty() || hud == null || hud.isEmpty())
        {
            return false;
        }

        RecapHudStream copy = RecapHudStream.tail(hud, 0);

        RecapWriter.write(side(record.id, ".hud"), copy::write, null);

        return true;
    }

    public static RecapHudStream loadHud(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapHudStream.read(waited(side(record.id, ".hud")));
    }

    /** Writes the recorded point of view, beside the other files. */
    public static void savePov(RecapRecord record, RecapPov pov)
    {
        if (record != null && !record.id.isEmpty() && pov != null && !pov.isEmpty())
        {
            RecapWriter.write(side(record.id, ".pov"), pov::write, null);
        }
    }

    public static RecapPov loadPov(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapPov.read(waited(side(record.id, ".pov")));
    }

    /** Recorded local section baseline for cross-dimension stages. */
    public static boolean saveBaseline(RecapRecord record, RecapBaseline baseline)
    {
        if (record.id.isEmpty() || baseline == null || baseline.isEmpty())
        {
            return false;
        }

        RecapBaseline copy = new RecapBaseline();

        copy.append(baseline);
        RecapWriter.write(side(record.id, ".base"), copy::write, null);

        return true;
    }

    public static RecapBaseline loadBaseline(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapBaseline.read(waited(side(record.id, ".base")));
    }

    /**
     * Writes an already-isolated {@link RecapData} instance directly on {@link RecapWriter} without
     * cloning its arrays on the game thread. Ownership of {@code data} transfers to the writer.
     */
    public static void writeAllDirect(RecapRecord record, RecapData data, Runnable afterDataWritten)
    {
        if (record == null || record.id.isEmpty() || data == null)
        {
            return;
        }

        if (data.track != null)
        {
            RecapWriter.write(trackFile(record.id), data.track::write, null);
        }

        if (data.motion != null)
        {
            RecapWriter.write(side(record.id, ".motion"), data.motion::write, null);
        }

        if (data.events != null)
        {
            RecapWriter.write(side(record.id, ".world"), data.events::write, null);
        }

        if (data.environment != null)
        {
            RecapWriter.write(side(record.id, ".env"), data.environment::write, null);
        }

        if (data.gear != null)
        {
            RecapWriter.write(side(record.id, ".gear"), data.gear::write, null);
        }

        if (data.entities != null)
        {
            RecapWriter.write(side(record.id, ".mobs"), data.entities::write, null);
        }

        if (data.sounds != null)
        {
            RecapWriter.write(side(record.id, ".sounds"), data.sounds::write, null);
        }

        if (data.particles != null)
        {
            RecapWriter.write(side(record.id, ".particles"), data.particles::write, null);
        }

        if (data.hud != null && !data.hud.isEmpty())
        {
            RecapWriter.write(side(record.id, ".hud"), data.hud::write, null);
        }

        if (data.baseline != null && !data.baseline.isEmpty())
        {
            RecapWriter.write(side(record.id, ".base"), data.baseline::write, null);
        }

        if (afterDataWritten != null)
        {
            RecapWriter.writeData(new File(folder(), record.id + ".done"), () ->
            {
                afterDataWritten.run();

                return true;
            });
        }
    }

    public static void consolidateSessionAsync(RecapRecord record, File sessionDir, int sliceCount)
    {
        consolidateSessionSyncOrAsync(record, sessionDir, sliceCount, true);
    }

    /**
     * Streams {@code slice_0000.dat .. slice_(N-1).dat} from {@code sessionDir} on the background
     * data thread, merges them into final sidecar files, and removes {@code sessionDir} once safe.
     */
    public static void consolidateSessionSyncOrAsync(RecapRecord record, File sessionDir, int sliceCount, boolean async)
    {
        if (record == null || record.id.isEmpty() || sessionDir == null || sliceCount <= 0)
        {
            return;
        }

        RecapData[] box = new RecapData[1];
        boolean[] allOk = {true};

        RecapWriter.writeData(new File(sessionDir, ".merge"), () ->
        {
            box[0] = mergeSlices(sessionDir, sliceCount);

            return box[0] != null;
        });

        queueMergedWrite(trackFile(record.id), () -> box[0] != null && box[0].track != null ? box[0].track::write : null, allOk);
        queueMergedWrite(side(record.id, ".motion"), () -> box[0] != null && box[0].motion != null ? box[0].motion::write : null, allOk);
        queueMergedWrite(side(record.id, ".world"), () -> box[0] != null && box[0].events != null ? box[0].events::write : null, allOk);
        queueMergedWrite(side(record.id, ".env"), () -> box[0] != null && box[0].environment != null ? box[0].environment::write : null, allOk);
        queueMergedWrite(side(record.id, ".gear"), () -> box[0] != null && box[0].gear != null ? box[0].gear::write : null, allOk);
        queueMergedWrite(side(record.id, ".mobs"), () -> box[0] != null && box[0].entities != null ? box[0].entities::write : null, allOk);
        queueMergedWrite(side(record.id, ".sounds"), () -> box[0] != null && box[0].sounds != null ? box[0].sounds::write : null, allOk);
        queueMergedWrite(side(record.id, ".particles"), () -> box[0] != null && box[0].particles != null ? box[0].particles::write : null, allOk);
        queueMergedWrite(side(record.id, ".hud"), () -> box[0] != null && box[0].hasHud() ? box[0].hud::write : null, allOk);
        queueMergedWrite(side(record.id, ".base"), () -> box[0] != null && box[0].hasBaseline() ? box[0].baseline::write : null, allOk);

        File doneKey = new File(sessionDir, ".cleanup");

        RecapWriter.writeData(doneKey, () ->
        {
            if (box[0] != null && allOk[0])
            {
                RecapSpool.deleteDirectory(sessionDir);
            }

            box[0] = null;

            return allOk[0];
        });

        if (!async)
        {
            RecapWriter.await(doneKey);
        }
    }

    private static void queueMergedWrite(File target, java.util.function.Supplier<RecapWriter.Job> supplier, boolean[] allOk)
    {
        RecapWriter.write(target, (into) ->
        {
            RecapWriter.Job job = supplier.get();

            if (job == null)
            {
                return false;
            }

            boolean ok = job.write(into);

            if (!ok)
            {
                allOk[0] = false;
            }

            return ok;
        }, null);
    }

    private static RecapData mergeSlices(File sessionDir, int sliceCount)
    {
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

        int tickOffset = 0;
        int loaded = 0;

        for (int i = 0; i < sliceCount; i++)
        {
            RecapSpool.Slice slice = RecapSpool.Slice.read(RecapSpool.Session.sliceFile(sessionDir, i));

            if (slice == null)
            {
                continue;
            }

            merged.track.append(slice.track);
            merged.motion.append(slice.motion);
            merged.events.append(slice.events, tickOffset);
            merged.environment.append(slice.environment);
            merged.gear.append(slice.gear);
            merged.entities.append(slice.entities, tickOffset);
            merged.sounds.append(slice.sounds, tickOffset);
            merged.particles.append(slice.particles, tickOffset);
            merged.hud.append(slice.hud, tickOffset);
            merged.baseline.append(slice.baseline);
            tickOffset += slice.ticks;
            loaded += 1;
        }

        return loaded > 0 ? merged : null;
    }

    private static File waited(File file)
    {
        RecapWriter.await(file);

        return file;
    }

    private static File side(String id, String extension)
    {
        return new File(folder(), id + extension);
    }

    private static File trackFile(String id)
    {
        return new File(folder(), id + ".track");
    }

    private static File file(String id)
    {
        return new File(folder(), id + EXTENSION);
    }
}
