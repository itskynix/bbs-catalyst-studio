package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.util.Locale;

/**
 * Data model for a media asset managed within the Catalyst Media Pool.
 * Holds file reference, media type, size, duration, and display properties.
 */
public class CatalystMediaAsset
{
    public enum MediaType
    {
        VIDEO, AUDIO, IMAGE;

        public static MediaType fromPath(String path)
        {
            if (path == null)
            {
                return IMAGE;
            }

            String lower = path.toLowerCase(Locale.ROOT);

            if (lower.endsWith(".mp4") || lower.endsWith(".mov"))
            {
                return VIDEO;
            }
            if (lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg"))
            {
                return AUDIO;
            }

            return IMAGE;
        }

        public static boolean isSupported(String path)
        {
            if (path == null)
            {
                return false;
            }

            String lower = path.toLowerCase(Locale.ROOT);

            return lower.endsWith(".mp4") || lower.endsWith(".mov")
                || lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".ogg")
                || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        }
    }

    public String id = "";
    public String name = "";
    public String path = "";
    public MediaType type = MediaType.IMAGE;
    public long size = 0L;
    public int durationFrames = 150;
    public int width = 0;
    public int height = 0;

    public CatalystMediaAsset()
    {
    }

    public CatalystMediaAsset(File file, int defaultFps)
    {
        this.name = file.getName();
        this.path = file.getAbsolutePath().replace('\\', '/');
        this.id = this.path;
        this.type = MediaType.fromPath(file.getName());
        this.size = file.length();
        this.durationFrames = defaultFps > 0 ? defaultFps * 5 : 150;
    }

    public CatalystMediaAsset(String path, int defaultFps)
    {
        this.path = path != null ? new File(path).getAbsolutePath().replace('\\', '/') : "";
        this.id = this.path;
        File file = new File(this.path);
        this.name = file.getName();
        this.type = MediaType.fromPath(this.path);
        if (file.exists())
        {
            this.size = file.length();
        }
        this.durationFrames = defaultFps > 0 ? defaultFps * 5 : 150;
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("id", this.id);
        data.putString("name", this.name);
        data.putString("path", this.path);
        data.putString("type", this.type.name());
        data.putLong("size", this.size);
        data.putInt("durationFrames", this.durationFrames);
        data.putInt("width", this.width);
        data.putInt("height", this.height);

        return data;
    }

    public void fromData(MapType data)
    {
        if (data == null)
        {
            return;
        }

        if (data.has("id")) this.id = data.getString("id");
        if (data.has("name")) this.name = data.getString("name");
        if (data.has("path")) this.path = data.getString("path").replace('\\', '/');
        if (data.has("type"))
        {
            try
            {
                this.type = MediaType.valueOf(data.getString("type"));
            }
            catch (Exception ignored)
            {
                this.type = MediaType.fromPath(this.path);
            }
        }
        if (data.has("size")) this.size = data.getLong("size");
        if (data.has("durationFrames")) this.durationFrames = data.getInt("durationFrames");
        if (data.has("width")) this.width = data.getInt("width");
        if (data.has("height")) this.height = data.getInt("height");
    }

    public String formatSize()
    {
        if (this.size <= 0)
        {
            return "";
        }
        if (this.size < 1024)
        {
            return this.size + " B";
        }
        if (this.size < 1024 * 1024)
        {
            return String.format(Locale.ROOT, "%.1f KB", this.size / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", this.size / (1024.0 * 1024.0));
    }

    public String formatDuration(int fps)
    {
        if (this.type == MediaType.IMAGE)
        {
            return "Image";
        }

        int curFps = fps > 0 ? fps : 60;
        double sec = (double) this.durationFrames / curFps;
        return String.format(Locale.ROOT, "%d fr (%.1fs)", this.durationFrames, sec);
    }
}
