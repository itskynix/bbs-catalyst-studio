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
 *  net.minecraft.client.gui.screen.DeathScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.SleepingChatScreen
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.text.Text
 */
package mchorse.bbs_mod.camera.pov.actions.menu.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.MenuPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.menu.render.MenuSleepOverlay;
import mchorse.bbs_mod.camera.pov.actions.menu.schema.MenuTypeResolver;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.DeathScreenPovAccess;
import java.util.List;
import java.util.Objects;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class MenuRecorder {
    private MenuPovActionClip recordingClip;
    private float lastRecordedCurTx = Float.NaN;
    private float lastRecordedCurTy = Float.NaN;
    private float lastCursorKeyTick = Float.NaN;
    private boolean cursorWasMoving;

    public void reset() {
        this.recordingClip = null;
        this.lastRecordedCurTx = Float.NaN;
        this.lastRecordedCurTy = Float.NaN;
        this.lastCursorKeyTick = Float.NaN;
        this.cursorWasMoving = false;
    }

    public void finish(ReplayKeyframesPovAccess access, int tick) {
        this.finalizeClip(access, tick);
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder) {
        int sleepTimer;
        if (!PovSettings.isBakeMenu()) {
            return;
        }
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client.currentScreen;
        String menuType = MenuTypeResolver.resolve(screen);
        String recordingType = this.recordingClip == null || this.recordingClip.state.isEmpty() ? null : (String)this.recordingClip.state.get(0).getValue();
        int n = sleepTimer = client.player == null ? 0 : client.player.getSleepTimer();
        if (menuType == null && "sleep".equals(recordingType) && sleepTimer > 0) {
            menuType = "sleep";
        }
        if (menuType == null) {
            if ("sleep".equals(recordingType) && this.recordingClip != null) {
                float localTick = recorder.tick - (Integer)this.recordingClip.tick.get();
                this.recordValue(this.recordingClip.opacity, Float.valueOf(0.0f), localTick);
                this.recordValue(this.recordingClip.leaveBed, false, localTick);
            }
            this.finalizeClip(access, recorder.tick);
            return;
        }
        if (recordingType != null && !recordingType.equals(menuType)) {
            this.finalizeClip(access, recorder.tick);
        }
        if (this.recordingClip == null) {
            this.recordingClip = (MenuPovActionClip)access.bbsPov$getActions().add(PovActionType.MENU, recorder.tick, 1);
            this.recordingClip.state.insert(0.0f, menuType);
            this.recordingClip.cursorVisible.insert(0.0f, true);
            RecordedHudData hud = access.bbsPov$getHud();
            if (hud != null) {
                hud.cursorVisible.insert((float)recorder.tick, true);
            }
        }
        float localTick = recorder.tick - (Integer)this.recordingClip.tick.get();
        this.recordingClip.duration.set(Math.max(1, (int)localTick + 1));
        if (screen != null) {
            this.recordCursorMotion(access, recorder, 0.0f);
            this.recordValue(this.recordingClip.cursorVisible, true, localTick);
        } else {
            RecordedHudData hud;
            this.recordValue(this.recordingClip.cursorVisible, false, localTick);
            RecordedHudData recordedHudData = hud = access == null ? null : access.bbsPov$getHud();
            if (hud != null && MinecraftClient.getInstance().currentScreen == null) {
                this.recordValue(hud.cursorVisible, false, recorder.tick);
            }
        }
        this.recordTypeFields(screen, menuType, localTick, client.player);
    }

    private void recordTypeFields(Screen screen, String menuType, float localTick, ClientPlayerEntity player) {
        if ("death".equals(menuType) && screen instanceof DeathScreen) {
            String message = "";
            String score = "Score: 0";
            if (screen instanceof DeathScreenPovAccess) {
                DeathScreenPovAccess deathAccess = (DeathScreenPovAccess)screen;
                Text deathMessage = deathAccess.bbsPov$getDeathMessage();
                Text scoreText = deathAccess.bbsPov$getScoreText();
                if (deathMessage != null) {
                    message = deathMessage.getString();
                }
                if (scoreText != null) {
                    score = scoreText.getString();
                }
            }
            this.recordValue(this.recordingClip.deathMessage, message, localTick);
            this.recordValue(this.recordingClip.score, score, localTick);
            this.recordValue(this.recordingClip.bgOpacity, Float.valueOf(1.0f), localTick);
            boolean active = true;
            if (screen instanceof DeathScreenPovAccess) {
                DeathScreenPovAccess deathAccess = (DeathScreenPovAccess)screen;
                active = deathAccess.bbsPov$getTicksSinceDeath() >= 20;
            } else if (localTick < 20.0f) {
                active = false;
            }
            this.recordValue(this.recordingClip.buttonsActive, active, localTick);
        } else if ("sleep".equals(menuType)) {
            int timer = player == null ? 0 : player.getSleepTimer();
            this.recordValue(this.recordingClip.opacity, Float.valueOf(MenuSleepOverlay.progress(timer)), localTick);
            this.recordValue(this.recordingClip.leaveBed, screen instanceof SleepingChatScreen, localTick);
        }
    }

    public void sampleCursor(ReplayKeyframesPovAccess access, Recorder recorder, float tickDelta) {
        if (this.recordingClip == null || recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) {
            return;
        }
        this.recordCursorMotion(access, recorder, tickDelta);
    }

    private void recordCursorMotion(ReplayKeyframesPovAccess access, Recorder recorder, float tickDelta) {
        boolean posChanged;
        MinecraftClient client = MinecraftClient.getInstance();
        double mouseX = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
        double centerX = (double)client.getWindow().getScaledWidth() / 2.0;
        double centerY = (double)client.getWindow().getScaledHeight() / 2.0;
        float curTx = (float)((mouseX - centerX) / 2.0);
        float curTy = (float)((centerY - mouseY) / 2.0);
        float fraction = Math.max(0.0f, Math.min(1.0f, tickDelta));
        float absTick = (float)recorder.tick + fraction;
        float localTick = absTick - (float)((Integer)this.recordingClip.tick.get()).intValue();
        RecordedHudData hud = access == null ? null : access.bbsPov$getHud();
        KeyframeChannel<Transform> clipCursor = this.recordingClip.cursorLayout;
        KeyframeChannel<Transform> hudCursor = hud == null ? null : hud.cursorLayout;
        boolean bl = posChanged = Math.abs(curTx - this.lastRecordedCurTx) > 0.005f || Math.abs(curTy - this.lastRecordedCurTy) > 0.005f;
        if (Float.isNaN(this.lastCursorKeyTick)) {
            if (hudCursor != null && !hudCursor.getKeyframes().isEmpty()) {
                int lastIdx = hudCursor.getKeyframes().size() - 1;
                ((Keyframe)hudCursor.getKeyframes().get(lastIdx)).getInterpolation().setInterp(Interpolations.CONST);
            }
            this.insertCursorKey(clipCursor, localTick, curTx, curTy);
            if (hudCursor != null) {
                this.insertCursorKey(hudCursor, absTick, curTx, curTy);
            }
            this.lastRecordedCurTx = curTx;
            this.lastRecordedCurTy = curTy;
            this.lastCursorKeyTick = absTick;
            this.cursorWasMoving = false;
        } else if (posChanged) {
            if (absTick > this.lastCursorKeyTick + 0.05f) {
                if (absTick - this.lastCursorKeyTick > 0.5f && !this.cursorWasMoving) {
                    this.insertCursorKey(clipCursor, localTick - 0.05f, this.lastRecordedCurTx, this.lastRecordedCurTy);
                    if (hudCursor != null) {
                        this.insertCursorKey(hudCursor, absTick - 0.05f, this.lastRecordedCurTx, this.lastRecordedCurTy);
                    }
                }
                this.insertCursorKey(clipCursor, localTick, curTx, curTy);
                if (hudCursor != null) {
                    this.insertCursorKey(hudCursor, absTick, curTx, curTy);
                }
                this.lastRecordedCurTx = curTx;
                this.lastRecordedCurTy = curTy;
                this.lastCursorKeyTick = absTick;
                this.cursorWasMoving = true;
            }
        } else if (this.cursorWasMoving) {
            this.insertCursorKey(clipCursor, localTick, curTx, curTy);
            if (hudCursor != null) {
                this.insertCursorKey(hudCursor, absTick, curTx, curTy);
            }
            this.lastRecordedCurTx = curTx;
            this.lastRecordedCurTy = curTy;
            this.lastCursorKeyTick = absTick;
            this.cursorWasMoving = false;
        }
    }

    private void finalizeClip(ReplayKeyframesPovAccess access, int tick) {
        RecordedHudData hud;
        if (this.recordingClip == null) {
            return;
        }
        float localEnd = tick - (Integer)this.recordingClip.tick.get();
        if (!Float.isNaN(this.lastRecordedCurTx) && !Float.isNaN(this.lastCursorKeyTick)) {
            this.insertCursorKey(this.recordingClip.cursorLayout, localEnd, this.lastRecordedCurTx, this.lastRecordedCurTy);
        }
        this.recordingClip.cursorVisible.insert(localEnd, false);
        RecordedHudData recordedHudData = hud = access == null ? null : access.bbsPov$getHud();
        if (hud != null) {
            float absTick = tick;
            if (!Float.isNaN(this.lastRecordedCurTx) && !Float.isNaN(this.lastCursorKeyTick) && absTick > this.lastCursorKeyTick) {
                this.insertCursorKey(hud.cursorLayout, absTick, this.lastRecordedCurTx, this.lastRecordedCurTy);
            }
            if (MinecraftClient.getInstance().currentScreen == null) {
                hud.cursorVisible.insert(absTick, false);
            }
        }
        this.recordingClip.ensureBakingBounds();
        this.recordingClip = null;
        this.lastRecordedCurTx = Float.NaN;
        this.lastRecordedCurTy = Float.NaN;
        this.lastCursorKeyTick = Float.NaN;
    }

    private <T> void recordValue(KeyframeChannel<T> channel, T value, float tick) {
        if (channel == null) {
            return;
        }
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
            return;
        }
        List<Keyframe<T>> keyframes = channel.getKeyframes();
        Keyframe<T> previous = (Keyframe<T>)keyframes.get(keyframes.size() - 1);
        if (!Objects.equals(previous.getValue(), value)) {
            if (tick - previous.getTick() > 1.0f) {
                channel.insert(tick - 1.0f, previous.getValue());
            }
            channel.insert(tick, value);
        }
    }

    private void insertCursorKey(KeyframeChannel<Transform> cursorLayout, float tick, float tx, float ty) {
        if (cursorLayout == null) {
            return;
        }
        Transform transform = new Transform();
        transform.translate.set(tx, ty, 0.0f);
        int index = cursorLayout.insert(tick, transform);
        if (index >= 0 && index < cursorLayout.getKeyframes().size()) {
            ((Keyframe)cursorLayout.getKeyframes().get(index)).getInterpolation().setInterp(Interpolations.LINEAR);
        }
    }
}

