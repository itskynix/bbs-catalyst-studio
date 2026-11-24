package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One card of the flow: its type, where it sits on the board, and its values. {@link #params} holds
 * the node's settings and, under each data input's name, the value that input reads while nothing
 * is wired in; "inputs" and "outputs" hold how many growable pins it has.
 */
public class FlowNode
{
    public int id;
    public String type;
    public float x;
    public float y;
    public final Map<String, String> params = new LinkedHashMap<>();

    public FlowNode(int id, String type, float x, float y)
    {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;

        FlowNodeType kind = FlowNodeTypes.get(type);

        if (kind != null)
        {
            for (FlowNodeType.Param param : kind.params)
            {
                this.params.put(param.id(), param.fallback());
            }

            for (FlowNodeType.DataPin pin : kind.dataInputs)
            {
                this.params.put(pin.id(), pin.fallback());
            }

            if (kind.growable())
            {
                this.setInputCount(kind.minInputs);
            }

            if (kind.growableOutputs())
            {
                this.setOutputCount(kind.minOutputs);
            }
        }
    }

    public FlowNodeType getType()
    {
        return FlowNodeTypes.get(this.type);
    }

    public String param(String id)
    {
        return this.params.getOrDefault(id, "");
    }

    /* Growable inputs */

    public int inputCount()
    {
        FlowNodeType type = this.getType();

        if (type == null || !type.growable())
        {
            return 0;
        }

        return Math.max(type.minInputs, (int) FlowValues.parse(this.params.get("inputs"), type.minInputs));
    }

    public void setInputCount(int count)
    {
        FlowNodeType type = this.getType();

        this.params.put("inputs", String.valueOf(count));

        for (int i = 0; i < count; i++)
        {
            this.params.putIfAbsent(FlowNodeType.input(i), type == null ? "0" : type.growInputFallback);
        }
    }

    /* Growable outputs */

    public int outputCount()
    {
        FlowNodeType type = this.getType();

        if (type == null || !type.growableOutputs())
        {
            return 0;
        }

        return Math.max(type.minOutputs, (int) FlowValues.parse(this.params.get("outputs"), type.minOutputs));
    }

    public void setOutputCount(int count)
    {
        FlowNodeType type = this.getType();

        this.params.put("outputs", String.valueOf(count));

        if (type != null && type.outputParam != null)
        {
            for (int i = 0; i < count; i++)
            {
                this.params.putIfAbsent(type.outputParam + i, type.outputParamFallback.isEmpty() ? String.valueOf(i) : type.outputParamFallback);
            }
        }
    }

    /* Pins as the card shows them */

    /** Every execution output: the fixed ones, then the grown ones. */
    public List<String> execOutputs()
    {
        FlowNodeType type = this.getType();
        List<String> outputs = new ArrayList<>();

        if (type == null)
        {
            return outputs;
        }

        if (type.growableOutputs())
        {
            for (int i = 0; i < this.outputCount(); i++)
            {
                outputs.add(type.growOutputPrefix + i);
            }
        }

        outputs.addAll(type.execOutputs);

        return outputs;
    }

    /** Every data input by name: the fixed ones, then the grown in0, in1... */
    public List<String> dataInputs()
    {
        FlowNodeType type = this.getType();
        List<String> inputs = new ArrayList<>();

        if (type == null)
        {
            return inputs;
        }

        for (FlowNodeType.DataPin pin : type.dataInputs)
        {
            inputs.add(pin.id());
        }

        for (int i = 0; i < this.inputCount(); i++)
        {
            inputs.add(FlowNodeType.input(i));
        }

        if (type.templateInputs)
        {
            for (String name : templateNames(this.param("template")))
            {
                inputs.add(FlowNodeType.templatePin(name));
            }
        }

        return inputs;
    }

    /** The distinct {names} of a template, in order; a name is letters, digits, _ or -, up to 32 of them. */
    public static List<String> templateNames(String template)
    {
        List<String> names = new ArrayList<>();
        int i = 0;

        while (template != null && i < template.length())
        {
            int open = template.indexOf('{', i);
            int close = open < 0 ? -1 : template.indexOf('}', open);

            if (open < 0 || close < 0)
            {
                break;
            }

            String name = template.substring(open + 1, close).trim();
            boolean valid = !name.isEmpty() && name.length() <= 32;

            for (char c : name.toCharArray())
            {
                valid &= Character.isLetterOrDigit(c) || c == '_' || c == '-';
            }

            if (valid && !names.contains(name))
            {
                names.add(name);
            }

            i = close + 1;
        }

        return names;
    }

    /** What a data input expects, before anything is wired into it. */
    public String declaredInputType(String pin)
    {
        FlowNodeType type = this.getType();

        if (type == null)
        {
            return "any";
        }

        FlowNodeType.DataPin data = type.dataInput(pin);

        return data != null ? data.type() : type.growInputType;
    }

    public MapType toData()
    {
        MapType data = new MapType();
        MapType params = new MapType();

        data.putInt("id", this.id);
        data.putString("type", this.type);
        data.putFloat("x", this.x);
        data.putFloat("y", this.y);

        for (Map.Entry<String, String> entry : this.params.entrySet())
        {
            params.putString(entry.getKey(), entry.getValue());
        }

        data.put("params", params);

        return data;
    }

    public static FlowNode fromData(MapType data)
    {
        FlowNode node = new FlowNode(data.getInt("id"), data.getString("type", FlowNodeTypes.ON_ENTER), data.getFloat("x"), data.getFloat("y"));
        MapType params = data.getMap("params");

        for (String key : params.keys())
        {
            node.params.put(key, params.getString(key));
        }

        return node;
    }
}
