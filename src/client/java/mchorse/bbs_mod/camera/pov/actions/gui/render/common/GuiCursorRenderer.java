/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.item.ItemGroup
 *  net.minecraft.item.ItemGroup$Type
 *  net.minecraft.item.ItemGroups
 *  net.minecraft.item.ItemStack
 *  net.minecraft.nbt.NbtCompound
 *  net.minecraft.text.MutableText
 *  net.minecraft.util.Formatting
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiItemRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

public final class GuiCursorRenderer {
    private GuiCursorRenderer() {
    }

    public static void renderTooltips(GuiRenderContext ctx, ItemStack hoveredStack) {
        if (!ctx.cursorVisible || ctx.cursorHasItem) {
            return;
        }
        Batcher2D batcher = ctx.batcher;
        GuiPointerHover pointerHover = ctx.hover;
        float curScreenX = ctx.curScreenX;
        float curScreenY = ctx.curScreenY;
        if (pointerHover.lines != null && !pointerHover.lines.isEmpty()) {
            try {
                batcher.getContext().drawTooltip(MinecraftClient.getInstance().textRenderer, pointerHover.lines, (int)curScreenX, (int)curScreenY);
                batcher.getContext().draw();
            }
            catch (Exception exception) {}
        } else if (pointerHover.widget != null) {
            try {
                batcher.getContext().drawTooltip(MinecraftClient.getInstance().textRenderer, pointerHover.widget, (int)curScreenX, (int)curScreenY);
                batcher.getContext().draw();
            }
            catch (Exception exception) {}
        } else if (hoveredStack != null && !hoveredStack.isEmpty()) {
            try {
                GuiItemRenderer.ensureItemGroupsPopulated();
                List<Text> tooltipLines = new ArrayList<Text>(Screen.getTooltipFromItem((MinecraftClient)MinecraftClient.getInstance(), (ItemStack)hoveredStack));
                if (pointerHover.itemGroup && !GuiCursorRenderer.hidesItemGroupLine(hoveredStack)) {
                    int insertIndex = Math.min(1, tooltipLines.size());
                    for (ItemGroup group : ItemGroups.getGroupsToDisplay()) {
                        if (group.getType() == ItemGroup.Type.SEARCH || group.getType() == ItemGroup.Type.HOTBAR || !group.contains(hoveredStack)) continue;
                        tooltipLines.add(insertIndex, group.getDisplayName().copy().formatted(Formatting.BLUE));
                        break;
                    }
                }
                batcher.getContext().drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipLines, (int)curScreenX, (int)curScreenY);
                batcher.getContext().draw();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private static boolean hidesItemGroupLine(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (stack.hasEnchantments()) {
            return true;
        }
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains("StoredEnchantments", 9) && !nbt.getList("StoredEnchantments", 10).isEmpty();
    }
}

