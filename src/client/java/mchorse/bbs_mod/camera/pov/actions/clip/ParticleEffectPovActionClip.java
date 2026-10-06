/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.core.ValueString
 *  mchorse.bbs_mod.settings.values.numeric.ValueFloat
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.ViewParticlePovActionClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ParticleEffectPovActionClip
extends ViewParticlePovActionClip {
    public final ValueString particle = new ValueString("particle", "minecraft:poof");
    public final ValueString blockId = new ValueString("block", "minecraft:stone");
    public final ValueString itemId = new ValueString("item", "minecraft:apple");
    public final ValueFloat dustR = new ValueFloat("dust_r", Float.valueOf(1.0f), Float.valueOf(0.0f), Float.valueOf(1.0f));
    public final ValueFloat dustG = new ValueFloat("dust_g", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(1.0f));
    public final ValueFloat dustB = new ValueFloat("dust_b", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(1.0f));
    public final ValueFloat dustScale = new ValueFloat("dust_scale", Float.valueOf(1.0f), Float.valueOf(0.01f), Float.valueOf(4.0f));

    public ParticleEffectPovActionClip() {
        this.add((BaseValue)this.particle);
        this.add((BaseValue)this.blockId);
        this.add((BaseValue)this.itemId);
        this.add((BaseValue)this.dustR);
        this.add((BaseValue)this.dustG);
        this.add((BaseValue)this.dustB);
        this.add((BaseValue)this.dustScale);
    }

    public ItemStack extraItem() {
        return ParticleEffectPovActionClip.parseItem((String)this.itemId.get(), Items.APPLE);
    }

    static ItemStack parseItem(String id, Item fallback) {
        Item item;
        Identifier identifier = Identifier.tryParse((String)id);
        Item item2 = item = identifier == null ? fallback : (Item)Registries.ITEM.get(identifier);
        if (item == null || item == Items.AIR) {
            item = fallback != null && fallback != Items.AIR ? fallback : Items.APPLE;
        }
        return new ItemStack((ItemConvertible)item);
    }

    @Override
    public PovActionType getActionType() {
        return PovActionType.PARTICLE_EFFECT;
    }

    protected Clip create() {
        return new ParticleEffectPovActionClip();
    }
}

