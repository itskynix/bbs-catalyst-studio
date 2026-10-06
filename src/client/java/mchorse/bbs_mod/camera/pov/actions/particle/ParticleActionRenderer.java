/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.film.BaseFilmController
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.utils.clips.Clip
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.particle.ItemStackParticleEffect
 *  net.minecraft.particle.ParticleEffect
 *  net.minecraft.particle.ParticleTypes
 *  net.minecraft.util.UseAction
 *  net.minecraft.util.math.Vec3d
 */
package mchorse.bbs_mod.camera.pov.actions.particle;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.clip.ParticleEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.particle.ParticleEffects;
import mchorse.bbs_mod.camera.pov.actions.particle.ParticleSpaces;
import mchorse.bbs_mod.camera.pov.actions.particle.recording.ParticleRecorder;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.FilmsPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;

public final class ParticleActionRenderer {
    private static final Random RANDOM = new Random();
    private static int lastPlayingFilmTick = Integer.MIN_VALUE;

    private ParticleActionRenderer() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void tick() {
        Map entities;
        boolean playing;
        int cursor;
        Film film;
        if (UIPovHandEditor.isActive() || ParticleRecorder.isArmed()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.particleManager == null) {
            return;
        }
        BaseFilmController world = ParticleActionRenderer.activeWorldController();
        UIFilmPanel panel = PovViewportMetrics.resolveFilmPanel();
        if (panel == null) {
            panel = PovReplaySettings.getFilmPanel();
        }
        if (world != null) {
            film = world.film;
            cursor = world.getTick();
            playing = !world.paused;
            entities = world.getEntities();
        } else if (panel != null && panel.getData() != null && panel.getController() != null) {
            film = (Film)panel.getData();
            cursor = panel.getCursor();
            playing = panel.getRunner() != null && panel.getRunner().isRunning();
            entities = panel.getController().getEntities();
        } else {
            lastPlayingFilmTick = Integer.MIN_VALUE;
            return;
        }
        if (film == null || entities == null) {
            return;
        }
        if (playing) {
            if (cursor == lastPlayingFilmTick) {
                return;
            }
            lastPlayingFilmTick = cursor;
        } else {
            lastPlayingFilmTick = Integer.MIN_VALUE;
        }
        List replays = film.replays.getList();
        ParticleRecorder.suspendCapture();
        try {
            for (int replayIndex = 0; replayIndex < replays.size(); ++replayIndex) {
                float height;
                float width;
                Vec3d origin;
                ReplayKeyframesPovAccess access;
                RecordedPovActions actions;
                Replay replay = (Replay)replays.get(replayIndex);
                ReplayKeyframes replayKeyframes = replay.keyframes;
                if (!(replayKeyframes instanceof ReplayKeyframesPovAccess) || (actions = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getActions()) == null) continue;
                IEntity entity = (IEntity)entities.get(replay.getId());
                if (entity != null) {
                    origin = ParticleSpaces.lerpPos(entity, 1.0f);
                    width = ParticleSpaces.width(entity);
                    height = ParticleSpaces.height(entity);
                } else if (client.player != null) {
                    origin = client.player.getPos();
                    width = client.player.getWidth();
                    height = client.player.getHeight();
                } else {
                    if (client.cameraEntity == null) continue;
                    origin = client.cameraEntity.getPos();
                    width = client.cameraEntity.getWidth();
                    height = client.cameraEntity.getHeight();
                }
                float replayTick = replay.getTick(cursor);
                boolean particleEmitted = false;
                RecordedHandData handData = access.bbsPov$getHand();
                if (handData != null) {
                    int wholeTick = (int)Math.floor(replayTick);
                    int active = (Integer)handData.activeHand.interpolate((float)wholeTick, 0);
                    boolean showParticles = (Boolean)handData.showUseParticles.interpolate(replayTick, true);
                    if (active != 0 && showParticles) {
                        ParticleActionRenderer.emitEatingParticles(client, origin, width, height, entity, replay, replayTick, handData);
                        particleEmitted = true;
                    }
                }
                for (Clip clip : actions.get()) {
                    ScreenEffectPovActionClip screenClip;
                    ParticleEffectPovActionClip particleClip;
                    if (clip instanceof ParticleEffectPovActionClip && (particleClip = (ParticleEffectPovActionClip)clip).isActive(replayTick)) {
                        if (particleEmitted) continue;
                        ParticleActionRenderer.emit(client, origin, width, height, particleClip, entity, replay, replayTick);
                        particleEmitted = true;
                        continue;
                    }
                    if (!(clip instanceof ScreenEffectPovActionClip) || !(screenClip = (ScreenEffectPovActionClip)clip).isActive(replayTick) || !screenClip.hasEffect("totem")) continue;
                    ParticleActionRenderer.emitTotem(client, origin, width, height, screenClip, replayTick - (float)((Integer)screenClip.tick.get()).intValue());
                }
            }
        }
        finally {
            ParticleRecorder.resumeCapture();
        }
    }

    private static void emitTotem(MinecraftClient client, Vec3d origin, float width, float height, ScreenEffectPovActionClip clip, float localTick) {
        boolean particles;
        boolean bl = particles = clip.totemParticles.isEmpty() ? false : (Boolean)clip.totemParticles.interpolate(localTick);
        if (!particles) {
            clip.lastTotemParticleTick = Integer.MIN_VALUE;
            return;
        }
        int curTick = (int)localTick;
        if (curTick < 0 || clip.lastTotemParticleTick == curTick) {
            return;
        }
        clip.lastTotemParticleTick = curTick;
        for (int i = 0; i < 16; ++i) {
            double f;
            double e;
            double d = RANDOM.nextFloat() * 2.0f - 1.0f;
            if (!(d * d + (e = (double)(RANDOM.nextFloat() * 2.0f - 1.0f)) * e + (f = (double)(RANDOM.nextFloat() * 2.0f - 1.0f)) * f <= 1.0)) continue;
            double px = origin.x + d / 4.0 * (double)width;
            double py = origin.y + (0.5 + e / 4.0) * (double)height;
            double pz = origin.z + f / 4.0 * (double)width;
            client.particleManager.addParticle((ParticleEffect)ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, d, e + 0.2, f);
        }
    }

    private static void emitEatingParticles(MinecraftClient client, Vec3d origin, float width, float height, IEntity entity, Replay replay, float replayTick, RecordedHandData handData) {
        float yaw;
        int wholeTick = (int)Math.floor(replayTick);
        ItemStack itemToUse = (ItemStack)handData.activeItem.interpolate((float)wholeTick, ItemStack.EMPTY);
        if ((itemToUse == null || itemToUse.isEmpty() || itemToUse.isOf(Items.AIR)) && replay != null && replay.keyframes != null) {
            ItemStack held = replay.keyframes.getMainHandStack(replayTick);
            if (held == null || held.isEmpty() || held.isOf(Items.AIR)) {
                held = (ItemStack)replay.keyframes.offHand.interpolate(replayTick, ItemStack.EMPTY);
            }
            if (held != null && !held.isEmpty() && !held.isOf(Items.AIR)) {
                itemToUse = held;
            }
        }
        if (itemToUse == null || itemToUse.isEmpty() || itemToUse.isOf(Items.AIR)) {
            return;
        }
        UseAction useAction = itemToUse.getUseAction();
        if (useAction != UseAction.EAT && useAction != UseAction.DRINK) {
            return;
        }
        ItemStackParticleEffect effect = new ItemStackParticleEffect(ParticleTypes.ITEM, itemToUse);
        yaw = entity != null ? entity.getHeadYaw() : (replay != null && replay.keyframes != null ? (float)((Double)replay.keyframes.yaw.interpolate(replayTick, 0.0)).doubleValue() : 0.0f);
        float pitch = entity != null ? entity.getPitch() : (replay != null && replay.keyframes != null ? (float)((Double)replay.keyframes.pitch.interpolate(replayTick, 0.0)).doubleValue() : 0.0f);
        float radPitch = -pitch * ((float)Math.PI / 180);
        float radYaw = -yaw * ((float)Math.PI / 180);
        int count = 1 + RANDOM.nextInt(3);
        for (int i = 0; i < count; ++i) {
            Vec3d vel = new Vec3d(((double)RANDOM.nextFloat() - 0.5) * 0.1, (double)RANDOM.nextFloat() * 0.1 + 0.1, 0.0).rotateX(radPitch).rotateY(radYaw);
            double d = (double)(-RANDOM.nextFloat()) * 0.4 - 0.2;
            Vec3d offset = new Vec3d(((double)RANDOM.nextFloat() - 0.5) * 0.3, d, 0.6).rotateX(radPitch).rotateY(radYaw);
            Vec3d pos = origin.add(0.0, (double)height * 0.85, 0.0).add(offset);
            client.particleManager.addParticle((ParticleEffect)effect, pos.x, pos.y, pos.z, vel.x, vel.y + 0.05, vel.z);
        }
    }

    private static void emit(MinecraftClient client, Vec3d origin, float width, float height, ParticleEffectPovActionClip clip, IEntity entity, Replay replay, float replayTick) {
        double vz;
        double vy;
        double vx;
        boolean status;
        ParticleEffect effect = ParticleEffects.fromClip(clip);
        if (effect == null) {
            return;
        }
        if (effect.getType() == ParticleTypes.ITEM) {
            float yaw;
            int wholeTick;
            ItemStack active;
            ReplayKeyframesPovAccess access;
            RecordedHandData handData;
            ReplayKeyframes replayKeyframes;
            ItemStack itemToUse = null;
            if (replay != null && (replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess && (handData = (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getHand()) != null && (active = (ItemStack)handData.activeItem.interpolate((float)(wholeTick = (int)Math.floor(replayTick)), ItemStack.EMPTY)) != null && !active.isEmpty() && !active.isOf(Items.AIR)) {
                itemToUse = active;
            }
            if (itemToUse == null || itemToUse.isEmpty() || itemToUse.isOf(Items.AIR)) {
                itemToUse = clip.extraItem();
            }
            if ((itemToUse == null || itemToUse.isEmpty() || itemToUse.isOf(Items.AIR)) && replay != null && replay.keyframes != null) {
                ItemStack held = replay.keyframes.getMainHandStack(replayTick);
                if (held == null || held.isEmpty() || held.isOf(Items.AIR)) {
                    held = (ItemStack)replay.keyframes.offHand.interpolate(replayTick, ItemStack.EMPTY);
                }
                if (held != null && !held.isEmpty() && !held.isOf(Items.AIR)) {
                    itemToUse = held;
                }
            }
            if (itemToUse == null || itemToUse.isEmpty() || itemToUse.isOf(Items.AIR)) {
                itemToUse = new ItemStack((ItemConvertible)Items.APPLE);
            }
            effect = new ItemStackParticleEffect(ParticleTypes.ITEM, itemToUse);
            yaw = entity != null ? entity.getHeadYaw() : (replay != null ? (float)((Double)replay.keyframes.yaw.interpolate(replayTick, 0.0)).doubleValue() : 0.0f);
            float pitch = entity != null ? entity.getPitch() : (replay != null ? (float)((Double)replay.keyframes.pitch.interpolate(replayTick, 0.0)).doubleValue() : 0.0f);
            float radPitch = -pitch * ((float)Math.PI / 180);
            float radYaw = -yaw * ((float)Math.PI / 180);
            int count = 1 + RANDOM.nextInt(3);
            for (int i = 0; i < count; ++i) {
                Vec3d vel = new Vec3d(((double)RANDOM.nextFloat() - 0.5) * 0.1, (double)RANDOM.nextFloat() * 0.1 + 0.1, 0.0).rotateX(radPitch).rotateY(radYaw);
                double d = (double)(-RANDOM.nextFloat()) * 0.4 - 0.2;
                Vec3d offset = new Vec3d(((double)RANDOM.nextFloat() - 0.5) * 0.3, d, 0.6).rotateX(radPitch).rotateY(radYaw);
                Vec3d pos = origin.add(0.0, (double)height * 0.85, 0.0).add(offset);
                client.particleManager.addParticle(effect, pos.x, pos.y, pos.z, vel.x, vel.y + 0.05, vel.z);
            }
            return;
        }
        boolean bl = status = effect.getType() == ParticleTypes.ENTITY_EFFECT || effect.getType() == ParticleTypes.AMBIENT_ENTITY_EFFECT;
        if (status && !RANDOM.nextBoolean()) {
            return;
        }
        if (effect.getType() == ParticleTypes.BLOCK) {
            Vec3d pos = origin.add((RANDOM.nextDouble() - 0.5) * (double)width, 0.1, (RANDOM.nextDouble() - 0.5) * (double)width);
            float yaw = entity != null ? entity.getHeadYaw() : (replay != null && replay.keyframes != null ? (float)((Double)replay.keyframes.yaw.interpolate(replayTick, 0.0)).doubleValue() : 0.0f);
            float radYaw = (float)Math.toRadians(yaw);
            double vx2 = Math.sin(radYaw) * 0.15 + (RANDOM.nextDouble() - 0.5) * 0.1;
            double vy2 = 0.15 + RANDOM.nextDouble() * 0.1;
            double vz2 = -Math.cos(radYaw) * 0.15 + (RANDOM.nextDouble() - 0.5) * 0.1;
            client.particleManager.addParticle(effect, pos.x, pos.y, pos.z, vx2, vy2, vz2);
            return;
        }
        Vec3d pos = ParticleSpaces.pointInActorAabb(origin, width, height, RANDOM);
        if (status) {
            vx = ((Float)clip.dustR.get()).floatValue();
            vy = ((Float)clip.dustG.get()).floatValue();
            vz = ((Float)clip.dustB.get()).floatValue();
        } else {
            vx = (RANDOM.nextDouble() - 0.5) * 0.15;
            vy = RANDOM.nextDouble() * 0.2;
            vz = (RANDOM.nextDouble() - 0.5) * 0.15;
        }
        client.particleManager.addParticle(effect, pos.x, pos.y, pos.z, vx, vy, vz);
    }

    public static boolean hidesLivePlayerParticles() {
        if (ParticleRecorder.isArmed() || ParticleRecorder.isPlaybackEmit()) {
            return false;
        }
        if (ParticleActionRenderer.activeWorldController() != null) {
            return true;
        }
        UIFilmPanel panel = PovViewportMetrics.resolveFilmPanel();
        if (panel == null) {
            panel = PovReplaySettings.getFilmPanel();
        }
        return panel != null && panel.getData() != null;
    }

    private static BaseFilmController activeWorldController() {
        List<BaseFilmController> controllers = ((FilmsPovAccess)BBSModClient.getFilms()).bbsPov$getControllers();
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }
        for (int i = controllers.size() - 1; i >= 0; --i) {
            BaseFilmController controller = controllers.get(i);
            if (controller == null || controller.hasFinished()) continue;
            return controller;
        }
        return null;
    }
}

