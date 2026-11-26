package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The BBS side of a trigger flow: films, cameras, forms and model blocks, called straight on the
 * server the way BBS's own /bbs commands do it. The flow runtime calls it, and the Java the code
 * view writes calls the very same methods, so that code is real: it compiles against the mod and
 * does what the nodes do.
 *
 * <p>Every method takes the entity that set the flow off ({@code source}) and the target as any of:
 * an entity, "@entity" (the source itself), a selector such as @a, a player's name, or an entity
 * UUID, resolved by {@link FlowEntities} as everywhere else in a flow.</p>
 */
public final class FlowBBS
{
    /** The server of the last flow run, for targets named while no entity is at hand. */
    private static MinecraftServer server;

    private FlowBBS()
    {}

    public static void use(MinecraftServer server)
    {
        FlowBBS.server = server;
        FlowEntities.use(server);
    }

    /* Films */

    /** Plays a film to a player (or every player a selector names), its camera on their view when {@code camera} is true. */
    public static void playFilm(Entity source, Object player, Object film, Object camera)
    {
        String filmId = text(film);

        if (filmId.isBlank())
        {
            return;
        }

        for (ServerPlayerEntity target : players(source, player))
        {
            ServerNetwork.sendPlayFilm(target, filmId, FlowValues.truthy(text(camera)));
        }
    }

    /** Stops a film for a player; the server stops acting it too, so its actors do not go on unseen. */
    public static void stopFilm(Entity source, Object player, Object film)
    {
        String filmId = text(film);

        if (filmId.isBlank())
        {
            return;
        }

        BBSMod.getActions().stop(filmId);

        for (ServerPlayerEntity target : players(source, player))
        {
            ServerNetwork.sendStopFilm(target, filmId);
        }
    }

    /** Puts the player's view on the film's camera clips: the film played with its camera. */
    public static void setCameraClip(Entity source, Object player, Object film)
    {
        playFilm(source, player, film, true);
    }

    /* Forms */

    /** Morphs a player into a form, given as BBS form data (as the form editor copies it). */
    public static void setForm(Entity source, Object player, Object formData)
    {
        List<ServerPlayerEntity> targets = players(source, player);

        if (targets.isEmpty())
        {
            return;
        }

        try
        {
            MapType data = DataToString.mapFromString(text(formData));
            Form form = data == null ? null : FormUtils.fromData(data);

            if (form != null)
            {
                for (ServerPlayerEntity target : targets)
                {
                    ServerNetwork.sendMorphToTracked(target, form);
                    Morph.getMorph(target).setForm(FormUtils.copy(form));
                }
            }
        }
        catch (Exception e)
        {
            BBSMod.LOGGER.warn("[Trigger box] Not a form: {}", text(formData));
        }
    }

    /** Turns a player back into themselves. */
    public static void clearForm(Entity source, Object player)
    {
        for (ServerPlayerEntity target : players(source, player))
        {
            ServerNetwork.sendMorphToTracked(target, null);
            Morph.getMorph(target).setForm(null);
        }
    }

    /** The id of the entity's current form, empty when it has none. */
    public static String formId(Entity source, Object entity)
    {
        Form form = form(resolve(source, entity));

        return form == null ? "" : form.getFormId();
    }

    /** All of the entity's current form, as BBS form data, empty when it has none. */
    public static String formData(Entity source, Object entity)
    {
        Form form = form(resolve(source, entity));

        return form == null ? "" : DataToString.toString(FormUtils.toData(form));
    }

    private static Form form(Entity entity)
    {
        Morph morph = entity == null ? null : Morph.getMorph(entity);

        return morph == null ? null : morph.getForm();
    }

    /* Active physics: the live ragdoll controller of the films a player is watching */

    /**
     * Sets the active physics strength of every active ragdoll in a film playing for the player
     * (every playing film when {@code film} is empty), from the tick it arrives on: 0 is the passive
     * ragdoll, 1 the full live response. The player's game pins it to that film tick and simulates
     * on from there, so the film plays it out the same each time it passes it.
     */
    public static void setActivePhysics(Entity source, Object player, Object film, Object strength)
    {
        float value = (float) Math.max(0D, Math.min(1D, FlowValues.parse(text(strength), 1D)));

        for (ServerPlayerEntity target : players(source, player))
        {
            ServerNetwork.sendActivePhysics(target, text(film), value, 0F, false);
        }
    }

    /** Gives the film's active ragdolls back their own strength, as their forms and keyframes have it. */
    public static void resetActivePhysics(Entity source, Object player, Object film)
    {
        for (ServerPlayerEntity target : players(source, player))
        {
            ServerNetwork.sendActivePhysics(target, text(film), -1F, 0F, false);
        }
    }

    /** Staggers the film's active ragdolls by {@code amount} (0 to 1) on the tick it arrives on, and knocks them down when {@code knockdown} is true. */
    public static void physicsStagger(Entity source, Object player, Object film, Object amount, Object knockdown)
    {
        float value = (float) Math.max(0D, Math.min(1D, FlowValues.parse(text(amount), 0.6D)));
        boolean down = FlowValues.truthy(text(knockdown));

        for (ServerPlayerEntity target : players(source, player))
        {
            ServerNetwork.sendActivePhysics(target, text(film), Float.NaN, value, down);
        }
    }

    /* Model blocks */

    /** Plays an animation state on the model block at a position, for every player within 64 blocks. */
    public static void modelBlockState(Entity source, Object position, Object state)
    {
        ServerWorld world = source != null && source.getWorld() instanceof ServerWorld w ? w : server == null ? null : server.getOverworld();

        if (world == null)
        {
            return;
        }

        BlockPos pos = BlockPos.ofFloored(position(source, position));

        if (!(world.getBlockEntity(pos) instanceof ModelBlockEntity))
        {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers())
        {
            if (player.getBlockPos().getSquaredDistance(pos) <= 64 * 64)
            {
                ServerNetwork.sendModelBlockState(player, pos, text(state));
            }
        }
    }

    /* Reading the arguments */

    private static String text(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** A position: a Vec3d, or "x y z" where ~ counts from the source. */
    public static Vec3d position(Entity source, Object value)
    {
        if (value instanceof Vec3d vec)
        {
            return vec;
        }

        Vec3d base = source == null ? Vec3d.ZERO : source.getPos();
        String[] split = text(value).split("[ ,]+");
        double[] xyz = {base.x, base.y, base.z};

        for (int i = 0; i < 3 && i < split.length; i++)
        {
            String part = split[i];

            if (part.isEmpty())
            {
                continue;
            }

            xyz[i] = part.startsWith("~") ? xyz[i] + FlowValues.parse(part.substring(1), 0) : FlowValues.parse(part, xyz[i]);
        }

        return new Vec3d(xyz[0], xyz[1], xyz[2]);
    }

    /** An entity: itself, "@entity" or nothing for the source, a selector, a player's name, or a UUID. */
    public static Entity resolve(Entity source, Object value)
    {
        return FlowEntities.resolveEntity(source, value);
    }

    /** Every player the value names: the source, a name, a UUID, or all a selector finds. */
    private static List<ServerPlayerEntity> players(Entity source, Object value)
    {
        List<ServerPlayerEntity> players = new ArrayList<>();

        for (Entity entity : FlowEntities.resolveEntities(source, value))
        {
            if (entity instanceof ServerPlayerEntity player && !players.contains(player))
            {
                players.add(player);
            }
        }

        return players;
    }
}
