/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  net.minecraft.client.toast.Toast
 *  net.minecraft.client.toast.ToastManager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.toast.recording.ToastRecorder;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ToastManager.class})
public class ToastManagerPovMixin {
    @Inject(method={"add"}, at={@At(value="HEAD")})
    private void bbsPov$onToastAdded(Toast toast, CallbackInfo ci) {
        if (!PovSettings.isBakeAnyActions()) {
            return;
        }
        try {
            ReplayKeyframesPovAccess access;
            RecordedPovActions actions;
            ReplayKeyframes replayKeyframes;
            Recorder recorder = BBSModClient.getFilms().getRecorder();
            if (recorder != null && !recorder.hasNotStarted() && recorder.tick >= 0 && (replayKeyframes = recorder.keyframes) instanceof ReplayKeyframesPovAccess && (actions = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getActions()) != null) {
                ToastRecorder.onToastAdded(toast, actions, recorder.tick);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

