package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Films;

/**
 * Static session controller for Live Multi-Track Replay Recording.
 * Coordinates starting, stopping, track switching, and HUD exposure.
 */
public class MultiTrackReplaySession
{
    private static MultiTrackRecorder activeRecorder;

    public static boolean isActive()
    {
        return activeRecorder != null && BBSModClient.getFilms().getRecorder() == activeRecorder;
    }

    public static MultiTrackRecorder getRecorder()
    {
        return activeRecorder;
    }

    public static MultiTrackRecorder start(Film film, int startTick)
    {
        Films films = BBSModClient.getFilms();
        MultiTrackRecorder recorder = films.startMultiTrackRecording(film, startTick);
        activeRecorder = recorder;
        return recorder;
    }

    public static void stop()
    {
        if (isActive())
        {
            BBSModClient.getFilms().stopRecording();
            activeRecorder = null;
        }
    }

    public static void reset()
    {
        activeRecorder = null;
    }

    public static void nextTrack()
    {
        if (isActive())
        {
            activeRecorder.nextTrack();
        }
    }

    public static void previousTrack()
    {
        if (isActive())
        {
            activeRecorder.previousTrack();
        }
    }

    public static void switchTrack(int index)
    {
        if (isActive())
        {
            activeRecorder.switchToTrack(index);
        }
    }
}
