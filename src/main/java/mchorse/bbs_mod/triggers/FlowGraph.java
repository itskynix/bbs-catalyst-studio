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

        this.links.removeIf((link) -> link.from() == from && link.pin().equals(pin));
        this.links.add(new FlowLink(from, pin, to));

        return true;
    }

    public void unlink(int from, String pin)
    {
        this.links.removeIf((link) -> link.from() == from && link.pin().equals(pin));
    }

    public void unlinkInput(int to)
    {
        this.links.removeIf((link) -> link.to() == to);
    }

    /** The node an output pin leads to, or null. */
    public FlowNode next(int from, String pin)
    {
        for (FlowLink link : this.links)
        {
            if (link.from() == from && link.pin().equals(pin))
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
