package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The items the first person hands hold and how far they are raised, lent the recorded ones while Recap draws its hands. */
@Mixin(HeldItemRenderer.class)
public interface RecapHeldItemAccessor
{
    @Accessor("mainHand")
    ItemStack bbs$getMainHand();

    @Accessor("mainHand")
    void bbs$setMainHand(ItemStack stack);

    @Accessor("offHand")
    ItemStack bbs$getOffHand();

    @Accessor("offHand")
    void bbs$setOffHand(ItemStack stack);

    @Accessor("equipProgressMainHand")
    float bbs$getEquipMain();

    @Accessor("equipProgressMainHand")
    void bbs$setEquipMain(float value);

    @Accessor("prevEquipProgressMainHand")
    float bbs$getPrevEquipMain();

    @Accessor("prevEquipProgressMainHand")
    void bbs$setPrevEquipMain(float value);

    @Accessor("equipProgressOffHand")
    float bbs$getEquipOff();

    @Accessor("equipProgressOffHand")
    void bbs$setEquipOff(float value);

    @Accessor("prevEquipProgressOffHand")
    float bbs$getPrevEquipOff();

    @Accessor("prevEquipProgressOffHand")
    void bbs$setPrevEquipOff(float value);
}
