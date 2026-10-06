/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.gui.widget.TextFieldWidget
 */
package mchorse.bbs_mod.camera.pov.actions.chat.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatHistoryRenderer;
import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatInputBarRenderer;
import mchorse.bbs_mod.camera.pov.actions.clip.ChatPovActionClip;
import mchorse.bbs_mod.camera.pov.hud.HudState;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.ChatScreenPovAccessor;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.TextFieldWidgetPovAccessor;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;

public final class ChatActionRenderer {
    private ChatActionRenderer() {
    }

    public static void renderHUD(Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height, HudState state) {
        ChatActionRenderer.renderHUD(batcher, null, actions, tick, tick, width, height, state);
    }

    public static void renderHUD(Batcher2D batcher, Film film, RecordedPovActions actions, float replayTick, float filmTick, int width, int height, HudState state) {
        if (actions == null && film == null) {
            return;
        }
        DrawContext context = batcher.getContext();
        if (context == null) {
            return;
        }
        ChatPovActionClip active = actions != null ? actions.getActiveChat(replayTick) : null;
        boolean barVis = active != null && (active.barVisible.isEmpty() || (Boolean)active.barVisible.interpolate(replayTick - (float)((Integer)active.tick.get()).intValue(), true) != false);
        int scrollOffset = 0;
        if (active != null) {
            scrollOffset = (Integer)active.chatScroll.interpolate(replayTick - (float)((Integer)active.tick.get()).intValue(), 0);
        }
        ChatHistoryRenderer.renderPlaybackHistory(batcher, film, actions, replayTick, filmTick, width, height, barVis, scrollOffset);
        if (active == null) {
            return;
        }
        float curX = -1000.0f;
        float curY = -1000.0f;
        boolean curVis = false;
        if (state != null && state.cursorVisible && state.cursorLayout != null) {
            curVis = true;
            curX = (float)width / 2.0f + state.cursorLayout.translate.x * 2.0f;
            curY = (float)height / 2.0f - state.cursorLayout.translate.y * 2.0f;
        }
        ChatActionRenderer.renderChatClip(batcher, active, replayTick, width, height, curX, curY, curVis);
    }

    public static void renderChatClip(Batcher2D batcher, ChatPovActionClip clip, float tick, int width, int height, float cursorX, float cursorY, boolean cursorVisible) {
        boolean barVisible;
        float localTick = tick - (float)((Integer)clip.tick.get()).intValue();
        if (localTick < 0.0f || localTick > (float)((Integer)clip.duration.get()).intValue()) {
            return;
        }
        boolean bl = barVisible = clip.barVisible.isEmpty() || (Boolean)clip.barVisible.interpolate(localTick, true) != false;
        if (!barVisible) {
            return;
        }
        String text = clip.text.interpolate(localTick, "");
        int cursorPos = clip.cursorPos.interpolate(localTick, text.length());
        int selStart = clip.selStart.interpolate(localTick, -1);
        int selEnd = clip.selEnd.interpolate(localTick, -1);
        boolean showRecs = (Boolean)clip.showRecommendations.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        if (font == null) {
            return;
        }
        ChatInputBarRenderer.renderInputBar(batcher, font, text, cursorPos, selStart, selEnd, showRecs, width, height, cursorX, cursorY, cursorVisible);
    }

    public static void renderLiveChat(Batcher2D batcher, ChatScreen chatScreen, int width, int height) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        DrawContext context = batcher.getContext();
        if (context == null) {
            return;
        }
        TextRenderer font = mc.textRenderer;
        if (font == null) {
            return;
        }
        double mouseX = mc.mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight();
        float cursorX = (float)mouseX;
        float cursorY = (float)mouseY;
        ChatHistoryRenderer.renderLiveHistory(context, width, height);
        TextFieldWidget field = ((ChatScreenPovAccessor)chatScreen).bbsPov$getChatField();
        if (field != null) {
            String text = field.getText();
            int cursorPos = field.getCursor();
            int selStart = ((TextFieldWidgetPovAccessor)field).bbsPov$getSelectionStart();
            int selEnd = ((TextFieldWidgetPovAccessor)field).bbsPov$getSelectionEnd();
            ChatInputBarRenderer.renderInputBar(batcher, font, text, cursorPos, selStart, selEnd, true, width, height, cursorX, cursorY, true);
        }
    }

    public static void renderExecutionTexts(Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height, boolean barVisible) {
        ChatHistoryRenderer.renderPlaybackHistory(batcher, null, actions, tick, tick, width, height, barVisible, 0);
    }

    public static void renderExecutionTexts(Batcher2D batcher, RecordedPovActions actions, float tick, int width, int height, boolean barVisible, int scrollOffset) {
        ChatHistoryRenderer.renderPlaybackHistory(batcher, null, actions, tick, tick, width, height, barVisible, scrollOffset);
    }

    public static void renderExecutionTexts(Batcher2D batcher, Film film, RecordedPovActions actions, float replayTick, float filmTick, int width, int height, boolean barVisible, int scrollOffset) {
        ChatHistoryRenderer.renderPlaybackHistory(batcher, film, actions, replayTick, filmTick, width, height, barVisible, scrollOffset);
    }
}

