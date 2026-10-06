/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.tracks.TrackCatalog
 *  mchorse.bbs_mod.film.replays.tracks.TrackDescriptor
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIForm
 *  mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel
 *  mchorse.bbs_mod.ui.forms.editors.panels.UIGeneralFormPanel
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.UISection
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle
 *  mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform
 *  mchorse.bbs_mod.ui.framework.elements.input.UITrackpad
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIGeneralFormPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIGeneralFormPanel.class}, remap=false)
public abstract class UIGeneralFormPanelPovMixin
extends UIFormPanel {
    @Shadow
    public UIPropTransform transform;
    @Shadow
    public UIToggle hitbox;
    @Shadow
    public UITrackpad hp;

    public UIGeneralFormPanelPovMixin(UIForm editor) {
        super(editor);
    }

    @Inject(method={"startEdit"}, at={@At(value="TAIL")})
    private void bbsPov$hideSectionsInPov(Form form, CallbackInfo info) {
        UIPovEditor povEditor;
        UIFilmPanelPovAccess access;
        UIFilmPanel panel;
        boolean isHandEditMode = UIPovHandEditor.isActive();
        if (!isHandEditMode && (panel = PovReplaySettings.getFilmPanel()) instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)panel).bbsPov$isPovActive() && (povEditor = access.bbsPov$getEditor()) != null && povEditor.getSection() == UIPovEditor.Section.HAND && form == povEditor.getHandEditorForm()) {
            isHandEditMode = true;
        }
        if (this.options != null) {
            UISection movementSection;
            UISection transformSection = this.transform != null ? (UISection)this.transform.getParent(UISection.class) : null;
            UISection hitboxSection = this.hitbox != null ? (UISection)this.hitbox.getParent(UISection.class) : null;
            UISection uISection = movementSection = this.hp != null ? (UISection)this.hp.getParent(UISection.class) : null;
            if (transformSection != null) {
                transformSection.setVisible(!isHandEditMode);
            }
            if (hitboxSection != null) {
                hitboxSection.setVisible(!isHandEditMode);
            }
            if (movementSection != null) {
                movementSection.setVisible(!isHandEditMode);
            }
            this.options.resize();
        }
    }

    @Inject(method={"openTrackFilter"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$openTrackFilter(CallbackInfo info) {
        boolean isPovCamera;
        if (this.form == null) {
            return;
        }
        boolean isHandEditor = UIPovHandEditor.isActive();
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        UIPovEditor povEditor = null;
        if (panel instanceof UIFilmPanelPovAccess) {
            UIFilmPanelPovAccess access = (UIFilmPanelPovAccess)panel;
            povEditor = access.bbsPov$getEditor();
        }
        boolean bl = isPovCamera = panel != null && panel.getController() != null && panel.getController().getPovMode() == 6;
        if (isHandEditor || isPovCamera && povEditor != null && (this.form == povEditor.getHandEditorForm() || povEditor.isPoseGizmoSection())) {
            info.cancel();
            this.openFormTrackFilter(this.form, povEditor);
            return;
        }
    }

    private void openFormTrackFilter(Form form, UIPovEditor povEditor) {
        Set disabled = (Set)form.disabledTracks.get();
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        HashMap<String, Integer> keyToColor = new HashMap<String, Integer>();
        for (TrackDescriptor track : TrackCatalog.of((Form)form)) {
            keys.add(track.filterKey());
            keyToColor.put(track.filterKey(), track.color());
        }
        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(disabled, keys, keyToColor);
        UIOverlay.addOverlay((UIContext)this.getContext(), (UIOverlayPanel)panel, (int)240, (float)0.9f);
        panel.onClose(e -> {
            form.disabledTracks.set(disabled);
            if (povEditor != null) {
                povEditor.refreshSheets(false);
            }
        });
    }
}

