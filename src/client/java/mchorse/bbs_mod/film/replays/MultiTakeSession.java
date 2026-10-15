package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/**
 * Multi-take hot-swap: one key press finishes the take that is running, moves to the next (or
 * previous) actor of the scene, rewinds the timeline to the start and begins that actor's take
 * from the top. Everything already recorded plays back as the film does for any take, so each take
 * is shot against the previous ones. Without a take running, the key only moves the selection.
 *
 * <p>The live multi-track session ({@link MultiTrackReplaySession}) keeps its own behaviour; this
 * handles the plain, single-actor takes.</p>
 */
public final class MultiTakeSession
{
    private static final long MESSAGE_MILLIS = 3000L;

    private static String message = "";
    private static long messageUntil;

    private MultiTakeSession()
    {
    }

    /**
     * @param direction +1 for the next actor, -1 for the previous
     * @return whether the key was handled
     */
    public static boolean step(int direction)
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        if (dashboard == null)
        {
            return false;
        }

        UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);
        Film film = panel == null ? null : panel.getData();

        if (film == null)
        {
            return false;
        }

        Films films = BBSModClient.getFilms();
        Recorder recorder = films.getRecorder();
        List<Replay> replays = film.replays.getList();
        int current = currentIndex(panel, film, recorder);
        int target = current + direction;

        if (replays.size() < 2)
        {
            notify("No other actor in this scene");

            return recorder != null;
        }

        if (target < 0 || target >= replays.size())
        {
            notify("Last take reached: no more actors " + (direction > 0 ? "ahead" : "behind"));

            return recorder != null;
        }

        return switchTo(target, Screen.hasShiftDown());
    }

    public static int currentIndex(UIFilmPanel panel, Film film, Recorder recorder)
    {
        return recorder != null ? recorder.exception : film.replays.getList().indexOf(panel.replayEditor.getReplay());
    }

    /**
     * Hands the take over to {@code target}: the running take is saved and stopped, the actor is
     * selected, and the next take starts. Punch-in by default — the timeline stays where it is and
     * the new take carries on from the same tick; {@code rewind} sends it back to the start instead.
     * Without a running take only the selection changes.
     */
    public static boolean switchTo(int target, boolean rewind)
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
        Film film = panel == null ? null : panel.getData();
        List<Replay> replays = film == null ? null : film.replays.getList();

        if (replays == null || target < 0 || target >= replays.size())
        {
            return false;
        }

        Films films = BBSModClient.getFilms();
        Recorder recorder = films.getRecorder();
        int tick = 0;

        /* 1. Finish the running take: save it and stop for good. */
        if (recorder != null)
        {
            tick = recorder.hasNotStarted() ? recorder.initialTick : recorder.getTick();

            Recorder done = films.stopRecording();

            panel.stopPlayback();
            Films.stopFilm(film.getId());

            if (done != null && !done.hasNotStarted())
            {
                panel.applyRecordedKeyframes(done, film);
            }
        }

        /* 2. The next actor. */
        panel.replayEditor.setReplay(replays.get(target));

        if (recorder == null)
        {
            notify("Actor " + (target + 1) + "/" + replays.size() + ": " + name(replays.get(target), target));

            return true;
        }

        /* 3. Rewind (from the beginning) or keep the tick (punch-in), and 4. begin the take there
         * (on the actor's mark, if allowed). */
        int start = rewind ? 0 : tick;

        panel.setCursor(start);
        films.startRecording(film, target, start, true);

        notify("[Take " + (target + 1) + "/" + replays.size() + ": " + name(replays.get(target), target) + "] " + (rewind ? "From the beginning" : "Punch-in at tick " + start) + "...");

        return true;
    }

    public static String name(Replay replay, int index)
    {
        String label = replay.label.get();

        if (label != null && !label.trim().isEmpty())
        {
            return label;
        }

        return replay.form.get() != null ? replay.form.get().getId() : "Actor " + (index + 1);
    }

    private static void notify(String text)
    {
        message = text;
        messageUntil = System.currentTimeMillis() + MESSAGE_MILLIS;
    }

    public static void renderHud(Batcher2D batcher2D)
    {
        if (System.currentTimeMillis() > messageUntil || message.isEmpty())
        {
            return;
        }

        int width = batcher2D.getFont().getWidth(message);
        int x = MinecraftClient.getInstance().getWindow().getScaledWidth() / 2 - width / 2;
        int y = 8;

        batcher2D.box(x - 5, y - 3, x + width + 5, y + 13, Colors.A50);
        batcher2D.textShadow(message, x, y + 1);
    }
}
