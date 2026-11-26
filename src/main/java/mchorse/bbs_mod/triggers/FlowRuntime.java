package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Runs a trigger box's flow on the server.
 *
 * <p>An event walks the execution wires from its node. Every node that remembers something (a
 * gate, a counter, a shuffle bag, a pulse) keeps it in {@link #state} under its id. Waits and pulses
 * park on the world's game time and resume in {@link #tick}; chance draws are seeded by the game time,
 * the node and the box, so a replayed take rolls the same. Data pins are worked out when asked, from
 * the current variables. A run may take at most {@link #MAX_STEPS} steps and a loop at most
 * {@link #MAX_LOOP} turns, so a wired loop can never hang the server.</p>
 */
public class FlowRuntime
{
    public static final int MAX_STEPS = 20000;
    public static final int MAX_LOOP = 10000;

    private record Pending(int node, String pin, UUID entity, long due) {}

    /** One run: the world, the entity that set it off, and its step budget. */
    private static class Run
    {
        final ServerWorld world;
        final Entity entity;
        int steps;

        Run(ServerWorld world, Entity entity)
        {
            this.world = world;
            this.entity = entity;
        }
    }

    /** Local variables by id, as text. */
    public final Map<String, String> values = new HashMap<>();
    private final Map<Integer, Map<String, Object>> state = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();

    private FlowGraph graph;
    private BlockPos pos = BlockPos.ORIGIN;

    public void reset(FlowGraph graph, BlockPos pos)
    {
        this.graph = graph;
        this.pos = pos;
        this.values.clear();
        this.state.clear();
        this.pending.clear();

        for (FlowVariable variable : graph.variables)
        {
            this.values.put(variable.id, variable.value);
        }
    }

    public boolean hasEvent(String type)
    {
        if (this.graph == null)
        {
            return false;
        }

        for (FlowNode node : this.graph.nodes)
        {
            if (node.type.equals(type))
            {
                return true;
            }
        }

        return false;
    }

    /** Fires every event node of the type. {@code stayTicks} is how long the entity has been inside, for OnStay. */
    public void fire(ServerWorld world, String type, Entity entity, int stayTicks)
    {
        if (this.graph == null)
        {
            return;
        }

        for (FlowNode node : new ArrayList<>(this.graph.nodes))
        {
            if (!node.type.equals(type))
            {
                continue;
            }

            if (type.equals(FlowNodeTypes.ON_STAY))
            {
                int interval = Math.max(1, (int) parse(node.param("interval"), 20));

                if (stayTicks <= 0 || stayTicks % interval != 0)
                {
                    continue;
                }
            }

            this.out(new Run(world, entity), node, "then");
        }
    }

    /** Resumes waits whose time has come and ticks the running pulses, on the world's game time. */
    public void tick(ServerWorld world)
    {
        if (this.graph == null)
        {
            return;
        }

        long time = world.getTime();

        if (!this.pending.isEmpty())
        {
            List<Pending> due = new ArrayList<>();

            this.pending.removeIf((p) ->
            {
                if (p.due <= time)
                {
                    due.add(p);

                    return true;
                }

                return false;
            });

            for (Pending p : due)
            {
                FlowNode node = this.graph.get(p.node);

                if (node != null)
                {
                    this.out(new Run(world, p.entity == null ? null : this.source(world, p.entity)), node, p.pin);
                }
            }
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : new ArrayList<>(this.state.entrySet()))
        {
            Map<String, Object> s = entry.getValue();

            if (!Boolean.TRUE.equals(s.get("running")))
            {
                continue;
            }

            long next = (Long) s.get("next");

            if (time < next)
            {
                continue;
            }

            FlowNode node = this.graph.get(entry.getKey());

            if (node == null)
            {
                s.put("running", false);

                continue;
            }

            UUID uuid = (UUID) s.get("entity");

            s.put("next", next + Math.max(1L, (Long) s.get("interval")));
            s.put("count", (Integer) s.getOrDefault("count", 0) + 1);
            this.out(new Run(world, uuid == null ? null : this.source(world, uuid)), node, "tick");
        }
    }

    /* Walking the wires */

    private void out(Run run, FlowNode node, String pin)
    {
        FlowLink link = this.graph.execFrom(node.id, pin);

        if (link != null)
        {
            this.enter(run, this.graph.get(link.to()), link.toPin());
        }
    }

    private Map<String, Object> state(FlowNode node)
    {
        return this.state.computeIfAbsent(node.id, (id) -> new HashMap<>());
    }

    private void enter(Run run, FlowNode node, String in)
    {
        if (node == null || ++run.steps > MAX_STEPS)
        {
            return;
        }

        FlowBBS.use(run.world.getServer());

        FlowNodeType type = node.getType();

        if (type == null)
        {
            return;
        }

        Map<String, Object> s = this.state(node);
        long now = run.world.getTime();

        switch (node.type)
        {
            case FlowNodeTypes.RUN_COMMAND ->
            {
                this.command(run, this.text(node.param("command"), run.entity));
                this.out(run, node, "then");
            }
            case FlowNodeTypes.SEND_MESSAGE ->
            {
                if (run.entity instanceof ServerPlayerEntity player)
                {
                    player.sendMessage(Text.literal(this.input(run, node, "text", 0)));
                }

                this.out(run, node, "then");
            }
            case FlowNodeTypes.LOG ->
            {
                BBSMod.LOGGER.info("[Trigger box {}] {}", this.pos.toShortString(), this.input(run, node, "message", 0));
                this.out(run, node, "then");
            }
            case FlowNodeTypes.SET_VARIABLE ->
            {
                FlowVariable variable = this.graph.variableOf(node);
                String value = this.asValue(run, this.input(run, node, "value", 0), variable == null ? "string" : variable.type);

                if (variable != null)
                {
                    this.write(run, variable, value);
                }

                s.put("value", value);
                this.out(run, node, "then");
            }
            case FlowNodeTypes.ADD_VARIABLE ->
            {
                FlowVariable variable = this.graph.variableOf(node);

                if (variable != null && variable.isArray)
                {
                    /* On a list, Add puts the amount at its end, as a new item */
                    String list = this.read(run, variable);
                    String item = this.input(run, node, "amount", 0).replace(",", " ");

                    this.write(run, variable, list.isBlank() ? item : list + "," + item);
                }
                else if (variable != null)
                {
                    double sum = parse(this.read(run, variable), 0) + parse(this.input(run, node, "amount", 0), 0);

                    this.write(run, variable, variable.type.equals("integer") ? String.valueOf((long) sum) : String.valueOf(sum));
                }

                this.out(run, node, "then");
            }
            case "set_array" ->
            {
                /* The list with the item replaced; a list read from a list variable is changed in place */
                String list = FlowValues.setItem(this.input(run, node, "array", 0), (int) parse(this.input(run, node, "index", 0), 0), this.input(run, node, "value", 0), MAX_LOOP);
                FlowVariable variable = this.graph.arrayVariableInto(node, "array");

                if (variable != null)
                {
                    this.write(run, variable, list);
                }

                s.put("array", list);
                this.out(run, node, "then");
            }
            case FlowNodeTypes.IF_ELSE -> this.out(run, node, FlowValues.logic("and", this.grown(run, node, 0)) ? "true" : "false");
            case FlowNodeTypes.BRANCH ->
            {
                FlowVariable variable = this.graph.variableOf(node);
                String value = variable == null ? "" : this.read(run, variable);

                this.out(run, node, FlowValues.compare(value, node.param("operator"), this.text(node.param("value"), run.entity)) ? "true" : "false");
            }
            case "sequence" ->
            {
                for (String pin : node.execOutputs())
                {
                    this.out(run, node, pin);
                }
            }
            case "flip_flop" ->
            {
                boolean a = !Boolean.TRUE.equals(s.get("is_a"));

                s.put("is_a", a);
                this.out(run, node, a ? "a" : "b");
            }
            case "do_once" ->
            {
                boolean closed = (Boolean) s.computeIfAbsent("closed", (k) -> FlowValues.truthy(node.param("start_closed")));

                if (in.equals("exec:reset"))
                {
                    s.put("closed", false);
                }
                else if (!closed)
                {
                    s.put("closed", true);
                    this.out(run, node, "completed");
                }
            }
            case "do_n" ->
            {
                int counter = (Integer) s.getOrDefault("counter", 0);

                if (in.equals("exec:reset"))
                {
                    s.put("counter", 0);
                }
                else if (counter < (int) parse(this.input(run, node, "n", 0), 0))
                {
                    s.put("counter", counter + 1);
                    this.out(run, node, "exit");
                }
            }
            case "gate" ->
            {
                boolean open = (Boolean) s.computeIfAbsent("open", (k) -> FlowValues.truthy(node.param("start_open")));

                switch (in)
                {
                    case "exec:open" -> s.put("open", true);
                    case "exec:close" -> s.put("open", false);
                    case "exec:toggle" -> s.put("open", !open);
                    default ->
                    {
                        if (open)
                        {
                            this.out(run, node, "exit");
                        }
                    }
                }
            }
            case "multi_gate" -> this.multiGate(run, node, s, in);
            case "for_loop", "for_loop_break" ->
            {
                if (in.equals("exec:break"))
                {
                    s.put("broken", true);

                    return;
                }

                int first = (int) parse(this.input(run, node, "first", 0), 0);
                int last = (int) parse(this.input(run, node, "last", 0), 0);

                s.put("broken", false);

                for (int i = first, n = 0; i <= last && n < MAX_LOOP && !Boolean.TRUE.equals(s.get("broken")); i++, n++)
                {
                    s.put("index", i);
                    this.out(run, node, "body");
                }

                this.out(run, node, "completed");
            }
            case "while_loop" ->
            {
                int max = Math.max(1, Math.min(MAX_LOOP, (int) parse(node.param("max_iterations"), 1000)));

                for (int n = 0; n < max && run.steps < MAX_STEPS && FlowValues.truthy(this.input(run, node, "condition", 0)); n++)
                {
                    this.out(run, node, "body");
                }

                this.out(run, node, "completed");
            }
            case "for_each", "for_each_break", "reverse_for_each" ->
            {
                if (in.equals("exec:break"))
                {
                    s.put("broken", true);

                    return;
                }

                List<String> items = FlowValues.list(this.input(run, node, "array", 0));
                boolean reverse = node.type.equals("reverse_for_each");

                s.put("broken", false);

                for (int n = 0; n < items.size() && n < MAX_LOOP && !Boolean.TRUE.equals(s.get("broken")); n++)
                {
                    int i = reverse ? items.size() - 1 - n : n;

                    s.put("index", i);
                    s.put("element", items.get(i));
                    this.out(run, node, "body");
                }

                this.out(run, node, "completed");
            }
            case "switch_int" ->
            {
                int index = (int) parse(this.input(run, node, "selection", 0), 0) - (int) parse(node.param("start"), 0);

                this.out(run, node, index >= 0 && index < node.outputCount() ? "case" + index : "default");
            }
            case "switch_string", "switch_name", "switch_enum" ->
            {
                String selection = this.input(run, node, "selection", 0);
                String pin = "default";

                for (int i = 0; i < node.outputCount(); i++)
                {
                    if (node.param("case" + i).equals(selection))
                    {
                        pin = "case" + i;

                        break;
                    }
                }

                this.out(run, node, pin);
            }
            case FlowNodeTypes.WAIT -> this.pending.add(new Pending(node.id, "then", this.uuid(run), now + Math.max(0L, (long) parse(this.input(run, node, "ticks", 0), 0))));
            case "retriggerable_delay" ->
            {
                this.pending.removeIf((p) -> p.node == node.id);
                this.pending.add(new Pending(node.id, "then", this.uuid(run), now + Math.max(0L, (long) parse(this.input(run, node, "ticks", 0), 0))));
            }
            case "cooldown" ->
            {
                long last = (Long) s.getOrDefault("last", Long.MIN_VALUE / 2);

                if (now - last >= (long) parse(this.input(run, node, "ticks", 0), 0))
                {
                    s.put("last", now);
                    this.out(run, node, "then");
                }
                else
                {
                    this.out(run, node, "blocked");
                }
            }
            case "debounce" ->
            {
                long last = (Long) s.getOrDefault("last", Long.MIN_VALUE / 2);

                s.put("last", now);

                if (now - last >= (long) parse(this.input(run, node, "ticks", 0), 0))
                {
                    this.out(run, node, "then");
                }
            }
            case "pulse" ->
            {
                if (in.equals("exec:stop"))
                {
                    s.put("running", false);
                }
                else
                {
                    long interval = Math.max(1L, (long) parse(this.input(run, node, "interval", 0), 20));

                    s.put("running", true);
                    s.put("interval", interval);
                    s.put("next", now + interval);
                    s.put("count", 0);
                    s.put("entity", this.uuid(run));
                }
            }
            case "schmitt_trigger" ->
            {
                double value = parse(this.input(run, node, "value", 0), 0);
                boolean on = Boolean.TRUE.equals(s.get("state"));

                if (!on && value >= parse(this.input(run, node, "high", 0), 0))
                {
                    s.put("state", true);
                    this.out(run, node, "on");
                }
                else if (on && value <= parse(this.input(run, node, "low", 0), 0))
                {
                    s.put("state", false);
                    this.out(run, node, "off");
                }
            }
            case "edge_detector" ->
            {
                boolean value = FlowValues.truthy(this.input(run, node, "value", 0));
                boolean before = Boolean.TRUE.equals(s.get("last"));

                s.put("last", value);

                if (value && !before)
                {
                    this.out(run, node, "rising");
                }
                else if (!value && before)
                {
                    this.out(run, node, "falling");
                }
            }
            case "state_machine" ->
            {
                int target = (int) parse(this.input(run, node, "target", 0), 0);
                int current = (Integer) s.getOrDefault("current", -1);

                if (target != current && target >= 0 && target < node.outputCount())
                {
                    s.put("current", target);
                    this.out(run, node, "state" + target);
                }
            }
            case "queue" -> this.queue(run, node, s, in);
            case "chance" -> this.out(run, node, this.random(run, node, s).nextDouble() * 100D < parse(this.input(run, node, "percent", 0), 0) ? "pass" : "fail");
            case "weighted_random" -> this.weighted(run, node, s);
            case "shuffle_bag" ->
            {
                @SuppressWarnings("unchecked")
                List<Integer> bag = (List<Integer>) s.computeIfAbsent("bag", (k) -> new ArrayList<Integer>());

                if (bag.isEmpty())
                {
                    for (int i = 0; i < node.outputCount(); i++)
                    {
                        bag.add(i);
                    }

                    Collections.shuffle(bag, this.random(run, node, s));
                }

                if (!bag.isEmpty())
                {
                    this.out(run, node, "out" + bag.remove(0));
                }
            }
            case "counter" ->
            {
                if (in.equals("exec:reset"))
                {
                    s.put("count", 0);

                    return;
                }

                int before = (Integer) s.getOrDefault("count", 0);
                int count = before + (int) parse(this.input(run, node, "step", 0), 1);
                int target = (int) parse(this.input(run, node, "target", 0), 0);

                s.put("count", count);
                this.out(run, node, "then");

                if (before < target && count >= target)
                {
                    this.out(run, node, "target_reached");
                }
            }
            case "damage_entity", "heal_entity" ->
            {
                float amount = (float) parse(this.input(run, node, "amount", 0), 0);

                for (Entity target : this.entities(run, this.input(run, node, "entity", 0)))
                {
                    if (target instanceof LivingEntity living)
                    {
                        if (node.type.equals("heal_entity"))
                        {
                            living.heal(amount);
                        }
                        else
                        {
                            living.damage(run.world.getDamageSources().generic(), amount);
                        }
                    }
                }

                this.out(run, node, "then");
            }
            case "play_film" ->
            {
                FlowBBS.playFilm(run.entity, this.input(run, node, "player", 0), this.input(run, node, "film", 0), node.param("camera"));
                this.out(run, node, "then");
            }
            case "stop_film" ->
            {
                FlowBBS.stopFilm(run.entity, this.input(run, node, "player", 0), this.input(run, node, "film", 0));
                this.out(run, node, "then");
            }
            case "set_camera_clip" ->
            {
                FlowBBS.setCameraClip(run.entity, this.input(run, node, "player", 0), this.input(run, node, "film", 0));
                this.out(run, node, "then");
            }
            case "set_form" ->
            {
                FlowBBS.setForm(run.entity, this.input(run, node, "player", 0), this.input(run, node, "form", 0));
                this.out(run, node, "then");
            }
            case "clear_form" ->
            {
                FlowBBS.clearForm(run.entity, this.input(run, node, "player", 0));
                this.out(run, node, "then");
            }
            case "model_block_state" ->
            {
                FlowBBS.modelBlockState(run.entity, this.input(run, node, "position", 0), this.input(run, node, "state", 0));
                this.out(run, node, "then");
            }
            case "launch_entity" ->
            {
                double[] v = FlowValues.vec(this.input(run, node, "velocity", 0));

                for (Entity target : this.entities(run, this.input(run, node, "entity", 0)))
                {
                    target.addVelocity(v[0], v[1], v[2]);
                    target.velocityModified = true;
                }

                this.out(run, node, "then");
            }
            default ->
            {
                if (type.command != null)
                {
                    this.command(run, this.fillCommand(run, node, type.command));
                }

                if (type.execOutputs.contains("then"))
                {
                    this.out(run, node, "then");
                }
            }
        }
    }

    private void multiGate(Run run, FlowNode node, Map<String, Object> s, String in)
    {
        int count = node.outputCount();
        boolean[] used = (boolean[]) s.get("used");

        if (used == null || used.length != count || in.equals("exec:reset"))
        {
            used = new boolean[count];
            s.put("used", used);
            s.put("next", 0);

            if (in.equals("exec:reset"))
            {
                return;
            }
        }

        List<Integer> open = new ArrayList<>();

        for (int i = 0; i < count; i++)
        {
            if (!used[i]) open.add(i);
        }

        if (open.isEmpty())
        {
            if (!FlowValues.truthy(node.param("loop")))
            {
                return;
            }

            used = new boolean[count];
            s.put("used", used);

            for (int i = 0; i < count; i++) open.add(i);
        }

        int pick;

        if (FlowValues.truthy(node.param("random")))
        {
            pick = open.get(this.random(run, node, s).nextInt(open.size()));
        }
        else
        {
            int next = (Integer) s.getOrDefault("next", 0);

            pick = open.contains(next) ? next : open.get(0);
        }

        s.put("next", pick + 1);
        used[pick] = true;
        this.out(run, node, "out" + pick);
    }

    private void queue(Run run, FlowNode node, Map<String, Object> s, String in)
    {
        @SuppressWarnings("unchecked")
        LinkedList<String> queue = (LinkedList<String>) s.computeIfAbsent("queue", (k) -> new LinkedList<String>());

        if (in.equals("exec:pop"))
        {
            if (queue.isEmpty())
            {
                this.out(run, node, "empty");
            }
            else
            {
                s.put("value", queue.poll());
                s.put("size", queue.size());
                this.out(run, node, "popped");
            }

            return;
        }

        if (queue.size() < 1024)
        {
            queue.add(this.input(run, node, "value", 0));
        }

        s.put("size", queue.size());
        this.out(run, node, "then");
    }

    private void weighted(Run run, FlowNode node, Map<String, Object> s)
    {
        int count = node.outputCount();
        double total = 0;

        for (int i = 0; i < count; i++)
        {
            total += Math.max(0D, parse(node.param("weight" + i), 0));
        }

        if (total <= 0)
        {
            return;
        }

        double roll = this.random(run, node, s).nextDouble() * total;

        for (int i = 0; i < count; i++)
        {
            roll -= Math.max(0D, parse(node.param("weight" + i), 0));

            if (roll < 0)
            {
                this.out(run, node, "out" + i);

                return;
            }
        }
    }

    /** A draw that comes out the same for the same game tick, node, box and call. */
    private Random random(Run run, FlowNode node, Map<String, Object> s)
    {
        long draws = (Long) s.getOrDefault("draws", 0L);

        s.put("draws", draws + 1);

        return new Random(run.world.getTime() * 6364136223846793005L + node.id * 1442695040888963407L + draws * 31L + this.pos.asLong());
    }

    /* Data */

    /** A data input's value: the wire into it, worked out now, or the value typed into it. */
    private String input(Run run, FlowNode node, String pin, int depth)
    {
        FlowLink link = this.graph.dataInto(node.id, pin);

        if (link != null)
        {
            return this.evaluate(run, this.graph.get(link.from()), link.pin(), depth + 1);
        }

        return this.text(node.param(pin), run.entity);
    }

    private List<String> grown(Run run, FlowNode node, int depth)
    {
        List<String> values = new ArrayList<>();

        for (int i = 0; i < node.inputCount(); i++)
        {
            values.add(this.input(run, node, FlowNodeType.input(i), depth));
        }

        return values;
    }

    private String evaluate(Run run, FlowNode node, String pin, int depth)
    {
        if (node == null || depth > 64)
        {
            return "";
        }

        String logic = FlowNodeTypes.BOOLEAN_OF.get(node.type);

        if (logic != null)
        {
            return String.valueOf(FlowValues.logic(logic, this.grown(run, node, depth)));
        }

        String operator = FlowNodeTypes.OPERATOR_OF.get(node.type);

        if (operator != null)
        {
            return String.valueOf(FlowValues.check(operator, this.grown(run, node, depth)));
        }

        return switch (node.type)
        {
            case FlowNodeTypes.GET_VARIABLE ->
            {
                FlowVariable variable = this.graph.variableOf(node);

                yield variable == null ? "" : this.read(run, variable);
            }
            case FlowNodeTypes.SET_VARIABLE ->
            {
                Object value = this.state(node).get("value");
                FlowVariable variable = this.graph.variableOf(node);

                yield value != null ? value.toString() : variable == null ? "" : this.read(run, variable);
            }
            case "not" -> String.valueOf(!FlowValues.truthy(this.input(run, node, "in0", depth)));
            case "select" ->
            {
                int index = (int) parse(this.input(run, node, "index", depth), 0);

                yield index >= 0 && index < node.inputCount() ? this.input(run, node, FlowNodeType.input(index), depth) : "";
            }
            case "is_valid" -> this.valid(run, this.input(run, node, "value", depth));
            case "append" -> String.join("", this.grown(run, node, depth));
            case "make_array" -> FlowValues.join(this.grown(run, node, depth));
            case "get_array" -> FlowValues.item(this.input(run, node, "array", depth), (int) parse(this.input(run, node, "index", depth), 0));
            case "format_text" -> this.format(run, node, depth);
            case "get_form" -> pin.equals("form_id") ? FlowBBS.formId(run.entity, this.input(run, node, "entity", depth)) : FlowBBS.formData(run.entity, this.input(run, node, "entity", depth));
            case "clamp" -> FlowValues.format(Math.max(this.number(run, node, "min", depth), Math.min(this.number(run, node, "max", depth), this.number(run, node, "value", depth))));
            case "map_range" -> this.mapRange(run, node, depth);
            case "threshold" -> String.valueOf(this.number(run, node, "value", depth) >= this.number(run, node, "threshold", depth));
            default -> this.world(run, node, pin, depth);
        };
    }

    /** Format Text: the template with each {name} replaced by what its pin reads. */
    private String format(Run run, FlowNode node, int depth)
    {
        String template = node.param("template");

        for (String name : FlowNode.templateNames(template))
        {
            template = template.replace("{" + name + "}", this.input(run, node, FlowNodeType.templatePin(name), depth));
        }

        return template;
    }

    private double number(Run run, FlowNode node, String pin, int depth)
    {
        return parse(this.input(run, node, pin, depth), 0);
    }

    /** Whether a value is there: an entity reference (@entity, a UUID, a selector) only when it finds one. */
    private String valid(Run run, String value)
    {
        String v = FlowEntities.raw(value);

        if (v.isEmpty())
        {
            return "false";
        }

        if (FlowEntities.isSource(v) || FlowEntities.isSelector(v) || FlowEntities.byReference(run.world.getServer(), v) != null)
        {
            return String.valueOf(this.entity(run, v) != null);
        }

        return String.valueOf(!v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
    }

    private String mapRange(Run run, FlowNode node, int depth)
    {
        double value = this.number(run, node, "value", depth);
        double inMin = this.number(run, node, "in_min", depth);
        double inMax = this.number(run, node, "in_max", depth);
        double outMin = this.number(run, node, "out_min", depth);
        double outMax = this.number(run, node, "out_max", depth);
        double t = inMax == inMin ? 0 : (value - inMin) / (inMax - inMin);

        if (FlowValues.truthy(node.param("clamp")))
        {
            t = Math.max(0, Math.min(1, t));
        }

        return FlowValues.format(outMin + (outMax - outMin) * t);
    }

    /** The world and entity reads, and what an impure node remembers (a loop's index, a counter's count). */
    private String world(Run run, FlowNode node, String pin, int depth)
    {
        Entity entity = node.getType() != null && node.getType().dataInput("entity") != null ? this.entity(run, this.input(run, node, "entity", depth)) : null;

        switch (node.type)
        {
            case "entity_health": return entity instanceof LivingEntity living ? FlowValues.format(living.getHealth()) : "0";
            case "entity_position": return entity == null ? "0 0 0" : vec(entity.getPos());
            case "entity_velocity": return entity == null ? "0 0 0" : vec(entity.getVelocity());
            case "entity_name": return entity == null ? "" : entity.getName().getString();
            case "is_player": return String.valueOf(entity instanceof PlayerEntity);
            case "has_tag": return String.valueOf(entity != null && entity.getCommandTags().contains(this.input(run, node, "tag", depth)));
            case "scoreboard":
            {
                Scoreboard scoreboard = run.world.getScoreboard();
                ScoreboardObjective objective = scoreboard.getNullableObjective(this.input(run, node, "objective", depth));

                if (entity == null || objective == null)
                {
                    return "0";
                }

                ReadableScoreboardScore score = scoreboard.getScore(entity, objective);

                return String.valueOf(score == null ? 0 : score.getScore());
            }
            case "world_time": return String.valueOf(run.world.getTimeOfDay() % 24000L);
            case "is_raining": return String.valueOf(run.world.isRaining());
            case "block_at":
            {
                Vec3d p = this.position(run, this.input(run, node, "position", depth));

                return Registries.BLOCK.getId(run.world.getBlockState(BlockPos.ofFloored(p)).getBlock()).toString();
            }
            case "players_in_radius":
            {
                Vec3d center = this.position(run, this.input(run, node, "position", depth));
                double radius = this.number(run, node, "radius", depth);
                List<String> names = new ArrayList<>();

                for (ServerPlayerEntity player : run.world.getPlayers())
                {
                    if (player.getPos().squaredDistanceTo(center) <= radius * radius)
                    {
                        names.add(player.getName().getString());
                    }
                }

                return pin.equals("count") ? String.valueOf(names.size()) : String.join(",", names);
            }
            case "get_form":
            {
                Morph morph = entity == null ? null : Morph.getMorph(entity);
                Form form = morph == null ? null : morph.getForm();

                if (form == null)
                {
                    return "";
                }

                return pin.equals("form_id") ? form.getFormId() : mchorse.bbs_mod.data.DataToString.toString(FormUtils.toData(form));
            }
            default:
            {
                Object value = this.state(node).get(pin);

                return value == null ? "" : value.toString();
            }
        }
    }

    private static String vec(Vec3d v)
    {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", v.x, v.y, v.z);
    }

    /** A position: three numbers, any of them ~ or ~n around the entity (or the box). */
    private Vec3d position(Run run, String value)
    {
        Vec3d base = run.entity != null ? run.entity.getPos() : Vec3d.ofCenter(this.pos);
        String[] split = value.trim().split("[ ,]+");
        double[] xyz = {base.x, base.y, base.z};

        for (int i = 0; i < 3 && i < split.length; i++)
        {
            String part = split[i];

            xyz[i] = part.startsWith("~") ? xyz[i] + parse(part.substring(1), 0) : parse(part, xyz[i]);
        }

        return new Vec3d(xyz[0], xyz[1], xyz[2]);
    }

    /**
     * The entity a value names, through {@link FlowEntities}, as everywhere else in a flow: "@entity"
     * (or nothing) is the entity that set the run off, then a selector, a UUID or a player's name.
     */
    private Entity entity(Run run, String value)
    {
        return FlowEntities.resolveEntity(run.entity, run.world, this.origin(), value);
    }

    /** Every entity a value names: all a selector such as @a finds, otherwise the one. */
    private List<Entity> entities(Run run, String value)
    {
        return FlowEntities.resolveEntities(run.entity, run.world, this.origin(), value);
    }

    /** Where a selector looks from when no entity set the run off: the box. */
    private Vec3d origin()
    {
        return Vec3d.ofCenter(this.pos);
    }

    /** The entity a delayed or pulsed run was set off by, wherever it is now. */
    private Entity source(ServerWorld world, UUID uuid)
    {
        return uuid == null ? null : FlowEntities.byReference(world.getServer(), uuid.toString());
    }

    private UUID uuid(Run run)
    {
        return run.entity == null ? null : run.entity.getUuid();
    }

    /** A value about to be stored in a variable of the type: an entity variable keeps a UUID (a selector of many stays itself). */
    private String asValue(Run run, String value, String type)
    {
        if (type.equals("entity"))
        {
            return FlowEntities.reference(run.entity, run.world, this.origin(), value);
        }

        return value;
    }

    /* Variables, by scope */

    private String read(Run run, FlowVariable variable)
    {
        String value = switch (variable.scope)
        {
            case "global" -> FlowGlobals.get(run.world.getServer()).getGlobal(variable.name);
            case "entity" -> run.entity == null ? null : FlowGlobals.get(run.world.getServer()).getEntity(run.entity.getUuid(), variable.name);
            default -> this.values.get(variable.id);
        };

        return value == null ? variable.value : value;
    }

    private void write(Run run, FlowVariable variable, String value)
    {
        switch (variable.scope)
        {
            case "global" -> FlowGlobals.get(run.world.getServer()).setGlobal(variable.name, value);
            case "entity" ->
            {
                if (run.entity != null)
                {
                    FlowGlobals.get(run.world.getServer()).setEntity(run.entity.getUuid(), variable.name, value);
                }
            }
            default -> this.values.put(variable.id, value);
        }
    }

    /* Commands */

    /** Fills a node's command: {pin} with the pin's value, {pin:q} quoted for a command string, {pin:json} as JSON text. */
    private String fillCommand(Run run, FlowNode node, String template)
    {
        StringBuilder builder = new StringBuilder();
        int i = 0;

        while (i < template.length())
        {
            int open = template.indexOf('{', i);
            int close = open < 0 ? -1 : template.indexOf('}', open);

            if (open < 0 || close < 0)
            {
                builder.append(template, i, template.length());

                break;
            }

            builder.append(template, i, open);

            String key = template.substring(open + 1, close);
            String mode = "";
            int colon = key.indexOf(':');

            if (colon >= 0)
            {
                mode = key.substring(colon + 1);
                key = key.substring(0, colon);
            }

            String value = node.getType().dataInput(key) != null ? this.input(run, node, key, 0) : node.param(key);

            if (node.declaredInputType(key).equals("entity"))
            {
                /* A selector goes into the command as it is, so @a still means everyone */
                value = FlowEntities.commandTarget(run.entity, run.world, this.origin(), value);
            }

            builder.append(mode.equals("q") || mode.equals("json") ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value);
            i = close + 1;
        }

        return builder.toString();
    }

    private void command(Run run, String command)
    {
        MinecraftServer server = run.world.getServer();

        if (command.isBlank())
        {
            return;
        }

        if (command.startsWith("/"))
        {
            command = command.substring(1);
        }

        ServerCommandSource source = server.getCommandSource()
            .withWorld(run.world)
            .withPosition(Vec3d.ofCenter(this.pos))
            .withLevel(2)
            .withSilent();

        if (run.entity != null)
        {
            source = source.withEntity(run.entity).withPosition(run.entity.getPos());
        }

        try
        {
            server.getCommandManager().executeWithPrefix(source, command);
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("[Trigger box {}] Command failed: {}", this.pos.toShortString(), command);
        }
    }

    /** Fills in {name}: {entity}, {entity_uuid}, {x} {y} {z} of the entity, and the flow's local variables by name. */
    public String text(String raw, Entity entity)
    {
        if (raw == null || raw.indexOf('{') < 0)
        {
            return raw == null ? "" : raw;
        }

        StringBuilder builder = new StringBuilder();
        int i = 0;

        while (i < raw.length())
        {
            int open = raw.indexOf('{', i);
            int close = open < 0 ? -1 : raw.indexOf('}', open);

            if (open < 0 || close < 0)
            {
                builder.append(raw, i, raw.length());

                break;
            }

            builder.append(raw, i, open);

            String key = raw.substring(open + 1, close);
            FlowVariable variable = this.graph == null ? null : this.graph.getVariable(key);
            String value = switch (key)
            {
                case "entity" -> entity == null ? "" : entity.getName().getString();
                case "entity_uuid" -> entity == null ? "" : entity.getUuidAsString();
                case "x" -> entity == null ? "" : String.format(Locale.ROOT, "%.2f", entity.getX());
                case "y" -> entity == null ? "" : String.format(Locale.ROOT, "%.2f", entity.getY());
                case "z" -> entity == null ? "" : String.format(Locale.ROOT, "%.2f", entity.getZ());
                default -> variable == null ? null : this.values.getOrDefault(variable.id, variable.value);
            };

            builder.append(value == null ? raw.substring(open, close + 1) : value);
            i = close + 1;
        }

        return builder.toString();
    }

    public static boolean compare(String a, String operator, String b)
    {
        return FlowValues.compare(a, operator, b);
    }

    public static double parse(String text, double fallback)
    {
        return FlowValues.parse(text, fallback);
    }
}
