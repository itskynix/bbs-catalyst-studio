/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.types.BaseType
 *  mchorse.bbs_mod.data.types.MapType
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.clip;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiRecipeBook;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import mchorse.bbs_mod.camera.pov.actions.gui.clip.GuiSlotChannels;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

public final class GuiClipMigration {
    private GuiClipMigration() {
    }

    public static void migrate(GuiPovActionClip clip, BaseType data) {
        if (data instanceof MapType) {
            MapType map = (MapType)data;
            if (map.has("scale") && clip.layout.isEmpty()) {
                float sc = map.getFloat("scale", 1.0f);
                Transform t = new Transform();
                t.scale.set(sc, sc, 1.0f);
                clip.layout.insert(0.0f, t);
            }
            if (map.has("opacity") && clip.opacity.isEmpty()) {
                clip.opacity.insert(0.0f, Float.valueOf(map.getFloat("opacity", 1.0f)));
            }
            if (map.has("darknessOpacity") && clip.darknessOpacity.isEmpty()) {
                clip.darknessOpacity.insert(0.0f, Float.valueOf(map.getFloat("darknessOpacity", 1.0f)));
            }
            String guiId = clip.state.isEmpty() ? "inventory" : (String)clip.state.get(0).getValue();
            GuiClipMigration.migrateLegacyCommon(clip, guiId);
            GuiClipMigration.migrateLegacySlots(clip, guiId);
        }
        for (GuiSlotSchema schema : GuiSlotSchema.getAll()) {
            if (!schema.hasCraftingSlots()) continue;
            GuiClipMigration.populateCraftingAnchor(clip, schema.guiId);
        }
    }

    private static void migrateLegacyCommon(GuiPovActionClip clip, String guiId) {
        GuiClipMigration.copyIfEmpty(clip.getLayout(guiId), clip.layout);
        GuiClipMigration.copyIfEmpty(clip.getOpacity(guiId), clip.opacity);
        GuiClipMigration.copyIfEmpty(clip.getDarknessOpacity(guiId), clip.darknessOpacity);
        GuiClipMigration.copyIfEmpty(clip.getCursorLayout(guiId), clip.cursorLayout);
        GuiClipMigration.copyIfEmpty(clip.getCursorVisible(guiId), clip.cursorVisible);
        GuiClipMigration.copyIfEmpty(clip.getCursorItem(guiId), clip.cursorItem);
        GuiClipMigration.copyIfEmpty(clip.getMouseButtons(guiId), clip.mouseButtons);
        GuiClipMigration.copyIfEmpty(clip.getMouseScroll(guiId), clip.mouseScroll);
        GuiClipMigration.migrateLegacyRecipe(clip, guiId);
    }

    private static void migrateLegacyRecipe(GuiPovActionClip clip, String guiId) {
        String target = GuiRecipeBook.supports(guiId) ? clip.resolveGuiId(guiId) : "inventory";
        GuiClipMigration.copyIfEmpty(clip.getRecipeOpen(target), clip.recipeOpen);
        GuiClipMigration.copyIfEmpty(clip.getRecipeSearch(target), clip.recipeSearch);
        GuiClipMigration.copyIfEmpty(clip.getRecipeSearchFocus(target), clip.recipeSearchFocus);
        GuiClipMigration.copyIfEmpty(clip.getRecipeShowing(target), clip.recipeShowing);
        GuiClipMigration.copyIfEmpty(clip.getRecipeCategory(target), clip.recipeCategory);
        GuiClipMigration.copyIfEmpty(clip.getRecipeSelected(target), clip.recipeSelected);
        GuiClipMigration.copyIfEmpty(clip.getRecipePage(target), clip.recipePage);
        GuiClipMigration.copyIfEmpty(clip.getRecipeButton(target), clip.recipeButton);
        GuiClipMigration.copyIfEmpty(clip.getRecipeSearchSelStart(target), clip.recipeSearchSelStart);
        GuiClipMigration.copyIfEmpty(clip.getRecipeSearchSelEnd(target), clip.recipeSearchSelEnd);
    }

    private static void migrateLegacySlots(GuiPovActionClip clip, String guiId) {
        GuiSlotSchema schema = GuiSlotSchema.get(guiId);
        for (GuiSlotSchema.Slot slot : schema.slots) {
            KeyframeChannel<ItemStack> legacy = GuiSlotChannels.legacySlot(clip, guiId, slot.id());
            GuiClipMigration.copyIfEmpty(clip.getGuiSlot(guiId, slot.id()), legacy);
        }
    }

    private static void populateCraftingAnchor(GuiPovActionClip clip, String guiId) {
        KeyframeChannel<Boolean> anchor = clip.guiCraftingSlotAnchors.get(guiId);
        if (anchor == null || !anchor.isEmpty()) {
            return;
        }
        for (KeyframeChannel<ItemStack> channel : clip.getCraftingSlots(guiId)) {
            for (Keyframe keyframe : channel.getKeyframes()) {
                anchor.insert(keyframe.getTick(), true);
            }
        }
    }

    public static <T> void copyIfEmpty(KeyframeChannel<T> target, KeyframeChannel<T> source) {
        if (target != null && target.isEmpty() && source != null && !source.isEmpty()) {
            target.copyOver(source, 0);
        }
    }
}

