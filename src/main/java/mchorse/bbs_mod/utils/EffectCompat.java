package mchorse.bbs_mod.utils;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
//? if >=1.21 {
/*import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
*///?}

/**
 * Status effects are registry entries since 1.20.5; the rest of the code keeps passing the effect
 * itself around and uses these at the vanilla boundary.
 */
public class EffectCompat
{
    public static StatusEffect value(StatusEffect effect)
    {
        return effect;
    }

    //? if >=1.21 {
    /*public static StatusEffect value(RegistryEntry<StatusEffect> entry)
    {
        return entry == null ? null : entry.value();
    }

    public static RegistryEntry<StatusEffect> entry(StatusEffect effect)
    {
        return Registries.STATUS_EFFECT.getEntry(effect);
    }
    *///?}

    public static StatusEffect type(StatusEffectInstance instance)
    {
        //? if >=1.21 {
        /*return instance.getEffectType().value();
        *///?} else {
        return instance.getEffectType();
        //?}
    }
}
