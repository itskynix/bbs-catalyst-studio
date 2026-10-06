/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.ui.film.controller.FilmEditorController
 *  mchorse.bbs_mod.ui.film.controller.UIFilmController
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FilmEditorController.class}, remap=false)
public class FilmEditorControllerPovMixin {
    @Inject(method={"renderEntity"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideHeadLookSourceActor(WorldRenderContext context, Replay replay, IEntity entity, CallbackInfo info) {
        FilmEditorController self = (FilmEditorController)(Object)this;
        UIFilmController controller = self.controller;
        if (UIPovHandEditor.isActive() && replay == controller.panel.replayEditor.getReplay()) {
            info.cancel();
            return;
        }
        int povMode = controller.getPovMode();
        if (povMode == 6 || povMode == 1 || povMode == 2) {
            return;
        }
        Film film = self.film;
        float filmTick = controller.panel.getRunner() != null && controller.panel.getRunner().isRunning() ? (float)controller.panel.getRunner().ticks + context.tickDelta() : (float)controller.panel.getCursor();
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick);
        if (clip != null && ((Boolean)clip.headLook.get()).booleanValue() && clip.isFirstPerson() && replay == PovCameraClips.resolveReplay(film, clip)) {
            info.cancel();
        }
    }

    @Redirect(method={"renderEntity"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/ui/film/controller/UIFilmController;getPovMode()I"))
    private int bbsPov$treatPovAsFirstPerson(UIFilmController controller) {
        int mode = controller.getPovMode();
        return mode == 6 ? 3 : mode;
    }
}

