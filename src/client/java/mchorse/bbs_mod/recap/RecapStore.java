package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The recaps on this computer, one file each in {@code <game>/bbs_cs/recaps}. */
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

    public static boolean save(RecapRecord record)
    {
        if (record.id.isEmpty())
        {
            do
            {
                record.id = UUID.randomUUID().toString().substring(0, 8);
            }
            while (file(record.id).exists());
        }

        return DataToString.writeSilently(file(record.id), record.toData(), true);
    }

    public static boolean delete(RecapRecord record)
    {
        if (record.id.isEmpty())
        {
            return false;
        }

        trackFile(record.id).delete();
        new File(folder(), record.id + ".motion").delete();
        new File(folder(), record.id + ".world").delete();

        return file(record.id).delete();
    }

    /** Writes the camera of a saved recap next to its file. */
    public static boolean saveTrack(RecapRecord record, RecapTrack track)
    {
        return !record.id.isEmpty() && track != null && track.write(trackFile(record.id));
    }

    /** The camera of a recap, or null when it has none: a recap made before the camera was kept. */
    public static RecapTrack loadTrack(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapTrack.read(trackFile(record.id));
    }

    /** Writes how the recorded player moved, next to the recap. */
    public static boolean saveMotion(RecapRecord record, RecapMotion motion)
    {
        return !record.id.isEmpty() && motion != null && motion.write(new File(folder(), record.id + ".motion"));
    }

    public static RecapMotion loadMotion(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapMotion.read(new File(folder(), record.id + ".motion"));
    }

    /** Writes what happened to the world's blocks during the recap, next to it. */
    public static boolean saveEvents(RecapRecord record, RecapEvents events)
    {
        return !record.id.isEmpty() && events != null && events.write(new File(folder(), record.id + ".world"));
    }

    public static RecapEvents loadEvents(RecapRecord record)
    {
        return record == null || record.id.isEmpty() ? null : RecapEvents.read(new File(folder(), record.id + ".world"));
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
