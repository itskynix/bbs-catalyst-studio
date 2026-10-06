/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.block.PaneBlock
 *  net.minecraft.item.BlockItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.block.PaneBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public final class CartographyGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final CartographyGuiRenderer INSTANCE = new CartographyGuiRenderer();
    private static final Identifier SCALED_MAP_TEXTURE = new Identifier("container/cartography_table/scaled_map");
    private static final Identifier DUPLICATED_MAP_TEXTURE = new Identifier("container/cartography_table/duplicated_map");
    private static final Identifier MAP_TEXTURE = new Identifier("container/cartography_table/map");
    private static final Identifier LOCKED_TEXTURE = new Identifier("container/cartography_table/locked");

    private CartographyGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        CartographyGuiRenderer.drawChrome(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick);
    }

    private static void drawChrome(Batcher2D batcher, GuiPovActionClip clip, String guiId, float tick) {
        BlockItem bi;
        Item item;
        boolean isGlassPane;
        KeyframeChannel<ItemStack> additionChannel = clip.getGuiSlot(guiId, "addition");
        ItemStack additionStack = additionChannel == null || additionChannel.isEmpty() ? ItemStack.EMPTY : (ItemStack)additionChannel.interpolate(tick, ItemStack.EMPTY);
        KeyframeChannel<ItemStack> mapChannel = clip.getGuiSlot(guiId, "map");
        ItemStack mapStack = mapChannel == null || mapChannel.isEmpty() ? ItemStack.EMPTY : (ItemStack)mapChannel.interpolate(tick, ItemStack.EMPTY);
        boolean isClone = additionStack != null && !additionStack.isEmpty() && (additionStack.isOf(Items.MAP) || additionStack.isOf(Items.FILLED_MAP) || additionStack.isOf(Items.PAPER));
        boolean bl = isGlassPane = additionStack != null && !additionStack.isEmpty() && (additionStack.isOf(Items.GLASS_PANE) || (item = additionStack.getItem()) instanceof BlockItem && (bi = (BlockItem)item).getBlock() instanceof PaneBlock);
        if (isClone) {
            batcher.getContext().drawGuiTexture(DUPLICATED_MAP_TEXTURE, 83, 13, 50, 66);
            batcher.getContext().drawGuiTexture(DUPLICATED_MAP_TEXTURE, 67, 29, 50, 66);
        } else if (isGlassPane) {
            batcher.getContext().drawGuiTexture(MAP_TEXTURE, 67, 13, 66, 66);
            batcher.getContext().drawGuiTexture(LOCKED_TEXTURE, 66, 12, 66, 66);
        } else {
            batcher.getContext().drawGuiTexture(MAP_TEXTURE, 67, 13, 66, 66);
        }
    }

    @Override
    public void drawLateItems(GuiRenderContext ctx) {
    }
}

