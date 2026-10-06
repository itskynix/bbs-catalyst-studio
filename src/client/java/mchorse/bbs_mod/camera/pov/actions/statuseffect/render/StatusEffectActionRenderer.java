/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.client.texture.StatusEffectSpriteManager
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.statuseffect.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.StatusEffectsPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.statuseffect.StatusEffectEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.StatusEffectSpriteManager;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class StatusEffectActionRenderer {
    private static final Identifier EFFECT_BACKGROUND_TEXTURE = new Identifier("hud/effect_background");
    private static final Identifier EFFECT_BACKGROUND_LARGE_TEXTURE = new Identifier("container/inventory/effect_background_large");

    private StatusEffectActionRenderer() {
    }

    public static void renderHUD(Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height) {
        StatusEffectsPovActionClip clip;
        if (actions == null) {
            return;
        }
        GuiPovActionClip gui = actions.getActiveGui(tick);
        if (gui != null) {
            String state;
            String string = state = gui.state.isEmpty() ? "inventory" : (String)gui.state.interpolate(gui.getLocalTick(tick), "inventory");
            if ("inventory".equals(state) || "creative_inventory".equals(state)) {
                return;
            }
        }
        if ((clip = actions.getActiveStatusEffects(tick)) == null || clip.getEffects().isEmpty()) {
            return;
        }
        DrawContext context = batcher.getContext();
        if (context == null) {
            return;
        }
        float localTick = clip.getLocalTick(tick);
        StatusEffectActionRenderer.renderHudEffects(context, clip.getEffects(), localTick, width);
    }

    public static void renderLiveHUD(Batcher2D batcher, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
    }

    private static void renderHudEffects(DrawContext context, List<StatusEffectEntry> effects, float elapsedTicks, int screenWidth) {
        MinecraftClient client = MinecraftClient.getInstance();
        StatusEffectSpriteManager spriteManager = client.getStatusEffectSpriteManager();
        int x = screenWidth - 25;
        int y = 1;
        int count = 0;
        for (StatusEffectEntry entry : effects) {
            int remainingTicks;
            Sprite sprite;
            StatusEffect effect;
            if (entry.isExpired(elapsedTicks) || (effect = entry.getStatusEffect()) == null || (sprite = spriteManager.getSprite(effect)) == null) continue;
            float alpha = 1.0f;
            if (!entry.isUnlimited() && (remainingTicks = (int)((float)entry.getDurationSeconds() * 20.0f - elapsedTicks)) <= 200) {
                int m = Math.max(0, remainingTicks);
                int n = 10 - m / 20;
                alpha = MathHelper.clamp((float)((float)m / 10.0f / 20.0f * 0.5f), (float)0.0f, (float)0.5f) + MathHelper.cos((float)((float)m * (float)Math.PI / 5.0f)) * MathHelper.clamp((float)((float)n / 10.0f * 0.25f), (float)0.0f, (float)0.25f);
                alpha = MathHelper.clamp((float)alpha, (float)0.0f, (float)1.0f);
            }
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            try {
                mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, EFFECT_BACKGROUND_TEXTURE, x, y, 24, 24);
            }
            catch (Exception ignored) {
                context.fill(x, y, x + 24, y + 24, -2009910477);
            }
            RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)alpha);
            context.drawSprite(x + 3, y + 3, 0, 18, 18, sprite);
            RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            x -= 25;
            if (++count % 10 != 0) continue;
            x = screenWidth - 25;
            y += 26;
        }
    }

    public static void renderInventorySidebar(DrawContext context, RecordedPovActions actions, float tick, int guiLeft, int guiTop, int guiWidth, int guiHeight) {
        if (actions == null) {
            return;
        }
        StatusEffectsPovActionClip clip = actions.getActiveStatusEffects(tick);
        if (clip == null || clip.getEffects().isEmpty()) {
            return;
        }
        float localTick = clip.getLocalTick(tick);
        if (localTick < 0.0f) {
            return;
        }
        List<StatusEffectEntry> effects = clip.getEffects();
        MinecraftClient client = MinecraftClient.getInstance();
        StatusEffectSpriteManager spriteManager = client.getStatusEffectSpriteManager();
        TextRenderer textRenderer = client.textRenderer;
        int startX = guiLeft + guiWidth + 2;
        int startY = guiTop;
        int cardWidth = 120;
        int cardHeight = 32;
        for (StatusEffectEntry entry : effects) {
            StatusEffect effect;
            if (entry.isExpired(localTick) || (effect = entry.getStatusEffect()) == null) continue;
            try {
                mchorse.bbs_mod.camera.pov.utils.PovDrawHelper.drawGuiTexture(context, EFFECT_BACKGROUND_LARGE_TEXTURE, startX, startY, cardWidth, cardHeight);
            }
            catch (Exception ignored) {
                context.fill(startX, startY, startX + cardWidth, startY + cardHeight, -534897122);
                context.drawBorder(startX, startY, cardWidth, cardHeight, -13158601);
            }
            Sprite sprite = spriteManager.getSprite(effect);
            if (sprite != null) {
                RenderSystem.enableBlend();
                context.drawSprite(startX + 6, startY + 7, 0, 18, 18, sprite);
            }
            Object name = entry.getDisplayName();
            if (entry.getAmplifier() > 0) {
                name = (String)name + " " + StatusEffectActionRenderer.toRoman(entry.getAmplifier() + 1);
            }
            context.drawTextWithShadow(textRenderer, (String)name, startX + 28, startY + 6, 0xFFFFFF);
            String duration = entry.formatDuration(localTick);
            context.drawTextWithShadow(textRenderer, duration, startX + 28, startY + 16, 0x7F7F7F);
            startY += cardHeight + 2;
        }
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}

