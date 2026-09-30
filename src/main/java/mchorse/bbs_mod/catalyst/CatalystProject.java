package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Data model for a Catalyst Editor project.
 * Contains composition timeline parameters: name, FPS, duration in seconds/frames, resolution, and composition tabs.
 */
public class CatalystProject
{
    public String name = "New Project";
    public int fps = 60;
    public double durationSeconds = 5.0;
    public int duration = 300;
    public int width = 1920;
    public int height = 1080;
    public long createdAt = System.currentTimeMillis();
    public long lastModified = System.currentTimeMillis();

    public final List<CatalystComposition> compositions = new ArrayList<>();
    public int activeCompositionIndex = 0;
    public final List<CatalystMediaAsset> mediaPool = new ArrayList<>();
    public transient File file;

    public CatalystMediaAsset getAssetByPath(String path)
    {
        if (path == null)
        {
            return null;
        }

        String norm = path.replace('\\', '/');
        for (CatalystMediaAsset asset : this.mediaPool)
        {
            if (norm.equals(asset.path) || path.equals(asset.path) || path.equals(asset.id))
            {
                return asset;
            }
        }

        return null;
    }

    public CatalystMediaAsset addAsset(File file)
    {
        if (file == null || !file.exists())
        {
            return null;
        }

        String norm = file.getAbsolutePath().replace('\\', '/');
        for (CatalystMediaAsset existing : this.mediaPool)
        {
            if (norm.equals(existing.path) || file.getAbsolutePath().equals(existing.path))
            {
                return existing;
            }
        }

        CatalystMediaAsset newAsset = new CatalystMediaAsset(file, this.fps);
        this.mediaPool.add(newAsset);
        this.lastModified = System.currentTimeMillis();

        return newAsset;
    }

    public boolean removeAsset(CatalystMediaAsset asset)
    {
        if (asset != null && this.mediaPool.remove(asset))
        {
            this.lastModified = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    public void syncMediaPoolWithLayers()
    {
        for (CatalystComposition comp : this.compositions)
        {
            for (CatalystLayer layer : comp.layers)
            {
                if (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                {
                    String p = layer.resourcePath.trim();
                    if (this.getAssetByPath(p) == null)
                    {
                        File f = new File(p);
                        CatalystMediaAsset asset;
                        if (f.exists())
                        {
                            asset = new CatalystMediaAsset(f, this.fps);
                        }
                        else
                        {
                            asset = new CatalystMediaAsset(p, this.fps);
                        }
                        if (layer.mediaDuration > 0)
                        {
                            asset.durationFrames = layer.mediaDuration;
                        }
                        this.mediaPool.add(asset);
                    }
                }
            }
        }
    }

    public int cleanUnusedMedia()
    {
        java.util.Set<String> usedPaths = new java.util.HashSet<>();

        for (CatalystComposition comp : this.compositions)
        {
            for (CatalystLayer layer : comp.layers)
            {
                if (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                {
                    String p = layer.resourcePath.trim();
                    usedPaths.add(p);
                    usedPaths.add(p.replace('\\', '/'));
                }
            }
        }

        int removed = 0;

        for (int i = this.mediaPool.size() - 1; i >= 0; i--)
        {
            CatalystMediaAsset asset = this.mediaPool.get(i);
            if (!usedPaths.contains(asset.path) && !usedPaths.contains(asset.id))
            {
                this.mediaPool.remove(i);
                removed++;
            }
        }

        if (removed > 0)
        {
            this.lastModified = System.currentTimeMillis();
        }

        return removed;
    }

    public CatalystProject()
    {
        this.ensureCompositions();
    }

    public CatalystProject(String name, int fps, double durationSeconds)
    {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "New Project";
        this.fps = fps > 0 ? fps : 60;
        this.durationSeconds = durationSeconds > 0 ? durationSeconds : 5.0;
        this.duration = (int) Math.round(this.durationSeconds * this.fps);
        this.ensureCompositions();
    }

    public CatalystProject(String name, int fps, int durationFrames)
    {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "New Project";
        this.fps = fps > 0 ? fps : 60;
        this.duration = durationFrames > 0 ? durationFrames : 300;
        this.durationSeconds = (double) this.duration / this.fps;
        this.ensureCompositions();
    }

    public void ensureCompositions()
    {
        if (this.compositions.isEmpty())
        {
            CatalystComposition defaultComp = new CatalystComposition("Main Comp", this.fps, this.duration);
            defaultComp.width = this.width;
            defaultComp.height = this.height;
            this.compositions.add(defaultComp);
        }

        if (this.activeCompositionIndex < 0 || this.activeCompositionIndex >= this.compositions.size())
        {
            this.activeCompositionIndex = 0;
        }
    }

    public CatalystComposition getActiveComposition()
    {
        this.ensureCompositions();
        return this.compositions.get(this.activeCompositionIndex);
    }

    public CatalystComposition addComposition(String name)
    {
        String compName = name != null && !name.trim().isEmpty() ? name.trim() : "Comp " + (this.compositions.size() + 1);
        CatalystComposition comp = new CatalystComposition(compName, this.fps, this.duration);
        comp.width = this.width;
        comp.height = this.height;
        this.compositions.add(comp);
        this.activeCompositionIndex = this.compositions.size() - 1;
        return comp;
    }

    public boolean removeComposition(int index)
    {
        if (this.compositions.size() <= 1)
        {
            return false; // Cannot remove the last remaining composition
        }

        if (index >= 0 && index < this.compositions.size())
        {
            this.compositions.remove(index);

            if (this.activeCompositionIndex >= this.compositions.size())
            {
                this.activeCompositionIndex = this.compositions.size() - 1;
            }

            return true;
        }

        return false;
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("name", this.name);
        data.putInt("fps", this.fps);
        data.putDouble("durationSeconds", this.durationSeconds);
        data.putInt("duration", this.duration);
        data.putInt("width", this.width);
        data.putInt("height", this.height);
        data.putLong("createdAt", this.createdAt);
        data.putLong("lastModified", this.lastModified);
        data.putInt("activeCompositionIndex", this.activeCompositionIndex);

        ListType compList = new ListType();

        for (CatalystComposition comp : this.compositions)
        {
            compList.add(comp.toData());
        }

        data.put("compositions", compList);

        ListType poolList = new ListType();

        for (CatalystMediaAsset asset : this.mediaPool)
        {
            poolList.add(asset.toData());
        }

        data.put("mediaPool", poolList);

        return data;
    }

    public void fromData(MapType data)
    {
        if (data == null)
        {
            return;
        }

        if (data.has("name")) this.name = data.getString("name");
        if (data.has("fps")) this.fps = data.getInt("fps");
        if (data.has("duration")) this.duration = data.getInt("duration");

        if (data.has("durationSeconds"))
        {
            this.durationSeconds = data.getDouble("durationSeconds");
        }
        else
        {
            this.durationSeconds = this.fps > 0 ? (double) this.duration / this.fps : 5.0;
        }

        if (data.has("width")) this.width = data.getInt("width");
        if (data.has("height")) this.height = data.getInt("height");
        if (data.has("createdAt")) this.createdAt = data.getLong("createdAt");
        if (data.has("lastModified")) this.lastModified = data.getLong("lastModified");

        if (data.has("compositions"))
        {
            this.compositions.clear();

            for (BaseType base : data.getList("compositions"))
            {
                if (base.isMap())
                {
                    CatalystComposition comp = new CatalystComposition();
                    comp.fromData(base.asMap());
                    this.compositions.add(comp);
                }
            }
        }

        if (data.has("mediaPool"))
        {
            this.mediaPool.clear();

            for (BaseType base : data.getList("mediaPool"))
            {
                if (base.isMap())
                {
                    CatalystMediaAsset asset = new CatalystMediaAsset();
                    asset.fromData(base.asMap());
                    this.mediaPool.add(asset);
                }
            }
        }

        this.ensureCompositions();
        this.syncMediaPoolWithLayers();

        if (data.has("activeCompositionIndex"))
        {
            int idx = data.getInt("activeCompositionIndex");
            if (idx >= 0 && idx < this.compositions.size())
            {
                this.activeCompositionIndex = idx;
            }
        }
    }

    public void save(File file)
    {
        this.lastModified = System.currentTimeMillis();
        DataToString.writeSilently(file, this.toData(), true);
    }

    public static CatalystProject load(File file)
    {
        try
        {
            if (file != null && file.exists() && file.isFile())
            {
                MapType data = DataToString.read(file).asMap();
                CatalystProject project = new CatalystProject();

                project.file = file;
                project.fromData(data);

                return project;
            }
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.error("Failed to load Catalyst project from " + (file != null ? file.getAbsolutePath() : "null") + "! Creating corrupt backup...", e);

            if (file != null && file.exists() && file.isFile())
            {
                try
                {
                    File corruptBackup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());

                    Files.copy(file.toPath(), corruptBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    BBSMod.LOGGER.info("Saved corrupted Catalyst project backup to: " + corruptBackup.getAbsolutePath());
                }
                catch (Exception copyEx)
                {
                    BBSMod.LOGGER.error("Failed to create corrupt backup for " + file.getAbsolutePath(), copyEx);
                }
            }
        }

        return null;
    }
}
