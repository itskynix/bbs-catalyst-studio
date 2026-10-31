package mchorse.bbs_mod.utils;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import net.minecraft.entity.effect.StatusEffect;

/**
 * The status effect icon, which wants the effect's registry entry since 1.20.5.
 */
public class EffectSprites
{
    public static Sprite get(StatusEffectSpriteManager manager, StatusEffect effect)
    {
        //? if >=1.21 {
        /*return manager.getSprite(EffectCompat.entry(effect));
        *///?} else {
        return manager.getSprite(effect);
        //?}
    }
}
