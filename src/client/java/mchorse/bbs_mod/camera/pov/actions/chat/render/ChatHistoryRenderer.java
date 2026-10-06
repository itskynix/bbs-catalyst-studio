/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.hud.ChatHudLine$Visible
 */
package mchorse.bbs_mod.camera.pov.actions.chat.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.chat.editor.UIExecutedTextKeyframeFactory;
import mchorse.bbs_mod.camera.pov.actions.clip.ChatPovActionClip;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.ChatHudPovAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;

public final class ChatHistoryRenderer {
    private ChatHistoryRenderer() {
    }

    public static int getChatBgColor(float factor) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double textBgOpacity = mc != null && mc.options != null && mc.options.getTextBackgroundOpacity() != null ? (Double)mc.options.getTextBackgroundOpacity().getValue() : 0.5;
        int alpha = (int)(255.0 * textBgOpacity * (double)factor);
        alpha = Math.max(0, Math.min(255, alpha));
        return alpha << 24;
    }

    public static void renderPlaybackHistory(Batcher2D batcher, Film film, RecordedPovActions actions, float replayTick, float filmTick, int width, int height, boolean barVisible, int scrollOffset) {
        if (batcher == null) {
            return;
        }
        DrawContext context = batcher.getContext();
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        if (font == null) {
            return;
        }
        ArrayList<ActiveExecutionText> activeList = new ArrayList<ActiveExecutionText>();
        if (film != null && film.replays != null && !film.replays.getList().isEmpty()) {
            for (Replay r : film.replays.getList()) {
                ReplayKeyframesPovAccess rAccess;
                RecordedPovActions rActions;
                ReplayKeyframes replayKeyframes;
                if (r == null || !((replayKeyframes = r.keyframes) instanceof ReplayKeyframesPovAccess) || (rActions = (rAccess = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getActions()) == null) continue;
                int looping = (Integer)r.looping.get();
                float rTick = looping > 0 ? filmTick % (float)looping : filmTick;
                ChatHistoryRenderer.collectExecutionTexts(rActions, 0.0f, rTick, activeList, barVisible);
            }
        } else if (actions != null) {
            ChatHistoryRenderer.collectExecutionTexts(actions, 0.0f, replayTick, activeList, barVisible);
        }
        if (activeList.isEmpty()) {
            return;
        }
        activeList.sort((a, b) -> Float.compare(a.globalTick, b.globalTick));
        int bottomY = height - 40;
        int boxX1 = 2;
        int chatWidth = mc.inGameHud != null && mc.inGameHud.getChatHud() != null ? mc.inGameHud.getChatHud().getWidth() : (mc.options != null && mc.options.getChatWidth() != null ? (int)Math.ceil((Double)mc.options.getChatWidth().getValue() * 280.0 + 40.0) : 320);
        int boxX2 = Math.min(boxX1 + chatWidth + 4, width - 2);
        int textX = boxX1 + 4;
        if (barVisible) {
            int itemIdx;
            int maxVisibleLines = 20;
            if (mc.options != null && mc.options.getChatHeightFocused() != null) {
                maxVisibleLines = Math.max(1, (int)Math.floor(((Double)mc.options.getChatHeightFocused().getValue() * 160.0 + 20.0) / 9.0));
            }
            maxVisibleLines = Math.min(maxVisibleLines, Math.max(1, (bottomY - 20) / 9));
            int totalLines = activeList.size();
            int maxScroll = Math.max(0, totalLines - maxVisibleLines);
            int clampedScroll = Math.max(0, Math.min(scrollOffset, maxScroll));
            for (int i = 0; i < maxVisibleLines && (itemIdx = totalLines - 1 - clampedScroll - i) >= 0 && itemIdx < totalLines; ++i) {
                ActiveExecutionText item = (ActiveExecutionText)activeList.get(itemIdx);
                int lineY = bottomY - i * 9;
                if (lineY < 10) break;
                String cleanText = item.text.replace("\r", "").replace("\n", "");
                batcher.box((float)boxX1, (float)(lineY - 1), (float)boxX2, (float)(lineY + 8), ChatHistoryRenderer.getChatBgColor(1.0f));
                batcher.flush();
                String plainText = cleanText.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                if (!plainText.startsWith("<")) {
                    batcher.box((float)boxX1, (float)(lineY - 1), (float)(boxX1 + 2), (float)(lineY + 8), -3092272);
                    batcher.flush();
                }
                if (context == null) continue;
                context.drawText(font, cleanText, textX, lineY, -1, true);
            }
            if (totalLines > maxVisibleLines) {
                int u = maxVisibleLines * 9;
                int t = totalLines * 9;
                int w = Math.max(4, u * u / t);
                int v = clampedScroll * u / totalLines;
                int thumbBottom = bottomY + 8 - v;
                int thumbTop = thumbBottom - w;
                int barX = boxX2 - 4;
                batcher.box((float)barX, (float)thumbTop, (float)(barX + 1), (float)thumbBottom, -11907731);
                batcher.box((float)(barX + 1), (float)thumbTop, (float)(barX + 2), (float)thumbBottom, -7499080);
                batcher.flush();
            }
        } else {
            int maxHudLines = 10;
            if (mc.options != null && mc.options.getChatHeightUnfocused() != null) {
                maxHudLines = Math.max(1, (int)Math.floor(((Double)mc.options.getChatHeightUnfocused().getValue() * 160.0 + 20.0) / 9.0));
            }
            maxHudLines = Math.min(maxHudLines, Math.max(1, (bottomY - 20) / 9));
            int renderedHudCount = 0;
            for (int i = activeList.size() - 1; i >= 0 && renderedHudCount < maxHudLines; --i) {
                int textAlpha;
                ActiveExecutionText item = (ActiveExecutionText)activeList.get(i);
                int lineY = bottomY - renderedHudCount * 9;
                if (lineY < 0) break;
                float remaining = item.duration - item.age;
                float alpha = 1.0f;
                if (remaining < 20.0f) {
                    alpha = Math.max(0.0f, remaining / 20.0f);
                }
                if ((textAlpha = (int)(255.0f * alpha)) < 4) continue;
                int textColor = textAlpha << 24 | 0xFFFFFF;
                String cleanText = item.text.replace("\r", "").replace("\n", "");
                batcher.box((float)boxX1, (float)(lineY - 1), (float)boxX2, (float)(lineY + 8), ChatHistoryRenderer.getChatBgColor(alpha));
                batcher.flush();
                String plainText = cleanText.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
                if (!plainText.startsWith("<")) {
                    int tagColor = textAlpha << 24 | 0xD0D0D0;
                    batcher.box((float)boxX1, (float)(lineY - 1), (float)(boxX1 + 2), (float)(lineY + 8), tagColor);
                    batcher.flush();
                }
                if (context != null) {
                    context.drawText(font, cleanText, textX, lineY, textColor, true);
                }
                ++renderedHudCount;
            }
        }
    }

    public static void renderLiveHistory(DrawContext context, int width, int height) {
        int itemIdx;
        if (context == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            return;
        }
        TextRenderer font = mc.textRenderer;
        if (font == null) {
            return;
        }
        List<ChatHudLine.Visible> visibleMessages = ((ChatHudPovAccessor)mc.inGameHud.getChatHud()).bbsPov$getVisibleMessages();
        int scrolledLines = ((ChatHudPovAccessor)mc.inGameHud.getChatHud()).bbsPov$getScrolledLines();
        if (visibleMessages == null || visibleMessages.isEmpty()) {
            return;
        }
        int bottomY = height - 40;
        int boxX1 = 2;
        int chatWidth = 320;
        if (mc.options != null && mc.options.getChatWidth() != null) {
            chatWidth = (int)Math.ceil((Double)mc.options.getChatWidth().getValue() * 280.0 + 40.0);
        }
        int boxX2 = Math.min(boxX1 + chatWidth + 4, width - 2);
        int textX = boxX1 + 4;
        int maxVisibleLines = 20;
        if (mc.options != null && mc.options.getChatHeightFocused() != null) {
            maxVisibleLines = Math.max(1, (int)Math.floor(((Double)mc.options.getChatHeightFocused().getValue() * 160.0 + 20.0) / 9.0));
        }
        maxVisibleLines = Math.min(maxVisibleLines, Math.max(1, (bottomY - 20) / 9));
        int totalLines = visibleMessages.size();
        int maxScroll = Math.max(0, totalLines - maxVisibleLines);
        int clampedScroll = Math.max(0, Math.min(scrolledLines, maxScroll));
        for (int i = 0; i < maxVisibleLines && (itemIdx = clampedScroll + i) >= 0 && itemIdx < totalLines; ++i) {
            ChatHudLine.Visible visibleLine = visibleMessages.get(itemIdx);
            if (visibleLine == null) continue;
            int lineY = bottomY - i * 9;
            if (lineY < 10) break;
            context.fill(boxX1, lineY - 1, boxX2, lineY + 8, ChatHistoryRenderer.getChatBgColor(1.0f));
            if (visibleLine.indicator() != null) {
                int tagColor = 0xFF000000 | visibleLine.indicator().indicatorColor() & 0xFFFFFF;
                context.fill(boxX1, lineY - 1, boxX1 + 2, lineY + 8, tagColor);
            }
            context.drawText(font, visibleLine.content(), textX, lineY, -1, true);
        }
        if (totalLines > maxVisibleLines) {
            int u = maxVisibleLines * 9;
            int t = totalLines * 9;
            int w = Math.max(4, u * u / t);
            int v = clampedScroll * u / totalLines;
            int thumbBottom = bottomY + 8 - v;
            int thumbTop = thumbBottom - w;
            int barX = boxX2 - 4;
            context.fill(barX, thumbTop, barX + 1, thumbBottom, -11907731);
            context.fill(barX + 1, thumbTop, barX + 2, thumbBottom, -7499080);
        }
    }

    private static void collectExecutionTexts(RecordedPovActions actions, float baseOffset, float currentTick, List<ActiveExecutionText> activeList, boolean barVisible) {
        for (ChatPovActionClip chatClip : actions.getClips(ChatPovActionClip.class)) {
            if (chatClip == null || chatClip.executedText == null || chatClip.executedText.isEmpty()) continue;
            float clipStart = baseOffset + (float)((Integer)chatClip.tick.get()).intValue();
            for (Keyframe kf : chatClip.executedText.getList()) {
                float duration;
                String rawVal;
                if (kf == null || kf.getValue() == null || ((String)kf.getValue()).trim().isEmpty() || UIExecutedTextKeyframeFactory.isHiddenFromHud(rawVal = (String)kf.getValue()) && !barVisible) continue;
                float kfLocalTick = kf.getTick();
                float globalKfTick = clipStart + kfLocalTick;
                float age = currentTick - globalKfTick;
                float f = duration = kf.getDuration() > 0.0f ? kf.getDuration() : 200.0f;
                if (age < 0.0f || !barVisible && age >= duration) continue;
                String val = UIExecutedTextKeyframeFactory.getRawText(rawVal).replace("\r", "");
                String[] lines = val.split("\n");
                for (int lineIdx = 0; lineIdx < lines.length; ++lineIdx) {
                    String line = lines[lineIdx];
                    if (line == null || line.trim().isEmpty()) continue;
                    activeList.add(new ActiveExecutionText(globalKfTick + (float)lineIdx * 1.0E-4f, age, duration, line));
                }
            }
        }
    }

    private static class ActiveExecutionText {
        final float globalTick;
        final float age;
        final float duration;
        final String text;

        ActiveExecutionText(float globalTick, float age, float duration, String text) {
            this.globalTick = globalTick;
            this.age = age;
            this.duration = duration;
            this.text = text;
        }
    }
}

