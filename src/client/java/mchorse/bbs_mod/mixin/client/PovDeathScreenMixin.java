package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.film.eye.PovGuiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The death screen drawn by a POV interface clip leaves its red to the clip (its red background channel, drawn before the screen). */
@Mixin(DeathScreen.class)
public class PovDeathScreenMixin
{
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info)
    {
        if (PovGuiRenderer.isDrawing())
        {
            info.cancel();
        }
    }
}
