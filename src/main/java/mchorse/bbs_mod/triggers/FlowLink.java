package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

/**
 * A wire. An execution wire runs from an output pin to a node's execution input ({@link #EXEC});
 * a data wire runs from a data output to one of the node's data inputs, named by {@code toPin}.
 */
public record FlowLink(int from, String pin, int to, String toPin)
{
    public static final String EXEC = "exec";

    public FlowLink(int from, String pin, int to)
    {
        this(from, pin, to, EXEC);
    }

    public boolean isExec()
    {
        return EXEC.equals(this.toPin);
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putInt("from", this.from);
        data.putString("pin", this.pin);
        data.putInt("to", this.to);
        data.putString("to_pin", this.toPin);

        return data;
    }

    public static FlowLink fromData(MapType data)
    {
        return new FlowLink(data.getInt("from"), data.getString("pin", "then"), data.getInt("to"), data.getString("to_pin", EXEC));
    }
}
