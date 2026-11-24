package mchorse.bbs_mod.triggers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a flow out as Java: a field per local variable, a method per event, and each chain as
 * statements. Control flow becomes Java's own: If / Else an {@code if}, the loops {@code for}
 * loops, the switches {@code switch} statements, Sequence its blocks one after another, the delays
 * scheduled lambdas. A node that remembers something (a gate, a counter, a bag) becomes a call to
 * a helper named after it, with the node's id, whose answer picks the way on: {@code switch
 * (this.flipFlop(4)) { case "a" -> ... }}. Data pins become expressions where they are read.
 *
 * <p>It is the same logic the box runs, shown as code; the class is not compiled or loaded. Its
 * helpers ({@code runCommand}, {@code teleportEntity}, {@code number}...) belong to the base class
 * it names.</p>
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
        this.line(0, "import mchorse.bbs_mod.triggers.FlowBBS;");
        this.line(0, "import net.minecraft.entity.Entity;");
        this.line(0, "import net.minecraft.util.math.Vec3d;");
        this.line(0, "");
        this.line(0, "public class " + className + " extends TriggerBoxScript");
        this.line(0, "{");

        boolean fields = false;

        for (FlowVariable variable : this.graph.variables)
        {
            String array = variable.isArray ? " /* list */" : "";

            if (variable.scope.equals("local") && variable.isArray)
            {
                this.line(1, "private java.util.List<String> " + identifier(variable.name) + " = " + listLiteral(variable.value) + "; /* list of " + variable.type + " */");
            }
            else if (variable.scope.equals("local"))
            {
                this.line(1, "private " + javaType(variable.type) + " " + identifier(variable.name) + " = " + literal(variable.type, variable.value) + ";" + array);
            }
            else
            {
                this.line(1, "/* " + variable.name + ": " + variable.scope + " " + variable.type + ", kept by the world, starts as " + quote(variable.value) + array + " */");
            }

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
                    this.line(2, "if (ticksInside % " + Math.max(1, (int) FlowValues.parse(node.param("interval"), 20)) + " == 0)");
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

    /* Statements */

    private void chain(int from, String pin, int indent, Set<String> path)
    {
        FlowLink link = this.graph.execFrom(from, pin);

        if (link != null)
        {
            this.enter(this.graph.get(link.to()), link.toPin(), indent, path);
        }
    }

    /** A block of its own: a fresh copy of the path, so sibling branches may reach the same node. */
    private void block(int from, String pin, int indent, Set<String> path)
    {
        this.chain(from, pin, indent, new HashSet<>(path));
    }

    private void enter(FlowNode node, String in, int indent, Set<String> path)
    {
        if (node == null || node.getType() == null)
        {
            return;
        }

        if (!path.add(node.id + in))
        {
            this.line(indent, "/* Loops back to " + title(node) + " */");

            return;
        }

        String id = String.valueOf(node.id);

        switch (node.type)
        {
            case FlowNodeTypes.RUN_COMMAND -> this.then(node, "this.runCommand(entity, " + this.template(node.param("command")) + ");", indent, path);
            case FlowNodeTypes.SET_VARIABLE -> this.then(node, this.assign(node), indent, path);
            case FlowNodeTypes.ADD_VARIABLE -> this.then(node, this.increment(node), indent, path);
            case FlowNodeTypes.IF_ELSE -> this.ifElse(node, this.logicOf("and", node, 0), "true", "false", indent, path);
            case FlowNodeTypes.BRANCH -> this.ifElse(node, this.oldCondition(node), "true", "false", indent, path);
            case "sequence" ->
            {
                for (String pin : node.execOutputs())
                {
                    this.line(indent, "/* " + pin + " */");
                    this.block(node.id, pin, indent, path);
                }
            }
            case FlowNodeTypes.WAIT, "retriggerable_delay" ->
            {
                String method = node.type.equals(FlowNodeTypes.WAIT) ? "schedule" : "scheduleOnce";
                String key = node.type.equals(FlowNodeTypes.WAIT) ? "" : id + ", ";

                this.line(indent, "this." + method + "(" + key + this.number(node, "ticks") + ", () ->");
                this.line(indent, "{");
                this.chain(node.id, "then", indent + 1, path);
                this.line(indent, "});");
            }
            case "pulse" ->
            {
                if (in.equals("exec:stop"))
                {
                    this.line(indent, "this.pulseStop(" + id + ");");
                }
                else
                {
                    this.line(indent, "this.pulse(" + id + ", " + this.number(node, "interval") + ", () ->");
                    this.line(indent, "{");
                    this.chain(node.id, "tick", indent + 1, path);
                    this.line(indent, "});");
                }
            }
            case "for_loop", "for_loop_break" ->
            {
                if (in.equals("exec:break"))
                {
                    this.line(indent, "this.breakLoop(" + id + ");");

                    return;
                }

                String i = "i" + id;

                this.line(indent, "for (int " + i + " = (int) " + this.number(node, "first") + "; " + i + " <= " + this.number(node, "last") + "; " + i + "++)");
                this.loopBody(node, i, indent, path, node.type.equals("for_loop_break"));
            }
            case "while_loop" ->
            {
                String n = "n" + id;
                int max = Math.max(1, (int) FlowValues.parse(node.param("max_iterations"), 1000));

                this.line(indent, "for (int " + n + " = 0; " + n + " < " + max + " && " + bool(this.input(node, "condition")) + "; " + n + "++)");
                this.line(indent, "{");
                this.block(node.id, "body", indent + 1, path);
                this.line(indent, "}");
                this.chain(node.id, "completed", indent, path);
            }
            case "for_each", "for_each_break", "reverse_for_each" ->
            {
                if (in.equals("exec:break"))
                {
                    this.line(indent, "this.breakLoop(" + id + ");");

                    return;
                }

                String list = "this.list(" + this.input(node, "array").code() + ")";

                if (node.type.equals("reverse_for_each"))
                {
                    list = "this.reversed(" + list + ")";
                }

                this.line(indent, "for (String element" + id + " : " + list + ")");
                this.loopBody(node, "element" + id, indent, path, node.type.equals("for_each_break"));
            }
            case "switch_int" -> this.switchInt(node, indent, path);
            case "switch_string", "switch_name", "switch_enum" -> this.switchString(node, indent, path);
            case "counter" ->
            {
                if (in.equals("exec:reset"))
                {
                    this.line(indent, "this.counterReset(" + id + ");");

                    return;
                }

                this.line(indent, "boolean reached" + id + " = this.counter(" + id + ", " + this.number(node, "step") + ", " + this.number(node, "target") + ");");
                this.block(node.id, "then", indent, path);
                this.line(indent, "if (reached" + id + ")");
                this.line(indent, "{");
                this.block(node.id, "target_reached", indent + 1, path);
                this.line(indent, "}");
            }
            default -> this.generic(node, in, indent, path);
        }
    }

    /** A plain step: one statement, then on along its "then" wire. */
    private void then(FlowNode node, String statement, int indent, Set<String> path)
    {
        this.line(indent, statement);
        this.chain(node.id, "then", indent, path);
    }

    private void loopBody(FlowNode node, String variable, int indent, Set<String> path, boolean breakable)
    {
        this.line(indent, "{");

        if (breakable)
        {
            this.line(indent + 1, "if (this.isBroken(" + node.id + "))");
            this.line(indent + 1, "{");
            this.line(indent + 2, "break;");
            this.line(indent + 1, "}");
            this.line(0, "");
        }

        this.line(indent + 1, "this.store(" + node.id + ", \"" + (variable.startsWith("element") ? "element" : "index") + "\", " + variable + ");");
        this.block(node.id, "body", indent + 1, path);
        this.line(indent, "}");
        this.line(0, "");
        this.chain(node.id, "completed", indent, path);
    }

    private void ifElse(FlowNode node, String condition, String yes, String no, int indent, Set<String> path)
    {
        this.line(indent, "if (" + condition + ")");
        this.line(indent, "{");
        this.block(node.id, yes, indent + 1, path);
        this.line(indent, "}");

        if (this.graph.execFrom(node.id, no) != null)
        {
            this.line(indent, "else");
            this.line(indent, "{");
            this.block(node.id, no, indent + 1, path);
            this.line(indent, "}");
        }
    }

    private void switchInt(FlowNode node, int indent, Set<String> path)
    {
        int start = (int) FlowValues.parse(node.param("start"), 0);

        this.line(indent, "switch ((int) " + this.number(node, "selection") + ")");
        this.line(indent, "{");

        for (int i = 0; i < node.outputCount(); i++)
        {
            this.caseBlock("case " + (start + i) + " ->", node, "case" + i, indent + 1, path);
        }

        this.caseBlock("default ->", node, "default", indent + 1, path);
        this.line(indent, "}");
    }

    private void switchString(FlowNode node, int indent, Set<String> path)
    {
        Set<String> seen = new HashSet<>();

        this.line(indent, "switch (String.valueOf(" + this.input(node, "selection").code() + "))");
        this.line(indent, "{");

        for (int i = 0; i < node.outputCount(); i++)
        {
            String value = node.param("case" + i);

            /* Two cases with the same value: only the first can ever be reached */
            if (seen.add(value))
            {
                this.caseBlock("case " + quote(value) + " ->", node, "case" + i, indent + 1, path);
            }
        }

        this.caseBlock("default ->", node, "default", indent + 1, path);
        this.line(indent, "}");
    }

    private void caseBlock(String label, FlowNode node, String pin, int indent, Set<String> path)
    {
        this.line(indent, label);
        this.line(indent, "{");
        this.block(node.id, pin, indent + 1, path);
        this.line(indent, "}");
    }

    /**
     * Everything else: a call to a helper named after the node. A side input (Reset, Open, Pop...)
     * calls the helper of that input. A node with one way on goes on when its helper says so, a node
     * with several picks the way by the name its helper gives back.
     */
    private void generic(FlowNode node, String in, int indent, Set<String> path)
    {
        FlowNodeType type = node.getType();
        List<String> args = new ArrayList<>();
        boolean routed = !type.kind.equals(FlowNodeType.Kind.ACTION) && !type.kind.equals(FlowNodeType.Kind.CINEMATIC) && !type.kind.equals(FlowNodeType.Kind.DEBUG);
        String method = camel(type.id);

        if (!in.equals(FlowNodeType.EXEC))
        {
            method += capital(camel(in.substring(FlowNodeType.EXEC.length() + 1)));
        }

        if (routed)
        {
            args.add(String.valueOf(node.id));
        }
        else if (type.id.equals(FlowNodeTypes.SEND_MESSAGE))
        {
            args.add("entity");
        }

        for (String pin : node.dataInputs())
        {
            args.add(this.input(node, pin).code());
        }

        if (type.kind == FlowNodeType.Kind.CINEMATIC)
        {
            List<String> bbs = new ArrayList<>();

            bbs.add("entity");

            for (String pin : node.dataInputs())
            {
                bbs.add(this.input(node, pin).code());
            }

            if (type.id.equals("play_film"))
            {
                bbs.add(String.valueOf(FlowValues.truthy(node.param("camera"))));
            }

            this.then(node, "FlowBBS." + camel(type.id) + "(" + String.join(", ", bbs) + ");", indent, path);

            return;
        }

        String call = "this." + method + "(" + String.join(", ", args) + ")";
        List<String> outputs = node.execOutputs();

        if (!in.equals(FlowNodeType.EXEC) && !node.type.equals("queue"))
        {
            this.line(indent, call + ";");

            return;
        }

        if (!routed || outputs.size() <= 1 && outputs.contains("then"))
        {
            this.then(node, call + ";", indent, path);

            return;
        }

        if (outputs.size() == 1)
        {
            this.line(indent, "if (\"" + outputs.get(0) + "\".equals(" + call + "))");
            this.line(indent, "{");
            this.block(node.id, outputs.get(0), indent + 1, path);
            this.line(indent, "}");

            return;
        }

        this.line(indent, "switch (String.valueOf(" + call + "))");
        this.line(indent, "{");

        for (String pin : outputs)
        {
            this.caseBlock("case " + quote(pin) + " ->", node, pin, indent + 1, path);
        }

        this.line(indent + 1, "default -> {}");
        this.line(indent, "}");
    }

    private String assign(FlowNode node)
    {
        FlowVariable variable = this.graph.variableOf(node);

        if (variable == null)
        {
            return "/* Set: its variable was deleted */";
        }

        Expr value = this.input(node, "value");

        if (variable.isArray && variable.scope.equals("local"))
        {
            return "this." + identifier(variable.name) + " = new java.util.ArrayList<>(this.list(" + value.code() + "));";
        }

        String code = variable.type.equals("string") && !value.type().equals("string") ? "String.valueOf(" + value.code() + ")" : value.code();

        if (variable.type.equals("integer") && value.type().equals("number"))
        {
            code = "(int) " + code;
        }
        else if (variable.type.equals("float") && value.type().equals("number"))
        {
            code = "(float) " + code;
        }

        return switch (variable.scope)
        {
            case "global" -> "this.setGlobal(" + quote(variable.name) + ", " + code + ");";
            case "entity" -> "this.setOnEntity(entity, " + quote(variable.name) + ", " + code + ");";
            default -> "this." + identifier(variable.name) + " = " + code + ";";
        };
    }

    private String increment(FlowNode node)
    {
        FlowVariable variable = this.graph.variableOf(node);

        if (variable == null)
        {
            return "/* Add: its variable was deleted */";
        }

        if (variable.isArray && variable.scope.equals("local"))
        {
            return "this." + identifier(variable.name) + ".add(String.valueOf(" + this.input(node, "amount").code() + "));";
        }

        String amount = this.number(node, "amount");

        return switch (variable.scope)
        {
            case "global" -> "this.addGlobal(" + quote(variable.name) + ", " + amount + ");";
            case "entity" -> "this.addOnEntity(entity, " + quote(variable.name) + ", " + amount + ");";
            default -> "this." + identifier(variable.name) + " += " + (variable.type.equals("integer") ? "(int) " : "(float) ") + amount + ";";
        };
    }

    private String oldCondition(FlowNode node)
    {
        FlowVariable variable = this.graph.variableOf(node);

        if (variable == null)
        {
            return "false /* its variable was deleted */";
        }

        return "this.compare(" + this.readVariable(variable).code() + ", " + quote(node.param("operator")) + ", " + quote(node.param("value")) + ")";
    }

    /* Data */

    private record Expr(String code, String type) {}

    private static String simple(String type)
    {
        return switch (type)
        {
            case "integer", "float", "number" -> "number";
            case "boolean" -> "boolean";
            case "string", "block", "item", "sound", "color" -> "string";
            default -> "any";
        };
    }

    /** A data input as an expression: the wire into it, or its typed-in value as a literal. */
    private Expr input(FlowNode node, String pin)
    {
        return this.input(node, pin, 0);
    }

    private Expr input(FlowNode node, String pin, int depth)
    {
        FlowLink link = this.graph.dataInto(node.id, pin);

        if (link != null)
        {
            return this.expr(this.graph.get(link.from()), link.pin(), depth + 1);
        }

        String raw = node.param(pin);
        String declared = node.declaredInputType(pin);
        String t = raw.trim();

        if (declared.equals("entity") && (t.isEmpty() || t.equals("@entity")))
        {
            return new Expr("entity", "any");
        }

        if (t.equals("true") || t.equals("false"))
        {
            return new Expr(t, "boolean");
        }

        if (declared.equals("vec3") && !t.contains("~"))
        {
            double[] v = FlowValues.vec(t);

            return new Expr("new Vec3d(" + v[0] + ", " + v[1] + ", " + v[2] + ")", "any");
        }

        try
        {
            return new Expr(FlowValues.format(Double.parseDouble(t)), "number");
        }
        catch (NumberFormatException e)
        {
            return new Expr(this.template(raw), "string");
        }
    }

    private String number(FlowNode node, String pin)
    {
        Expr e = this.input(node, pin);

        return e.type().equals("number") ? e.code() : "this.toNumber(" + e.code() + ")";
    }

    private Expr readVariable(FlowVariable variable)
    {
        String type = simple(variable.type);

        return switch (variable.scope)
        {
            case "global" -> new Expr("this.getGlobal(" + quote(variable.name) + ")", "any");
            case "entity" -> new Expr("this.getOnEntity(entity, " + quote(variable.name) + ")", "any");
            default -> new Expr("this." + identifier(variable.name), variable.isArray ? "any" : type);
        };
    }

    private Expr expr(FlowNode node, String pin, int depth)
    {
        if (node == null || node.getType() == null || depth > 32)
        {
            return new Expr("null", "any");
        }

        FlowNodeType type = node.getType();
        String operator = FlowNodeTypes.OPERATOR_OF.get(node.type);
        String logic = FlowNodeTypes.BOOLEAN_OF.get(node.type);

        if (logic != null)
        {
            return new Expr(this.logicOf(logic, node, depth), "boolean");
        }

        if (operator != null)
        {
            return new Expr(this.compareOf(node, operator, depth), "boolean");
        }

        switch (node.type)
        {
            case FlowNodeTypes.GET_VARIABLE, FlowNodeTypes.SET_VARIABLE ->
            {
                FlowVariable variable = this.graph.variableOf(node);

                return variable == null ? new Expr("null /* deleted variable */", "any") : this.readVariable(variable);
            }
            case "not" ->
            {
                return new Expr("!" + paren(bool(this.input(node, "in0", depth))), "boolean");
            }
            case "clamp" ->
            {
                return new Expr("Math.max(" + this.number(node, "min") + ", Math.min(" + this.number(node, "max") + ", " + this.number(node, "value") + "))", "number");
            }
            case "append" ->
            {
                List<String> parts = new ArrayList<>();

                for (int i = 0; i < node.inputCount(); i++)
                {
                    parts.add("String.valueOf(" + this.input(node, FlowNodeType.input(i), depth).code() + ")");
                }

                return new Expr(parts.isEmpty() ? "\"\"" : String.join(" + ", parts), "string");
            }
            case "format_text" ->
            {
                return new Expr(this.format(node, depth), "string");
            }
            case "get_form" ->
            {
                return new Expr("FlowBBS." + (pin.equals("form_id") ? "formId" : "formData") + "(entity, " + this.input(node, "entity", depth).code() + ")", "string");
            }
            case "threshold" ->
            {
                return new Expr(this.number(node, "value") + " >= " + this.number(node, "threshold"), "boolean");
            }
            case "select" ->
            {
                List<String> args = new ArrayList<>();

                args.add("(int) " + this.number(node, "index"));

                for (int i = 0; i < node.inputCount(); i++)
                {
                    args.add(this.input(node, FlowNodeType.input(i), depth).code());
                }

                return new Expr("this.select(" + String.join(", ", args) + ")", "any");
            }
            default -> {}
        }

        /* A node with execution pins only remembers its values: a loop's index, a counter's count */
        String outputType = simple(this.graph.outputType(node, pin));

        if (!type.pure())
        {
            String getter = switch (outputType)
            {
                case "number" -> "number";
                case "boolean" -> "bool";
                case "string" -> "text";
                default -> "value";
            };

            return new Expr("this." + getter + "(" + node.id + ", " + quote(pin) + ")", outputType);
        }

        /* A read of the world: a helper named after the node, and after the output when it has several */
        List<String> args = new ArrayList<>();

        for (String input : node.dataInputs())
        {
            args.add(this.input(node, input, depth).code());
        }

        if (node.type.equals("map_range"))
        {
            args.add(String.valueOf(FlowValues.truthy(node.param("clamp"))));
        }

        String method = camel(type.id) + (type.dataOutputs.size() > 1 ? capital(camel(pin)) : "");

        return new Expr("this." + method + "(" + String.join(", ", args) + ")", outputType);
    }

    /** Format Text: the template as a string expression, each {name} the value of its pin. */
    private String format(FlowNode node, int depth)
    {
        String template = node.param("template");
        List<String> parts = new ArrayList<>();
        int i = 0;

        while (i < template.length())
        {
            int open = template.indexOf('{', i);
            int close = open < 0 ? -1 : template.indexOf('}', open);

            if (open < 0 || close < 0)
            {
                parts.add(quote(template.substring(i)));

                break;
            }

            String name = template.substring(open + 1, close).trim();

            if (open > i)
            {
                parts.add(quote(template.substring(i, open)));
            }

            parts.add(FlowNode.templateNames(template).contains(name) ? "String.valueOf(" + this.input(node, FlowNodeType.templatePin(name), depth).code() + ")" : quote("{" + name + "}"));
            i = close + 1;
        }

        return parts.isEmpty() ? "\"\"" : String.join(" + ", parts);
    }

    private static String bool(Expr e)
    {
        return e.type().equals("boolean") ? e.code() : "this.isTrue(" + e.code() + ")";
    }

    private static String paren(String code)
    {
        return code.contains(" ") ? "(" + code + ")" : code;
    }

    /** A Boolean operator over the grown inputs. */
    private String logicOf(String operator, FlowNode node, int depth)
    {
        List<String> parts = new ArrayList<>();

        for (int i = 0; i < node.inputCount(); i++)
        {
            parts.add(paren(bool(this.input(node, FlowNodeType.input(i), depth))));
        }

        if (parts.isEmpty())
        {
            return "true";
        }

        return switch (operator)
        {
            case "or" -> String.join(" || ", parts);
            case "nand" -> "!(" + String.join(" && ", parts) + ")";
            case "nor" -> "!(" + String.join(" || ", parts) + ")";
            case "xor" -> String.join(" ^ ", parts);
            case "xnor" -> "!(" + String.join(" ^ ", parts) + ")";
            default -> String.join(" && ", parts);
        };
    }

    /** Each input against the next: a < b < c reads as a < b && b < c. */
    private String compareOf(FlowNode node, String operator, int depth)
    {
        List<Expr> values = new ArrayList<>();
        List<String> pairs = new ArrayList<>();

        for (int i = 0; i < node.inputCount(); i++)
        {
            values.add(this.input(node, FlowNodeType.input(i), depth));
        }

        for (int i = 1; i < values.size(); i++)
        {
            Expr a = values.get(i - 1);
            Expr b = values.get(i);
            boolean equality = operator.equals("==") || operator.equals("!=");
            String code;

            if ((a.type().equals("number") && b.type().equals("number")) || (equality && a.type().equals("boolean") && b.type().equals("boolean")))
            {
                code = a.code() + " " + operator + " " + b.code();
            }
            else if (equality)
            {
                code = (operator.equals("!=") ? "!" : "") + "java.util.Objects.equals(" + a.code() + ", " + b.code() + ")";
            }
            else
            {
                code = "String.valueOf(" + a.code() + ").compareTo(String.valueOf(" + b.code() + ")) " + operator + " 0";
            }

            pairs.add(values.size() > 2 ? "(" + code + ")" : code);
        }

        return pairs.isEmpty() ? "true" : String.join(" && ", pairs);
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
            FlowVariable variable = this.graph.getVariable(key);

            parts.add(switch (key)
            {
                case "entity" -> "entity.getName().getString()";
                case "entity_uuid" -> "entity.getUuidAsString()";
                case "x" -> "entity.getX()";
                case "y" -> "entity.getY()";
                case "z" -> "entity.getZ()";
                default -> variable != null ? this.readVariable(variable).code() : quote("{" + key + "}");
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

    /* Names and literals */

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

    private static String literal(String type, String value)
    {
        String v = value == null ? "" : value.trim();

        return switch (type)
        {
            case "boolean" -> String.valueOf(v.equals("true") || v.equals("1"));
            case "integer" -> String.valueOf((long) FlowValues.parse(v, 0));
            case "float" -> FlowValues.parse(v, 0) + "F";
            case "vec3" ->
            {
                double[] xyz = FlowValues.vec(v);

                yield "new Vec3d(" + xyz[0] + ", " + xyz[1] + ", " + xyz[2] + ")";
            }
            case "entity" -> "null";
            default -> quote(v);
        };
    }

    /** A comma separated list as a Java list of text. */
    private static String listLiteral(String value)
    {
        List<String> parts = new ArrayList<>();

        for (String item : FlowValues.list(value))
        {
            parts.add(quote(item));
        }

        return "new java.util.ArrayList<>(java.util.List.of(" + String.join(", ", parts) + "))";
    }

    private static String quote(String text)
    {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** snake_case to camelCase: teleport_entity, teleportEntity. */
    public static String camel(String id)
    {
        StringBuilder builder = new StringBuilder();
        boolean up = false;

        for (char c : id.toCharArray())
        {
            if (c == '_' || c == ':')
            {
                up = true;
            }
            else
            {
                builder.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }

        return builder.toString();
    }

    private static String capital(String text)
    {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
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
