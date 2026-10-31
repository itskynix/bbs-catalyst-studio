package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BeaconScreenPovAccess;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.entity.effect.StatusEffect;
//? if >=1.21
/*import net.minecraft.registry.entry.RegistryEntry;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BeaconScreen.class})
public interface BeaconScreenPovAccessor
extends BeaconScreenPovAccess {
    //? if >=1.21 {
    /*@Override
    @Accessor(value="primaryEffect")
    public RegistryEntry<StatusEffect> bbsPov$getPrimaryEffect();

    @Override
    @Accessor(value="secondaryEffect")
    public RegistryEntry<StatusEffect> bbsPov$getSecondaryEffect();
    *///?} else {
    @Override
    @Accessor(value="primaryEffect")
    public StatusEffect bbsPov$getPrimaryEffect();

    @Override
    @Accessor(value="secondaryEffect")
    public StatusEffect bbsPov$getSecondaryEffect();
    //?}
}
