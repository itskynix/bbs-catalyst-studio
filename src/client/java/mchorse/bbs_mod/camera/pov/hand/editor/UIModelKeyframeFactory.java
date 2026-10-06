/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.forms.UIFormPalette
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 */
package mchorse.bbs_mod.camera.pov.hand.editor;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIModelKeyframeFactory
extends UIKeyframeFactory<String> {
    private final UIButton pickModel;
    private String currentModel;

    public UIModelKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        this.currentModel = (String)keyframe.getValue();
        if (this.currentModel == null) {
            this.currentModel = "";
        }
        this.pickModel = new UIButton(IKey.constant((String)this.getButtonLabel()), this::onPickModelClicked);
        this.scroll.add((IUIElement)this.pickModel);
    }

    private String getButtonLabel() {
        if (this.currentModel == null || this.currentModel.isBlank()) {
            return "Pick Model";
        }
        return "Model: " + this.currentModel;
    }

    private void updateButtonLabel() {
        this.pickModel.label = IKey.constant((String)this.getButtonLabel());
    }

    private void onPickModelClicked(UIButton button) {
        UIFormPalette palette;
        ModelForm currentForm = new ModelForm();
        if (this.currentModel != null && !this.currentModel.isBlank()) {
            currentForm.model.set(this.currentModel);
        }
        Object parent = null;
        UIFilmPanel filmPanel = PovViewportMetrics.resolveFilmPanel();
        if (filmPanel != null) {
            parent = filmPanel;
        } else if (this.getRoot() != null) {
            parent = this.getParentContainer();
        }
        if (parent == null) {
            parent = this.getParent();
        }
        if (parent == null) {
            parent = this;
        }
        if ((palette = UIFormPalette.open((UIElement)parent, (boolean)false, (Form)currentForm, form -> {
            if (form == null) {
                return;
            }
            Form root = FormUtils.getRoot((Form)form);
            if (root instanceof ModelForm) {
                ModelForm mf = (ModelForm)root;
                String pickedModel = (String)mf.model.get();
                if (pickedModel != null && !pickedModel.isBlank()) {
                    UIFilmPanelPovAccess access;
                    UIPovEditor povEditor;
                    this.setModel(pickedModel);
                    if (filmPanel instanceof UIFilmPanelPovAccess && (povEditor = (access = (UIFilmPanelPovAccess)filmPanel).bbsPov$getEditor()) != null) {
                        povEditor.refreshSheets(false);
                    }
                }
            }
        })) != null) {
            palette.updatable();
        }
    }

    private void setModel(String model) {
        this.currentModel = model == null ? "" : model;
        this.setValue(this.currentModel);
        this.updateButtonLabel();
    }

    public void update() {
        super.update();
        String val = (String)this.keyframe.getValue();
        if (val != null && !val.equals(this.currentModel)) {
            this.currentModel = val;
            this.updateButtonLabel();
        }
    }

    public void render(UIContext context) {
        context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), -15461356);
        super.render(context);
    }
}

