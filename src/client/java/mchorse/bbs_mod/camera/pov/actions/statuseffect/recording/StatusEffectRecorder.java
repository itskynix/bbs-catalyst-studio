/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.statuseffect.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.StatusEffectsPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.statuseffect.StatusEffectEntry;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import mchorse.bbs_mod.film.Recorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class StatusEffectRecorder {
    private StatusEffectsPovActionClip recordingClip;
    private Set<String> currentEffectIds = new HashSet<String>();

    public void reset() {
        this.recordingClip = null;
        this.currentEffectIds.clear();
    }

    public void finish(ReplayKeyframesPovAccess access, int tick) {
        this.finalizeClip(tick);
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder) {
        if (!PovSettings.isBakeStatusEffects()) {
            return;
        }
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            this.finalizeClip(recorder.tick);
            return;
        }
        Collection<StatusEffectInstance> active = player.getStatusEffects();
        if (active == null || active.isEmpty()) {
            this.finalizeClip(recorder.tick);
            return;
        }
        HashSet<String> newEffectIds = new HashSet<String>();
        for (StatusEffectInstance inst : active) {
            Identifier identifier = Registries.STATUS_EFFECT.getId(inst.getEffectType());
            if (identifier == null) continue;
            newEffectIds.add(identifier.toString());
        }
        if (this.recordingClip != null && !this.currentEffectIds.equals(newEffectIds)) {
            this.finalizeClip(recorder.tick);
        }
        if (this.recordingClip == null) {
            this.recordingClip = (StatusEffectsPovActionClip)access.bbsPov$getActions().add(PovActionType.STATUS_EFFECTS, recorder.tick, 1);
            this.currentEffectIds = newEffectIds;
            ArrayList<StatusEffectInstance> sortedActive = new ArrayList<StatusEffectInstance>(active);
            sortedActive.sort(null);
            for (StatusEffectInstance statusEffectInstance : sortedActive) {
                Identifier id = Registries.STATUS_EFFECT.getId(statusEffectInstance.getEffectType());
                if (id == null) continue;
                boolean unlimited = statusEffectInstance.isInfinite();
                int durationSeconds = unlimited ? 100 : Math.max(1, statusEffectInstance.getDuration() / 20);
                int amp = statusEffectInstance.getAmplifier();
                StatusEffectEntry entry = new StatusEffectEntry(id.toString(), unlimited, durationSeconds, amp);
                this.recordingClip.addEffect(entry);
            }
        }
        float localTick = recorder.tick - (Integer)this.recordingClip.tick.get();
        this.recordingClip.duration.set(Math.max(1, (int)localTick + 1));
    }

    private void finalizeClip(int tick) {
        if (this.recordingClip == null) {
            return;
        }
        this.recordingClip.duration.set(Math.max(1, tick - (Integer)this.recordingClip.tick.get()));
        this.recordingClip = null;
        this.currentEffectIds.clear();
    }
}

