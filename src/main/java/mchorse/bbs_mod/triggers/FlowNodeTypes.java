package mchorse.bbs_mod.triggers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every node a trigger flow can hold, in the order the add menu lists them. */
public class FlowNodeTypes
{
    public static final String ON_ENTER = "on_enter";
    public static final String ON_EXIT = "on_exit";
    public static final String ON_STAY = "on_stay";
    public static final String ON_PLAYER_INTERACT = "on_player_interact";
    public static final String ON_PLAYER_HURT = "on_player_hurt";
    public static final String ON_BLOCK_BREAK = "on_block_break";

    public static final String RUN_COMMAND = "run_command";
    public static final String SEND_MESSAGE = "send_message";
    public static final String SET_VARIABLE = "set_variable";
    public static final String ADD_VARIABLE = "add_variable";
    public static final String BRANCH = "branch";
    public static final String WAIT = "wait";
    public static final String LOG = "log";

    private static final Map<String, FlowNodeType> TYPES = new LinkedHashMap<>();

    static
    {
        List<String> then = List.of("then");

        event(ON_ENTER);
        event(ON_EXIT);
        register(new FlowNodeType(ON_STAY, FlowNodeType.Kind.EVENT, then, List.of(new FlowNodeType.Param("interval", "20"))));
        event(ON_PLAYER_INTERACT);
        event(ON_PLAYER_HURT);
        event(ON_BLOCK_BREAK);

        register(new FlowNodeType(RUN_COMMAND, FlowNodeType.Kind.ACTION, then, List.of(new FlowNodeType.Param("command", "say {entity} entered the trigger"))));
        register(new FlowNodeType(SEND_MESSAGE, FlowNodeType.Kind.ACTION, then, List.of(new FlowNodeType.Param("text", "Hello, {entity}!"))));
        register(new FlowNodeType(SET_VARIABLE, FlowNodeType.Kind.VARIABLE, then, List.of(new FlowNodeType.Param("variable", ""), new FlowNodeType.Param("value", "0"))));
        register(new FlowNodeType(ADD_VARIABLE, FlowNodeType.Kind.VARIABLE, then, List.of(new FlowNodeType.Param("variable", ""), new FlowNodeType.Param("amount", "1"))));
        register(new FlowNodeType(BRANCH, FlowNodeType.Kind.LOGIC, List.of("true", "false"), List.of(new FlowNodeType.Param("variable", ""), new FlowNodeType.Param("operator", "=="), new FlowNodeType.Param("value", "0"))));
        register(new FlowNodeType(WAIT, FlowNodeType.Kind.LOGIC, then, List.of(new FlowNodeType.Param("ticks", "20"))));
        register(new FlowNodeType(LOG, FlowNodeType.Kind.DEBUG, then, List.of(new FlowNodeType.Param("message", "{entity} triggered the box"))));
    }

    /** The comparisons a branch can make, in the order its field cycles through them. */
    public static final List<String> OPERATORS = List.of("==", "!=", ">", "<", ">=", "<=");

    private static void event(String id)
    {
        register(new FlowNodeType(id, FlowNodeType.Kind.EVENT, List.of("then"), List.of()));
    }

    private static void register(FlowNodeType type)
    {
        TYPES.put(type.id, type);
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
