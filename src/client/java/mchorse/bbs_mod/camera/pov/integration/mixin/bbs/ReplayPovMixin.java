/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.actions.types.ActionClip
 *  mchorse.bbs_mod.actions.types.item.UseItemActionClip
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.numeric.ValueBoolean
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.ReplayPovAccess;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.item.UseItemActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Replay.class}, remap=false)
public class ReplayPovMixin
implements ReplayPovAccess {
    @Unique
    private ValueBoolean bbsPov$overlayEnabled;
    @Unique
    private ValueBoolean bbsPov$hardcoreLook;
    @Unique
    private ValueBoolean bbsPov$cameraShake;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void bbsPov$addReplaySettings(String id, CallbackInfo info) {
        Replay replay = (Replay)(Object)this;
        this.bbsPov$overlayEnabled = new ValueBoolean("bbs_pov_enabled", false);
        this.bbsPov$hardcoreLook = new ValueBoolean("bbs_pov_hardcore_look", false);
        this.bbsPov$cameraShake = new ValueBoolean("bbs_pov_camera_shake", true);
        replay.add((BaseValue)this.bbsPov$overlayEnabled);
        replay.add((BaseValue)this.bbsPov$hardcoreLook);
        replay.add((BaseValue)this.bbsPov$cameraShake);
        ReplayKeyframes replayKeyframes = replay.keyframes;
        if (replayKeyframes instanceof ReplayKeyframesPovAccess) {
            RecordedHandData hand;
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            RecordedHudData hotbar = access.bbsPov$getHud();
            if (hotbar != null) {
                hotbar.initializeReplayPreset(replay.keyframes);
            }
            if ((hand = access.bbsPov$getHand()) != null) {
                hand.initializeReplayPreset();
            }
        }
    }

    @Redirect(method={"applyClientActions"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/actions/types/ActionClip;applyClient(Lmchorse/bbs_mod/forms/entities/IEntity;Lmchorse/bbs_mod/film/Film;Lmchorse/bbs_mod/film/replays/Replay;I)V"))
    private void bbsPov$filterRejectedUseAction(ActionClip action, IEntity entity, Film film, Replay replay, int tick) {
        ReplayKeyframesPovAccess access;
        RecordedHandData hand;
        ReplayKeyframes replayKeyframes;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive();
        if (action instanceof UseItemActionClip && playback != null && playback.replay() == replay && (replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess && (hand = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHand()) != null && (Integer)hand.activeHand.interpolate((float)tick, 0) == 0) {
            return;
        }
        action.applyClient(entity, film, replay, tick);
    }

    @Override
    public ValueBoolean bbsPov$getOverlayEnabled() {
        return this.bbsPov$overlayEnabled;
    }

    @Override
    public ValueBoolean bbsPov$getHardcoreLook() {
        return this.bbsPov$hardcoreLook;
    }

    @Override
    public ValueBoolean bbsPov$getCameraShake() {
        return this.bbsPov$cameraShake;
    }
}

