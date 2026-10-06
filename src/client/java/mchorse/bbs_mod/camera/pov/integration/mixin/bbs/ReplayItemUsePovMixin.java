/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.animation.ItemUsePose$Use
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayItemUse
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ReplayItemUse.class}, remap=false)
public abstract class ReplayItemUsePovMixin {
    @Inject(method={"compute"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$useRecordedHandState(Replay replay, float tick, boolean mainHand, CallbackInfoReturnable<ItemUsePose.Use> info) {
        int expected;
        boolean filmPreview;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive();
        boolean povPlayback = playback != null && playback.replay() == replay;
        boolean bl = filmPreview = PovReplaySettings.getSelectedReplay() == replay;
        if (!povPlayback && !filmPreview) {
            return;
        }
        ReplayKeyframes replayKeyframes = replay.keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedHandData hand = access.bbsPov$getHand();
        if (hand == null) {
            info.setReturnValue(null);
            return;
        }
        int active = (Integer)hand.activeHand.interpolate((float)Math.floor(tick), 0);
        int n = expected = mainHand ? 1 : 2;
        if (active != expected) {
            info.setReturnValue(null);
        }
    }
}

