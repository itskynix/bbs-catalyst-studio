package mchorse.bbs_mod.triggers;

import java.util.List;

/**
 * What a kind of flow node is: its family, its execution pins, its data pins and the fields it is
 * set up with.
 *
 * <p>Execution pins carry the run: an event has only an output, every other node that is not pure
 * has one input and at least one output. Data pins carry values: a pure node (a comparison, a
 * variable read) has no execution pins at all and is worked out when a node downstream asks for its
 * value. A growable node starts with {@link #minInputs} data inputs and takes more with its + button.</p>
 */
public class FlowNodeType
{
    public enum Kind
    {
        EVENT(0xffc2453d), LOGIC(0xff8a4fc4), VARIABLE(0xff2f9e5c), ACTION(0xff2f7fd0), DEBUG(0xff6d7a86);

        public final int color;

        Kind(int color)
        {
            this.color = color;
        }
    }

    /** A field of the node, with what a fresh node holds in it. */
    public record Param(String id, String fallback) {}

    /** A data output: its name and type; "variable" takes the type of the variable the node reads. */
    public record DataPin(String id, String type) {}

    public final String id;
    public final Kind kind;
    public final List<String> outputs;
    public final List<Param> params;

    public boolean pure;
    public List<DataPin> dataOutputs = List.of();
    public int minInputs;
    public boolean growable;
    /** What the data inputs expect: "any", or "boolean" for conditions. */
    public String inputType = "any";
    /** Kept so old flows still load and run, but no longer offered in the add menu. */
    public boolean hidden;

    public FlowNodeType(String id, Kind kind, List<String> outputs, List<Param> params)
    {
        this.id = id;
        this.kind = kind;
        this.outputs = outputs;
        this.params = params;
    }

    public FlowNodeType pure()
    {
        this.pure = true;

        return this;
    }

    public FlowNodeType data(DataPin... outputs)
    {
        this.dataOutputs = List.of(outputs);

        return this;
    }

    public FlowNodeType inputs(int min, boolean growable, String type)
    {
        this.minInputs = min;
        this.growable = growable;
        this.inputType = type;

        return this;
    }

    public FlowNodeType hidden()
    {
        this.hidden = true;

        return this;
    }

    public boolean hasInput()
    {
        return this.kind != Kind.EVENT && !this.pure;
    }

    /** The name of the {@code i}th data input. */
    public static String input(int i)
    {
        return "in" + i;
    }
}
