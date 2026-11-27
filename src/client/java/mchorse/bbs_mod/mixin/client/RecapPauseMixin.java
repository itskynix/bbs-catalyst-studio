package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.recap.RecapRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While a recap is being recorded and the setting asks for it, the single player world goes on
 * under a pausing screen, so the recording's time does too.
 */
@Mixin(MinecraftClient.class)
public class RecapPauseMixin
{
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;shouldPause()Z"), require = 0)
    private boolean bbs$keepWorldRunning(Screen screen)
    {
        return !RecapRecorder.keepsWorldRunning() && screen.shouldPause();
    }
}
