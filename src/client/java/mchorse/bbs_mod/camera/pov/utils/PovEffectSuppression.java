/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.camera.controller.RunnerCameraController
 */
package mchorse.bbs_mod.camera.pov.utils;

import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;

public final class PovEffectSuppression {
    private PovEffectSuppression() {
    }

    public static boolean isBbsActive() {
        if (PovPlaybackContext.getActive() != null || PovReplaySettings.getFilmPanel() != null) {
            return true;
        }
        return BBSModClient.getCameraController().getCurrent() instanceof RunnerCameraController;
    }
}

