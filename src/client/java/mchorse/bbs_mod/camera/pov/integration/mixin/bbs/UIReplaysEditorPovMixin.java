/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIReplaysEditor.class}, remap=false)
public class UIReplaysEditorPovMixin {
    @Shadow
    private UIFilmPanel filmPanel;

    @Inject(method={"selectBodyPart"}, at={@At(value="HEAD")})
    private void bbsPov$onSelectBodyPart(String path, CallbackInfo info) {
        UIPovEditor povEditor;
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel = this.filmPanel;
        if (uIFilmPanel instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$isPovActive() && (povEditor = access.bbsPov$getEditor()) != null) {
            povEditor.selectBodyPart(path);
        }
    }

    @Inject(method={"pickFormBone"}, at={@At(value="HEAD")})
    private void bbsPov$selectOwnerReplayOnPick(Form form, String bone, boolean insert, CallbackInfo info) {
        if (form == null || this.filmPanel == null || this.filmPanel.getData() == null) {
            return;
        }
        UIReplaysEditor self = (UIReplaysEditor)(Object)this;
        Form root = FormUtils.getRoot((Form)form);
        Film film = (Film)this.filmPanel.getData();
        for (Replay r : film.replays.getList()) {
            if (r == null || r.form.get() == null) continue;
            Form rRoot = FormUtils.getRoot((Form)((Form)r.form.get()));
            if (r.form.get() != root && rRoot != root) continue;
            if (self.getReplay() == r) break;
            self.setReplay(r);
            break;
        }
    }

    @Inject(method={"updateChannelsList"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$preventReplayEditorUpdateInPov(CallbackInfo info) {
        UIFilmPanelPovAccess access;
        UIFilmPanel uIFilmPanel = this.filmPanel;
        if (uIFilmPanel instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$isPovActive()) {
            info.cancel();
        }
    }

    @Inject(method={"collectCuratedSheets"}, at={@At(value="RETURN")})
    private void bbsPov$hideHotbarSlotsInReplayEditor(List<UIKeyframeSheet> sheets, CallbackInfo info) {
        sheets.removeIf(sheet -> UIReplaysEditorPovMixin.bbsPov$isPovOwnedReplaySheet(sheet.id));
    }

    private static boolean bbsPov$isPovOwnedReplaySheet(String id) {
        if ("item_off_hand".equals(id) || "selected_slot".equals(id)) {
            return true;
        }
        for (int i = 0; i < 9; ++i) {
            if (!ReplayKeyframes.hotbarChannelId((int)i).equals(id)) continue;
            return true;
        }
        return false;
    }
}

