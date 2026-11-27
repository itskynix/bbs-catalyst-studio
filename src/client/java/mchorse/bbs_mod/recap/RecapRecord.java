package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One saved recap: its name, where it was recorded, how long it is, when it was made and the
 * moments marked in it, plus the editor's view settings. Every time in it is a count of game ticks
 * from its start.
 */
public class RecapRecord
{
    public String id = "";
    public String name = "";
    public String worldKey = "";
    public String worldName = "";
    /** The save folder of a world of this computer, or the address of a server. */
    public String worldTarget = "";
    public boolean singleplayer = true;
    public int ticks;
    public long created;
    public boolean instant;
    public final List<Integer> markers = new ArrayList<>();
    private MapType view = new MapType();

    public boolean isOn(RecapOption option)
    {
        return this.view.getBool(option.id, option.onByDefault());
    }

    public void setOn(RecapOption option, boolean on)
    {
        this.view.putBool(option.id, on);
    }

    public int getValue(RecapOption option)
    {
        int value = this.view.getInt(option.id + "_value", option.fallback);

        return Math.max(option.min, Math.min(option.max, value));
    }

    public void setValue(RecapOption option, int value)
    {
        this.view.putInt(option.id + "_value", Math.max(option.min, Math.min(option.max, value)));
    }

    /** Adds a marker, kept in order, once per tick. */
    public void addMarker(int tick)
    {
        int at = Math.max(0, Math.min(this.ticks, tick));

        if (!this.markers.contains(at))
        {
            this.markers.add(at);
            Collections.sort(this.markers);
        }
    }

    /** Takes the editable side of another copy: its name, markers and view settings. */
    public void setEditable(RecapRecord other)
    {
        this.name = other.name;
        this.markers.clear();
        this.markers.addAll(other.markers);
        this.view = (MapType) other.view.copy();
    }

    /** Whether the editable side matches another copy. */
    public boolean sameEditable(RecapRecord other)
    {
        return this.name.equals(other.name) && this.markers.equals(other.markers) && this.view.equals(other.view);
    }

    public MapType toData()
    {
        MapType data = new MapType();
        ListType markers = new ListType();

        for (int marker : this.markers)
        {
            markers.addInt(marker);
        }

        data.putString("name", this.name);
        data.putString("world_key", this.worldKey);
        data.putString("world_name", this.worldName);
        data.putString("world_target", this.worldTarget);
        data.putBool("singleplayer", this.singleplayer);
        data.putInt("ticks", this.ticks);
        data.putLong("created", this.created);
        data.putBool("instant", this.instant);
        data.put("markers", markers);
        data.put("view", this.view.copy());

        return data;
    }

    public static RecapRecord fromData(String id, MapType data)
    {
        RecapRecord record = new RecapRecord();

        record.id = id;
        record.name = data.getString("name", id);
        record.worldKey = data.getString("world_key", "");
        record.worldName = data.getString("world_name", "");
        record.worldTarget = data.getString("world_target", "");
        record.singleplayer = data.getBool("singleplayer", true);
        record.ticks = Math.max(0, data.getInt("ticks", 0));
        record.created = data.getLong("created", 0L);
        record.instant = data.getBool("instant", false);

        for (BaseType marker : data.getList("markers"))
        {
            if (BaseType.isNumeric(marker))
            {
                record.addMarker(marker.asNumeric().intValue());
            }
        }

        MapType view = data.getMap("view");

        record.view = view == null ? new MapType() : (MapType) view.copy();

        return record;
    }
}
