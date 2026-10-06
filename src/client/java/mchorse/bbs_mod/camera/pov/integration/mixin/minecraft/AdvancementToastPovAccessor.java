/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.advancement.AdvancementEntry
 *  net.minecraft.client.toast.AdvancementToast
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

//? if >=1.20.4 {
import net.minecraft.advancement.AdvancementEntry;
//?} else {
/*import net.minecraft.advancement.Advancement;
*///?}
import net.minecraft.client.toast.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={AdvancementToast.class})
public interface AdvancementToastPovAccessor {
    //? if >=1.20.4 {
    @Accessor(value="advancement")
    public AdvancementEntry bbsPov$getAdvancement();
    //?} else {
    /*@Accessor(value="advancement")
    public Advancement bbsPov$getAdvancement();
    *///?}
}

