package mchorse.bbs_mod.ui.dashboard.panels.hub;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;

import java.util.ArrayList;
import java.util.List;

public class HubAssetEntry
{
    public String id = "";
    public String name = "";
    public String author = "";
    public HubCategory category = HubCategory.ALL;
    public String description = "";
    public String version = "1.0.0";
    public String downloadUrl = "";
    public String thumbnailUrl = "";
    public long downloads = 0L;
    public long likes = 0L;
    public long sizeBytes = 0L;
    public List<String> files = new ArrayList<>();
    public List<String> tags = new ArrayList<>();

    public HubAssetEntry()
    {
    }

    public static HubAssetEntry fromData(MapType data)
    {
        HubAssetEntry entry = new HubAssetEntry();

        if (data == null)
        {
            return entry;
        }

        entry.id = data.getString("id");
        entry.name = data.getString("name");
        entry.author = data.getString("author");
        entry.category = HubCategory.fromId(data.getString("category"));
        entry.description = data.getString("description");
        entry.version = data.getString("version", "1.0.0");
        entry.downloadUrl = data.getString("download_url");
        entry.thumbnailUrl = data.getString("thumbnail_url");
        entry.downloads = data.getLong("downloads", 0L);
        entry.likes = data.getLong("likes", 0L);
        entry.sizeBytes = data.getLong("size_bytes", 0L);

        ListType filesList = data.getList("files");

        for (BaseType base : filesList)
        {
            if (base.isString())
            {
                entry.files.add(((StringType) base).value);
            }
        }

        ListType tagsList = data.getList("tags");

        for (BaseType base : tagsList)
        {
            if (base.isString())
            {
                entry.tags.add(((StringType) base).value);
            }
        }

        return entry;
    }

    public MapType toData()
    {
        MapType map = new MapType();

        map.putString("id", this.id);
        map.putString("name", this.name);
        map.putString("author", this.author);
        map.putString("category", this.category.id);
        map.putString("description", this.description);
        map.putString("version", this.version);
        map.putString("download_url", this.downloadUrl);
        map.putString("thumbnail_url", this.thumbnailUrl);
        map.putLong("downloads", this.downloads);
        map.putLong("likes", this.likes);
        map.putLong("size_bytes", this.sizeBytes);

        ListType filesList = new ListType();

        for (String file : this.files)
        {
            filesList.add(new StringType(file));
        }

        map.put("files", filesList);

        ListType tagsList = new ListType();

        for (String tag : this.tags)
        {
            tagsList.add(new StringType(tag));
        }

        map.put("tags", tagsList);

        return map;
    }

    public boolean isInstalled()
    {
        return HubAssetDownloader.isAssetTrackedInstalled(this.id);
    }

    public boolean hasUpdate()
    {
        if (!this.isInstalled())
        {
            return false;
        }

        String installedVersion = HubAssetDownloader.getInstalledVersion(this.id);

        return installedVersion != null && !installedVersion.trim().isEmpty() && !installedVersion.equalsIgnoreCase(this.version);
    }

    public String getFormattedSize()
    {
        if (this.sizeBytes <= 0L)
        {
            return "N/A";
        }

        if (this.sizeBytes < 1024L)
        {
            return this.sizeBytes + " B";
        }

        if (this.sizeBytes < 1024L * 1024L)
        {
            return String.format("%.1f KB", this.sizeBytes / 1024F);
        }

        return String.format("%.1f MB", this.sizeBytes / (1024F * 1024F));
    }

    public boolean matches(String query, HubCategory filterCategory)
    {
        if (filterCategory != null && filterCategory != HubCategory.ALL && this.category != filterCategory)
        {
            return false;
        }

        if (query == null || query.trim().isEmpty())
        {
            return true;
        }

        String q = query.trim().toLowerCase();

        if (this.name.toLowerCase().contains(q))
        {
            return true;
        }

        if (this.author.toLowerCase().contains(q))
        {
            return true;
        }

        if (this.id.toLowerCase().contains(q))
        {
            return true;
        }

        if (this.description.toLowerCase().contains(q))
        {
            return true;
        }

        for (String tag : this.tags)
        {
            if (tag.toLowerCase().contains(q))
            {
                return true;
            }
        }

        return false;
    }
}
