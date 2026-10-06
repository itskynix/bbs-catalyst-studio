/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class GamemodeGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final GamemodeGuiRenderer INSTANCE = new GamemodeGuiRenderer();
    private static final Identifier GAMEMODE_SLOT_TEXTURE = new Identifier("gamemode_switcher/slot");
    private static final Identifier GAMEMODE_SELECTION_TEXTURE = new Identifier("gamemode_switcher/selection");
    private static final ItemStack GRASS_BLOCK_STACK = new ItemStack((ItemConvertible)Items.GRASS_BLOCK);
    private static final ItemStack IRON_SWORD_STACK = new ItemStack((ItemConvertible)Items.IRON_SWORD);
    private static final ItemStack MAP_STACK = new ItemStack((ItemConvertible)Items.MAP);
    private static final ItemStack ENDER_EYE_STACK = new ItemStack((ItemConvertible)Items.ENDER_EYE);

    private static void drawSwitcher(Batcher2D batcher, GuiPovActionClip clip, float tick, float originX, float originY, float scaleX, float scaleY) {
        MutableText title;
        int selectedMode = clip.gamemodeSelection.isEmpty() ? 0 : (Integer)clip.gamemodeSelection.interpolate(tick, 0);
        selectedMode = MathHelper.clamp((int)selectedMode, (int)0, (int)3);
        int highlightedSlotIndex = switch (selectedMode) {
            case 1 -> {
                title = Text.translatable((String)"gameMode.creative");
                yield 0;
            }
            case 2 -> {
                title = Text.translatable((String)"gameMode.adventure");
                yield 2;
            }
            case 3 -> {
                title = Text.translatable((String)"gameMode.spectator");
                yield 3;
            }
            default -> {
                title = Text.translatable((String)"gameMode.survival");
                yield 1;
            }
        };
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        DrawContext context = batcher.getContext();
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originX, originY, 0.0f);
        matrices.scale(scaleX, scaleY, 1.0f);
        context.drawCenteredTextWithShadow(textRenderer, (Text)title, 62, 7, 0xFFFFFF);
        ItemStack[] icons = new ItemStack[]{GRASS_BLOCK_STACK, IRON_SWORD_STACK, MAP_STACK, ENDER_EYE_STACK};
        for (int i = 0; i < 4; ++i) {
            int slotX = 3 + i * 31;
            int slotY = 27;
            boolean highlighted = i == highlightedSlotIndex;
            mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, GAMEMODE_SLOT_TEXTURE, slotX, slotY, 26, 26);
            if (highlighted) {
                mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, GAMEMODE_SELECTION_TEXTURE, slotX, slotY, 26, 26);
            }
            DiffuseLighting.enableGuiDepthLighting();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean)true);
            GuiSlotRenderer.drawSlotItem(batcher, icons[i], slotX + 5, slotY + 5);
            batcher.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask((boolean)false);
            DiffuseLighting.disableGuiDepthLighting();
        }
        MutableText keyText = Text.literal((String)"[ F4 ]").formatted(Formatting.AQUA);
        MutableText hintText = Text.translatable((String)"debug.gamemodes.select_next", (Object[])new Object[]{keyText});
        context.drawCenteredTextWithShadow(textRenderer, (Text)hintText, 62, 63, 0xFFFFFF);
        matrices.pop();
        batcher.flush();
    }

    private GamemodeGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawPreview(GuiRenderContext ctx) {
        GamemodeGuiRenderer.drawSwitcher(ctx.batcher, ctx.clip, ctx.localTick, ctx.originX, ctx.originY, ctx.scaleX, ctx.scaleY);
    }
}

