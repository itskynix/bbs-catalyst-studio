package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a composition within a CatalystProject.
 * Owns independent layers, duration/frame parameters, and current playhead position.
 */
public class CatalystComposition
{
    /** Represents a single timeline marker placed at a specific frame. */
    public static class CatalystMarker
    {
        public int    frame  = 0;
        public String label  = "";
        public int    color  = 0xFFE8C43A; // default amber

        public CatalystMarker() {}

        public CatalystMarker(int frame, String label, int color)
        {
            this.frame = frame;
            this.label = label;
            this.color = color;
        }

        public MapType toData()
        {
            MapType d = new MapType();
            d.putInt   ("frame", this.frame);
            d.putString("label", this.label);
            d.putInt   ("color", this.color);
            return d;
        }

        public void fromData(MapType d)
        {
            if (d == null) return;
            if (d.has("frame")) this.frame = d.getInt("frame");
            if (d.has("label")) this.label = d.getString("label");
            if (d.has("color")) this.color = d.getInt("color");
        }
    }

    public String name = "Main Comp";
    public int fps = 60;
    public int duration = 300;
    public int width = 1920;
    public int height = 1080;
    public int playhead = 0;
    public final List<CatalystLayer> layers = new ArrayList<>();
    public final List<CatalystMarker> markers = new ArrayList<>();

    public CatalystComposition()
    {
        this.setupDefaultLayers();
    }

    public CatalystComposition(String name, int fps, int duration)
    {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "Main Comp";
        this.fps = fps > 0 ? fps : 60;
        this.duration = duration > 0 ? duration : 300;
        this.setupDefaultLayers();
    }

    public void setupDefaultLayers()
    {
        if (this.layers.isEmpty())
        {
            this.layers.add(new CatalystLayer("V1: Video / Scene Plate", CatalystLayer.LayerType.SCENE,  0xFF2B5B84));
            this.layers.add(new CatalystLayer("A1: 3D Replay Actor",     CatalystLayer.LayerType.SCENE,  0xFF4A7C38));
            this.layers.add(new CatalystLayer("A2: Audio Ambience",      CatalystLayer.LayerType.AUDIO,  0xFF88602A));
            this.layers.add(new CatalystLayer("FX1: Post FX / Color",    CatalystLayer.LayerType.SOLID,  0xFF7A3482));
        }
    }

    public double getDurationSeconds()
    {
        return this.fps > 0 ? (double) this.duration / this.fps : 0.0;
    }

    public void setDurationSeconds(double seconds)
    {
        if (seconds > 0 && this.fps > 0)
        {
            this.duration = Math.max(1, (int) Math.round(seconds * this.fps));
        }
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("name", this.name);
        data.putInt("fps", this.fps);
        data.putInt("duration", this.duration);
        data.putInt("width", this.width);
        data.putInt("height", this.height);
        data.putInt("playhead", this.playhead);

        ListType list = new ListType();

        for (CatalystLayer layer : this.layers)
        {
            list.add(layer.toData());
        }

        data.put("layers", list);

        if (!this.markers.isEmpty())
        {
            ListType markerList = new ListType();
            for (CatalystMarker marker : this.markers)
            {
                markerList.add(marker.toData());
            }
            data.put("markers", markerList);
        }

        return data;
    }

    public void fromData(MapType data)
    {
        if (data == null)
        {
            return;
        }

        if (data.has("name")) this.name = data.getString("name");
        if (data.has("fps")) this.fps = Math.max(1, data.getInt("fps"));
        if (data.has("duration")) this.duration = Math.max(1, data.getInt("duration"));
        if (data.has("width")) this.width = Math.max(1, data.getInt("width"));
        if (data.has("height")) this.height = Math.max(1, data.getInt("height"));
        if (data.has("playhead")) this.playhead = Math.max(0, data.getInt("playhead"));

        if (data.has("layers"))
        {
            this.layers.clear();

            for (BaseType base : data.getList("layers"))
            {
                if (base.isMap())
                {
                    CatalystLayer layer = new CatalystLayer();
                    layer.fromData(base.asMap());
                    this.layers.add(layer);
                }
            }
        }

        if (data.has("markers"))
        {
            this.markers.clear();
            for (BaseType base : data.getList("markers"))
            {
                if (base.isMap())
                {
                    CatalystMarker marker = new CatalystMarker();
                    marker.fromData(base.asMap());
                    this.markers.add(marker);
                }
            }
        }

        if (this.layers.isEmpty())
        {
            this.setupDefaultLayers();
        }
    }
}
