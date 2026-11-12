package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
//? if >=1.21
/*import net.minecraft.client.render.RenderTickCounter;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin
{
    //? if >=1.21 {
    /*@Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    public void render(DrawContext drawContext, RenderTickCounter tickCounter, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();
        if (current instanceof PlayCameraController)
        {
            mchorse.bbs_mod.client.BBSRendering.onRenderBeforeScreen();
            mchorse.bbs_mod.client.BBSRendering.renderHud(drawContext, tickCounter.getTickDelta(true));
            info.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderEnd(DrawContext drawContext, RenderTickCounter tickCounter, CallbackInfo info)
    {
        mchorse.bbs_mod.client.BBSRendering.onRenderBeforeScreen();
    }
    *///?} else {
    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    public void render(DrawContext drawContext, float tickDelta, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();
        if (current instanceof PlayCameraController)
        {
            mchorse.bbs_mod.client.BBSRendering.onRenderBeforeScreen();
            mchorse.bbs_mod.client.BBSRendering.renderHud(drawContext, tickDelta);
            info.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void onRenderEnd(DrawContext drawContext, float tickDelta, CallbackInfo info)
    {
        mchorse.bbs_mod.client.BBSRendering.onRenderBeforeScreen();

        /* After everything else of the HUD, and drawn even with F1 hiding it; a screen draws its own copy */
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen == null)
        {
            mchorse.bbs_mod.dev.DevOverlay.render(drawContext);
        }
    }
    //?}
}
