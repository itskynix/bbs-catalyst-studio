package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin
{
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    public void onOnKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        BBSRendering.lastAction = action;

        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F1)
        {
            if (BBSModClient.getVideoRecorder().isRecording() || (BBSModClient.getDashboard() != null && BBSModClient.getDashboard().getPanels().panel instanceof mchorse.bbs_mod.ui.film.UIFilmPanel filmPanel && filmPanel.recorder != null && (filmPanel.recorder.isExporting() || filmPanel.recorder.isRecording())))
            {
                info.cancel();
                return;
            }
        }
    }

    @Inject(method = "onKey", at = @At("TAIL"))
    public void onOnEndKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        BBSModClient.onEndKey(window, key, scancode, action, modifiers, info);
    }
}