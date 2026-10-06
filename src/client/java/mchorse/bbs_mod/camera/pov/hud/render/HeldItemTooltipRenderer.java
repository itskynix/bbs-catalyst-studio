/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.hud.InGameHud
 *  net.minecraft.client.option.SimpleOption
 *  net.minecraft.item.ItemStack
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.hud.render;

import mchorse.bbs_mod.camera.pov.hud.HudState;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.InGameHudHeldItemPovAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

public final class HeldItemTooltipRenderer {
    private HeldItemTooltipRenderer() {
    }

    public static void renderPlayback(Batcher2D batcher, ReplayKeyframes replay, HudState state, float tick, int width, int height) {
        if (state == null || replay == null || !state.visible) {
            return;
        }
        int slot = MathHelper.clamp((int)state.selectedSlot, (int)0, (int)8);
        ItemStack stack = state.items[slot];
        int fade = HeldItemTooltipRenderer.remainingFade(replay, tick, stack);
        HeldItemTooltipRenderer.render(batcher, stack, fade, state.statusBarsVisible, state.layout, width, height);
    }

    public static void renderLive(Batcher2D batcher, HudState state, int width, int height) {
        if (state == null || !state.visible) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        InGameHud hud = client.inGameHud;
        if (!(hud instanceof InGameHudHeldItemPovAccess)) {
            return;
        }
        InGameHudHeldItemPovAccess access = (InGameHudHeldItemPovAccess)hud;
        ItemStack stack = access.bbsPov$getHeldItemTooltipStack();
        if (stack == null || stack.isEmpty()) {
            int slot = MathHelper.clamp((int)state.selectedSlot, (int)0, (int)8);
            stack = state.items[slot];
        }
        HeldItemTooltipRenderer.render(batcher, stack, access.bbsPov$getHeldItemTooltipFade(), state.statusBarsVisible, state.layout, width, height);
    }

    public static void render(Batcher2D batcher, ItemStack stack, int fade, boolean statusBarsVisible, Transform layout, int width, int height) {
        int alpha;
        if (fade <= 0 || stack == null || stack.isEmpty()) {
            return;
        }
        MutableText name = Text.empty().append(stack.getName()).formatted(stack.getRarity().formatting);
        if (stack.hasCustomName()) {
            name = name.formatted(Formatting.ITALIC);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer texts = client.textRenderer;
        int textWidth = texts.getWidth((StringVisitable)name);
        float layoutX = layout == null ? 0.0f : layout.translate.x * 2.0f;
        float layoutY = layout == null ? 0.0f : layout.translate.y * 2.0f;
        int x = (width - textWidth) / 2 + Math.round(layoutX);
        int y = height - 59 - Math.round(layoutY);
        if (!statusBarsVisible) {
            y += 14;
        }
        if ((alpha = (int)((float)fade * 256.0f / 10.0f)) > 255) {
            alpha = 255;
        }
        if (alpha <= 0) {
            return;
        }
        DrawContext context = batcher.getContext();
        batcher.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int background = client.options.getTextBackgroundColor(0);
        context.fill(x - 2, y - 2, x + textWidth + 2, y + 9 + 2, background);
        context.drawTextWithShadow(texts, (Text)name, x, y, 0xFFFFFF + (alpha << 24));
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        batcher.flush();
    }

    private static int remainingFade(ReplayKeyframes replay, float tick, ItemStack now) {
        if (now == null || now.isEmpty()) {
            return 0;
        }
        double displayTime = 1.0;
        SimpleOption option = MinecraftClient.getInstance().options.getNotificationDisplayTime();
        if (option != null && option.getValue() != null) {
            displayTime = (Double)option.getValue();
        }
        int max = Math.max(1, (int)(40.0 * displayTime));
        for (int i = 1; i <= max + 1; ++i) {
            float thenTick = tick - (float)i;
            if (thenTick < 0.0f) {
                return 0;
            }
            int thenSlot = MathHelper.clamp((int)replay.getSelectedSlot(thenTick), (int)0, (int)8);
            ItemStack then = (ItemStack)((KeyframeChannel)replay.hotbar.get(thenSlot)).interpolate(thenTick, ItemStack.EMPTY);
            if (HeldItemTooltipRenderer.sameHeldName(then, now)) continue;
            return Math.max(0, max - i + 1);
        }
        return 0;
    }

    private static boolean sameHeldName(ItemStack a, ItemStack b) {
        boolean bEmpty;
        boolean aEmpty = a == null || a.isEmpty();
        boolean bl = bEmpty = b == null || b.isEmpty();
        if (aEmpty || bEmpty) {
            return aEmpty && bEmpty;
        }
        return a.isOf(b.getItem()) && a.getName().equals(b.getName());
    }
}

