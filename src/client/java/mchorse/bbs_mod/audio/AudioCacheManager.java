package mchorse.bbs_mod.audio;

import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages temporary cache files for audio preview.
 * Cache files are stored in config/bbs/settings/audio_cache/ directory.
 */
public class AudioCacheManager
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_CACHE_FILES = 30;
    
    private static AudioCacheManager INSTANCE;
    
    private final File cacheDir;
    private final Map<String, File> cachedFiles;
    
    /**
     * Private constructor for singleton pattern
     */
    private AudioCacheManager()
    {
        this.cacheDir = BBSMod.getAudioCacheFolder();
        /* Access-ordered: iteration starts at the least recently used entry */
        this.cachedFiles = new LinkedHashMap<>(16, 0.75F, true);
        
        if (!this.cacheDir.exists() && !this.cacheDir.mkdirs())
        {
            LOGGER.error("Failed to create audio cache directory: " + this.cacheDir.getAbsolutePath());
        }
    }
    
    /**
     * Get singleton instance
     */
    public static AudioCacheManager getInstance()
    {
        if (INSTANCE == null)
        {
            INSTANCE = new AudioCacheManager();
        }
        
        return INSTANCE;
    }
    
    /**
     * Create temporary cache file for the specified audio path.
     * Evicts the least recently used files if the limit is exceeded.
     */
    public File createTempCacheFile(String soundPath) throws IOException
    {
        if (this.cachedFiles.size() >= MAX_CACHE_FILES)
        {
            this.cleanupExcessCache();
        }
        
        String flattenedPath = soundPath.replace("/", "_").replace("\\", "_");
        String fileName = flattenedPath.endsWith(".ogg")
            ? "preview_" + flattenedPath
            : "preview_" + flattenedPath + ".ogg";
        
        File cacheFile = new File(this.cacheDir, fileName);
        
        if (cacheFile.exists())
        {
            if (!cacheFile.delete())
            {
                LOGGER.warn("Failed to delete existing cache file: " + cacheFile.getAbsolutePath());
            }
        }
        
        this.cachedFiles.put(soundPath, cacheFile);

        return cacheFile;
    }
    
    /**
     * Get cached file for the specified audio path.
     */
    public File getCachedFile(String soundPath)
    {
        File cachedFile = this.cachedFiles.get(soundPath);
        
        if (cachedFile != null && cachedFile.exists())
        {
            return cachedFile;
        }
        else if (cachedFile != null)
        {
            this.cachedFiles.remove(soundPath);
        }
        
        return null;
    }
    
    /**
     * Clear all cache files.
     * Called when overlay panel opens/closes.
     */
    public void clearAllCache()
    {
        int deletedCount = 0;
        int failedCount = 0;
        
        if (this.cacheDir.exists() && this.cacheDir.isDirectory())
        {
            File[] files = this.cacheDir.listFiles();
            
            if (files != null)
            {
                for (File file : files)
                {
                    if (file.isFile())
                    {
                        if (file.delete())
                        {
                            deletedCount++;
                        }
                        else
                        {
                            failedCount++;
                            LOGGER.warn("Failed to delete cache file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        
        this.cachedFiles.clear();
        
        if (deletedCount > 0 || failedCount > 0)
        {
            LOGGER.info("Cleared audio cache - deleted: {}, failed: {}", deletedCount, failedCount);
        }
    }
    
    /**
     * Cleanup excess cache when limit is reached.
     * Evicts only the least recently used files, down to 3/4 of the limit, so the sounds
     * still in use stay cached.
     */
    private void cleanupExcessCache()
    {
        int target = MAX_CACHE_FILES * 3 / 4;
        Iterator<Map.Entry<String, File>> iterator = this.cachedFiles.entrySet().iterator();

        while (this.cachedFiles.size() > target && iterator.hasNext())
        {
            File file = iterator.next().getValue();

            iterator.remove();

            if (file.exists() && !file.delete())
            {
                LOGGER.warn("Failed to delete cache file: " + file.getAbsolutePath());
            }
        }
    }

    
    /**
     * Clean up invalid cache entries.
     * Used for sanity check on startup.
     */
    public void cleanupInvalidCache()
    {
        var iterator = this.cachedFiles.entrySet().iterator();
        
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            File file = entry.getValue();
            
            if (!file.exists())
            {
                iterator.remove();
            }
        }
    }
}