package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

import java.util.LinkedHashMap;
import java.util.Map;

/** One card of the flow: its type, where it sits on the board, and its fields. */
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
