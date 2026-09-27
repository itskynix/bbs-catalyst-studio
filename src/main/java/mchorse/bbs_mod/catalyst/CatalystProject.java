package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
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

        this.ensureCompositions();

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
                project.fromData(data);
                return project;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
