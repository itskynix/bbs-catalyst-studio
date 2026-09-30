package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class HubAssetDownloader
{
    public static final String DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/mchorse/bbs-hub-assets/main/manifest.json";

    private static final String TRACKER_FILENAME = ".installed_hub_assets.json";
    private static final String MANIFEST_CACHE_FILENAME = ".hub_manifest_cache.json";

    private static String manifestUrl = DEFAULT_MANIFEST_URL;

    public static class DownloadStatus
    {
        public final float progress;
        public final String message;
        public final boolean done;
        public final boolean success;

        public DownloadStatus(float progress, String message, boolean done, boolean success)
        {
            this.progress = progress;
            this.message = message;
            this.done = done;
            this.success = success;
        }
    }

    public static String getManifestUrl()
    {
        return manifestUrl;
    }

    public static void setManifestUrl(String url)
    {
        if (url != null && !url.trim().isEmpty())
        {
            manifestUrl = url.trim();
        }
    }

    public static synchronized boolean isAssetTrackedInstalled(String id)
    {
        if (id == null || id.isEmpty())
        {
            return false;
        }

        MapType map = loadTrackerMap();

        return map != null && map.has(id);
    }

    public static synchronized String getInstalledVersion(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        MapType map = loadTrackerMap();

        return map != null ? map.getString(id, null) : null;
    }

    public static synchronized void markInstalled(String id, String version)
    {
        if (id == null || id.isEmpty())
        {
            return;
        }

        MapType map = loadTrackerMap();

        if (map == null)
        {
            map = new MapType();
        }

        map.putString(id, version == null ? "1.0.0" : version);
        saveTrackerMap(map);
    }

    public static synchronized void markUninstalled(String id)
    {
        if (id == null || id.isEmpty())
        {
            return;
        }

        MapType map = loadTrackerMap();

        if (map != null && map.has(id))
        {
            map.remove(id);
            saveTrackerMap(map);
        }
    }

    public static synchronized Set<String> getInstalledIds()
    {
        Set<String> set = new HashSet<>();
        MapType map = loadTrackerMap();

        if (map != null)
        {
            for (Map.Entry<String, BaseType> entry : map)
            {
                set.add(entry.getKey());
            }
        }

        return set;
    }

    private static MapType loadTrackerMap()
    {
        File file = new File(BBSMod.getAssetsFolder(), TRACKER_FILENAME);

        if (!file.exists())
        {
            return new MapType();
        }

        try
        {
            return (MapType) DataToString.read(file);
        }
        catch (Exception e)
        {
            return new MapType();
        }
    }

    private static void saveTrackerMap(MapType map)
    {
        File file = new File(BBSMod.getAssetsFolder(), TRACKER_FILENAME);

        try
        {
            DataToString.write(file, map, true);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void fetchCatalog(Consumer<List<HubAssetEntry>> callback)
    {
        Thread thread = new Thread(() ->
        {
            List<HubAssetEntry> entries = new ArrayList<>();
            boolean onlineSuccess = false;

            /* Try fetching remote manifest */
            try
            {
                URL url = new URL(manifestUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestProperty("User-Agent", "BBSMod-CommunityHub/1.0");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
                {
                    try (InputStream stream = conn.getInputStream())
                    {
                        String json = IOUtils.readText(stream);

                        if (json != null && !json.trim().isEmpty())
                        {
                            ListType listData = DataToString.listFromString(json);

                            if (listData != null)
                            {
                                for (BaseType base : listData)
                                {
                                    if (base.isMap())
                                    {
                                        entries.add(HubAssetEntry.fromData((MapType) base));
                                    }
                                }

                                /* Cache successful manifest on disk */
                                File cacheFile = new File(BBSMod.getAssetsFolder(), MANIFEST_CACHE_FILENAME);
                                IOUtils.writeText(cacheFile, json);
                                onlineSuccess = true;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                /* Remote manifest fetch failed, fall through to cache/bundled catalog */
            }

            /* If remote fetch failed, try cached manifest */
            if (!onlineSuccess)
            {
                File cacheFile = new File(BBSMod.getAssetsFolder(), MANIFEST_CACHE_FILENAME);

                if (cacheFile.exists() && cacheFile.length() > 0L)
                {
                    try
                    {
                        String cachedJson = IOUtils.readText(cacheFile);
                        ListType listData = DataToString.listFromString(cachedJson);

                        if (listData != null)
                        {
                            for (BaseType base : listData)
                            {
                                if (base.isMap())
                                {
                                    entries.add(HubAssetEntry.fromData((MapType) base));
                                }
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        /* Cache corrupted */
                    }
                }
            }

            /* Fallback to bundled fallback resource if still empty */
            if (entries.isEmpty())
            {
                try (InputStream stream = HubAssetDownloader.class.getResourceAsStream("/assets/bbs/hub/community_assets_catalog.json"))
                {
                    if (stream != null)
                    {
                        String fallbackJson = IOUtils.readText(stream);
                        ListType listData = DataToString.listFromString(fallbackJson);

                        if (listData != null)
                        {
                            for (BaseType base : listData)
                            {
                                if (base.isMap())
                                {
                                    entries.add(HubAssetEntry.fromData((MapType) base));
                                }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }

            MinecraftClient.getInstance().execute(() -> callback.accept(entries));
        }, "BBSHub-FetchCatalog");

        thread.setDaemon(true);
        thread.start();
    }

    public static void downloadAndInstall(HubAssetEntry entry, Consumer<DownloadStatus> listener)
    {
        if (entry.downloadUrl == null || (!entry.downloadUrl.startsWith("http://") && !entry.downloadUrl.startsWith("https://")))
        {
            notify(listener, 0.0F, "Error: Invalid or missing download URL!", true, false);

            return;
        }

        Thread thread = new Thread(() ->
        {
            File tmpFile = null;

            try
            {
                notify(listener, 0.05F, "Connecting to download server...", false, true);

                URL url = new URL(entry.downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestProperty("User-Agent", "BBSMod-CommunityHub/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                int responseCode = conn.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK)
                {
                    throw new IOException("Server responded with HTTP " + responseCode + ": " + conn.getResponseMessage());
                }

                int contentLength = conn.getContentLength();
                tmpFile = new File(BBSMod.getAssetsFolder(), ".dl_" + entry.id + "_" + System.currentTimeMillis() + ".tmp");

                if (tmpFile.getParentFile() != null && !tmpFile.getParentFile().exists())
                {
                    tmpFile.getParentFile().mkdirs();
                }

                /* Download into temporary file with progress reporting */
                try (InputStream in = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tmpFile))
                {
                    byte[] buffer = new byte[8192];
                    int len;
                    long totalRead = 0L;

                    while ((len = in.read(buffer)) > 0)
                    {
                        fos.write(buffer, 0, len);
                        totalRead += len;

                        if (contentLength > 0)
                        {
                            float p = 0.05F + 0.65F * ((float) totalRead / (float) contentLength);
                            String mbRead = String.format("%.1f", totalRead / (1024F * 1024F));
                            String mbTotal = String.format("%.1f", contentLength / (1024F * 1024F));

                            notify(listener, Math.min(0.70F, p), "Downloading: " + mbRead + " / " + mbTotal + " MB (" + (int) (p * 100F) + "%)", false, true);
                        }
                    }
                }

                /* Extracting archive with ZipSlip path traversal protection */
                notify(listener, 0.75F, "Extracting package files...", false, true);

                File targetDir = BBSMod.getAssetsFolder();
                String canonicalTargetDir = targetDir.getCanonicalPath();

                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tmpFile)))
                {
                    byte[] buffer = new byte[8192];
                    ZipEntry ze;

                    while ((ze = zis.getNextEntry()) != null)
                    {
                        String entryName = ze.getName();
                        File destFile = new File(targetDir, entryName);

                        String canonicalDestFile = destFile.getCanonicalPath();

                        /* Strict ZipSlip protection */
                        if (!canonicalDestFile.startsWith(canonicalTargetDir + File.separator) && !canonicalDestFile.equals(canonicalTargetDir))
                        {
                            throw new SecurityException("Blocked ZipSlip exploit entry: " + entryName);
                        }

                        if (ze.isDirectory())
                        {
                            destFile.mkdirs();
                        }
                        else
                        {
                            destFile.getParentFile().mkdirs();

                            try (FileOutputStream fos = new FileOutputStream(destFile))
                            {
                                int l;

                                while ((l = zis.read(buffer)) > 0)
                                {
                                    fos.write(buffer, 0, l);
                                }
                            }
                        }

                        zis.closeEntry();
                    }
                }

                /* Delete temporary archive */
                if (tmpFile.exists())
                {
                    tmpFile.delete();
                }

                markInstalled(entry.id, entry.version);
                notify(listener, 0.90F, "Hot-reloading studio assets...", false, true);

                triggerHotReload();

                notify(listener, 1.0F, "Installed successfully!", true, true);
            }
            catch (Throwable t)
            {
                t.printStackTrace();

                if (tmpFile != null && tmpFile.exists())
                {
                    tmpFile.delete();
                }

                notify(listener, 0.0F, "Error: " + t.getMessage(), true, false);
            }
        }, "BBSHub-Download-" + entry.id);

        thread.setDaemon(true);
        thread.start();
    }

    public static void uninstall(HubAssetEntry entry, Consumer<DownloadStatus> listener)
    {
        Thread thread = new Thread(() ->
        {
            try
            {
                notify(listener, 0.20F, "Removing asset files...", false, true);

                File assetsDir = BBSMod.getAssetsFolder();

                for (String relPath : entry.files)
                {
                    File f = new File(assetsDir, relPath);

                    if (f.exists())
                    {
                        f.delete();

                        File parent = f.getParentFile();

                        if (parent != null && !parent.equals(assetsDir))
                        {
                            String[] children = parent.list();

                            if (children != null && children.length == 0)
                            {
                                parent.delete();
                            }
                        }
                    }
                }

                markUninstalled(entry.id);
                notify(listener, 0.70F, "Hot-reloading studio assets...", false, true);

                triggerHotReload();

                notify(listener, 1.0F, "Uninstalled successfully!", true, true);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                notify(listener, 0.0F, "Error: " + t.getMessage(), true, false);
            }
        }, "BBSHub-Uninstall-" + entry.id);

        thread.setDaemon(true);
        thread.start();
    }

    public static void triggerHotReload()
    {
        MinecraftClient.getInstance().execute(() ->
        {
            BBSModClient.getTextures().delete();
            BBSModClient.getSounds().deleteSounds();
            BBSModClient.getModels().reload();
        });
    }

    private static void notify(Consumer<DownloadStatus> listener, float progress, String msg, boolean done, boolean success)
    {
        if (listener != null)
        {
            MinecraftClient.getInstance().execute(() -> listener.accept(new DownloadStatus(progress, msg, done, success)));
        }
    }
}
