package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The recording side of a recap: its state, its clock, its markers and its camera, driven by the
 * transport bar of the pause menu and by the key bindings.
 *
 * <p><b>States.</b> Idle, recording and stopped. Only a recording counts: Stop holds the clock
 * where it is, Start then goes on from there, and Save or Cancel end the session and put the clock
 * back to zero. Nothing runs behind a stopped or ended recording.</p>
 *
 * <p><b>The clock.</b> A running recording counts client ticks, twenty a second, also while the
 * pause menu is open, so the time on the transport bar goes on from the moment Start is pressed
 * there. What is drawn between two ticks is {@code ticks + transition}, the transition being the
 * frame's share of the next tick; nothing reads the wall clock. Every counted tick also keeps where
 * the camera was ({@link RecapTrack}), which is what Recap Studio shows the recap through.</p>
 *
 * <p><b>Instant recap.</b> The last stretch of play can be saved after the fact: the ticks played
 * since the world was joined, their camera and the markers pressed in them are counted all the
 * time, so switching the setting on mid session already has the stretch behind it; a paused game
 * is not play and does not add to it, and neither does Recap Studio looking through a recap's
 * camera. Saving takes the last so many seconds, the setting's length, or all that was played when
 * that is shorter.</p>
 */
public final class RecapRecorder
{
    public enum State
    {
        IDLE, RECORDING, STOPPED
    }

    /** The longest instant recap the setting allows, in ticks: what the play buffer keeps. */
    private static final int BUFFER_LIMIT = 600 * 20;

    private static State state = State.IDLE;
    private static int ticks;
    private static final List<Integer> markers = new ArrayList<>();
    private static final RecapTrack track = new RecapTrack();
    private static final RecapMotion motion = new RecapMotion();
    private static final RecapEvents events = new RecapEvents();
    private static RecapWorlds.Info world;

    private static int playedTicks;
    private static final List<Integer> playedMarkers = new ArrayList<>();
    private static final RecapTrack playedTrack = new RecapTrack();
    private static final RecapMotion playedMotion = new RecapMotion();
    private static final RecapEvents playedEvents = new RecapEvents();

    private RecapRecorder()
    {}

    public static State getState()
    {
        return state;
    }

    public static int getTicks()
    {
        return ticks;
    }

    public static int getMarkerCount()
    {
        return markers.size();
    }

    /** The recording's time between two ticks, for drawing: the ticks plus the frame's transition. */
    public static float getTime(float transition)
    {
        return state == State.RECORDING ? ticks + Math.max(0F, Math.min(1F, transition)) : ticks;
    }

    public static boolean isInstantEnabled()
    {
        return BBSSettings.recapInstant != null && BBSSettings.recapInstant.get();
    }

    private static int instantWindow()
    {
        return BBSSettings.recapInstantSeconds == null ? 0 : Math.min(BUFFER_LIMIT, BBSSettings.recapInstantSeconds.get() * 20);
    }

    /** How much play an instant recap would take right now. */
    public static int getInstantTicks()
    {
        return Math.min(instantWindow(), Math.min(playedTicks, playedTrack.size()));
    }

    /** Once a client tick. */
    public static void tick(MinecraftClient mc)
    {
        if (mc.world == null)
        {
            return;
        }

        /* The recording counts on with the pause menu open; only play feeds the instant stretch */
        if (state == State.RECORDING)
        {
            ticks += 1;
            sample(mc, track);
            sampleMotion(mc, motion);
        }

        if (mc.isPaused() || RecapScene.isActive())
        {
            return;
        }

        playedTicks += 1;
        sample(mc, playedTrack);
        playedTrack.trim(BUFFER_LIMIT);
        sampleMotion(mc, playedMotion);
        playedMotion.trim(BUFFER_LIMIT);
        playedEvents.dropBefore(playedTicks - BUFFER_LIMIT);

        int oldest = playedTicks - BUFFER_LIMIT;

        playedMarkers.removeIf((marker) -> marker < oldest);
    }

    /** How the player moves this tick: a frame for every tick the camera is kept on, so both line up. */
    private static void sampleMotion(MinecraftClient mc, RecapMotion into)
    {
        if (mc.player != null)
        {
            into.add(mc.player);
        }
        else if (!into.isEmpty())
        {
            /* No player for a moment: the last frame again, so the frames keep step with the ticks */
            RecapMotion.Frame frame = new RecapMotion.Frame();

            into.sample(into.size() - 1, frame);
            into.add(frame.x, frame.y, frame.z, frame.bodyYaw, frame.headYaw, frame.pitch, frame.limbPos, frame.limbSpeed, frame.swing, frame.lean, frame.pose.ordinal(), frame.flags);
        }
    }

    /**
     * A block of the world changed: broken, placed or used. It goes into the running recording at
     * its tick, and into the instant stretch while play is going on; the recap's own blocks put
     * in by Recap Studio are not play and are left out.
     */
    public static void onBlockChanged(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState from, net.minecraft.block.BlockState to)
    {
        if (RecapEvents.applying || from == to)
        {
            return;
        }

        if (state == State.RECORDING)
        {
            events.add(ticks, pos, from, to);
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null && !RecapScene.isActive())
        {
            playedEvents.add(playedTicks, pos, from, to);
        }
    }

    private static void sample(MinecraftClient mc, RecapTrack into)
    {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();

        into.add(pos.x, pos.y, pos.z, camera.getYaw(), camera.getPitch(), mc.options.getFov().getValue());
    }

    /** Starts a recording, or goes on with the stopped one from where it was held. */
    public static boolean start()
    {
        if (state == State.STOPPED)
        {
            state = State.RECORDING;

            return true;
        }

        RecapWorlds.Info info = RecapWorlds.current();

        if (state != State.IDLE || info == null)
        {
            return false;
        }

        world = info;
        ticks = 0;
        markers.clear();
        track.clear();
        motion.clear();
        events.clear();
        state = State.RECORDING;

        return true;
    }

    public static boolean stop()
    {
        if (state != State.RECORDING)
        {
            return false;
        }

        state = State.STOPPED;

        return true;
    }

    /** Writes the recording to the list and ends the session; null when there is nothing to write. */
    public static RecapRecord save()
    {
        if (state == State.IDLE || world == null)
        {
            return null;
        }

        /* The session ends here whatever comes of the writing: no clock runs behind it */
        state = State.STOPPED;

        RecapRecord record = create(world, ticks, markers, track, motion, events, false);

        if (record != null)
        {
            reset();
        }

        return record;
    }

    public static boolean cancel()
    {
        if (state == State.IDLE)
        {
            return false;
        }

        reset();

        return true;
    }

    /** Marks this moment: in the running recording, or in the instant buffer while idle. */
    public static boolean marker()
    {
        if (state == State.RECORDING)
        {
            if (!markers.contains(ticks))
            {
                markers.add(ticks);
            }

            return true;
        }

        if (state == State.IDLE && MinecraftClient.getInstance().world != null)
        {
            if (!playedMarkers.contains(playedTicks))
            {
                playedMarkers.add(playedTicks);
            }

            return true;
        }

        return false;
    }

    /** Saves the last stretch of play as a recap of its own. */
    public static RecapRecord saveInstant()
    {
        RecapWorlds.Info info = RecapWorlds.current();

        if (!isInstantEnabled() || info == null)
        {
            return null;
        }

        int length = getInstantTicks();

        if (length <= 0)
        {
            return null;
        }

        int from = playedTicks - length;
        List<Integer> inside = new ArrayList<>();

        for (int marker : playedMarkers)
        {
            if (marker >= from)
            {
                inside.add(marker - from);
            }
        }

        return create(info, length, inside, RecapTrack.tail(playedTrack, length), RecapMotion.tail(playedMotion, length), RecapEvents.tail(playedEvents, from), true);
    }

    /** The record key: starts a recording, and saves the one running or stopped. */
    public static void toggleWithMessage()
    {
        if (state == State.IDLE)
        {
            startWithMessage();
        }
        else
        {
            saveWithMessage();
        }
    }


    /** The instant key and button: saves the last stretch, and says what came of it. */
    public static RecapRecord saveInstantWithMessage()
    {
        if (!isInstantEnabled())
        {
            say("bbs.recap.message.instant_off");

            return null;
        }

        RecapRecord record = saveInstant();

        say(record != null ? "bbs.recap.message.instant_saved" : "bbs.recap.message.instant_empty");

        if (record != null)
        {
            toast("bbs.recap.message.instant_saved", record);
        }

        return record;
    }

    /** The start of the transport bar, with what came of it said. */
    public static void startWithMessage()
    {
        boolean resume = state == State.STOPPED;

        if (start())
        {
            say(resume ? "bbs.recap.message.resumed" : "bbs.recap.message.started");
        }
        else
        {
            say("bbs.recap.message.unavailable");
        }
    }

    public static void stopWithMessage()
    {
        if (stop())
        {
            say("bbs.recap.message.stopped");
        }
    }

    public static void saveWithMessage()
    {
        RecapRecord record = save();

        say(record != null ? "bbs.recap.message.saved" : "bbs.recap.message.failed");

        if (record != null)
        {
            toast("bbs.recap.message.saved", record);
        }
    }

    /** A pop up in the corner when a recap was written, with its name and length, seen also over the pause menu. */
    private static void toast(String key, RecapRecord record)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Text title = Text.literal(L10n.lang(key).get());
        Text description = Text.literal(record.name + "  " + RecapTime.format(record.ticks));

        net.minecraft.client.toast.SystemToast.show(mc.getToastManager(), net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION, title, description);
    }

    public static void cancelWithMessage()
    {
        if (cancel())
        {
            say("bbs.recap.message.cancelled");
        }
    }

    public static void markerWithMessage()
    {
        say(marker() ? "bbs.recap.message.marker" : "bbs.recap.message.no_marker");
    }

    private static void say(String key)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null)
        {
            mc.player.sendMessage(Text.literal(L10n.lang(key).get()), true);
        }
    }

    public static void onJoin()
    {
        reset();
        playedTicks = 0;
        playedMarkers.clear();
        playedTrack.clear();
        playedMotion.clear();
        playedEvents.clear();
    }

    /** Leaving the world keeps what was being recorded rather than losing it. */
    public static void onLeave()
    {
        if (state != State.IDLE && ticks > 0)
        {
            save();
        }

        onJoin();
    }

    private static void reset()
    {
        state = State.IDLE;
        ticks = 0;
        markers.clear();
        track.clear();
        motion.clear();
        events.clear();
        world = null;
    }

    private static RecapRecord create(RecapWorlds.Info info, int length, List<Integer> marked, RecapTrack camera, RecapMotion moves, RecapEvents changes, boolean instant)
    {
        RecapRecord record = new RecapRecord();
        LocalDateTime now = LocalDateTime.now();

        record.name = info.name() + " " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"));
        record.worldKey = info.key();
        record.worldName = info.name();
        record.worldTarget = info.target();
        record.singleplayer = info.singleplayer();
        record.ticks = length;
        record.created = System.currentTimeMillis();
        record.instant = instant;

        for (int marker : marked)
        {
            record.addMarker(marker);
        }

        if (!RecapStore.save(record))
        {
            return null;
        }

        RecapStore.saveTrack(record, camera);
        RecapStore.saveMotion(record, moves);
        RecapStore.saveEvents(record, changes);

        return record;
    }
}
