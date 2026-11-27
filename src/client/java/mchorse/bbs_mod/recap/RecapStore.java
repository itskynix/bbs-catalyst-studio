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

        RecapWriter.await(file);

        return file.isFile() ? load(file) : null;
    }

    private static RecapRecord load(File file)
    {
        try
        {
            BaseType data = DataToString.read(file);

            if (data != null && data.isMap())
            {
                String name = file.getName();

                return RecapRecord.fromData(name.substring(0, name.length() - EXTENSION.length()), data.asMap());
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

        File[] files = {trackFile(record.id), side(record.id, ".motion"), side(record.id, ".world"), side(record.id, ".env"), file(record.id)};

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
