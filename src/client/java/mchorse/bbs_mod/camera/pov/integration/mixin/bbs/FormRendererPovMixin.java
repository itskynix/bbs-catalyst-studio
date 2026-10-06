/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.renderers.FormRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  org.joml.Matrix4f
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FormRenderer.class}, remap=false)
public abstract class FormRendererPovMixin {
    @Shadow
    public Form form;

    @Inject(method={"applyTransforms(Lnet/minecraft/client/util/math/MatrixStack;ZF)V"}, at={@At(value="HEAD")}, cancellable=true, remap=true, require=0)
    private void bbsPov$suppressFormTransforms(MatrixStack stack, boolean origin, float transition, CallbackInfo info) {
        if (PovHandPlayback.isSuppressFormTransform(this.form)) {
            info.cancel();
        }
    }

    @Inject(method={"applyTransforms(Lorg/joml/Matrix4f;F)V"}, at={@At(value="HEAD")}, cancellable=true, remap=false, require=0)
    private void bbsPov$suppressMatrixTransforms(Matrix4f matrix, float transition, CallbackInfo info) {
        if (PovHandPlayback.isSuppressFormTransform(this.form)) {
            info.cancel();
        }
    }
}

