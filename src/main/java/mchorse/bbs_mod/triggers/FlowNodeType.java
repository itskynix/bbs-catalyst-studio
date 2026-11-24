package mchorse.bbs_mod.triggers;

import java.util.List;

/**
 * What a kind of flow node is: its family, the colour of its header, its execution outputs and the
 * fields it is set up with. An event node starts a run and has no execution input; every other node
 * has one input and at least one output, so a chain always reads left to right.
 */
public class FlowNodeType
{
    public enum Kind
    {
        EVENT(0xffc2453d), ACTION(0xff2f7fd0), VARIABLE(0xff2f9e5c), LOGIC(0xff8a4fc4), DEBUG(0xff6d7a86);

        public final int color;

        Kind(int color)
        {
            this.color = color;
        }
    }

    /** A field of the node, with what a fresh node holds in it. */
    public record Param(String id, String fallback) {}

    public final String id;
    public final Kind kind;
    public final List<String> outputs;
    public final List<Param> params;

    public FlowNodeType(String id, Kind kind, List<String> outputs, List<Param> params)
    {
        this.id = id;
        this.kind = kind;
        this.outputs = outputs;
        this.params = params;
    }

    public boolean hasInput()
    {
        return this.kind != Kind.EVENT;
    }
}
