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
import net.minecraft.sound.SoundCategory;
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

    /**
     * Maximum physical speed (m/s) allowed for audio velocity derivation
     * to eliminate division spikes, mouse jumps, and runaway Doppler distortion.
     */
    private static final float MAX_AUDIO_SPEED = 40.0F;

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

    /* Kinematic tracking state for 3D Doppler effect and velocity derivation */
    private double prevWorldX = Double.NaN;
    private double prevWorldY = Double.NaN;
    private double prevWorldZ = Double.NaN;
    private double prevCamX = Double.NaN;
    private double prevCamY = Double.NaN;
    private double prevCamZ = Double.NaN;
    private long prevTimeMs = 0L;
    private int prevRenderFrame = -1;
    private float velocityX = 0.0F;
    private float velocityY = 0.0F;
    private float velocityZ = 0.0F;
    private float camVelocityX = 0.0F;
    private float camVelocityY = 0.0F;
    private float camVelocityZ = 0.0F;

    /* Occlusion: the voxel raymarch runs at most every OCCLUSION_TICKS world ticks, or sooner once the
     * listener or the source has moved further than OCCLUSION_MOVE blocks (never twice in one tick);
     * frames in between ease toward the last result instead of jumping to it. */
    private static final int OCCLUSION_TICKS = 4;
    private static final double OCCLUSION_MOVE_SQ = 0.25D * 0.25D;

    private float cachedOcclusion;
    private float smoothedOcclusion;
    private long occlusionTick = Long.MIN_VALUE;
    private long occlusionSmoothNs;
    private Vec3d occlusionListener;
    private Vec3d occlusionSource;

    /**
     * The diagnostic log (switched on from the audio form panel, never saved): once a second, per
     * sound, what the playback actually did - how often it was drawn and ticked, how many draws a
     * single frame had, how far the source jumped between draws, seeks, restarts, stops and who
     * stopped it, the muffle and the filter writes - so a stutter can be read off the log instead
     * of guessed at.
     */
    public static class Diagnostics {
        public static boolean enabled;
        static long frame;

        private long lastFrame = -1;
        private int callsThisFrame;
        private int maxCallsPerFrame;
        private int renders;
        private int ticks;
        private float maxJump;
        private float offAxis;
        private float viewDistance;
        private final Vector3f prev = new Vector3f();
        private boolean hasPrev;
        int starts;
        int seeks;
        int revives;
        int filterWrites;
        int undrawn;
        int jumps;
        float drift;
        final Map<String, Integer> stops = new java.util.LinkedHashMap<>();
        private long windowStart;
        private long windowFrame;

        void render(Vector3f view) {
            if (!enabled) {
                return;
            }

            this.renders += 1;

            if (this.lastFrame == frame) {
                this.callsThisFrame += 1;
            } else {
                this.callsThisFrame = 1;
                this.lastFrame = frame;
            }

            this.maxCallsPerFrame = Math.max(this.maxCallsPerFrame, this.callsThisFrame);

            if (this.hasPrev) {
                this.maxJump = Math.max(this.maxJump, view.distance(this.prev));
            }

            this.prev.set(view);
            this.hasPrev = true;
            this.viewDistance = view.length();
            this.offAxis = this.viewDistance > 0.001F ? (float) Math.toDegrees(Math.acos(MathUtils.clamp(-view.z / this.viewDistance, -1F, 1F))) : 0F;
        }

        void tick(AudioFormRenderer renderer) {
            if (!enabled) {
                return;
            }

            this.ticks += 1;

            long now = System.currentTimeMillis();

            if (this.windowStart == 0L) {
                this.windowStart = now;
                this.windowFrame = frame;

                return;
            }

            if (now - this.windowStart < 1000L) {
                return;
            }

            float seconds = (now - this.windowStart) / 1000F;
            boolean playing = renderer.activeSoundInstance != null && renderer.activeSoundInstance.isPlaying();

            mchorse.bbs_mod.BBSMod.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[BBS audio] %s fps=%.0f draws=%d maxDrawsPerFrame=%d ticks=%d dist=%.2f offAxis=%.1f maxJump=%.3f playing=%s state=%s undrawnTicks=%d starts=%d seeks=%d jumps=%d revives=%d drift=%.3f sync=%.3f occl=%.3f/%.3f filterWrites=%d stops=%s",
                renderer.form.sound.get(), (frame - this.windowFrame) / seconds, this.renders, this.maxCallsPerFrame, this.ticks,
                this.viewDistance, this.offAxis, this.maxJump, playing, renderer.lastPlaybackState, this.undrawn,
                this.starts, this.seeks, this.jumps, this.revives, this.drift, renderer.syncPitch,
                renderer.cachedOcclusion, renderer.smoothedOcclusion, this.filterWrites, this.stops));

            this.windowStart = now;
            this.windowFrame = frame;
            this.renders = 0;
            this.ticks = 0;
            this.maxCallsPerFrame = 0;
            this.maxJump = 0F;
            this.starts = 0;
            this.seeks = 0;
            this.jumps = 0;
            this.revives = 0;
            this.filterWrites = 0;
            this.undrawn = 0;
            this.stops.clear();
        }
    }

    private final Diagnostics diag = new Diagnostics();

    /* The frame this source was last placed in (see render3D); one placement per frame */
    private long audioFrame = -1;

    /* The source in camera space as of the last frame it was drawn (see render3D) */
    private final Vector3f lastViewPos = new Vector3f();
    /* The source from the form's world matrix (see render3D), null when the pass has none */
    private Vec3d occlusionWorldPos;
    /* The source relative to its entity at the last draw, so the undrawn path keeps it in the same place */
    private Vec3d drawnOffset;
    private boolean hasViewPos;

    /* The direction OpenAL is given, eased so no single frame can make it jump */
    private final Vector3f smoothedSpatial = new Vector3f(0F, 0F, -1F);
    private long spatialSmoothNs;

    /* How long without a draw before tick() takes the spatial update over: long enough that a
     * slow frame does not hand it back and forth between the two every other tick */
    private static final long RENDER_STALE_MS = 250L;

    /* Keeping the sound on the film's time. A small drift is pulled back by playing a hair faster or
     * slower (SYNC_RATE of the drift per second, at most SYNC_MAX); only a real jump - a scrub, a
     * loop, a drift past SYNC_SEEK - seeks, because every seek is an audible skip. */
    private static final float SYNC_SEEK = 0.75F;
    private static final float SYNC_JUMP = 0.15F;
    private static final float SYNC_RATE = 0.5F;
    private static final float SYNC_MAX = 0.05F;

    private float syncPitch = 1F;
    private float lastTimelineSec = Float.NaN;
    private long lastTimelineMs;

    /* The source the one-off spatial setup (relative, rolloff, spatialize) was written for */
    private int configuredSource = -1;

    /* Last values sent to the low-pass filter, so it is only rewritten when it really changes */
    private float filterGain = Float.NaN;
    private float filterGainHf = Float.NaN;
    private boolean filterAttached;

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

            if (renderer.activeSoundInstance == null || !renderer.activeSoundInstance.isValid()) {
                renderer.stopInternal(false);
                it.remove();
                continue;
            }

            if (renderer.lastEntity != null && renderer.lastEntity.getWorld() == null) {
                renderer.stop();
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
        if (Diagnostics.enabled) {
            StackTraceElement caller = Thread.currentThread().getStackTrace()[2];

            this.diag.stops.merge(caller.getMethodName() + ":" + caller.getLineNumber(), 1, Integer::sum);
        }

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

        this.prevWorldX = Double.NaN;
        this.prevWorldY = Double.NaN;
        this.prevWorldZ = Double.NaN;
        this.prevCamX = Double.NaN;
        this.prevCamY = Double.NaN;
        this.prevCamZ = Double.NaN;
        this.prevTimeMs = 0L;
        this.prevRenderFrame = -1;
        this.syncPitch = 1F;
        this.lastTimelineSec = Float.NaN;
        this.spatialSmoothNs = 0L;
        this.smoothedSpatial.set(0F, 0F, -1F);
        this.hasViewPos = false;
        this.velocityX = 0.0F;
        this.velocityY = 0.0F;
        this.velocityZ = 0.0F;
        this.camVelocityX = 0.0F;
        this.camVelocityY = 0.0F;
        this.camVelocityZ = 0.0F;

        /* The next source starts from scratch: its setup is written again and the muffle is measured anew */
        this.configuredSource = -1;
        this.filterAttached = false;
        this.filterGain = Float.NaN;
        this.filterGainHf = Float.NaN;
        this.occlusionTick = Long.MIN_VALUE;
        this.occlusionSmoothNs = 0L;
        this.occlusionListener = null;
        this.occlusionSource = null;

        if (removeFromSet) {
            ACTIVE_RENDERERS.remove(this);
        }
    }

    /**
     * Once per frame, before the world draws: a source whose block was broken or whose chunk
     * unloaded (the block entity drops its world on removal), or whose film has stopped or paused,
     * is stopped and deleted right away instead of waiting for the next client tick.
     */
    public static void reapOrphans() {
        Diagnostics.frame++;
        if (ACTIVE_RENDERERS.isEmpty()) {
            return;
        }

        Iterator<AudioFormRenderer> it = ACTIVE_RENDERERS.iterator();

        while (it.hasNext()) {
            AudioFormRenderer renderer = it.next();
            boolean orphan = renderer.activeSoundInstance == null
                || !renderer.activeSoundInstance.isValid()
                || (renderer.lastEntity != null && renderer.lastEntity.getWorld() == null)
                || renderer.resolvePlaybackState(renderer.lastEntity) == PlaybackState.STOPPED;

            if (orphan && Diagnostics.enabled) {
                String why = renderer.activeSoundInstance == null || !renderer.activeSoundInstance.isValid() ? "invalid" : (renderer.lastEntity != null && renderer.lastEntity.getWorld() == null ? "noWorld" : "stoppedState");

                renderer.diag.stops.merge("reapOrphans:" + why, 1, Integer::sum);
            }

            if (orphan) {
                renderer.stopInternal(false);
                it.remove();
            }
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
            /* Form Editor 3D viewport: the visual helper only. Nothing here keeps the source alive, so the
             * idle watchdog lets go of it - render3D itself never starts or stops hardware. */
            if (!context.isPicking() && !BBSRendering.isIrisShadowPass()) {
                this.renderEditorVisuals(context.stack, (float) this.form.getRadius(context.getTransition()));
            }

            return;
        }

        /* Ignore hand/inventory/ground item renders (ModelBlockItemRenderer) */
        if (context.type != FormRenderType.MODEL_BLOCK && context.type != FormRenderType.ENTITY) {
            return;
        }

        /* A portal window draws the scene a second time from the portal's own camera: in that pass
         * the source was ~125 blocks off, the last draw of the frame won, and the sound was thrown
         * past its radius and back every frame a portal was on screen - the stutter (and the ears
         * flipping) that only happened looking in some directions, and only in the world. */
        if (context.isPicking() || BBSRendering.isIrisShadowPass() || mchorse.bbs_mod.forms.FormPortals.isPassActive()) {
            return;
        }

        /* And whatever else may draw it again in the same frame: the first draw of the frame is the
         * view the listener is in, the rest only get to show it */
        if (this.audioFrame == Diagnostics.frame) {
            return;
        }

        this.audioFrame = Diagnostics.frame;

        this.lastEntity = context.entity;

        /*
         * Extract exact 3D world position from camera-relative render matrix (supports
         * ModelBlock, Replay, Anchors & Bones)
         */
        Matrix3f invView = InverseViewRotation.get();
        Matrix4f worldRelMatrix = new Matrix4f(invView).mul(context.stack.peek().getPositionMatrix());
        Vector3f relOffset = worldRelMatrix.getTranslation(new Vector3f());

        /* Where the source is in the very frame being drawn, in the camera's own axes (+X right,
         * +Y up, -Z ahead - OpenAL's listener axes too). The ear direction is taken from this
         * alone: mixing a world offset with the camera's direction vectors agreed only near the
         * middle of the screen, and drifted apart toward its edges. */
        context.stack.peek().getPositionMatrix().getTranslation(this.lastViewPos);
        this.diag.render(this.lastViewPos);
        this.hasViewPos = Float.isFinite(this.lastViewPos.x) && Float.isFinite(this.lastViewPos.y) && Float.isFinite(this.lastViewPos.z);

        /* The same place, straight from the form's world matrix: no camera math in it, so it does not
         * move by a hair as the head turns. The muffle raymarch goes to this one. */
        if (context.world != null) {
            Vector3f world = context.world.peek().getPositionMatrix().getTranslation(new Vector3f());

            this.occlusionWorldPos = Float.isFinite(world.x) && Float.isFinite(world.y) && Float.isFinite(world.z) ? new Vec3d(world.x, world.y, world.z) : null;
        } else {
            this.occlusionWorldPos = null;
        }

        this.lastCameraPos = new Vec3d(context.camera.position.x, context.camera.position.y, context.camera.position.z);
        this.lastWorldPos = new Vec3d(
                this.lastCameraPos.x + relOffset.x,
                this.lastCameraPos.y + relOffset.y,
                this.lastCameraPos.z + relOffset.z);
        this.lastCameraRotation.set(invView).transpose();

        if (context.entity != null) {
            float t = context.getTransition();
            double ex = context.entity.getPrevX() + (context.entity.getX() - context.entity.getPrevX()) * t;
            double ey = context.entity.getPrevY() + (context.entity.getY() - context.entity.getPrevY()) * t;
            double ez = context.entity.getPrevZ() + (context.entity.getZ() - context.entity.getPrevZ()) * t;

            this.drawnOffset = this.lastWorldPos.subtract(ex, ey, ez);
        }
        this.lastWorldRenderTimeMs = System.currentTimeMillis();
        this.lastActiveTimeMs = this.lastWorldRenderTimeMs;

        /*
         * render3D only steers: sub-tick listener orientation and the source's spatial position. It
         * never starts, stops or raycasts - play/pause/stop/loop belong to tick(), and stopping a
         * paused or removed source belongs to reapOrphans(), which runs once per frame.
         */
        if (this.activeSoundInstance != null && this.lastPlaybackState == PlaybackState.PLAYING && this.activeSoundInstance.isPlaying()) {
            this.updateSpatialOnly(context.entity, context.getTransition());
        }

        /* Part of the picture, not of the editor: drawn in F1, while the film plays and in exports */
        this.renderWaves(context);

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
        this.diag.tick(this);
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

        /* Ignore dummy item entities at origin (0, 0, 0) from ModelBlockItemRenderer */
        if (entity.getX() == 0D && entity.getY() == 0D && entity.getZ() == 0D && this.lastWorldRenderTimeMs == 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        this.lastEntity = entity;
        this.lastActiveTimeMs = now;

        /*
         * If the block is NOT being rendered in 3D right now (e.g. player looking away,
         * frustum culling, distance > render distance, or behind camera), update world and camera positions
         * from entity coords and camera.
         */
        boolean isRenderCulled = (now - this.lastWorldRenderTimeMs >= RENDER_STALE_MS);

        if (isRenderCulled || this.lastWorldPos == null || this.lastCameraPos == null) {
            if (this.drawnOffset != null) {
                /* Where the last draw put the source relative to its entity, carried along with the
                 * entity: the form's place on a bone, an anchor or a parent is not lost, so the
                 * sound does not leap to another spot the moment it stops being drawn */
                this.lastWorldPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ()).add(this.drawnOffset);
            } else {
                Transform transform = this.createTransform();
                this.lastWorldPos = new Vec3d(
                        entity.getX() + transform.translate.x,
                        entity.getY() + transform.translate.y,
                        entity.getZ() + transform.translate.z);
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                Camera cam = mc.gameRenderer.getCamera();
                this.lastCameraPos = cam.getPos();
                Quaternionf q = new Quaternionf(cam.getRotation()).conjugate();
                this.lastCameraRotation.rotation(q);
            }
        }

        this.updateAudioPlayback(entity, 0.0F, isRenderCulled);
    }

    private void updateAudioPlayback(IEntity entity, float transition, boolean applySpatial) {
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
        float filmSec = this.getFilmTimelineSeconds(entity, transition) - offsetSec;

        if (filmSec < 0.0F) {
            this.stop();
            return;
        }

        /* A sound pitched up runs through its samples faster than the film runs: the place it
         * should be at is the film's time scaled by the pitch, or it falls out of sync by itself */
        float timelineSec = filmSec * MathUtils.clamp(this.form.pitch.get(), 0.5F, 2.0F);

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

        long nowMs = System.currentTimeMillis();

        /* Did the film's clock jump (a scrub, a seek, a loop of the film) rather than run? */
        boolean jumped = Float.isNaN(this.lastTimelineSec);

        if (!jumped) {
            float ran = (nowMs - this.lastTimelineMs) / 1000F;
            float moved = filmSec - this.lastTimelineSec;

            jumped = Math.abs(moved - ran) > SYNC_JUMP + ran * 0.5F;
        }

        this.lastTimelineSec = filmSec;
        this.lastTimelineMs = nowMs;

        if (!this.activeSoundInstance.isPlaying()) {
            this.activeSoundInstance.setPlaybackPosition(timelineSec);
            this.activeSoundInstance.play();
            this.diag.starts++;
            this.syncPitch = 1F;
        } else {
            float currentPos = this.activeSoundInstance.getPlaybackPosition();
            /* Positive: the sound is behind where it should be */
            float drift = timelineSec - currentPos;

            if (loop && duration > 0.0F) {
                if (drift > duration / 2F) {
                    drift -= duration;
                } else if (drift < -duration / 2F) {
                    drift += duration;
                }
            }

            this.diag.drift = drift;
            this.diag.jumps += jumped ? 1 : 0;

            if (jumped || Math.abs(drift) > SYNC_SEEK) {
                this.activeSoundInstance.setPlaybackPosition(timelineSec);
                this.diag.seeks++;
                this.syncPitch = 1F;
            } else if (Math.abs(drift) > 0.02F) {
                this.syncPitch = 1F + MathUtils.clamp(drift * SYNC_RATE, -SYNC_MAX, SYNC_MAX);
            } else {
                this.syncPitch = 1F;
            }
        }

        /*
         * Apply 3D spatial positioning if culled from rendering (e.g. looking away).
         * If rendered in 3D (< 100ms ago), render3D handles sub-tick spatial updates!
         */
        if (applySpatial) {
            this.diag.undrawn++;
            this.updateVelocity(System.currentTimeMillis());
            this.applySpatialParameters(sound, timelineSec, loop, dx, dy, dz, distance, radius, false);
        }
    }

    private void updateSpatialOnly(IEntity entity, float transition) {
        Link sound = this.form.sound.get();

        /* tick() takes it from here on its next run; the render pass does not stop hardware */
        if (sound == null || !this.isFormHierarchyVisible()) {
            return;
        }

        if (this.lastWorldPos == null || this.lastCameraPos == null) {
            return;
        }

        this.updateVelocity(System.currentTimeMillis());

        double dx = this.lastWorldPos.x - this.lastCameraPos.x;
        double dy = this.lastWorldPos.y - this.lastCameraPos.y;
        double dz = this.lastWorldPos.z - this.lastCameraPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float radius = Math.max(0.1F, this.form.radius.get());

        float offsetSec = this.form.offset.get();
        float timelineSec = this.getFilmTimelineSeconds(entity, transition) - offsetSec;
        boolean loop = this.form.loop.get();

        this.applySpatialParameters(sound, timelineSec, loop, dx, dy, dz, distance, radius, this.hasViewPos);
    }

    private boolean ensureActivePlayer(Link sound, SoundBuffer buffer) {
        if (this.activeSoundInstance != null) {
            if (this.activeSoundInstance.getSource() > 0 && this.activeSoundInstance.isValid()) {
                this.activeSoundInstance.refresh();
                ACTIVE_RENDERERS.add(this);
                return true;
            }

            /* Invalid or dead OpenAL hardware source: dispose stale instance immediately! */
            this.stopInternal(false);
        }

        if (buffer == null || !buffer.isValid()) {
            return false;
        }

        this.stopInternal(false);

        SoundPlayer player = new SoundPlayer(buffer).unique().owner(this);

        if (player.getSource() <= 0 || !player.isValid()) {
            player.delete();
            return false;
        }

        this.activeSoundInstance = player;
        this.currentSound = sound;
        ACTIVE_RENDERERS.add(this);

        return true;
    }

    private void updateVelocity(long now) {
        if (this.lastWorldPos == null || this.lastCameraPos == null) {
            return;
        }

        boolean isRecording = BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording();
        MinecraftSoundCapture capture = BBSModClient.getMinecraftSoundCapture();
        int currentFrame = (isRecording && capture != null && capture.isActive()) ? capture.getCurrentFrame() : -1;

        float dtSec = 0.0F;
        boolean validDt = false;

        if (isRecording && currentFrame >= 0) {
            if (this.prevRenderFrame >= 0 && currentFrame != this.prevRenderFrame) {
                int frameDiff = currentFrame - this.prevRenderFrame;
                if (frameDiff > 0 && frameDiff <= 5) {
                    float fps = (float) Math.max(1, BBSRendering.getVideoFrameRate());
                    dtSec = frameDiff / fps;
                    validDt = true;
                }
            }
            this.prevRenderFrame = currentFrame;
        } else {
            if (!Double.isNaN(this.prevWorldX) && this.prevTimeMs > 0L) {
                long dtMs = now - this.prevTimeMs;
                if (dtMs < 5L) {
                    /* Skip velocity update on sub-5ms delta to prevent division spikes / NaN / audio glitches */
                    return;
                }
                if (dtMs < 500L) {
                    dtSec = (float) dtMs / 1000.0F;
                    validDt = true;
                }
            }
            this.prevRenderFrame = -1;
        }

        if (validDt && (isRecording || dtSec >= 0.005F)) {
            if (!Double.isNaN(this.prevWorldX)) {
                float vx = (float) (this.lastWorldPos.x - this.prevWorldX) / dtSec;
                float vy = (float) (this.lastWorldPos.y - this.prevWorldY) / dtSec;
                float vz = (float) (this.lastWorldPos.z - this.prevWorldZ) / dtSec;

                float speedSq = vx * vx + vy * vy + vz * vz;
                if (speedSq > MAX_AUDIO_SPEED * MAX_AUDIO_SPEED) {
                    float scale = MAX_AUDIO_SPEED / (float) Math.sqrt(speedSq);
                    vx *= scale;
                    vy *= scale;
                    vz *= scale;
                }

                float alpha = isRecording ? 1.0F : MathUtils.clamp(dtSec * 15.0F, 0.1F, 1.0F);
                this.velocityX += (vx - this.velocityX) * alpha;
                this.velocityY += (vy - this.velocityY) * alpha;
                this.velocityZ += (vz - this.velocityZ) * alpha;
            }

            if (!Double.isNaN(this.prevCamX)) {
                float cvx = (float) (this.lastCameraPos.x - this.prevCamX) / dtSec;
                float cvy = (float) (this.lastCameraPos.y - this.prevCamY) / dtSec;
                float cvz = (float) (this.lastCameraPos.z - this.prevCamZ) / dtSec;

                float camSpeedSq = cvx * cvx + cvy * cvy + cvz * cvz;
                if (camSpeedSq > MAX_AUDIO_SPEED * MAX_AUDIO_SPEED) {
                    float scale = MAX_AUDIO_SPEED / (float) Math.sqrt(camSpeedSq);
                    cvx *= scale;
                    cvy *= scale;
                    cvz *= scale;
                }

                float alpha = isRecording ? 1.0F : MathUtils.clamp(dtSec * 15.0F, 0.1F, 1.0F);
                this.camVelocityX += (cvx - this.camVelocityX) * alpha;
                this.camVelocityY += (cvy - this.camVelocityY) * alpha;
                this.camVelocityZ += (cvz - this.camVelocityZ) * alpha;
            }

            /* Extra safety clamp on smoothed velocities */
            float speedSq = this.velocityX * this.velocityX + this.velocityY * this.velocityY + this.velocityZ * this.velocityZ;
            if (speedSq > MAX_AUDIO_SPEED * MAX_AUDIO_SPEED) {
                float scale = MAX_AUDIO_SPEED / (float) Math.sqrt(speedSq);
                this.velocityX *= scale;
                this.velocityY *= scale;
                this.velocityZ *= scale;
            }
            float camSpeedSq = this.camVelocityX * this.camVelocityX + this.camVelocityY * this.camVelocityY + this.camVelocityZ * this.camVelocityZ;
            if (camSpeedSq > MAX_AUDIO_SPEED * MAX_AUDIO_SPEED) {
                float scale = MAX_AUDIO_SPEED / (float) Math.sqrt(camSpeedSq);
                this.camVelocityX *= scale;
                this.camVelocityY *= scale;
                this.camVelocityZ *= scale;
            }
        } else if (!isRecording && (now - this.prevTimeMs >= 500L)) {
            this.velocityX = 0.0F;
            this.velocityY = 0.0F;
            this.velocityZ = 0.0F;
            this.camVelocityX = 0.0F;
            this.camVelocityY = 0.0F;
            this.camVelocityZ = 0.0F;
        }

        this.prevWorldX = this.lastWorldPos.x;
        this.prevWorldY = this.lastWorldPos.y;
        this.prevWorldZ = this.lastWorldPos.z;
        this.prevCamX = this.lastCameraPos.x;
        this.prevCamY = this.lastCameraPos.y;
        this.prevCamZ = this.lastCameraPos.z;
        this.prevTimeMs = now;
    }

    private void applySpatialParameters(Link sound, double sourceSec, boolean loop, double dx, double dy, double dz, double distance, float radius, boolean fromView) {
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
        Vector3f camForward = new Vector3f(0.0F, 0.0F, 1.0F);

        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
            Camera cam = mc.gameRenderer.getCamera();
            camRight.set(cam.getDiagonalPlane());
            camUp.set(cam.getVerticalPlane());
            camForward.set(cam.getHorizontalPlane());
        } else {
            this.lastCameraRotation.transform(camRight);
            this.lastCameraRotation.transform(camUp);
            this.lastCameraRotation.transform(camForward);
        }

        float localX;
        float localY;
        float localZForward;

        float viewLength = this.lastViewPos.length();

        if (fromView && viewLength > 0.001F) {
            /* The frame's own camera space: no world offset, no camera vectors, nothing to disagree */
            localX = this.lastViewPos.x / viewLength;
            localY = this.lastViewPos.y / viewLength;
            localZForward = -this.lastViewPos.z / viewLength;
        } else if (distance > 0.001D) {
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
         * 360-Degree OpenAL 3D Spatial Position:
         * Vanilla Minecraft / playsound compatible full spherical coordinates in listener space.
         * localX: +Right, -Left
         * localY: +Up, -Down
         * localZ: +Behind, -Forward
         */
        float localZ = -localZForward;
        float nearBlend = MathUtils.clamp((float) (distance / 0.5D), 0.0F, 1.0F);

        float spatialX = MathUtils.clamp(localX * nearBlend + panBias, -1.0F, 1.0F);
        float spatialY = MathUtils.clamp(localY * nearBlend, -1.0F, 1.0F);
        float spatialZ = MathUtils.clamp(localZ * nearBlend, -1.0F, 1.0F);

        float spatialLenSq = spatialX * spatialX + spatialY * spatialY + spatialZ * spatialZ;
        if (spatialLenSq > 0.0001F) {
            float invLen = (float) (1.0D / Math.sqrt(spatialLenSq));
            spatialX *= invLen;
            spatialY *= invLen;
            spatialZ *= invLen;
        } else {
            spatialX = 0.0F;
            spatialY = 0.0F;
            spatialZ = -1.0F;
        }

        /* Eased over ~30 ms and kept on the unit sphere: a turn of the head still follows at once,
         * but whatever one odd frame says can no longer snap the sound across the ears */
        long spatialNow = System.nanoTime();
        float spatialDt = this.spatialSmoothNs == 0L ? 1F : MathUtils.clamp((spatialNow - this.spatialSmoothNs) / 1.0E9F, 0F, 0.25F);
        float spatialAlpha = MathUtils.clamp(spatialDt / 0.03F, 0F, 1F);

        this.spatialSmoothNs = spatialNow;
        this.smoothedSpatial.lerp(new Vector3f(spatialX, spatialY, spatialZ), spatialAlpha);

        if (this.smoothedSpatial.lengthSquared() > 0.0001F) {
            this.smoothedSpatial.normalize();
        } else {
            this.smoothedSpatial.set(spatialX, spatialY, spatialZ);
        }

        spatialX = this.smoothedSpatial.x;
        spatialY = this.smoothedSpatial.y;
        spatialZ = this.smoothedSpatial.z;

        /* 3. Base Volume & Pitch + Acoustic Muffle (Low-Pass) + Doppler Pitch */
        float effectiveVolume = this.form.volume.get() * distanceGain;

        /* Apply Minecraft Sound Options (Master Volume and Records/Blocks Category Volume) */
        float masterVolume = mc.options != null ? mc.options.getSoundVolume(SoundCategory.MASTER) : 1.0F;
        float categoryVolume = mc.options != null ? mc.options.getSoundVolume(SoundCategory.RECORDS) : 1.0F;
        effectiveVolume *= (masterVolume * categoryVolume);

        float basePitch = MathUtils.clamp(this.form.pitch.get(), 0.5F, 2.0F);
        float effectivePitch = basePitch;

        float relVx = 0.0F;
        float relVy = 0.0F;
        float relVz = 0.0F;

        if (this.form.isDoppler()) {
            relVx = MathUtils.clamp(this.velocityX - this.camVelocityX, -MAX_AUDIO_SPEED, MAX_AUDIO_SPEED);
            relVy = MathUtils.clamp(this.velocityY - this.camVelocityY, -MAX_AUDIO_SPEED, MAX_AUDIO_SPEED);
            relVz = MathUtils.clamp(this.velocityZ - this.camVelocityZ, -MAX_AUDIO_SPEED, MAX_AUDIO_SPEED);

            if (distance > 0.001D) {
                float invDist = 1.0F / (float) distance;
                float rx = (float) dx * invDist;
                float ry = (float) dy * invDist;
                float rz = (float) dz * invDist;

                /* Relative radial velocity along line-of-sight:
                 * vRel > 0: source moving away from listener -> frequency/pitch drops (< 1.0)
                 * vRel < 0: source moving towards listener -> frequency/pitch rises (> 1.0)
                 */
                float vRel = relVx * rx + relVy * ry + relVz * rz;
                float dopplerScale = (float) this.form.getDopplerScale();
                float speedOfSound = 340.0F;

                float dopplerFactor = MathUtils.clamp(1.0F - (vRel / speedOfSound) * dopplerScale, 0.5F, 1.5F);
                effectivePitch *= dopplerFactor;
            }
        }

        float muffled = 0.0F;

        /* If sound is behind listener (localZForward < 0), apply subtle natural rear-shadow muffle & attenuation */
        if (localZForward < 0.0F) {
            float behindFactor = MathUtils.clamp(-localZForward, 0.0F, 1.0F);
            effectiveVolume *= (1.0F - behindFactor * 0.18F);
            muffled += behindFactor * 0.20F;
        }

        if (this.form.muffle.get()) {
            float occlusion = this.getOcclusion();
            float factor = MathUtils.clamp(this.form.occlusionFactor.get(), 0.0F, 2.0F);
            muffled = MathUtils.clamp(muffled + occlusion * factor, 0.0F, 1.0F);

            effectiveVolume *= (1.0F - Math.min(1.0F, occlusion * factor) * 0.65F);
            /* No pitch drop for a muffled sound: a wall dulls a sound, it does not slow it down - and any
             * pitch away from the film's own makes the playback drift off the timeline. */
        }

        /* 4. Report to MinecraftSoundCapture for offline video export mix */
        MinecraftSoundCapture capture = BBSModClient.getMinecraftSoundCapture();

        if (capture != null && capture.isActive() && sound != null) {
            float clampedX = MathUtils.clamp(spatialX, -1.0F, 1.0F);
            double angle = (clampedX + 1.0D) * Math.PI / 4.0D;
            float gainL = effectiveVolume * (float) Math.cos(angle);
            float gainR = effectiveVolume * (float) Math.sin(angle);
            float lowPassFactor = MathUtils.clamp((float) Math.pow(1.0F - Math.min(1.0F, muffled), 2.0D), 0.02F, 1.0F);

            capture.reportExternal(this, sound, sourceSec, gainL, gainR, effectivePitch, lowPassFactor, loop);
        }

        /* 5. Live OpenAL hardware playback. What a source is (listener relative, no rolloff,
         * spatialized) is written once per source; only position and velocity move every frame. */
        if (this.configuredSource != source) {
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);

            ALCapabilities caps = AL.getCapabilities();

            if (caps != null && caps.AL_SOFT_source_spatialize) {
                AL10.alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
            }

            this.configuredSource = source;
            this.filterAttached = false;
            this.filterGain = Float.NaN;
            this.filterGainHf = Float.NaN;
        }

        AL10.alSource3f(source, AL10.AL_POSITION, spatialX, spatialY, spatialZ);
        if (this.form.isDoppler()) {
            AL10.alSource3f(source, AL10.AL_VELOCITY, relVx, relVy, relVz);
        } else {
            AL10.alSource3f(source, AL10.AL_VELOCITY, 0.0F, 0.0F, 0.0F);
        }

        boolean muteDuringCapture = BBSSettings.videoMuteAudioWhileRender.get()
                && BBSModClient.getVideoRecorder().isRecording();

        if (muteDuringCapture || distance >= radius || effectiveVolume <= 0.0001F) {
            this.activeSoundInstance.setVolume(0.0F);
        } else {
            this.applyLowPassFilter(source, Math.min(1.0F, muffled));
            this.activeSoundInstance.setVolume(Math.max(0.0F, effectiveVolume));
            this.activeSoundInstance.setPitch(MathUtils.clamp(effectivePitch * this.syncPitch, 0.25F, 2.5F));

            /* Revive hardware playback if sound was paused or stopped due to zero volume / reload */
            if (!this.activeSoundInstance.isPlaying()) {
                this.activeSoundInstance.play();
                this.diag.revives++;
            }
        }
    }

    /**
     * The muffle, cached: the raymarch through the blocks runs every few ticks or after real movement,
     * and the frames between ease toward its result, so a wall coming between does not click in.
     */
    private float getOcclusion() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long tick = mc.world == null ? 0L : mc.world.getTime();
        Vec3d listener = this.lastCameraPos;
        boolean drawn = System.currentTimeMillis() - this.lastWorldRenderTimeMs < RENDER_STALE_MS;
        /* Trusted only where it agrees with the drawn place: a pass with an empty world matrix would
         * aim the ray at the world origin */
        boolean agrees = this.occlusionWorldPos != null && this.occlusionWorldPos.squaredDistanceTo(this.lastWorldPos) < 4.0D;
        Vec3d source = drawn && agrees ? this.occlusionWorldPos : this.lastWorldPos;
        boolean stale = this.occlusionTick == Long.MIN_VALUE || tick - this.occlusionTick >= OCCLUSION_TICKS || tick < this.occlusionTick;
        boolean moved = this.occlusionListener == null || this.occlusionSource == null
            || listener.squaredDistanceTo(this.occlusionListener) > OCCLUSION_MOVE_SQ
            || source.squaredDistanceTo(this.occlusionSource) > OCCLUSION_MOVE_SQ;

        if (stale || (moved && tick != this.occlusionTick)) {
            this.cachedOcclusion = AudioOcclusionManager.calculateOcclusion(listener, source);
            this.occlusionTick = tick;
            this.occlusionListener = listener;
            this.occlusionSource = source;

            if (this.occlusionSmoothNs == 0L) {
                this.smoothedOcclusion = this.cachedOcclusion;
            }
        }

        long now = System.nanoTime();
        float dt = this.occlusionSmoothNs == 0L ? 1F : MathUtils.clamp((now - this.occlusionSmoothNs) / 1.0E9F, 0F, 0.25F);

        this.occlusionSmoothNs = now;
        /* About a fifth of a second to settle: as long as the raymarch interval itself */
        this.smoothedOcclusion += (this.cachedOcclusion - this.smoothedOcclusion) * MathUtils.clamp(dt * 10F, 0F, 1F);

        return this.smoothedOcclusion;
    }

    private void applyLowPassFilter(int source, float muffled) {
        try {
            /* Hysteresis: a muffle hovering at the edge must not switch the filter in and out every frame */
            if (this.filterAttached ? muffled <= 0.005F : muffled <= 0.02F) {
                if (this.lowPassFilterId > 0 && this.filterAttached) {
                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                    this.filterAttached = false;
                    this.diag.filterWrites++;
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

            /* A filter's settings reach the source only when it is (re)attached, so a change means both */
            if (!this.filterAttached || !(Math.abs(gain - this.filterGain) < 0.001F) || !(Math.abs(gainHf - this.filterGainHf) < 0.001F)) {
                EXTEfx.alFilterf(this.lowPassFilterId, EXTEfx.AL_LOWPASS_GAIN, gain);
                EXTEfx.alFilterf(this.lowPassFilterId, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, this.lowPassFilterId);

                this.filterGain = gain;
                this.filterGainHf = gainHf;
                this.filterAttached = true;
                this.diag.filterWrites++;
            }
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
        if (entity != null && entity.getWorld() == null) {
            return PlaybackState.STOPPED;
        }

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

    /* Sound vibration waves */

    /** The air bending of this frame's waves, one screen-space pass each, drawn once the world is done */
    private static final java.util.List<mchorse.bbs_mod.camera.clips.fx.FxOverlay> RIPPLES = new java.util.ArrayList<>();

    /** Called when the world has been drawn and before the hand: bends the air of every wave queued this frame. */
    public static void flushRipples() {
        if (!RIPPLES.isEmpty()) {
            mchorse.bbs_mod.ui.film.PostFxRenderer.renderRipples(RIPPLES);
            RIPPLES.clear();
        }
    }

    /** At the start of a frame: what a frame that never got to its end queued is not carried over. */
    public static void clearRipples() {
        RIPPLES.clear();
    }

    /**
     * The rings as the screen sees them: the source's center and every ring's radius projected with
     * the frame's own projection, in frame heights, with the same phases and tremble as the drawn
     * rings, so the bend sits on them.
     */
    private void queueRipple(float time, float reach, float strength) {
        float distortion = MathUtils.clamp(this.form.waveDistortion.get(), 0F, 4F);

        if (distortion <= 0.001F || RIPPLES.size() >= 8 || this.lastViewPos.z >= -0.05F) {
            return;
        }

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        org.joml.Vector4f center = projection.transform(new org.joml.Vector4f(this.lastViewPos, 1F));
        org.joml.Vector4f up = projection.transform(new org.joml.Vector4f(this.lastViewPos.x, this.lastViewPos.y + 1F, this.lastViewPos.z, 1F));

        if (center.w <= 0.0001F || up.w <= 0.0001F) {
            return;
        }

        float cx = center.x / center.w * 0.5F + 0.5F;
        float cy = center.y / center.w * 0.5F + 0.5F;
        /* One block at the source's distance, in frame heights */
        float perBlock = Math.abs(up.y / up.w - center.y / center.w) * 0.5F;

        if (!Float.isFinite(perBlock) || perBlock <= 0.0001F || cx < -1F || cx > 2F || cy < -1F || cy > 2F) {
            return;
        }

        mchorse.bbs_mod.camera.clips.fx.FxOverlay ripple = new mchorse.bbs_mod.camera.clips.fx.FxOverlay(mchorse.bbs_mod.camera.clips.fx.FxOverlay.RIPPLE);
        float width = (0.025F + 0.04F * Math.min(strength, 1.5F)) * 1.6F;

        ripple.set(0, cx).set(1, cy);
        /* The largest shift: a fraction of the band's own width, so a far source bends as little as it looks */
        ripple.set(2, width * perBlock * 0.45F * distortion * Math.min(strength, 1.5F));
        ripple.set(3, width * perBlock);

        for (int ring = 0; ring < WAVE_RINGS; ring++) {
            float phase = time / WAVE_PERIOD + ring / (float) WAVE_RINGS;
            float p = phase - (float) Math.floor(phase);

            ripple.set(4 + ring, (0.08F + p * reach) * perBlock);
            ripple.set(8 + ring, MathUtils.clamp((float) Math.sin(p * Math.PI) * (1F - p) * 1.4F, 0F, 1F));
        }

        ripple.set(12, this.lastViewPos.length());
        ripple.set(16, time).set(17, 0.07F * strength);

        RIPPLES.add(ripple);
    }

    private static final int WAVE_RINGS = 4;
    private static final int WAVE_SEGMENTS = 56;
    /** Seconds for one ring to travel from the source out to its widest */
    private static final float WAVE_PERIOD = 0.9F;

    /** How loud the sound is right now, eased: quick to rise with a hit, slow to let go of it */
    private float waveEnvelope;
    private long waveEnvelopeNs;

    /**
     * The loudness (RMS, 0..1) of the sound around a moment of it, read straight from the samples.
     * Taken from the film's time rather than from OpenAL, so an exported frame shows exactly what
     * the frame before it on screen did. Every 8th frame of a 40 ms window is plenty for a ripple.
     */
    private static float sampleLoudness(SoundBuffer buffer, float seconds) {
        mchorse.bbs_mod.audio.Wave wave = buffer == null ? null : buffer.getWave();

        if (wave == null || wave.data == null || wave.bitsPerSample != 16 || wave.sampleRate <= 0) {
            return 0F;
        }

        int frameBytes = wave.numChannels * 2;
        int frames = wave.data.length / frameBytes;
        int center = (int) (seconds * wave.sampleRate);
        int half = (int) (wave.sampleRate * 0.02F);
        int from = Math.max(0, center - half);
        int to = Math.min(frames, center + half);

        if (to <= from) {
            return 0F;
        }

        double sum = 0D;
        int count = 0;

        for (int frame = from; frame < to; frame += 8) {
            int at = frame * frameBytes;
            /* Little endian 16 bit, the channels averaged */
            double sample = 0D;

            for (int c = 0; c < wave.numChannels; c++) {
                int i = at + c * 2;
                short value = (short) ((wave.data[i] & 0xFF) | (wave.data[i + 1] << 8));

                sample += value / 32768D;
            }

            sample /= wave.numChannels;
            sum += sample * sample;
            count += 1;
        }

        return count == 0 ? 0F : MathUtils.clamp((float) Math.sqrt(sum / count), 0F, 1F);
    }

    /**
     * Rings that ripple out of the source, faint and camera facing, as wide and as bright as the
     * sound is loud this moment, with a tremble along their rims that grows with it. Drawn additively
     * and without depth writes, so they read as a disturbance in the air rather than an object.
     */
    private void renderWaves(FormRenderingContext context) {
        if (!this.form.waves.get() || context.isPicking() || BBSRendering.isIrisShadowPass() || !this.hasViewPos) {
            return;
        }

        Link sound = this.form.sound.get();
        SoundBuffer buffer = sound == null ? null : BBSModClient.getSounds().get(sound, false);
        float filmSec = this.getFilmTimelineSeconds(context.entity, context.getTransition()) - this.form.offset.get();
        float soundSec = filmSec * MathUtils.clamp(this.form.pitch.get(), 0.5F, 2.0F);
        float target = 0F;

        if (buffer != null && this.lastPlaybackState == PlaybackState.PLAYING && soundSec >= 0F) {
            float duration = buffer.getDuration();

            if (this.form.loop.get() && duration > 0F) {
                soundSec %= duration;
            }

            if (duration <= 0F || soundSec < duration) {
                /* Loudness heard in a quiet mix still makes a ripple: RMS of speech sits near 0.1 */
                target = MathUtils.clamp(sampleLoudness(buffer, soundSec) * 3.5F * this.form.volume.get(), 0F, 1.5F);
            }
        }

        long now = System.nanoTime();
        float dt = this.waveEnvelopeNs == 0L ? 0F : MathUtils.clamp((now - this.waveEnvelopeNs) / 1.0E9F, 0F, 0.25F);

        this.waveEnvelopeNs = now;
        this.waveEnvelope += (target - this.waveEnvelope) * MathUtils.clamp(dt / (target > this.waveEnvelope ? 0.03F : 0.25F), 0F, 1F);

        float strength = this.waveEnvelope * MathUtils.clamp(this.form.waveIntensity.get(), 0F, 4F);

        if (strength <= 0.003F) {
            return;
        }

        mchorse.bbs_mod.utils.colors.Color color = this.form.waveColor.get();
        Matrix4f matrix = new Matrix4f().translation(this.lastViewPos);
        float time = filmSec;
        float reach = 0.5F + 1.6F * Math.min(strength, 2F);
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int ring = 0; ring < WAVE_RINGS; ring++) {
            float phase = time / WAVE_PERIOD + ring / (float) WAVE_RINGS;
            float p = phase - (float) Math.floor(phase);
            float radius = 0.08F + p * reach;
            float width = (0.025F + 0.04F * Math.min(strength, 1.5F)) * (1F - p * 0.5F);
            /* Fades in as it leaves the source and out as it spreads */
            float alpha = MathUtils.clamp(strength * 0.32F * (float) Math.sin(p * Math.PI) * (1F - p), 0F, 0.6F);

            if (alpha <= 0.002F) {
                continue;
            }

            for (int i = 0; i < WAVE_SEGMENTS; i++) {
                float a0 = (float) (Math.PI * 2D * i / WAVE_SEGMENTS);
                float a1 = (float) (Math.PI * 2D * (i + 1) / WAVE_SEGMENTS);
                float r0 = radius * (1F + 0.07F * strength * (float) Math.sin(a0 * 6F + time * 11F + ring * 1.7F));
                float r1 = radius * (1F + 0.07F * strength * (float) Math.sin(a1 * 6F + time * 11F + ring * 1.7F));
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);

                /* A soft band: transparent at both rims, brightest along the middle */
                float in0 = r0 - width;
                float in1 = r1 - width;
                float out0 = r0 + width;
                float out1 = r1 + width;

                builder.vertex(matrix, c0 * in0, s0 * in0, 0F).color(color.r, color.g, color.b, 0F)/* $ nx */.next()/**/;
                builder.vertex(matrix, c0 * r0, s0 * r0, 0F).color(color.r, color.g, color.b, alpha)/* $ nx */.next()/**/;
                builder.vertex(matrix, c1 * r1, s1 * r1, 0F).color(color.r, color.g, color.b, alpha)/* $ nx */.next()/**/;
                builder.vertex(matrix, c0 * in0, s0 * in0, 0F).color(color.r, color.g, color.b, 0F)/* $ nx */.next()/**/;
                builder.vertex(matrix, c1 * r1, s1 * r1, 0F).color(color.r, color.g, color.b, alpha)/* $ nx */.next()/**/;
                builder.vertex(matrix, c1 * in1, s1 * in1, 0F).color(color.r, color.g, color.b, 0F)/* $ nx */.next()/**/;

                builder.vertex(matrix, c0 * r0, s0 * r0, 0F).color(color.r, color.g, color.b, alpha)/* $ nx */.next()/**/;
                builder.vertex(matrix, c0 * out0, s0 * out0, 0F).color(color.r, color.g, color.b, 0F)/* $ nx */.next()/**/;
                builder.vertex(matrix, c1 * out1, s1 * out1, 0F).color(color.r, color.g, color.b, 0F)/* $ nx */.next()/**/;
                builder.vertex(matrix, c0 * r0, s0 * r0, 0F).color(color.r, color.g, color.b, alpha)/* $ nx */.next()/**/;
                builder.vertex(matrix, c1 * out1, s1 * out1, 0F).color(color.r, color.g, color.b, 0F)/* $ nx */.next()/**/;
                builder.vertex(matrix, c1 * r1, s1 * r1, 0F).color(color.r, color.g, color.b, alpha)/* $ nx */.next()/**/;
            }
        }

        this.queueRipple(time, reach, strength);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Bufs.draw(builder);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
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

    public float getVelocityX() {
        return this.velocityX;
    }

    public float getVelocityY() {
        return this.velocityY;
    }

    public float getVelocityZ() {
        return this.velocityZ;
    }

    public float getCamVelocityX() {
        return this.camVelocityX;
    }

    public float getCamVelocityY() {
        return this.camVelocityY;
    }

    public float getCamVelocityZ() {
        return this.camVelocityZ;
    }
}
