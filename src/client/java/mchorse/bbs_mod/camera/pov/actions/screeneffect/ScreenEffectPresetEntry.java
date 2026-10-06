/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect;

import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ScreenEffectPresetEntry {
    public final String id;
    public final String name;
    public final String description;
    public final String iconItemId;
    public final float defaultIntensity;
    public final int vanillaRenderOrder;

    public ScreenEffectPresetEntry(String id, String name, String description, String iconItemId, float defaultIntensity, int vanillaRenderOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.iconItemId = iconItemId;
        this.defaultIntensity = defaultIntensity;
        this.vanillaRenderOrder = vanillaRenderOrder;
    }

    public ItemStack createIconStack() {
        if (this.iconItemId != null && !this.iconItemId.isEmpty()) {
            try {
                Item item = (Item)Registries.ITEM.get(new Identifier(this.iconItemId));
                if (item != null && item != Items.AIR) {
                    return new ItemStack((ItemConvertible)item);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return new ItemStack((ItemConvertible)Items.DIAMOND);
    }
}

