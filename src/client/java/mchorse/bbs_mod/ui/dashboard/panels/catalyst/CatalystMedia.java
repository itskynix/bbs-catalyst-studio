package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.resources.Link;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a piece of media is offline: its file is not where the project says it is. Asked for every
 * clip and pool entry on every frame, so the answer for a path is kept for a couple of seconds
 * instead of going to the disk each time.
 */
public class CatalystMedia
{
    private static final long KEEP_MS = 2000L;

    private static final Map<String, long[]> CACHE = new HashMap<>();

    public static boolean isOffline(String path)
    {
        if (path == null || path.trim().isEmpty())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        long[] entry = CACHE.get(path);

        if (entry == null || now - entry[0] > KEEP_MS)
        {
            entry = new long[] {now, CatalystProject.isMissing(path) ? 1L : 0L};
            CACHE.put(path, entry);
        }

        return entry[1] != 0L;
    }

    public static boolean isOffline(CatalystLayer layer)
    {
        return CatalystProject.usesFile(layer) && isOffline(layer.resourcePath);
    }

    /** Forgets what is known, after a relink: the next question goes to the disk */
    public static void refresh()
    {
        CACHE.clear();
        WAVEFORMS.clear();
    }

    /* ── Waveforms, one per file ── */

    /** What a file that could not be read leaves in the cache, so it is not read again every frame */
    private static final Object UNREADABLE = new Object();
    private static final Map<String, Object> WAVEFORMS = new ConcurrentHashMap<>();
    private static final Set<String> WAVEFORMS_LOADING = ConcurrentHashMap.newKeySet();

    /**
     * The waveform of the file at this path, shared by every clip cut from it: a cut makes a new
     * clip, and the new clip draws its own slice of what is already here instead of reading the
     * file again. Null while it is being made in the background (this starts that) or when the
     * file has none.
     */
    public static Waveform waveform(String path)
    {
        if (path == null || path.trim().isEmpty())
        {
            return null;
        }

        String key = path.trim();
        Object known = WAVEFORMS.get(key);

        if (known instanceof Waveform waveform)
        {
            return waveform;
        }

        if (known == null && WAVEFORMS_LOADING.add(key))
        {
            Thread loader = new Thread(() -> loadWaveform(key), "CatalystWaveformLoader");

            loader.setDaemon(true);
            loader.start();
        }

        return null;
    }

    /** Whether the waveform of this path is still being made */
    public static boolean isWaveformLoading(String path)
    {
        return path != null && WAVEFORMS_LOADING.contains(path.trim());
    }

    private static void loadWaveform(String path)
    {
        Object result = UNREADABLE;

        try
        {
            File file = new File(path);
            Wave wave = file.isFile() ? AudioReader.readWave(file) : null;

            if (wave == null)
            {
                wave = AudioReader.read(BBSMod.getProvider(), Link.create(path));
            }

            if (wave != null)
            {
                Waveform waveform = new Waveform();

                waveform.generate(wave, null, BBSSettings.audioWaveformDensity.get(), 40);
                result = waveform;
            }
        }
        catch (Throwable ignored)
        {
            /* A file with no readable sound: it simply has no waveform */
        }
        finally
        {
            WAVEFORMS.put(path, result);
            WAVEFORMS_LOADING.remove(path);
        }
    }
}
