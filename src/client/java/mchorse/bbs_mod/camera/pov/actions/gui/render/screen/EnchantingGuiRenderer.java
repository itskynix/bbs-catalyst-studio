/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.screen.ingame.EnchantingPhrases
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.render.OverlayTexture
 *  net.minecraft.client.render.VertexConsumer
 *  net.minecraft.client.render.entity.model.BookModel
 *  net.minecraft.client.render.entity.model.EntityModelLayers
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.enchantment.Enchantment
 *  net.minecraft.item.ItemStack
 *  net.minecraft.screen.ScreenTexts
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.math.RotationAxis
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.data.EnchantmentSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.EnchantingPhrases;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BookModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class EnchantingGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final EnchantingGuiRenderer INSTANCE = new EnchantingGuiRenderer();
    private static final Identifier ENCHANTING_BOOK_TEXTURE = new Identifier("textures/entity/enchanting_table_book.png");
    private static final Identifier ENCHANTMENT_SLOT = new Identifier("container/enchanting_table/enchantment_slot");
    private static final Identifier ENCHANTMENT_SLOT_HIGHLIGHTED = new Identifier("container/enchanting_table/enchantment_slot_highlighted");
    private static final Identifier ENCHANTMENT_SLOT_DISABLED = new Identifier("container/enchanting_table/enchantment_slot_disabled");
    private static final Identifier[] ENCHANTMENT_LEVELS = new Identifier[]{new Identifier("container/enchanting_table/level_1"), new Identifier("container/enchanting_table/level_2"), new Identifier("container/enchanting_table/level_3")};
    private static final Identifier[] ENCHANTMENT_LEVELS_DISABLED = new Identifier[]{new Identifier("container/enchanting_table/level_1_disabled"), new Identifier("container/enchanting_table/level_2_disabled"), new Identifier("container/enchanting_table/level_3_disabled")};
    private static BookModel enchantingBook;

    private EnchantingGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        EnchantingGuiRenderer.drawBook(ctx.batcher, ctx.clip, ctx.localTick, ctx.opacity);
        EnchantingGuiRenderer.drawOffers(ctx.batcher, ctx.clip, ctx.localTick, cursorX, cursorY, ctx.hover);
    }

    private static void drawBook(Batcher2D batcher, GuiPovActionClip clip, float tick, float opacity) {
        if (enchantingBook == null) {
            enchantingBook = new BookModel(MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.BOOK));
        }
        float open = EnchantingGuiRenderer.enchantingBookOpen(clip, tick);
        batcher.getContext().draw();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        DiffuseLighting.method_34742();
        MatrixStack matrices = batcher.getContext().getMatrices();
        matrices.push();
        matrices.translate(33.0f, 31.0f, 100.0f);
        matrices.scale(-40.0f, 40.0f, 40.0f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25.0f));
        float closed = 1.0f - open;
        matrices.translate(closed * 0.2f, closed * 0.1f, closed * 0.25f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-closed * 90.0f - 90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
        float page = open > 0.0f ? tick * 0.02f : 0.0f;
        float leftPage = MathHelper.clamp((float)(MathHelper.fractionalPart((float)(page + 0.25f)) * 1.6f - 0.3f), (float)0.0f, (float)1.0f);
        float rightPage = MathHelper.clamp((float)(MathHelper.fractionalPart((float)(page + 0.75f)) * 1.6f - 0.3f), (float)0.0f, (float)1.0f);
        enchantingBook.setPageAngles(0.0f, leftPage, rightPage, open);
        VertexConsumer vertexConsumer = batcher.getContext().getVertexConsumers().getBuffer(enchantingBook.getLayer(ENCHANTING_BOOK_TEXTURE));
        enchantingBook.render(matrices, vertexConsumer, 0xF000F0, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, opacity);
        batcher.getContext().draw();
        matrices.pop();
        RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
    }

    private static float enchantingBookOpen(GuiPovActionClip clip, float tick) {
        int[][] offers;
        if (clip.enchantBookOpen != null && !clip.enchantBookOpen.isEmpty()) {
            return MathHelper.clamp((float)((Float)clip.enchantBookOpen.interpolate(tick, Float.valueOf(0.0f))).floatValue(), (float)0.0f, (float)1.0f);
        }
        for (int[] offer : offers = EnchantmentSnapshot.parse(GuiTextRenderer.sampleString(clip.enchantOffers, tick, ""))) {
            if (offer[0] <= 0) continue;
            return 1.0f;
        }
        if (!clip.enchantOffers.isEmpty()) {
            return 0.0f;
        }
        ItemStack item = GuiSlotRenderer.sampleSlot(clip, "enchanting_table", "item", tick);
        return item.isEmpty() || !item.isEnchantable() ? 0.0f : 1.0f;
    }

    private static void drawOffers(Batcher2D batcher, GuiPovActionClip clip, float tick, float cursorX, float cursorY, GuiPointerHover hover) {
        int[][] offers = EnchantmentSnapshot.parse(GuiTextRenderer.sampleString(clip.enchantOffers, tick, ""));
        int seed = GuiTextRenderer.sampleInt(clip.enchantSeed, tick, 0);
        int playerLevel = GuiTextRenderer.sampleInt(clip.enchantPlayerLevel, tick, 0);
        boolean creative = GuiTextRenderer.sampleBool(clip.enchantCreative, tick, false);
        int lapis = GuiSlotRenderer.sampleSlot(clip, "enchanting_table", "lapis", tick).getCount();
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        EnchantingPhrases.getInstance().setSeed((long)seed);
        for (int i = 0; i < 3; ++i) {
            List<Text> lines;
            boolean tooltipBounds;
            boolean hovered;
            int power = offers[i][0];
            int rowX = 60;
            int rowY = 14 + 19 * i;
            if (power <= 0) {
                batcher.getContext().drawGuiTexture(ENCHANTMENT_SLOT_DISABLED, rowX, rowY, 108, 19);
                continue;
            }
            String cost = Integer.toString(power);
            int phraseWidth = 86 - font.getWidth(cost);
            StringVisitable phrase = EnchantingPhrases.getInstance().generatePhrase(font, phraseWidth);
            int color = 6839882;
            boolean affordable = creative || lapis >= i + 1 && playerLevel >= power;
            boolean bl = hovered = cursorX >= (float)rowX && cursorY >= (float)rowY && cursorX < (float)(rowX + 108) && cursorY < (float)(rowY + 19);
            if (!affordable) {
                batcher.getContext().drawGuiTexture(ENCHANTMENT_SLOT_DISABLED, rowX, rowY, 108, 19);
                batcher.getContext().drawGuiTexture(ENCHANTMENT_LEVELS_DISABLED[i], rowX + 1, rowY + 1, 16, 16);
                batcher.getContext().drawTextWrapped(font, phrase, rowX + 20, rowY + 2, phraseWidth, (color & 0xFEFEFE) >> 1);
                color = 4226832;
            } else {
                batcher.getContext().drawGuiTexture(hovered ? ENCHANTMENT_SLOT_HIGHLIGHTED : ENCHANTMENT_SLOT, rowX, rowY, 108, 19);
                if (hovered) {
                    color = 0xFFFF80;
                }
                batcher.getContext().drawGuiTexture(ENCHANTMENT_LEVELS[i], rowX + 1, rowY + 1, 16, 16);
                batcher.getContext().drawTextWrapped(font, phrase, rowX + 20, rowY + 2, phraseWidth, color);
                color = hovered ? 0xFFFF80 : 8453920;
            }
            batcher.getContext().drawTextWithShadow(font, cost, rowX + 20 + 86 - font.getWidth(cost), rowY + 2, color);
            boolean bl2 = tooltipBounds = cursorX >= (float)rowX && cursorY >= (float)rowY && cursorX < (float)(rowX + 108) && cursorY < (float)(rowY + 17);
            if (!tooltipBounds || (lines = EnchantingGuiRenderer.enchantmentClueTooltip(offers[i][1], offers[i][2], power, i + 1, lapis, playerLevel, creative)).isEmpty()) continue;
            hover.lines = lines;
        }
    }

    private static List<Text> enchantmentClueTooltip(int enchantmentId, int enchantmentLevel, int power, int lapisCost, int lapisCount, int playerLevel, boolean creative) {
        Enchantment enchantment = Enchantment.byRawId((int)enchantmentId);
        if (enchantment == null || enchantmentLevel < 0 || power <= 0) {
            return List.of();
        }
        ArrayList<Text> lines = new ArrayList<Text>();
        lines.add((Text)Text.translatable((String)"container.enchant.clue", (Object[])new Object[]{enchantment.getName(enchantmentLevel)}).formatted(Formatting.WHITE));
        if (creative) {
            return lines;
        }
        lines.add(ScreenTexts.EMPTY);
        if (playerLevel < power) {
            lines.add((Text)Text.translatable((String)"container.enchant.level.requirement", (Object[])new Object[]{power}).formatted(Formatting.RED));
            return lines;
        }
        lines.add((Text)(lapisCost == 1 ? Text.translatable((String)"container.enchant.lapis.one") : Text.translatable((String)"container.enchant.lapis.many", (Object[])new Object[]{lapisCost})).formatted(lapisCount >= lapisCost ? Formatting.GRAY : Formatting.RED));
        lines.add((Text)(lapisCost == 1 ? Text.translatable((String)"container.enchant.level.one") : Text.translatable((String)"container.enchant.level.many", (Object[])new Object[]{lapisCost})).formatted(Formatting.GRAY));
        return lines;
    }
}

