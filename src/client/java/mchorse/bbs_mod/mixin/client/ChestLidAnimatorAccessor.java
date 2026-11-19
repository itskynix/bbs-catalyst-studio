package mchorse.bbs_mod.mixin.client;

import net.minecraft.block.entity.ChestLidAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChestLidAnimator.class)
public interface ChestLidAnimatorAccessor
{
    @Accessor("progress")
    void bbs$setProgress(float progress);

    @Accessor("lastProgress")
    void bbs$setLastProgress(float progress);
}
