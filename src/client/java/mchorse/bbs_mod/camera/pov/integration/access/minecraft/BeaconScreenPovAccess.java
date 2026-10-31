package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

//? if >=1.21 {
/*import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
*///?} else {
import net.minecraft.entity.effect.StatusEffect;
//?}

public interface BeaconScreenPovAccess {
    //? if >=1.21 {
    /*public RegistryEntry<StatusEffect> bbsPov$getPrimaryEffect();

    public RegistryEntry<StatusEffect> bbsPov$getSecondaryEffect();
    *///?} else {
    public StatusEffect bbsPov$getPrimaryEffect();

    public StatusEffect bbsPov$getSecondaryEffect();
    //?}
}
