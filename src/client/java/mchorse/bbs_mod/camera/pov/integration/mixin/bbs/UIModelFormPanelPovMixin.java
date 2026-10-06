/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.IBoneHierarchy
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel
 *  mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIModelFormPanel.class}, remap=false)
public class UIModelFormPanelPovMixin {
    @Shadow
    public UIModelPoseEditor poseEditor;

    @Inject(method={"startEdit(Lmchorse/bbs_mod/forms/forms/ModelForm;)V"}, at={@At(value="TAIL")})
    private void bbsPov$filterPoseEditor(ModelForm form, CallbackInfo info) {
        if (UIPovHandEditor.isActive()) {
            boolean isRoot;
            UIPovHandEditor editor = UIPovHandEditor.getActive();
            boolean bl = isRoot = editor != null && form == editor.getRootForm();
            if (isRoot) {
                UIModelFormPanel self = (UIModelFormPanel)(Object)this;
                if (self.shapeKeysSection != null) {
                    self.shapeKeysSection.removeFromParent();
                    self.options.resize();
                }
                UIPovHandEditor.filterModelPoseEditor(this.poseEditor, form);
            } else {
                ModelInstance model = ModelFormRenderer.getModel((ModelForm)form);
                if (model != null && model.getModel() != null) {
                    this.poseEditor.fillGroups((IBoneHierarchy)model.getModel(), model.getFlippedParts(), false, null);
                }
            }
        }
    }
}

