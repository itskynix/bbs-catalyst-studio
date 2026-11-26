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
        return !record.id.isEmpty() && file(record.id).delete();
    }

    private static File file(String id)
    {
        return new File(folder(), id + EXTENSION);
    }
}
