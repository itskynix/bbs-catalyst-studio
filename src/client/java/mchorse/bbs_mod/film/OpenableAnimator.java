package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Interactive openables: a door, trapdoor, fence gate or chest opened a share of the way at a time, by
 * hand, and kept in the film as keyframes.
 *
 * <p>With the setting on the form in use - the one the player wears, or the one of the replay being
 * recorded - holding use on an openable turns it into a film actor on the spot: a new replay standing
 * exactly where the block stood, wearing a block form of it (a door with both its halves, a double
 * chest with both its sides), and the block itself is taken out of the world, so there is never a copy
 * of it on top of the actor. From then on the mouse moves the actor: forward opens, back closes, in
 * proportion. Every tick of the hold is written into the form's openness track, a keyframe a tick, and
 * letting go finishes the swing past the threshold or shuts it short of it, also keyed. Played back, it
 * is plain animation: the keyframes swing the block, nothing else is needed.</p>
 *
 * <p>There has to be a film to put the actor in: the one being recorded, or the one open in the film
 * editor. Without one the click is an ordinary one.</p>
 */
public final class OpenableAnimator
{
    /** Pixels of mouse movement that open or close it all the way */
    private static final float PIXELS_FULL = 260F;

    /** Ticks a finished swing takes to settle */
    private static final int SETTLE_TICKS = 6;

    private static Drag drag;

    /** Where the film the actor goes into is, and how to reach it */
    private record Target(mchorse.bbs_mod.film.Film film, IntSupplier tick, Runnable rebuild, java.util.function.Function<String, IEntity> entity) {}

    private static class Drag
    {
        Target target;
        Replay replay;
        float start;
        float openness;
        float threshold;
        /** Letting go keeps it where it is, instead of finishing or shutting it */
        boolean hold;
        /** -1 while the hand moves it, then the ticks into the settle */
        int settle = -1;
        float from;
        float goal;
    }

    private OpenableAnimator()
    {}

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register((mc) -> tick());
    }

    /** The form with the setting on: the one the player wears, or the one of the replay being recorded; null when neither has it */
    private static Form settingsForm(MinecraftClient mc)
    {
        mchorse.bbs_mod.morphing.Morph morph = mc.player == null ? null : mchorse.bbs_mod.morphing.Morph.getMorph(mc.player);
        Form worn = morph == null ? null : morph.getForm();

        if (worn != null && worn.interactiveOpenables.get())
        {
            return worn;
        }

        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null && recorder.film != null)
        {
            List<Replay> replays = recorder.film.replays.getList();
            Replay replay = recorder.exception >= 0 && recorder.exception < replays.size() ? replays.get(recorder.exception) : null;
            Form form = replay == null ? null : replay.form.get();

            if (form != null && form.interactiveOpenables.get())
            {
                return form;
            }
        }

        return null;
    }

    /** The film an actor can go into: the one being recorded, else the one open in the film editor */
    private static Target target()
    {
        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null && recorder.film != null)
        {
            return new Target(recorder.film, () -> Math.max(0, recorder.tick), recorder::createEntities, (id) -> recorder.getEntities().get(id));
        }

        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel != null && panel.getData() != null)
        {
            return new Target(panel.getData(), () -> Math.max(0, panel.getCursor()), () -> panel.getController().createEntities(), (id) -> panel.getController().getEntities().get(id));
        }

        return null;
    }

    /** Called before the game handles a use click; true when it was taken */
    public static boolean onUse()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (drag != null)
        {
            return true;
        }

        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null)
        {
            return false;
        }

        Form settings = settingsForm(mc);

        if (settings == null)
        {
            return false;
        }

        /* An openable already made into an actor: taken up again where it is, no new actor */
        Target existing = target();

        if (existing != null)
        {
            Drag again = regrab(mc, existing, settings);

            if (again != null)
            {
                drag = again;

                return true;
            }
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
        {
            return false;
        }

        if (mc.player.shouldCancelInteraction() && (!mc.player.getMainHandStack().isEmpty() || !mc.player.getOffHandStack().isEmpty()))
        {
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);

        if (OpenableModel.kindOf(state) == null)
        {
            return false;
        }

        Target target = target();

        if (target == null)
        {
            mc.player.sendMessage(Text.literal(UIKeys.OPENABLE_NEEDS_FILM.get()), true);

            return false;
        }

        drag = takeOver(mc, target, pos, state, settings);

        return drag != null;
    }

    /**
     * Turns the block into an actor of the film: a replay standing where it stands, with a block form of
     * it whose openness is keyed from the film's start, and the block taken out of the world.
     */
    private static Drag takeOver(MinecraftClient mc, Target target, BlockPos pos, BlockState state, Form settings)
    {
        /* A door is one actor from its lower half, with the upper one drawn above it */
        BlockPos base = pos;
        BlockState baseState = state;

        if (state.getBlock() instanceof DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
        {
            base = pos.down();
            baseState = mc.world.getBlockState(base);

            if (!(baseState.getBlock() instanceof DoorBlock))
            {
                return null;
            }
        }

        boolean open = baseState.contains(Properties.OPEN) && baseState.get(Properties.OPEN);
        BlockPos partner = OpenableModel.partnerOffset(baseState);
        List<BlockPos> taken = new ArrayList<>();

        taken.add(base);

        if (partner != null && OpenableModel.kindOf(mc.world.getBlockState(base.add(partner))) != null)
        {
            taken.add(base.add(partner));
        }

        BlockForm form = new BlockForm();

        form.blockState.set(baseState.contains(Properties.OPEN) ? baseState.with(Properties.OPEN, false) : baseState);
        form.openAngle.set(settings.openableAngle.get());
        form.openablePair.set(taken.size() > 1);
        form.openness.set(open ? 1F : 0F);

        Replay replay = target.film().replays.addReplay();

        replay.label.set(form.getDisplayName());
        replay.form.set(form);
        replay.keyframes.x.insert(0, base.getX() + 0.5D);
        replay.keyframes.y.insert(0, (double) base.getY());
        replay.keyframes.z.insert(0, base.getZ() + 0.5D);

        Drag next = new Drag();

        next.target = target;
        next.replay = replay;
        next.start = open ? 1F : 0F;
        next.openness = next.start;
        next.threshold = settings.openableThreshold.get();
        next.hold = settings.holdOpenAngle.get();

        /* Shut or open as it was from the film's start to this tick */
        key(next, 0, next.start);
        key(next, target.tick().getAsInt(), next.start);

        /* The block goes, on the client at once and on the server for good */
        for (BlockPos at : taken)
        {
            mc.world.setBlockState(at, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }

        ClientNetwork.sendOpenableTakeover(taken);
        target.rebuild().run();

        return next;
    }

    /**
     * The actor of an openable the player is looking at, if any is nearer than the block under the
     * crosshair: its box (a door two blocks high, a double chest two wide) against the line of sight.
     */
    private static Drag regrab(MinecraftClient mc, Target target, Form settings)
    {
        net.minecraft.util.math.Vec3d eye = mc.player.getCameraPosVec(1F);
        double reach = mc.interactionManager.getReachDistance();
        net.minecraft.util.math.Vec3d end = eye.add(mc.player.getRotationVec(1F).multiply(reach));
        double blockDistance = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK ? mc.crosshairTarget.getPos().squaredDistanceTo(eye) : Double.MAX_VALUE;
        Replay best = null;
        BlockForm bestForm = null;
        double bestDistance = blockDistance + 1.0E-3D;

        for (Replay replay : target.film().replays.getList())
        {
            if (!(replay.form.get() instanceof BlockForm form) || OpenableModel.kindOf(form.blockState.get()) == null || !replay.enabled.get())
            {
                continue;
            }

            IEntity entity = target.entity().apply(replay.getId());
            double x = entity != null ? entity.getX() : replay.keyframes.x.interpolateDouble(0);
            double y = entity != null ? entity.getY() : replay.keyframes.y.interpolateDouble(0);
            double z = entity != null ? entity.getZ() : replay.keyframes.z.interpolateDouble(0);
            net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(x - 0.5D, y, z - 0.5D, x + 0.5D, y + 1D, z + 0.5D);
            BlockPos partner = form.openablePair.get() ? OpenableModel.partnerOffset(form.blockState.get()) : null;

            if (partner != null)
            {
                box = box.union(box.offset(partner.getX(), partner.getY(), partner.getZ()));
            }

            java.util.Optional<net.minecraft.util.math.Vec3d> hit = box.raycast(eye, end);

            if (hit.isPresent() && hit.get().squaredDistanceTo(eye) < bestDistance)
            {
                best = replay;
                bestForm = entity != null && entity.getForm() instanceof BlockForm shown ? shown : form;
                bestDistance = hit.get().squaredDistanceTo(eye);
            }
        }

        if (best == null)
        {
            return null;
        }

        Drag next = new Drag();

        next.target = target;
        next.replay = best;
        next.start = bestForm.openness.get();
        next.openness = next.start;
        next.threshold = settings.openableThreshold.get();
        next.hold = settings.holdOpenAngle.get();

        /* From where it stands now, at this tick */
        key(next, target.tick().getAsInt(), next.start);

        return next;
    }

    /** Keys the actor's openness at a tick, a straight line to the next key */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void key(Drag drag, int tick, float openness)
    {
        Form form = drag.replay.form.get();

        if (form == null)
        {
            return;
        }

        KeyframeChannel channel = drag.replay.properties.getOrCreate(form, "openness");

        if (channel == null)
        {
            return;
        }

        int index = channel.insert(tick, openness);
        Keyframe keyframe = channel.get(index);

        if (keyframe != null)
        {
            keyframe.getInterpolation().setInterp(mchorse.bbs_mod.utils.interps.Interpolations.LINEAR);
        }

        /* And the actor on screen at once, not a tick later */
        IEntity entity = drag.target.entity().apply(drag.replay.getId());

        if (entity != null && entity.getForm() instanceof BlockForm shown)
        {
            shown.openness.set(openness);
        }
    }

    /** The mouse, while a hold is on: forward opens, back closes. True when it was taken */
    public static boolean consumeLook(double dx, double dy)
    {
        Drag current = drag;

        if (current == null)
        {
            return false;
        }

        if (current.settle < 0)
        {
            current.openness = Math.max(0F, Math.min(1F, current.openness - (float) dy / PIXELS_FULL));

            IEntity entity = current.target.entity().apply(current.replay.getId());

            if (entity != null && entity.getForm() instanceof BlockForm shown)
            {
                shown.openness.set(current.openness);
            }
        }

        return true;
    }

    private static void tick()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Drag current = drag;

        if (current == null)
        {
            return;
        }

        if (mc.world == null || mc.player == null || mc.currentScreen != null && current.settle < 0)
        {
            drag = null;

            return;
        }

        int tick = current.target.tick().getAsInt();

        if (current.settle < 0)
        {
            key(current, tick, current.openness);

            if (mc.options.useKey.isPressed())
            {
                return;
            }

            /* Let go: past the threshold it opens, short of it it closes - or it stays where it was let go */
            current.settle = 0;
            current.from = current.openness;
            current.goal = current.hold ? current.openness : (current.openness >= current.threshold ? 1F : 0F);

            if (current.hold)
            {
                drag = null;

                return;
            }
        }

        current.settle += 1;

        float t = Math.min(1F, current.settle / (float) SETTLE_TICKS);
        float eased = t * t * (3F - 2F * t);

        current.openness = current.from + (current.goal - current.from) * eased;
        key(current, tick, current.openness);

        if (current.settle >= SETTLE_TICKS)
        {
            drag = null;
        }
    }

    /** Whether a hold is being made right now */
    public static boolean isDragging()
    {
        return drag != null;
    }
}
