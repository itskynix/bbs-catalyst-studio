package mchorse.bbs_mod.film.world;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedded mini Dope Sheet structure owned by a {@link WorldEventBlock}.
 * Manages MTR dispatch tracks and evaluates their keyframe stages relative to the block's start tick.
 */
public class SubDopeSheet
{
    public MtrDispatchTrack dispatchTrack = new MtrDispatchTrack();
    public final List<MtrDispatchTrack> tracks = new ArrayList<>();

    public SubDopeSheet()
    {
        this.tracks.add(this.dispatchTrack);
    }

    public MtrDispatchTrack getPrimaryTrack()
    {
        if (this.dispatchTrack == null)
        {
            this.dispatchTrack = new MtrDispatchTrack();
        }

        if (this.tracks.isEmpty())
        {
            this.tracks.add(this.dispatchTrack);
        }

        return this.dispatchTrack;
    }

    public void apply(WorldEventBlock block, float localTick, float masterTick, WorldTimeline.IMtrWorldBridge bridge)
    {
        MtrDispatchTrack primary = this.getPrimaryTrack();

        primary.apply(block, localTick, masterTick, bridge);

        for (int i = 1; i < this.tracks.size(); i++)
        {
            MtrDispatchTrack extra = this.tracks.get(i);

            if (extra != null && extra != primary)
            {
                extra.apply(block, localTick, masterTick, bridge);
            }
        }
    }

    public MapType toNbt()
    {
        MapType map = new MapType();

        this.toNbt(map);

        return map;
    }

    public void toNbt(MapType map)
    {
        if (map == null)
        {
            return;
        }

        MtrDispatchTrack primary = this.getPrimaryTrack();

        map.put("dispatch_track", primary.toNbt());

        if (this.tracks.size() > 1)
        {
            ListType list = new ListType();

            for (MtrDispatchTrack track : this.tracks)
            {
                if (track != null)
                {
                    list.add(track.toNbt());
                }
            }

            map.put("tracks", list);
        }
    }

    public void fromNbt(MapType map)
    {
        this.tracks.clear();

        if (map == null)
        {
            this.dispatchTrack = new MtrDispatchTrack();
            this.tracks.add(this.dispatchTrack);

            return;
        }

        if (map.has("dispatch_track") && map.get("dispatch_track").isMap())
        {
            this.dispatchTrack = new MtrDispatchTrack();
            this.dispatchTrack.fromNbt(map.getMap("dispatch_track"));
        }
        else
        {
            this.dispatchTrack = new MtrDispatchTrack();
        }

        if (map.has("tracks") && map.get("tracks").isList())
        {
            ListType list = map.getList("tracks");

            for (int i = 0; i < list.size(); i++)
            {
                BaseType entry = list.get(i);

                if (entry != null && entry.isMap())
                {
                    MtrDispatchTrack track = new MtrDispatchTrack();

                    track.fromNbt(entry.asMap());
                    this.tracks.add(track);
                }
            }
        }

        if (this.tracks.isEmpty())
        {
            this.tracks.add(this.dispatchTrack);
        }
        else
        {
            this.dispatchTrack = this.tracks.get(0);
        }
    }

    public SubDopeSheet copy()
    {
        SubDopeSheet copy = new SubDopeSheet();

        copy.tracks.clear();
        copy.dispatchTrack = this.getPrimaryTrack().copy();
        copy.tracks.add(copy.dispatchTrack);

        for (int i = 1; i < this.tracks.size(); i++)
        {
            MtrDispatchTrack extra = this.tracks.get(i);

            if (extra != null)
            {
                copy.tracks.add(extra.copy());
            }
        }

        return copy;
    }
}
