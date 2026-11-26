package mchorse.bbs_mod.triggers;

import com.mojang.brigadier.StringReader;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Where a trigger flow finds the entity an "entity" value names. The flow runtime, the BBS nodes
 * ({@link FlowBBS}) and the Java the code view writes all go through here, so a value means the same
 * everywhere: on an action node, a pure read, a Get of a variable, and after a delay.
 *
 * <p>A value may be an entity itself, nothing or "@entity" (or "@s") for the entity that set the
 * flow off, a vanilla selector such as "@a", "@p" or "@e[type=pig]", an entity UUID, or a player's
 * name. A UUID is looked up in every world, not only the box's, and a player first of all by the
 * server's player list. What a drop down list shows beside a value ("Player558  Player") never
 * reaches here as part of it, and if an old field kept it, the part after the two spaces is dropped.</p>
 */
public final class FlowEntities
{
    /** The source entity's own reference, as the drop down list offers it. */
    public static final String SOURCE = "@entity";

    /** The server of the last flow run, for values read while no entity or world is at hand. */
    private static MinecraftServer server;

    private FlowEntities()
    {}

    public static void use(MinecraftServer server)
    {
        if (server != null)
        {
            FlowEntities.server = server;
        }
    }

    /** The value as it was stored: trimmed, without a label a list may once have put after it. */
    public static String raw(Object value)
    {
        String v = value == null ? "" : String.valueOf(value).trim();
        int label = v.indexOf("  ");
        int bracket = v.indexOf('[');

        /* A selector's own arguments may hold spaces: only what comes after them is a label */
        if (label > 0 && (bracket < 0 || bracket > label || v.indexOf(']') < label))
        {
            v = v.substring(0, label).trim();
        }

        return v;
    }

    /** Whether the value names the entity that set the flow off. */
    public static boolean isSource(Object value)
    {
        String v = raw(value);

        return v.isEmpty() || v.equals(SOURCE) || v.equals("@s");
    }

    /** Whether the value is a vanilla selector other than the source's own (@a, @p, @r, @e...). */
    public static boolean isSelector(Object value)
    {
        String v = raw(value);

        return v.startsWith("@") && !isSource(v);
    }

    /** Whether the value is a selector that may name more than one entity (@a, @e). */
    public static boolean isMany(Object value)
    {
        String v = raw(value);

        return v.startsWith("@a") || v.startsWith("@e");
    }

    /* Resolving */

    /** The one entity the value names, or null; for a selector, the first it finds. */
    public static Entity resolveEntity(Entity source, Object value)
    {
        return resolveEntity(source, null, null, value);
    }

    /**
     * The one entity the value names, or null. {@code world} and {@code origin} are where a selector
     * looks from when there is no source entity (the box); either may be null.
     */
    public static Entity resolveEntity(Entity source, ServerWorld world, Vec3d origin, Object value)
    {
        if (value instanceof Entity entity)
        {
            return entity;
        }

        List<Entity> list = resolveEntities(source, world, origin, value);

        return list.isEmpty() ? null : list.get(0);
    }

    /** Every entity the value names: one for a name, a UUID or the source, any number for a selector. */
    public static List<Entity> resolveEntities(Entity source, Object value)
    {
        return resolveEntities(source, null, null, value);
    }

    public static List<Entity> resolveEntities(Entity source, ServerWorld world, Vec3d origin, Object value)
    {
        if (value instanceof Entity entity)
        {
            return List.of(entity);
        }

        if (value instanceof Iterable<?> iterable)
        {
            List<Entity> list = new ArrayList<>();

            for (Object item : iterable)
            {
                list.addAll(resolveEntities(source, world, origin, item));
            }

            return list;
        }

        String v = raw(value);

        if (isSource(v))
        {
            return source == null ? Collections.emptyList() : List.of(source);
        }

        MinecraftServer server = server(source, world);

        if (server == null)
        {
            return Collections.emptyList();
        }

        if (isSelector(v))
        {
            return select(server, source, world, origin, v);
        }

        /* A list of references, as a list variable keeps them */
        if (v.indexOf(',') >= 0)
        {
            List<Entity> list = new ArrayList<>();

            for (String part : FlowValues.list(v))
            {
                list.addAll(resolveEntities(source, world, origin, part));
            }

            return list;
        }

        Entity found = byReference(server, v);

        return found == null ? Collections.emptyList() : List.of(found);
    }

    /** An entity by UUID in any world, or a player by name (in any case, as the player list keeps it). */
    public static Entity byReference(MinecraftServer server, String v)
    {
        UUID uuid = uuid(v);

        if (uuid != null)
        {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

            if (player != null)
            {
                return player;
            }

            for (ServerWorld w : server.getWorlds())
            {
                Entity entity = w.getEntity(uuid);

                if (entity != null)
                {
                    return entity;
                }
            }

            return null;
        }

        if (v.isEmpty())
        {
            return null;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(v);

        if (player != null)
        {
            return player;
        }

        /* A name in another case, or one shown differently from the profile's */
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList())
        {
            if (online.getGameProfile().getName().equalsIgnoreCase(v) || online.getName().getString().equalsIgnoreCase(v))
            {
                return online;
            }
        }

        return null;
    }

    /** What a variable of type entity keeps for the value: the UUID of the entity, or the value as it is. */
    public static String reference(Entity source, ServerWorld world, Vec3d origin, Object value)
    {
        if (isSelector(value) && isMany(value))
        {
            /* A selector of many stays a selector: it is looked at again whenever it is read */
            return raw(value);
        }

        Entity entity = resolveEntity(source, world, origin, value);

        return entity == null ? raw(value) : entity.getUuidAsString();
    }

    /**
     * What a command should see for the value: a selector as it is, a player by name, any other
     * entity by UUID, "@s" when nothing is found.
     *
     * <p>A player must go in by name: give, clear, effect and title take players only, and vanilla
     * reads a UUID as a selector that may hold other entities and turns it down ("Only players may
     * be affected by this command"). That is why @a worked while @entity and a player's name, which
     * used to be written as UUIDs, did nothing.</p>
     */
    public static String commandTarget(Entity source, ServerWorld world, Vec3d origin, Object value)
    {
        if (isSelector(value))
        {
            return raw(value);
        }

        return commandName(resolveEntity(source, world, origin, value));
    }

    /** How a command names an entity: a player by their name, anything else by its UUID, nothing as "@s". */
    public static String commandName(Entity entity)
    {
        if (entity == null)
        {
            return "@s";
        }

        if (entity instanceof PlayerEntity player)
        {
            return player.getGameProfile().getName();
        }

        return entity.getUuidAsString();
    }

    /** A selector read the way a command reads it, from the source entity or else from the box. */
    private static List<Entity> select(MinecraftServer server, Entity source, ServerWorld world, Vec3d origin, String v)
    {
        try
        {
            EntitySelector selector = new EntitySelectorReader(new StringReader(v)).read();
            ServerWorld at = world != null ? world : source != null && source.getWorld() instanceof ServerWorld w ? w : server.getOverworld();
            ServerCommandSource command = server.getCommandSource().withWorld(at).withLevel(2).withSilent();

            if (origin != null)
            {
                command = command.withPosition(origin);
            }

            if (source != null)
            {
                command = command.withEntity(source).withPosition(source.getPos());
            }

            return new ArrayList<>(selector.getEntities(command));
        }
        catch (Exception e)
        {
            return Collections.emptyList();
        }
    }

    private static MinecraftServer server(Entity source, ServerWorld world)
    {
        if (world != null && world.getServer() != null)
        {
            return world.getServer();
        }

        if (source != null && source.getServer() != null)
        {
            return source.getServer();
        }

        return server;
    }

    private static UUID uuid(String v)
    {
        if (v.length() != 36 || v.indexOf('-') < 0)
        {
            return null;
        }

        try
        {
            return UUID.fromString(v);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
