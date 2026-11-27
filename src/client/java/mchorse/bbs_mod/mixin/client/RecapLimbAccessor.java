package mchorse.bbs_mod.mixin.client;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Recap Studio lends the recorded walk to the player's limbs while it draws the recorded player. */
@Mixin(LimbAnimator.class)
public interface RecapLimbAccessor
{
    @Accessor("prevSpeed")
    float bbs$getPrevSpeed();

    @Accessor("prevSpeed")
    void bbs$setPrevSpeed(float speed);

    @Accessor("speed")
    void bbs$setSpeed(float speed);

    @Accessor("pos")
    void bbs$setPos(float pos);
}
