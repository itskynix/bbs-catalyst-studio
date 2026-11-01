package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.MinecraftSoundCapture;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.audio.occlusion.AudioOcclusionManager;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FrozenFilmController;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.AudioForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.InverseViewRotation;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTSourceSpatialize;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AudioFormRenderer extends FormRenderer<AudioForm> implements ITickable {
    private static final Set<AudioFormRenderer> ACTIVE_RENDERERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, AudioFormRenderer> ENTITY_SLOT_OWNERS = new ConcurrentHashMap<>();

    /**
     * Maximum idle time (ms) without tick/render for Film Editor playback
     * so timeline pause stops audio immediately.
     */
    private static final long FILM_MAX_IDLE_MS = 150L;

    private SoundPlayer activeSoundInstance;
    private Link currentSound;
    private int lowPassFilterId = -1;

    private IEntity lastEntity;
    private PlaybackState lastPlaybackState = PlaybackState.STOPPED;
    private Vec3d lastWorldPos;
    private Vec3d lastCameraPos;
    private final Matrix3f lastCameraRotation = new Matrix3f().identity();
    private long lastWorldRenderTimeMs;
    private long lastActiveTimeMs;
    private boolean lastLoopState;

    private enum PlaybackState {
        STOPPED,
        PLAYING
    }

    public AudioFormRenderer(AudioForm form) {
        super(form);
    }

    /**
     * Global lifecycle watchdog invoked every client tick from
     * SoundManager.update().
     * Guarantees that paused timelines, hidden forms, replaced forms, or stopped
     * scenes stop their OpenAL source cleanly without leaking hardware channels.
     */
    public static void updateActiveRenderers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        if (mc.world == null || mc.isPaused()) {
            stopAll();
            return;
        }

        Iterator<AudioFormRenderer> it = ACTIVE_RENDERERS.iterator();

        while (it.hasNext()) {
            AudioFormRenderer renderer = it.next();

            if (renderer.activeSoundInstance == null) {
                it.remove();
                continue;
            }

            /* Fast 150ms watchdog for instant timeline pause / menu close */
            if (now - renderer.lastActiveTimeMs > FILM_MAX_IDLE_MS) {
                renderer.stop();
                continue;
            }

            PlaybackState state = renderer.resolvePlaybackState(renderer.lastEntity);

            if (state == PlaybackState.STOPPED) {
                renderer.stop();
            }
        }
    }

    /**
     * Immediately stops and releases all active AudioForm OpenAL sources.
     */
    public static void stopAll() {
        for (AudioFormRenderer renderer : ACTIVE_RENDERERS) {
            renderer.stopInternal(false);
        }

        ACTIVE_RENDERERS.clear();
        ENTITY_SLOT_OWNERS.clear();
    }

    /**
     * Stops playback and frees the OpenAL hardware source channel immediately.
     */
    public void stop() {
        this.stopInternal(true);
    }

    private void stopInternal(boolean removeFromSet) {
        if (this.activeSoundInstance != null) {
            try {
                int source = this.activeSoundInstance.getSource();

                if (source > 0 && this.lowPassFilterId > 0) {
                    try {
                        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                    } catch (Throwable ignored) {
                    }
                }

                this.activeSoundInstance.stop();
                this.activeSoundInstance.delete();
            } catch (Throwable ignored) {
            }

            if (BBSModClient.getSounds() != null) {
                BBSModClient.getSounds().getPlayers().remove(this.activeSoundInstance);
            }

            this.activeSoundInstance = null;
        }

        if (this.lowPassFilterId > 0) {
            try {
                EXTEfx.alDeleteFilters(this.lowPassFilterId);
            } catch (Throwable ignored) {
            }

            this.lowPassFilterId = -1;
        }

        this.currentSound = null;
        this.lastPlaybackState = PlaybackState.STOPPED;

        if (removeFromSet) {
            ACTIVE_RENDERERS.remove(this);
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2) {
        int x = (x1 + x2) / 2;
        int y = (y1 + y2) / 2;

        context.batcher.icon(AudioForm.AUDIO_ICON, Colors.WHITE, x, y, 0.5F, 0.5F);
    }

    @Override
    protected void render3D(FormRenderingContext context) {
        if (context.ui) {
            return;
        }

        if (context.modelRenderer) {
            /* Form Editor 3D viewport: stop playback and render visual helper */
            this.stop();

            if (!context.isPicking() && !BBSRendering.isIrisShadowPass()) {
                this.renderEditorVisuals(context.stack, (float) this.form.getRadius(context.getTransition()));
            }

            return;
        }

        /* Ignore hand/inventory/ground item renders (ModelBlockItemRenderer) */
        if (context.type != FormRenderType.MODEL_BLOCK && context.type != FormRenderType.ENTITY) {
            return;
        }

        if (context.isPicking() || BBSRendering.isIrisShadowPass()) {
            return;
        }

        this.lastEntity = context.entity;

        /*
         * Extract exact 3D world position from camera-relative render matrix (supports
         * ModelBlock, Replay, Anchors & Bones)
         */
        Matrix3f invView = InverseViewRotation.get();
        Matrix4f worldRelMatrix = new Matrix4f(invView).mul(context.stack.peek().getPositionMatrix());
        Vector3f relOffset = worldRelMatrix.getTranslation(new Vector3f());

        this.lastCameraPos = new Vec3d(context.camera.position.x, context.camera.position.y, context.camera.position.z);
        this.lastWorldPos = new Vec3d(
                this.lastCameraPos.x + relOffset.x,
                this.lastCameraPos.y + relOffset.y,
                this.lastCameraPos.z + relOffset.z);
        this.lastCameraRotation.set(invView).transpose();
        this.lastWorldRenderTimeMs = System.currentTimeMillis();
        this.lastActiveTimeMs = this.lastWorldRenderTimeMs;

        /*
         * Update playback state directly on render frame as well so pausing timeline
         * stops audio within 1 frame. If stopped (or not in an active film playback),
         * audio is immediately stopped and released.
         */
        PlaybackState state = this.resolvePlaybackState(context.entity);

        if (state == PlaybackState.STOPPED) {
            if (this.activeSoundInstance != null) {
                this.stop();
            }
        } else {
            this.updateAudioPlayback(context.entity, context.getTransition());
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* Hide visual gizmo in F1 mode or during active film playback */
        if (mc.options.hudHidden || isAnyFilmPlaying()) {
            return;
        }

        this.renderEditorVisuals(context.stack, (float) this.form.getRadius(context.getTransition()));
    }

    private void renderEditorVisuals(MatrixStack stack, float radius) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        /* Center audio source representative box */
        float boxSize = 0.22F;
        Draw.renderBox(stack, -boxSize / 2, -boxSize / 2, -boxSize / 2, boxSize, boxSize, boxSize, 1.0F, 0.78F, 0.15F,
                0.85F);

        /* Wireframe radius sphere (only if showWireframe toggle is enabled) */
        if (this.form.showWireframe.get()) {
            RenderSystem.lineWidth(1.5F);
            this.renderWireframeSphere(stack, Math.max(0.2F, radius), 1.0F, 0.78F, 0.15F, 0.65F);
        }

        RenderSystem.enableCull();
    }

    @Override
    public void tick(IEntity entity) {
        if (entity == null || entity.getWorld() == null) {
            this.stop();
            return;
        }

        PlaybackState state = this.resolvePlaybackState(entity);

        if (state == PlaybackState.STOPPED) {
            if (this.activeSoundInstance != null) {
                this.stop();
            }
            return;
        }

        /*
         * If this form was rendered in 3D recently (< 150ms), render3D handles playback
         * with exact sub-tick camera transforms. Skip tick playback update to prevent
         * jitter and ear-flipping!
         */
        long now = System.currentTimeMillis();
        if (now - this.lastWorldRenderTimeMs < 150L) {
            return;
        }

        /* Ignore dummy item entities at origin (0, 0, 0) from ModelBlockItemRenderer */
        if (entity.getX() == 0D && entity.getY() == 0D && entity.getZ() == 0D && this.lastWorldRenderTimeMs == 0L) {
            return;
        }

        this.lastEntity = entity;
        this.lastActiveTimeMs = now;

        /* Always refresh world position from entity + form transform */
        Transform transform = this.createTransform();
        this.lastWorldPos = new Vec3d(
                entity.getX() + transform.translate.x,
                entity.getY() + transform.translate.y,
                entity.getZ() + transform.translate.z);

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            Camera cam = mc.gameRenderer.getCamera();
            this.lastCameraPos = cam.getPos();
            Quaternionf q = new Quaternionf(cam.getRotation()).conjugate();
            this.lastCameraRotation.rotation(q);
        }

        this.updateAudioPlayback(entity, 0.0F);
    }

    private void updateAudioPlayback(IEntity entity, float transition) {
        Link sound = this.form.sound.get();

        if (sound == null || !this.isFormHierarchyVisible()) {
            this.stop();
            return;
        }

        PlaybackState state = this.resolvePlaybackState(entity);

        if (state == PlaybackState.STOPPED) {
            this.stop();
            return;
        }

        this.lastPlaybackState = state;

        if (this.lastWorldPos == null || this.lastCameraPos == null) {
            return;
        }

        boolean loop = this.form.loop.get();

        if (loop != this.lastLoopState) {
            this.lastLoopState = loop;
        }

        /* If sound link changed, reset active source */
        if (this.currentSound != null && !sound.equals(this.currentSound)) {
            this.stop();
        }

        double dx = this.lastWorldPos.x - this.lastCameraPos.x;
        double dy = this.lastWorldPos.y - this.lastCameraPos.y;
        double dz = this.lastWorldPos.z - this.lastCameraPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float radius = Math.max(0.1F, this.form.radius.get());

        /*
         * Claim slot for this (entity, formPath) so cloned forms on the same entity
         * stop their predecessor immediately. Guarantees no ghost/duplicate instances!
         */
        String slotKey = (entity != null ? System.identityHashCode(entity) : 0) + ":" + FormUtils.getPath(this.form);
        AudioFormRenderer previous = ENTITY_SLOT_OWNERS.put(slotKey, this);

        if (previous != null && previous != this) {
            previous.stop();
        }

        SoundBuffer buffer = BBSModClient.getSounds().get(sound, false);

        if (buffer == null) {
            return;
        }

        float offsetSec = this.form.offset.get();
        float duration = buffer.getDuration();
        float timelineSec = this.getFilmTimelineSeconds(entity, transition) - offsetSec;

        if (timelineSec < 0.0F) {
            this.stop();
            return;
        }

        if (duration > 0.0F) {
            if (loop) {
                timelineSec = timelineSec % duration;

                if (timelineSec < 0.0F) {
                    timelineSec += duration;
                }
            } else if (timelineSec >= duration) {
                this.stop();
                return;
            }
        }

        if (!this.ensureActivePlayer(sound, buffer)) {
            return;
        }

        this.activeSoundInstance.setLooping(loop);

        if (!this.activeSoundInstance.isPlaying()) {
            this.activeSoundInstance.setPlaybackPosition(timelineSec);
            this.activeSoundInstance.play();
        } else {
            float currentPos = this.activeSoundInstance.getPlaybackPosition();
            float drift = Math.abs(timelineSec - currentPos);

            if (loop && duration > 0.0F) {
                drift = Math.min(drift, Math.abs(drift - duration));
            }

            if (drift > 0.25F) {
                this.activeSoundInstance.setPlaybackPosition(timelineSec);
            }
        }

        /*
         * Apply 3D spatial positioning, distance attenuation, pan, pitch, and acoustic
         * muffle
         */
        this.applySpatialParameters(sound, timelineSec, loop, dx, dy, dz, distance, radius);
    }

    private boolean ensureActivePlayer(Link sound, SoundBuffer buffer) {
        if (this.activeSoundInstance != null && this.activeSoundInstance.getSource() > 0) {
            this.activeSoundInstance.refresh();
            ACTIVE_RENDERERS.add(this);
            return true;
        }

        this.stopInternal(false);

        SoundPlayer player = new SoundPlayer(buffer).unique().owner(this);

        if (player.getSource() <= 0) {
            return false;
        }

        this.activeSoundInstance = player;
        this.currentSound = sound;
        ACTIVE_RENDERERS.add(this);

        return true;
    }

    private void applySpatialParameters(Link sound, double sourceSec, boolean loop, double dx, double dy, double dz, double distance, float radius) {
        if (this.activeSoundInstance == null || this.activeSoundInstance.getSource() <= 0) {
            return;
        }

        int source = this.activeSoundInstance.getSource();

        /* 1. Distance attenuation over [0, radius] */
        float normDist = MathUtils.clamp((float) (distance / radius), 0.0F, 1.0F);
        float distanceGain = distance >= radius ? 0.0F : (1.0F - normDist) * (1.0F - normDist * 0.45F);

        /*
         * 2. Compute camera listener axes directly from camera orientation.
         * Pure dot products - immune to gimbal lock, quadrant flip, and matrix transposition sign flips!
         */
        MinecraftClient mc = MinecraftClient.getInstance();
        Vector3f camRight = new Vector3f(1.0F, 0.0F, 0.0F);
        Vector3f camUp = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f camForward = new Vector3f(0.0F, 0.0F, -1.0F);

        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            Camera cam = mc.gameRenderer.getCamera();
            Quaternionf rot = cam.getRotation();
            camRight.rotate(rot);
            camUp.rotate(rot);
            camForward.rotate(rot);
        } else {
            this.lastCameraRotation.transform(camRight);
            this.lastCameraRotation.transform(camUp);
            this.lastCameraRotation.transform(camForward);
        }

        float localX;
        float localY;
        float localZForward;

        if (distance > 0.001D) {
            float invDist = 1.0F / (float) distance;
            /* Invert dot product polarity so +localX is strictly right ear and -localX is strictly left ear */
            localX = -(float) (dx * camRight.x + dy * camRight.y + dz * camRight.z) * invDist;
            localY = (float) (dx * camUp.x + dy * camUp.y + dz * camUp.z) * invDist;
            localZForward = (float) (dx * camForward.x + dy * camForward.y + dz * camForward.z) * invDist;
        } else {
            localX = 0.0F;
            localY = 0.0F;
            localZForward = 1.0F;
        }

        float panBias = MathUtils.clamp(this.form.pan.get(), -1.0F, 1.0F);

        /*
         * Near-field blend: when camera is extremely close (< 0.5 blocks), smoothly blend
         * localX toward center/panBias to avoid harsh ear-flipping when crossing through center.
         */
        float nearBlend = MathUtils.clamp((float) (distance / 0.5D), 0.0F, 1.0F);
        float effectivePan = MathUtils.clamp(localX * nearBlend + panBias, -1.0F, 1.0F);

        /*
         * OpenAL 3D Spatial Position:
         * Uses a circular constant-power panning vector on the listener's front/lateral arc.
         * Keeping spatialZ <= 0 eliminates OpenAL-Soft HRTF back-hemisphere pinna phase flips
         * that cause sound on the right to abruptly leak into the left ear.
         */
        float spatialX = effectivePan;
        float spatialY = MathUtils.clamp(localY * nearBlend * 0.7F, -1.0F, 1.0F);
        float spatialZ = (float) -Math.sqrt(Math.max(0.0F, 1.0F - spatialX * spatialX));

        /* 3. Base Volume & Pitch + Acoustic Muffle (Low-Pass) */
        float effectiveVolume = this.form.volume.get() * distanceGain;
        float effectivePitch = MathUtils.clamp(this.form.pitch.get(), 0.5F, 2.0F);
        float muffled = 0.0F;

        /* If sound is behind listener (localZForward < 0), apply subtle natural rear-shadow muffle & attenuation */
        if (localZForward < 0.0F) {
            float behindFactor = MathUtils.clamp(-localZForward, 0.0F, 1.0F);
            effectiveVolume *= (1.0F - behindFactor * 0.18F);
            muffled += behindFactor * 0.20F;
        }

        if (this.form.muffle.get()) {
            float occlusion = AudioOcclusionManager.calculateOcclusion(this.lastCameraPos, this.lastWorldPos);
            float factor = MathUtils.clamp(this.form.occlusionFactor.get(), 0.0F, 2.0F);
            muffled = MathUtils.clamp(muffled + occlusion * factor, 0.0F, 1.0F);

            effectiveVolume *= (1.0F - Math.min(1.0F, occlusion * factor) * 0.65F);
            effectivePitch *= (1.0F - Math.min(1.0F, occlusion * factor) * 0.12F);
        }

        /* 4. Report to MinecraftSoundCapture for offline video export mix */
        MinecraftSoundCapture capture = BBSModClient.getMinecraftSoundCapture();

        if (capture != null && capture.isActive() && sound != null) {
            double angle = (spatialX + 1.0D) * Math.PI / 4.0D;
            float gainL = effectiveVolume * (float) Math.cos(angle);
            float gainR = effectiveVolume * (float) Math.sin(angle);
            float lowPassFactor = MathUtils.clamp((float) Math.pow(1.0F - Math.min(1.0F, muffled), 2.0D), 0.02F, 1.0F);

            capture.reportExternal(this, sound, sourceSec, gainL, gainR, effectivePitch, lowPassFactor, loop);
        }

        /* 5. Live OpenAL hardware playback */
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, spatialX, spatialY, spatialZ);
        AL10.alSource3f(source, AL10.AL_VELOCITY, 0.0F, 0.0F, 0.0F);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);

        ALCapabilities caps = AL.getCapabilities();

        if (caps != null && caps.AL_SOFT_source_spatialize) {
            AL10.alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
        }

        boolean muteDuringCapture = BBSSettings.videoMuteAudioWhileRender.get()
                && BBSModClient.getVideoRecorder().isRecording();

        if (muteDuringCapture || distance >= radius) {
            this.activeSoundInstance.setVolume(0.0F);
        } else {
            this.applyLowPassFilter(source, Math.min(1.0F, muffled));
            this.activeSoundInstance.setVolume(Math.max(0.0F, effectiveVolume));
            this.activeSoundInstance.setPitch(MathUtils.clamp(effectivePitch, 0.25F, 2.5F));
        }
    }

    private void applyLowPassFilter(int source, float muffled) {
        try {
            if (muffled <= 0.001F) {
                if (this.lowPassFilterId > 0) {
                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                }

                return;
            }

            if (this.lowPassFilterId <= 0) {
                int filter = EXTEfx.alGenFilters();

                if (filter <= 0 || AL10.alGetError() != AL10.AL_NO_ERROR) {
                    return;
                }

                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

                if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                    EXTEfx.alDeleteFilters(filter);
                    return;
                }

                this.lowPassFilterId = filter;
            }

            float gain = MathUtils.clamp(1.0F - muffled * 0.45F, 0.15F, 1.0F);
            float gainHf = MathUtils.clamp((float) Math.pow(1.0F - muffled, 2.2D), 0.02F, 1.0F);

            EXTEfx.alFilterf(this.lowPassFilterId, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(this.lowPassFilterId, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, this.lowPassFilterId);
        } catch (Throwable ignored) {
        }
    }

    private boolean isFormHierarchyVisible() {
        Form current = this.form;

        while (current != null) {
            if (!current.visible.get()) {
                return false;
            }

            current = current.getParentForm();
        }

        return true;
    }

    private PlaybackState resolvePlaybackState(IEntity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.isPaused()) {
            return PlaybackState.STOPPED;
        }

        Form root = FormUtils.getRoot(this.form);

        /* 1. Check Film / Replay Editor (UIFilmPanel -> FilmEditorController) */
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        if (dashboard != null && dashboard.getPanels() != null) {
            UIFilmPanel filmPanel = dashboard.getPanels().getPanel(UIFilmPanel.class);

            if (filmPanel != null && filmPanel.getController() != null) {
                FilmEditorController editorController = filmPanel.getController().editorController;

                if (editorController != null) {
                    boolean inDashboardScreen = mc.currentScreen instanceof UIScreen
                            && dashboard.getPanels().panel == filmPanel;

                    if (inDashboardScreen) {
                        /* When in Film Editor screen, pause state strictly controls all sounds */
                        if (!filmPanel.isRunning() || editorController.paused) {
                            return PlaybackState.STOPPED;
                        }

                        /* Film Editor is actively playing: both controller entities and scene ModelBlocks play in sync */
                        return PlaybackState.PLAYING;
                    }

                    if (belongsToController(editorController, entity, root)) {
                        if (!filmPanel.isRunning() || editorController.paused) {
                            return PlaybackState.STOPPED;
                        }

                        return PlaybackState.PLAYING;
                    }
                }
            }
        }

        /* 2. Check active in-world Film / Replay Controllers */
        if (BBSModClient.getFilms() != null) {
            for (BaseFilmController controller : BBSModClient.getFilms().getControllers()) {
                if (controller instanceof FrozenFilmController || controller.paused) {
                    continue;
                }

                if (belongsToController(controller, entity, root)) {
                    return PlaybackState.PLAYING;
                }
            }

            /* Also allow world ModelBlocks to play if an in-world film is actively playing */
            for (BaseFilmController controller : BBSModClient.getFilms().getControllers()) {
                if (!(controller instanceof FrozenFilmController) && !controller.paused) {
                    return PlaybackState.PLAYING;
                }
            }
        }

        /* 3. Not in an active film/replay context -> STOPPED!
         * ModelBlock in the world NEVER plays automatically on its own. */
        return PlaybackState.STOPPED;
    }

    private float getFilmTimelineSeconds(IEntity entity, float transition) {
        Form root = FormUtils.getRoot(this.form);
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (dashboard != null && dashboard.getPanels() != null) {
            UIFilmPanel filmPanel = dashboard.getPanels().getPanel(UIFilmPanel.class);

            if (filmPanel != null && filmPanel.getController() != null) {
                FilmEditorController editorController = filmPanel.getController().editorController;

                if (editorController != null) {
                    boolean inDashboardScreen = mc.currentScreen instanceof UIScreen
                            && dashboard.getPanels().panel == filmPanel;

                    if (inDashboardScreen || belongsToController(editorController, entity, root)) {
                        return (editorController.getTick() + transition) / 20.0F;
                    }
                }
            }
        }

        if (BBSModClient.getFilms() != null) {
            for (BaseFilmController controller : BBSModClient.getFilms().getControllers()) {
                if (belongsToController(controller, entity, root)) {
                    return (controller.getTick() + transition) / 20.0F;
                }
            }

            for (BaseFilmController controller : BBSModClient.getFilms().getControllers()) {
                if (!(controller instanceof FrozenFilmController) && !controller.paused) {
                    return (controller.getTick() + transition) / 20.0F;
                }
            }
        }

        int age = entity == null ? 0 : entity.getAge();

        return (age + transition) / 20.0F;
    }

    private static boolean belongsToController(BaseFilmController controller, IEntity entity, Form root) {
        if (controller == null) {
            return false;
        }

        for (IEntity e : controller.getEntities().values()) {
            if (e == null) {
                continue;
            }

            if (e == entity || e.getForm() == root) {
                return true;
            }
        }

        Map<String, Integer> actors = controller.getActors();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (actors != null && mc.world != null) {
            for (Integer actorId : actors.values()) {
                if (actorId == null) {
                    continue;
                }

                Entity worldEntity = mc.world.getEntityById(actorId);

                if (worldEntity instanceof ActorEntity actor && actor.getForm() == root) {
                    return true;
                } else if (worldEntity instanceof PlayerEntity player) {
                    Morph morph = Morph.getMorph(player);

                    if (morph != null && morph.getForm() == root) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isAnyFilmPlaying() {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        if (dashboard != null && dashboard.getPanels() != null
                && MinecraftClient.getInstance().currentScreen instanceof UIScreen) {
            UIFilmPanel filmPanel = dashboard.getPanels().getPanel(UIFilmPanel.class);

            if (filmPanel != null && dashboard.getPanels().panel == filmPanel && filmPanel.isRunning()) {
                return true;
            }
        }

        if (BBSModClient.getFilms() != null) {
            for (BaseFilmController controller : BBSModClient.getFilms().getControllers()) {
                if (!(controller instanceof FrozenFilmController) && !controller.paused) {
                    return true;
                }
            }
        }

        return false;
    }

    private void renderWireframeSphere(MatrixStack stack, float radius, float r, float g, float b, float a) {
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = stack.peek().getPositionMatrix();

        int segments = 32;

        /* X-Z Circle (Equator, Y=0) */
        drawCircle(builder, matrix, 1, 0F, radius, r, g, b, a, segments);

        /* X-Y Circle (Z=0) */
        drawCircle(builder, matrix, 2, 0F, radius, r, g, b, a, segments);

        /* Y-Z Circle (X=0) */
        drawCircle(builder, matrix, 0, 0F, radius, r, g, b, a, segments);

        /* Upper and lower latitude circles */
        float latOffset = radius * 0.7071F;
        float latRadius = radius * 0.7071F;
        drawCircle(builder, matrix, 1, latOffset, latRadius, r, g, b, a * 0.6F, segments);
        drawCircle(builder, matrix, 1, -latOffset, latRadius, r, g, b, a * 0.6F, segments);

        Bufs.draw(builder);
    }

    private void drawCircle(BufferBuilder builder, Matrix4f m, int axis, float off, float radius, float r, float g,
            float b, float a, int seg) {
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2.0D * i / seg;
            double a1 = Math.PI * 2.0D * (i + 1) / seg;
            float c0 = (float) Math.cos(a0) * radius;
            float s0 = (float) Math.sin(a0) * radius;
            float c1 = (float) Math.cos(a1) * radius;
            float s1 = (float) Math.sin(a1) * radius;

            if (axis == 0) {
                builder.vertex(m, off, c0, s0).color(r, g, b, a)/* $ nx */.next()/**/;
                builder.vertex(m, off, c1, s1).color(r, g, b, a)/* $ nx */.next()/**/;
            } else if (axis == 1) {
                builder.vertex(m, c0, off, s0).color(r, g, b, a)/* $ nx */.next()/**/;
                builder.vertex(m, c1, off, s1).color(r, g, b, a)/* $ nx */.next()/**/;
            } else {
                builder.vertex(m, c0, s0, off).color(r, g, b, a)/* $ nx */.next()/**/;
                builder.vertex(m, c1, s1, off).color(r, g, b, a)/* $ nx */.next()/**/;
            }
        }
    }
}
