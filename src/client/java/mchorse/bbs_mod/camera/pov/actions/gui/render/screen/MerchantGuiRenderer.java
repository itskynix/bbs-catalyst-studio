/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.data.MerchantSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class MerchantGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final MerchantGuiRenderer INSTANCE = new MerchantGuiRenderer();
    private static final Identifier EXP_BAR_BG = new Identifier("container/villager/experience_bar_background");
    private static final Identifier EXP_BAR_CURRENT = new Identifier("container/villager/experience_bar_current");
    private static final Identifier TRADE_ARROW = new Identifier("container/villager/trade_arrow");
    private static final Identifier TRADE_ARROW_OUT_OF_STOCK = new Identifier("container/villager/trade_arrow_out_of_stock");
    private static final Identifier DISCOUNT_STRIKETHROUGH = new Identifier("container/villager/discount_strikethrough");
    private static final Identifier SCROLLER = new Identifier("container/villager/scroller");
    private static final Identifier SCROLLER_DISABLED = new Identifier("container/villager/scroller_disabled");
    private static final Identifier BUTTON = new Identifier("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED = new Identifier("widget/button_highlighted");

    private MerchantGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        MerchantGuiRenderer.drawOffers(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick, cursorX, cursorY, ctx.hover);
    }

    private static void drawOffers(Batcher2D batcher, GuiPovActionClip clip, String guiId, float tick, float cursorX, float cursorY, GuiPointerHover pointerHover) {
        int offerIdx;
        KeyframeChannel<Integer> scrollChan;
        KeyframeChannel<String> offersChan;
        String offersStr;
        List<MerchantSnapshot.ParsedOffer> offers;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.textRenderer;
        MutableText tradesText = Text.translatable((String)"merchant.trades");
        int tradesWidth = textRenderer.getWidth((StringVisitable)tradesText);
        batcher.text(tradesText.getString(), (float)(49 - tradesWidth / 2), 6.0f, 0x404040, false);
        KeyframeChannel<String> titleChan = clip.getMerchantTitle(guiId);
        String customTitle = titleChan != null && !titleChan.isEmpty() ? (String)titleChan.interpolate(tick, "") : "";
        KeyframeChannel<Integer> profChan = clip.getMerchantProfession(guiId);
        int profession = profChan != null && !profChan.isEmpty() ? (Integer)profChan.interpolate(tick, 1) : 1;
        KeyframeChannel<Integer> lvlChan = clip.getMerchantLevel(guiId);
        int level = lvlChan != null && !lvlChan.isEmpty() ? (Integer)lvlChan.interpolate(tick, 1) : 1;
        KeyframeChannel<Boolean> canLvlChan = clip.getMerchantCanLevel(guiId);
        boolean canLevel = canLvlChan == null || canLvlChan.isEmpty() || (Boolean)canLvlChan.interpolate(tick, true) != false;
        Text displayTitleText = MerchantSnapshot.getTitleText(customTitle, profession, level, canLevel || level >= 1);
        int titleWidth = textRenderer.getWidth((StringVisitable)displayTitleText);
        int titleX = 136 + (102 - titleWidth) / 2;
        batcher.text(displayTitleText.getString(), (float)titleX, 6.0f, 0x404040, false);
        if (canLevel && level < 5) {
            batcher.flush();
            batcher.getContext().drawGuiTexture(EXP_BAR_BG, 136, 16, 102, 5);
            KeyframeChannel<Integer> xpChan = clip.getMerchantExperience(guiId);
            int xp = xpChan != null && !xpChan.isEmpty() ? (Integer)xpChan.interpolate(tick, 0) : 0;
            int minXp = 0;
            int maxXp = 10;
            if (level == 2) {
                minXp = 10;
                maxXp = 70;
            } else if (level == 3) {
                minXp = 70;
                maxXp = 150;
            } else if (level == 4) {
                minXp = 150;
                maxXp = 250;
            }
            float progress = MathHelper.clamp((float)((float)(xp - minXp) / (float)(maxXp - minXp)), (float)0.0f, (float)1.0f);
            int fillWidth = Math.round(102.0f * progress);
            if (fillWidth > 0) {
                batcher.getContext().drawGuiTexture(EXP_BAR_CURRENT, 102, 5, 0, 0, 136, 16, fillWidth, 5);
            }
        }
        if ((offers = MerchantSnapshot.parse(offersStr = (offersChan = clip.getMerchantOffers(guiId)) != null && !offersChan.isEmpty() ? (String)offersChan.interpolate(tick, "") : "")).isEmpty()) {
            offers = MerchantGuiRenderer.getDefaultOffers(profession);
        }
        int scrollOffset = (scrollChan = clip.getMerchantScrollOffset(guiId)) != null && !scrollChan.isEmpty() ? (Integer)scrollChan.interpolate(tick, 0) : 0;
        int maxScroll = Math.max(0, offers.size() - 7);
        scrollOffset = MathHelper.clamp((int)scrollOffset, (int)0, (int)maxScroll);
        KeyframeChannel<Integer> selChan = clip.getMerchantSelectedOffer(guiId);
        int selectedIndex = selChan != null && !selChan.isEmpty() ? (Integer)selChan.interpolate(tick, 0) : 0;
        for (int i = 0; i < 7 && (offerIdx = scrollOffset + i) < offers.size(); ++i) {
            boolean isSellHover;
            ItemStack sell;
            ItemStack buy2;
            MerchantSnapshot.ParsedOffer offer = offers.get(offerIdx);
            int rowX = 5;
            int rowY = 16 + i * 20;
            boolean isRowHover = cursorX >= (float)rowX && cursorX <= (float)(rowX + 88) && cursorY >= (float)rowY && cursorY <= (float)(rowY + 20);
            boolean isSelected = offerIdx == selectedIndex;
            batcher.getContext().drawGuiTexture(isRowHover ? BUTTON_HIGHLIGHTED : BUTTON, rowX, rowY, 88, 20);
            if (isSelected) {
                batcher.box((float)rowX, (float)rowY, (float)(rowX + 88), (float)(rowY + 1), -1);
                batcher.box((float)rowX, (float)(rowY + 19), (float)(rowX + 88), (float)(rowY + 20), -1);
                batcher.box((float)rowX, (float)rowY, (float)(rowX + 1), (float)(rowY + 20), -1);
                batcher.box((float)(rowX + 87), (float)rowY, (float)(rowX + 88), (float)(rowY + 20), -1);
            }
            if (offer.disabled()) {
                batcher.getContext().drawGuiTexture(TRADE_ARROW_OUT_OF_STOCK, rowX + 50, rowY + 3, 10, 9);
            } else {
                batcher.getContext().drawGuiTexture(TRADE_ARROW, rowX + 50, rowY + 3, 10, 9);
            }
            ItemStack buy1 = offer.buy1();
            if (buy1 != null && !buy1.isEmpty()) {
                boolean isBuy1Hover;
                GuiSlotRenderer.drawSlotItem(batcher, buy1, rowX + 5, rowY + 1);
                boolean bl = isBuy1Hover = cursorX >= (float)(rowX + 5) && cursorX <= (float)(rowX + 21) && cursorY >= (float)(rowY + 1) && cursorY <= (float)(rowY + 17);
                if (isBuy1Hover) {
                    pointerHover.item = buy1;
                    pointerHover.itemGroup = false;
                }
                if (offer.specialPrice() < 0) {
                    int baseCount = buy1.getCount();
                    String baseText = String.valueOf(baseCount);
                    int baseWidth = textRenderer.getWidth(baseText);
                    int countX = rowX + 5 + 16 - baseWidth;
                    int countY = rowY + 1 + 9;
                    batcher.getContext().drawGuiTexture(DISCOUNT_STRIKETHROUGH, countX - 1, countY + 4, baseWidth + 2, 2);
                }
            }
            if ((buy2 = offer.buy2()) != null && !buy2.isEmpty()) {
                boolean isBuy2Hover;
                GuiSlotRenderer.drawSlotItem(batcher, buy2, rowX + 35, rowY + 1);
                boolean bl = isBuy2Hover = cursorX >= (float)(rowX + 35) && cursorX <= (float)(rowX + 51) && cursorY >= (float)(rowY + 1) && cursorY <= (float)(rowY + 17);
                if (isBuy2Hover) {
                    pointerHover.item = buy2;
                    pointerHover.itemGroup = false;
                }
            }
            if ((sell = offer.sell()) == null || sell.isEmpty()) continue;
            GuiSlotRenderer.drawSlotItem(batcher, sell, rowX + 68, rowY + 1);
            boolean bl = isSellHover = cursorX >= (float)(rowX + 68) && cursorX <= (float)(rowX + 84) && cursorY >= (float)(rowY + 1) && cursorY <= (float)(rowY + 17);
            if (!isSellHover) continue;
            pointerHover.item = sell;
            pointerHover.itemGroup = false;
        }
        if (offers.size() <= 7) {
            batcher.getContext().drawGuiTexture(SCROLLER_DISABLED, 94, 18, 6, 27);
        } else {
            int thumbY = 18 + Math.round(112.0f * ((float)scrollOffset / (float)maxScroll));
            batcher.getContext().drawGuiTexture(SCROLLER, 94, thumbY, 6, 27);
        }
    }

    private static List<MerchantSnapshot.ParsedOffer> getDefaultOffers(int profession) {
        ArrayList<MerchantSnapshot.ParsedOffer> list = new ArrayList<MerchantSnapshot.ParsedOffer>();
        switch (profession) {
            case 1: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.COAL, 15), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 5), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_HELMET, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 4), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_BOOTS, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 7), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_LEGGINGS, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 9), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_CHESTPLATE, 1), 0, 12, 0, false));
                break;
            }
            case 2: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.CHICKEN, 14), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.COOKED_CHICKEN, 5), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.PORKCHOP, 15), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.COOKED_PORKCHOP, 5), 0, 16, 0, false));
                break;
            }
            case 3: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.PAPER, 24), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 7), new ItemStack((ItemConvertible)Items.COMPASS, 1), new ItemStack((ItemConvertible)Items.MAP, 1), 0, 12, 0, false));
                break;
            }
            case 4: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.ROTTEN_FLESH, 32), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.REDSTONE, 2), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.LAPIS_LAZULI, 1), 0, 12, 0, false));
                break;
            }
            case 5: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.WHEAT, 20), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.POTATO, 26), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.CARROT, 22), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.BREAD, 6), 0, 16, 0, false));
                break;
            }
            case 6: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.COD, 20), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), new ItemStack((ItemConvertible)Items.COD, 1), new ItemStack((ItemConvertible)Items.COOKED_COD, 1), 0, 16, 0, false));
                break;
            }
            case 7: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.STICK, 32), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.ARROW, 16), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), new ItemStack((ItemConvertible)Items.GRAVEL, 10), new ItemStack((ItemConvertible)Items.FLINT, 10), 0, 12, 0, false));
                break;
            }
            case 8: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.LEATHER, 6), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 7), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.LEATHER_LEGGINGS, 1), 0, 12, 0, false));
                break;
            }
            case 9: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.PAPER, 24), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 9), new ItemStack((ItemConvertible)Items.BOOK, 1), new ItemStack((ItemConvertible)Items.ENCHANTED_BOOK, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 9), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.BOOKSHELF, 1), 0, 12, 0, false));
                break;
            }
            case 10: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.CLAY_BALL, 10), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.BRICK, 10), 0, 16, 0, false));
                break;
            }
            case 12: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.WHITE_WOOL, 18), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.SHEARS, 1), 0, 12, 0, false));
                break;
            }
            case 13: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.COAL, 15), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.STONE_AXE, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.STONE_PICKAXE, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 1), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.STONE_SHOVEL, 1), 0, 12, 0, false));
                break;
            }
            case 14: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.COAL, 15), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 3), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_AXE, 1), 0, 12, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 2), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_SWORD, 1), 0, 12, 0, false));
                break;
            }
            default: {
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.COAL, 15), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.EMERALD, 1), 0, 16, 0, false));
                list.add(new MerchantSnapshot.ParsedOffer(new ItemStack((ItemConvertible)Items.EMERALD, 4), ItemStack.EMPTY, new ItemStack((ItemConvertible)Items.IRON_BOOTS, 1), 0, 12, 0, false));
            }
        }
        return list;
    }
}

