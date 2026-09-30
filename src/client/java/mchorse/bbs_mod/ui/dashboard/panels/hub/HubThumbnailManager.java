package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HubThumbnailManager
{
    private static final Map<String, Texture> TEXTURES = new HashMap<>();
    private static final Set<String> DOWNLOADING = new HashSet<>();
    private static File thumbnailFolder;

    public static File getThumbnailFolder()
    {
        if (thumbnailFolder == null)
        {
            thumbnailFolder = new File(BBSMod.getAssetsFolder(), ".hub_thumbnails");

            if (!thumbnailFolder.exists())
            {
                thumbnailFolder.mkdirs();
            }
        }

        return thumbnailFolder;
    }

    public static Texture getThumbnail(HubAssetEntry entry)
    {
        if (entry == null || entry.id == null || entry.id.isEmpty())
        {
            return null;
        }

        Texture texture = TEXTURES.get(entry.id);

        if (texture != null)
        {
            return texture;
        }

        File file = new File(getThumbnailFolder(), entry.id + ".png");

        if (file.exists() && file.length() > 0L)
        {
            try (InputStream in = new FileInputStream(file))
            {
                Link link = new Link("hub_thumb", entry.id + ".png");
                Texture tex = BBSModClient.getTextures().createTexture(link);

                tex.bind();
                tex.uploadTexture(Pixels.fromPNGStream(in));
                TEXTURES.put(entry.id, tex);

                return tex;
            }
            catch (Exception e)
            {
                /* File might be corrupt; re-fetch */
                file.delete();
            }
        }

        if (entry.thumbnailUrl != null && (entry.thumbnailUrl.startsWith("http://") || entry.thumbnailUrl.startsWith("https://")))
        {
            synchronized (DOWNLOADING)
            {
                if (!DOWNLOADING.contains(entry.id))
                {
                    DOWNLOADING.add(entry.id);

                    Thread thread = new Thread(() ->
                    {
                        try
                        {
                            URL url = new URL(entry.thumbnailUrl);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                            conn.setRequestProperty("User-Agent", "BBSMod-CommunityHub/1.0");
                            conn.setConnectTimeout(6000);
                            conn.setReadTimeout(12000);

                            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK)
                            {
                                try (InputStream in = conn.getInputStream();
                                     FileOutputStream out = new FileOutputStream(file))
                                {
                                    in.transferTo(out);
                                }

                                MinecraftClient.getInstance().execute(() ->
                                {
                                    try (InputStream in = new FileInputStream(file))
                                    {
                                        Link link = new Link("hub_thumb", entry.id + ".png");
                                        Texture tex = BBSModClient.getTextures().createTexture(link);

                                        tex.bind();
                                        tex.uploadTexture(Pixels.fromPNGStream(in));
                                        TEXTURES.put(entry.id, tex);
                                    }
                                    catch (Exception e)
                                    {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                        catch (Exception ex)
                        {
                            /* Thumbnail fetch failed, fallback icon will show */
                        }
                        finally
                        {
                            synchronized (DOWNLOADING)
                            {
                                DOWNLOADING.remove(entry.id);
                            }
                        }
                    }, "HubThumbnailDownload-" + entry.id);

                    thread.setDaemon(true);
                    thread.start();
                }
            }
        }

        return null;
    }

    public static void clearCache()
    {
        TEXTURES.clear();
    }
}
