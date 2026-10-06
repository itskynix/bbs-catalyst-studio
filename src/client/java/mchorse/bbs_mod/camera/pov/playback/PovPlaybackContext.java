/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.film.BaseFilmController
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.FirstPersonFilmController
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.utils.VideoRecorder
 */
package mchorse.bbs_mod.camera.pov.playback;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.FilmsPovAccess;
import java.util.List;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FirstPersonFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.VideoRecorder;

public final class PovPlaybackContext {
    private static FirstPersonFilmController exportController;
    private static int exportBaseFrame;
    private static float exportBaseCursor;
    private static boolean exportClockActive;

    private PovPlaybackContext() {
    }

    public static Frame getActive() {
        return PovPlaybackContext.getActive(0.0f);
    }

    public static Frame getActive(float tickDelta) {
        List<BaseFilmController> controllers = ((FilmsPovAccess)BBSModClient.getFilms()).bbsPov$getControllers();
        for (int i = controllers.size() - 1; i >= 0; --i) {
            BaseFilmController candidate = controllers.get(i);
            if (!(candidate instanceof FirstPersonFilmController)) continue;
            FirstPersonFilmController firstPerson = (FirstPersonFilmController)candidate;
            Film film = firstPerson.film;
            Replay replay = film.getFirstPersonReplay();
            if (replay == null || firstPerson.hasFinished()) continue;
            int filmTick = firstPerson.getTick();
            float transition = firstPerson.paused ? 0.0f : Math.max(0.0f, Math.min(1.0f, tickDelta));
            float filmCursor = PovPlaybackContext.getFilmCursor(firstPerson, (float)filmTick + transition);
            int wholeCursor = (int)Math.floor(filmCursor);
            float cursorTransition = filmCursor - (float)wholeCursor;
            PovCameraClip clip = PovCameraClips.resolve(film, filmCursor);
            if (clip == null) {
                clip = new PovCameraClip();
                clip.selector.set(PovCameraClips.indexOfReplay(film, replay));
            } else {
                Replay clipReplay = PovCameraClips.resolveReplay(film, clip);
                if (clipReplay != null) {
                    replay = clipReplay;
                }
            }
            if (clip == null || replay == null) continue;
            return new Frame(firstPerson, film, replay, clip, wholeCursor, (float)replay.getTick(wholeCursor) + cursorTransition);
        }
        exportClockActive = false;
        exportController = null;
        return null;
    }

    private static float getFilmCursor(FirstPersonFilmController controller, float normalCursor) {
        VideoRecorder recorder = BBSModClient.getVideoRecorder();
        if (!recorder.isRecording()) {
            exportClockActive = false;
            exportController = null;
            return normalCursor;
        }
        int frame = recorder.getCounter();
        if (!exportClockActive || exportController != controller || frame < exportBaseFrame) {
            exportClockActive = true;
            exportController = controller;
            exportBaseFrame = frame;
            exportBaseCursor = normalCursor;
        }
        int frameRate = Math.max(1, BBSRendering.getVideoFrameRate());
        return exportBaseCursor + (float)(frame - exportBaseFrame) * (20.0f / (float)frameRate);
    }

    public record Frame(FirstPersonFilmController controller, Film film, Replay replay, PovCameraClip clip, int filmTick, float replayTick) {
    }
}

