/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.film.BaseFilmController
 *  mchorse.bbs_mod.film.Films
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.option.KeyBinding
 */
package mchorse.bbs_mod.camera.pov.playback;

import mchorse.bbs_mod.camera.pov.integration.access.bbs.BBSModClientAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.FilmsPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import java.util.List;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class PovPlaybackInput {
    private PovPlaybackInput() {
    }

    public static boolean isWorldPlaybackRunning() {
        Films films = BBSModClient.getFilms();
        if (films == null) {
            return false;
        }
        List<BaseFilmController> controllers = ((FilmsPovAccess)films).bbsPov$getControllers();
        if (controllers != null) {
            for (BaseFilmController controller : controllers) {
                if (controller.hasFinished()) continue;
                return true;
            }
        }
        return false;
    }

    public static boolean isFirstPersonPlayback() {
        Films films = BBSModClient.getFilms();
        if (films == null || films.getRecorder() != null) {
            return false;
        }
        return PovPlaybackContext.getActive() != null;
    }

    public static boolean isLocked() {
        return PovPlaybackInput.isFirstPersonPlayback();
    }

    public static boolean isAltKey(int key) {
        return key == 346;
    }

    public static boolean isAllowed(int key, int scancode) {
        try {
            KeyBinding playFilm = BBSModClientAccess.bbsPov$getKeyPlayFilm();
            if (playFilm != null && playFilm.matchesKey(key, scancode)) {
                return true;
            }
            KeyBinding recordVideo = BBSModClientAccess.bbsPov$getKeyRecordVideo();
            if (recordVideo != null && recordVideo.matchesKey(key, scancode)) {
                return true;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.options != null && mc.options.togglePerspectiveKey != null && mc.options.togglePerspectiveKey.matchesKey(key, scancode)) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return key == 345 || key == 293 || key == 294;
    }
}

