package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.renderers.AudioFormRenderer;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundSystemMixin
{
    @Inject(method = "reloadSounds", at = @At("HEAD"))
    public void bbs$onReloadSounds(CallbackInfo info)
    {
        AudioFormRenderer.stopAll();

        if (BBSModClient.getSounds() != null)
        {
            BBSModClient.getSounds().deleteSounds();
        }
    }
}

