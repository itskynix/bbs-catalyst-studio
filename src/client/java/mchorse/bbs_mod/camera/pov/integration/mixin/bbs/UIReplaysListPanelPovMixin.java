/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.UIReplayList
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel
 *  mchorse.bbs_mod.ui.forms.editors.UIForms
 *  mchorse.bbs_mod.ui.forms.editors.UIForms$FormEntry
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.UISection
 *  mchorse.bbs_mod.ui.utils.resizers.IResizer
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIReplaysListPanelPovAccess;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.utils.resizers.IResizer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIReplaysListPanel.class}, remap=false)
public abstract class UIReplaysListPanelPovMixin
extends UIElement
implements UIReplaysListPanelPovAccess {
    @Shadow
    @Final
    public UIElement content;
    @Shadow
    @Final
    public UIElement bar;
    @Shadow
    @Final
    public UIReplayList replays;
    @Shadow
    private UIFilmPanel filmPanel;
    @Shadow
    private Replay bodyPartsReplay;
    @Shadow
    @Final
    public UIForms bodyParts;
    @Shadow
    @Final
    private UISection bodyPartsSection;
    @Unique
    private UIForms bbsPov$povBodyParts;
    @Unique
    private UISection bbsPov$povBodyPartsSection;
    @Unique
    private boolean bbsPov$povActive;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void bbsPov$init(UIFilmPanel panel, Consumer<List<Replay>> callback, Consumer<Form> formConsumer, Consumer<String> partConsumer, CallbackInfo info) {
        this.bbsPov$povBodyParts = new UIForms(list -> {
            if (!list.isEmpty()) {
                UIFilmPanelPovAccess access;
                UIPovEditor editor;
                String path = ((UIForms.FormEntry)list.get(0)).getPath();
                UIFilmPanel patt0$temp = this.filmPanel;
                if (patt0$temp instanceof UIFilmPanelPovAccess && (editor = (access = (UIFilmPanelPovAccess)patt0$temp).bbsPov$getEditor()) != null) {
                    editor.selectBodyPart(path);
                }
            }
        });
        this.bbsPov$povBodyPartsSection = new UISection(IKey.constant((String)"POV Body parts"));
        this.bbsPov$povBodyPartsSection.fields.add((IUIElement)this.bbsPov$povBodyParts);
        this.bbsPov$povBodyPartsSection.setExpanded(false);
        int padding = 3;
        this.bbsPov$povBodyPartsSection.relative(this.content).x(padding).y(1.0f, -padding).w(1.0f, -padding * 2).anchorY(1.0f);
        this.bbsPov$povBodyPartsSection.setVisible(false);
        this.content.addAfter((IUIElement)this.bodyPartsSection, (IUIElement)this.bbsPov$povBodyPartsSection);
    }

    @Inject(method={"setBodyPartsReplay"}, at={@At(value="TAIL")})
    private void bbsPov$updatePovBodyParts(Replay replay, String path, CallbackInfoReturnable<String> info) {
        UIFilmPanelPovAccess access;
        UIPovEditor editor;
        Form handForm;
        ReplayKeyframes replayKeyframes;
        if (this.bbsPov$povBodyParts == null) {
            return;
        }
        if (replay == null || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
            this.bbsPov$povBodyParts.clear();
            return;
        }
        ReplayKeyframesPovAccess keyAccess = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedHandData hand = keyAccess.bbsPov$getHand();
        Form form = handForm = hand != null && hand.baseForm.get() != null ? (Form)hand.baseForm.get() : (Form)replay.form.get();
        if (handForm == null) {
            this.bbsPov$povBodyParts.clear();
            return;
        }
        this.bbsPov$povBodyParts.setForm(handForm);
        UIFilmPanel uIFilmPanel = this.filmPanel;
        if (uIFilmPanel instanceof UIFilmPanelPovAccess && (editor = (access = (UIFilmPanelPovAccess)uIFilmPanel).bbsPov$getEditor()) != null && editor.getSelectedBodyPart() != null) {
            this.bbsPov$povBodyParts.setCurrentPath(editor.getSelectedBodyPart());
        }
    }

    @Inject(method={"resize"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$resize(CallbackInfo info) {
        info.cancel();
        boolean hasReplay = this.bodyPartsReplay != null;
        boolean pov = this.bbsPov$povActive;
        int maxHeight = Math.min(160, this.getFlex().getH() / 2);
        int rowsHeight1 = this.bodyParts.getList().size() * this.bodyParts.scroll.scrollItemSize;
        this.bodyParts.h(Math.max(1, Math.min(rowsHeight1, maxHeight)));
        if (this.bbsPov$povBodyParts != null) {
            int rowsHeight2 = this.bbsPov$povBodyParts.getList().size() * this.bbsPov$povBodyParts.scroll.scrollItemSize;
            this.bbsPov$povBodyParts.h(Math.max(1, Math.min(rowsHeight2, maxHeight)));
        }
        this.bodyPartsSection.setVisible(hasReplay && !pov);
        if (this.bbsPov$povBodyPartsSection != null) {
            this.bbsPov$povBodyPartsSection.setVisible(hasReplay && pov);
        }
        UISection activeSection = pov ? this.bbsPov$povBodyPartsSection : this.bodyPartsSection;
        this.replays.hTo((IResizer)(hasReplay && activeSection != null ? activeSection.area : this.content.area), hasReplay ? 0.0f : 1.0f);
        super.resize();
    }

    @Override
    public void bbsPov$setPovMode(boolean povActive) {
        this.bbsPov$povActive = povActive;
        this.resize();
        if (this.content != null) {
            this.content.resize();
        }
    }

    @Override
    public UIForms bbsPov$getPovBodyParts() {
        return this.bbsPov$povBodyParts;
    }
}

