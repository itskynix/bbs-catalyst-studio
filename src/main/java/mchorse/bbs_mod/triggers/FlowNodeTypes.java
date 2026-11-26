package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.triggers.FlowNodeType.Kind;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The catalogue of every node a trigger flow can hold, family by family, in the order the add menu
 * lists them. The runtime ({@link FlowRuntime}) and the Java writer ({@link FlowJavaGenerator}) work
 * each one out by its id; an action with a {@link FlowNodeType#command} needs nothing more than its
 * entry here.
 */
public class FlowNodeTypes
{
    /* Ids the code refers to by name */
    public static final String ON_ENTER = "on_enter";
    public static final String ON_EXIT = "on_exit";
    public static final String ON_STAY = "on_stay";
    public static final String ON_PLAYER_INTERACT = "on_player_interact";
    public static final String ON_PLAYER_HURT = "on_player_hurt";
    public static final String ON_BLOCK_BREAK = "on_block_break";

    public static final String IF_ELSE = "if_else";
    public static final String LESS = "less";
    public static final String GREATER = "greater";
    public static final String LESS_EQUAL = "less_equal";
    public static final String GREATER_EQUAL = "greater_equal";
    public static final String EQUAL = "equal";
    public static final String NOT_EQUAL = "not_equal";
    public static final String AND = "and";
    public static final String WAIT = "wait";

    public static final String GET_VARIABLE = "get_variable";
    public static final String SET_VARIABLE = "set_variable";
    public static final String ADD_VARIABLE = "add_variable";

    public static final String RUN_COMMAND = "run_command";
    public static final String SEND_MESSAGE = "send_message";
    public static final String LOG = "log";

    /** The first build's branch: a variable against a typed value. Still runs, no longer offered. */
    public static final String BRANCH = "branch";

    /** The comparison each comparison node makes, as the operator it reads as. */
    public static final Map<String, String> OPERATOR_OF = Map.of(
        LESS, "<", GREATER, ">", LESS_EQUAL, "<=", GREATER_EQUAL, ">=", EQUAL, "==", NOT_EQUAL, "!=", AND, "&&"
    );

    /** The Boolean operators over several inputs, and the single input NOT. */
    public static final Map<String, String> BOOLEAN_OF = Map.of(
        AND, "and", "or", "or", "nand", "nand", "nor", "nor", "xor", "xor", "xnor", "xnor"
    );

    /** The comparisons the old branch could make, in the order its field cycles through them. */
    public static final java.util.List<String> OPERATORS = java.util.List.of("==", "!=", ">", "<", ">=", "<=");

    /** What a Get of the box, an entity or a block reads; a transform reads the other three at once. */
    public static final java.util.List<String> TRANSFORM_PARTS = java.util.List.of("location", "rotation", "scale", "transform");

    private static final Map<String, FlowNodeType> TYPES = new LinkedHashMap<>();

    static
    {
        /* Events: only an output */
        for (String id : new String[] {ON_ENTER, ON_EXIT, ON_STAY, ON_PLAYER_INTERACT, ON_PLAYER_HURT, ON_BLOCK_BREAK})
        {
            FlowNodeType event = add(id, Kind.EVENT).out("then");

            if (id.equals(ON_STAY))
            {
                event.param("interval", "20");
            }
        }

        /* Flow control */
        add(IF_ELSE, Kind.FLOW).in("exec").out("true", "false").grow(1, "boolean", "true");
        add("sequence", Kind.FLOW).in("exec").growOut("then", 2, null, "");
        add("flip_flop", Kind.FLOW).in("exec").out("a", "b").value("is_a", "boolean");
        add("do_once", Kind.FLOW).in("exec", "reset").out("completed").param("start_closed", "false");
        add("do_n", Kind.FLOW).in("exec", "reset").out("exit").data("n", "integer", "3").value("counter", "integer");
        add("gate", Kind.FLOW).in("exec", "open", "close", "toggle").out("exit").param("start_open", "true");
        add("multi_gate", Kind.FLOW).in("exec", "reset").growOut("out", 2, null, "").param("random", "false").param("loop", "false");
        add("for_loop", Kind.FLOW).in("exec").out("body", "completed").data("first", "integer", "0").data("last", "integer", "9").value("index", "integer");
        add("for_loop_break", Kind.FLOW).in("exec", "break").out("body", "completed").data("first", "integer", "0").data("last", "integer", "9").value("index", "integer");
        add("while_loop", Kind.FLOW).in("exec").out("body", "completed").data("condition", "boolean", "false").param("max_iterations", "1000");
        add("for_each", Kind.FLOW).in("exec").out("body", "completed").data("array", "array", "a,b,c").value("element", "string").value("index", "integer");
        add("for_each_break", Kind.FLOW).in("exec", "break").out("body", "completed").data("array", "array", "a,b,c").value("element", "string").value("index", "integer");
        add("reverse_for_each", Kind.FLOW).in("exec").out("body", "completed").data("array", "array", "a,b,c").value("element", "string").value("index", "integer");
        add(WAIT, Kind.TIME).in("exec").out("then").data("ticks", "integer", "20");

        /* Logic: Boolean operators, comparisons, choosing */
        for (String id : new String[] {AND, "or", "nand", "nor", "xor", "xnor"})
        {
            add(id, Kind.LOGIC).grow(2, "boolean", "true").value("result", "boolean");
        }

        add("not", Kind.LOGIC).data("in0", "boolean", "false").value("result", "boolean");

        for (String id : new String[] {EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL})
        {
            add(id, Kind.LOGIC).grow(2, "any", "0").value("result", "boolean");
        }

        add("switch_int", Kind.LOGIC).in("exec").out("default").data("selection", "integer", "0").growOut("case", 2, null, "").param("start", "0");
        add("switch_string", Kind.LOGIC).in("exec").out("default").data("selection", "string", "").growOut("case", 2, "case", "");
        add("switch_name", Kind.LOGIC).in("exec").out("default").data("selection", "string", "").growOut("case", 2, "case", "");
        add("switch_enum", Kind.LOGIC).in("exec").out("default").data("selection", "string", "").growOut("case", 2, "case", "");
        add("select", Kind.LOGIC).data("index", "integer", "0").grow(2, "any", "").value("result", "any");
        add("is_valid", Kind.LOGIC).data("value", "any", "").value("result", "boolean");

        /* Time: everything on the world's game ticks */
        add("retriggerable_delay", Kind.TIME).in("exec").out("then").data("ticks", "integer", "20");
        add("cooldown", Kind.TIME).in("exec").out("then", "blocked").data("ticks", "integer", "20");
        add("debounce", Kind.TIME).in("exec").out("then").data("ticks", "integer", "10");
        add("pulse", Kind.TIME).in("exec", "stop").out("tick").data("interval", "integer", "20").value("count", "integer");

        /* State and memory */
        add("schmitt_trigger", Kind.STATE).in("exec").out("on", "off").data("value", "float", "0").data("low", "float", "0.3").data("high", "float", "0.7").value("state", "boolean");
        add("edge_detector", Kind.STATE).in("exec").out("rising", "falling").data("value", "boolean", "false");
        add("state_machine", Kind.STATE).in("exec").data("target", "integer", "0").growOut("state", 2, null, "").value("current", "integer");
        add("queue", Kind.STATE).in("exec", "pop").out("then", "popped", "empty").data("value", "any", "").value("value", "any").value("size", "integer");

        /* Chance: seeded by the game tick and the node, so a take plays out the same each time */
        add("chance", Kind.RANDOM).in("exec").out("pass", "fail").data("percent", "float", "50");
        add("weighted_random", Kind.RANDOM).in("exec").growOut("out", 2, "weight", "1");
        add("shuffle_bag", Kind.RANDOM).in("exec").growOut("out", 2, null, "");

        /* Maths and signals */
        add("clamp", Kind.MATH).data("value", "float", "0").data("min", "float", "0").data("max", "float", "1").value("result", "float");
        add("map_range", Kind.MATH).data("value", "float", "0.5").data("in_min", "float", "0").data("in_max", "float", "1").data("out_min", "float", "0").data("out_max", "float", "10").param("clamp", "true").value("result", "float");
        add("threshold", Kind.MATH).data("value", "float", "0").data("threshold", "float", "0.5").value("result", "boolean");
        /* Text */
        add("append", Kind.TEXT).grow(2, "any", "").value("result", "string");
        add("format_text", Kind.TEXT).param("template", "{player} scored {points} points!").template().value("result", "string");

        /* Arrays: lists, kept as comma separated text */
        add("make_array", Kind.ARRAY).grow(2, "any", "").value("array", "array");
        add("get_array", Kind.ARRAY).data("array", "array", "a,b,c").data("index", "integer", "0").value("element", "any");
        add("set_array", Kind.ARRAY).in("exec").out("then").data("array", "array", "a,b,c").data("index", "integer", "0").data("value", "any", "").value("array", "array");

        add("counter", Kind.MATH).in("exec", "reset").out("then", "target_reached").data("step", "integer", "1").data("target", "integer", "10").value("count", "integer");

        /* Variables */
        add(GET_VARIABLE, Kind.VARIABLE).param("variable", "").value("value", "variable");
        add(SET_VARIABLE, Kind.VARIABLE).in("exec").out("then").param("variable", "").data("value", "variable", "0").value("value", "variable");
        add(ADD_VARIABLE, Kind.VARIABLE).in("exec").out("then").param("variable", "").data("amount", "float", "1");

        /* World and entity reads */
        add("entity_health", Kind.WORLD).data("entity", "entity", "@entity").value("result", "float");
        add("entity_position", Kind.WORLD).data("entity", "entity", "@entity").value("result", "vec3");
        add("entity_velocity", Kind.WORLD).data("entity", "entity", "@entity").value("result", "vec3");
        add("entity_name", Kind.WORLD).data("entity", "entity", "@entity").value("result", "string");
        add("is_player", Kind.WORLD).data("entity", "entity", "@entity").value("result", "boolean");
        add("has_tag", Kind.WORLD).data("entity", "entity", "@entity").data("tag", "string", "vip").value("result", "boolean");
        add("scoreboard", Kind.WORLD).data("entity", "entity", "@entity").data("objective", "string", "points").value("result", "integer");
        add("world_time", Kind.WORLD).value("result", "integer");
        add("is_raining", Kind.WORLD).value("result", "boolean");
        add("block_at", Kind.WORLD).data("position", "vec3", "0 0 0").value("result", "string");
        /* Where the box, an entity and a block are, how turned and how big: in the world, or relative to the box (FlowTransforms) */
        for (String part : TRANSFORM_PARTS)
        {
            transform(add("get_trigger_" + part, Kind.WORLD), part);
        }

        for (String part : TRANSFORM_PARTS)
        {
            transform(add("get_entity_" + part, Kind.WORLD).data("entity", "entity", "@entity"), part);
        }

        for (String part : TRANSFORM_PARTS)
        {
            transform(add("get_block_" + part, Kind.WORLD).data("position", "vec3", "~ ~-1 ~"), part);
        }

        add("players_in_radius", Kind.WORLD).data("position", "vec3", "~ ~ ~").data("radius", "float", "8").value("count", "integer").value("players", "string");

        /* Game actions: commands run as the entity, at operator level */
        add(RUN_COMMAND, Kind.ACTION).in("exec").out("then").param("command", "say {entity} entered the trigger");
        add(SEND_MESSAGE, Kind.ACTION).in("exec").out("then").data("text", "string", "Hello, {entity}!");
        add("teleport_entity", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("position", "vec3", "~ ~1 ~").command("tp {entity} {position}");
        add("damage_entity", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("amount", "float", "2");
        add("heal_entity", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("amount", "float", "2");
        add("launch_entity", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("velocity", "vec3", "0 1 0");
        add("give_item", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("item", "item", "minecraft:diamond").data("count", "integer", "1").command("give {entity} {item} {count}");
        add("remove_item", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("item", "item", "minecraft:diamond").data("count", "integer", "1").command("clear {entity} {item} {count}");
        add("add_effect", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("effect", "string", "minecraft:speed").data("seconds", "integer", "10").data("amplifier", "integer", "0").command("effect give {entity} {effect} {seconds} {amplifier}");
        add("remove_effect", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("effect", "string", "minecraft:speed").command("effect clear {entity} {effect}");
        add("spawn_entity", Kind.ACTION).in("exec").out("then").data("type", "entity_type", "minecraft:pig").data("position", "vec3", "~ ~ ~").command("summon {type} {position}");
        add("set_block", Kind.ACTION).in("exec").out("then").data("position", "vec3", "~ ~-1 ~").data("block", "block", "minecraft:gold_block").command("setblock {position} {block}");
        add("play_sound", Kind.ACTION).in("exec").out("then").data("sound", "sound", "minecraft:entity.experience_orb.pickup").data("position", "vec3", "~ ~ ~").data("volume", "float", "1").data("pitch", "float", "1").command("playsound {sound} master @a {position} {volume} {pitch}");
        add("spawn_particle", Kind.ACTION).in("exec").out("then").data("particle", "particle", "minecraft:happy_villager").data("position", "vec3", "~ ~1 ~").data("count", "integer", "10").command("particle {particle} {position} 0.3 0.3 0.3 0.05 {count}");
        add("show_title", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("text", "string", "Welcome").command("title {entity} title {text:json}");
        add("show_actionbar", Kind.ACTION).in("exec").out("then").data("entity", "entity", "@entity").data("text", "string", "You found it").command("title {entity} actionbar {text:json}");

        /* BBS: films, cameras, forms */
        add("play_film", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("film", "string", "my_film").param("camera", "true");
        add("stop_film", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("film", "string", "my_film");
        add("set_camera_clip", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("film", "string", "my_film");
        add("set_form", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("form", "string", "{id:\"bbs:model\",model:\"player/steve\"}");
        add("clear_form", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity");
        add("model_block_state", Kind.CINEMATIC).in("exec").out("then").data("position", "vec3", "0 0 0").data("state", "string", "idle");
        add("set_active_physics", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("film", "string", "").data("strength", "float", "1");
        add("reset_active_physics", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("film", "string", "");
        add("physics_stagger", Kind.CINEMATIC).in("exec").out("then").data("player", "entity", "@entity").data("film", "string", "").data("amount", "float", "0.6").data("knockdown", "boolean", "false");
        add("get_form", Kind.CINEMATIC).data("entity", "entity", "@entity").value("form_id", "string").value("form", "string");

        /* Debug */
        add(LOG, Kind.DEBUG).in("exec").out("then").data("message", "string", "{entity} triggered the box");

        /* Old */
        add(BRANCH, Kind.FLOW).in("exec").out("true", "false").param("variable", "").param("operator", "==").param("value", "0").hidden();
    }

    /** The switch between the world and the box's frame, and the outputs: one vec3, or all three for a transform. */
    private static void transform(FlowNodeType type, String part)
    {
        type.param("relative", "false");

        if (part.equals("transform"))
        {
            type.value("location", "vec3").value("rotation", "vec3").value("scale", "vec3");
        }
        else
        {
            type.value("result", "vec3");
        }
    }

    /** The family (trigger, entity, block) and the part a Get of a transform reads, or null for any other node. */
    public static String[] transformOf(String id)
    {
        for (String family : new String[] {"trigger", "entity", "block"})
        {
            String prefix = "get_" + family + "_";

            if (id.startsWith(prefix) && TRANSFORM_PARTS.contains(id.substring(prefix.length())))
            {
                return new String[] {family, id.substring(prefix.length())};
            }
        }

        return null;
    }

    private static FlowNodeType add(String id, Kind kind)
    {
        FlowNodeType type = new FlowNodeType(id, kind);

        TYPES.put(id, type);

        return type;
    }

    public static FlowNodeType get(String id)
    {
        return TYPES.get(id);
    }

    public static Collection<FlowNodeType> all()
    {
        return TYPES.values();
    }
}
