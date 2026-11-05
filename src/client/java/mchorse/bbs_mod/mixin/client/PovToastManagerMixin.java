package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Recorder;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A toast that pops up during a live take goes into the take too, on the tick it popped up */
@Mixin(ToastManager.class)
public class PovToastManagerMixin
{
    @Inject(method = "add", at = @At("HEAD"))
    private void onAdd(Toast toast, CallbackInfo info)
    {
        Recorder recorder = BBSModClient.getFilms() == null ? null : BBSModClient.getFilms().getRecorder();

        if (recorder != null && !recorder.hasNotStarted() && recorder.tick >= 0)
        {
            recorder.pov.recordToast(toast, recorder.tick);
        }
    }
}
