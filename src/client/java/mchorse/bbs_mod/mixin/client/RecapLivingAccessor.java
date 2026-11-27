package mchorse.bbs_mod.mixin.client;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The swimming lean, which Recap Studio records and lends back while it draws the recorded player. */
@Mixin(LivingEntity.class)
public interface RecapLivingAccessor
{
    @Accessor("leaningPitch")
    float bbs$getLeaningPitch();

    @Accessor("leaningPitch")
    void bbs$setLeaningPitch(float pitch);

    @Accessor("lastLeaningPitch")
    float bbs$getLastLeaningPitch();

    @Accessor("lastLeaningPitch")
    void bbs$setLastLeaningPitch(float pitch);
}
