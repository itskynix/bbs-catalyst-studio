package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.recap.RecapRecorder;
import mchorse.bbs_mod.recap.RecapScene;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Recap and the game's sounds: every sound of the world goes into a running recording and the
 * instant stretch, and while Recap Studio plays a recap with its own sounds the live world's are
 * held back, so what is heard is the recording.
 */
@Mixin(SoundManager.class)
public class RecapSoundMixin
{
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapSound(SoundInstance sound, CallbackInfo info)
    {
        bbs$recapHandle(sound, info);
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapDelayedSound(SoundInstance sound, int delay, CallbackInfo info)
    {
        bbs$recapHandle(sound, info);
    }

    private static void bbs$recapHandle(SoundInstance sound, CallbackInfo info)
    {
        try
        {
            float volume = 1F;
            float pitch = 1F;

            if (sound instanceof AbstractSoundInstance)
            {
                RecapSoundAccessor access = (RecapSoundAccessor) sound;

                volume = access.bbs$getVolume();
                pitch = access.bbs$getPitch();
            }

            RecapRecorder.onSound(sound, volume, pitch);

            if (RecapScene.mutesLiveSound(sound))
            {
                info.cancel();
            }
        }
        catch (RuntimeException e)
        {
            /* A sound is never lost to the recap's bookkeeping */
        }
    }
}
