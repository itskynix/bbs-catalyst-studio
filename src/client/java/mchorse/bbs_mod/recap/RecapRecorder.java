package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The recording side of a recap: its state, its clock and its markers, driven by the transport bar
 * of the pause menu and by the key bindings.
 *
 * <p>The clock counts game ticks only: a paused game does not advance it.</p>
 *
 * <p><b>Instant recap.</b> While the setting is on, the last stretch of play can be saved after
 * the fact: ticks since the world was joined and the markers pressed in them are kept in memory,
 * and saving takes the last so many seconds of them.</p>
 */
public final class RecapRecorder
{
    public enum State
    {
        IDLE, RECORDING, STOPPED
    }

    private static State state = State.IDLE;
    private static int ticks;
    private static final List<Integer> markers = new ArrayList<>();
    private static RecapWorlds.Info world;

    private static int playedTicks;
    private static final List<Integer> playedMarkers = new ArrayList<>();

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

    public static boolean isInstantEnabled()
    {
        return BBSSettings.recapInstant != null && BBSSettings.recapInstant.get();
    }

    private static int instantWindow()
    {
        return BBSSettings.recapInstantSeconds == null ? 0 : BBSSettings.recapInstantSeconds.get() * 20;
    }

    /** Once a client tick. */
    public static void tick(MinecraftClient mc)
    {
        if (mc.world == null || mc.isPaused())
        {
            return;
        }

        playedTicks += 1;

        if (state == State.RECORDING)
        {
            ticks += 1;
        }

        int oldest = playedTicks - instantWindow();

        playedMarkers.removeIf((marker) -> marker < oldest);
    }

    public static boolean start()
    {
        RecapWorlds.Info info = RecapWorlds.current();

        if (state != State.IDLE || info == null)
        {
            return false;
        }

        world = info;
        ticks = 0;
        markers.clear();
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

    /** Writes the recording to the list and goes back to idle; null when there is nothing to write. */
    public static RecapRecord save()
    {
        if (state == State.IDLE || world == null)
        {
            return null;
        }

        RecapRecord record = create(world, ticks, markers, false);

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

        if (state == State.IDLE && isInstantEnabled() && MinecraftClient.getInstance().world != null)
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

        int length = Math.min(playedTicks, instantWindow());

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

        return create(info, length, inside, true);
    }

    /** The record key: starts a recording, and saves the one running or stopped. */
    public static void toggleWithMessage()
    {
        if (state == State.IDLE)
        {
            say(start() ? "bbs.recap.message.started" : "bbs.recap.message.unavailable");
        }
        else
        {
            say(save() != null ? "bbs.recap.message.saved" : "bbs.recap.message.failed");
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
        world = null;
    }

    private static RecapRecord create(RecapWorlds.Info info, int length, List<Integer> marked, boolean instant)
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

        return RecapStore.save(record) ? record : null;
    }
}
