/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.hud.BossBarHud
 *  net.minecraft.client.gui.hud.ClientBossBar
 *  net.minecraft.entity.boss.BossBar$Color
 */
package mchorse.bbs_mod.camera.pov.actions.bossbar.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarLooks;
import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.clip.BossBarPovActionClip;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.BossBarHudPovAccess;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;

public final class BossBarRecorder {
    private static final int MAX_BOSSBARS = 9;
    private final Map<UUID, OpenBossBar> recordingClips = new LinkedHashMap<UUID, OpenBossBar>();

    public void reset() {
        this.recordingClips.clear();
    }

    public void finish(ReplayKeyframesPovAccess access, int tick) {
        for (OpenBossBar open : this.recordingClips.values()) {
            this.finalizeClip(open, tick);
        }
        this.recordingClips.clear();
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder) {
        BossBarHud hud;
        if (!PovSettings.isBakeBossBars()) {
            return;
        }
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        BossBarHud bossBarHud = hud = client.inGameHud == null ? null : client.inGameHud.getBossBarHud();
        if (!(hud instanceof BossBarHudPovAccess)) {
            this.finish(access, recorder.tick);
            return;
        }
        BossBarHudPovAccess barAccess = (BossBarHudPovAccess)hud;
        Map<UUID, ClientBossBar> bars = barAccess.bbsPov$getBossBars();
        if (bars == null || bars.isEmpty()) {
            this.finish(access, recorder.tick);
            return;
        }
        LinkedHashSet<UUID> visibleNow = new LinkedHashSet<UUID>();
        int count = 0;
        int baseLayer = PovActionType.BOSS_BARS.seedLayer();
        for (Map.Entry<UUID, ClientBossBar> entry : bars.entrySet()) {
            if (count >= 9) break;
            UUID uuid = entry.getKey();
            ClientBossBar bar = entry.getValue();
            if (uuid == null || bar == null) continue;
            visibleNow.add(uuid);
            ++count;
            String type = BossBarRecorder.inferType(bar);
            OpenBossBar open = this.recordingClips.get(uuid);
            if (open != null && !Objects.equals(open.currentType, type)) {
                this.finalizeClip(open, recorder.tick);
                this.recordingClips.remove(uuid);
                open = null;
            }
            if (open == null) {
                int targetLayer = this.findAvailableLayer(baseLayer, access.bbsPov$getActions(), recorder.tick);
                BossBarPovActionClip clip = (BossBarPovActionClip)access.bbsPov$getActions().add(PovActionType.BOSS_BARS, recorder.tick, 1);
                clip.layer.set(targetLayer);
                clip.state.insert(0.0f, type);
                open = new OpenBossBar(uuid, clip, targetLayer, type);
                this.recordingClips.put(uuid, open);
            }
            float localTick = recorder.tick - (Integer)open.clip.tick.get();
            open.clip.duration.set(Math.max(1, (int)localTick + 1));
            this.recordValue(open.clip.state, type, localTick);
            this.recordValue(open.clip.name, bar.getName().getString(), localTick);
            this.recordValue(open.clip.percent, Float.valueOf(BossBarRecorder.clamp(bar.getPercent())), localTick);
            this.recordValue(open.clip.color, BossBarLooks.colorId(bar.getColor()), localTick);
            this.recordValue(open.clip.style, BossBarLooks.styleId(bar.getStyle()), localTick);
        }
        Iterator<Map.Entry<UUID, OpenBossBar>> iterator = this.recordingClips.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, OpenBossBar> entry = iterator.next();
            if (visibleNow.contains(entry.getKey())) continue;
            this.finalizeClip(entry.getValue(), recorder.tick);
            iterator.remove();
        }
    }

    private int findAvailableLayer(int baseLayer, RecordedPovActions actions, int tick) {
        boolean[] occupied = new boolean[9];
        for (OpenBossBar open : this.recordingClips.values()) {
            int offset = open.layer - baseLayer;
            if (offset < 0 || offset >= 9) continue;
            occupied[offset] = true;
        }
        if (actions != null) {
            for (Clip c : actions.get()) {
                int offset;
                if (!(c instanceof BossBarPovActionClip)) continue;
                BossBarPovActionClip barClip = (BossBarPovActionClip)c;
                int start = (Integer)barClip.tick.get();
                int end = start + (Integer)barClip.duration.get();
                if (tick < start || tick >= end || (offset = (Integer)barClip.layer.get() - baseLayer) < 0 || offset >= 9) continue;
                occupied[offset] = true;
            }
        }
        for (int i = 0; i < 9; ++i) {
            if (occupied[i]) continue;
            return baseLayer + i;
        }
        return baseLayer;
    }

    private void finalizeClip(OpenBossBar open, int tick) {
        if (open == null || open.clip == null) {
            return;
        }
        open.clip.duration.set(Math.max(1, tick - (Integer)open.clip.tick.get()));
        open.clip.ensureBakingBounds();
    }

    private static String inferType(ClientBossBar bar) {
        String name;
        String string = name = bar.getName() == null ? "" : bar.getName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("raid")) {
            return BossBarTypeEntry.RAID.id;
        }
        if (name.contains("wither")) {
            return BossBarTypeEntry.WITHER.id;
        }
        if (name.contains("dragon")) {
            return BossBarTypeEntry.DRAGON.id;
        }
        BossBar.Color color = bar.getColor();
        if (color == BossBar.Color.RED) {
            return BossBarTypeEntry.RAID.id;
        }
        if (color == BossBar.Color.PURPLE) {
            return BossBarTypeEntry.WITHER.id;
        }
        return BossBarTypeEntry.DRAGON.id;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
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

    private static final class OpenBossBar {
        final UUID uuid;
        final BossBarPovActionClip clip;
        final int layer;
        final String currentType;

        OpenBossBar(UUID uuid, BossBarPovActionClip clip, int layer, String currentType) {
            this.uuid = uuid;
            this.clip = clip;
            this.layer = layer;
            this.currentType = currentType;
        }
    }
}

