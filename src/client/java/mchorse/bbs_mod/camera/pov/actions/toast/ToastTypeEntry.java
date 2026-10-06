/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.toast;

import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ToastTypeEntry {
    public final String id;
    public final String category;
    public final String title;
    public final String description;
    public final String titleKey;
    public final String descriptionKey;
    public final String iconItemId;
    public final String frameType;
    public final String textureId;

    public ToastTypeEntry(String id, String category, String title, String description, String titleKey, String descriptionKey, String iconItemId, String frameType, String textureId) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.description = description;
        this.titleKey = titleKey == null ? "" : titleKey;
        this.descriptionKey = descriptionKey == null ? "" : descriptionKey;
        this.iconItemId = iconItemId;
        this.frameType = frameType;
        this.textureId = textureId;
    }

    public ToastTypeEntry(String id, String category, String title, String description, String iconItemId, String frameType, String textureId) {
        this(id, category, title, description, "", "", iconItemId, frameType, textureId);
    }

    public Text getTitleText() {
        if (this.titleKey != null && !this.titleKey.isEmpty()) {
            return Text.translatable((String)this.titleKey);
        }
        return Text.literal((String)(this.title != null ? this.title : ""));
    }

    public Text getDescriptionText() {
        if (this.descriptionKey != null && !this.descriptionKey.isEmpty()) {
            return Text.translatable((String)this.descriptionKey);
        }
        return Text.literal((String)(this.description != null ? this.description : ""));
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

    public int getTitleColor() {
        if ("challenge".equalsIgnoreCase(this.frameType)) {
            return -43521;
        }
        if ("goal".equalsIgnoreCase(this.frameType)) {
            return -11141121;
        }
        if ("recipe".equalsIgnoreCase(this.frameType)) {
            return -11534256;
        }
        if ("system".equalsIgnoreCase(this.frameType)) {
            return -1;
        }
        return -171;
    }
}

