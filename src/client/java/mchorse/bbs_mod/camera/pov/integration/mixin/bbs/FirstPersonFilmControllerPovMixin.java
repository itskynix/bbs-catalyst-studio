/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.FirstPersonFilmController
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FirstPersonFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FirstPersonFilmController.class}, remap=false)
public class FirstPersonFilmControllerPovMixin {
    @Inject(method={"renderEntity"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideHeadLookSourceActor(WorldRenderContext context, Replay replay, IEntity entity, CallbackInfo info) {
        FirstPersonFilmController controller = (FirstPersonFilmController)(Object)this;
        Film film = controller.film;
        if (replay != film.getFirstPersonReplay()) {
            return;
        }
        float transition = controller.paused ? 0.0f : Math.max(0.0f, Math.min(1.0f, context.tickDelta()));
        float filmTick = (float)controller.getTick() + transition;
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null && ((Boolean)clip.headLook.get()).booleanValue() && clip.isFirstPerson() && replay == PovCameraClips.resolveReplay(film, clip)) {
            info.cancel();
        }
    }
}

