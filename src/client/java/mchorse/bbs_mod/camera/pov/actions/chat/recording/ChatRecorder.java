/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.utils.interps.Interpolations
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.widget.TextFieldWidget
 *  net.minecraft.text.Style
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 */
package mchorse.bbs_mod.camera.pov.actions.chat.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.ChatPovActionClip;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.ChatHudPovAccessor;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.ChatScreenPovAccessor;
import mchorse.bbs_mod.camera.pov.integration.mixin.minecraft.TextFieldWidgetPovAccessor;
import java.util.Objects;
import java.util.Optional;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatRecorder {
    private ChatPovActionClip recordingChatClip;
    private String lastText = null;
    private Boolean lastBarVisible = null;
    private Integer lastCursor = null;
    private Integer lastSelStart = null;
    private Integer lastSelEnd = null;
    private Integer lastScroll = null;
    private float lastRecordedCurTx = Float.NaN;
    private float lastRecordedCurTy = Float.NaN;
    private float lastCursorKeyTick = Float.NaN;
    private static ChatRecorder activeInstance = null;
    private static int currentRecorderTick = 0;
    private static float lastExecutedLocalTick = -1.0f;
    private static int executedTextCounter = 0;
    private ReplayKeyframesPovAccess currentAccess = null;
    private Recorder currentRecorder = null;
    private static boolean replayingExternalMessage = false;

    public static void setReplayingExternalMessage(boolean replaying) {
        replayingExternalMessage = replaying;
    }

    public void reset() {
        activeInstance = null;
        this.currentAccess = null;
        this.currentRecorder = null;
        this.recordingChatClip = null;
        this.lastText = null;
        this.lastBarVisible = null;
        this.lastCursor = null;
        this.lastSelStart = null;
        this.lastSelEnd = null;
        this.lastScroll = null;
        this.lastRecordedCurTx = Float.NaN;
        this.lastRecordedCurTy = Float.NaN;
        this.lastCursorKeyTick = Float.NaN;
        lastExecutedLocalTick = -1.0f;
        executedTextCounter = 0;
        replayingExternalMessage = false;
    }

    public static void onChatMessageReceived(Text message) {
        String[] lines;
        RecordedPovActions actions;
        if (replayingExternalMessage || activeInstance == null || message == null) {
            return;
        }
        if (ChatRecorder.activeInstance.currentAccess == null || ChatRecorder.activeInstance.currentRecorder == null) {
            return;
        }
        String formatted = ChatRecorder.toFormattedString(message);
        if (formatted == null || formatted.trim().isEmpty()) {
            return;
        }
        if (ChatRecorder.activeInstance.recordingChatClip == null && (actions = ChatRecorder.activeInstance.currentAccess.bbsPov$getActions()) != null) {
            for (ChatPovActionClip clip : actions.getClips(ChatPovActionClip.class)) {
                if (clip == null) continue;
                ChatRecorder.activeInstance.recordingChatClip = clip;
                break;
            }
            if (ChatRecorder.activeInstance.recordingChatClip == null) {
                ChatRecorder.activeInstance.recordingChatClip = (ChatPovActionClip)actions.add(PovActionType.CHAT, 0, ChatRecorder.activeInstance.currentRecorder.tick + 1);
                ChatRecorder.activeInstance.recordingChatClip.createDefaultKeyframes();
                ChatRecorder.activeInstance.recordingChatClip.barVisible.insert(0.0f, false);
            }
        }
        if (ChatRecorder.activeInstance.recordingChatClip == null) {
            return;
        }
        float localTick = currentRecorderTick - (Integer)ChatRecorder.activeInstance.recordingChatClip.tick.get();
        if (localTick < 0.0f) {
            localTick = 0.0f;
        }
        ChatRecorder.activeInstance.recordingChatClip.duration.set(Math.max((Integer)ChatRecorder.activeInstance.recordingChatClip.duration.get(), (int)localTick + 1));
        for (String line : lines = formatted.split("\n")) {
            if (line == null || line.trim().isEmpty()) continue;
            if (Math.abs(localTick - lastExecutedLocalTick) < 1.0E-4f) {
                ++executedTextCounter;
            } else {
                lastExecutedLocalTick = localTick;
                executedTextCounter = 0;
            }
            float keyTick = localTick + (float)executedTextCounter * 0.001f;
            ChatRecorder.activeInstance.recordingChatClip.executedText.insert(keyTick, line);
        }
    }

    public static String toFormattedString(Text text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        text.visit((style, string) -> {
            if (style != null) {
                if (style.getColor() != null) {
                    Formatting formatting = Formatting.byName((String)style.getColor().getName());
                    if (formatting != null) {
                        sb.append(formatting.toString());
                    } else {
                        sb.append("\u00a7f");
                    }
                }
                if (style.isBold()) {
                    sb.append("\u00a7l");
                }
                if (style.isItalic()) {
                    sb.append("\u00a7o");
                }
                if (style.isUnderlined()) {
                    sb.append("\u00a7n");
                }
                if (style.isStrikethrough()) {
                    sb.append("\u00a7m");
                }
                if (style.isObfuscated()) {
                    sb.append("\u00a7k");
                }
            }
            sb.append(string);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    public void finish(ReplayKeyframesPovAccess access, int tick) {
        RecordedHudData hotbar;
        if (this.recordingChatClip != null) {
            if (this.lastBarVisible != null && this.lastBarVisible.booleanValue()) {
                float localTick = tick - (Integer)this.recordingChatClip.tick.get();
                this.recordValue(this.recordingChatClip.barVisible, false, localTick, this.lastBarVisible);
                this.lastBarVisible = false;
                this.recordingChatClip.duration.set(Math.max((Integer)this.recordingChatClip.duration.get(), (int)localTick + 1));
            }
            this.recordingChatClip.duration.set(Math.max(1, (Integer)this.recordingChatClip.duration.get()));
            this.recordingChatClip = null;
        }
        RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
        if (hotbar != null && this.lastBarVisible != null && this.lastBarVisible.booleanValue() && MinecraftClient.getInstance().currentScreen == null) {
            hotbar.cursorVisible.insert((float)tick, false);
        }
        this.currentAccess = null;
        this.currentRecorder = null;
        activeInstance = null;
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder) {
        RecordedHudData hotbar;
        if (!PovSettings.isBakeActions()) {
            return;
        }
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        activeInstance = this;
        currentRecorderTick = recorder.tick;
        this.currentAccess = access;
        this.currentRecorder = recorder;
        Screen screen = MinecraftClient.getInstance().currentScreen;
        RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
        if (screen instanceof ChatScreen) {
            ChatScreen chatScreen = (ChatScreen)screen;
            if (this.recordingChatClip == null) {
                this.recordingChatClip = (ChatPovActionClip)access.bbsPov$getActions().add(PovActionType.CHAT, recorder.tick, 1);
                this.recordingChatClip.createDefaultKeyframes();
                this.lastText = null;
                this.lastBarVisible = null;
                this.lastCursor = null;
                this.lastSelStart = null;
                this.lastSelEnd = null;
                if (hotbar != null) {
                    hotbar.cursorVisible.insert((float)recorder.tick, true);
                }
            }
            float localTick = recorder.tick - (Integer)this.recordingChatClip.tick.get();
            this.recordingChatClip.duration.set(Math.max(1, (int)localTick + 1));
            TextFieldWidget field = ((ChatScreenPovAccessor)chatScreen).bbsPov$getChatField();
            if (field != null) {
                String text = field.getText();
                int cursor = field.getCursor();
                int selStart = ((TextFieldWidgetPovAccessor)field).bbsPov$getSelectionStart();
                int selEnd = ((TextFieldWidgetPovAccessor)field).bbsPov$getSelectionEnd();
                if (this.lastBarVisible == null || !this.lastBarVisible.booleanValue()) {
                    this.recordValue(this.recordingChatClip.barVisible, true, localTick, this.lastBarVisible);
                    this.lastBarVisible = true;
                    if (hotbar != null) {
                        hotbar.cursorVisible.insert((float)recorder.tick, true);
                    }
                }
                this.recordValue(this.recordingChatClip.text, text, localTick, this.lastText);
                this.lastText = text;
                this.recordValue(this.recordingChatClip.cursorPos, cursor, localTick, this.lastCursor);
                this.lastCursor = cursor;
                this.recordValue(this.recordingChatClip.selStart, selStart, localTick, this.lastSelStart);
                this.lastSelStart = selStart;
                this.recordValue(this.recordingChatClip.selEnd, selEnd, localTick, this.lastSelEnd);
                this.lastSelEnd = selEnd;
                int scroll = 0;
                if (MinecraftClient.getInstance().inGameHud != null && MinecraftClient.getInstance().inGameHud.getChatHud() != null) {
                    scroll = ((ChatHudPovAccessor)MinecraftClient.getInstance().inGameHud.getChatHud()).bbsPov$getScrolledLines();
                }
                this.recordValue(this.recordingChatClip.chatScroll, scroll, localTick, this.lastScroll);
                this.lastScroll = scroll;
            }
        } else if (this.recordingChatClip != null && (this.lastBarVisible == null || this.lastBarVisible.booleanValue())) {
            float localTick = recorder.tick - (Integer)this.recordingChatClip.tick.get();
            this.recordValue(this.recordingChatClip.barVisible, false, localTick, this.lastBarVisible);
            this.lastBarVisible = false;
            this.recordingChatClip.duration.set(Math.max((Integer)this.recordingChatClip.duration.get(), (int)localTick + 1));
            if (hotbar != null && MinecraftClient.getInstance().currentScreen == null) {
                hotbar.cursorVisible.insert((float)recorder.tick, false);
            }
        }
    }

    public void sampleCursor(ReplayKeyframesPovAccess access, Recorder recorder, float tickDelta) {
        RecordedHudData hotbar;
        if (this.recordingChatClip == null || recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (!(screen instanceof ChatScreen)) {
            return;
        }
        RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
        if (hotbar == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        double mouseX = mc.mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight();
        double screenCenterX = (double)mc.getWindow().getScaledWidth() / 2.0;
        double screenCenterY = (double)mc.getWindow().getScaledHeight() / 2.0;
        float curTx = (float)((mouseX - screenCenterX) / 2.0);
        float curTy = (float)((screenCenterY - mouseY) / 2.0);
        float fraction = Math.max(0.0f, Math.min(1.0f, tickDelta));
        float absTick = (float)recorder.tick + fraction;
        KeyframeChannel<Transform> hudCursorLayout = hotbar.cursorLayout;
        if (Float.isNaN(this.lastCursorKeyTick)) {
            if (hudCursorLayout != null && !hudCursorLayout.getKeyframes().isEmpty()) {
                int lastIdx = hudCursorLayout.getKeyframes().size() - 1;
                ((Keyframe)hudCursorLayout.getKeyframes().get(lastIdx)).getInterpolation().setInterp(Interpolations.CONST);
            }
            hotbar.cursorVisible.insert(absTick, true);
            this.insertCursorKey(hudCursorLayout, absTick, curTx, curTy);
            this.lastRecordedCurTx = curTx;
            this.lastRecordedCurTy = curTy;
            this.lastCursorKeyTick = absTick;
        } else if (absTick > this.lastCursorKeyTick + 0.35f && (Math.abs(curTx - this.lastRecordedCurTx) > 0.03f || Math.abs(curTy - this.lastRecordedCurTy) > 0.03f)) {
            if (absTick - this.lastCursorKeyTick > 0.8f) {
                this.insertCursorKey(hudCursorLayout, absTick - 0.1f, this.lastRecordedCurTx, this.lastRecordedCurTy);
            }
            this.insertCursorKey(hudCursorLayout, absTick, curTx, curTy);
            this.lastRecordedCurTx = curTx;
            this.lastRecordedCurTy = curTy;
            this.lastCursorKeyTick = absTick;
        }
    }

    private void insertCursorKey(KeyframeChannel<Transform> channel, float tick, float x, float y) {
        if (channel == null) {
            return;
        }
        Transform transform = new Transform();
        transform.translate.set(x, y, 0.0f);
        int index = channel.insert(tick, transform);
        if (index >= 0 && index < channel.getKeyframes().size()) {
            ((Keyframe)channel.getKeyframes().get(index)).getInterpolation().setInterp(Interpolations.LINEAR);
        }
    }

    private <T> void recordValue(KeyframeChannel<T> channel, T value, float tick, T last) {
        if (channel.isEmpty() || !Objects.equals(value, last)) {
            channel.insert(tick, value);
        }
    }
}

