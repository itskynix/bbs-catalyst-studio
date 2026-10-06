/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.ModelInstance
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.ui.forms.editors.UIBodyPartEditor
 *  mchorse.bbs_mod.utils.Pair
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.editor.HandBoneUtils;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.forms.editors.UIBodyPartEditor;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIBodyPartEditor.class}, remap=false)
public class UIBodyPartEditorPovMixin {
    @Inject(method={"pickBone"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$restrictViewportPickBone(Pair<Form, String> pair, CallbackInfoReturnable<Boolean> info) {
        HandBoneUtils.HandBones handBones;
        ModelForm rootForm;
        ModelInstance instance;
        Form form;
        UIPovHandEditor editor;
        if (UIPovHandEditor.isActive() && pair != null && (editor = UIPovHandEditor.getActive()) != null && pair.a == editor.getRootForm() && (form = editor.getRootForm()) instanceof ModelForm && (instance = ModelFormRenderer.getModel((ModelForm)(rootForm = (ModelForm)form))) != null && !(handBones = HandBoneUtils.collect(instance)).contains((String)pair.b)) {
            info.setReturnValue(false);
        }
    }
}

