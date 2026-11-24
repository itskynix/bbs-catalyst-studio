package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * The flow of a trigger box: its variables, its nodes and the execution wires between them. A new
 * flow starts with one {@code OnEnter} event, so there is always somewhere to hang the first action.
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

    /** Wires an output to an input. An output drives one wire: a new one replaces the old. */
    public boolean link(int from, String pin, int to)
    {
        FlowNode a = this.get(from);
        FlowNode b = this.get(to);

        if (a == null || b == null || from == to || b.getType() == null || !b.getType().hasInput())
        {
            return false;
        }

        this.links.removeIf((link) -> link.isExec() && link.from() == from && link.pin().equals(pin));
        this.links.add(new FlowLink(from, pin, to));

        return true;
    }

    /** Wires a data output to a data input. An input takes one wire, an output feeds as many as it likes. */
    public boolean linkData(int from, String pin, int to, String toPin)
    {
        FlowNode a = this.get(from);
        FlowNode b = this.get(to);

        if (a == null || b == null || from == to || a.getType() == null || a.getType().dataOutputs.stream().noneMatch((p) -> p.id().equals(pin)))
        {
            return false;
        }

        this.unlinkData(to, toPin);
        this.links.add(new FlowLink(from, pin, to, toPin));

        return true;
    }

    public void unlink(int from, String pin)
    {
        this.links.removeIf((link) -> link.isExec() && link.from() == from && link.pin().equals(pin));
    }

    public void unlinkData(int to, String toPin)
    {
        this.links.removeIf((link) -> link.to() == to && link.toPin().equals(toPin));
    }

    /** The wire into a data input, or null when the input reads its typed-in value. */
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

    /** Sets how many data inputs a node has, dropping the wires of the inputs that went away. */
    public void setInputCount(FlowNode node, int count)
    {
        FlowNodeType type = node.getType();

        if (type == null || type.minInputs <= 0)
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

    /** The type a data output gives: a comparison gives a boolean, a variable read the variable's type. */
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
                    FlowVariable variable = this.getVariable(node.param("variable"));

                    return variable == null ? "any" : variable.type;
                }

                return data.type();
            }
        }

        return "any";
    }

    /** The type a data input has taken on: that of the wire into it, or what it expects when it has none. */
    public String inputType(FlowNode node, int index)
    {
        FlowLink link = this.dataInto(node.id, FlowNodeType.input(index));

        if (link != null)
        {
            return this.outputType(this.get(link.from()), link.pin());
        }

        return node.getType() == null ? "any" : node.getType().inputType;
    }

    /** The node an output pin leads to, or null. */
    public FlowNode next(int from, String pin)
    {
        for (FlowLink link : this.links)
        {
            if (link.isExec() && link.from() == from && link.pin().equals(pin))
            {
                return this.get(link.to());
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

        int max = 0;

        for (FlowNode node : this.nodes)
        {
            max = Math.max(max, node.id);
        }

        this.nextId = Math.max(data.getInt("next_id", 1), max + 1);
    }
}
