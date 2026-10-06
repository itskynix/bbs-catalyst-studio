/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.forms.forms.BodyPart
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIForm
 *  mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm
 *  mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer
 *  org.joml.Matrix3f
 *  org.joml.Matrix3fc
 *  org.joml.Matrix4f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIModelRenderer.class}, remap=false)
public class UIModelRendererPovMixin {
    @Inject(method={"setupPosition"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$setupPosition(CallbackInfo info) {
        if (UIPovHandEditor.isActive()) {
            UIModelRenderer self = (UIModelRenderer)(Object)this;
            self.camera.position.set(0.0, 0.0, 0.0);
            info.cancel();
        }
    }

    @Inject(method={"getSceneAxes"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getPovSceneAxes(CallbackInfoReturnable<Matrix3f> info) {
        UIForm uIForm;
        UIPovHandEditor editor;
        if (UIPovHandEditor.isActive() && (editor = UIPovHandEditor.getActive()) != null && editor.getFormEditor() != null && (uIForm = editor.getFormEditor().editor) instanceof UIModelForm) {
            UIModelForm modelForm = (UIModelForm)uIForm;
            if (modelForm.modelPanel != null && modelForm.modelPanel.poseEditor != null) {
                if (modelForm.form == editor.getRootForm()) {
                    Matrix3f globalBasis;
                    String bone = (String)modelForm.modelPanel.poseEditor.groups.list.getCurrentFirst();
                    if (bone != null && !bone.isEmpty() && (globalBasis = PovHandMatrices.getGlobalBasis(bone)) != null) {
                        info.setReturnValue(new Matrix3f((Matrix3fc)globalBasis));
                    }
                } else {
                    BodyPart part = UIPovHandEditor.findBodyPart(modelForm.form);
                    if (part != null) {
                        Matrix4f bodyPartBase = UIPovHandEditor.getBodyPartBase(part);
                        info.setReturnValue(bodyPartBase.get3x3(new Matrix3f()));
                    }
                }
            }
        }
    }
}

