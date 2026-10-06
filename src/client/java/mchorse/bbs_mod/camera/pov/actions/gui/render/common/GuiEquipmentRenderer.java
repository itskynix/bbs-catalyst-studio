/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.SmithingTemplateItem
 *  net.minecraft.screen.PlayerScreenHandler
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SmithingTemplateItem;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

public final class GuiEquipmentRenderer {
    private static final Identifier HORSE_SADDLE_SLOT = new Identifier("container/horse/saddle_slot");
    private static final Identifier HORSE_ARMOR_SLOT = new Identifier("container/horse/armor_slot");
    private static final Identifier HORSE_CHEST_SLOTS = new Identifier("container/horse/chest_slots");
    private static final List<Identifier> SMITHING_TEMPLATE_PLACEHOLDERS = List.of(new Identifier("item/empty_slot_smithing_template_armor_trim"), new Identifier("item/empty_slot_smithing_template_netherite_upgrade"));

    private GuiEquipmentRenderer() {
    }

    public static void renderEmptyEquipmentSlots(Batcher2D batcher, GuiPovActionClip clip, String guiId, float tick) {
        int[][] nArrayArray;
        String[] slotIds = new String[]{"armor_head", "armor_chest", "armor_legs", "armor_feet", "offhand"};
        Identifier[] sprites = new Identifier[]{PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_OFFHAND_ARMOR_SLOT};
        if ("creative_inventory".equals(guiId)) {
            int[][] nArrayArray2 = new int[5][];
            nArrayArray2[0] = new int[]{54, 6};
            nArrayArray2[1] = new int[]{54, 33};
            nArrayArray2[2] = new int[]{108, 6};
            nArrayArray2[3] = new int[]{108, 33};
            nArrayArray = nArrayArray2;
            nArrayArray2[4] = new int[]{35, 20};
        } else {
            int[][] nArrayArray3 = new int[5][];
            nArrayArray3[0] = new int[]{8, 8};
            nArrayArray3[1] = new int[]{8, 26};
            nArrayArray3[2] = new int[]{8, 44};
            nArrayArray3[3] = new int[]{8, 62};
            nArrayArray = nArrayArray3;
            nArrayArray3[4] = new int[]{77, 62};
        }
        int[][] positions = nArrayArray;
        for (int i = 0; i < slotIds.length; ++i) {
            ItemStack stack;
            KeyframeChannel<ItemStack> channel = clip.getGuiSlot(guiId, slotIds[i]);
            ItemStack itemStack = stack = channel == null || channel.isEmpty() ? ItemStack.EMPTY : (ItemStack)channel.interpolate(tick, ItemStack.EMPTY);
            if (stack != null && !stack.isEmpty()) continue;
            Sprite sprite = (Sprite)MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(sprites[i]);
            batcher.getContext().drawSprite(positions[i][0], positions[i][1], 0, 16, 16, sprite);
        }
    }

    public static void renderEmptySlotPlaceholders(Batcher2D batcher, GuiPovActionClip clip, String guiId, float tick, float opacity) {
        if ("inventory".equals(guiId)) {
            GuiEquipmentRenderer.renderEmptyEquipmentSlots(batcher, clip, guiId, tick);
        } else if ("loom".equals(guiId)) {
            GuiSlotRenderer.drawGuiSlotPlaceholder(batcher, clip, guiId, "banner", tick, "container/loom/banner_slot", 13, 26, 16);
            GuiSlotRenderer.drawGuiSlotPlaceholder(batcher, clip, guiId, "dye", tick, "container/loom/dye_slot", 33, 26, 16);
            GuiSlotRenderer.drawGuiSlotPlaceholder(batcher, clip, guiId, "pattern", tick, "container/loom/pattern_slot", 23, 45, 16);
        } else if ("smithing_table".equals(guiId)) {
            Item item;
            ItemStack templateStack;
            GuiSlotRenderer.drawCyclingSlotPlaceholder(batcher, clip, guiId, "template", tick, SMITHING_TEMPLATE_PLACEHOLDERS, 8, 48, opacity);
            KeyframeChannel<ItemStack> templateChannel = clip.getGuiSlot(guiId, "template");
            ItemStack itemStack = templateStack = templateChannel == null || templateChannel.isEmpty() ? ItemStack.EMPTY : (ItemStack)templateChannel.interpolate(tick, ItemStack.EMPTY);
            if (templateStack != null && (item = templateStack.getItem()) instanceof SmithingTemplateItem) {
                SmithingTemplateItem template = (SmithingTemplateItem)item;
                GuiSlotRenderer.drawCyclingSlotPlaceholder(batcher, clip, guiId, "base", tick, template.getEmptyBaseSlotTextures(), 26, 48, opacity);
                GuiSlotRenderer.drawCyclingSlotPlaceholder(batcher, clip, guiId, "addition", tick, template.getEmptyAdditionsSlotTextures(), 44, 48, opacity);
            }
        } else if ("enchanting_table".equals(guiId) && GuiSlotRenderer.isSlotEmpty(clip, guiId, "lapis", tick)) {
            GuiSlotRenderer.drawBlockAtlasPlaceholder(batcher, new Identifier("item/empty_slot_lapis_lazuli"), 35, 47, opacity);
        } else if ("horse".equals(guiId)) {
            batcher.getContext().drawGuiTexture(HORSE_SADDLE_SLOT, 7, 17, 18, 18);
            batcher.getContext().drawGuiTexture(HORSE_ARMOR_SLOT, 7, 35, 18, 18);
        } else if ("donkey".equals(guiId)) {
            batcher.getContext().drawGuiTexture(HORSE_SADDLE_SLOT, 7, 17, 18, 18);
            if (clip.isMountChestOpen(guiId, tick)) {
                batcher.getContext().drawGuiTexture(HORSE_CHEST_SLOTS, 79, 17, 90, 54);
            }
        }
    }
}

