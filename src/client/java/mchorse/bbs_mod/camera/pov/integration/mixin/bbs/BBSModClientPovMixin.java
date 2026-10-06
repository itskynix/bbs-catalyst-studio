/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.playback.PovPlaybackInput;
import mchorse.bbs_mod.BBSModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BBSModClient.class}, remap=false)
public abstract class BBSModClientPovMixin {
    @Inject(method={"keyRecordReplay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$blockRecordReplayDuringPlayback(CallbackInfo info) {
        if (PovPlaybackInput.isLocked()) {
            info.cancel();
        }
    }

    @Inject(method={"keyPlayFilm", "keyPlayFilmAndRecord"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$blockPlayFilmDuringRecording(CallbackInfo info) {
        if (BBSModClient.getFilms() != null && BBSModClient.getFilms().getRecorder() != null) {
            info.cancel();
        }
    }
}

