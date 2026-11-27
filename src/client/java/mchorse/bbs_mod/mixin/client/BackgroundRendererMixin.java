package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.world.ClientWorld;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin
{
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void onBackgroundRender(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo info)
    {
        /* In an artificial space scene the horizon and the fog are the colour of empty space, not the blue of the sky */
        if (mchorse.bbs_mod.forms.renderers.utils.FakeSpaceSky.isActive())
        {
            red = mchorse.bbs_mod.forms.renderers.utils.FakeSpaceSky.red();
            green = mchorse.bbs_mod.forms.renderers.utils.FakeSpaceSky.green();
            blue = mchorse.bbs_mod.forms.renderers.utils.FakeSpaceSky.blue();
        }

        /* Recap Studio showing a recap with its fog colour overridden */
        Integer recapFog = mchorse.bbs_mod.recap.RecapScene.fogColor();

        if (recapFog != null)
        {
            red = (recapFog >> 16 & 0xff) / 255F;
            green = (recapFog >> 8 & 0xff) / 255F;
            blue = (recapFog & 0xff) / 255F;
        }

        BBSRendering.setFogColor(red, green, blue);
        RenderSystem.clearColor(red, green, blue, 1.0F);
    }

    /**
     * Under the orthographic projection the whole frame sits at roughly the
     * same depth, but blocks near the screen edges are laterally further from
     * the camera point than the view distance — the fog paints them sky
     * colored, which reads as geometry vanishing at the edges (and Sodium
     * additionally culls whole sections beyond the fog end it reads back from
     * RenderSystem). Push the fog out of reach for the ortho frame.
     */
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void onApplyFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo info)
    {
        if (BBSRendering.isOrthoActive())
        {
            RenderSystem.setShaderFogStart(1_000_000F);
            RenderSystem.setShaderFogEnd(1_001_000F);

            return;
        }

        /* Recap Studio showing a recap with its fog distance overridden: thick from a quarter of it */
        Float recapFog = mchorse.bbs_mod.recap.RecapScene.fogDistance();

        if (recapFog != null)
        {
            float end = Math.max(1F, recapFog);

            RenderSystem.setShaderFogStart(end * 0.25F);
            RenderSystem.setShaderFogEnd(end);
        }
    }
}
