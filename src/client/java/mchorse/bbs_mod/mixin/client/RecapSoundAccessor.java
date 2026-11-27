package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.sound.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sound's loudness and pitch as asked, read before the game has picked which of its files plays. */
@Mixin(AbstractSoundInstance.class)
public interface RecapSoundAccessor
{
    @Accessor("volume")
    float bbs$getVolume();

    @Accessor("pitch")
    float bbs$getPitch();
}
