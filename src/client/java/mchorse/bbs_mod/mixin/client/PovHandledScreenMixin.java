package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.eye.PovGuiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A game screen drawn by a POV interface clip leaves its slot tooltip to the clip, which draws it
 * after the film's cursor and what the cursor carries, so the tooltip is the topmost of them.
 */
@Mixin(HandledScreen.class)
public class PovHandledScreenMixin
{
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void onDrawMouseoverTooltip(DrawContext context, int x, int y, CallbackInfo info)
    {
        if (PovGuiRenderer.isDrawing())
        {
            info.cancel();
        }
    }
}
