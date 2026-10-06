/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.render.LightmapTextureManager
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffects
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovDarknessHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovNightVisionHelper;
import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LightmapTextureManager.class})
public abstract class LightmapTextureManagerPovMixin {
    @Inject(method={"getDarknessFactor"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$suppressDarkness(float delta, CallbackInfoReturnable<Float> info) {
        float povDarkness = PovDarknessHelper.resolveDarknessLightFactor(delta);
        if (povDarkness >= 0.0f) {
            info.setReturnValue(Float.valueOf(povDarkness));
            return;
        }
        if (PovEffectSuppression.isBbsActive()) {
            info.setReturnValue(Float.valueOf(0.0f));
        }
    }

    @Redirect(method={"update"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z"))
    private boolean bbsPov$hasStatusEffect(ClientPlayerEntity player, StatusEffect effect) {
        float povNv;
        if (effect == StatusEffects.NIGHT_VISION && (povNv = PovNightVisionHelper.resolveNightVisionStrength(0.0f)) >= 0.0f) {
            return povNv > 0.001f;
        }
        return player.hasStatusEffect(effect);
    }

    @Redirect(method={"update"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float bbsPov$getNightVisionStrength(LivingEntity entity, float delta) {
        float povNv = PovNightVisionHelper.resolveNightVisionStrength(delta);
        if (povNv >= 0.0f) {
            return povNv;
        }
        return GameRenderer.getNightVisionStrength((LivingEntity)entity, (float)delta);
    }
}

