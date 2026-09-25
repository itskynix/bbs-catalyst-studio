package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin
{
    /**
     * Under the orthographic projection the whole frame sits at roughly the
     * same depth, but blocks near the screen edges are laterally further from
     * the camera point than the view distance — the fog paints them sky
     * colored, which reads as geometry vanishing at the edges (and Sodium
     * additionally culls whole sections beyond the fog end it reads back from
     * RenderSystem). Push the fog out of reach for the ortho frame.
     *
     * In perspective / film view and export, ensure vanilla and Sodium distance fog
     * (CYLINDER shape, viewDistance - 10% to viewDistance) is strictly preserved.
     */
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void onApplyFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo info)
    {
        if (BBSRendering.isOrthoActive())
        {
            RenderSystem.setShaderFogStart(1_000_000F);
            RenderSystem.setShaderFogEnd(1_001_000F);
        }
        else if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            float maxViewDistance = mc.options.getClampedViewDistance() * 16.0F;
            float actualDistance = viewDistance > 0 ? viewDistance : Math.max(maxViewDistance - 16.0F, 32.0F);
            float g = MathHelper.clamp(actualDistance / 10.0F, 4.0F, 64.0F);
            float fogStart = Math.max(0.0F, actualDistance - g);
            float fogEnd = actualDistance;

            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(FogShape.CYLINDER);
        }
    }
}
