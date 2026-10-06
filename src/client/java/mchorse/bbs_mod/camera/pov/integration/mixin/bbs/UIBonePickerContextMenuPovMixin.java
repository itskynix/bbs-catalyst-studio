/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.IBoneHierarchy
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneHierarchy;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneUtils;
import java.util.Collection;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={UIBonePickerContextMenu.class}, remap=false)
public class UIBonePickerContextMenuPovMixin {
    @ModifyVariable(method={"bones"}, at=@At(value="HEAD"), argsOnly=true)
    private IBoneHierarchy bbsPov$filterModel(IBoneHierarchy model) {
        ModelForm rootForm;
        ModelInstance instance;
        Form form;
        UIPovHandEditor editor;
        if (UIPovHandEditor.isActive() && model != null && (editor = UIPovHandEditor.getActive()) != null && (form = editor.getRootForm()) instanceof ModelForm && (instance = ModelFormRenderer.getModel((ModelForm)(rootForm = (ModelForm)form))) != null && instance.getModel() == model) {
            HandBoneUtils.HandBones handBones = HandBoneUtils.collect(instance);
            return new HandBoneHierarchy(model, handBones);
        }
        return model;
    }

    @ModifyVariable(method={"bones"}, at=@At(value="HEAD"), argsOnly=true)
    private Collection<String> bbsPov$filterHandBones(Collection<String> disabled, IBoneHierarchy model) {
        if (UIPovHandEditor.isActive()) {
            return null;
        }
        return disabled;
    }
}

