package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

/** An execution wire: from an output pin of one node to the input of another. */
public record FlowLink(int from, String pin, int to)
{
    public MapType toData()
    {
        MapType data = new MapType();

        data.putInt("from", this.from);
        data.putString("pin", this.pin);
        data.putInt("to", this.to);

        return data;
    }

    public static FlowLink fromData(MapType data)
    {
        return new FlowLink(data.getInt("from"), data.getString("pin", "then"), data.getInt("to"));
    }
}
