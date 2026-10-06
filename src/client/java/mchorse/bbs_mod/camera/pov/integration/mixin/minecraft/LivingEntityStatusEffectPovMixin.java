/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.entity.effect.StatusEffects
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovBlindnessHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovDarknessHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovNightVisionHelper;
import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LivingEntity.class})
public abstract class LivingEntityStatusEffectPovMixin {
    @Inject(method={"hasStatusEffect"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$suppressVisualStatusEffects(StatusEffect effect, CallbackInfoReturnable<Boolean> info) {
        if (PovEffectSuppression.isBbsActive()) {
            float tickDelta = MinecraftClient.getInstance().getTickDelta();
            if (effect == StatusEffects.BLINDNESS) {
                float factor = PovBlindnessHelper.resolveBlindnessFactor(tickDelta);
                info.setReturnValue(factor > 0.001f);
                return;
            }
            if (effect == StatusEffects.DARKNESS) {
                float factor = PovDarknessHelper.resolveDarknessFactor(tickDelta);
                info.setReturnValue(factor > 0.001f);
                return;
            }
            if (effect == StatusEffects.NIGHT_VISION) {
                float factor = PovNightVisionHelper.resolveNightVisionStrength(tickDelta);
                info.setReturnValue(factor > 0.001f);
                return;
            }
            if (effect == StatusEffects.NAUSEA) {
                info.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method={"getStatusEffect"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$suppressVisualStatusEffectInstance(StatusEffect effect, CallbackInfoReturnable<StatusEffectInstance> info) {
        if (PovEffectSuppression.isBbsActive()) {
            float tickDelta = MinecraftClient.getInstance().getTickDelta();
            if (effect == StatusEffects.BLINDNESS) {
                float factor = PovBlindnessHelper.resolveBlindnessFactor(tickDelta);
                info.setReturnValue((factor > 0.001f ? new StatusEffectInstance(StatusEffects.BLINDNESS, 200, 0, false, false, false) : null));
                return;
            }
            if (effect == StatusEffects.DARKNESS) {
                float factor = PovDarknessHelper.resolveDarknessFactor(tickDelta);
                info.setReturnValue((factor > 0.001f ? new StatusEffectInstance(StatusEffects.DARKNESS, 200, 0, false, false, false) : null));
                return;
            }
            if (effect == StatusEffects.NIGHT_VISION) {
                float factor = PovNightVisionHelper.resolveNightVisionStrength(tickDelta);
                info.setReturnValue((factor > 0.001f ? new StatusEffectInstance(StatusEffects.NIGHT_VISION, 200, 0, false, false, false) : null));
                return;
            }
            if (effect == StatusEffects.NAUSEA) {
                info.setReturnValue(null);
                return;
            }
        }
    }
}

