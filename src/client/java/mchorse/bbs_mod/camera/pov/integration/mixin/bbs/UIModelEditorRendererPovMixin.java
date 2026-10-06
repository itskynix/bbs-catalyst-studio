/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.model_editor.ModelSlotTarget
 *  mchorse.bbs_mod.ui.model_editor.UIModelEditorRenderer
 *  mchorse.bbs_mod.ui.utils.GizmoDrag
 *  org.joml.Matrix4f
 *  org.joml.Vector3f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.integration.access.bbs.IGizmoDragFirstPerson;
import mchorse.bbs_mod.ui.model_editor.ModelSlotTarget;
import mchorse.bbs_mod.ui.model_editor.UIModelEditorRenderer;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIModelEditorRenderer.class}, remap=false)
public abstract class UIModelEditorRendererPovMixin {
    @Shadow
    private Matrix4f boneMatrix(ModelSlotTarget target) {
        return null;
    }

    @Inject(method={"buildGizmoDrag"}, at={@At(value="RETURN")})
    private void bbsPov$attachFirstPersonPivot(ModelSlotTarget target, CallbackInfoReturnable<GizmoDrag> info) {
        Matrix4f bone;
        GizmoDrag drag = (GizmoDrag)info.getReturnValue();
        if (drag != null && target != null && target.kind() != null && target.kind().firstPerson && (bone = this.boneMatrix(target)) != null) {
            ((IGizmoDragFirstPerson)drag).bbsPov$setRotationPivot(bone.getTranslation(new Vector3f()));
        }
    }
}

