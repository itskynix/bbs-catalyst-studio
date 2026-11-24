package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

/**
 * A wire, from a pin of one node to a pin of another. Both ends are named: {@code fromPin} is an
 * execution output ("then", "true", "out0"...) or a data output ("result", "value"...), {@code toPin}
 * an execution input ("exec", "exec:reset"...) or a data input ("in0", "entity"...). Whether it carries
 * the run or a value is read off the input it goes into.
 */
public record FlowLink(int from, String pin, int to, String toPin)
{
    public static final String EXEC = FlowNodeType.EXEC;

    public FlowLink(int from, String pin, int to)
    {
        this(from, pin, to, EXEC);
    }

    /** The output pin the wire leaves from. */
    public String fromPin()
    {
        return this.pin;
    }

    public boolean isExec()
    {
        return FlowNodeType.isExec(this.toPin);
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
