/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.actions.SuperFakePlayer
 *  mchorse.bbs_mod.actions.types.item.UseItemActionClip
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  net.minecraft.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UseItemActionClip.class}, remap=false)
public abstract class UseItemActionClipPovMixin {
    @Inject(method={"applyAction"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$filterRejectedUse(LivingEntity actor, SuperFakePlayer fakePlayer, Film film, Replay replay, int tick, CallbackInfo info) {
        int active;
        ReplayKeyframes replayKeyframes = replay.keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedHandData hand = access.bbsPov$getHand();
        boolean mainHand = (Boolean)((UseItemActionClip)(Object)this).hand.get();
        int expected = mainHand ? 1 : 2;
        int n = active = hand == null ? 0 : (Integer)hand.activeHand.interpolate((float)tick, 0);
        if (active != expected) {
            info.cancel();
        }
    }
}

