package mchorse.bbs_mod.mixin.client;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.toast.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The advancement an advancement toast is of, for a live take's toast clips */
@Mixin(AdvancementToast.class)
public interface PovAdvancementToastAccessor
{
    @Accessor("advancement")
    AdvancementEntry bbs$getAdvancement();
}
