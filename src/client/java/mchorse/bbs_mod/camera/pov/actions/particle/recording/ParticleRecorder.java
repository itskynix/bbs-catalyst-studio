/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  net.minecraft.block.BlockState
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.particle.BlockStateParticleEffect
 *  net.minecraft.particle.DustParticleEffect
 *  net.minecraft.particle.ParticleEffect
 *  net.minecraft.particle.ParticleType
 *  net.minecraft.particle.ParticleTypes
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 *  org.joml.Vector3f
 */
package mchorse.bbs_mod.camera.pov.actions.particle.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.ParticleEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.film.Recorder;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public final class ParticleRecorder {
    private static final int BURST_GRACE = 2;
    private static final double PLAYER_RANGE = 4.5;
    private static ParticleRecorder current;
    private static int captureSuspended;
    private final List<Spawn> pending = new ArrayList<Spawn>();
    private OpenEffect openClip;
    private ReplayKeyframesPovAccess access;
    private Recorder recorder;

    public void reset() {
        this.pending.clear();
        this.openClip = null;
        this.access = null;
        this.recorder = null;
        if (current == this) {
            current = null;
        }
    }

    public void arm() {
        current = this;
    }

    public void finish(int tick) {
        if (this.openClip != null) {
            this.openClip.clip.duration.set(Math.max(1, this.openClip.lastTick - (Integer)this.openClip.clip.tick.get() + 1));
            this.openClip = null;
        }
        this.pending.clear();
        if (current == this) {
            current = null;
        }
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder, ClientPlayerEntity player) {
        int color;
        BlockState stepping;
        Object effectKey;
        if (!PovSettings.isBakeParticles()) {
            this.pending.clear();
            return;
        }
        this.access = access;
        this.recorder = recorder;
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            this.pending.clear();
            return;
        }
        current = this;
        int tick = recorder.tick;
        Spawn validSpawn = null;
        for (Spawn spawn : this.pending) {
            if (player != null && !(player.squaredDistanceTo(spawn.x, spawn.y, spawn.z) <= 20.25)) continue;
            validSpawn = spawn;
            break;
        }
        this.pending.clear();
        if (validSpawn != null) {
            Spawn finalSpawn = validSpawn;
            effectKey = ParticleRecorder.effectKey(finalSpawn.parameters);
            this.activateEffect(tick, (String)effectKey, clip -> {
                String id = Registries.PARTICLE_TYPE.getId(finalSpawn.parameters.getType()).toString();
                clip.particle.set(id);
                ParticleRecorder.fillExtra(clip, finalSpawn.parameters);
            });
            return;
        }
        if (player != null && player.isSprinting() && player.isOnGround() && (stepping = player.getSteppingBlockState()) != null && !stepping.isAir()) {
            Identifier blockId = Registries.BLOCK.getId(stepping.getBlock());
            String effectKey2 = "sprint:block:" + String.valueOf(blockId);
            this.activateEffect(tick, effectKey2, clip -> {
                clip.particle.set(Registries.PARTICLE_TYPE.getId(ParticleTypes.BLOCK).toString());
                clip.blockId.set(blockId.toString());
            });
            return;
        }
        if (player != null && (color = ParticleRecorder.visibleStatusColor(player)) >= 0) {
            effectKey = "status:entity_effect:" + color;
            this.activateEffect(tick, (String)effectKey, clip -> {
                clip.particle.set(Registries.PARTICLE_TYPE.getId(ParticleTypes.ENTITY_EFFECT).toString());
                clip.dustR.set(Float.valueOf((float)(color >> 16 & 0xFF) / 255.0f));
                clip.dustG.set(Float.valueOf((float)(color >> 8 & 0xFF) / 255.0f));
                clip.dustB.set(Float.valueOf((float)(color & 0xFF) / 255.0f));
            });
            return;
        }
        if (this.openClip != null && tick - this.openClip.lastTick > 2) {
            this.openClip.clip.duration.set(Math.max(1, this.openClip.lastTick - (Integer)this.openClip.clip.tick.get() + 1));
            this.openClip = null;
        }
    }

    private void activateEffect(int tick, String key, Consumer<ParticleEffectPovActionClip> configurator) {
        if (this.access == null) {
            return;
        }
        if (this.openClip != null) {
            if (this.openClip.key.equals(key)) {
                this.openClip.clip.duration.set(Math.max(1, tick - (Integer)this.openClip.clip.tick.get() + 1));
                this.openClip.lastTick = tick;
                return;
            }
            this.openClip.clip.duration.set(Math.max(1, this.openClip.lastTick - (Integer)this.openClip.clip.tick.get() + 1));
            this.openClip = null;
        }
        ParticleEffectPovActionClip clip = (ParticleEffectPovActionClip)this.access.bbsPov$getActions().add(PovActionType.PARTICLE_EFFECT, tick, 1);
        configurator.accept(clip);
        this.openClip = new OpenEffect(clip, key, tick);
    }

    public static boolean isArmed() {
        return current != null;
    }

    public static boolean isPlaybackEmit() {
        return captureSuspended > 0;
    }

    public static void suspendCapture() {
        ++captureSuspended;
    }

    public static void resumeCapture() {
        captureSuspended = Math.max(0, captureSuspended - 1);
    }

    public static void capture(ParticleEffect parameters, double x, double y, double z) {
        if (captureSuspended > 0) {
            return;
        }
        ParticleRecorder recorder = current;
        if (recorder == null || !PovSettings.isBakeParticles() || parameters == null) {
            return;
        }
        ParticleType type = parameters.getType();
        if (type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.AMBIENT_ENTITY_EFFECT || type == ParticleTypes.EFFECT || type == ParticleTypes.TOTEM_OF_UNDYING || !ParticleRecorder.isBurstEffect(type)) {
            return;
        }
        recorder.pending.add(new Spawn(parameters, x, y, z));
    }

    private static String effectKey(ParticleEffect effect) {
        String id = Registries.PARTICLE_TYPE.getId(effect.getType()).toString();
        if (effect instanceof BlockStateParticleEffect) {
            BlockStateParticleEffect block = (BlockStateParticleEffect)effect;
            return id + ":" + String.valueOf(Registries.BLOCK.getId(block.getBlockState().getBlock()));
        }
        return id;
    }

    private static int visibleStatusColor(ClientPlayerEntity player) {
        int color = -1;
        for (StatusEffectInstance instance : player.getStatusEffects()) {
            if (instance == null || !instance.shouldShowParticles()) continue;
            color = instance.getEffectType().getColor();
        }
        return color;
    }

    private static boolean isBurstEffect(ParticleType<?> type) {
        return type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_MARKER || type == ParticleTypes.FALLING_DUST || type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT || type == ParticleTypes.SWEEP_ATTACK || type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.HEART || type == ParticleTypes.NOTE || type == ParticleTypes.SOUL || type == ParticleTypes.WITCH || type == ParticleTypes.INSTANT_EFFECT || type == ParticleTypes.SPLASH || type == ParticleTypes.BUBBLE_POP || type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.ANGRY_VILLAGER || type == ParticleTypes.HAPPY_VILLAGER || type == ParticleTypes.POOF || type == ParticleTypes.FIREWORK || type == ParticleTypes.FLASH;
    }

    private static void fillExtra(ParticleEffectPovActionClip clip, ParticleEffect parameters) {
        if (parameters instanceof BlockStateParticleEffect) {
            BlockStateParticleEffect block = (BlockStateParticleEffect)parameters;
            Identifier blockId = Registries.BLOCK.getId(block.getBlockState().getBlock());
            clip.blockId.set(blockId.toString());
        } else if (parameters instanceof DustParticleEffect) {
            DustParticleEffect dust = (DustParticleEffect)parameters;
            Vector3f color = dust.getColor();
            clip.dustR.set(Float.valueOf(color.x()));
            clip.dustG.set(Float.valueOf(color.y()));
            clip.dustB.set(Float.valueOf(color.z()));
            clip.dustScale.set(Float.valueOf(dust.getScale()));
        }
    }

    private static final class OpenEffect {
        final ParticleEffectPovActionClip clip;
        final String key;
        int lastTick;

        OpenEffect(ParticleEffectPovActionClip clip, String key, int tick) {
            this.clip = clip;
            this.key = key;
            this.lastTick = tick;
        }
    }

    private static final class Spawn {
        final ParticleEffect parameters;
        final double x;
        final double y;
        final double z;

        Spawn(ParticleEffect parameters, double x, double y, double z) {
            this.parameters = parameters;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}

