package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;
import mchorse.bbs_mod.api.client.events.FilmEvents;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.LatticeDeform;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ValueLattice;
import mchorse.bbs_mod.forms.forms.ValueVehicleParts;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.VehicleBoneDetector;
import mchorse.bbs_mod.forms.renderers.VehicleGeometry;
import mchorse.bbs_mod.forms.renderers.VehicleIkState;
import mchorse.bbs_mod.forms.renderers.VehicleSeats;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Getting into a vehicle that stands in a film, and driving it.
 *
 * <p>The vehicle stays the film's replay actor the whole time. Looking at a door, the hood or the
 * trunk and right clicking swings it open or shut; right clicking a seat (through an open door, or
 * past a closed one) opens its door, sits the player down and shuts it again. While seated, the
 * player's own movement is taken away: W/S drive and brake, A/D steer, space is the handbrake, the
 * door key opens and shuts the door beside them, and sneaking gets them out through it.</p>
 *
 * <p>While a replay is ridden its actor is pinned to the drive in every film controller that shows
 * it (the film's own keyframes are overruled tick by tick), its form keeps ticking even in a paused
 * film, so the vehicle IK turns the wheels and leans the body from the drive exactly as it does
 * from a recorded take, and recording that replay records the vehicle instead of the player.</p>
 */
public class VehicleRide
{
    private static final float TICK = 0.05F;
    private static final float REACH = 5F;
    /** How near a bone without a box of its own a click has to pass: about half a door */
    private static final float BONE_RADIUS = 0.6F;
    private static final float SEAT_RADIUS = 0.55F;

    /* Driving, in blocks per second (and per second squared) */
    private static final float MAX_FORWARD = 16F;
    private static final float MAX_REVERSE = 6F;
    private static final float ACCELERATION = 7F;
    private static final float BRAKING = 16F;
    private static final float COASTING = 2.5F;
    private static final float HANDBRAKE = 24F;
    private static final float MAX_STEER = 35F;
    private static final float WHEELBASE = 2.5F;
    private static final float STEER_RATE = 3.5F;
    /** How quickly the turning rate follows the steering, per second: the yaw eases into a turn and out of it */
    private static final float YAW_DAMPING = 8F;
    /** How far an axle climbs toward a higher ground each tick (a share of what is left), and how fast it drops */
    private static final double AXLE_RISE = 0.45D;
    private static final double AXLE_FALL = 0.45D;
    private static final float MAX_CLIMB_PITCH = 30F;
    /** How far a wheel over a hole hangs below the others, in blocks */
    private static final double DROOP = 0.4D;
    /** A half block is a half block: what the step height is allowed over, for collision boxes that round up */
    private static final double STEP_TOLERANCE = 0.05D;
    /**
     * The least a vehicle climbs whatever its step height says: a path block, a carpet, a layer of
     * snow (a sixteenth to three) is ground the suspension takes, never a wall to be stopped by
     */
    private static final double MICRO_STEP = 0.1875D;
    /** How far apart the body's collision columns stand, in blocks */
    private static final float COLUMN_SPACING = 0.5F;
    /** Hitting something faster than this (blocks per second) is an impact: a thud, sparks and a mark in the take */
    private static final float IMPACT_SPEED = 3F;
    /** The spring the view rides on over steps: settles in about a tenth of a second */
    private static final float VIEW_OMEGA = 18F;

    private static final int FL = 0;
    private static final int FR = 1;
    private static final int RL = 2;
    private static final int RR = 3;

    /** Ticks a door takes to open before the player gets through it (its swing, and one more) */
    private static final int DOOR_WAIT = 7;
    /** The ticks a part takes to swing, as {@link VehicleIkState} swings it */
    private static final float DOOR_SWING_TICKS = 6F;
    /** Ticks a take keeps sampling after the player is out: the door shutting behind them */
    private static final int TAKE_TAIL = 12;
    /** A replay moving its vehicle less than this a tick (blocks, half a block a second) has it standing still */
    private static final double MOVING_STEP = 0.025D;
    /** How high a player's hips are when they sit, over their feet: a leg's pivot (12 pixels) less half its thickness */
    private static final float PLAYER_HIPS = 0.625F;

    public static KeyBinding doorKey;

    private static Session session;
    /** The drive being recorded into the vehicle's replay, written there once it is over */
    private static Take take;
    private static long lastFrame;
    private static final List<Closing> closing = new ArrayList<>();
    /** Parts swung from outside, written into the replay once their swing is over */
    private static final List<DoorClick> clicks = new ArrayList<>();
    private static final Set<IEntity> pinned = Collections.newSetFromMap(new IdentityHashMap<>());

    /* What the camera and the body renderer ask about every frame, kept flat */
    private static Entity seated;
    private static float eyeHeight = -1F;

    /* A held right click repeats every tick; a door answers the press, not the hold */
    private static boolean useHeld;
    private static boolean useHandled;

    public static void init()
    {
        FilmEvents.TICK_AFTER.register((controller, ticks) ->
        {
            Take parked = parked();

            if (parked != null && controller.film != null && parked.filmId.equals(controller.film.getId()))
            {
                IEntity entity = controller.entities.get(parked.replayId);

                if (entity != null)
                {
                    parked.pin(entity);
                }
            }

            if (session != null && session.isOf(controller.film))
            {
                IEntity entity = controller.entities.get(session.replayId);
                MinecraftClient mc = MinecraftClient.getInstance();

                if (entity == null)
                {
                    return;
                }

                if (!session.follow)
                {
                    session.pin(entity);
                }
                else if (entity == session.entity && mc.player != null && mc.player == session.player)
                {
                    session.follow(entity);
                    sit(mc.player, session);

                    /* A cut in the film (a jump of the timeline): the view turns with it at once */
                    if (session.cut != 0F && session.phase == Phase.SEATED)
                    {
                        turnView(mc.player, session.cut);
                    }

                    session.cut = 0F;
                }
            }
        });

        WorldRenderEvents.START.register((context) -> frame());
        /* Before the player's own tick: whatever velocity reached a seated player (a shove, a packet)
         * goes before it can move them off the seat for a tick */
        ClientTickEvents.START_CLIENT_TICK.register((mc) ->
        {
            if (seated != null && mc.player == seated)
            {
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        MCEntity.ridingOverride = VehicleRide::isSeated;
    }

    private static void turnView(ClientPlayerEntity player, float turn)
    {
        player.setYaw(player.getYaw() + turn);
        player.prevYaw += turn;
        player.setHeadYaw(player.getHeadYaw() + turn);
        player.prevHeadYaw += turn;
    }

    /**
     * The view turns with the vehicle every frame, not every tick: the turning rate is integrated
     * over the frame's own time (into the previous yaw too, the way the mouse turns it), so the
     * camera sweeps through a corner at whatever the frame rate is instead of stepping 20 times a
     * second. Clocked by the frame, so extra passes of one frame (portals) add nothing.
     */
    private static void frame()
    {
        /* A portal draws the world once more inside the frame, from a camera of its own: the seat's
         * view is turned on the main pass only, and the portal's pass leaves the clock alone too, so
         * the main pass of the next frame gets the whole of the time that went by */
        if (FormPortals.isPassActive())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0F : Math.min((now - lastFrame) / 1.0E9F, 0.1F);

        lastFrame = now;

        if (session == null || session.phase != Phase.SEATED || mc.isPaused() || mc.player != seated || dt <= 0F)
        {
            return;
        }

        turnView(mc.player, session.yawRate * dt);
    }

    /**
     * Keep an actor's vehicle settings on what the film editor has: the actor carries a copy of its
     * replay's form made when the film started, so a switch flipped in the editor (the wheel
     * direction, a bone, an openable part) would otherwise only show once the film was played again.
     */
    public static void syncSettings(Film film, Replay replay, IEntity entity)
    {
        if (entity == null || replay == null || !(entity.getForm() instanceof ModelForm target))
        {
            return;
        }

        Replay source = replay;
        Film editing = editorFilm();

        if (editing != null && editing != film && film != null && editing.getId().equals(film.getId()))
        {
            for (Replay other : editing.replays.getList())
            {
                if (other.getId().equals(replay.getId()))
                {
                    source = other;

                    break;
                }
            }
        }

        if (!(source.form.get() instanceof ModelForm from) || from == target)
        {
            return;
        }

        syncLattice(from, target);

        if (!from.vehicleIk.get() && !target.vehicleIk.get())
        {
            return;
        }

        copy(from.vehicleIk, target.vehicleIk);
        copy(from.invertWheelRoll, target.invertWheelRoll);
        copy(from.pitchFromReplay, target.pitchFromReplay);
        copy(from.wheelRadius, target.wheelRadius);
        copy(from.suspensionStrength, target.suspensionStrength);
        copy(from.steeringMultiplier, target.steeringMultiplier);
        copy(from.seatHeight, target.seatHeight);
        copy(from.stepHeight, target.stepHeight);
        copy(from.driverCamera, target.driverCamera);
        copy(from.driverCameraX, target.driverCameraX);
        copy(from.driverCameraY, target.driverCameraY);
        copy(from.driverCameraZ, target.driverCameraZ);
        copy(from.driverCameraYaw, target.driverCameraYaw);
        copy(from.driverCameraPitch, target.driverCameraPitch);
        copy(from.groundSettle, target.groundSettle);
        copy(from.seatsCustom, target.seatsCustom);
        copy(from.driverHud, target.driverHud);
        copy(from.passengerHud, target.passengerHud);

        for (int i = 0; i < ModelForm.SEATS.length; i++)
        {
            copy(from.seatEnabled[i], target.seatEnabled[i]);
            copy(from.seatX[i], target.seatX[i]);
            copy(from.seatY[i], target.seatY[i]);
            copy(from.seatZ[i], target.seatZ[i]);
        }

        copy(from.boneBody, target.boneBody);
        copy(from.boneSteering, target.boneSteering);
        copy(from.boneWheelFL, target.boneWheelFL);
        copy(from.boneWheelFR, target.boneWheelFR);
        copy(from.boneWheelRL, target.boneWheelRL);
        copy(from.boneWheelRR, target.boneWheelRR);

        if (!sameParts(from.vehicleParts, target.vehicleParts))
        {
            target.vehicleParts.fromData(from.vehicleParts.toData());
        }
    }

    /** The lattice too, for a model that is no vehicle: bent in the editor, it bends in the playing film at once */
    private static void syncLattice(ModelForm from, ModelForm target)
    {
        copy(from.lattice.enabled, target.lattice.enabled);
        copy(from.lattice.dimensions, target.lattice.dimensions);
        copy(from.lattice.targetBone, target.lattice.targetBone);
        copy(from.lattice.shiftX, target.lattice.shiftX);
        copy(from.lattice.shiftY, target.lattice.shiftY);
        copy(from.lattice.shiftZ, target.lattice.shiftZ);
        copy(from.lattice.bulgeEnabled, target.lattice.bulgeEnabled);

        if (!Arrays.equals(from.lattice.center.getOriginalValue(), target.lattice.center.getOriginalValue()))
        {
            target.lattice.center.set(from.lattice.center.getOriginalValue().clone());
        }

        if (!Arrays.equals(from.lattice.bulge.getOriginalValue(), target.lattice.bulge.getOriginalValue()))
        {
            target.lattice.bulge.set(from.lattice.bulge.getOriginalValue().clone());
        }

        /* The saved shapes, not what a track gives the playing form this tick: that is the film's */
        if (!Arrays.equals(from.lattice.points.getOriginalValue(), target.lattice.points.getOriginalValue()))
        {
            target.lattice.points.set(from.lattice.points.getOriginalValue().clone());
        }
    }

    private static <T> void copy(BaseValueBasic<T> from, BaseValueBasic<T> to)
    {
        if (!Objects.equals(from.get(), to.get()))
        {
            to.set(from.get());
        }
    }

    private static boolean sameParts(ValueVehicleParts a, ValueVehicleParts b)
    {
        List<ValueVehicleParts.Part> x = a.getAllTyped();
        List<ValueVehicleParts.Part> y = b.getAllTyped();

        if (x.size() != y.size())
        {
            return false;
        }

        for (int i = 0; i < x.size(); i++)
        {
            ValueVehicleParts.Part p = x.get(i);
            ValueVehicleParts.Part q = y.get(i);

            if (!p.bone.get().equals(q.bone.get()) || !p.axis.get().equals(q.axis.get()) || !p.angle.get().equals(q.angle.get()))
            {
                return false;
            }
        }

        return true;
    }

    private static UIFilmPanel editorPanel()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        return dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
    }

    private static Film editorFilm()
    {
        UIFilmPanel panel = editorPanel();

        return panel == null ? null : panel.getData();
    }

    /* Questions from the film, the input and the renderers */

    public static boolean isActive()
    {
        return session != null;
    }

    public static boolean isRidden(Film film, Replay replay)
    {
        if (replay == null || film == null)
        {
            return false;
        }

        Take parked = parked();

        if (parked != null && parked.filmId.equals(film.getId()) && parked.replayId.equals(replay.getId()))
        {
            return true;
        }

        return session != null && !session.follow && session.isOf(film) && session.replayId.equals(replay.getId());
    }

    public static boolean isRidden(IEntity entity)
    {
        return (session != null && !session.follow || parked() != null) && pinned.contains(entity);
    }

    /**
     * A drive just over and not written yet - its take still watching the door shut behind the
     * driver: the vehicle stays where it was left until the drive is in its replay, instead of the
     * film putting it back where it was boarded for those ticks
     */
    private static Take parked()
    {
        return session == null && take != null && take.motion && !take.pose.isEmpty() ? take : null;
    }

    /** The ridden vehicle's actor, for the take that records the replay at {@code index} */
    public static IEntity getRecorded(Film film, int index)
    {
        if (session == null || session.follow || film == null || !session.isOf(film))
        {
            return null;
        }

        List<Replay> replays = film.replays.getList();

        return index >= 0 && index < replays.size() && replays.get(index).getId().equals(session.replayId) ? session.entity : null;
    }

    /** The steering of a ridden vehicle's driver (-1 right, 1 left), NaN when nobody drives this actor */
    public static float getSteering(IEntity entity)
    {
        return session != null && session.driver && session.phase == Phase.SEATED && pinned.contains(entity) ? session.steer : Float.NaN;
    }

    /**
     * Whether this is an actor of a film standing in the world - playing, recording, or open in the
     * film editor - not a form previewed in a menu
     */
    public static boolean isFilmActor(IEntity entity)
    {
        if (entity == null)
        {
            return false;
        }

        for (BaseFilmController controller : controllers())
        {
            if (controller.entities.containsValue(entity))
            {
                return true;
            }
        }

        UIFilmPanel panel = editorPanel();

        return panel != null && panel.getController() != null && panel.getController().getEntities().containsValue(entity);
    }

    public static boolean isSeated(Entity entity)
    {
        return entity != null && entity == seated;
    }

    /** Whether this is the seated player, or the server's copy of them in single player */
    public static boolean isSeatedAnywhere(Entity entity)
    {
        Entity player = seated;

        return player != null && entity != null && (entity == player || entity.getUuid().equals(player.getUuid()));
    }

    public static float getEyeHeight(Entity entity)
    {
        return entity == seated ? eyeHeight : -1F;
    }

    /* The right click */

    /**
     * A right click, before the game takes it: true when it was the vehicle's (a door, a seat, or
     * anything at all while seated) and the game must not use it.
     */
    public static boolean onUse()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || mc.world == null)
        {
            return false;
        }

        if (session != null)
        {
            return true;
        }

        if (useHeld)
        {
            return useHandled;
        }

        useHeld = true;
        useHandled = false;

        Pick pick = pick(mc);

        if (pick == null)
        {
            return false;
        }

        /* A block or a mob in front of the part is what the click is for */
        HitResult target = mc.crosshairTarget;

        if (target != null && target.getType() != HitResult.Type.MISS && mc.player.getCameraPosVec(mc.getTickDelta()).distanceTo(target.getPos()) < pick.distance - 0.05F)
        {
            return false;
        }

        useHandled = true;
        mc.player.swingHand(Hand.MAIN_HAND);

        VehicleIkState state = state(pick.entity);

        if (pick.seat)
        {
            board(mc, pick);
        }
        else if (state != null)
        {
            String bone = pick.part.bone.get();
            float angle = (float) Math.toRadians(pick.part.angle.get());
            float from = state.getOpenness(bone) * angle;

            state.toggle(bone);
            clicks.add(new DoorClick(pick.candidate, pick.part, from, state.isOpen(bone) ? angle : 0F, state));
        }

        return true;
    }

    private static Pick pick(MinecraftClient mc)
    {
        float delta = mc.getTickDelta();
        Vec3d eye = mc.player.getCameraPosVec(delta);
        Vec3d look = mc.player.getRotationVec(delta);
        Vector3f direction = new Vector3f((float) look.x, (float) look.y, (float) look.z).normalize();
        Pick best = null;

        for (Candidate candidate : candidates())
        {
            IEntity entity = candidate.entity;
            ModelForm form = candidate.form;
            Model model = VehicleGeometry.getModel(form);

            if (!near(entity, eye))
            {
                continue;
            }

            for (ValueVehicleParts.Part part : VehicleGeometry.parts(form))
            {
                String bone = part.bone.get();
                float t = hit(model, form, entity, bone, delta, eye, direction);

                if (t >= 0F && t <= REACH && (best == null || t < best.distance))
                {
                    best = new Pick(candidate, part, false, t, eye, direction);
                }

                if (!part.isDoor())
                {
                    continue;
                }

                Vector3f seat = seatInWorld(model, form, entity, bone, delta, eye);

                t = seat == null ? -1F : VehicleGeometry.raySphere(seat, direction, SEAT_RADIUS);

                if (t >= 0F && t <= REACH && (best == null || t < best.distance))
                {
                    best = new Pick(candidate, part, true, t, eye, direction);
                }
            }

            /* A seat or the steering wheel itself boards too, into the seat of the quarter it is in */
            for (String bone : FormUtilsClient.getBones(form))
            {
                if (!bone.equals(form.boneSteering.get()) && !VehicleBoneDetector.isSeat(bone))
                {
                    continue;
                }

                float t = hit(model, form, entity, bone, delta, eye, direction);

                if (t >= 0F && t <= REACH && (best == null || t < best.distance))
                {
                    best = new Pick(candidate, null, true, t, eye, direction);
                }
            }
        }

        if (best != null)
        {
            return best;
        }

        /* Nothing of it in particular, but the vehicle all the same: the body boards the seat of the
         * quarter the click lands in, whatever its bones are called */
        for (Candidate candidate : candidates())
        {
            IEntity entity = candidate.entity;
            ModelForm form = candidate.form;
            Model model = VehicleGeometry.getModel(form);
            Vector3f min = new Vector3f();
            Vector3f max = new Vector3f();

            if (!near(entity, eye) || !VehicleGeometry.modelBounds(model, min, max))
            {
                continue;
            }

            Matrix4f root = VehicleGeometry.toWorld(entity, form, "", delta, eye.x, eye.y, eye.z);
            float t = root == null ? -1F : VehicleGeometry.rayBox(root, direction, min, max);

            if (t >= 0F && t <= REACH && (best == null || t < best.distance))
            {
                best = new Pick(candidate, null, true, t, eye, direction);
            }
        }

        return best;
    }

    private static boolean near(IEntity entity, Vec3d eye)
    {
        double dx = entity.getX() - eye.x;
        double dy = entity.getY() - eye.y;
        double dz = entity.getZ() - eye.z;

        return dx * dx + dy * dy + dz * dz <= 16D * 16D;
    }

    /**
     * Where along the look a bone is hit, or -1: its box when the model gives it one, otherwise a
     * ball around its pivot - a bone of a model without boxes (a mesh, another format), or an empty
     * one, is still there to click.
     */
    private static float hit(Model model, ModelForm form, IEntity entity, String bone, float delta, Vec3d eye, Vector3f direction)
    {
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        if (model != null && model.getGroup(bone) != null && VehicleGeometry.bounds(model, bone, min, max))
        {
            Matrix4f matrix = VehicleGeometry.toWorld(entity, form, bone, delta, eye.x, eye.y, eye.z);

            if (matrix != null)
            {
                return VehicleGeometry.rayBox(matrix, direction, min, max);
            }
        }

        Vector3f origin = VehicleGeometry.boneOrigin(entity, form, bone, delta, eye.x, eye.y, eye.z);

        return origin == null ? -1F : VehicleGeometry.raySphere(origin, direction, BONE_RADIUS);
    }

    /**
     * The seat behind a door, in the world relative to {@code origin}: carried by the door's parent,
     * so it stays put while the door swings. A door without a box of its own has its seat pulled in
     * from its pivot most of the way to the vehicle's middle.
     */
    private static Vector3f seatInWorld(Model model, ModelForm form, IEntity entity, String door, float delta, Vec3d origin)
    {
        Vector3f seat = VehicleGeometry.seat(model, form, door);
        ModelGroup group = model == null ? null : model.getGroup(door);
        Matrix4f parent = VehicleGeometry.toWorld(entity, form, group == null || group.parent == null ? "" : group.parent.id, delta, origin.x, origin.y, origin.z);

        if (seat != null && parent != null)
        {
            return VehicleGeometry.transform(parent, seat);
        }

        Vector3f pivot = VehicleGeometry.boneOrigin(entity, form, door, delta, origin.x, origin.y, origin.z);
        Matrix4f root = VehicleGeometry.toWorld(entity, form, "", delta, origin.x, origin.y, origin.z);

        if (pivot == null || root == null)
        {
            return null;
        }

        Vector3f middle = VehicleGeometry.transform(root, VehicleGeometry.center(model, form));

        return new Vector3f(middle.x + (pivot.x - middle.x) * 0.45F, pivot.y, middle.z + (pivot.z - middle.z) * 0.45F);
    }

    private static List<Candidate> candidates()
    {
        List<Candidate> list = new ArrayList<>();
        Films films = BBSModClient.getFilms();
        List<BaseFilmController> controllers = new ArrayList<>(films.getControllers());

        if (films.getRecorder() != null)
        {
            controllers.add(films.getRecorder());
        }

        for (BaseFilmController controller : controllers)
        {
            if (controller.film == null)
            {
                continue;
            }

            List<Replay> replays = controller.film.replays.getList();

            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);
                IEntity entity = controller.entities.get(replay.getId());

                /* The take being recorded is the player, not an actor standing there */
                if (entity == null || (i == controller.exception && !isRidden(controller.film, replay)))
                {
                    continue;
                }

                if (entity.getForm() instanceof ModelForm form && form.vehicleIk.get())
                {
                    list.add(new Candidate(controller, replay, entity, form));
                }
            }
        }

        return list;
    }

    private static VehicleIkState state(IEntity entity)
    {
        return entity != null && entity.getForm() != null && FormUtilsClient.getRenderer(entity.getForm()) instanceof ModelFormRenderer renderer ? renderer.getVehicle() : null;
    }

    /* Getting in */

    private static void board(MinecraftClient mc, Pick pick)
    {
        IEntity entity = pick.entity;
        ModelForm form = pick.candidate.form;
        Model model = VehicleGeometry.getModel(form);
        float delta = mc.getTickDelta();
        Session ride = new Session(pick.candidate);
        VehicleGeometry.Wheels wheels = VehicleGeometry.wheels(form);
        VehicleSeats.Layout layout = VehicleSeats.layout(form);

        /* Everything below in the vehicle's own frame (front +Z, left +X), as the model stands at rest:
         * the frame its seats, wheels and box are worked out in */
        double ox = lerp(entity.getPrevX(), entity.getX(), delta);
        double oy = lerp(entity.getPrevY(), entity.getY(), delta);
        double oz = lerp(entity.getPrevZ(), entity.getZ(), delta);
        float yaw = (float) lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), delta);

        /* The seat of the quarter the click landed in (or the nearest, when that quarter has none) */
        int seat = layout.pick(ride.local(new Vector3f((float) pick.at.x, (float) pick.at.y, (float) pick.at.z), ox, oy, oz, yaw));

        if (seat < 0)
        {
            return;
        }

        boolean leftSide = layout.isLeft(seat);
        float side = leftSide ? layout.left : -layout.left;
        Vector3f middle = new Vector3f(layout.middle);

        ride.middle = middle;
        ride.front = layout.front;
        ride.seat = new Vector3f(layout.seats[seat]);
        ride.contact = wheels.middle.y;
        ride.footMin.set(wheels.footMin);
        ride.footMax.set(wheels.footMax);

        for (int i = 0; i < 4; i++)
        {
            ride.wheels[i] = new float[] {wheels.points[i].x, wheels.points[i].z};
        }

        /* The door of that seat: on its side, the one nearest along the vehicle */
        ValueVehicleParts.Part door = null;
        Vector3f doorMiddle = null;
        float best = Float.POSITIVE_INFINITY;

        for (ValueVehicleParts.Part part : VehicleGeometry.parts(form))
        {
            Vector3f point = part.isDoor() ? doorPoint(model, form, entity, part.bone.get(), delta) : null;

            if (point == null)
            {
                continue;
            }

            Vector3f local = ride.local(point, ox, oy, oz, yaw);
            float distance = Math.abs(local.z - ride.seat.z);

            if (((local.x - middle.x) * layout.left >= 0F) == leftSide && distance < best)
            {
                best = distance;
                door = part;
                doorMiddle = local;
            }
        }

        ride.door = door;
        /* Beside the vehicle on the seat's side - the driver on the left, a passenger on the right - at
         * 1.2 blocks from where it stands (further for a wider one), by the seat: from the vehicle's
         * own place and heading, never from where a door was found */
        float reach = Math.max(1.2F, (side > 0F ? ride.footMax.x : -ride.footMin.x) + 0.5F);

        ride.exit = new Vector3f(side * reach, 0F, MathUtils.clamp(ride.seat.z, ride.footMin.z, ride.footMax.z));

        /* Only the front left seat drives, and only a vehicle standing still where its film is now:
         * one moving along a drive its replay holds goes on along it, the actor riding with it, until
         * it stops (see takeOver). Anybody else rides along whatever drive the replay holds */
        Replay replay = pick.candidate.replay;
        int replayTick = replay.getTick(pick.candidate.controller.getTick());

        ride.driverSeat = seat == VehicleSeats.FL;
        ride.driver = ride.driverSeat && !isMoving(replay, replayTick);
        ride.follow = !ride.driver && hasDrive(replay);
        ride.replaces = ride.driver && hasDriveAfter(replay, replayTick);
        ride.x = ride.prevX = entity.getX();
        ride.z = ride.prevZ = entity.getZ();
        ride.yaw = ride.prevYaw = entity.getBodyYaw();
        ride.settle(entity, state(entity));

        /* What the ride writes into the vehicle's replay, from the tick it was boarded on: the drive
         * (its place, heading and climb, and its parts), or for anybody else only the door they
         * went through. Not while a take of that very replay is being recorded with the record key:
         * that take has it */
        if (take != null)
        {
            commit(take);
        }

        take = isRecordedByKey(ride) || (!ride.driver && door == null) ? null : new Take(ride, replayTick, pick.candidate.controller.getTick(), ride.driver);

        VehicleIkState state = state(entity);

        if (state != null && door != null && !state.isOpen(door.bone.get()))
        {
            state.setOpen(door.bone.get(), true);
            ride.timer = DOOR_WAIT;
        }
        else
        {
            ride.timer = 1;
        }

        ride.phase = Phase.BOARDING;
        session = ride;
    }

    /** Whether the vehicle's replay is the one a take with the record key is recording right now */
    private static boolean isRecordedByKey(Session ride)
    {
        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder == null || recorder.film == null || !recorder.film.getId().equals(ride.filmId))
        {
            return false;
        }

        List<Replay> replays = recorder.film.replays.getList();

        return recorder.exception >= 0 && recorder.exception < replays.size() && replays.get(recorder.exception).getId().equals(ride.replayId);
    }

    /** The middle of a door in the world: of its box, or its pivot when it has none */
    private static Vector3f doorPoint(Model model, ModelForm form, IEntity entity, String bone, float delta)
    {
        ModelGroup group = model == null ? null : model.getGroup(bone);
        Matrix4f parent = VehicleGeometry.toWorld(entity, form, group == null || group.parent == null ? "" : group.parent.id, delta, 0D, 0D, 0D);
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        return parent != null && VehicleGeometry.bounds(model, bone, min, max)
            ? VehicleGeometry.transform(parent, min.add(max).mul(0.5F))
            : VehicleGeometry.boneOrigin(entity, form, bone, delta, 0D, 0D, 0D);
    }

    /**
     * Whether a vehicle's replay holds a drive anywhere: its place or heading keyed to more than one
     * value. A passenger rides along it, so their take never writes over the motion the scene was
     * cut to.
     */
    private static boolean hasDrive(Replay replay)
    {
        return hasDriveAfter(replay, Integer.MIN_VALUE);
    }

    /** Whether a vehicle's replay moves it anywhere after this tick: a drive a new one from here would replace */
    private static boolean hasDriveAfter(Replay replay, int tick)
    {
        return replay != null && (varies(replay.keyframes.x, tick) || varies(replay.keyframes.z, tick) || varies(replay.keyframes.yaw, tick));
    }

    /** Whether a channel holds a value after this tick other than the one it has at it */
    private static boolean varies(KeyframeChannel<Double> channel, int tick)
    {
        List<Keyframe<Double>> keys = channel.getKeyframes();

        if (keys.isEmpty())
        {
            return false;
        }

        double at = tick == Integer.MIN_VALUE ? keys.get(0).getValue() : channel.interpolateDouble(tick);

        for (Keyframe<Double> key : keys)
        {
            if (key.getTick() > tick && Math.abs(key.getValue() - at) > 1.0E-3D)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether a vehicle's replay has it moving at this tick: its place or heading changing over the
     * tick before or after. A vehicle that is is driven by its replay; one standing still (parked,
     * or stopped where the last driver left it) may be driven on from there.
     */
    private static boolean isMoving(Replay replay, int tick)
    {
        if (replay == null)
        {
            return false;
        }

        ReplayKeyframes keys = replay.keyframes;

        for (int step : new int[] {-1, 1})
        {
            double dx = keys.x.interpolateDouble(tick + step) - keys.x.interpolateDouble(tick);
            double dz = keys.z.interpolateDouble(tick + step) - keys.z.interpolateDouble(tick);
            double turn = MathHelper.wrapDegrees(keys.yaw.interpolateDouble(tick + step) - keys.yaw.interpolateDouble(tick));

            if (dx * dx + dz * dz > MOVING_STEP * MOVING_STEP || Math.abs(turn) > 0.5D)
            {
                return true;
            }
        }

        return false;
    }

    /* The tick */

    public static void tick(MinecraftClient mc)
    {
        VehicleIkState.tickDoors();
        VehicleIkState.tickGround();

        for (int i = clicks.size() - 1; i >= 0; i--)
        {
            DoorClick click = clicks.get(i);

            if (--click.ticks <= 0)
            {
                clicks.remove(i);
                writeClick(click);
            }
        }

        for (int i = closing.size() - 1; i >= 0; i--)
        {
            Closing close = closing.get(i);

            if (--close.ticks <= 0)
            {
                close.state.setOpen(close.bone, false);
                closing.remove(i);
            }
        }

        if (mc.options.useKey != null && !mc.options.useKey.isPressed())
        {
            useHeld = false;
        }

        /* The door key only means something in a seat: presses from before are not kept for later */
        if (doorKey != null && (session == null || session.phase != Phase.SEATED))
        {
            while (doorKey.wasPressed())
            {}
        }

        if (session == null)
        {
            pinned.clear();

            /* Out of the vehicle: the take still watches the door shut behind the driver */
            if (take != null && take.tail(state(take.entity)))
            {
                commit(take);
                take = null;
            }

            Take parked = parked();

            if (parked != null)
            {
                parked.pinAll();
            }

            return;
        }

        Session ride = session;
        ClientPlayerEntity player = mc.player;

        if (player == null || mc.world == null || (ride.player != null && ride.player != player))
        {
            end(false);

            return;
        }

        ride.player = player;

        if (!ride.rebind())
        {
            /* The film went away with the vehicle in it */
            end(true);

            return;
        }

        boolean sneaking = mc.options.sneakKey.isPressed();
        boolean sneakPressed = sneaking && !ride.sneaking;

        ride.sneaking = sneaking;
        ride.prevX = ride.x;
        ride.prevY = ride.y;
        ride.prevZ = ride.z;
        ride.prevYaw = ride.yaw;
        ride.prevPitch = ride.pitch;
        ride.prevSpeed = ride.speed;

        if (ride.phase == Phase.BOARDING)
        {
            if (--ride.timer <= 0)
            {
                ride.phase = Phase.SEATED;
                seated = player;
                player.getAbilities().flying = false;
                closeLater(ride, 4);
                boolean custom = ride.driverSeat && ride.form.driverCamera.get();

                player.setYaw(custom ? ride.yaw + ride.form.driverCameraYaw.get() : ride.heading());
                player.prevYaw = player.getYaw();
                player.setPitch(custom ? ride.form.driverCameraPitch.get() : 0F);
                player.prevPitch = player.getPitch();
                ride.viewSet = false;

                if (ride.replaces)
                {
                    /* Said once, out loud: what this drive is about to write over */
                    notify("bbs.vehicle.takeover_warning");
                }
                else if (!ride.hasDashboard())
                {
                    message(ride.driver ? "bbs.vehicle.seated_driver" : ride.driverSeat ? "bbs.vehicle.seated_locked" : "bbs.vehicle.seated_passenger");
                }
            }
        }
        else if (ride.phase == Phase.SEATED)
        {
            while (doorKey != null && doorKey.wasPressed())
            {
                VehicleIkState state = state(ride.entity);

                if (state != null)
                {
                    state.toggle(ride.doorBone());
                }
            }

            if (sneakPressed)
            {
                VehicleIkState state = state(ride.entity);

                ride.phase = Phase.EXITING;
                ride.timer = state != null && ride.door != null && !state.isOpen(ride.doorBone()) ? DOOR_WAIT : 1;

                if (state != null)
                {
                    state.setOpen(ride.doorBone(), true);
                }
            }
            else if (ride.follow && ride.driverSeat)
            {
                /* In the driver's seat of a vehicle its replay drives: once that drive has stopped,
                 * putting a foot down takes the wheel */
                Candidate candidate = ride.current();

                ride.canTakeOver = candidate != null && !isMoving(candidate.replay, candidate.replay.getTick(candidate.controller.getTick()));

                if (ride.canTakeOver && (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()))
                {
                    takeOver(ride, candidate);
                }
            }
        }

        if (ride.follow)
        {
            /* Riding along a drive the film plays: placed where the film put the vehicle, once it
             * has (see init), so the seat never trails the body by a tick */
        }
        else if (ride.driver && ride.phase == Phase.SEATED)
        {
            drive(mc, ride);
        }
        else
        {
            /* Getting out (or sitting on the passenger's side): the vehicle rolls to a stop */
            ride.speed = approach(ride.speed, 0F, BRAKING * TICK);
            ride.steer = approach(ride.steer, 0F, STEER_RATE * TICK);
            ride.yawRate = 0F;
            move(mc, ride, ride.speed * TICK);
        }

        if (!ride.follow)
        {
            ride.pinAll();
            sit(player, ride);
        }

        if (take != null && take.ride == ride)
        {
            take.sample(ride, state(ride.entity));
        }

        if (ride.phase == Phase.EXITING && --ride.timer <= 0)
        {
            end(true);
        }
    }

    /**
     * Take the wheel of a vehicle whose recorded drive has stopped where the film stands: from this
     * tick on it is driven, and the drive written into its replay from here, over whatever came
     * after (the next driver carries on where the last one got out). Whatever was written of the
     * ride so far (the door) goes in first.
     */
    private static void takeOver(Session ride, Candidate candidate)
    {
        int replayTick = candidate.replay.getTick(candidate.controller.getTick());

        ride.follow = false;
        ride.driver = true;
        ride.canTakeOver = false;
        ride.replaces = hasDriveAfter(candidate.replay, replayTick);
        ride.speed = ride.prevSpeed = 0F;
        ride.steer = 0F;
        ride.yawRate = 0F;
        ride.x = ride.prevX = ride.entity.getX();
        ride.z = ride.prevZ = ride.entity.getZ();
        ride.yaw = ride.prevYaw = ride.entity.getBodyYaw();
        ride.settle(ride.entity, state(ride.entity));

        if (take != null && take.ride == ride)
        {
            commit(take);
            take = null;
        }

        if (!isRecordedByKey(ride))
        {
            take = new Take(ride, replayTick, candidate.controller.getTick(), true);
        }

        notify(ride.replaces ? "bbs.vehicle.takeover_warning" : "bbs.vehicle.taken_over");
    }

    private static void drive(MinecraftClient mc, Session ride)
    {
        GameOptions options = mc.options;
        boolean forward = options.forwardKey.isPressed();
        boolean back = options.backKey.isPressed();
        float speed = ride.speed;

        if (forward && !back)
        {
            speed = speed < 0F ? approach(speed, 0F, BRAKING * TICK) : Math.min(speed + ACCELERATION * TICK * (1F - 0.5F * speed / MAX_FORWARD), MAX_FORWARD);
        }
        else if (back && !forward)
        {
            speed = speed > 0F ? approach(speed, 0F, BRAKING * TICK) : Math.max(speed - ACCELERATION * 0.6F * TICK, -MAX_REVERSE);
        }
        else
        {
            speed = approach(speed, 0F, COASTING * TICK);
        }

        if (options.jumpKey.isPressed())
        {
            speed = approach(speed, 0F, HANDBRAKE * TICK);
        }

        float input = (options.leftKey.isPressed() ? 1F : 0F) - (options.rightKey.isPressed() ? 1F : 0F);

        ride.steer = approach(ride.steer, input, STEER_RATE * TICK);
        ride.speed = speed;

        /* The bicycle model: the turning rate this steering angle gives at this speed (a left turn
         * takes Minecraft's yaw down), and the rate eased toward it, so a turn sets in and lets go
         * smoothly instead of the heading snapping. The view turns at this rate every frame */
        float target = (float) -Math.toDegrees(speed * Math.tan(Math.toRadians(ride.steer * MAX_STEER)) / WHEELBASE);

        ride.yawRate += (target - ride.yawRate) * (1F - (float) Math.exp(-YAW_DAMPING * TICK));
        ride.yaw += ride.yawRate * TICK;

        move(mc, ride, speed * TICK);
    }

    /**
     * Roll {@code distance} along the heading (with the yaw this tick already turned to), over the
     * ground. The body is tried at the new place as a set of columns over its whole footprint, each
     * from the step height above the ground the wheels make there up to the roof: anything in one is
     * a wall (or a kerb too tall, or the far side of a pit), and the vehicle stops there, turn and
     * all. A hit faster than {@link #IMPACT_SPEED} is an impact. Then each wheel finds its own
     * ground, and the body takes the plane they make: nose up and down, and side to side.
     */
    private static void move(MinecraftClient mc, Session ride, float distance)
    {
        double step = Math.max(MathUtils.clamp(ride.form.stepHeight.get(), 0F, 1.25F), MICRO_STEP);
        double rad = Math.toRadians(ride.yaw);
        double fx = -Math.sin(rad) * ride.front;
        double fz = Math.cos(rad) * ride.front;
        double nx = ride.x + fx * distance;
        double nz = ride.z + fz * distance;
        boolean moving = Math.abs(distance) > 1.0E-4F || ride.yaw != ride.prevYaw;

        /* Into something is stopped. Already in something (parked against a wall, or the front
         * wheels dropped into a pit so the far side now stands above the step) it may only move
         * so that less of it is in there: it backs out, but never pushes on into the wall */
        int inside = moving ? blocked(mc, ride, nx, nz, ride.yaw, step) : 0;

        if (inside > 0 && inside >= blocked(mc, ride, ride.x, ride.z, ride.prevYaw, step))
        {
            if (Math.abs(ride.speed) > IMPACT_SPEED)
            {
                impact(mc, ride, fx * Math.signum(distance), fz * Math.signum(distance));
            }

            ride.speed = 0F;
            ride.yawRate = 0F;
            ride.yaw = ride.prevYaw;
            nx = ride.x;
            nz = ride.z;
        }

        ride.x = nx;
        ride.z = nz;

        double[] wheels = ride.wheelY;

        for (int i = 0; i < 4; i++)
        {
            wheels[i] = wheel(mc, ride, i, step);
        }

        /* A wheel over a hole hangs down only as far as its suspension reaches: the others hold
         * the body up, and it leans that way instead of dropping in */
        double sum = wheels[0] + wheels[1] + wheels[2] + wheels[3];

        for (int i = 0; i < 4; i++)
        {
            wheels[i] = Math.max(wheels[i], (sum - wheels[i]) / 3D - DROOP);
        }

        /* The vehicle's own height is where its wheels touch, less how far above its feet they do */
        ride.y = (wheels[0] + wheels[1] + wheels[2] + wheels[3]) / 4D - ride.contact;
        ride.pitch = ride.climb();
        ride.roll = ride.lean();
    }

    /** How many of the body's columns, at this place and yaw, run into anything above the step height */
    private static int blocked(MinecraftClient mc, Session ride, double x, double z, float yaw, double step)
    {
        int count = 0;
        float inset = 0.05F;
        float x0 = ride.footMin.x + inset;
        float x1 = ride.footMax.x - inset;
        float z0 = ride.footMin.z + inset;
        float z1 = ride.footMax.z - inset;
        int columnsX = Math.max(2, (int) Math.ceil((x1 - x0) / COLUMN_SPACING) + 1);
        int columnsZ = Math.max(2, (int) Math.ceil((z1 - z0) / COLUMN_SPACING) + 1);
        float angle = -MathUtils.toRad(yaw);
        Vector3f offset = new Vector3f();

        for (int i = 0; i < columnsX; i++)
        {
            for (int j = 0; j < columnsZ; j++)
            {
                float px = x0 + (x1 - x0) * i / (columnsX - 1);
                float pz = z0 + (z1 - z0) * j / (columnsZ - 1);
                double floor = ride.plane(px, pz);
                double bottom = floor + step + STEP_TOLERANCE;
                double top = Math.max(ride.y + ride.footMax.y, bottom + 0.2D);

                offset.set(px, 0F, pz).rotateY(angle);

                double wx = x + offset.x;
                double wz = z + offset.z;
                Box column = new Box(wx - 0.04D, bottom, wz - 0.04D, wx + 0.04D, top, wz + 0.04D);

                if (!mc.world.getBlockCollisions(null, column).iterator().hasNext())
                {
                    continue;
                }

                /* The plane the four wheels make is only a guess between them: what is right under
                 * this column decides. Ground there up to the step above the plane is a rise the
                 * wheels take - the column starts a step above it instead, and only what is still
                 * in it then is a wall */
                Double under = ground(mc, wx, floor, wz, step);

                if (under != null && !under.isNaN() && under > floor)
                {
                    double lifted = under + step + STEP_TOLERANCE;
                    Box higher = new Box(wx - 0.04D, lifted, wz - 0.04D, wx + 0.04D, Math.max(top, lifted + 0.2D), wz + 0.04D);

                    if (!mc.world.getBlockCollisions(null, higher).iterator().hasNext())
                    {
                        continue;
                    }
                }

                count += 1;
            }
        }

        return count;
    }

    /** One wheel's height: up toward a higher ground a share at a time, down a drop at falling speed */
    private static double wheel(MinecraftClient mc, Session ride, int index, double step)
    {
        double current = ride.wheelY[index];
        Vector3f offset = ride.world(new Vector3f(ride.wheels[index][0], 0F, ride.wheels[index][1]));
        Double found = ground(mc, ride.x + offset.x, current, ride.z + offset.z, step);

        if (found == null)
        {
            return current;
        }

        double target = Double.isNaN(found) ? current - AXLE_FALL : found;

        if (target > current)
        {
            double next = current + (target - current) * AXLE_RISE;

            return target - next < 0.01D ? target : next;
        }

        return Math.max(target, current - AXLE_FALL);
    }

    /**
     * The ground under a point: the top of what stands there, up to {@code step} above {@code y}
     * (and a little more, so a half block is a half block whatever its collision box rounds to).
     * Null when it is a wall (taller than that), NaN when there is nothing within four blocks below.
     */
    private static Double ground(MinecraftClient mc, double x, double y, double z, double step)
    {
        Vec3d from = new Vec3d(x, y + step + STEP_TOLERANCE, z);
        Vec3d to = new Vec3d(x, y - 4D, z);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

        if (hit.getType() == HitResult.Type.MISS)
        {
            return Double.NaN;
        }

        if (hit.isInsideBlock() || hit.getPos().y - y > step + STEP_TOLERANCE)
        {
            return null;
        }

        return hit.getPos().y;
    }

    /** A hit at speed: a thud and sparks where the bumper met it, and a mark in the take */
    private static void impact(MinecraftClient mc, Session ride, double dx, double dz)
    {
        float reach = Math.max(Math.abs(ride.footMax.z), Math.abs(ride.footMin.z));
        double x = ride.x + dx * reach;
        double y = ride.y + 0.6D;
        double z = ride.z + dz * reach;
        float strength = MathUtils.clamp(Math.abs(ride.speed) / MAX_FORWARD, 0.3F, 1F);

        mc.world.playSound(x, y, z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, strength, 0.7F, false);
        mc.world.playSound(x, y, z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.NEUTRAL, strength * 0.8F, 0.9F, false);

        for (int i = 0; i < 24; i++)
        {
            mc.world.addParticle(ParticleTypes.CRIT, x, y, z, (Math.random() - 0.5D) * 0.6D, Math.random() * 0.4D, (Math.random() - 0.5D) * 0.6D);
        }

        if (take != null && take.ride == ride)
        {
            take.impacts.add(take.pose.size());
        }

        crush(ride, strength);
    }

    /**
     * A hit crumples the lattice (when the model has one on, with its vehicle IK): the points at the
     * end that hit are pushed back toward the middle by the hit's force, squeezed in at the sides and
     * the roof a little, each by a slightly different amount so the metal does not fold evenly. Shown
     * at once, and kept in the take as the lattice before and after, keyed to the tick of the hit.
     */
    private static void crush(Session ride, float strength)
    {
        ModelForm form = ride.form;
        Vector3f[] box = form.vehicleIk.get() && form.lattice.enabled.get() ? LatticeDeform.box(form) : null;

        if (box == null)
        {
            return;
        }

        ValueLattice lattice = form.lattice;
        float width = box[1].x - box[0].x;
        float height = box[1].y - box[0].y;
        float length = box[1].z - box[0].z;
        /* Toward the model's front (+Z of the actor's frame when it faces that way) when going forward, the rear when backing */
        float end = ride.front * (ride.speed >= 0F ? 1F : -1F);
        float depth = strength * 0.35F * length;
        int d = lattice.dimensions();
        float[] before = lattice.points.getOriginalValue().clone();
        float[] after = before.clone();
        float limit = 0.6F * length;

        for (int n = 0; n < lattice.count(); n++)
        {
            int[] cell = lattice.cell(n);
            float fy = cell[1] / (d - 1F);
            float fz = cell[2] / (d - 1F);
            float side = cell[0] / (d - 1F) * 2F - 1F;
            float weight = end > 0F ? fz : 1F - fz;

            if (weight <= 0F)
            {
                continue;
            }

            float uneven = 0.75F + (float) Math.random() * 0.5F;
            int slot = lattice.slot(n) * 3;

            after[slot] = MathUtils.clamp(after[slot] - side * 0.08F * width * weight * strength * uneven, -limit, limit);
            after[slot + 1] = MathUtils.clamp(after[slot + 1] + 0.1F * height * fy * weight * strength * uneven, -limit, limit);
            after[slot + 2] = MathUtils.clamp(after[slot + 2] - end * depth * weight * uneven, -limit, limit);
        }

        lattice.points.set(after);

        if (take != null && take.ride == ride && take.motion)
        {
            take.crushes.add(new Object[] {take.pose.size(), before, after.clone()});
            notify("bbs.vehicle.crushed");
        }
    }

    /**
     * The player in the seat: their hips on its sitting plane, as the body carries the plane now
     * (tilted with the ground the vehicle stands on, swaying over its wheels), turned with the
     * vehicle - so the take of them, played back, sits exactly there instead of sinking into the
     * seat or hovering over it. The eyes are the seated actor's own, or where the driver's camera
     * was set; the view rides a short spring behind the seat's height, so a step under the wheels
     * lifts it instead of jolting it, while the body stays on the seat.
     */
    private static void sit(ClientPlayerEntity player, Session ride)
    {
        if (ride.phase == Phase.BOARDING)
        {
            return;
        }

        ModelForm form = ride.form;
        VehicleIkState state = state(ride.entity);
        boolean custom = ride.driverSeat && form.driverCamera.get();
        float hips = hipHeight(player);
        Vector3f plane = new Vector3f(ride.seat).add(0F, form.seatHeight.get(), 0F);
        Vector3f seat = state == null ? plane : state.carry(form, plane, 1F);
        Vector3f place = ride.world(seat);
        double feet = ride.y + seat.y - hips;

        /* Under a low seat the feet go a little into the ground, which the server of this game lets
         * them (see VehicleRideMoveMixin); another server would pull them back out every tick, so
         * there they stop on the ground, the hips as near the seat as that lets them be */
        if (!MinecraftClient.getInstance().isIntegratedServerRunning())
        {
            feet = Math.max(feet, ride.y);
        }

        if (!ride.viewSet)
        {
            ride.viewSet = true;
            ride.viewY = feet;
            ride.viewVelocity = 0F;
        }

        float[] view = {(float) (ride.viewY - feet), ride.viewVelocity};

        spring(view, 0F, VIEW_OMEGA);
        ride.viewY = feet + view[0];
        ride.viewVelocity = view[1];

        player.setPosition(ride.x + place.x, feet, ride.z + place.z);
        player.setVelocity(Vec3d.ZERO);
        player.setNoGravity(true);
        player.setOnGround(true);
        player.fallDistance = 0F;
        player.setBodyYaw(ride.heading());

        ride.prevCamera.set(ride.camera);

        if (custom)
        {
            /* The driver's camera is in the cabin too: carried the same way, the eyes where it is
             * and the view moved over to it from where the body sits */
            Vector3f point = new Vector3f(form.driverCameraX.get(), form.driverCameraY.get(), form.driverCameraZ.get());
            Vector3f camera = state == null ? point : state.carry(form, point, 1F);
            Vector3f over = ride.world(new Vector3f(camera).sub(seat));

            eyeHeight = MathUtils.clamp(camera.y - seat.y + hips, 0.1F, 16F);
            ride.camera.set(over.x, view[0], over.z);
        }
        else
        {
            eyeHeight = -1F;
            ride.camera.set(0F, view[0], 0F);
        }
    }

    /**
     * How high a seated actor's hips are over their feet: where its legs meet its body, less half a
     * leg's thickness, as the player is morphed (a model's own legs, scaled with the form), or a
     * player's.
     */
    private static float hipHeight(ClientPlayerEntity player)
    {
        Morph morph = Morph.getMorph(player);
        Form form = morph == null ? null : morph.getForm();
        Model model = form instanceof ModelForm modelForm ? VehicleGeometry.getModel(modelForm) : null;

        if (model == null)
        {
            return PLAYER_HIPS;
        }

        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        float hips = -1F;

        for (ModelGroup group : model.getAllGroups())
        {
            if (VehicleIkState.legSide(group.id) == 0)
            {
                continue;
            }

            float half = VehicleGeometry.bounds(model, group.id, min, max) ? (max.z - min.z) / 32F : 0.125F;

            hips = Math.max(hips, group.initial.translate.y / 16F - half);
        }

        return hips > 0F ? hips * Math.abs(form.transform.get().scale.y) : PLAYER_HIPS;
    }

    /**
     * Where the view goes from the seated player's eyes, in the world: over to the driver's camera
     * when it was set by hand, and up or down by how far the spring the view rides on is behind the
     * seat. Null for anybody else, and in the third person.
     */
    public static Vec3d getCameraOffset(Entity entity, boolean thirdPerson, float tickDelta)
    {
        Session ride = session;

        if (ride == null || thirdPerson || entity == null || entity != seated || ride.phase == Phase.BOARDING)
        {
            return null;
        }

        return new Vec3d(
            lerp(ride.prevCamera.x, ride.camera.x, tickDelta),
            lerp(ride.prevCamera.y, ride.camera.y, tickDelta),
            lerp(ride.prevCamera.z, ride.camera.z, tickDelta)
        );
    }

    /** A critically damped spring over one tick, in small steps (offset and velocity in {@code state}) */
    private static void spring(float[] state, float target, float omega)
    {
        float step = TICK / 4F;

        for (int i = 0; i < 4; i++)
        {
            float acceleration = omega * omega * (target - state[0]) - 2F * omega * state[1];

            state[1] += acceleration * step;
            state[0] += state[1] * step;
        }
    }

    /** Out through the door, onto the ground beside it (or the other side, or the roof, when that is walled in) */
    private static void end(boolean putOut)
    {
        Session ride = session;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        session = null;
        seated = null;
        eyeHeight = -1F;
        pinned.clear();

        if (ride == null || player == null)
        {
            return;
        }

        player.setNoGravity(false);

        if (putOut)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            Vector3f exit = ride.world(ride.exit);
            Vector3f other = ride.world(new Vector3f(2F * ride.middle.x - ride.exit.x, 0F, ride.exit.z));
            Double ground = mc.world == null ? null : ground(mc, ride.x + exit.x, ride.y, ride.z + exit.z, 1.05D);

            if (ground != null)
            {
                player.setPosition(ride.x + exit.x, Double.isNaN(ground) ? ride.y : ground, ride.z + exit.z);
            }
            else if (mc.world != null && (ground = ground(mc, ride.x + other.x, ride.y, ride.z + other.z, 1.05D)) != null)
            {
                player.setPosition(ride.x + other.x, Double.isNaN(ground) ? ride.y : ground, ride.z + other.z);
            }
            else
            {
                player.setPosition(ride.x, ride.y + 2D, ride.z);
            }

            player.setVelocity(Vec3d.ZERO);
            closeLater(ride, 3);
        }
    }

    private static void closeLater(Session ride, int ticks)
    {
        VehicleIkState state = state(ride.entity);

        if (state != null)
        {
            closing.add(new Closing(state, ride.doorBone(), ticks));
        }
    }

    private static void message(String key)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.inGameHud != null)
        {
            String door = doorKey == null ? "G" : doorKey.getBoundKeyLocalizedText().getString();
            String sneak = mc.options.sneakKey.getBoundKeyLocalizedText().getString();

            mc.inGameHud.setOverlayMessage(Text.literal(L10n.lang(key).format(sneak, door).get()), false);
        }
    }

    private static void notify(String key, Object... args)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.inGameHud != null)
        {
            mc.inGameHud.setOverlayMessage(Text.literal(L10n.lang(key).format(args).get()), false);
        }
    }

    /* The dashboard */

    private static final int HUD_MARGIN = 10;
    private static final int HUD_ROW = 11;
    private static final int HUD_GAUGE_WIDTH = 96;
    private static final int HUD_GAUGE_HEIGHT = 36;

    /**
     * The seated player's dashboard, in the bottom right corner: in the driver's seat a speedometer
     * (km/h, a block a second being 3.6 of them) with the gear and a bar of how fast against the
     * vehicle's top speed, and under it the keys the seat answers to, as they are bound; in any other
     * seat the keys only. Hidden with the rest of the HUD (F1), behind BBS's own screens, and when the
     * vehicle's tab switches it off.
     */
    public static void renderHud(Batcher2D batcher, float tickDelta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Session ride = session;

        if (ride == null || ride.phase != Phase.SEATED || mc.player == null || mc.player != seated || mc.options.hudHidden || UIScreen.getCurrentMenu() != null || !ride.hasDashboard())
        {
            return;
        }

        GameOptions options = mc.options;
        FontRenderer font = batcher.getFont();
        List<String[]> hints = new ArrayList<>();

        if (ride.driver)
        {
            hints.add(new String[] {keys(options.forwardKey, options.backKey), hud("throttle")});
            hints.add(new String[] {keys(options.leftKey, options.rightKey), hud("steer")});
            hints.add(new String[] {keys(options.jumpKey), hud("handbrake")});
        }
        else if (ride.canTakeOver)
        {
            hints.add(new String[] {keys(options.forwardKey, options.backKey), hud("take_over")});
        }

        hints.add(new String[] {doorKey == null ? "G" : keys(doorKey), hud(ride.driverSeat ? "door" : "door_passenger")});
        hints.add(new String[] {keys(options.sneakKey), hud(ride.driverSeat ? "exit" : "exit_passenger")});

        int right = mc.getWindow().getScaledWidth() - HUD_MARGIN;
        int y = mc.getWindow().getScaledHeight() - HUD_MARGIN - hints.size() * HUD_ROW;

        for (int i = 0; i < hints.size(); i++)
        {
            String key = hints.get(i)[0];
            String action = hints.get(i)[1];
            int actionWidth = font.getWidth(action);
            int keyWidth = font.getWidth(key) + 6;
            int rowY = y + i * HUD_ROW;
            int keyX = right - actionWidth - 4 - keyWidth;

            batcher.box(keyX, rowY, keyX + keyWidth, rowY + HUD_ROW - 1, Colors.A50);
            batcher.textShadow(key, keyX + 3, rowY + 1, Colors.WHITE);
            batcher.textShadow(action, right - actionWidth, rowY + 1, 0xFFD8D8D8);
        }

        if (ride.driverSeat)
        {
            renderGauge(batcher, font, ride, right, y - 4, tickDelta);
        }
    }

    private static void renderGauge(Batcher2D batcher, FontRenderer font, Session ride, int right, int bottom, float tickDelta)
    {
        float speed = (float) lerp(ride.prevSpeed, ride.speed, tickDelta);
        int kmh = Math.round(Math.abs(speed) * 3.6F);
        String gear = ride.follow ? "" : speed < -0.05F ? "R" : Math.abs(speed) < 0.05F ? "N" : "D";
        String number = String.valueOf(kmh);
        String unit = hud("unit");
        int left = right - HUD_GAUGE_WIDTH;
        int top = bottom - HUD_GAUGE_HEIGHT;
        float fill = MathUtils.clamp(Math.abs(speed) / Math.max(MAX_FORWARD, Math.abs(speed)), 0F, 1F);
        int barY = bottom - 7;

        batcher.box(left, top, right, bottom, 0x88000000);
        batcher.box(left, top, left + 2, bottom, ride.follow ? 0xFF8AB4F8 : 0xFF6CE08A);

        /* The figure, twice the size of the rest, right of the gear */
        MatrixStack matrices = batcher.getContext().getMatrices();
        int numberWidth = font.getWidth(number) * 2;
        int unitWidth = font.getWidth(unit);
        int numberX = right - 6 - unitWidth - 3 - numberWidth;

        matrices.push();
        matrices.translate(numberX, top + 5, 0F);
        matrices.scale(2F, 2F, 1F);
        batcher.textShadow(number, 0, 0, Colors.WHITE);
        matrices.pop();

        batcher.textShadow(unit, right - 6 - unitWidth, top + 12, 0xFFBBBBBB);

        if (!gear.isEmpty())
        {
            batcher.textShadow(gear, left + 7, top + 9, gear.equals("R") ? 0xFFFF7A6A : 0xFFDDDDDD);
        }
        else
        {
            batcher.textShadow(hud("recorded"), left + 7, top + 3, 0xFF8AB4F8);
        }

        /* How fast against the top speed */
        batcher.box(left + 7, barY, right - 6, barY + 2, 0x44FFFFFF);
        batcher.box(left + 7, barY, left + 7 + (right - 13 - left) * fill, barY + 2, fill > 0.85F ? 0xFFFFB347 : 0xFFFFFFFF);
    }

    private static String keys(KeyBinding... bindings)
    {
        StringBuilder builder = new StringBuilder();

        for (KeyBinding binding : bindings)
        {
            if (builder.length() > 0)
            {
                builder.append('/');
            }

            builder.append(binding.getBoundKeyLocalizedText().getString());
        }

        return builder.toString();
    }

    private static String hud(String key)
    {
        return L10n.lang("bbs.vehicle.hud." + key).get();
    }

    private static float approach(float value, float target, float step)
    {
        return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
    }

    private static double lerp(double a, double b, float t)
    {
        return a + (b - a) * t;
    }

    /* The pieces */

    private enum Phase
    {
        BOARDING, SEATED, EXITING
    }

    private record Candidate(BaseFilmController controller, Replay replay, IEntity entity, ModelForm form)
    {}

    private static class Pick
    {
        public final Candidate candidate;
        public final IEntity entity;
        public final ValueVehicleParts.Part part;
        public final boolean seat;
        public final float distance;
        /** The point of the vehicle the look met, in the world */
        public final Vec3d at;

        public Pick(Candidate candidate, ValueVehicleParts.Part part, boolean seat, float distance, Vec3d eye, Vector3f direction)
        {
            this.candidate = candidate;
            this.entity = candidate.entity;
            this.part = part;
            this.seat = seat;
            this.distance = distance;
            this.at = eye.add(direction.x * distance, direction.y * distance, direction.z * distance);
        }
    }

    private static class DoorClick
    {
        public final Candidate candidate;
        public final ValueVehicleParts.Part part;
        public final float from;
        public final float to;
        public final VehicleIkState state;
        /* Its swing and one tick more */
        public int ticks = (int) DOOR_SWING_TICKS + 1;

        public DoorClick(Candidate candidate, ValueVehicleParts.Part part, float from, float to, VehicleIkState state)
        {
            this.candidate = candidate;
            this.part = part;
            this.from = from;
            this.to = to;
            this.state = state;
        }
    }

    private static class Closing
    {
        public final VehicleIkState state;
        public final String bone;
        public int ticks;

        public Closing(VehicleIkState state, String bone, int ticks)
        {
            this.state = state;
            this.bone = bone;
            this.ticks = ticks;
        }
    }

    /* Writing a drive into the replay */

    /**
     * The drive, into the vehicle's replay from the tick it was boarded on: its place, heading and
     * climb every tick, and each part that moved as a bone track (doors swing about their yaw, lids
     * about their pitch), so playing the film back drives and opens the vehicle the same way. Like
     * a recorded take it replaces what the replay held from there on. Written to the film open in
     * the editor (and saved and synced from there), and to every copy of it playing in the world.
     */
    private static void commit(Take take)
    {
        int count = take.pose.size();

        if (count < 2)
        {
            return;
        }

        ReplayKeyframes keys = new ReplayKeyframes("keyframes");

        for (int i = 0; i < count; i++)
        {
            double[] p = take.pose.get(i);

            keys.x.insert(i, p[0]);
            keys.y.insert(i, p[1]);
            keys.z.insert(i, p[2]);
            keys.yaw.insert(i, p[3]);
            keys.headYaw.insert(i, p[3]);
            keys.bodyYaw.insert(i, p[3]);
            keys.pitch.insert(i, p[4]);
            keys.roll.insert(i, (double) Math.round(p[5] * 100D));
        }

        Map<String, KeyframeChannel<PoseTransform>> parts = new LinkedHashMap<>();

        for (Map.Entry<String, List<Float>> entry : take.doors.entrySet())
        {
            List<Float> values = entry.getValue();
            boolean moved = false;

            for (float value : values)
            {
                moved |= Math.abs(value) > 1.0E-4F;
            }

            if (!moved)
            {
                continue;
            }

            KeyframeChannel<PoseTransform> channel = new KeyframeChannel<>("part", KeyframeFactories.POSE_TRANSFORM);
            boolean lid = take.axes.getOrDefault(entry.getKey(), ValueVehicleParts.AXIS_YAW) == ValueVehicleParts.AXIS_PITCH;

            /* The tick before the take, the part as it stood when the take began (shut, getting in;
             * as it was left, taking the wheel over), whatever the track held there before */
            if (take.start > 0)
            {
                channel.insert(-1, part(lid, take.initial.getOrDefault(entry.getKey(), 0F)));
            }

            for (int i = 0; i < values.size(); i++)
            {
                float value = values.get(i);
                boolean edge = i == 0 || i == values.size() - 1 || value != values.get(i - 1) || value != values.get(i + 1);

                /* Every tick of a swing (it is eased), only its ends where it stands still */
                if (edge)
                {
                    channel.insert(i, part(lid, value));
                }
            }

            /* And shut once the take is over, so the angle never runs on past the drive (a part
             * still swinging when the tail ran out is closed the tick after) */
            if (Math.abs(values.get(values.size() - 1)) > 1.0E-4F)
            {
                channel.insert(values.size(), new PoseTransform());
            }

            parts.put(entry.getKey(), channel);
        }

        if (!take.motion && parts.isEmpty())
        {
            return;
        }

        boolean wrote = writeReplay(take.filmId, take.replayId, (replay) ->
        {
            if (take.motion)
            {
                replay.keyframes.copyOver(keys, take.start);
            }

            for (Map.Entry<String, KeyframeChannel<PoseTransform>> entry : parts.entrySet())
            {
                KeyframeChannel<PoseTransform> channel = boneTrack(replay, entry.getKey());

                if (channel != null)
                {
                    channel.copyOver(entry.getValue(), take.start);
                }
            }

            if (take.motion && replay.form.get() instanceof ModelForm form)
            {
                form.pitchFromReplay.set(true);
            }

            if (take.motion)
            {
                writeImpacts(replay, take);
                writeCrushes(replay, take);
            }
        });

        if (!wrote || !take.motion)
        {
            return;
        }

        /* A paused film stays on the tick the drive ended on, so the vehicle stands where it was
         * left instead of jumping back to where it was boarded */
        for (BaseFilmController controller : controllers())
        {
            if (controller instanceof WorldFilmController world && !(controller instanceof Recorder) && controller.paused && controller.film.getId().equals(take.filmId))
            {
                world.tick = take.filmStart + count - 1;
            }
        }

        notify("bbs.vehicle.recorded", count);
    }

    /** A bone's track in a replay (made when it has none): pose transforms, as every bone track holds */
    @SuppressWarnings("unchecked")
    private static KeyframeChannel<PoseTransform> boneTrack(Replay replay, String bone)
    {
        return (KeyframeChannel<PoseTransform>) replay.properties.getOrCreate(TrackId.bone("", bone));
    }

    /** A part's bone turned open by an angle (radians): a lid about its pitch, a door about its yaw */
    private static PoseTransform part(boolean lid, float angle)
    {
        PoseTransform transform = new PoseTransform();

        if (lid)
        {
            transform.rotate.x = angle;
        }
        else
        {
            transform.rotate.y = angle;
        }

        return transform;
    }

    /**
     * A part swung from outside, into the replay as a bone track: the swing (eased, as it is drawn)
     * ends on the tick the film stands on now and starts a swing's length before it, from where the
     * part was. Then the live swing lets go of the part, so the track alone holds it there and the
     * two never add up: in a paused film the part stays where it was swung to, and a playing one
     * meets the swing where it happened.
     */
    private static void writeClick(DoorClick click)
    {
        Candidate candidate = click.candidate;

        if (!controllers().contains(candidate.controller) || candidate.controller.film == null)
        {
            return;
        }

        int now = candidate.replay.getTick(candidate.controller.getTick());
        int ticks = (int) DOOR_SWING_TICKS;
        int start = Math.max(0, now - ticks);
        String bone = click.part.bone.get();
        boolean lid = !click.part.isDoor();

        boolean wrote = writeReplay(candidate.controller.film.getId(), candidate.replay.getId(), (replay) ->
        {
            KeyframeChannel<PoseTransform> channel = boneTrack(replay, bone);

            if (channel == null)
            {
                return;
            }

            List<Keyframe<PoseTransform>> keys = channel.getKeyframes();

            for (int k = keys.size() - 1; k >= 0; k--)
            {
                double tick = keys.get(k).getTick();

                if (tick >= start && tick <= now)
                {
                    channel.remove(k);
                }
            }

            for (int t = start; t <= now; t++)
            {
                float f = now == start ? 1F : (t - start) / (float) (now - start);
                float eased = f * f * (3F - 2F * f);

                channel.insert(t, part(lid, click.from + (click.to - click.from) * eased));
            }
        });

        if (wrote)
        {
            click.state.forget(bone);
        }
    }

    /* What an impact does when the film plays it back: run at the vehicle, as its actor */
    private static final String IMPACT_SOUND = "playsound minecraft:block.anvil.land neutral @a ~ ~1 ~ 1 0.7";
    private static final String IMPACT_GLASS = "playsound minecraft:block.glass.break neutral @a ~ ~1 ~ 0.8 0.9";
    private static final String IMPACT_SPARKS = "particle minecraft:crit ~ ~1 ~ 0.8 0.4 0.8 0.3 40 force";

    /**
     * The lattice of the drive as keyframes on the replay's lattice track: the shape the vehicle
     * began with at the start of the take, then for every hit the shape the tick before and the
     * crumpled one on the tick itself, so the film crumples the metal at the moment of the crash.
     */
    private static void writeCrushes(Replay replay, Take take)
    {
        if (take.crushes.isEmpty())
        {
            return;
        }

        @SuppressWarnings("unchecked")
        KeyframeChannel<float[]> channel = (KeyframeChannel<float[]>) replay.properties.getOrCreate(replay.form.get(), "lattice_points");

        if (channel == null)
        {
            return;
        }

        KeyframeChannel<float[]> keys = new KeyframeChannel<>("lattice_points", KeyframeFactories.LATTICE);
        int last = -1;

        for (Object[] crush : take.crushes)
        {
            int index = (Integer) crush[0];

            if (last < 0)
            {
                keys.insert(0, (float[]) crush[1]);
                last = 0;
            }

            if (index - 1 > last)
            {
                keys.insert(index - 1, (float[]) crush[1]);
            }

            keys.insert(Math.max(index, last + 1), (float[]) crush[2]);
            last = Math.max(index, last + 1);
        }

        channel.copyOver(keys, take.start);
    }

    /**
     * Every hit of the drive as command action clips on the vehicle's replay (a thud, breaking
     * glass and sparks), on a layer of their own, so they can be moved, swapped for other sounds
     * or dropped like any action. The ones an earlier drive left from this tick on go first.
     */
    private static void writeImpacts(Replay replay, Take take)
    {
        List<String> commands = List.of(IMPACT_SOUND, IMPACT_GLASS, IMPACT_SPARKS);
        int layer = 0;

        for (Clip clip : new ArrayList<>(replay.actions.get()))
        {
            if (clip instanceof CommandActionClip command && commands.contains(command.command.get()) && clip.tick.get() >= take.start)
            {
                replay.actions.remove(clip);

                continue;
            }

            layer = Math.max(layer, clip.layer.get() + 1);
        }

        String title = L10n.lang("bbs.vehicle.impact_clip").get();

        for (int index : take.impacts)
        {
            for (String text : commands)
            {
                CommandActionClip clip = new CommandActionClip();

                clip.command.set(text);
                clip.title.set(title);
                clip.tick.set(take.start + index);
                clip.duration.set(1);
                clip.layer.set(layer);
                replay.actions.addClip(clip);
            }
        }
    }

    /**
     * Change a replay in the film open in the editor (then synced and saved from there) and in
     * every copy of it playing in the world; with the editor on another film, the first copy is
     * saved to the film repository. False when no film by that id holds that replay.
     */
    private static boolean writeReplay(String filmId, String replayId, Consumer<Replay> writer)
    {
        UIFilmPanel panel = editorPanel();
        Film editing = panel == null ? null : panel.getData();
        List<Film> films = new ArrayList<>();

        if (editing != null && editing.getId().equals(filmId))
        {
            films.add(editing);
        }

        for (BaseFilmController controller : controllers())
        {
            if (controller.film != null && controller.film.getId().equals(filmId) && !films.contains(controller.film))
            {
                films.add(controller.film);
            }
        }

        Replay written = null;
        Film first = null;

        for (Film film : films)
        {
            Replay replay = find(film, replayId);

            if (replay == null)
            {
                continue;
            }

            if (film == editing)
            {
                written = replay;
            }

            if (first == null)
            {
                first = film;
            }

            BaseValue.edit(film, (f) -> writer.accept(replay));
        }

        if (first == null)
        {
            return false;
        }

        if (written != null)
        {
            panel.sendReplaySync(editing.replays.getList().indexOf(written));
            panel.save();
        }
        else
        {
            ContentType.FILMS.getRepository().save(first.getId(), first.toData().asMap());
        }

        return true;
    }

    private static Replay find(Film film, String id)
    {
        for (Replay replay : film.replays.getList())
        {
            if (replay.getId().equals(id))
            {
                return replay;
            }
        }

        return null;
    }

    private static List<BaseFilmController> controllers()
    {
        Films films = BBSModClient.getFilms();
        List<BaseFilmController> list = new ArrayList<>(films.getControllers());

        if (films.getRecorder() != null)
        {
            list.add(films.getRecorder());
        }

        return list;
    }

    /** A drive as it goes: the vehicle's place, heading and climb, and how far each part is open, tick by tick */
    private static class Take
    {
        public final Session ride;
        public final String filmId;
        public final String replayId;
        /** The replay's own tick it starts on, and the film's */
        public final int start;
        public final int filmStart;
        public final List<double[]> pose = new ArrayList<>();
        public final Map<String, List<Float>> doors = new LinkedHashMap<>();
        public final Map<String, Integer> axes = new LinkedHashMap<>();
        public final Map<String, Float> angles = new LinkedHashMap<>();
        /** How far each part stood open (radians) when the take began: what it holds up to the take */
        public final Map<String, Float> initial = new LinkedHashMap<>();
        /** The samples a hit came at */
        public final List<Integer> impacts = new ArrayList<>();
        /** The lattice at each hit that crumpled it: the sample, the shape before and the shape after */
        public final List<Object[]> crushes = new ArrayList<>();
        public IEntity entity;
        /** Whether the drive itself is written; a passenger's take holds only the door they used */
        public final boolean motion;
        private int tail = TAKE_TAIL;

        public Take(Session ride, int start, int filmStart, boolean motion)
        {
            this.ride = ride;
            this.motion = motion;
            this.filmId = ride.filmId;
            this.replayId = ride.replayId;
            this.start = start;
            this.filmStart = filmStart;
            this.entity = ride.entity;

            VehicleIkState state = state(ride.entity);

            for (ValueVehicleParts.Part part : VehicleGeometry.parts(ride.form))
            {
                if (!motion && !part.bone.get().equals(ride.doorBone()))
                {
                    continue;
                }

                float open = state == null ? 0F : state.getOpenness(part.bone.get());

                this.axes.put(part.bone.get(), part.axis.get());
                this.angles.put(part.bone.get(), part.angle.get());
                this.initial.put(part.bone.get(), (float) Math.toRadians(part.angle.get() * open));
            }
        }

        public void sample(Session ride, VehicleIkState state)
        {
            this.entity = ride.entity;
            this.pose.add(new double[] {ride.x, ride.y, ride.z, ride.yaw, ride.pitch, ride.roll});
            this.sampleParts(state);
        }

        /** One more tick with the vehicle standing where it was left; true once the tail is over */
        public boolean tail(VehicleIkState state)
        {
            if (this.pose.isEmpty())
            {
                return true;
            }

            this.pose.add(this.pose.get(this.pose.size() - 1).clone());
            this.sampleParts(state);

            return --this.tail <= 0;
        }

        /** Every actor of the vehicle's replay where the drive left it, while the drive is not written yet */
        public void pinAll()
        {
            for (Candidate candidate : candidates())
            {
                if (candidate.controller.film.getId().equals(this.filmId) && candidate.replay.getId().equals(this.replayId))
                {
                    this.pin(candidate.entity);
                }
            }
        }

        /** The actor standing still where the drive left it: its last pose, this tick and the last */
        public void pin(IEntity entity)
        {
            double[] p = this.pose.get(this.pose.size() - 1);
            float yaw = (float) p[3];
            float pitch = (float) p[4];

            pinned.add(entity);

            entity.setPrevX(p[0]);
            entity.setPrevY(p[1]);
            entity.setPrevZ(p[2]);
            entity.setPosition(p[0], p[1], p[2]);
            entity.setVelocity(0F, 0F, 0F);
            entity.setPrevYaw(yaw);
            entity.setPrevHeadYaw(yaw);
            entity.setPrevBodyYaw(yaw);
            entity.setYaw(yaw);
            entity.setHeadYaw(yaw);
            entity.setBodyYaw(yaw);
            entity.setPrevPitch(pitch);
            entity.setPitch(pitch);
            entity.setRoll((int) Math.round(p[5] * 100D));
            entity.setOnGround(true);
        }

        private void sampleParts(VehicleIkState state)
        {
            for (Map.Entry<String, Float> entry : this.angles.entrySet())
            {
                float open = state == null ? 0F : state.getOpenness(entry.getKey());

                this.doors.computeIfAbsent(entry.getKey(), (k) -> new ArrayList<>()).add((float) Math.toRadians(entry.getValue() * open));
            }
        }
    }

    private static class Session
    {
        public final String filmId;
        public final String replayId;
        public IEntity entity;
        public ModelForm form;
        /** The door beside the seat; null when that side has none */
        public ValueVehicleParts.Part door;
        public ClientPlayerEntity player;

        public Phase phase = Phase.BOARDING;
        public int timer;
        public boolean driver;
        /** The front left seat, whether or not it may drive */
        public boolean driverSeat;
        /** Riding along the drive the vehicle's replay holds: the film moves it, not the ride */
        public boolean follow;
        /** In the driver's seat of a vehicle its replay drove here and stopped: a foot on the pedal takes the wheel */
        public boolean canTakeOver;
        /** Driving where the replay drives on later: this drive is written over that one */
        public boolean replaces;
        public boolean sneaking = true;

        /* In the vehicle's frame */
        public Vector3f seat = new Vector3f();
        public Vector3f exit = new Vector3f();
        public Vector3f middle = new Vector3f();
        /** +1 when the model's front is the actor's forward (+Z), -1 when it was built facing back */
        public float front = 1F;
        /** How far above the actor's feet the wheels touch the ground */
        public float contact;

        public double x, y, z;
        public double prevX, prevY, prevZ;
        public float yaw, prevYaw;
        /** Blocks per second along the heading (what the dashboard shows), the last tick's too */
        public float speed, prevSpeed;
        public float steer;
        /** A turn of the film's own (a cut) the view takes at once, not over a tick */
        public float cut;
        /** Degrees per second, eased toward what the steering asks */
        public float yawRate;
        /** The climb, nose up, in degrees: how much higher the front axle stands than the rear */
        public float pitch, prevPitch;

        /** The lean, left side up, in degrees: how much higher the left wheels stand than the right */
        public float roll;

        /* The footprint and the wheels (x, z), in the vehicle's frame; the wheels' heights in the world */
        public final Vector3f footMin = new Vector3f();
        public final Vector3f footMax = new Vector3f();
        public final float[][] wheels = new float[4][2];
        public final double[] wheelY = new double[4];

        /** The height the view is at, sprung behind the seat's own; set once seated */
        public double viewY;
        public float viewVelocity;
        public boolean viewSet;
        /** Where the view goes from the seated player's eyes (see {@link #getCameraOffset}), this tick and the last */
        public final Vector3f camera = new Vector3f();
        public final Vector3f prevCamera = new Vector3f();

        public float wheelbase()
        {
            return Math.abs((this.wheels[FL][1] + this.wheels[FR][1]) - (this.wheels[RL][1] + this.wheels[RR][1])) / 2F;
        }

        public float track()
        {
            return Math.abs((this.wheels[FL][0] + this.wheels[RL][0]) - (this.wheels[FR][0] + this.wheels[RR][0])) / 2F;
        }

        /** The climb the wheels make, nose up: how much higher the front ones stand than the rear */
        public float climb()
        {
            double[] w = this.wheelY;

            return MathUtils.clamp((float) Math.toDegrees(Math.atan(((w[FL] + w[FR]) - (w[RL] + w[RR])) / (2D * Math.max(this.wheelbase(), 0.5F)))), -MAX_CLIMB_PITCH, MAX_CLIMB_PITCH);
        }

        /** The lean the wheels make, left side up */
        public float lean()
        {
            double[] w = this.wheelY;

            return MathUtils.clamp((float) Math.toDegrees(Math.atan(((w[FL] + w[RL]) - (w[FR] + w[RR])) / (2D * Math.max(this.track(), 0.5F)))), -MAX_CLIMB_PITCH, MAX_CLIMB_PITCH);
        }

        /**
         * The drive from where the vehicle stands: its wheels on the ground the vehicle IK found
         * under them (the ground it is drawn settled on), so getting in moves nothing; without one,
         * on the actor's own place and tilt.
         */
        public void settle(IEntity entity, VehicleIkState state)
        {
            double[] ground = state == null ? null : state.getWheelGround();

            for (int i = 0; i < 4; i++)
            {
                this.wheelY[i] = ground != null ? ground[i] : entity.getY() + this.contact;
            }

            if (ground != null)
            {
                this.y = (ground[0] + ground[1] + ground[2] + ground[3]) / 4D - this.contact;
                this.pitch = this.climb();
                this.roll = this.lean();
            }
            else
            {
                this.y = entity.getY();
                this.pitch = entity.getPitch();
                this.roll = entity.getRoll() / 100F;
            }

            this.prevY = this.y;
            this.prevPitch = this.pitch;
        }

        /** The film controller and replay the vehicle is in now */
        public Candidate current()
        {
            for (Candidate candidate : candidates())
            {
                if (candidate.entity == this.entity)
                {
                    return candidate;
                }
            }

            return null;
        }

        /** Whether this seat shows the dashboard (the driver's, or a passenger's) */
        public boolean hasDashboard()
        {
            return this.driverSeat ? this.form.driverHud.get() : this.form.passengerHud.get();
        }

        /** The height of the plane the wheels stand on, under a point of the vehicle's frame */
        public double plane(float x, float z)
        {
            float leftX = (this.wheels[FL][0] + this.wheels[RL][0]) / 2F;
            float rightX = (this.wheels[FR][0] + this.wheels[RR][0]) / 2F;
            float frontZ = (this.wheels[FL][1] + this.wheels[FR][1]) / 2F;
            float rearZ = (this.wheels[RL][1] + this.wheels[RR][1]) / 2F;
            float u = Math.abs(leftX - rightX) < 1.0E-3F ? 0.5F : MathUtils.clamp((x - rightX) / (leftX - rightX), 0F, 1F);
            float v = Math.abs(frontZ - rearZ) < 1.0E-3F ? 0.5F : MathUtils.clamp((z - rearZ) / (frontZ - rearZ), 0F, 1F);
            double rear = this.wheelY[RR] + (this.wheelY[RL] - this.wheelY[RR]) * u;
            double fore = this.wheelY[FR] + (this.wheelY[FL] - this.wheelY[FR]) * u;

            return rear + (fore - rear) * v;
        }

        public Session(Candidate candidate)
        {
            this.filmId = candidate.controller.film.getId();
            this.replayId = candidate.replay.getId();
            this.entity = candidate.entity;
            this.form = candidate.form;
        }

        public String doorBone()
        {
            return this.door == null ? "" : this.door.bone.get();
        }

        /** Where the film has the vehicle now: the ride goes with it */
        public void follow(IEntity entity)
        {
            /* The view turns with it every frame (see frame()), at the rate the film turned it this
             * tick; a jump of the timeline is a cut, taken at once instead of spun through */
            float turn = MathHelper.wrapDegrees(entity.getBodyYaw() - this.yaw);
            double dx = entity.getX() - this.x;
            double dz = entity.getZ() - this.z;
            boolean cut = dx * dx + dz * dz > 16D || Math.abs(turn) > 45F;

            this.yawRate = cut ? 0F : turn / TICK;
            this.cut = cut ? turn : 0F;
            this.prevSpeed = this.speed;
            this.speed = cut ? 0F : (float) (Math.sqrt(dx * dx + dz * dz) / TICK);
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
            this.yaw = entity.getBodyYaw();
            this.pitch = entity.getPitch();
            this.roll = entity.getRoll() / 100F;
        }

        public boolean isOf(Film film)
        {
            return film != null && this.filmId.equals(film.getId());
        }

        /** The heading the player faces: the vehicle's front */
        public float heading()
        {
            return this.front < 0F ? this.yaw + 180F : this.yaw;
        }

        /** A world point into the vehicle's frame */
        public Vector3f local(Vector3f world, double ox, double oy, double oz, float yaw)
        {
            return new Vector3f((float) (world.x - ox), (float) (world.y - oy), (float) (world.z - oz)).rotateY(MathUtils.toRad(yaw));
        }

        /** A point of the vehicle's frame into the world, relative to the vehicle */
        public Vector3f world(Vector3f local)
        {
            return new Vector3f(local).rotateY(-MathUtils.toRad(this.yaw));
        }

        /**
         * Still in the world? The controller it was boarded in may be gone (the film stopped, a take
         * started or ended): the same replay in any controller still showing the film carries on.
         */
        public boolean rebind()
        {
            for (Candidate candidate : candidates())
            {
                if (candidate.entity == this.entity)
                {
                    return true;
                }
            }

            for (Candidate candidate : candidates())
            {
                if (this.isOf(candidate.controller.film) && candidate.replay.getId().equals(this.replayId))
                {
                    this.entity = candidate.entity;
                    this.form = candidate.form;

                    return true;
                }
            }

            return false;
        }

        public void pinAll()
        {
            for (Candidate candidate : candidates())
            {
                if (this.isOf(candidate.controller.film) && candidate.replay.getId().equals(this.replayId))
                {
                    this.pin(candidate.entity);
                }
            }
        }

        /** The actor where the drive has it, this tick and the last, so the frames between glide */
        public void pin(IEntity entity)
        {
            pinned.add(entity);

            entity.setPrevX(this.prevX);
            entity.setPrevY(this.prevY);
            entity.setPrevZ(this.prevZ);
            entity.setPosition(this.x, this.y, this.z);
            entity.setVelocity((float) (this.x - this.prevX), (float) (this.y - this.prevY), (float) (this.z - this.prevZ));
            entity.setPrevYaw(this.prevYaw);
            entity.setPrevHeadYaw(this.prevYaw);
            entity.setPrevBodyYaw(this.prevYaw);
            entity.setYaw(this.yaw);
            entity.setHeadYaw(this.yaw);
            entity.setBodyYaw(this.yaw);
            entity.setPrevPitch(this.prevPitch);
            entity.setPitch(this.pitch);
            /* The lean in hundredths of a degree: the roll channel holds whole numbers */
            entity.setRoll(Math.round(this.roll * 100F));
            entity.setOnGround(true);
        }
    }
}
