package mchorse.bbs_mod.triggers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a flow out as Java: a class with a field per variable and a method per event, each node of
 * a chain one statement, a branch an if, a wait a scheduled lambda. It is the same logic the box runs,
 * shown as code; the class is not compiled or loaded.
 */
public class FlowJavaGenerator
{
    private static final Map<String, String> EVENT_METHODS = new LinkedHashMap<>();

    static
    {
        EVENT_METHODS.put(FlowNodeTypes.ON_ENTER, "onEnter(Entity entity)");
        EVENT_METHODS.put(FlowNodeTypes.ON_EXIT, "onExit(Entity entity)");
        EVENT_METHODS.put(FlowNodeTypes.ON_STAY, "onStay(Entity entity, int ticksInside)");
        EVENT_METHODS.put(FlowNodeTypes.ON_PLAYER_INTERACT, "onPlayerInteract(Entity entity)");
        EVENT_METHODS.put(FlowNodeTypes.ON_PLAYER_HURT, "onPlayerHurt(Entity entity)");
        EVENT_METHODS.put(FlowNodeTypes.ON_BLOCK_BREAK, "onBlockBreak(Entity entity)");
    }

    private final FlowGraph graph;
    private final StringBuilder out = new StringBuilder();

    public static String generate(FlowGraph graph, String className)
    {
        return new FlowJavaGenerator(graph).write(className);
    }

    private FlowJavaGenerator(FlowGraph graph)
    {
        this.graph = graph;
    }

    private String write(String className)
    {
        this.line(0, "/* Generated from the trigger box flow. The box runs the flow itself;");
        this.line(0, " * this is the same logic written out as Java, read only. */");
        this.line(0, "package bbs.triggers;");
        this.line(0, "");
        this.line(0, "import net.minecraft.entity.Entity;");
        this.line(0, "import net.minecraft.util.math.Vec3d;");
        this.line(0, "");
        this.line(0, "public class " + className + " extends TriggerBoxScript");
        this.line(0, "{");

        boolean fields = false;

        for (FlowVariable variable : this.graph.variables)
        {
            this.line(1, "private " + javaType(variable.type) + " " + identifier(variable.name) + " = " + literal(variable.type, variable.value, false) + ";");
            fields = true;
        }

        boolean first = !fields;

        for (Map.Entry<String, String> event : EVENT_METHODS.entrySet())
        {
            List<FlowNode> nodes = new ArrayList<>();

            for (FlowNode node : this.graph.nodes)
            {
                if (node.type.equals(event.getKey()))
                {
                    nodes.add(node);
                }
            }

            if (nodes.isEmpty())
            {
                continue;
            }

            if (!first)
            {
                this.line(0, "");
            }

            first = false;

            this.line(1, "@Override");
            this.line(1, "public void " + event.getValue());
            this.line(1, "{");

            for (int i = 0; i < nodes.size(); i++)
            {
                FlowNode node = nodes.get(i);
                int indent = 2;

                if (i > 0)
                {
                    this.line(0, "");
                }

                if (nodes.size() > 1)
                {
                    this.line(2, "/* " + title(node) + " */");
                }

                if (node.type.equals(FlowNodeTypes.ON_STAY))
                {
                    int interval = Math.max(1, (int) FlowValues.parse(node.param("interval"), 20));

                    this.line(2, "if (ticksInside % " + interval + " == 0)");
                    this.line(2, "{");
                    indent = 3;
                }

                this.chain(node.id, "then", indent, new HashSet<>());

                if (node.type.equals(FlowNodeTypes.ON_STAY))
                {
                    this.line(2, "}");
                }
            }

            this.line(1, "}");
        }

        if (first)
        {
            this.line(1, "/* No events yet: add one in the Flow tab */");
        }

        this.line(0, "}");

        return this.out.toString();
    }

    private void chain(int from, String pin, int indent, Set<Integer> path)
    {
        FlowNode node = this.graph.next(from, pin);

        while (node != null)
        {
            if (!path.add(node.id))
            {
                this.line(indent, "/* Loops back to " + title(node) + " */");

                return;
            }

            switch (node.type)
            {
                case FlowNodeTypes.RUN_COMMAND -> this.line(indent, "this.runCommand(entity, " + this.template(node.param("command")) + ");");
                case FlowNodeTypes.SEND_MESSAGE -> this.line(indent, "this.sendMessage(entity, " + this.template(node.param("text")) + ");");
                case FlowNodeTypes.LOG -> this.line(indent, "this.log(" + this.template(node.param("message")) + ");");
                case FlowNodeTypes.SET_VARIABLE -> this.line(indent, this.assign(node.param("variable"), node.param("value")));
                case FlowNodeTypes.ADD_VARIABLE -> this.line(indent, this.increment(node.param("variable"), node.param("amount")));
                case FlowNodeTypes.BRANCH ->
                {
                    this.line(indent, "if (" + this.condition(node.param("variable"), node.param("operator"), node.param("value")) + ")");
                    this.line(indent, "{");
                    this.chain(node.id, "true", indent + 1, new HashSet<>(path));
                    this.line(indent, "}");

                    if (this.graph.next(node.id, "false") != null)
                    {
                        this.line(indent, "else");
                        this.line(indent, "{");
                        this.chain(node.id, "false", indent + 1, new HashSet<>(path));
                        this.line(indent, "}");
                    }

                    return;
                }
                case FlowNodeTypes.WAIT ->
                {
                    long ticks = Math.max(0, (long) FlowValues.parse(node.param("ticks"), 0));

                    this.line(indent, "this.schedule(" + ticks + ", () ->");
                    this.line(indent, "{");
                    this.chain(node.id, "then", indent + 1, path);
                    this.line(indent, "});");

                    return;
                }
                default -> this.line(indent, "/* " + title(node) + " */");
            }

            node = this.graph.next(node.id, "then");
        }
    }

    private String assign(String name, String value)
    {
        FlowVariable variable = this.graph.getVariable(name);

        if (variable == null)
        {
            return "/* Set: no variable named \"" + name + "\" */";
        }

        String expression = value.trim().equals("@entity") && variable.type.equals("entity") ? "entity" : this.typed(variable.type, value);

        return "this." + identifier(name) + " = " + expression + ";";
    }

    private String increment(String name, String amount)
    {
        FlowVariable variable = this.graph.getVariable(name);

        if (variable == null)
        {
            return "/* Add: no variable named \"" + name + "\" */";
        }

        String type = variable.type.equals("float") ? "float" : "integer";

        return "this." + identifier(name) + " += " + literal(type, amount, false) + ";";
    }

    private String condition(String name, String operator, String value)
    {
        FlowVariable variable = this.graph.getVariable(name);

        if (variable == null)
        {
            return "false /* no variable named \"" + name + "\" */";
        }

        String field = "this." + identifier(name);
        String other = this.typed(variable.type, value);

        return switch (variable.type)
        {
            case "integer", "float" -> field + " " + operator + " " + other;
            case "boolean" -> operator.equals("!=") ? field + " != " + other : field + " == " + other;
            case "string" -> operator.equals("==") ? field + ".equals(" + other + ")"
                : operator.equals("!=") ? "!" + field + ".equals(" + other + ")"
                : field + ".compareTo(" + other + ") " + operator + " 0";
            default -> operator.equals("!=") ? "!java.util.Objects.equals(" + field + ", " + other + ")" : "java.util.Objects.equals(" + field + ", " + other + ")";
        };
    }

    /** A value for a variable of the type: a plain literal, or text with {variables} when it is a string. */
    private String typed(String type, String value)
    {
        return type.equals("string") ? this.template(value) : literal(type, value, true);
    }

    /** Text with {name} placeholders as a Java string expression. */
    private String template(String raw)
    {
        List<String> parts = new ArrayList<>();
        int i = 0;

        while (i < raw.length())
        {
            int open = raw.indexOf('{', i);
            int close = open < 0 ? -1 : raw.indexOf('}', open);

            if (open < 0 || close < 0)
            {
                parts.add(quote(raw.substring(i)));

                break;
            }

            if (open > i)
            {
                parts.add(quote(raw.substring(i, open)));
            }

            String key = raw.substring(open + 1, close);

            parts.add(switch (key)
            {
                case "entity" -> "entity.getName().getString()";
                case "entity_uuid" -> "entity.getUuidAsString()";
                case "x" -> "entity.getX()";
                case "y" -> "entity.getY()";
                case "z" -> "entity.getZ()";
                default -> this.graph.getVariable(key) != null ? "this." + identifier(key) : quote("{" + key + "}");
            });

            i = close + 1;
        }

        if (parts.isEmpty())
        {
            return "\"\"";
        }

        if (!parts.get(0).startsWith("\""))
        {
            parts.add(0, "\"\"");
        }

        return String.join(" + ", parts);
    }

    private static String javaType(String type)
    {
        return switch (type)
        {
            case "boolean" -> "boolean";
            case "integer" -> "int";
            case "float" -> "float";
            case "vec3" -> "Vec3d";
            case "entity" -> "Entity";
            default -> "String";
        };
    }

    private static String literal(String type, String value, boolean inBody)
    {
        String v = value == null ? "" : value.trim();

        return switch (type)
        {
            case "boolean" -> String.valueOf(v.equals("true") || v.equals("1"));
            case "integer" -> String.valueOf((long) FlowValues.parse(v, 0));
            case "float" -> FlowValues.parse(v, 0) + "F";
            case "vec3" ->
            {
                String[] split = v.split("[\\s,]+");
                double x = split.length > 0 ? FlowValues.parse(split[0], 0) : 0;
                double y = split.length > 1 ? FlowValues.parse(split[1], 0) : 0;
                double z = split.length > 2 ? FlowValues.parse(split[2], 0) : 0;

                yield "new Vec3d(" + x + ", " + y + ", " + z + ")";
            }
            case "entity" -> "null";
            default -> quote(v);
        };
    }

    private static String quote(String text)
    {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A variable name as a Java field name: letters, digits and underscores, never starting with a digit. */
    public static String identifier(String name)
    {
        StringBuilder builder = new StringBuilder();

        for (char c : name.toCharArray())
        {
            builder.append(Character.isLetterOrDigit(c) && c < 128 ? c : '_');
        }

        if (builder.isEmpty() || Character.isDigit(builder.charAt(0)))
        {
            builder.insert(0, '_');
        }

        return builder.toString();
    }

    private static String title(FlowNode node)
    {
        return node.type + " #" + node.id;
    }

    private void line(int indent, String text)
    {
        this.out.append("    ".repeat(indent)).append(text).append('\n');
    }
}
