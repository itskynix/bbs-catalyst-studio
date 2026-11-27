package mchorse.bbs_mod.mixin.client;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How hard it rains and thunders as the world itself has it, under anything drawn over it; what Recap records. */
@Mixin(World.class)
public interface RecapWorldWeatherAccessor
{
    @Accessor("rainGradient")
    float bbs$getRainGradient();

    @Accessor("thunderGradient")
    float bbs$getThunderGradient();
}
