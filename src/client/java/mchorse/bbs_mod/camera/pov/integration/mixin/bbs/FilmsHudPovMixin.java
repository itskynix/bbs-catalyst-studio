/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.film.BaseFilmController
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.Films
 *  mchorse.bbs_mod.film.FirstPersonFilmController
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.clips.Clip
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.RecorderPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.recording.PovRecordingSession;
import mchorse.bbs_mod.camera.pov.render.PovOverlayRenderer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.FirstPersonFilmController;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.Clip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Films.class}, remap=false)
public abstract class FilmsHudPovMixin {
    @Shadow
    private Recorder recorder;
    @Shadow
    private List<BaseFilmController> controllers;
    @Unique
    private final Map<BaseFilmController, Integer> bbsPov$lastPlaybackTicks = new HashMap<BaseFilmController, Integer>();

    @Inject(method={"update"}, at={@At(value="HEAD")})
    private void bbsPov$dispatchThirdPersonPlaybackChat(CallbackInfo info) {
        if (this.controllers == null || this.controllers.isEmpty()) {
            this.bbsPov$lastPlaybackTicks.clear();
            return;
        }
        if (BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording()) {
            return;
        }
        if (PovPlaybackContext.getActive() != null) {
            return;
        }
        for (BaseFilmController controller : this.controllers) {
            int startRange;
            if (!(controller instanceof FirstPersonFilmController)) continue;
            FirstPersonFilmController firstPerson = (FirstPersonFilmController)controller;
            Film film = firstPerson.film;
            if (film == null || film.hasFirstPerson()) continue;
            int currentTick = firstPerson.getTick();
            int lastTick = this.bbsPov$lastPlaybackTicks.getOrDefault(controller, -1);
            if (lastTick < 0 || currentTick < lastTick || currentTick - lastTick > 100) {
                lastTick = currentTick - 1;
            }
            if (currentTick <= lastTick) continue;
            for (int t = startRange = Math.max(0, lastTick + 1); t <= currentTick; ++t) {
                PovRecordingSession.dispatchExternalChatMessages(film, -1, t);
            }
            this.bbsPov$lastPlaybackTicks.put(controller, currentTick);
        }
    }

    @Inject(method={"startRecording(Lmchorse/bbs_mod/film/Film;IIZ)V"}, at={@At(value="TAIL")})
    private void bbsPov$tagOutsideRecording(Film film, int replayId, int tick, boolean onMark, CallbackInfo info) {
        Recorder recorder = this.recorder;
        if (recorder instanceof RecorderPovAccess) {
            RecorderPovAccess access = (RecorderPovAccess)recorder;
            access.bbsPov$setOutside(onMark);
        }
    }

    @Redirect(method={"playFilm(Lmchorse/bbs_mod/film/Film;Z)V"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/film/Film;hasFirstPerson()Z"))
    private static boolean bbsPov$allowCameraTimelineForPovClips(Film film) {
        return film.hasFirstPerson() || PovCameraClips.hasAny(film) && film.camera.getClips(Clip.class).stream().allMatch(clip -> clip instanceof PovCameraClip);
    }

    @Inject(method={"renderHud"}, at={@At(value="RETURN")})
    private void bbsPov$renderPlaybackHud(Batcher2D batcher, float tickDelta, CallbackInfo info) {
        PovOverlayRenderer.renderPlayback(batcher, tickDelta);
    }
}

