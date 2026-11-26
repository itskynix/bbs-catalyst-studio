package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.data.types.MapType;

import java.util.List;
import java.util.UUID;

/**
 * A variable of a flow. Nodes hold its {@link #id}, never its name, so renaming it shows on every
 * node at once. Its scope says where its value lives: in this box ({@code local}), shared by the
 * whole world ({@code global}, by name, so boxes that name a variable alike share it), or one per
 * entity ({@code entity}, keyed by the triggering entity's UUID). {@link #isArray} is carried and
 * saved now, for lists later.
 */
public class FlowVariable
{
    public static final List<String> TYPES = List.of("boolean", "integer", "float", "string", "vec3", "entity", "block", "item", "sound", "particle", "entity_type", "color");
    public static final List<String> SCOPES = List.of("local", "global", "entity");

    public String id;
    public String name;
    public String type;
    public String value;
    public String scope = "local";
    public boolean isArray;

    public FlowVariable(String name, String type, String value)
    {
        this(newId(), name, type, value);
    }

    public FlowVariable(String id, String name, String type, String value)
    {
        this.id = id;
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public static String newId()
    {
        return "v" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
            case "block" -> "minecraft:stone";
            case "item" -> "minecraft:diamond";
            case "sound" -> "minecraft:block.note_block.pling";
            case "particle" -> "minecraft:happy_villager";
            case "entity_type" -> "minecraft:pig";
            case "color" -> "ffffff";
            default -> "";
        };
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("id", this.id);
        data.putString("name", this.name);
        data.putString("type", this.type);
        data.putString("value", this.value);
        data.putString("scope", this.scope);
        data.putBool("is_array", this.isArray);

        return data;
    }

    public static FlowVariable fromData(MapType data)
    {
        String type = data.getString("type", "integer");
        String scope = data.getString("scope", "local");
        FlowVariable variable = new FlowVariable(data.getString("id", newId()), data.getString("name", "var"), TYPES.contains(type) ? type : "string", data.getString("value", ""));

        variable.scope = SCOPES.contains(scope) ? scope : "local";
        variable.isArray = data.getBool("is_array", false);

        return variable;
    }
}
