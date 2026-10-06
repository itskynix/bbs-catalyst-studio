/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.compat.iris;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovDarknessHelper;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"net.irisshaders.iris.uniforms.CapturedRenderingState"}, remap=false)
public class IrisCapturedRenderingStatePovMixin {
    @Inject(method={"getDarknessLightFactor"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getDarknessLightFactor(CallbackInfoReturnable<Float> info) {
        float delta = MinecraftClient.getInstance().getTickDelta();
        float factor = PovDarknessHelper.resolveDarknessLightFactor(delta);
        if (factor >= 0.0f) {
            info.setReturnValue(Float.valueOf(factor));
        }
    }
}

