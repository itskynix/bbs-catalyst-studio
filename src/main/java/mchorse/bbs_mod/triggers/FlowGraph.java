package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * The flow of a trigger box: its variables, its nodes and the wires between them. A new flow starts
 * with one {@code OnEnter} event, so there is always somewhere to hang the first action.
 *
 * <p>An execution output drives one wire, an execution input takes any number. A data input takes one
 * wire, a data output feeds as many as it likes. Nodes point at variables by id; a node whose variable
 * was deleted keeps the id and is shown as pointing at an invalid variable, it is never silently
 * re-aimed.</p>
 */
public class FlowGraph
{
    public final List<FlowVariable> variables = new ArrayList<>();
    public final List<FlowNode> nodes = new ArrayList<>();
    public final List<FlowLink> links = new ArrayList<>();
    public int nextId = 1;

    public static FlowGraph createDefault()
    {
        FlowGraph graph = new FlowGraph();

        graph.add(FlowNodeTypes.ON_ENTER, 40, 60);

        return graph;
    }

    public FlowNode add(String type, float x, float y)
    {
        FlowNode node = new FlowNode(this.nextId++, type, x, y);

        this.nodes.add(node);

        return node;
    }

    public FlowNode get(int id)
    {
        for (FlowNode node : this.nodes)
        {
            if (node.id == id)
            {
                return node;
            }
        }

        return null;
    }

    public void remove(FlowNode node)
    {
        this.nodes.remove(node);
        this.links.removeIf((link) -> link.from() == node.id || link.to() == node.id);
    }

    /* Execution wires */

    public boolean link(int from, String pin, int to)
    {
        return this.link(from, pin, to, FlowNodeType.EXEC);
    }

    /** Wires an execution output to an execution input. A new wire from an output replaces its old one. */
    public boolean link(int from, String pin, int to, String toPin)
    {
        FlowNode a = this.get(from);
        FlowNode b = this.get(to);

        if (a == null || b == null || from == to || b.getType() == null || !b.getType().execInputs.contains(toPin) || !a.execOutputs().contains(pin))
        {
            return false;
        }

        this.unlink(from, pin);
        this.links.add(new FlowLink(from, pin, to, toPin));

        return true;
    }

    public void unlink(int from, String pin)
    {
        this.links.removeIf((link) -> link.isExec() && link.from() == from && link.pin().equals(pin));
    }

    /** The wire leaving an execution output, or null. */
    public FlowLink execFrom(int from, String pin)
    {
        for (FlowLink link : this.links)
        {
            if (link.isExec() && link.from() == from && link.pin().equals(pin))
            {
                return link;
            }
        }

        return null;
    }

    /** The node an execution output leads to, or null. */
    public FlowNode next(int from, String pin)
    {
        FlowLink link = this.execFrom(from, pin);

        return link == null ? null : this.get(link.to());
    }

    /* Data wires */

    public boolean linkData(int from, String pin, int to, String toPin)
    {
        FlowNode a = this.get(from);
        FlowNode b = this.get(to);

        if (a == null || b == null || from == to || a.getType() == null || a.getType().dataOutputs.stream().noneMatch((p) -> p.id().equals(pin)) || !b.dataInputs().contains(toPin))
        {
            return false;
        }

        this.unlinkData(to, toPin);
        this.links.add(new FlowLink(from, pin, to, toPin));

        return true;
    }

    public void unlinkData(int to, String toPin)
    {
        this.links.removeIf((link) -> link.to() == to && link.toPin().equals(toPin));
    }

    public FlowLink dataInto(int to, String toPin)
    {
        for (FlowLink link : this.links)
        {
            if (link.to() == to && link.toPin().equals(toPin))
            {
                return link;
            }
        }

        return null;
    }

    /** Drops the data wires into inputs the node no longer has, after its template changed. */
    public void pruneLinks(FlowNode node)
    {
        List<String> inputs = node.dataInputs();

        this.links.removeIf((link) -> link.to() == node.id && !link.isExec() && !inputs.contains(link.toPin()));
    }

    /* Growing */

    public void setInputCount(FlowNode node, int count)
    {
        FlowNodeType type = node.getType();

        if (type == null || !type.growable())
        {
            return;
        }

        count = Math.max(type.minInputs, Math.min(16, count));

        for (int i = count; i < node.inputCount(); i++)
        {
            this.unlinkData(node.id, FlowNodeType.input(i));
            node.params.remove(FlowNodeType.input(i));
        }

        node.setInputCount(count);
    }

    public void setOutputCount(FlowNode node, int count)
    {
        FlowNodeType type = node.getType();

        if (type == null || !type.growableOutputs())
        {
            return;
        }

        count = Math.max(type.minOutputs, Math.min(16, count));

        for (int i = count; i < node.outputCount(); i++)
        {
            this.unlink(node.id, type.growOutputPrefix + i);

            if (type.outputParam != null)
            {
                node.params.remove(type.outputParam + i);
            }
        }

        node.setOutputCount(count);
    }

    /* Types */

    /** The type a data output gives; "variable" outputs give the variable's type. */
    public String outputType(FlowNode node, String pin)
    {
        FlowNodeType type = node == null ? null : node.getType();

        if (type == null)
        {
            return "any";
        }

        for (FlowNodeType.DataPin data : type.dataOutputs)
        {
            if (data.id().equals(pin))
            {
                if (data.type().equals("variable"))
                {
                    FlowVariable variable = this.variableOf(node);

                    return variable == null ? "any" : variable.type;
                }

                if (type.id.equals("select"))
                {
                    /* Select gives what it picks from: the type of its first wired option */
                    for (int i = 0; i < node.inputCount(); i++)
                    {
                        FlowLink link = this.dataInto(node.id, FlowNodeType.input(i));

                        if (link != null)
                        {
                            return this.outputType(this.get(link.from()), link.pin());
                        }
                    }
                }

                return data.type();
            }
        }

        return "any";
    }

    /** The type a data input has taken on: that of the wire into it, or what it expects with none. */
    public String inputType(FlowNode node, String pin)
    {
        FlowLink link = this.dataInto(node.id, pin);

        if (link != null)
        {
            return this.outputType(this.get(link.from()), link.pin());
        }

        String declared = node.declaredInputType(pin);

        if (declared.equals("variable"))
        {
            FlowVariable variable = this.variableOf(node);

            return variable == null ? "any" : variable.type;
        }

        return declared;
    }

    public String inputType(FlowNode node, int index)
    {
        return this.inputType(node, FlowNodeType.input(index));
    }

    /* Variables */

    public FlowVariable getVariableById(String id)
    {
        for (FlowVariable variable : this.variables)
        {
            if (variable.id.equals(id))
            {
                return variable;
            }
        }

        return null;
    }

    public FlowVariable getVariable(String name)
    {
        for (FlowVariable variable : this.variables)
        {
            if (variable.name.equals(name))
            {
                return variable;
            }
        }

        return null;
    }

    /** The variable a node reads or writes, or null when it has none or it was deleted. */
    public FlowVariable variableOf(FlowNode node)
    {
        return this.getVariableById(node.param("variable"));
    }

    /** How many nodes read or write a variable. */
    public int usages(FlowVariable variable)
    {
        int count = 0;

        for (FlowNode node : this.nodes)
        {
            if (variable.id.equals(node.params.get("variable")))
            {
                count += 1;
            }
        }

        return count;
    }

    public FlowGraph copy()
    {
        FlowGraph graph = new FlowGraph();

        graph.fromData(this.toData());

        return graph;
    }

    public MapType toData()
    {
        MapType data = new MapType();
        ListType variables = new ListType();
        ListType nodes = new ListType();
        ListType links = new ListType();

        for (FlowVariable variable : this.variables) variables.add(variable.toData());
        for (FlowNode node : this.nodes) nodes.add(node.toData());
        for (FlowLink link : this.links) links.add(link.toData());

        data.put("variables", variables);
        data.put("nodes", nodes);
        data.put("links", links);
        data.putInt("next_id", this.nextId);

        return data;
    }

    public void fromData(MapType data)
    {
        this.variables.clear();
        this.nodes.clear();
        this.links.clear();

        for (BaseType type : data.getList("variables"))
        {
            if (type.isMap()) this.variables.add(FlowVariable.fromData(type.asMap()));
        }

        for (BaseType type : data.getList("nodes"))
        {
            if (type.isMap()) this.nodes.add(FlowNode.fromData(type.asMap()));
        }

        for (BaseType type : data.getList("links"))
        {
            if (type.isMap()) this.links.add(FlowLink.fromData(type.asMap()));
        }

        /* Flows from before variable ids pointed at variables by name */
        for (FlowNode node : this.nodes)
        {
            String ref = node.params.get("variable");

            if (ref != null && !ref.isEmpty() && this.getVariableById(ref) == null)
            {
                FlowVariable byName = this.getVariable(ref);

                if (byName != null)
                {
                    node.params.put("variable", byName.id);
                }
            }
        }

        int max = 0;

        for (FlowNode node : this.nodes)
        {
            max = Math.max(max, node.id);
        }

        this.nextId = Math.max(data.getInt("next_id", 1), max + 1);
    }
}
