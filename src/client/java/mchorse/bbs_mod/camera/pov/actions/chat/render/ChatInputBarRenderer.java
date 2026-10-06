/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 */
package mchorse.bbs_mod.camera.pov.actions.chat.render;

import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatCommandSuggestor;
import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatHistoryRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class ChatInputBarRenderer {
    private ChatInputBarRenderer() {
    }

    public static void renderInputBar(Batcher2D batcher, TextRenderer font, String text, int cursorPos, int selStart, int selEnd, boolean showRecs, int width, int height, float cursorX, float cursorY, boolean cursorVisible) {
        boolean cursorBlink;
        int maxSel;
        if (batcher == null || font == null) {
            return;
        }
        DrawContext context = batcher.getContext();
        if (text == null) {
            text = "";
        }
        cursorPos = Math.max(0, Math.min(cursorPos, text.length()));
        int barY1 = height - 14;
        int barY2 = height - 2;
        int barX1 = 2;
        int barX2 = width - 2;
        MinecraftClient mc = MinecraftClient.getInstance();
        int barColor = mc != null && mc.options != null ? mc.options.getTextBackgroundColor(Integer.MIN_VALUE) : ChatHistoryRenderer.getChatBgColor(1.0f);
        batcher.box((float)barX1, (float)barY1, (float)barX2, (float)barY2, barColor);
        batcher.flush();
        int textX = 4;
        int textY = height - 12;
        ChatCommandSuggestor.ParseResultInfo info = null;
        String ghostPreview = null;
        if (showRecs && text.startsWith("/")) {
            info = ChatCommandSuggestor.getParsedInfo(text, cursorPos);
            ghostPreview = ChatCommandSuggestor.renderCommandSuggestions(context, font, text, info, textX, barY1, width, cursorX, cursorY, cursorVisible);
        }
        boolean hasSelection = selStart >= 0 && selEnd >= 0 && selStart != selEnd;
        int minSel = hasSelection ? Math.max(0, Math.min(selStart, selEnd)) : -1;
        int n = maxSel = hasSelection ? Math.min(text.length(), Math.max(selStart, selEnd)) : -1;
        if (!hasSelection) {
            ChatCommandSuggestor.renderColoredCommandText(context, font, text, textX, textY, ghostPreview, info);
        } else {
            String before = text.substring(0, minSel);
            String selected = text.substring(minSel, maxSel);
            String after = text.substring(maxSel);
            int x = textX;
            ChatCommandSuggestor.renderColoredCommandText(context, font, before, x, textY, null, info);
            int selWidth = font.getWidth(selected);
            context.fill(x += font.getWidth(before), textY - 1, x + selWidth, textY + 9, -2147483393);
            context.drawTextWithShadow(font, selected, x, textY, -1);
            ChatCommandSuggestor.renderColoredCommandText(context, font, after, x += selWidth, textY, ghostPreview, info);
        }
        boolean bl = cursorBlink = System.currentTimeMillis() / 300L % 2L == 0L;
        if (cursorBlink && !hasSelection) {
            String textBeforeCursor = text.substring(0, cursorPos);
            int cursorScreenX = textX + font.getWidth(textBeforeCursor);
            if (cursorPos < text.length()) {
                context.fill(cursorScreenX, textY - 1, cursorScreenX + 1, textY + 9, -3092272);
            } else {
                context.drawTextWithShadow(font, "_", cursorScreenX, textY, -1);
            }
        }
    }
}

