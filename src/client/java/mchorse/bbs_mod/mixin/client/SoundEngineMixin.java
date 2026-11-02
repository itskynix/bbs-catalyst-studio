package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.renderers.AudioFormRenderer;
import net.minecraft.client.sound.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin
{
    @Inject(method = "close", at = @At("HEAD"))
    public void bbs$onSoundEngineClose(CallbackInfo info)
    {
        AudioFormRenderer.stopAll();

        if (BBSModClient.getSounds() != null)
        {
            BBSModClient.getSounds().deleteSounds();
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    public void bbs$onSoundEngineInit(CallbackInfo info)
    {
        AudioFormRenderer.stopAll();

        if (BBSModClient.getSounds() != null)
        {
            BBSModClient.getSounds().deleteSounds();
        }
    }
}

