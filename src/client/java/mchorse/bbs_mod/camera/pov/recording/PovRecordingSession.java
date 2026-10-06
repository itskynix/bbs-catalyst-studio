/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.text.Text
 */
package mchorse.bbs_mod.camera.pov.recording;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.bossbar.recording.BossBarRecorder;
import mchorse.bbs_mod.camera.pov.actions.camera.recording.CameraShakeRecorder;
import mchorse.bbs_mod.camera.pov.actions.chat.editor.UIExecutedTextKeyframeFactory;
import mchorse.bbs_mod.camera.pov.actions.chat.recording.ChatRecorder;
import mchorse.bbs_mod.camera.pov.actions.clip.ChatPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiRecorder;
import mchorse.bbs_mod.camera.pov.actions.menu.recording.MenuRecorder;
import mchorse.bbs_mod.camera.pov.actions.particle.recording.ParticleRecorder;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.recording.ScreenEffectRecorder;
import mchorse.bbs_mod.camera.pov.actions.statuseffect.recording.StatusEffectRecorder;
import mchorse.bbs_mod.camera.pov.actions.toast.recording.ToastRecorder;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.List;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

public final class PovRecordingSession {
    private static PovRecordingSession current;
    private final Form recordingForm;
    private Film film;
    private int replayIndex;
    private int lastDispatchedTick = -1;
    private final GuiRecorder gui = new GuiRecorder();
    private final CameraShakeRecorder cameraShake = new CameraShakeRecorder();
    private final MenuRecorder menu = new MenuRecorder();
    private final ParticleRecorder particles = new ParticleRecorder();
    private final BossBarRecorder bossBars = new BossBarRecorder();
    private final ChatRecorder chat = new ChatRecorder();
    private final StatusEffectRecorder statusEffects = new StatusEffectRecorder();
    private final ScreenEffectRecorder screenEffects = new ScreenEffectRecorder();

    private PovRecordingSession(Form recordingForm) {
        this.recordingForm = recordingForm;
        this.gui.reset();
        this.cameraShake.reset();
        this.menu.reset();
        this.particles.reset();
        this.bossBars.reset();
        this.chat.reset();
        this.statusEffects.reset();
        this.screenEffects.reset();
    }

    public static PovRecordingSession start(Recorder recorder, Film film, Form form, int replayIndex, int tick) {
        RecordedPovActions actions;
        ReplayKeyframesPovAccess access;
        Object hud;
        ReplayKeyframes replayKeyframes;
        PovRecordingSession session = new PovRecordingSession(form);
        session.film = film;
        session.replayIndex = replayIndex;
        session.lastDispatchedTick = tick - 1;
        session.particles.arm();
        current = session;
        Replay targetReplay = null;
        if (film != null && film.replays.getList().size() > replayIndex) {
            targetReplay = (Replay)film.replays.getList().get(replayIndex);
        }
        if ((replayKeyframes = recorder.keyframes) instanceof ReplayKeyframesPovAccess && (hud = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHud()) != null) {
            if (PovSettings.isBakeActions()) {
                ((RecordedHudData)hud).ensureStartRecordingBounds(tick, targetReplay != null ? targetReplay.keyframes : null);
            } else {
                ((RecordedHudData)hud).cursorLayout.removeAll();
                ((RecordedHudData)hud).cursorVisible.removeAll();
                ((RecordedHudData)hud).cursorItem.removeAll();
            }
        }
        if (!PovSettings.isBakeAnyActions() && (hud = recorder.keyframes) instanceof ReplayKeyframesPovAccess && (actions = (access = (ReplayKeyframesPovAccess)hud).bbsPov$getActions()) != null) {
            actions.clearAll();
        }
        return session;
    }

    public void recordFrame(Recorder recorder) {
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        ReplayKeyframes replayKeyframes = recorder.keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        int currentTick = recorder.tick;
        if (this.film != null && this.film.replays != null && currentTick > this.lastDispatchedTick) {
            int startRange;
            for (int t = startRange = Math.max(0, this.lastDispatchedTick + 1); t <= currentTick; ++t) {
                PovRecordingSession.dispatchExternalChatMessages(this.film, this.replayIndex, t);
            }
            this.lastDispatchedTick = currentTick;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        RecordedHudData hud = access.bbsPov$getHud();
        if (player != null && hud != null) {
            hud.recording.record(hud, recorder.tick, (PlayerEntity)player);
        }
        RecordedHandData hand = access.bbsPov$getHand();
        if (player != null && hand != null) {
            hand.recording.record(hand, recorder.tick, player, this.recordingForm);
        }
        this.gui.record(access, recorder);
        this.cameraShake.record(access, recorder, player);
        this.menu.record(access, recorder);
        this.particles.record(access, recorder, player);
        this.bossBars.record(access, recorder);
        this.chat.record(access, recorder);
        this.statusEffects.record(access, recorder);
        this.screenEffects.record(access, recorder);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void dispatchExternalChatMessages(Film film, int currentReplayIndex, int targetTick) {
        if (film == null || film.replays == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.inGameHud == null || mc.inGameHud.getChatHud() == null) {
            return;
        }
        List list = film.replays.getList();
        for (int i = 0; i < list.size(); ++i) {
            ReplayKeyframesPovAccess otherAccess;
            RecordedPovActions otherActions;
            ReplayKeyframes replayKeyframes;
            Replay other;
            if (i == currentReplayIndex && currentReplayIndex >= 0 || (other = (Replay)list.get(i)) == null || !((replayKeyframes = other.keyframes) instanceof ReplayKeyframesPovAccess) || (otherActions = (otherAccess = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getActions()) == null) continue;
            int looping = (Integer)other.looping.get();
            int otherTick = looping > 0 ? targetTick % looping : targetTick;
            for (ChatPovActionClip chatClip : otherActions.getClips(ChatPovActionClip.class)) {
                if (chatClip == null || chatClip.executedText == null || chatClip.executedText.isEmpty()) continue;
                float clipStart = ((Integer)chatClip.tick.get()).intValue();
                for (Keyframe kf : chatClip.executedText.getList()) {
                    String[] lines;
                    float globalTick;
                    if (kf == null || kf.getValue() == null || ((String)kf.getValue()).trim().isEmpty() || (int)Math.floor(globalTick = clipStart + kf.getTick()) != otherTick) continue;
                    String val = UIExecutedTextKeyframeFactory.getRawText((String)kf.getValue()).replace("\r", "");
                    for (String line : lines = val.split("\n")) {
                        if (line == null || line.trim().isEmpty()) continue;
                        try {
                            ChatRecorder.setReplayingExternalMessage(true);
                            mc.inGameHud.getChatHud().addMessage((Text)Text.literal((String)line), null, null);
                        }
                        finally {
                            ChatRecorder.setReplayingExternalMessage(false);
                        }
                    }
                }
            }
        }
    }

    public static void sampleCursor(float tickDelta) {
        PovRecordingSession session = current;
        Recorder recorder = BBSModClient.getFilms().getRecorder();
        if (session == null || recorder == null || recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        ReplayKeyframes replayKeyframes = recorder.keyframes;
        if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        session.gui.sampleCursor(access, recorder, tickDelta);
        session.menu.sampleCursor(access, recorder, tickDelta);
        session.chat.sampleCursor(access, recorder, tickDelta);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void finish(Recorder recorder) {
        try {
            ReplayKeyframes replayKeyframes = recorder.keyframes;
            if (!(replayKeyframes instanceof ReplayKeyframesPovAccess)) {
                return;
            }
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            this.gui.finish(access, recorder.tick);
            this.cameraShake.finish(access, recorder.tick);
            this.menu.finish(access, recorder.tick);
            this.particles.finish(recorder.tick);
            this.bossBars.finish(access, recorder.tick);
            this.chat.finish(access, recorder.tick);
            this.statusEffects.finish(access, recorder.tick);
            this.screenEffects.finish(access, recorder.tick);
            ToastRecorder.finish(access, recorder.tick);
            RecordedHudData hud = access.bbsPov$getHud();
            if (hud != null) {
                hud.ensureEndRecordingBounds(recorder.tick);
            }
        }
        finally {
            if (current == this) {
                current = null;
            }
        }
    }

    public static PovRecordingSession getCurrent() {
        return current;
    }

    public Film getFilm() {
        return this.film;
    }

    public int getReplayIndex() {
        return this.replayIndex;
    }

    public Replay getReplay() {
        if (this.film != null && this.film.replays != null && this.replayIndex >= 0 && this.film.replays.getList().size() > this.replayIndex) {
            return (Replay)this.film.replays.getList().get(this.replayIndex);
        }
        return null;
    }
}

