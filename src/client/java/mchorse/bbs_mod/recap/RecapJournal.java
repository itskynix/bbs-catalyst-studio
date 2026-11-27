package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup inspection and crash recovery for interrupted Recap sessions in {@code bbs_cs/recaps/.active/}.
 *
 * <p>Sessions belonging to a live OS process ({@link RecapSpool#isOtherProcessAlive}) are never
 * touched. Sessions marked {@code clean_stop} (fast game exit) are silently completed into
 * {@link RecapStore}; sessions interrupted by a crash or power loss are surfaced to
 * {@code UIRecapHome} so the author can recover or discard them with one click.</p>
 */
public final class RecapJournal
{
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm");
    private static boolean startupCleaned;

    public record Recoverable(String id, String worldName, int ticks, int slices, long created, File dir, MapType journal)
    {}

    private RecapJournal()
    {}

    /** Scans {@code .active/}, auto-completes clean-exit sessions, and returns crashed sessions ready for recovery. */
    public static List<Recoverable> scan()
    {
        if (!startupCleaned)
        {
            startupCleaned = true;
            sweepStaleArtifacts();
        }

        List<Recoverable> crashed = new ArrayList<>();
        File root = RecapSpool.activeRoot();
        File[] dirs = root.isDirectory() ? root.listFiles(File::isDirectory) : null;

        if (dirs == null)
        {
            return crashed;
        }

        for (File dir : dirs)
        {
            String activeId = RecapRecorder.activeSessionId();

            if (activeId != null && activeId.equals(dir.getName()))
            {
                continue;
            }

            File journalFile = new File(dir, "journal.json");
            MapType journal = RecapSpool.readMapWithBackup(journalFile);

            if (journal == null)
            {
                /* Empty directory with no journal and no live recorder */
                File[] anySlices = dir.listFiles((d, n) -> n.endsWith(".dat"));

                if (anySlices == null || anySlices.length == 0)
                {
                    RecapSpool.deleteDirectory(dir);
                }

                continue;
            }

            long pid = journal.getLong("pid", -1L);

            if (RecapSpool.isOtherProcessAlive(pid))
            {
                /* Another game window is actively recording into this folder */
                continue;
            }

            int slices = countValidSlices(dir, journal.getInt("slices", 0));

            if (slices <= 0)
            {
                RecapSpool.deleteDirectory(dir);

                continue;
            }

            boolean cleanStop = journal.getBool("clean_stop", false);

            if (cleanStop)
            {
                recoverInternal(dir, journal, slices);
            }
            else
            {
                String id = journal.getString("id", dir.getName());
                String worldName = journal.getString("world_name", "World");
                int ticks = Math.max(1, journal.getInt("ticks", slices * RecapSpool.SLICE_TICKS));
                long created = journal.getLong("created", dir.lastModified());

                crashed.add(new Recoverable(id, worldName, ticks, slices, created, dir, journal));
            }
        }

        return crashed;
    }

    /** Recovers a crashed session into {@link RecapStore} and returns the restored {@link RecapRecord}. */
    public static RecapRecord recover(Recoverable item)
    {
        if (item == null || !item.dir.isDirectory())
        {
            return null;
        }

        int slices = countValidSlices(item.dir, item.slices);

        return slices <= 0 ? null : recoverInternal(item.dir, item.journal, slices);
    }

    /** Permanently deletes an unwanted crashed session directory. */
    public static void discard(Recoverable item)
    {
        if (item != null && item.dir != null)
        {
            RecapSpool.deleteDirectory(item.dir);
        }
    }

    private static int countValidSlices(File dir, int declared)
    {
        int valid = 0;
        int max = Math.max(declared, 1024);

        for (int i = 0; i < max; i++)
        {
            File slice = RecapSpool.Session.sliceFile(dir, i);

            if (slice.isFile() && slice.length() > 16L)
            {
                valid = i + 1;
            }
            else
            {
                break;
            }
        }

        return valid;
    }

    private static RecapRecord recoverInternal(File dir, MapType journal, int sliceCount)
    {
        try
        {
            RecapRecord existing = RecapStore.get(journal.getString("id", ""));
            RecapRecord record = existing != null ? existing : new RecapRecord();

            if (existing == null)
            {
                String candidate = journal.getString("id", "");

                if (!candidate.isEmpty() && RecapStore.get(candidate) == null)
                {
                    record.id = candidate;
                }

                long created = journal.getLong("created", System.currentTimeMillis());
                String worldName = journal.getString("world_name", "World");
                LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(created), ZoneId.systemDefault());

                record.name = worldName + " " + dt.format(NAME_DATE);
                record.worldKey = journal.getString("world_key", "");
                record.worldName = worldName;
                record.worldTarget = journal.getString("world_target", "");
                record.singleplayer = journal.getBool("singleplayer", true);
                record.created = created;
                record.instant = false;
                record.ticks = Math.max(1, journal.getInt("ticks", sliceCount * RecapSpool.SLICE_TICKS));
                record.dimensions.copy(RecapDimensions.fromData(journal.getMap("dimensions")));

                for (BaseType m : journal.getList("markers"))
                {
                    if (BaseType.isNumeric(m))
                    {
                        record.addMarker(m.asNumeric().intValue());
                    }
                }

                if (!RecapStore.save(record))
                {
                    return null;
                }
            }

            RecapStore.consolidateSessionSyncOrAsync(record, dir, sliceCount, true);

            return record;
        }
        catch (Throwable e)
        {
            BBSMod.LOGGER.error("Recap session could not be recovered from " + dir.getName(), e);

            return null;
        }
    }

    /** Cleans up orphaned temporary files from hard crashes without touching active files. */
    private static void sweepStaleArtifacts()
    {
        try
        {
            File recaps = RecapStore.folder();
            File[] tmps = recaps.listFiles((d, name) -> name.endsWith(".tmp"));
            long cutoff = System.currentTimeMillis() - 60_000L;

            if (tmps != null)
            {
                for (File tmp : tmps)
                {
                    if (tmp.lastModified() < cutoff)
                    {
                        tmp.delete();
                    }
                }
            }

            File videos = BBSRendering.getVideoFolder();
            File[] wavs = videos.isDirectory() ? videos.listFiles((d, name) -> name.endsWith(".wav")) : null;
            long wavCutoff = System.currentTimeMillis() - 3_600_000L;

            if (wavs != null)
            {
                for (File wav : wavs)
                {
                    /* Timestamped temp audio files created for muxing that were left behind by a crash */
                    if (wav.lastModified() < wavCutoff && wav.getName().matches("\\d{4}-\\d{2}-\\d{2}_.*\\.wav"))
                    {
                        wav.delete();
                    }
                }
            }
        }
        catch (Throwable ignored)
        {}
    }
}
