/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.render.BackgroundRenderer
 *  net.minecraft.client.render.BackgroundRenderer$FogType
 *  net.minecraft.client.render.Camera
 *  net.minecraft.client.render.FogShape
 *  net.minecraft.client.world.ClientWorld
 *  net.minecraft.util.math.MathHelper
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovBlindnessHelper;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.PovDarknessHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BackgroundRenderer.class})
public abstract class BackgroundRendererPovMixin {
    @Shadow
    private static float field_4034;
    @Shadow
    private static float field_4033;
    @Shadow
    private static float field_4032;

    @Inject(method={"render"}, at={@At(value="TAIL")})
    private static void bbsPov$darkenFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo info) {
        float darkFactor = PovDarknessHelper.resolveDarknessFactor(tickDelta);
        float blindFactor = PovBlindnessHelper.resolveBlindnessFactor(tickDelta);
        float totalFactor = Math.max(Math.max(0.0f, darkFactor), Math.max(0.0f, blindFactor));
        if (totalFactor > 0.001f) {
            float mult = 1.0f - totalFactor;
            RenderSystem.clearColor((float)(field_4034 *= mult), (float)(field_4033 *= mult), (float)(field_4032 *= mult), (float)0.0f);
        }
    }

    @Inject(method={"applyFog"}, at={@At(value="TAIL")})
    private static void bbsPov$applyDarknessOrBlindnessFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo info) {
        float blindFactor = PovBlindnessHelper.resolveBlindnessFactor(tickDelta);
        if (blindFactor > 0.001f) {
            float radius = PovBlindnessHelper.resolveBlindnessRadius(tickDelta);
            float g = MathHelper.lerp((float)blindFactor, (float)(viewDistance * 0.75f), (float)radius);
            float fogStart = fogType == BackgroundRenderer.FogType.FOG_SKY ? 0.0f : g * 0.25f;
            float fogEnd = g;
            RenderSystem.setShaderFogStart((float)fogStart);
            RenderSystem.setShaderFogEnd((float)fogEnd);
            RenderSystem.setShaderFogShape((FogShape)FogShape.SPHERE);
            return;
        }
        float darkFactor = PovDarknessHelper.resolveDarknessFactor(tickDelta);
        if (darkFactor > 0.001f) {
            float radius = PovDarknessHelper.resolveDarknessRadius(tickDelta);
            float g = MathHelper.lerp((float)darkFactor, (float)viewDistance, (float)radius);
            float fogStart = fogType == BackgroundRenderer.FogType.FOG_SKY ? 0.0f : g * 0.75f;
            float fogEnd = g;
            RenderSystem.setShaderFogStart((float)fogStart);
            RenderSystem.setShaderFogEnd((float)fogEnd);
            RenderSystem.setShaderFogShape((FogShape)FogShape.SPHERE);
        }
    }
}

