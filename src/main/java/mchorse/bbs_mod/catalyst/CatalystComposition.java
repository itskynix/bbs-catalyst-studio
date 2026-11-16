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

    /** What a nested clip names this timeline by; it stays when the timeline is renamed */
    public String id = java.util.UUID.randomUUID().toString().substring(0, 8);
    public String name = "Main Comp";
    public int fps = 60;
    public int duration = 300;
    public int width = 1920;
    public int height = 1080;
    public int playhead = 0;
    public final List<CatalystLayer> layers = new ArrayList<>();
    public final List<CatalystMarker> markers = new ArrayList<>();

    /** The edit timeline's tracks: video tracks bottom (V1) to top, audio tracks top (A1) to bottom */
    public final List<CatalystTrack> videoTracks = new ArrayList<>();
    public final List<CatalystTrack> audioTracks = new ArrayList<>();

    /** The tracks of a kind */
    public List<CatalystTrack> tracks(boolean audio)
    {
        return audio ? this.audioTracks : this.videoTracks;
    }

    /** The track a clip sits on, or null when it has none yet */
    public CatalystTrack trackOf(CatalystLayer layer)
    {
        List<CatalystTrack> tracks = this.tracks(layer.isAudioClip());

        return layer.track >= 0 && layer.track < tracks.size() ? tracks.get(layer.track) : null;
    }

    /**
     * Puts every clip on a track and makes sure there are tracks for them, at least two of each kind.
     * A composition from the layer stack days has no tracks: each of its layers gets a track of its
     * own, in the order they were stacked, so it looks exactly as it did - the first layer, drawn on
     * top, on the highest video track.
     */
    public void ensureTracks()
    {
        List<CatalystLayer> video = new ArrayList<>();
        List<CatalystLayer> audio = new ArrayList<>();

        for (CatalystLayer layer : this.layers)
        {
            if (layer.track < 0)
            {
                (layer.isAudioClip() ? audio : video).add(layer);
            }
        }

        int highestVideo = -1;
        int highestAudio = -1;

        for (CatalystLayer layer : this.layers)
        {
            if (layer.track >= 0)
            {
                if (layer.isAudioClip()) highestAudio = Math.max(highestAudio, layer.track);
                else highestVideo = Math.max(highestVideo, layer.track);
            }
        }

        /* The stack drew its last layer first, so the last one goes lowest */
        for (int i = video.size() - 1; i >= 0; i--)
        {
            video.get(i).track = ++highestVideo;
        }

        for (CatalystLayer layer : audio)
        {
            layer.track = ++highestAudio;
        }

        while (this.videoTracks.size() < Math.max(2, highestVideo + 1))
        {
            this.videoTracks.add(new CatalystTrack());
        }

        while (this.audioTracks.size() < Math.max(2, highestAudio + 1))
        {
            this.audioTracks.add(new CatalystTrack());
        }
    }

    public CatalystComposition()
    {
        this.setupDefaultLayers();
        this.ensureTracks();
    }

    public CatalystComposition(String name, int fps, int duration)
    {
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "Main Comp";
        this.fps = fps > 0 ? fps : 60;
        this.duration = duration > 0 ? duration : 300;
        this.setupDefaultLayers();
        this.ensureTracks();
    }

    public void setupDefaultLayers()
    {
        /* Sıfır varsayılan dayatma: Yeni proje/sahne açıldığında timeline tertemiz ve boş başlatılır */
    }

    /** The frame after the last clip of any track, 0 for a timeline with no clips */
    public int endFrame()
    {
        int end = 0;

        for (CatalystLayer layer : this.layers)
        {
            end = Math.max(end, layer.startFrame + layer.duration);
        }

        return end;
    }

    /** The timeline is as long as its last clip; one with no clips keeps a ruler of ten seconds */
    public void refreshDuration()
    {
        int end = this.endFrame();

        this.duration = end > 0 ? end : Math.max(1, this.fps * 10);
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

        data.putString("id", this.id);
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

        ListType videoList = new ListType();
        ListType audioList = new ListType();

        for (CatalystTrack track : this.videoTracks) videoList.add(track.toData());
        for (CatalystTrack track : this.audioTracks) audioList.add(track.toData());

        data.put("videoTracks", videoList);
        data.put("audioTracks", audioList);

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

        if (data.has("id") && !data.getString("id").isEmpty()) this.id = data.getString("id");
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

        this.videoTracks.clear();
        this.audioTracks.clear();

        for (String key : new String[] {"videoTracks", "audioTracks"})
        {
            if (data.has(key))
            {
                for (BaseType base : data.getList(key))
                {
                    CatalystTrack track = new CatalystTrack();

                    track.fromData(base.isMap() ? base.asMap() : null);
                    this.tracks(key.equals("audioTracks")).add(track);
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

        this.ensureTracks();
    }
}
