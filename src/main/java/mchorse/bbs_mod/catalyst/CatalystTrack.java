package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.data.types.MapType;

/**
 * One track of the edit timeline: a video track (V1, V2, ...) or an audio track (A1, A2, ...). Clips
 * name the track they sit on by its index within its kind. A video track can be hidden (its clips
 * are not drawn), an audio track muted (its clips are not heard); a locked track can't be edited, and
 * soloing tracks of a kind leaves only those seen or heard.
 */
public class CatalystTrack
{
    public boolean locked = false;
    /** Video: the track's clips are not drawn. Audio: they are not heard. */
    public boolean muted = false;
    public boolean solo = false;

    public MapType toData()
    {
        MapType data = new MapType();

        data.putBool("locked", this.locked);
        data.putBool("muted", this.muted);
        data.putBool("solo", this.solo);

        return data;
    }

    public void fromData(MapType data)
    {
        if (data == null)
        {
            return;
        }

        if (data.has("locked")) this.locked = data.getBool("locked");
        if (data.has("muted")) this.muted = data.getBool("muted");
        if (data.has("solo")) this.solo = data.getBool("solo");
    }
}
