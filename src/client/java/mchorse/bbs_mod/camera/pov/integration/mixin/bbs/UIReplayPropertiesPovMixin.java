/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.UIKeys
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.UIReplayPropertiesPanel
 *  mchorse.bbs_mod.ui.forms.UIFormPalette
 *  mchorse.bbs_mod.ui.forms.UINestedEdit
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.UISection
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle
 *  mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIReplayPropertiesPovAccess;
import mchorse.bbs_mod.camera.pov.replay.ReplayPovAccess;
import java.util.function.Consumer;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayPropertiesPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIReplayPropertiesPanel.class}, remap=false)
public abstract class UIReplayPropertiesPovMixin
implements UIReplayPropertiesPovAccess {
    @Shadow
    @Final
    private UIFilmPanel filmPanel;
    @Shadow
    public UIElement properties;
    @Shadow
    public UITextbox nameTag;
    @Shadow
    private Replay replay;
    @Unique
    private UINestedEdit bbsPov$povPickEdit;
    @Unique
    private UIToggle bbsPov$povHardcoreLook;
    @Unique
    private UIToggle bbsPov$povCameraShake;

    @Shadow
    private void edit(Consumer<Replay> consumer) {
    }

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void bbsPov$init(UIFilmPanel filmPanel, CallbackInfo info) {
        this.bbsPov$povPickEdit = new UINestedEdit(edit -> {
            if (!edit.booleanValue()) {
                this.bbsPov$pickBaseForm();
            } else {
                this.bbsPov$openHandEditor();
            }
        });
        if (this.bbsPov$povPickEdit.pick != null) {
            this.bbsPov$povPickEdit.pick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM);
        }
        if (this.bbsPov$povPickEdit.edit != null) {
            this.bbsPov$povPickEdit.edit.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM);
            this.bbsPov$povPickEdit.edit.setEnabled(true);
        }
        this.bbsPov$povHardcoreLook = new UIToggle(L10n.lang("bbs.pov.replay.hardcore_look", "Hardcore look"), toggle -> this.edit(r -> {
            if (r instanceof ReplayPovAccess) {
                ReplayPovAccess access = (ReplayPovAccess)r;
                access.bbsPov$getHardcoreLook().set(toggle.getValue());
            }
        }));
        this.bbsPov$povHardcoreLook.tooltip(L10n.lang("bbs.pov.replay.hardcore_look.tooltip", "Show that replay's actual head bone position in POV Camera mode"));
        this.bbsPov$povHardcoreLook.valueBinding(() -> {
            Replay patt0$temp = this.replay;
            if (patt0$temp instanceof ReplayPovAccess) {
                ReplayPovAccess access = (ReplayPovAccess)patt0$temp;
                this.bbsPov$povHardcoreLook.setValue(((Boolean)access.bbsPov$getHardcoreLook().get()).booleanValue());
            }
        });
        this.bbsPov$povCameraShake = new UIToggle(L10n.lang("bbs.pov.replay.camera_shake", "Camera shake"), toggle -> this.edit(r -> {
            if (r instanceof ReplayPovAccess) {
                ReplayPovAccess access = (ReplayPovAccess)r;
                access.bbsPov$getCameraShake().set(toggle.getValue());
            }
        }));
        this.bbsPov$povCameraShake.tooltip(L10n.lang("bbs.pov.replay.camera_shake.tooltip", "Show baked Camera Shake for this replay in POV Camera mode"));
        this.bbsPov$povCameraShake.valueBinding(() -> {
            Replay patt0$temp = this.replay;
            if (patt0$temp instanceof ReplayPovAccess) {
                ReplayPovAccess access = (ReplayPovAccess)patt0$temp;
                this.bbsPov$povCameraShake.setValue(((Boolean)access.bbsPov$getCameraShake().get()).booleanValue());
            }
        });
        UISection povSection = new UISection(L10n.lang("bbs.pov.replay.section", "POV"));
        povSection.fields.add((IUIElement)this.bbsPov$povPickEdit);
        povSection.fields.add((IUIElement)this.bbsPov$povHardcoreLook);
        povSection.fields.add((IUIElement)this.bbsPov$povCameraShake);
        povSection.setExpanded(false);
        this.properties.addAfter((IUIElement)this.nameTag, (IUIElement)povSection);
    }

    @Inject(method={"setReplay"}, at={@At(value="TAIL")})
    private void bbsPov$setReplay(Replay replay, CallbackInfo info) {
        if (this.bbsPov$povPickEdit != null) {
            ReplayKeyframes replayKeyframes;
            if (replay != null && (replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess) {
                ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
                RecordedHandData hand = access.bbsPov$getHand();
                Form baseForm = hand != null && hand.baseForm.get() != null ? (Form)hand.baseForm.get() : (Form)replay.form.get();
                this.bbsPov$povPickEdit.setForm(baseForm);
            } else if (replay != null) {
                this.bbsPov$povPickEdit.setForm((Form)replay.form.get());
            } else {
                this.bbsPov$povPickEdit.setForm(null);
            }
            if (this.bbsPov$povPickEdit.edit != null) {
                this.bbsPov$povPickEdit.edit.setEnabled(true);
            }
        }
    }

    @Override
    public void bbsPov$setPovMode(boolean povActive) {
    }

    @Unique
    private void bbsPov$pickBaseForm() {
        UIFormPalette palette;
        Replay currentReplay = this.replay != null ? this.replay : (this.filmPanel.replayEditor != null ? this.filmPanel.replayEditor.getReplay() : null);
        if (currentReplay == null) {
            return;
        }
        ReplayKeyframes replayKeyframes = currentReplay.keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        RecordedHandData hand = access.bbsPov$getHand();
        if (hand == null) {
            return;
        }
        Form current = (Form)hand.baseForm.get();
        if (current == null) {
            current = (Form)currentReplay.form.get();
        }
        UIElement parent = this.filmPanel;
        if (this.filmPanel.getRoot() != null) {
            parent = this.filmPanel.getParentContainer();
        }
        if ((palette = UIFormPalette.open(parent, (boolean)false, (Form)current, picked -> {
            UIFilmPanelPovAccess povAccess;
            UIPovEditor editor;
            UIFilmPanel patt0$temp;
            Form copy = picked == null ? null : FormUtils.copy((Form)picked);
            hand.baseForm.set(copy);
            if (this.bbsPov$povPickEdit != null) {
                this.bbsPov$povPickEdit.setForm(copy);
                if (this.bbsPov$povPickEdit.edit != null) {
                    this.bbsPov$povPickEdit.edit.setEnabled(true);
                }
            }
            if ((patt0$temp = this.filmPanel) instanceof UIFilmPanelPovAccess && (editor = (povAccess = (UIFilmPanelPovAccess)patt0$temp).bbsPov$getEditor()) != null) {
                editor.reloadHandModel();
            }
            if (this.filmPanel.replayEditor != null && this.filmPanel.replayEditor.replaysList != null) {
                this.filmPanel.replayEditor.replaysList.replays.update();
                this.filmPanel.replayEditor.replaysList.setBodyPartsReplay(currentReplay, "");
            }
        })) != null) {
            palette.updatable();
        }
    }

    @Unique
    private void bbsPov$openHandEditor() {
        ReplayKeyframes replayKeyframes;
        Replay currentReplay = this.replay != null ? this.replay : (this.filmPanel.replayEditor != null ? this.filmPanel.replayEditor.getReplay() : null);
        RecordedHandData hand = null;
        if (currentReplay != null && (replayKeyframes = currentReplay.keyframes) instanceof ReplayKeyframesPovAccess) {
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            hand = access.bbsPov$getHand();
        }
        UIPovHandEditor.open(this.filmPanel, currentReplay, hand);
    }
}

