package mchorse.bbs_mod.triggers;

import java.util.ArrayList;
import java.util.List;

/**
 * What a kind of flow node is, all in one place, so the runtime, the Java writer and the editor read
 * the same description: its family, its execution pins, its data pins and its settings.
 *
 * <p>Execution inputs are named {@code exec} (the main one) or {@code exec:<name>} (Reset, Open...),
 * execution outputs by plain names. A node with no execution pins at all is pure: it is worked out
 * when something downstream asks for one of its values. Growable nodes add inputs ({@code in0},
 * {@code in1}...) or outputs ({@code <prefix>0}, {@code <prefix>1}...) with + and - buttons; an
 * output may carry a setting of its own, such as a weight or a case value.</p>
 */
public class FlowNodeType
{
    public static final String EXEC = "exec";

    public enum Kind
    {
        EVENT(0xffc2453d),
        FLOW(0xff9b59d0),
        LOGIC(0xff7a5cc9),
        TIME(0xffd08a2f),
        STATE(0xffc96f9e),
        RANDOM(0xffd0b12f),
        MATH(0xff4fa3a8),
        VARIABLE(0xff2f9e5c),
        WORLD(0xff3f8fd0),
        ACTION(0xff2f6fc0),
        CINEMATIC(0xffe0663a),
        DEBUG(0xff6d7a86);

        public final int color;

        Kind(int color)
        {
            this.color = color;
        }
    }

    /** A setting of the node, edited in Details and never wired. */
    public record Param(String id, String fallback) {}

    /** A data pin: its name, its type ("any", "boolean", "integer", "float", "number", "string", "vec3", "entity", or "variable" for the read variable's type) and, for an input, what it reads while nothing is wired in. */
    public record DataPin(String id, String type, String fallback) {}

    public final String id;
    public final Kind kind;

    public final List<String> execInputs = new ArrayList<>();
    public final List<String> execOutputs = new ArrayList<>();
    public final List<DataPin> dataInputs = new ArrayList<>();
    public final List<DataPin> dataOutputs = new ArrayList<>();
    public final List<Param> params = new ArrayList<>();

    /* Growable data inputs: in0, in1... */
    public int minInputs;
    public String growInputType = "any";
    public String growInputFallback = "0";

    /* Growable execution outputs: prefix0, prefix1... */
    public String growOutputPrefix;
    public int minOutputs;
    /** The setting each growable output carries (its weight, its case), or null. */
    public String outputParam;
    public String outputParamFallback = "";

    /** A command run for this node, with {pin} for a data input's value; null when the node is coded by hand. */
    public String command;

    /** Kept so old flows still load and run, but not offered in the add menu. */
    public boolean hidden;

    public FlowNodeType(String id, Kind kind)
    {
        this.id = id;
        this.kind = kind;
    }

    /* Building */

    public FlowNodeType in(String... names)
    {
        for (String name : names)
        {
            this.execInputs.add(name.equals(EXEC) ? EXEC : EXEC + ":" + name);
        }

        return this;
    }

    public FlowNodeType out(String... names)
    {
        this.execOutputs.addAll(List.of(names));

        return this;
    }

    public FlowNodeType data(String id, String type, String fallback)
    {
        this.dataInputs.add(new DataPin(id, type, fallback));

        return this;
    }

    public FlowNodeType value(String id, String type)
    {
        this.dataOutputs.add(new DataPin(id, type, ""));

        return this;
    }

    public FlowNodeType param(String id, String fallback)
    {
        this.params.add(new Param(id, fallback));

        return this;
    }

    public FlowNodeType grow(int min, String type, String fallback)
    {
        this.minInputs = min;
        this.growInputType = type;
        this.growInputFallback = fallback;

        return this;
    }

    public FlowNodeType growOut(String prefix, int min, String param, String paramFallback)
    {
        this.growOutputPrefix = prefix;
        this.minOutputs = min;
        this.outputParam = param;
        this.outputParamFallback = paramFallback;

        return this;
    }

    public FlowNodeType command(String command)
    {
        this.command = command;

        return this;
    }

    public FlowNodeType hidden()
    {
        this.hidden = true;

        return this;
    }

    /* Reading */

    public boolean pure()
    {
        return this.execInputs.isEmpty() && this.kind != Kind.EVENT;
    }

    public boolean hasInput()
    {
        return this.execInputs.contains(EXEC);
    }

    public boolean growable()
    {
        return this.minInputs > 0;
    }

    public boolean growableOutputs()
    {
        return this.growOutputPrefix != null;
    }

    public DataPin dataInput(String id)
    {
        for (DataPin pin : this.dataInputs)
        {
            if (pin.id().equals(id))
            {
                return pin;
            }
        }

        return null;
    }

    /** The name of the {@code i}th growable data input. */
    public static String input(int i)
    {
        return "in" + i;
    }

    public static boolean isExec(String pin)
    {
        return pin.equals(EXEC) || pin.startsWith(EXEC + ":");
    }
}
