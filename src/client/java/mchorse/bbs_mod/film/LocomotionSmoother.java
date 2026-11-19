package mchorse.bbs_mod.film;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Locomotion smoothing: an actor that starts, turns and stops with some inertia on the screen, while
 * landing exactly where it is meant to be.
 *
 * <p>Each tick gives a place - the keyframes' in a film, the player's own in the world. What is shown
 * follows that place through a critically damped spring (no overshoot, no ringing), so a start eases in
 * and a stop eases out. A spring alone would trail by its time constant the whole time the actor walks,
 * so it follows the place the actor will be in one time constant from now, from how fast it is going:
 * exactly the lead that cancels a spring's lag on steady movement, so a walk is neither early nor late
 * and only its changes are softened. When the movement stops the lead is gone, the spring settles onto
 * the place and the last millimetre is snapped.</p>
 *
 * <p>Smooth between ticks too: the step keeps the place it was shown at on the tick before as well as
 * this tick's, and the entity is given both - the previous and the current - so every frame drawn in
 * between is a straight blend of two smoothed places, never a jump from an unsmoothed one. A tick asked
 * for again (a paused film, a frame drawn twice) gives the same answer; anything that is not the very
 * next tick (a scrub, a loop, a first sight) starts over on the place itself.</p>
 *
 * <p>Three places use it: an actor of a film while it plays, the take being recorded (whose player is
 * drawn morphed), and any player wearing a form with the setting on from the podium. Only the ground
 * plane is smoothed; height is left alone, so jumps and falls keep their timing.</p>
 *
 * <p>Whatever the setting, an actor's {@link LocomotionState} is kept up to date for blending later.</p>
 */
public final class LocomotionSmoother
{
    private static final double TICK = 0.05D;
    private static final double TELEPORT = 8D;
    private static final double REST_DISTANCE = 0.002D;
    private static final double REST_SPEED = 0.02D;

    private static final Map<IEntity, State> ACTORS = new WeakHashMap<>();
    private static final Map<PlayerEntity, State> PLAYERS = new WeakHashMap<>();

    private static class State
    {
        int tick = Integer.MIN_VALUE;
        double lastX;
        double lastZ;
        double prevX;
        double prevZ;
        double x;
        double z;
        double vx;
        double vz;
        double keyVx;
        double keyVz;
    }

    private LocomotionSmoother()
    {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register((mc) -> tickPlayers(mc));
    }

    /**
     * One step of the spring for this tick's place. Returns whether it was smoothed at all (false when
     * the state started over on the place).
     */
    private static boolean step(State state, int tick, double tx, double tz, boolean smooth, double time)
    {
        if (state.tick == tick)
        {
            return true;
        }

        boolean next = state.tick != Integer.MIN_VALUE && tick == state.tick + 1;
        double jump = Math.hypot(tx - state.lastX, tz - state.lastZ);

        if (!next || jump > TELEPORT || !Double.isFinite(tx) || !Double.isFinite(tz))
        {
            state.tick = tick;
            state.lastX = tx;
            state.lastZ = tz;
            state.prevX = state.x = tx;
            state.prevZ = state.z = tz;
            state.vx = state.vz = 0D;
            state.keyVx = state.keyVz = 0D;

            return false;
        }

        double kx = (tx - state.lastX) / TICK;
        double kz = (tz - state.lastZ) / TICK;

        state.tick = tick;
        state.lastX = tx;
        state.lastZ = tz;
        state.keyVx = kx;
        state.keyVz = kz;
        state.prevX = state.x;
        state.prevZ = state.z;

        if (!smooth)
        {
            state.x = tx;
            state.z = tz;
            state.vx = kx;
            state.vz = kz;

            return true;
        }

        time = Math.max(0.02D, time);

        double gx = tx + kx * time;
        double gz = tz + kz * time;
        double omega = 2D / time;
        double x = omega * TICK;
        double damp = 1D / (1D + x + 0.48D * x * x + 0.235D * x * x * x);

        double changeX = state.x - gx;
        double tempX = (state.vx + omega * changeX) * TICK;

        state.vx = (state.vx - omega * tempX) * damp;
        state.x = gx + (changeX + tempX) * damp;

        double changeZ = state.z - gz;
        double tempZ = (state.vz + omega * changeZ) * TICK;

        state.vz = (state.vz - omega * tempZ) * damp;
        state.z = gz + (changeZ + tempZ) * damp;

        if (Math.hypot(kx, kz) < REST_SPEED && Math.hypot(state.x - tx, state.z - tz) < REST_DISTANCE && Math.hypot(state.vx, state.vz) < REST_SPEED)
        {
            state.x = tx;
            state.z = tz;
            state.vx = state.vz = 0D;
        }

        if (!Double.isFinite(state.x) || !Double.isFinite(state.z) || !Double.isFinite(state.vx) || !Double.isFinite(state.vz))
        {
            state.prevX = state.x = tx;
            state.prevZ = state.z = tz;
            state.vx = state.vz = 0D;
        }

        return true;
    }

    /* Film actors */

    /**
     * Called every tick of a film after the keyframes (and the AI) have put the actor on its place;
     * leaves the shown place, this tick's and the tick before's, in the entity.
     */
    public static void apply(Replay replay, int ticks, IEntity entity)
    {
        if (replay == null || entity == null)
        {
            return;
        }

        Form form = entity.getForm();
        boolean smooth = form != null && form.locomotionSmoothing.get();
        State state = ACTORS.computeIfAbsent(entity, (e) -> new State());
        boolean stepped = step(state, ticks, entity.getX(), entity.getZ(), smooth, form == null ? 0.3D : form.locomotionTime.get());

        LocomotionState.get(entity).set(smooth ? state.vx : state.keyVx, smooth ? state.vz : state.keyVz, entity.getBodyYaw());

        if (!smooth || !stepped)
        {
            return;
        }

        entity.setPosition(state.x, entity.getY(), state.z);
        entity.setPrevX(state.prevX);
        entity.setPrevZ(state.prevZ);
    }

    /* Players wearing a form, live and while a take is recorded */

    private static void tickPlayers(MinecraftClient mc)
    {
        if (mc.world == null)
        {
            PLAYERS.clear();

            return;
        }

        int tick = (int) mc.world.getTime();

        for (PlayerEntity player : mc.world.getPlayers())
        {
            Morph morph = Morph.getMorph(player);
            Form form = morph == null ? null : morph.getForm();

            if (form == null || !form.locomotionSmoothing.get())
            {
                PLAYERS.remove(player);

                continue;
            }

            step(PLAYERS.computeIfAbsent(player, (p) -> new State()), tick, player.getX(), player.getZ(), true, form.locomotionTime.get());
        }
    }

    /**
     * How far the smoothed place of a morphed player is from where the game draws it on this frame,
     * along x and z; zero when the player is not smoothed. Added to the matrix the form is drawn with.
     */
    public static double[] morphOffset(PlayerEntity player, float delta)
    {
        State state = PLAYERS.get(player);

        if (state == null || state.tick == Integer.MIN_VALUE)
        {
            return null;
        }

        double shownX = state.prevX + (state.x - state.prevX) * delta;
        double shownZ = state.prevZ + (state.z - state.prevZ) * delta;
        double drawnX = player.prevX + (player.getX() - player.prevX) * delta;
        double drawnZ = player.prevZ + (player.getZ() - player.prevZ) * delta;
        double dx = shownX - drawnX;
        double dz = shownZ - drawnZ;

        /* A teleport or a lost step: the smoothed place is not to be trusted this frame */
        if (Math.abs(dx) > TELEPORT || Math.abs(dz) > TELEPORT)
        {
            return null;
        }

        return new double[] {dx, dz};
    }

    public static void forget(IEntity entity)
    {
        ACTORS.remove(entity);
        LocomotionState.forget(entity);
    }
}
