package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a trigger box's flow on the server. An event walks the wires from its node; a wait parks the
 * rest of the chain until the world's game time reaches it, so delays follow the game clock and
 * nothing else. Variables live here as text, seeded from the flow's own defaults.
 */
public class FlowRuntime
{
    /** How many nodes a single run may step through before it is cut, so a wired loop cannot hang the server. */
    private static final int MAX_STEPS = 512;

    private record Pending(int node, String pin, UUID entity, long due) {}

    public final Map<String, String> values = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();

    private FlowGraph graph;
    private BlockPos pos = BlockPos.ORIGIN;

    public void reset(FlowGraph graph, BlockPos pos)
    {
        this.graph = graph;
        this.pos = pos;
        this.values.clear();
        this.pending.clear();

        for (FlowVariable variable : graph.variables)
        {
            this.values.put(variable.name, variable.value);
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

            this.run(world, node.id, "then", entity);
        }
    }

    /** Resumes the chains whose wait is over, on the world's game time. */
    public void tick(ServerWorld world)
    {
        if (this.pending.isEmpty())
        {
            return;
        }

        long time = world.getTime();
        List<Pending> due = new ArrayList<>();

        for (Iterator<Pending> it = this.pending.iterator(); it.hasNext(); )
        {
            Pending p = it.next();

            if (p.due <= time)
            {
                due.add(p);
                it.remove();
            }
        }

        for (Pending p : due)
        {
            Entity entity = p.entity == null ? null : world.getEntity(p.entity);

            this.run(world, p.node, p.pin, entity);
        }
    }

    private void run(ServerWorld world, int from, String pin, Entity entity)
    {
        FlowNode node = this.graph.next(from, pin);
        int steps = 0;

        while (node != null && steps++ < MAX_STEPS)
        {
            String out = "then";

            switch (node.type)
            {
                case FlowNodeTypes.RUN_COMMAND -> this.command(world, entity, this.text(node.param("command"), entity));
                case FlowNodeTypes.SEND_MESSAGE ->
                {
                    if (entity instanceof ServerPlayerEntity player)
                    {
                        player.sendMessage(Text.literal(this.text(node.param("text"), entity)));
                    }
                }
                case FlowNodeTypes.LOG -> BBSMod.LOGGER.info("[Trigger box {}] {}", this.pos.toShortString(), this.text(node.param("message"), entity));
                case FlowNodeTypes.SET_VARIABLE -> this.set(node.param("variable"), this.value(node.param("value"), entity));
                case FlowNodeTypes.ADD_VARIABLE ->
                {
                    String name = node.param("variable");
                    double sum = parse(this.values.get(name), 0) + parse(this.text(node.param("amount"), entity), 0);
                    FlowVariable variable = this.graph.getVariable(name);

                    this.set(name, variable != null && variable.type.equals("integer") ? String.valueOf((long) sum) : String.valueOf(sum));
                }
                case FlowNodeTypes.BRANCH -> out = compare(this.values.getOrDefault(node.param("variable"), ""), node.param("operator"), this.value(node.param("value"), entity)) ? "true" : "false";
                case FlowNodeTypes.WAIT ->
                {
                    long ticks = Math.max(0, (long) parse(this.text(node.param("ticks"), entity), 0));

                    this.pending.add(new Pending(node.id, "then", entity == null ? null : entity.getUuid(), world.getTime() + ticks));

                    return;
                }
                default -> {}
            }

            node = this.graph.next(node.id, out);
        }
    }

    private void set(String name, String value)
    {
        if (!name.isEmpty())
        {
            this.values.put(name, value);
        }
    }

    private void command(ServerWorld world, Entity entity, String command)
    {
        MinecraftServer server = world.getServer();

        if (command.isBlank())
        {
            return;
        }

        if (command.startsWith("/"))
        {
            command = command.substring(1);
        }

        ServerCommandSource source = server.getCommandSource()
            .withWorld(world)
            .withPosition(Vec3d.ofCenter(this.pos))
            .withLevel(2)
            .withSilent();

        if (entity != null)
        {
            source = source.withEntity(entity).withPosition(entity.getPos());
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

    /** A field read as a value: {@code @entity} is the triggering entity, the rest is text with its {variables} filled in. */
    private String value(String raw, Entity entity)
    {
        if (raw.trim().equals("@entity"))
        {
            return entity == null ? "" : entity.getUuidAsString();
        }

        return this.text(raw, entity);
    }

    /** Fills in {name}: {entity}, {entity_uuid}, {x} {y} {z} of the entity, and the flow's variables. */
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
            String value = switch (key)
            {
                case "entity" -> entity == null ? "" : entity.getName().getString();
                case "entity_uuid" -> entity == null ? "" : entity.getUuidAsString();
                case "x" -> entity == null ? "" : String.format("%.2f", entity.getX());
                case "y" -> entity == null ? "" : String.format("%.2f", entity.getY());
                case "z" -> entity == null ? "" : String.format("%.2f", entity.getZ());
                default -> this.values.get(key);
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
