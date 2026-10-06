/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.Util
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class GuiTextRenderer {
    public static final Identifier SEARCH_FIELD = new Identifier("widget/text_field");

    private GuiTextRenderer() {
    }

    public static float sampleFloat(KeyframeChannel<Float> channel, float tick, float fallback) {
        if (channel == null || channel.isEmpty()) {
            return fallback;
        }
        Float value = (Float)channel.interpolate(tick, Float.valueOf(fallback));
        return value == null ? fallback : value.floatValue();
    }

    public static int sampleInt(KeyframeChannel<Integer> channel, float tick, int fallback) {
        if (channel == null || channel.isEmpty()) {
            return fallback;
        }
        Integer value = (Integer)channel.interpolate(tick, fallback);
        return value == null ? fallback : value;
    }

    public static boolean sampleBool(KeyframeChannel<Boolean> channel, float tick, boolean fallback) {
        if (channel == null || channel.isEmpty()) {
            return fallback;
        }
        Boolean value = (Boolean)channel.interpolate(tick, fallback);
        return value == null ? fallback : value;
    }

    public static String sampleString(KeyframeChannel<String> channel, float tick, String fallback) {
        if (channel == null || channel.isEmpty()) {
            return fallback;
        }
        String value = (String)channel.interpolate(tick, fallback);
        return value == null ? fallback : value;
    }

    public static boolean inBounds(float x, float y, int left, int top, int width, int height) {
        return x >= (float)left && x < (float)(left + width) && y >= (float)top && y < (float)(top + height);
    }

    public static boolean isSearchFocused(KeyframeChannel<Boolean> channel, float tick, String value, boolean hovered) {
        if (channel != null && !channel.isEmpty()) {
            return Boolean.TRUE.equals(channel.interpolate(tick, false));
        }
        return hovered || value != null && !value.isBlank();
    }

    public static void drawSearchField(Batcher2D batcher, String value, int x, int y, int width, int height, boolean focused, boolean drawBackground, float opacity, int selStart, int selEnd) {
        int end;
        int start;
        boolean selected;
        if (drawBackground) {
            batcher.getContext().drawGuiTexture(SEARCH_FIELD, x, y, width, height);
        }
        String text = value == null ? "" : value;
        int textX = x + (drawBackground ? 4 : 0);
        int textY = drawBackground ? y + Math.max(0, (height - 8) / 2) : y;
        int alpha = (int)(255.0f * opacity) << 24;
        boolean empty = text.isBlank();
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        if (empty && !focused && drawBackground) {
            MutableText hint = Text.translatable((String)"gui.recipebook.search_hint");
            batcher.getContext().drawTextWithShadow(font, (Text)hint, textX, textY, alpha | 0x808080);
            return;
        }
        if (!empty) {
            batcher.getContext().drawTextWithShadow(font, text, textX, textY, alpha | 0xFFFFFF);
        }
        boolean bl = selected = (start = MathHelper.clamp((int)Math.min(selStart, selEnd), (int)0, (int)text.length())) != (end = MathHelper.clamp((int)Math.max(selStart, selEnd), (int)0, (int)text.length()));
        if (selected) {
            int x1 = textX + font.getWidth(text.substring(0, start));
            int x2 = textX + font.getWidth(text.substring(0, end));
            batcher.getContext().fill(RenderLayer.getGuiTextHighlight(), x1, textY - 1, x2, textY + 9, -16776961);
        } else if (focused && Util.getMeasuringTimeMs() / 300L % 2L == 0L) {
            int caret = MathHelper.clamp((int)selStart, (int)0, (int)text.length());
            int caretX = textX + font.getWidth(text.substring(0, caret));
            if (caret < text.length()) {
                batcher.getContext().fill(RenderLayer.getGuiOverlay(), caretX, textY - 1, caretX + 1, textY + 10, alpha | 0xD0D0D0);
            } else {
                batcher.getContext().drawTextWithShadow(font, "_", caretX, textY, alpha | 0xFFFFFF);
            }
        }
    }
}

