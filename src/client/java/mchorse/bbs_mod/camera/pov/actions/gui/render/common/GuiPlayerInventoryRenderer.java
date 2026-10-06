/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSlotDragPreview;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.LiveGuiPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import java.util.Set;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public final class GuiPlayerInventoryRenderer {
    private GuiPlayerInventoryRenderer() {
    }

    public static ItemStack render(Batcher2D batcher, ReplayKeyframes replayKeyframes, RecordedHudData hudData, GuiPovActionClip clip, String guiId, float localTick, float globalTick, float cursorGuiX, float cursorGuiY, int invX, int invY, Set<String> dragKeys, String encoded, ItemStack paint, GuiPointerHover hover) {
        ItemStack shown;
        boolean preview;
        ItemStack stack;
        boolean isHover;
        ItemStack hovered = null;
        boolean playerItemGroup = false;
        for (int i = 0; i < 27; ++i) {
            int sx = invX + i % 9 * 18;
            int sy = invY + i / 9 * 18;
            isHover = cursorGuiX >= (float)sx && cursorGuiX <= (float)(sx + 16) && cursorGuiY >= (float)sy && cursorGuiY <= (float)(sy + 16);
            stack = ItemStack.EMPTY;
            if (hudData != null && i < hudData.inventory.size()) {
                KeyframeChannel<ItemStack> ch = hudData.inventory.get(i);
                stack = ch != null && !ch.isEmpty() ? (ItemStack)ch.interpolate(globalTick, ItemStack.EMPTY) : ItemStack.EMPTY;
            } else if (LiveGuiPreviewRenderer.isRenderingLive() && MinecraftClient.getInstance().player != null) {
                stack = (ItemStack)MinecraftClient.getInstance().player.getInventory().main.get(9 + i);
            }
            preview = dragKeys.contains("inv_" + i);
            if (preview && paint != null && !paint.isEmpty()) {
                int existing = stack == null || stack.isEmpty() ? 0 : stack.getCount();
                stack = paint.copyWithCount(GuiSlotDragPreview.previewCount(GuiSlotDragPreview.decodeButton(encoded), GuiSlotDragPreview.decodeOriginalCount(encoded), dragKeys.size(), paint.getMaxCount(), existing));
            }
            shown = GuiSlotRenderer.drawSlotContents(batcher, stack, sx, sy, preview, isHover);
            if (!isHover || shown == null || shown.isEmpty()) continue;
            hovered = shown;
            hover.itemGroup = playerItemGroup;
        }
        int hotbarY = invY + 58;
        for (int i = 0; i < 9; ++i) {
            int sx = invX + i * 18;
            isHover = cursorGuiX >= (float)sx && cursorGuiX <= (float)(sx + 16) && cursorGuiY >= (float)hotbarY && cursorGuiY <= (float)(hotbarY + 16);
            stack = ItemStack.EMPTY;
            if (replayKeyframes != null) {
                KeyframeChannel ch = (KeyframeChannel)replayKeyframes.hotbar.get(i);
                stack = ch != null && !ch.isEmpty() ? (ItemStack)ch.interpolate(globalTick, ItemStack.EMPTY) : ItemStack.EMPTY;
            } else if (LiveGuiPreviewRenderer.isRenderingLive() && MinecraftClient.getInstance().player != null) {
                stack = MinecraftClient.getInstance().player.getInventory().getStack(i);
            }
            preview = dragKeys.contains("hotbar_" + i);
            if (preview && paint != null && !paint.isEmpty()) {
                int existing = stack == null || stack.isEmpty() ? 0 : stack.getCount();
                stack = paint.copyWithCount(GuiSlotDragPreview.previewCount(GuiSlotDragPreview.decodeButton(encoded), GuiSlotDragPreview.decodeOriginalCount(encoded), dragKeys.size(), paint.getMaxCount(), existing));
            }
            shown = GuiSlotRenderer.drawSlotContents(batcher, stack, sx, hotbarY, preview, isHover);
            if (!isHover || shown == null || shown.isEmpty()) continue;
            hovered = shown;
            hover.itemGroup = playerItemGroup;
        }
        return hovered;
    }
}

