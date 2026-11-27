package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The client world's own time of day, under anything drawn over it; what Recap records. */
@Mixin(ClientWorld.Properties.class)
public interface RecapClientTimeAccessor
{
    @Accessor("timeOfDay")
    long bbs$getTimeOfDay();
}
