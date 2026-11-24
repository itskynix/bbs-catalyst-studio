package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

import java.util.List;

/** A named value of the flow: a type and what it starts as. Values are kept as text and read by type. */
public class FlowVariable
{
    public static final List<String> TYPES = List.of("boolean", "integer", "float", "string", "vec3", "entity");

    public String name;
    public String type;
    public String value;

    public FlowVariable(String name, String type, String value)
    {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    /** What a fresh variable of the type starts as. */
    public static String defaultValue(String type)
    {
        return switch (type)
        {
            case "boolean" -> "false";
            case "integer" -> "0";
            case "float" -> "0.0";
            case "vec3" -> "0 0 0";
            default -> "";
        };
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("name", this.name);
        data.putString("type", this.type);
        data.putString("value", this.value);

        return data;
    }

    public static FlowVariable fromData(MapType data)
    {
        String type = data.getString("type", "integer");

        return new FlowVariable(data.getString("name", "var"), TYPES.contains(type) ? type : "string", data.getString("value", ""));
    }
}
