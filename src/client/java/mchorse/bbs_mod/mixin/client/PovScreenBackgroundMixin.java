package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.eye.PovGuiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a POV interface clip draws a game screen into the film's frame, the dimming of the world
 * behind it is the clip's own (its darkness channel, drawn before the screen over the whole frame), not the screen's.
 */
@Mixin(Screen.class)
public class PovScreenBackgroundMixin
{
    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderInGameBackground(DrawContext context, CallbackInfo info)
    {
        if (PovGuiRenderer.isDrawing())
        {
            info.cancel();
        }
    }
}
