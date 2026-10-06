/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffects
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class BeaconGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final BeaconGuiRenderer INSTANCE = new BeaconGuiRenderer();

    private BeaconGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        BeaconGuiRenderer.drawChrome(ctx.batcher, ctx.clip, ctx.guiId, ctx.localTick, cursorX, cursorY, ctx.hover);
    }

    @Override
    public void drawLateItems(GuiRenderContext ctx) {
        BeaconGuiRenderer.drawPaymentIcons(ctx.batcher);
    }

    private static void drawChrome(Batcher2D batcher, GuiPovActionClip clip, String guiId, float localTick, float cursorX, float cursorY, GuiPointerHover hover) {
        batcher.flush();
        DrawContext context = batcher.getContext();
        Identifier texture = new Identifier("minecraft", "textures/gui/container/beacon.png");
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        MutableText primaryText = Text.translatable((String)"block.minecraft.beacon.primary");
        MutableText secondaryText = Text.translatable((String)"block.minecraft.beacon.secondary");
        int pw = textRenderer.getWidth((StringVisitable)primaryText);
        int sw = textRenderer.getWidth((StringVisitable)secondaryText);
        context.drawTextWithShadow(textRenderer, (Text)primaryText, 62 - pw / 2, 10, 0xE0E0E0);
        context.drawTextWithShadow(textRenderer, (Text)secondaryText, 169 - sw / 2, 10, 0xE0E0E0);
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 10.0f);
        int primary = clip.beaconPrimary.isEmpty() ? 0 : (Integer)clip.beaconPrimary.interpolate(localTick, 0);
        int secondary = clip.beaconSecondary.isEmpty() ? 0 : (Integer)clip.beaconSecondary.interpolate(localTick, 0);
        int level = clip.beaconLevel.isEmpty() ? 0 : (Integer)clip.beaconLevel.interpolate(localTick, 0);
        KeyframeChannel<ItemStack> paymentChannel = clip.getGuiSlot(guiId, "payment");
        ItemStack payment = paymentChannel == null || paymentChannel.isEmpty() ? ItemStack.EMPTY : (ItemStack)paymentChannel.interpolate(localTick, ItemStack.EMPTY);
        boolean hasPayment = payment != null && !payment.isEmpty();
        BeaconGuiRenderer.drawBeaconButton(context, 58, 27, StatusEffects.SPEED, level >= 1, primary == 1, cursorX, cursorY, hover);
        BeaconGuiRenderer.drawBeaconButton(context, 80, 27, StatusEffects.HASTE, level >= 1, primary == 2, cursorX, cursorY, hover);
        BeaconGuiRenderer.drawBeaconButton(context, 58, 51, StatusEffects.RESISTANCE, level >= 2, primary == 3, cursorX, cursorY, hover);
        BeaconGuiRenderer.drawBeaconButton(context, 80, 51, StatusEffects.JUMP_BOOST, level >= 2, primary == 4, cursorX, cursorY, hover);
        BeaconGuiRenderer.drawBeaconButton(context, 69, 75, StatusEffects.STRENGTH, level >= 3, primary == 5, cursorX, cursorY, hover);
        BeaconGuiRenderer.drawBeaconButton(context, 144, 47, StatusEffects.REGENERATION, level >= 4, secondary == 1, cursorX, cursorY, hover);
        StatusEffect primaryEffect = BeaconGuiRenderer.beaconPrimaryEffect(primary);
        if (level >= 4 && primaryEffect != null) {
            BeaconGuiRenderer.drawBeaconButton(context, 168, 47, primaryEffect, true, secondary == 2, cursorX, cursorY, hover);
        }
        boolean confirmHover = GuiTextRenderer.inBounds(cursorX, cursorY, 164, 107, 22, 22);
        boolean cancelHover = GuiTextRenderer.inBounds(cursorX, cursorY, 190, 107, 22, 22);
        String confirmSprite = !hasPayment || primary == 0 ? "container/beacon/button_disabled" : (confirmHover ? "container/beacon/button_highlighted" : "container/beacon/button");
        context.drawGuiTexture(new Identifier(confirmSprite), 164, 107, 22, 22);
        context.drawGuiTexture(new Identifier(cancelHover ? "container/beacon/button_highlighted" : "container/beacon/button"), 190, 107, 22, 22);
        context.setShaderColor(!hasPayment || primary == 0 ? 0.45f : 1.0f, !hasPayment || primary == 0 ? 0.45f : 1.0f, !hasPayment || primary == 0 ? 0.45f : 1.0f, 1.0f);
        context.drawGuiTexture(new Identifier("container/beacon/confirm"), 166, 109, 18, 18);
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        context.drawGuiTexture(new Identifier("container/beacon/cancel"), 192, 109, 18, 18);
        context.getMatrices().pop();
        context.draw();
        batcher.flush();
    }

    private static void drawBeaconButton(DrawContext context, int x, int y, StatusEffect effect, boolean enabled, boolean selected, float cursorX, float cursorY, GuiPointerHover hover) {
        boolean isHovered = GuiTextRenderer.inBounds(cursorX, cursorY, x, y, 22, 22);
        String buttonSprite = selected ? "container/beacon/button_selected" : (!enabled ? "container/beacon/button_disabled" : (isHovered ? "container/beacon/button_highlighted" : "container/beacon/button"));
        context.drawGuiTexture(new Identifier(buttonSprite), x, y, 22, 22);
        if (effect != null) {
            Sprite sprite = MinecraftClient.getInstance().getStatusEffectSpriteManager().getSprite(effect);
            if (sprite != null) {
                context.drawSprite(x + 2, y + 2, 0, 18, 18, sprite);
            }
            if (isHovered) {
                hover.widget = effect.getName();
            }
        }
    }

    private static StatusEffect beaconPrimaryEffect(int primary) {
        return switch (primary) {
            case 1 -> StatusEffects.SPEED;
            case 2 -> StatusEffects.HASTE;
            case 3 -> StatusEffects.RESISTANCE;
            case 4 -> StatusEffects.JUMP_BOOST;
            case 5 -> StatusEffects.STRENGTH;
            default -> null;
        };
    }

    private static void drawPaymentIcons(Batcher2D batcher) {
        DrawContext context = batcher.getContext();
        DiffuseLighting.enableGuiDepthLighting();
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 100.0f);
        context.drawItem(new ItemStack((ItemConvertible)Items.NETHERITE_INGOT), 20, 109);
        context.drawItem(new ItemStack((ItemConvertible)Items.EMERALD), 41, 109);
        context.drawItem(new ItemStack((ItemConvertible)Items.DIAMOND), 63, 109);
        context.drawItem(new ItemStack((ItemConvertible)Items.GOLD_INGOT), 86, 109);
        context.drawItem(new ItemStack((ItemConvertible)Items.IRON_INGOT), 108, 109);
        context.getMatrices().pop();
    }
}

