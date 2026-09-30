package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.catalyst.CatalystMediaAsset;
import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.catalyst.CatalystProjectManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystCompTab;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystProjectList;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTab;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UIMediaPoolPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.camera.export.FFmpegCommandBuilder;
import mchorse.bbs_mod.camera.export.RenderJob;
import mchorse.bbs_mod.camera.export.RenderQueue;
import mchorse.bbs_mod.camera.export.VideoExportProfile;
import mchorse.bbs_mod.ui.film.export.UIDeliverOverlayPanel;
import mchorse.bbs_mod.ui.film.export.UIRenderMonitorHud;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.camera.controller.CatalystSceneCameraController;
import mchorse.bbs_mod.video.VideoPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.utils.resources.Pixels;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.utils.FFMpegUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;
import mchorse.bbs_mod.audio.Wave;

/**
 * UICatalystPanel — Catalyst Studio / Editor Dashboard Panel.
 *
 * Stage 34 changes:
 *  - Projects view now renders an opaque background; no bleed-through from editor views.
 *  - CatalystLayer uses LayerType enum. Add-layer button shows SOLID / SCENE quick options.
 *  - Timeline supports click-to-scrub and drag-scrub (mouseDown + mouseScroll mapped).
 *  - Play/Pause advances currentFrame at ~60 FPS via render-tick counter; playhead updates live.
 */
public class UICatalystPanel extends UIDashboardPanel
{
    public enum CatalystTab { PROJECTS, EDITOR }

    /* ── State ── */
    public CatalystTab    currentTab    = CatalystTab.PROJECTS;
    public CatalystProject activeProject = null;

    /* Playback state (frame-level) */
    private boolean isPlaying     = false;
    private int     currentFrame  = 0;
    private long    lastTickMs    = 0;


    private boolean isFullscreen = false;

    /* ── Top bar & tabs ── */
    public UIElement       topBar;
    public UICatalystTab   tabProjects;
    public UICatalystTab   tabEditor;
    public UILabel         activeProjectLabel;
    public UIElement       editorActions;
    public UIIcon          playPauseButton;
    public UIIcon          splitButton;
    public UIIcon          settingsButton;
    public UIIcon          renderExportButton;
    public UIIcon          fullscreenButton;
    public UIIcon          mediaPoolToggleBtn;
    public boolean         showMediaPool = true;
    public UIMediaPoolPanel mediaPoolPanel;
    public CatalystMediaAsset draggedAsset = null;
    private org.lwjgl.glfw.GLFWDropCallback prevDropCallback = null;

    /* ── View 1: Projects ── */
    public UIElement              projectsView;
    public UIElement              projectListColumn;
    public UIElement              projectListHeader;
    public UICatalystProjectList  projectList;
    public UIIcon                 newProjectHeaderBtn;
    public UIIcon                 openFolderBtn;
    public UIIcon                 refreshBtn;

    public UIElement     projectDetailsColumn;
    public UIScrollView  projectDetailsScroll;
    public UITextbox     nameInput;
    public UITrackpad    fpsInput;
    public UITrackpad    durationSecondsInput;
    public UILabel       durationCalcLabel;
    public UITrackpad    widthInput;
    public UITrackpad    heightInput;
    public UIButton      openProjectButton;
    public UIButton      deleteProjectButton;
    public UIButton      clearFormButton;

    /* ── View 2: Editor ── */
    public UIElement     previewArea;

    /* AE-style Comp Tab Strip */
    public UIElement     compTabStrip;
    public UIScrollView  compTabScroll;
    public UIIcon        addCompButton;

    public UIElement     bottomArea;
    public UIElement     layersContainer;
    public UIElement     layersHeader;
    public UIScrollView  layersList;
    public UICatalystTimeline catalystTimeline;

    /* ── Inspector Panel ── */
    public UIElement     inspectorContainer;
    public UIElement     inspectorHeader;
    public UIScrollView  inspectorScroll;
    public UIElement     inspectorForm;
    public UILabel       inspectorEmptyLabel;
    public UITextbox     layerNameInput;
    public UILabel       layerTypeLabel;
    public UITrackpad    layerOpacityInput;

    /* Text Layer controls */
    public UIElement     textControlsGroup;
    public UITextarea    layerTextarea;
    public UITrackpad    layerFontSizeInput;
    public UIColor       layerTextColorPicker;
    public UIButton      layerLineWrapButton;
    public UIButton      layerShadowButton;

    /* Transform / Pivot controls */
    public UIElement     transformGroup;
    public UIButton      layerResetTransformBtn;
    public UITrackpad    layerPosX;
    public UITrackpad    layerPosY;
    public UITrackpad    layerScaleX;
    public UITrackpad    layerScaleY;
    public UITrackpad    layerRotation;
    public UITrackpad    layerAnchorX;
    public UITrackpad    layerAnchorY;

    /* Resource label & media controls */
    public UILabel       layerResourceLabel;
    public UIElement     mediaGroup;
    public UIElement     audioControlsGroup;
    public UITrackpad    layerVolumeInput;
    public UITrackpad    layerPanInput;
    public UITrackpad    layerAudioOffsetInput;
    public UIButton      layerExtendDurationBtn;
    public UILabel       layerFilmFpsLabel;
    public UITrackpad    layerFilmFpsInput;

    public UIButton      layerBlendButton;
    public UIButton      layerVisibleButton;
    public UIButton      layerLockedButton;
    public UIElement     layerColorRow;
    public UIColor       layerColorPicker;
    private CatalystLayer lastInspectedLayer = null;

    /* ── Undo / Redo Stacks (Snapshots of active project) ── */
    private final List<mchorse.bbs_mod.data.types.MapType> undoStack = new java.util.ArrayList<>();
    private final List<mchorse.bbs_mod.data.types.MapType> redoStack = new java.util.ArrayList<>();
    private boolean isUndoingOrRedoing = false;

    /* ── Viewport Bounding Box Drag State ── */
    private int viewportDragMode = 0; // 0 = none, 1 = move, 2 = resize corner
    private int viewportDragStartX;
    private int viewportDragStartY;
    private float initialLayerPosX;
    private float initialLayerPosY;
    private float initialLayerScaleX;
    private float initialLayerScaleY;

    /* ── Multi-Track Audio Playback State (Stage 48.3) ── */
    private final Map<CatalystLayer, SoundPlayer> activeAudioPlayers = new HashMap<>();
    private final Map<CatalystLayer, Link> layerAudioLinks = new HashMap<>();
    private SoundPlayer masterClockPlayer = null;
    private boolean isScrubbing = false;
    private double lastCompRenderMs = 0.0;

    /* ── Live Audio Scrub Preview State ── */
    private int scrubSource = -1;
    private int scrubBuffer = -1;
    private ByteBuffer scrubByteBuffer = null;
    private long lastScrubPlayMs = 0L;

    /* ── SCENE Layer Camera State ── */
    /** Drives the BBS world-render camera to the film's position for SCENE layers. */
    private final CatalystSceneCameraController sceneCamera = new CatalystSceneCameraController();
    /** ID of the film currently bound to sceneCamera; used to detect film switching. */
    private String lastSceneFilmId = null;

    /* ── Offline Export State (Stage 48) ── */
    private boolean isExporting = false;
    private int exportCurrentFrame = 0;
    private int exportTotalFrames = 0;
    private int exportWidth = 1920;
    private int exportHeight = 1080;
    private int exportFps = 60;
    private Process exportProcess = null;
    private WritableByteChannel exportChannel = null;
    private Framebuffer exportFbo = null;
    private Texture exportTexture = null;
    private ByteBuffer exportBuffer = null;
    private File exportTargetFile = null;
    private File exportTempAudioMixFile = null;
    private String exportStatusMessage = "";
    private long exportStartTime = 0;

    public final UIRenderMonitorHud renderMonitorHud = new UIRenderMonitorHud();
    private RenderJob currentExportJob = null;
    private Runnable onExportFinishedCallback = null;
    private VideoExportProfile activeExportProfile = null;

    /* ── Constructor ── */

    public UICatalystPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.setupTopBar();
        this.setupProjectsView();
        this.setupEditorView();

        /* Order matters: projectsView on top of editor elements so no bleed */
        this.add(this.topBar,
                 this.mediaPoolPanel,
                 this.previewArea, this.compTabStrip, this.bottomArea,
                 this.projectsView);

        this.onAppear(() ->
        {
            this.refreshProjects();

            if (this.activeProject == null)
            {
                this.setTab(CatalystTab.PROJECTS);
            }
            else
            {
                this.setTab(this.currentTab);
            }

            /* Register our scene-layer camera controller so the BBS world render picks it up */
            BBSModClient.getCameraController().add(this.sceneCamera);

            /* Register GLFW window drop callback for desktop drag-and-drop */
            long win = Window.getWindow();
            this.prevDropCallback = org.lwjgl.glfw.GLFW.glfwSetDropCallback(win, (window, count, names) ->
            {
                if (this.mediaPoolPanel != null && this.isVisible())
                {
                    for (int i = 0; i < count; i++)
                    {
                        String filePath = org.lwjgl.glfw.GLFWDropCallback.getName(names, i);
                        File file = new File(filePath);
                        this.mediaPoolPanel.importFile(file);
                    }
                }
            });

            this.resize();
        });

        this.onDisappear(() ->
        {
            this.isPlaying = false;
            this.stopAllAudio();
            cleanupProjectResources(this.activeProject);
            if (this.isExporting)
            {
                this.cancelExport();
            }
            if (this.prevDropCallback != null)
            {
                org.lwjgl.glfw.GLFW.glfwSetDropCallback(Window.getWindow(), this.prevDropCallback);
                this.prevDropCallback = null;
            }
            /* Release off-screen FBO; UIFilmPanel.enterEditing() will re-enable it if needed */
            BBSRendering.setCustomSize(false);
            /* Deactivate scene camera and clean up frozen film state */
            this.sceneCamera.setFilmCamera(null);
            BBSModClient.getCameraController().remove(CatalystSceneCameraController.class);
            if (this.lastSceneFilmId != null)
            {
                try
                {
                    BBSModClient.getFilms().unfreeze(this.lastSceneFilmId);
                }
                catch (Exception ignored) {}
                this.lastSceneFilmId = null;
            }
        });

        this.onClose(() ->
        {
            this.isPlaying = false;
            this.stopAllAudio();
            cleanupProjectResources(this.activeProject);
            if (this.isExporting)
            {
                this.cancelExport();
            }
            if (this.prevDropCallback != null)
            {
                org.lwjgl.glfw.GLFW.glfwSetDropCallback(Window.getWindow(), this.prevDropCallback);
                this.prevDropCallback = null;
            }
            BBSRendering.setCustomSize(false);
            this.sceneCamera.setFilmCamera(null);
            BBSModClient.getCameraController().remove(CatalystSceneCameraController.class);
            if (this.lastSceneFilmId != null)
            {
                try
                {
                    BBSModClient.getFilms().unfreeze(this.lastSceneFilmId);
                }
                catch (Exception ignored) {}
                this.lastSceneFilmId = null;
            }
        });
    }

    @Override
    public boolean needsBackground()
    {
        return true;
    }

    /* ════════════════════════════════════════════════════════
     *  Tab Switcher
     * ════════════════════════════════════════════════════════ */

    public void setTab(CatalystTab tab)
    {
        if (tab == CatalystTab.EDITOR && this.activeProject == null)
        {
            List<CatalystProject> projects = CatalystProjectManager.loadAllProjects();

            if (!projects.isEmpty())
            {
                this.setActiveProject(projects.get(0));
            }
            else
            {
                this.currentTab = CatalystTab.PROJECTS;
                this.updateVisibility();
                return;
            }
        }

        this.currentTab = tab;
        this.updateVisibility();

        if (tab == CatalystTab.PROJECTS)
        {
            this.refreshProjects();
        }
        else
        {
            this.rebuildCompTabs();
        }

        this.resize();
    }

    private void updateVisibility()
    {
        boolean isEditor = (this.currentTab == CatalystTab.EDITOR && this.activeProject != null);

        /* Projects view renders its own full opaque background; hide it in editor mode */
        this.projectsView.setVisible(!isEditor);

        /* Editor elements shown only when in editor mode */
        this.previewArea.setVisible(isEditor);
        this.compTabStrip.setVisible(isEditor);
        this.bottomArea.setVisible(isEditor);
        this.editorActions.setVisible(isEditor);

        if (this.mediaPoolPanel != null)
        {
            this.mediaPoolPanel.setVisible(isEditor && this.showMediaPool && !this.isFullscreen);
        }

        this.updateTitleLabel();
    }

    private void updateTitleLabel()
    {
        if (this.activeProject != null)
        {
            CatalystComposition comp = this.activeProject.getActiveComposition();
            String compName = (comp != null) ? " > " + comp.name : "";
            String info = " • " + this.activeProject.name + compName
                        + " [" + this.activeProject.fps + " FPS / "
                        + String.format("%.1fs", this.activeProject.durationSeconds) + "]";
            this.activeProjectLabel.label = IKey.raw(info);
        }
        else
        {
            this.activeProjectLabel.label = IKey.raw("");
        }
    }

    public void setActiveProject(CatalystProject project)
    {
        if (this.activeProject != null && this.activeProject != project)
        {
            this.isPlaying = false;
            this.stopAllAudio();
            cleanupProjectResources(this.activeProject);
        }

        this.activeProject = project;

        if (project != null)
        {
            project.ensureCompositions();
            project.syncMediaPoolWithLayers();
            if (this.mediaPoolPanel != null)
            {
                this.mediaPoolPanel.rebuildList();
            }
            this.currentFrame = 0;

            this.nameInput.setText(project.name);
            this.fpsInput.setValue(project.fps);
            this.durationSecondsInput.setValue(project.durationSeconds);
            this.widthInput.setValue(project.width);
            this.heightInput.setValue(project.height);
            this.updateDurationCalcLabel();

            this.openProjectButton.label = UIKeys.CATALYST_PROJECTS_OPEN;
            this.deleteProjectButton.setVisible(true);

            this.rebuildCompTabs();
        }

        this.updateTitleLabel();
    }

    public void refreshProjects()
    {
        List<CatalystProject> projects = CatalystProjectManager.loadAllProjects();
        this.projectList.setList(projects);

        if (this.activeProject != null)
        {
            this.projectList.setCurrent(this.activeProject);
        }
    }

    private void updateDurationCalcLabel()
    {
        double sec = this.durationSecondsInput != null ? this.durationSecondsInput.getValue() : 5.0;
        int fps    = this.fpsInput != null ? (int) this.fpsInput.getValue() : 60;
        int total  = (int) Math.round(sec * fps);

        if (this.durationCalcLabel != null)
        {
            this.durationCalcLabel.label = IKey.raw(
                String.format("%.1f sn (%d Frame @ %d FPS)", sec, total, fps));
        }
    }

    /* ════════════════════════════════════════════════════════
     *  Playback helpers
     * ════════════════════════════════════════════════════════ */

    /* ════════════════════════════════════════════════════════
     *  Playback helpers (Audio-Driven Master Clock, Multi-Track Mixing & A/V Sync)
     * ════════════════════════════════════════════════════════ */

    /** Returns all active AUDIO and VIDEO layers at the given frame that have audio content. */
    private List<CatalystLayer> getActiveAudioLayers(CatalystComposition comp, int frame)
    {
        List<CatalystLayer> list = new ArrayList<>();
        if (comp == null) return list;

        boolean hasSolo = false;
        for (CatalystLayer l : comp.layers)
        {
            if (l.solo)
            {
                hasSolo = true;
                break;
            }
        }

        for (CatalystLayer layer : comp.layers)
        {
            if (hasSolo && !layer.solo) continue;
            if (layer.muted) continue;
            if ((layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.VIDEO)
                && frame >= layer.startFrame && frame < layer.startFrame + layer.duration
                && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty()
                && layer.volume > 0)
            {
                list.add(layer);
            }
        }
        return list;
    }

    /** Synchronizes all active audio streams (both AUDIO layers and VIDEO audio tracks) concurrently with 2D stereo and Pan. */
    private void syncAudioPlayback(CatalystComposition comp, boolean forceSeek)
    {
        if (comp == null) return;

        int fps = comp.fps > 0 ? comp.fps : 60;
        List<CatalystLayer> activeLayers = this.getActiveAudioLayers(comp, this.currentFrame);

        /* 1. Stop and remove audio players for layers that are no longer active */
        Iterator<Map.Entry<CatalystLayer, SoundPlayer>> it = this.activeAudioPlayers.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<CatalystLayer, SoundPlayer> entry = it.next();
            CatalystLayer layer = entry.getKey();
            SoundPlayer player = entry.getValue();

            if (!activeLayers.contains(layer))
            {
                if (player != null)
                {
                    if (player == this.masterClockPlayer)
                    {
                        this.masterClockPlayer = null;
                    }
                    try
                    {
                        player.stop();
                        player.delete();
                    }
                    catch (Exception ignored) {}
                }
                it.remove();
                this.layerAudioLinks.remove(layer);
            }
        }

        /* 2. For each active layer, play / update its dedicated SoundPlayer */
        for (CatalystLayer layer : activeLayers)
        {
            try
            {
                Link audioLink = Link.create(layer.resourcePath.trim());
                int relativeFrame = (this.currentFrame - layer.startFrame) + layer.mediaOffset;
                float relSec = (float) (relativeFrame + layer.audioOffset) / fps;

                if (relSec < 0)
                {
                    SoundPlayer p = this.activeAudioPlayers.get(layer);
                    if (p != null && p.isPlaying())
                    {
                        p.pause();
                    }
                    continue;
                }

                SoundPlayer player = this.activeAudioPlayers.get(layer);
                Link existingLink = this.layerAudioLinks.get(layer);

                if (player != null && player.getSource() > 0)
                {
                    if (audioLink.equals(existingLink))
                    {
                        float vol = layer.volume * (layer.opacity / 100.0F);
                        player.setVolume(vol);
                        player.setPan(layer.pan);

                        boolean isMaster = (player == this.masterClockPlayer);
                        if (forceSeek || !player.isPlaying() || (!isMaster && Math.abs(player.getPlaybackPosition() - relSec) > 0.15F))
                        {
                            player.setPlaybackPosition(relSec);
                        }
                        if (this.isPlaying && !player.isPlaying())
                        {
                            player.play();
                        }
                        else if (!this.isPlaying && player.isPlaying())
                        {
                            player.pause();
                        }
                        continue;
                    }
                    else
                    {
                        /* Resource link changed, reload buffer */
                        if (player == this.masterClockPlayer)
                        {
                            this.masterClockPlayer = null;
                        }
                        player.stop();
                        player.delete();
                        this.activeAudioPlayers.remove(layer);
                        this.layerAudioLinks.remove(layer);
                    }
                }

                SoundBuffer buffer = BBSModClient.getSounds().get(audioLink, false);
                if (buffer != null)
                {
                    SoundPlayer newPlayer = new SoundPlayer(buffer);
                    /* 2D stereo: source relative to listener at origin with 0 velocity and 0 rolloff —
                     * prevents 3D distance attenuation and spatial mono downmixing, preserving AL_FORMAT_STEREO16. */
                    newPlayer.configure2DStereo();
                    newPlayer.setPan(layer.pan);
                    newPlayer.setPlaybackPosition(relSec);
                    newPlayer.setVolume(layer.volume * (layer.opacity / 100.0F));

                    if (this.isPlaying)
                    {
                        newPlayer.play();
                    }
                    this.activeAudioPlayers.put(layer, newPlayer);
                    this.layerAudioLinks.put(layer, audioLink);
                }
            }
            catch (Exception ignored) {}
        }
    }

    /** Called from editor render every frame; advances playhead according to Audio Master Clock or high-precision wall clock. */
    private void tickPlayback(CatalystComposition comp)
    {
        if (!this.isPlaying || comp == null)
        {
            this.lastTickMs = 0;
            return;
        }

        int fps = comp.fps > 0 ? comp.fps : 60;
        this.syncAudioPlayback(comp, false);

        /* 1. Follow primary audio master clock if any active stream is playing */
        CatalystLayer masterLayer = null;
        SoundPlayer masterPlayer = null;

        /* Prefer dedicated AUDIO layers as master clock first */
        for (Map.Entry<CatalystLayer, SoundPlayer> entry : this.activeAudioPlayers.entrySet())
        {
            if (entry.getKey().layerType == CatalystLayer.LayerType.AUDIO && entry.getValue() != null && entry.getValue().isPlaying())
            {
                masterLayer = entry.getKey();
                masterPlayer = entry.getValue();
                break;
            }
        }
        /* Otherwise, fall back to active VIDEO audio player */
        if (masterLayer == null)
        {
            for (Map.Entry<CatalystLayer, SoundPlayer> entry : this.activeAudioPlayers.entrySet())
            {
                if (entry.getValue() != null && entry.getValue().isPlaying())
                {
                    masterLayer = entry.getKey();
                    masterPlayer = entry.getValue();
                    break;
                }
            }
        }

        if (masterLayer != null && masterPlayer != null)
        {
            this.masterClockPlayer = masterPlayer;
            float audioSec = masterPlayer.getPlaybackPosition();
            int mediaRelFrame = (int) Math.round(audioSec * fps) - masterLayer.audioOffset;
            int syncedFrame = (masterLayer.startFrame - masterLayer.mediaOffset) + mediaRelFrame;

            /* Audio stream drives the master timeline playhead smoothly */
            if (syncedFrame >= this.currentFrame)
            {
                this.currentFrame = syncedFrame;
            }
            else if (this.currentFrame - syncedFrame > 4)
            {
                /* Large drift fallback: synchronize without stuttering */
                this.currentFrame = syncedFrame;
            }

            if (this.currentFrame >= comp.duration)
            {
                this.currentFrame = 0;
                this.stopAllAudio();
                this.syncAudioPlayback(comp, true);
            }

            comp.playhead = this.currentFrame;
            this.lastTickMs = System.currentTimeMillis();
            return;
        }

        this.masterClockPlayer = null;

        /* 2. Fallback to monotonic time clock when no audio is driving */
        long now = System.currentTimeMillis();
        if (this.lastTickMs == 0)
        {
            this.lastTickMs = now;
            return;
        }

        long elapsed = now - this.lastTickMs;
        int advance  = (int) (elapsed * fps / 1000L);

        if (advance > 0)
        {
            this.lastTickMs += (long) advance * 1000L / fps;
            this.currentFrame += advance;

            if (this.currentFrame >= comp.duration)
            {
                this.currentFrame = 0; /* loop */
                this.stopAllAudio();
            }

            comp.playhead = this.currentFrame;
            this.syncAudioPlayback(comp, true);
        }
    }

    /** Scrubbing (manual timeline slide) seeks audio and video instantly with synchronous frame decoding */
    public void seekToFrame(int frame)
    {
        CatalystComposition comp = this.activeProject != null ? this.activeProject.getActiveComposition() : null;
        if (comp == null) return;

        this.currentFrame = Math.max(0, Math.min(frame, comp.duration - 1));
        comp.playhead = this.currentFrame;

        int fps = comp.fps > 0 ? comp.fps : 60;

        /* Seek all video layers immediately for instant scrubbing preview */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.visible && layer.layerType == CatalystLayer.LayerType.VIDEO
                && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty()
                && this.currentFrame >= layer.startFrame && this.currentFrame < layer.startFrame + layer.duration)
            {
                try
                {
                    Link link = Link.create(layer.resourcePath.trim());
                    VideoPlayer player = BBSModClient.getVideos().getPlayer(layer, link);
                    if (player != null)
                    {
                        int relativeFrame = (this.currentFrame - layer.startFrame) + layer.mediaOffset;
                        float relSec = (float) (relativeFrame + layer.audioOffset) / fps;
                        Texture tex = player.seekFrame(relSec);
                        if (tex != null && tex.isValid())
                        {
                            layer.cachedVideoTexture = tex;
                            float playerFps = player.getFps() > 0 ? player.getFps() : 30F;
                            layer.lastVideoFrameIndex = (int) Math.round(relSec * playerFps);
                        }
                    }
                }
                catch (Exception ignored) {}
            }
        }

        /* Seek all active audio layers */
        this.syncAudioPlayback(comp, true);

        /* Scrub Audio Feedback when paused/scrubbing */
        if (!this.isPlaying)
        {
            this.playScrubAudio(comp, this.currentFrame);
        }
    }

    /**
     * Plays a high-fidelity 32-bit float stereo audio preview burst when scrubbing the playhead.
     * Features dynamic headroom protection, stereo panning, and analog tanh soft-clipping.
     */
    private void playScrubAudio(CatalystComposition comp, int frame)
    {
        if (comp == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        /* Throttle scrub bursts slightly to prevent audio driver queue clogging (min 30ms interval) */
        if (now - this.lastScrubPlayMs < 30L)
        {
            return;
        }
        this.lastScrubPlayMs = now;

        List<CatalystLayer> activeLayers = this.getActiveAudioLayers(comp, frame);
        if (activeLayers.isEmpty())
        {
            return;
        }

        int fps = comp.fps > 0 ? comp.fps : 60;
        int scrubSamples = Math.max(1600, (int) Math.round(48000.0 * (1.5 / fps)));
        float[] scrubLeft = new float[scrubSamples];
        float[] scrubRight = new float[scrubSamples];
        boolean hasSamples = false;

        for (CatalystLayer layer : activeLayers)
        {
            try
            {
                int relativeFrame = (frame - layer.startFrame) + layer.mediaOffset;
                float startSec = (float) (relativeFrame + layer.audioOffset) / fps;
                if (startSec < 0)
                {
                    continue;
                }

                Link link = Link.create(layer.resourcePath.trim());
                SoundBuffer sb = BBSModClient.getSounds().get(link, false);
                if (sb == null || sb.getWave() == null)
                {
                    continue;
                }

                Wave wave = sb.getWave();
                int waveRate = wave.sampleRate > 0 ? wave.sampleRate : 48000;
                int srcStartSample = (int) Math.round(startSec * (double) waveRate);
                int totalSrcSamples = wave.data.length / (wave.numChannels * 2);

                float vol = layer.volume * (layer.opacity / 100.0F);
                float pan = Math.max(-1.0F, Math.min(1.0F, layer.pan));
                float panL = pan <= 0.0F ? 1.0F : (1.0F - pan);
                float panR = pan >= 0.0F ? 1.0F : (1.0F + pan);
                float gainL = vol * panL;
                float gainR = vol * panR;

                double step = (double) waveRate / 48000.0;
                boolean isStereo = wave.numChannels >= 2;

                for (int i = 0; i < scrubSamples; i++)
                {
                    int sIdx = srcStartSample + (int) Math.round(i * step);
                    if (sIdx < 0)
                    {
                        continue;
                    }
                    if (sIdx >= totalSrcSamples)
                    {
                        break;
                    }

                    hasSamples = true;
                    float sampleL = (wave.getSample16(sIdx, 0) / 32768.0F) * gainL;
                    float sampleR = isStereo ? ((wave.getSample16(sIdx, 1) / 32768.0F) * gainR) : (sampleL * gainR / (gainL == 0.0F ? 1.0F : gainL));

                    scrubLeft[i] += sampleL;
                    scrubRight[i] += sampleR;
                }
            }
            catch (Exception ignored) {}
        }

        if (!hasSamples)
        {
            return;
        }

        /* Peak amplitude scan */
        float peak = 0.0F;
        for (int i = 0; i < scrubSamples; i++)
        {
            float aL = Math.abs(scrubLeft[i]);
            float aR = Math.abs(scrubRight[i]);
            if (aL > peak)
            {
                peak = aL;
            }
            if (aR > peak)
            {
                peak = aR;
            }
        }
        float scale = peak > 1.25F ? (1.25F / peak) : 1.0F;

        int byteCount = scrubSamples * 4;
        if (this.scrubByteBuffer == null || this.scrubByteBuffer.capacity() < byteCount)
        {
            if (this.scrubByteBuffer != null)
            {
                MemoryUtil.memFree(this.scrubByteBuffer);
            }
            this.scrubByteBuffer = MemoryUtil.memAlloc(byteCount);
        }

        this.scrubByteBuffer.clear();
        for (int i = 0; i < scrubSamples; i++)
        {
            float l = AudioRenderer.softClip(scrubLeft[i] * scale);
            float r = AudioRenderer.softClip(scrubRight[i] * scale);

            short sL = (short) Math.max(-32768, Math.min(32767, Math.round(l * 32767.0F)));
            short sR = (short) Math.max(-32768, Math.min(32767, Math.round(r * 32767.0F)));

            this.scrubByteBuffer.put((byte) (sL & 0xFF));
            this.scrubByteBuffer.put((byte) ((sL >> 8) & 0xFF));
            this.scrubByteBuffer.put((byte) (sR & 0xFF));
            this.scrubByteBuffer.put((byte) ((sR >> 8) & 0xFF));
        }
        this.scrubByteBuffer.flip();

        if (this.scrubSource <= 0)
        {
            this.scrubSource = AL10.alGenSources();
            AL10.alSourcei(this.scrubSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(this.scrubSource, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
            AL10.alSource3f(this.scrubSource, AL10.AL_VELOCITY, 0.0F, 0.0F, 0.0F);
            AL10.alSourcef(this.scrubSource, AL10.AL_ROLLOFF_FACTOR, 0.0F);
            AL10.alSourcef(this.scrubSource, AL10.AL_GAIN, 1.0F);
        }
        if (this.scrubBuffer <= 0)
        {
            this.scrubBuffer = AL10.alGenBuffers();
        }

        try
        {
            AL10.alSourceStop(this.scrubSource);
            AL10.alSourcei(this.scrubSource, AL10.AL_BUFFER, 0);
            AL10.alBufferData(this.scrubBuffer, AL10.AL_FORMAT_STEREO16, this.scrubByteBuffer, 48000);
            AL10.alSourcei(this.scrubSource, AL10.AL_BUFFER, this.scrubBuffer);
            AL10.alSourcePlay(this.scrubSource);
        }
        catch (Exception ignored) {}
    }



    /* ════════════════════════════════════════════════════════
     *  TOP BAR
     * ════════════════════════════════════════════════════════ */

    private void setupTopBar()
    {
        this.topBar = new UIElement();
        this.topBar.relative(this).w(1F).h(24);

        this.topBar.add(new UIRenderable((context) ->
        {
            Area a = this.topBar.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.chromeSurface());
            context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), BBSSettings.dividerColor());
        }));

        /* Tab buttons */
        this.tabProjects = new UICatalystTab(Icons.FOLDER, UIKeys.CATALYST_TAB_PROJECTS,
            () -> this.currentTab == CatalystTab.PROJECTS,
            (b) -> this.setTab(CatalystTab.PROJECTS));
        this.tabProjects.relative(this.topBar).x(4).y(2).w(80).h(20);

        this.tabEditor = new UICatalystTab(Icons.FILM, UIKeys.CATALYST_TAB_EDITOR,
            () -> this.currentTab == CatalystTab.EDITOR,
            (b) -> this.setTab(CatalystTab.EDITOR));
        this.tabEditor.relative(this.topBar).x(88).y(2).w(75).h(20);

        this.activeProjectLabel = new UILabel(IKey.raw(""));
        this.activeProjectLabel.relative(this.topBar).x(170).y(4).w(340).h(16);

        /* Action buttons — editor-only group */
        this.editorActions = new UIElement();
        this.editorActions.relative(this.topBar).x(1F, -118).y(2).w(114).h(20);

        this.mediaPoolToggleBtn = new UIIcon(Icons.SAVED, (b) -> this.toggleMediaPool());
        this.mediaPoolToggleBtn.tooltip(IKey.raw("Toggle Media Pool (B)"));

        this.playPauseButton = new UIIcon(() -> this.isPlaying ? Icons.PAUSE : Icons.PLAY, (b) -> this.togglePlayback());
        this.playPauseButton.tooltip(IKey.raw("Play / Pause (Space)"));

        this.settingsButton = new UIIcon(Icons.GEAR, (b) -> this.openCompositionSettingsModal());
        this.settingsButton.tooltip(IKey.raw("Composition Settings"));

        this.renderExportButton = new UIIcon(Icons.VIDEO_CAMERA, (b) -> this.openExportModal());
        this.renderExportButton.tooltip(IKey.raw("Render / Export Video"));

        this.fullscreenButton = new UIIcon(Icons.FULLSCREEN, (b) -> this.toggleFullscreen());
        this.fullscreenButton.tooltip(IKey.raw("Maximize Viewport"));

        this.mediaPoolToggleBtn.relative(this.editorActions).x(0).w(20).h(20);
        this.playPauseButton.relative(this.editorActions).x(22).w(20).h(20);
        this.settingsButton.relative(this.editorActions).x(44).w(20).h(20);
        this.renderExportButton.relative(this.editorActions).x(66).w(20).h(20);
        this.fullscreenButton.relative(this.editorActions).x(88).w(20).h(20);

        this.editorActions.add(this.mediaPoolToggleBtn, this.playPauseButton,
                               this.settingsButton, this.renderExportButton, this.fullscreenButton);

        this.topBar.add(this.tabProjects, this.tabEditor, this.activeProjectLabel, this.editorActions);
    }

    public void togglePlayback()
    {
        this.isPlaying = !this.isPlaying;
        this.lastTickMs = 0;
        if (!this.isPlaying)
        {
            this.stopAllAudio();
        }
        /* When starting playback, tickPlayback() will call ensureAudioPlaying() on the next render frame */
    }

    public void toggleFullscreen()
    {
        this.isFullscreen = !this.isFullscreen;

        if (this.isFullscreen)
        {
            this.compTabStrip.setVisible(false);
            this.bottomArea.setVisible(false);
            if (this.mediaPoolPanel != null)
            {
                this.mediaPoolPanel.setVisible(false);
            }
            this.previewArea.relative(this).xy(0, 24).w(1F).h(1F, -24);
        }
        else
        {
            this.compTabStrip.setVisible(true);
            this.bottomArea.setVisible(true);
            this.updateMediaPoolLayout();
        }

        this.resize();
    }

    public void updateMediaPoolLayout()
    {
        boolean isEditor = (this.currentTab == CatalystTab.EDITOR && this.activeProject != null);

        if (this.mediaPoolPanel == null || this.previewArea == null)
        {
            return;
        }

        if (isEditor && this.showMediaPool && !this.isFullscreen)
        {
            this.mediaPoolPanel.setVisible(true);
            this.mediaPoolPanel.relative(this).y(24).w(240).h(0.53F, -46);
            this.previewArea.relative(this).x(240).y(24).w(1F, -240).h(0.53F, -46);
        }
        else
        {
            this.mediaPoolPanel.setVisible(false);
            if (this.isFullscreen)
            {
                this.previewArea.relative(this).x(0).y(24).w(1F).h(1F, -24);
            }
            else
            {
                this.previewArea.relative(this).x(0).y(24).w(1F).h(0.53F, -46);
            }
        }

        this.resize();
    }

    public void toggleMediaPool()
    {
        this.showMediaPool = !this.showMediaPool;
        this.updateMediaPoolLayout();
    }

    /* ════════════════════════════════════════════════════════
     *  PROJECTS VIEW
     * ════════════════════════════════════════════════════════ */

    private void setupProjectsView()
    {
        this.projectsView = new UIElement();
        this.projectsView.relative(this).y(24).w(1F).h(1F, -24);

        /* Solid opaque background — completely covers editor views below */
        this.projectsView.add(new UIRenderable((context) ->
        {
            Area a = this.projectsView.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.deepSurface());
        }));

        /* Left Column */
        this.projectListColumn = new UIElement();
        this.projectListColumn.relative(this.projectsView).w(320).h(1F);

        this.projectListHeader = new UIElement();
        this.projectListHeader.relative(this.projectListColumn).w(1F).h(24);
        this.projectListHeader.add(new UIRenderable((context) ->
        {
            Area a = this.projectListHeader.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.chromeSurface());
            context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), BBSSettings.dividerColor());

            FontRenderer font = context.batcher.getFont();
            String lbl = UIKeys.CATALYST_PROJECTS_TITLE.get() + " (" + this.projectList.getList().size() + ")";
            context.batcher.text(lbl, a.x + 8, a.y + (a.h - font.getHeight()) / 2, Colors.WHITE, false);
        }));

        this.newProjectHeaderBtn = new UIIcon(Icons.ADD, (b) -> this.clearForm());
        this.newProjectHeaderBtn.tooltip(UIKeys.CATALYST_PROJECTS_NEW);

        this.openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(CatalystProjectManager.getProjectsFolder()));
        this.openFolderBtn.tooltip(IKey.raw("Open Projects Folder"));

        this.refreshBtn = new UIIcon(Icons.REFRESH, (b) -> this.refreshProjects());
        this.refreshBtn.tooltip(IKey.raw("Refresh List"));

        this.refreshBtn.relative(this.projectListHeader).x(1F, -22).y(2).w(20).h(20);
        this.openFolderBtn.relative(this.projectListHeader).x(1F, -44).y(2).w(20).h(20);
        this.newProjectHeaderBtn.relative(this.projectListHeader).x(1F, -66).y(2).w(20).h(20);
        this.projectListHeader.add(this.refreshBtn, this.openFolderBtn, this.newProjectHeaderBtn);

        this.projectList = new UICatalystProjectList((selected) ->
        {
            if (!selected.isEmpty()) this.setActiveProject(selected.get(0));
        });
        this.projectList.onOpen((project) ->
        {
            this.setActiveProject(project);
            this.setTab(CatalystTab.EDITOR);
        });
        this.projectList.relative(this.projectListColumn).y(24).w(1F).h(1F, -24);

        this.projectListColumn.add(new UIRenderable((context) ->
        {
            Area a = this.projectListColumn.area;
            context.batcher.box(a.ex() - 1, a.y, a.ex(), a.ey(), BBSSettings.dividerColor());
        }));
        this.projectListColumn.add(this.projectListHeader, this.projectList);

        /* Right Column */
        this.projectDetailsColumn = new UIElement();
        this.projectDetailsColumn.relative(this.projectsView).x(320).w(1F, -320).h(1F);

        this.projectDetailsColumn.add(new UIRenderable((context) ->
        {
            Area a = this.projectDetailsColumn.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.deepSurface());
        }));

        /* Form controls */
        this.nameInput = new UITextbox(120, (text) -> {});
        this.nameInput.setText("New Project");

        this.fpsInput = new UITrackpad((v) -> this.updateDurationCalcLabel());
        this.fpsInput.limit(1, 240, true).setValue(60);

        this.durationSecondsInput = new UITrackpad((v) -> this.updateDurationCalcLabel());
        this.durationSecondsInput.limit(0.1, 3600.0).setValue(5.0);

        this.durationCalcLabel = new UILabel(IKey.raw("5.0 sn (300 Frame @ 60 FPS)"), Colors.GRAY);

        this.widthInput = new UITrackpad((v) -> {});
        this.widthInput.limit(128, 7680, true).setValue(1920);

        this.heightInput = new UITrackpad((v) -> {});
        this.heightInput.limit(128, 4320, true).setValue(1080);

        this.openProjectButton  = new UIButton(UIKeys.CATALYST_PROJECTS_CREATE_OPEN, (b) -> this.openOrCreateProject());
        this.clearFormButton    = new UIButton(UIKeys.CATALYST_PROJECTS_NEW, (b) -> this.clearForm());
        this.deleteProjectButton= new UIButton(UIKeys.CATALYST_PROJECTS_DELETE, (b) -> this.deleteSelectedProject());
        this.deleteProjectButton.color(0xAA882222, 0xCCAA3333);
        this.deleteProjectButton.setVisible(false);

        this.nameInput.h(20);
        this.fpsInput.h(20);
        this.durationSecondsInput.h(20);
        this.durationCalcLabel.h(16);
        this.widthInput.h(20);
        this.heightInput.h(20);
        this.openProjectButton.h(20);
        this.clearFormButton.h(20);
        this.deleteProjectButton.h(20);

        UIElement formContainer = UI.column(
            6,
            UI.label(UIKeys.CATALYST_PROJECTS_NAME).h(16),
            this.nameInput,
            UI.label(UIKeys.CATALYST_PROJECTS_FPS).h(16),
            this.fpsInput,
            UI.label(UIKeys.CATALYST_PROJECTS_DURATION).h(16),
            this.durationSecondsInput,
            this.durationCalcLabel,
            UI.label(UIKeys.CATALYST_PROJECTS_RESOLUTION).h(16),
            UI.row(this.widthInput, this.heightInput),
            UI.row(this.openProjectButton, this.clearFormButton),
            this.deleteProjectButton
        );

        this.projectDetailsScroll = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, formContainer);
        this.projectDetailsScroll.relative(this.projectDetailsColumn).w(1F).h(1F);
        this.projectDetailsColumn.add(this.projectDetailsScroll);

        this.projectsView.add(this.projectListColumn, this.projectDetailsColumn);
    }

    private void openOrCreateProject()
    {
        String name = this.nameInput.getText().trim();
        if (name.isEmpty()) name = "Project_" + (System.currentTimeMillis() % 10000);

        int    fps             = (int) this.fpsInput.getValue();
        double durationSeconds = this.durationSecondsInput.getValue();
        int    width           = (int) this.widthInput.getValue();
        int    height          = (int) this.heightInput.getValue();

        CatalystProject project = new CatalystProject(name, fps, durationSeconds);
        project.width  = width  > 0 ? width  : 1920;
        project.height = height > 0 ? height : 1080;

        CatalystProjectManager.saveProject(project);
        this.setActiveProject(project);
        this.refreshProjects();
        this.setTab(CatalystTab.EDITOR);
    }

    private void deleteSelectedProject()
    {
        if (this.activeProject != null)
        {
            this.isPlaying = false;
            this.stopAllAudio();
            cleanupProjectResources(this.activeProject);
            CatalystProjectManager.deleteProject(this.activeProject);
            this.activeProject = null;
            this.clearForm();
            this.refreshProjects();
        }
    }

    private void clearForm()
    {
        if (this.activeProject != null)
        {
            this.isPlaying = false;
            this.stopAllAudio();
            cleanupProjectResources(this.activeProject);
            this.activeProject = null;
        }
        this.nameInput.setText("New Project");
        this.fpsInput.setValue(60);
        this.durationSecondsInput.setValue(5.0);
        this.widthInput.setValue(1920);
        this.heightInput.setValue(1080);
        this.updateDurationCalcLabel();
        this.openProjectButton.label = UIKeys.CATALYST_PROJECTS_CREATE_OPEN;
        this.deleteProjectButton.setVisible(false);
        this.updateTitleLabel();
    }

    /* ════════════════════════════════════════════════════════
     *  EDITOR VIEW
     * ════════════════════════════════════════════════════════ */

    private void setupEditorView()
    {
        this.setupMediaPoolPanel();
        this.setupPreviewArea();
        this.setupCompTabStrip();
        this.setupBottomArea();
    }

    private void setupMediaPoolPanel()
    {
        this.mediaPoolPanel = new UIMediaPoolPanel(
            this,
            () -> this.activeProject,
            (asset) -> this.dropAssetToTimeline(asset, this.currentFrame),
            (asset) -> this.draggedAsset = asset
        );
        this.updateMediaPoolLayout();
    }

    public void dropAssetToTimeline(CatalystMediaAsset asset, int startFrame)
    {
        CatalystComposition comp = this.activeProject != null ? this.activeProject.getActiveComposition() : null;
        if (comp == null || asset == null)
        {
            return;
        }

        String normPath = asset.path != null ? new File(asset.path).getAbsolutePath().replace('\\', '/') : "";
        File checkFile = new File(normPath);
        if (!checkFile.exists())
        {
            return;
        }

        this.pushUndo();

        CatalystLayer layer = new CatalystLayer();
        layer.name = asset.name;
        layer.resourcePath = normPath;
        layer.startFrame = Math.max(0, startFrame);

        if (asset.type == CatalystMediaAsset.MediaType.VIDEO)
        {
            layer.layerType = CatalystLayer.LayerType.VIDEO;
            layer.duration = asset.durationFrames > 0 ? asset.durationFrames : 150;
            layer.mediaDuration = layer.duration;
            layer.color = 0x3366BB;
        }
        else if (asset.type == CatalystMediaAsset.MediaType.AUDIO)
        {
            layer.layerType = CatalystLayer.LayerType.AUDIO;
            layer.duration = asset.durationFrames > 0 ? asset.durationFrames : 150;
            layer.mediaDuration = layer.duration;
            layer.color = 0x228855;
            UICatalystTimeline.ensureWaveformLoaded(layer);
        }
        else
        {
            layer.layerType = CatalystLayer.LayerType.IMAGE;
            layer.duration = 150;
            layer.mediaDuration = 0;
            layer.color = 0xAA6633;
        }

        comp.layers.add(layer);
        if (this.catalystTimeline != null)
        {
            this.catalystTimeline.setSelected(layer);
        }
        this.saveAndRefresh();
        this.updateInspectorForm();
    }

    private void setupPreviewArea()
    {
        this.previewArea = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    CatalystLayer sel = getSelectedLayer();
                    CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                    if (sel != null && comp != null)
                    {
                        int margin = 16;
                        int compW = comp.width > 0 ? comp.width : 1920;
                        int compH = comp.height > 0 ? comp.height : 1080;
                        float scale = Math.min((float) (this.area.w - margin * 2) / compW, (float) (this.area.h - margin * 2) / compH);
                        if (scale <= 0.0001F) scale = 1.0F;
                        float offsetX = this.area.x + (this.area.w - compW * scale) / 2.0F;
                        float offsetY = this.area.y + (this.area.h - compH * scale) / 2.0F;

                        float vMouseX = (context.mouseX - offsetX) / scale;
                        float vMouseY = (context.mouseY - offsetY) / scale;

                        float[] seff = sel.computeEffective(comp.playhead);
                        float[] sdims = UICatalystPanel.this.getLayerDimensions(sel, compW, compH, seff[2], seff[3]);
                        float selW = sdims[0];
                        float selH = sdims[1];
                        float bx = (compW / 2.0F + seff[0]) - selW * seff[6];
                        float by = (compH / 2.0F + seff[1]) - selH * seff[7];
                        float pivotX = bx + selW * seff[6];
                        float pivotY = by + selH * seff[7];

                        float minBx = Math.min(bx, bx + selW);
                        float maxBx = Math.max(bx, bx + selW);
                        float minBy = Math.min(by, by + selH);
                        float maxBy = Math.max(by, by + selH);

                        /* Check corner handles first (resize) */
                        int handleS = Math.max(6, Math.round(12 / scale));
                        int[][] corners = new int[][] {
                            {(int) minBx, (int) minBy},
                            {(int) maxBx, (int) minBy},
                            {(int) maxBx, (int) maxBy},
                            {(int) minBx, (int) maxBy}
                        };

                        double rad = Math.toRadians(seff[4]);
                        double cos = Math.cos(rad);
                        double sin = Math.sin(rad);

                        for (int[] pt : corners)
                        {
                            double rptX = pivotX + (pt[0] - pivotX) * cos - (pt[1] - pivotY) * sin;
                            double rptY = pivotY + (pt[0] - pivotX) * sin + (pt[1] - pivotY) * cos;
                            if (Math.abs(vMouseX - rptX) <= handleS && Math.abs(vMouseY - rptY) <= handleS)
                            {
                                UICatalystPanel.this.pushUndo();
                                viewportDragMode = 2; /* Resize */
                                viewportDragStartX = context.mouseX;
                                viewportDragStartY = context.mouseY;
                                initialLayerScaleX = sel.scaleX;
                                initialLayerScaleY = sel.scaleY;
                                return true;
                            }
                        }

                        /* Check body (move) - test unrotated point */
                        double unRotX = pivotX + (vMouseX - pivotX) * cos + (vMouseY - pivotY) * sin;
                        double unRotY = pivotY - (vMouseX - pivotX) * sin + (vMouseY - pivotY) * cos;
                        if (unRotX >= minBx && unRotX <= maxBx && unRotY >= minBy && unRotY <= maxBy)
                        {
                            UICatalystPanel.this.pushUndo();
                            viewportDragMode = 1; /* Move */
                            viewportDragStartX = context.mouseX;
                            viewportDragStartY = context.mouseY;
                            initialLayerPosX = sel.posX;
                            initialLayerPosY = sel.posY;
                            return true;
                        }
                    }
                }
                return super.subMouseClicked(context);
            }

            @Override
            protected boolean subMouseReleased(UIContext context)
            {
                if (viewportDragMode != 0)
                {
                    CatalystLayer sel = UICatalystPanel.this.getSelectedLayer();
                    if (sel != null)
                    {
                        if (viewportDragMode == 1)
                        {
                            if (sel.animPosX || sel.animPosY || !sel.channelPosX.isEmpty() || !sel.channelPosY.isEmpty())
                            {
                                sel.updatePropertyValue(UICatalystTimeline.PROP_POSITION, UICatalystPanel.this.currentFrame, sel.posX, sel.posY);
                            }
                        }
                        else if (viewportDragMode == 2)
                        {
                            if (sel.animScaleX || sel.animScaleY || !sel.channelScaleX.isEmpty() || !sel.channelScaleY.isEmpty())
                            {
                                sel.updatePropertyValue(UICatalystTimeline.PROP_SCALE, UICatalystPanel.this.currentFrame, sel.scaleX, sel.scaleY);
                            }
                        }
                    }
                    viewportDragMode = 0;
                    UICatalystPanel.this.saveAndRefresh();
                    UICatalystPanel.this.updateInspectorForm();
                }
                return super.subMouseReleased(context);
            }

            @Override
            public void render(UIContext context)
            {
                if (viewportDragMode != 0)
                {
                    CatalystLayer sel = getSelectedLayer();
                    CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                    if (sel != null && comp != null)
                    {
                        int margin = 16;
                        int compW = comp.width > 0 ? comp.width : 1920;
                        int compH = comp.height > 0 ? comp.height : 1080;
                        float scale = Math.min((float) (this.area.w - margin * 2) / compW, (float) (this.area.h - margin * 2) / compH);
                        if (scale <= 0.0001F) scale = 1.0F;

                        float vDx = (context.mouseX - viewportDragStartX) / scale;
                        float vDy = (context.mouseY - viewportDragStartY) / scale;

                        if (viewportDragMode == 1)
                        {
                            sel.posX = initialLayerPosX + vDx;
                            sel.posY = initialLayerPosY + vDy;
                        }
                        else if (viewportDragMode == 2)
                        {
                            float signX = Math.signum(initialLayerScaleX) != 0F ? Math.signum(initialLayerScaleX) : 1F;
                            float signY = Math.signum(initialLayerScaleY) != 0F ? Math.signum(initialLayerScaleY) : 1F;
                            if (Window.isShiftPressed())
                            {
                                float factorX = 1.0F + vDx / (compW * 0.25F);
                                float factorY = 1.0F + vDy / (compH * 0.25F);
                                sel.scaleX = signX * Math.max(0.05F, Math.abs(initialLayerScaleX * factorX));
                                sel.scaleY = signY * Math.max(0.05F, Math.abs(initialLayerScaleY * factorY));
                            }
                            else
                            {
                                float factor = 1.0F + (vDx + vDy) / ((compW + compH) * 0.25F);
                                sel.scaleX = signX * Math.max(0.05F, Math.abs(initialLayerScaleX * factor));
                                sel.scaleY = signY * Math.max(0.05F, Math.abs(initialLayerScaleY * factor));
                            }
                        }
                    }
                }
                CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                if (isExporting)
                {
                    stepExport(context);
                }
                else
                {
                    tickPlayback(comp);
                }
                renderPreviewCanvas(context, this.area, comp);
                super.render(context);
            }
        };
        this.renderMonitorHud.relative(this.previewArea).y(1F, -50).w(1F).h(50);
        this.renderMonitorHud.setVisible(false);
        this.previewArea.add(this.renderMonitorHud);
        this.previewArea.relative(this).y(24).w(1F).h(0.53F, -46);
    }

    private void setupCompTabStrip()
    {
        this.compTabStrip = new UIElement();
        this.compTabStrip.relative(this).y(0.53F, -22).w(1F).h(22);

        this.compTabStrip.add(new UIRenderable((context) ->
        {
            Area a = this.compTabStrip.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xFF14161C);
            context.batcher.box(a.x, a.y, a.ex(), a.y + 1, BBSSettings.dividerColor());
            context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), BBSSettings.dividerColor());
        }));

        this.compTabScroll = new UIScrollView(ScrollDirection.HORIZONTAL);
        this.compTabScroll.scroll.cancelScrolling().noScrollbar();
        this.compTabScroll.relative(this.compTabStrip).w(1F, -24).h(22).row(0).scroll();

        this.addCompButton = new UIIcon(Icons.ADD, (b) ->
        {
            if (this.activeProject != null)
            {
                int newIdx = this.activeProject.compositions.size() + 1;
                this.activeProject.addComposition("Comp " + newIdx);
                /* addComposition already sets activeCompositionIndex to the new comp */
                this.currentFrame = 0;
                CatalystProjectManager.saveProject(this.activeProject);
                this.rebuildCompTabs();
                this.updateTitleLabel();
                this.resize();
            }
        });
        this.addCompButton.tooltip(UIKeys.CATALYST_COMP_NEW);
        this.addCompButton.relative(this.compTabStrip).x(1F, -22).y(1).w(20).h(20);

        this.compTabStrip.add(this.compTabScroll, this.addCompButton);
    }

    public void rebuildCompTabs()
    {
        this.compTabScroll.removeAll();

        if (this.activeProject == null) return;

        this.activeProject.ensureCompositions();

        for (int i = 0; i < this.activeProject.compositions.size(); i++)
        {
            CatalystComposition comp = this.activeProject.compositions.get(i);
            final int index = i;

            int tabW = 100 + (comp.name.length() * 6);
            tabW = Math.max(90, Math.min(tabW, 180));

            UICatalystCompTab tab = new UICatalystCompTab(
                comp, index,
                () -> this.activeProject != null && this.activeProject.activeCompositionIndex == index,
                (idx) ->
                {
                    this.stopAllAudio();
                    this.activeProject.activeCompositionIndex = idx;
                    this.currentFrame = this.activeProject.getActiveComposition() != null
                        ? this.activeProject.getActiveComposition().playhead : 0;
                    this.updateTitleLabel();
                },
                this.activeProject.compositions.size() > 1 ? (idx) ->
                {
                    this.activeProject.removeComposition(idx);
                    CatalystProjectManager.saveProject(this.activeProject);
                    this.rebuildCompTabs();
                    this.updateTitleLabel();
                    this.resize();
                } : null
            );

            tab.w(tabW).h(20);
            this.compTabScroll.add(tab);
        }

        this.compTabScroll.resize();
    }

    /* ════════════════════════════════════════════════════════
     *  PREVIEW CANVAS
     * ════════════════════════════════════════════════════════ */

    public Texture getOrLoadImageTexture(CatalystLayer layer)
    {
        if (layer == null || layer.resourcePath == null || layer.resourcePath.trim().isEmpty())
        {
            return null;
        }

        String path = layer.resourcePath.trim();

        if (layer.cachedImageTexture instanceof Texture && ((Texture) layer.cachedImageTexture).isValid()
            && path.equals(layer.cachedImagePath))
        {
            return (Texture) layer.cachedImageTexture;
        }

        File imgFile = new File(path);
        if (!imgFile.exists() || !imgFile.isFile())
        {
            imgFile = AssetProvider.resolveDirectFile(Link.create(path));
        }

        if (imgFile != null && imgFile.exists() && imgFile.isFile())
        {
            try (InputStream stream = new FileInputStream(imgFile))
            {
                Pixels pixels = Pixels.fromPNGStream(stream);
                if (pixels != null)
                {
                    Texture tex = Texture.textureFromPixels(pixels, GL11.GL_LINEAR);
                    if (layer.cachedImageTexture instanceof Texture && ((Texture) layer.cachedImageTexture).isValid())
                    {
                        ((Texture) layer.cachedImageTexture).delete();
                    }
                    layer.cachedImageTexture = tex;
                    layer.cachedImagePath = path;
                    return tex;
                }
            }
            catch (Exception e)
            {
                BBSMod.LOGGER.error("Failed to load image texture from file: " + imgFile.getAbsolutePath(), e);
            }
        }

        try
        {
            Link link = Link.create(path);
            Texture tex = BBSModClient.getTextures().getTexture(link, GL11.GL_LINEAR);
            if (tex != null && tex != BBSModClient.getTextures().getError() && tex.isValid())
            {
                layer.cachedImageTexture = tex;
                layer.cachedImagePath = path;
                return tex;
            }
        }
        catch (Exception ignored)
        {
        }

        return null;
    }

    public float[] getLayerDimensions(CatalystLayer layer, int compW, int compH)
    {
        return this.getLayerDimensions(layer, compW, compH, layer != null ? layer.scaleX : 1F, layer != null ? layer.scaleY : 1F);
    }

    public float[] getLayerDimensions(CatalystLayer layer, int compW, int compH, float scaleX, float scaleY)
    {
        float baseW = compW;
        float baseH = compH;

        if (layer != null && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
        {
            if (layer.layerType == CatalystLayer.LayerType.VIDEO)
            {
                try
                {
                    Link link = Link.create(layer.resourcePath.trim());
                    VideoPlayer player = BBSModClient.getVideos().getPlayer(layer, link);
                    if (player != null)
                    {
                        player.setMaxSize(compW, compH);
                        player.ensureProbed();
                        int vw = player.getWidth();
                        int vh = player.getHeight();
                        if (vw > 0 && vh > 0)
                        {
                            float aspect = (float) vw / (float) vh;
                            float compAspect = (float) compW / (float) Math.max(1, compH);
                            if (aspect >= compAspect)
                            {
                                baseW = compW;
                                baseH = compW / aspect;
                            }
                            else
                            {
                                baseH = compH;
                                baseW = compH * aspect;
                            }
                        }
                    }
                }
                catch (Exception ignored) {}
            }
            else if (layer.layerType == CatalystLayer.LayerType.IMAGE)
            {
                try
                {
                    Texture texture = this.getOrLoadImageTexture(layer);
                    if (texture != null && texture.isValid() && texture.width > 0 && texture.height > 0)
                    {
                        float aspect = (float) texture.width / (float) texture.height;
                        float compAspect = (float) compW / (float) Math.max(1, compH);
                        if (aspect >= compAspect)
                        {
                            baseW = compW;
                            baseH = compW / aspect;
                        }
                        else
                        {
                            baseH = compH;
                            baseW = compH * aspect;
                        }
                    }
                }
                catch (Exception ignored) {}
            }
        }

        float sx = (layer != null) ? Math.copySign(Math.max(0.01F, Math.abs(scaleX)), scaleX == 0F ? 1F : scaleX) : 1F;
        float sy = (layer != null) ? Math.copySign(Math.max(0.01F, Math.abs(scaleY)), scaleY == 0F ? 1F : scaleY) : 1F;
        float w = baseW * sx;
        float h = baseH * sy;
        return new float[] {w, h};
    }

    private void renderPreviewCanvas(UIContext context, Area area, CatalystComposition comp)
    {
        int compW     = (comp != null && comp.width > 0) ? comp.width : 1920;
        int compH     = (comp != null && comp.height > 0) ? comp.height : 1080;
        int targetFps = (comp != null && comp.fps > 0) ? comp.fps : 60;
        int totalDur  = (comp != null) ? comp.duration : 300;
        int playhead  = this.currentFrame;

        if (this.isExporting)
        {
            /* Fullscreen dark monitor background */
            context.batcher.box(area.x, area.y, area.ex(), area.ey(), 0xFF000000);

            int viewH = Math.max(1, area.h - 50);
            float scale = Math.min((float) area.w / compW, (float) viewH / compH);
            if (scale <= 0.0001F) scale = 1.0F;

            float drawW = compW * scale;
            float drawH = compH * scale;
            float offsetX = area.x + (area.w - drawW) / 2.0F;
            float offsetY = area.y + (viewH - drawH) / 2.0F;

            /* Flush monitor background before applying GL hardware scissor */
            context.batcher.flush();

            /* Determine canvas area clamped strictly to preview bounds */
            int canvasX = (int) Math.floor(offsetX);
            int canvasY = (int) Math.floor(offsetY);
            int canvasW = (int) Math.ceil(drawW);
            int canvasH = (int) Math.ceil(drawH);

            int cX = Math.max(area.x, canvasX);
            int cY = Math.max(area.y, canvasY);
            int cEx = Math.min(area.ex(), canvasX + canvasW);
            int cEy = Math.min(area.y + viewH, canvasY + canvasH);
            int cW = Math.max(0, cEx - cX);
            int cH = Math.max(0, cEy - cY);

            MinecraftClient mc = MinecraftClient.getInstance();
            int guiFactor = (mc != null && mc.getWindow() != null) ? (int) mc.getWindow().getScaleFactor() : 1;
            int fbH = (mc != null && mc.getWindow() != null) ? mc.getWindow().getFramebufferHeight() : (area.y + viewH);
            int sx = cX * guiFactor;
            int sy = fbH - (cY + cH) * guiFactor;
            int sw = cW * guiFactor;
            int sh = cH * guiFactor;

            boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(sx, Math.max(0, sy), Math.max(0, sw), Math.max(0, sh));

            MatrixStack viewStack = context.batcher.getContext().getMatrices();
            viewStack.push();
            viewStack.translate(offsetX, offsetY, 0);
            viewStack.scale(scale, scale, 1.0F);

            /* Canvas surface */
            context.batcher.box(0, 0, compW, compH, 0xFF0D0D11);

            /* Render clean composition without bounding boxes or editing grid */
            this.renderCompositionLayers(context, comp, playhead, compW, compH, targetFps);
            context.batcher.outline(0, 0, compW, compH, 0xFF222228, 1);

            /* Flush layers before releasing scissor so no graphics bleed outside */
            context.batcher.flush();
            viewStack.pop();

            if (!prevScissor)
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            return;
        }


        context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.deepSurface());

        int margin = 16;
        int maxW   = area.w - margin * 2;
        int maxH   = area.h - margin * 2;

        if (maxW <= 0 || maxH <= 0) return;

        float scale = Math.min((float) maxW / compW, (float) maxH / compH);
        if (scale <= 0.0001F) scale = 1.0F;
        float offsetX = area.x + (area.w - compW * scale) / 2.0F;
        float offsetY = area.y + (area.h - compH * scale) / 2.0F;

        Area canvasArea = new Area((int) Math.floor(offsetX), (int) Math.floor(offsetY), (int) Math.ceil(compW * scale), (int) Math.ceil(compH * scale));
        context.batcher.clip(canvasArea, context);

        MatrixStack viewStack = context.batcher.getContext().getMatrices();
        viewStack.push();
        viewStack.translate(offsetX, offsetY, 0);
        viewStack.scale(scale, scale, 1.0F);

        /* Canvas surface in virtual composition space */
        context.batcher.box(0, 0, compW, compH, 0xFF0D0D11);

        CatalystLayer selectedLayer = this.getSelectedLayer();

        /* Render Active Layers (Back to Front: reverse order of comp.layers) */
        long compStartNano = System.nanoTime();
        this.renderCompositionLayers(context, comp, playhead, compW, compH, targetFps);
        this.lastCompRenderMs = (System.nanoTime() - compStartNano) / 1_000_000.0;

        context.batcher.outline(0, 0, compW, compH, 0xFF2A2A38, 1);

        /* Rule of Thirds grid */
        int thirdW = compW / 3;
        int thirdH = compH / 3;
        context.batcher.box(thirdW,     0, thirdW + 1,     compH, 0x1AFFFFFF);
        context.batcher.box(thirdW * 2, 0, thirdW * 2 + 1, compH, 0x1AFFFFFF);
        context.batcher.box(0, thirdH,     compW, thirdH + 1,     0x1AFFFFFF);
        context.batcher.box(0, thirdH * 2, compW, thirdH * 2 + 1, 0x1AFFFFFF);

        context.batcher.unclip(context);

        /* ── Bounding Box & Handles for Selected Layer ── */
        if (selectedLayer != null && selectedLayer.visible)
        {
            float[] seff = selectedLayer.computeEffective(playhead);
            float[] sdims = this.getLayerDimensions(selectedLayer, compW, compH, seff[2], seff[3]);
            float selW = sdims[0];
            float selH = sdims[1];
            float bx = (compW / 2.0F + seff[0]) - selW * seff[6];
            float by = (compH / 2.0F + seff[1]) - selH * seff[7];

            MatrixStack bstack = context.batcher.getContext().getMatrices();
            bstack.push();
            float bpx = bx + selW * seff[6];
            float bpy = by + selH * seff[7];
            bstack.translate(bpx, bpy, 0);
            if (seff[4] != 0)
            {
                bstack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(seff[4]));
            }
            bstack.translate(-bpx, -bpy, 0);

            float minBx = Math.min(bx, bx + selW);
            float maxBx = Math.max(bx, bx + selW);
            float minBy = Math.min(by, by + selH);
            float maxBy = Math.max(by, by + selH);

            /* White outline */
            context.batcher.outline((int) minBx, (int) minBy, (int) maxBx, (int) maxBy, Colors.WHITE, 1);

            /* 4 Corner handles */
            int handleS = Math.max(4, Math.round(8 / scale));
            int halfH = handleS / 2;
            int hCol = Colors.WHITE;
            int hBorder = 0xFF000000;

            int[][] corners = new int[][] {
                {(int) minBx, (int) minBy},
                {(int) maxBx, (int) minBy},
                {(int) maxBx, (int) maxBy},
                {(int) minBx, (int) maxBy}
            };

            for (int[] pt : corners)
            {
                context.batcher.box(pt[0] - halfH - 1, pt[1] - halfH - 1, pt[0] + halfH + 1, pt[1] + halfH + 1, hBorder);
                context.batcher.box(pt[0] - halfH, pt[1] - halfH, pt[0] + halfH, pt[1] + halfH, hCol);
            }

            /* Center / Anchor point crosshair */
            int ax = (int) (bx + selW * selectedLayer.anchorX);
            int ay = (int) (by + selH * selectedLayer.anchorY);
            int arm = Math.max(3, Math.round(6 / scale));
            context.batcher.box(ax - arm, ay, ax + arm + 1, ay + 1, Colors.WHITE);
            context.batcher.box(ax, ay - arm, ax + 1, ay + arm + 1, Colors.WHITE);

            bstack.pop();
        }

        viewStack.pop();

        /* OSD Info (Left Bottom, above timecode) */
        String compName = (comp != null ? comp.name.toUpperCase() : "COMP");
        String info     = String.format(java.util.Locale.ROOT, "%s • %d×%d • %d FPS • Rec.709 • Comp: %.1f ms",
            compName, compW, compH, targetFps, this.lastCompRenderMs);
        context.batcher.textCard(info, area.x + 8, area.ey() - 34, Colors.LIGHTEST_GRAY, 0x88000000, 2);

        /* Timecode */
        int secs   = playhead / Math.max(1, targetFps);
        int frames = playhead % Math.max(1, targetFps);
        String timecode = String.format("00:00:%02d:%02d  [Frame %d / %d]", secs, frames, playhead, totalDur);
        context.batcher.textCard(timecode, area.x + 8, area.ey() - 18, Colors.WHITE, 0x88000000, 2);

        context.batcher.box(area.x, area.ey() - 1, area.ex(), area.ey(), BBSSettings.dividerColor());
    }

    /**
     * Renders all active composition layers in virtual canvas space (0,0 to compW, compH).
     * Used by both real-time UI preview and offline export FBO renderer.
     */
    public void renderCompositionLayers(UIContext context, CatalystComposition comp, int playhead, int compW, int compH, int targetFps)
    {
        if (comp == null || comp.layers.isEmpty()) return;

        /* Unfreeze SCENE layers that are no longer in the playhead range */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.layerType == CatalystLayer.LayerType.SCENE
                && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty()
                && (playhead < layer.startFrame || playhead >= layer.startFrame + layer.duration))
            {
                String outFilmId = layer.resourcePath.trim();
                try { BBSModClient.getFilms().unfreeze(outFilmId); }
                catch (Exception ignored) {}

                /* Deactivate scene camera when the film we were tracking leaves range */
                if (outFilmId.equals(this.lastSceneFilmId))
                {
                    this.sceneCamera.setFilmCamera(null);
                    this.lastSceneFilmId = null;
                }
            }
        }

        /* Check if any layer is solo */
        boolean hasSolo = false;
        for (CatalystLayer l : comp.layers)
        {
            if (l.solo)
            {
                hasSolo = true;
                break;
            }
        }

        for (int i = comp.layers.size() - 1; i >= 0; i--)
        {
            CatalystLayer layer = comp.layers.get(i);
            if (hasSolo && !layer.solo) continue;
            if (!layer.visible) continue;
            if (playhead < layer.startFrame || playhead >= layer.startFrame + layer.duration) continue;

            /* Evaluate keyframe animations for current playhead frame without mutating base values */
            float[] eff = layer.computeEffective(playhead);
            float effPosX = eff[0];
            float effPosY = eff[1];
            float effScaleX = eff[2];
            float effScaleY = eff[3];
            float effRotation = eff[4];
            float effOpacity = eff[5];
            float effAnchorX = eff[6];
            float effAnchorY = eff[7];

            float alpha = Math.max(0F, Math.min(1F, effOpacity / 100.0F));
            if (alpha <= 0.001F) continue;

            long layerStartNano = System.nanoTime();

            int renderColor = Colors.setA(layer.color, alpha);

            /* Calculate Layer Transform Rect with dynamic aspect ratio in virtual space */
            float[] dims = this.getLayerDimensions(layer, compW, compH, effScaleX, effScaleY);
            float layerW = dims[0];
            float layerH = dims[1];
            float lx = (compW / 2.0F + effPosX) - layerW * effAnchorX;
            float ly = (compH / 2.0F + effPosY) - layerH * effAnchorY;

            /* Viewport Culling: Skip layers completely outside virtual canvas (with rotation margin) */
            float rotMargin = Math.max(Math.abs(layerW), Math.abs(layerH)) * 0.75F;
            float minLx = Math.min(lx, lx + layerW);
            float maxLx = Math.max(lx, lx + layerW);
            float minLy = Math.min(ly, ly + layerH);
            float maxLy = Math.max(ly, ly + layerH);
            if (layer.layerType != CatalystLayer.LayerType.AUDIO &&
                (maxLx + rotMargin < 0 || minLx - rotMargin > compW || maxLy + rotMargin < 0 || minLy - rotMargin > compH))
            {
                layer.lastRenderMs = 0;
                continue;
            }

            MatrixStack stack = context.batcher.getContext().getMatrices();
            stack.push();
            float px = lx + layerW * effAnchorX;
            float py = ly + layerH * effAnchorY;
            stack.translate(px, py, 0);
            if (effRotation != 0)
            {
                stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(effRotation));
            }
            stack.translate(-px, -py, 0);

            if (layer.layerType == CatalystLayer.LayerType.SOLID)
            {
                context.batcher.box((int) lx, (int) ly, (int) (lx + layerW), (int) (ly + layerH), renderColor);
            }
            else if (layer.layerType == CatalystLayer.LayerType.IMAGE)
            {
                boolean rendered = false;
                if (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                {
                    try
                    {
                        Texture texture = this.getOrLoadImageTexture(layer);
                        if (texture != null && texture.isValid())
                        {
                            int imgColor = Colors.setA(0xFFFFFFFF, alpha);
                            context.batcher.texturedBox(texture, imgColor, lx, ly, layerW, layerH, 0, 0, texture.width, texture.height);
                            rendered = true;
                        }
                    }
                    catch (Exception ignored) {}
                }
                if (!rendered)
                {
                    context.batcher.box((int) lx, (int) ly, (int) (lx + layerW), (int) (ly + layerH), renderColor);
                }
            }
            else if (layer.layerType == CatalystLayer.LayerType.VIDEO)
            {
                boolean rendered = false;
                if (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                {
                    try
                    {
                        Link link = Link.create(layer.resourcePath.trim());
                        VideoPlayer player = BBSModClient.getVideos().getPlayer(layer, link);
                        if (player != null)
                        {
                            player.setMaxSize(compW, compH);
                            int relativeFrame = (playhead - layer.startFrame) + layer.mediaOffset;
                            float relSec = (float) (relativeFrame + layer.audioOffset) / Math.max(1, targetFps);
                            float playerFps = player.getFps() > 0 ? player.getFps() : 30F;
                            int videoFrameIdx = (int) Math.round(relSec * playerFps);

                            Texture frameTex = null;
                            if (layer.cachedVideoTexture instanceof Texture && layer.lastVideoFrameIndex == videoFrameIdx && ((Texture) layer.cachedVideoTexture).isValid())
                            {
                                frameTex = (Texture) layer.cachedVideoTexture;
                            }
                            else
                            {
                                frameTex = player.getFrame(relSec);
                                if (frameTex != null && frameTex.isValid())
                                {
                                    layer.cachedVideoTexture = frameTex;
                                    layer.lastVideoFrameIndex = videoFrameIdx;
                                }
                                else if (layer.cachedVideoTexture instanceof Texture && ((Texture) layer.cachedVideoTexture).isValid())
                                {
                                    /* Frame skip / lag fallback: draw last loaded texture without stalling the UI thread */
                                    frameTex = (Texture) layer.cachedVideoTexture;
                                }
                            }

                            if (frameTex != null && frameTex.isValid())
                            {
                                int videoColor = Colors.setA(0xFFFFFFFF, alpha);
                                context.batcher.texturedBox(frameTex, videoColor, lx, ly, layerW, layerH, 0, 0, frameTex.width, frameTex.height);
                                rendered = true;
                            }
                        }
                    }
                    catch (Exception ignored) {}
                }
                if (!rendered)
                {
                    context.batcher.box((int) lx, (int) ly, (int) (lx + layerW), (int) (ly + layerH), renderColor);
                }
            }
            else if (layer.layerType == CatalystLayer.LayerType.SCENE)
            {
                boolean filmRendered = false;
                if (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                {
                    try
                    {
                        Film film = BBSMod.getFilms().load(layer.resourcePath.trim());
                        if (film != null)
                        {
                            String filmId = layer.resourcePath.trim();

                            /* ── 1. Custom FPS & sub-frame tick interpolation ── */
                            int filmFps = layer.filmFps > 0 ? layer.filmFps : 20;
                            int relFrame = (playhead - layer.startFrame) + layer.mediaOffset;
                            float targetTicks = (relFrame / (float) Math.max(1, targetFps)) * filmFps;
                            int baseTick = Math.max(0, (int) targetTicks);
                            float partialTick = Math.max(0.0F, targetTicks - (float) Math.floor(targetTicks));

                            int filmDuration = film.calculateDuration();
                            if (filmDuration > 0 && baseTick >= filmDuration)
                            {
                                baseTick = filmDuration - 1;
                                partialTick = 0.0F;
                            }

                            /* ── 2. Film switching: rebind camera when film ID changes ── */
                            if (!filmId.equals(this.lastSceneFilmId))
                            {
                                /* Unfreeze the old film so it doesn't ghost in the world */
                                if (this.lastSceneFilmId != null)
                                {
                                    BBSModClient.getFilms().unfreeze(this.lastSceneFilmId);
                                }
                                this.lastSceneFilmId = filmId;
                                /* Point sceneCamera at the new film's camera track */
                                this.sceneCamera.setFilmCamera(film.camera);
                            }

                            /* ── 3. Update camera tick and sub-frame partial tick for 60 FPS smooth interpolation ── */
                            this.sceneCamera.setTick(baseTick);
                            this.sceneCamera.setPartialTick(partialTick);

                            /* ── 4. Freeze replay entities with sub-frame interpolation ── */
                            BBSModClient.getFilms().freeze(film, baseTick, partialTick, false);

                            /* ── 5. Ensure the off-screen FBO is active ── */
                            if (this.isExporting && this.exportWidth > 0 && this.exportHeight > 0)
                            {
                                BBSRendering.setCustomSize(true, this.exportWidth, this.exportHeight);
                            }
                            else
                            {
                                BBSRendering.setCustomSize(true);
                            }

                            Texture texture = BBSRendering.getTexture();
                            if (texture != null && texture.isValid() && texture.width > 0 && texture.height > 0)
                            {
                                int filmColor = Colors.setA(0xFFFFFFFF, alpha);
                                context.batcher.texturedBox(texture.id, filmColor, lx, ly, layerW, layerH, 0, texture.height, texture.width, 0, texture.width, texture.height);
                                filmRendered = true;
                            }
                        }
                    }
                    catch (Exception ignored) {}
                }

                /* If no film is in range or couldn't render, deactivate scene camera */
                if (!filmRendered)
                {
                    this.sceneCamera.setFilmCamera(null);
                    context.batcher.box((int) lx, (int) ly, (int) (lx + layerW), (int) (ly + layerH), renderColor);
                    context.batcher.outline((int) lx, (int) ly, (int) (lx + layerW), (int) (ly + layerH), 0x55FFFFFF, 1);
                    String filmTitle = (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                        ? "Film: " + layer.resourcePath
                        : "Film: [No film selected]";
                    context.batcher.textCard(filmTitle, (int) lx + 8, (int) ly + 8, Colors.WHITE, 0x88000000, 2);
                }
            }
            else if (layer.layerType == CatalystLayer.LayerType.TEXT)
            {
                CatalystLayer selectedLayer = this.getSelectedLayer();
                String text = (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                    ? layer.resourcePath
                    : (selectedLayer == layer ? "[Double click or use Inspector to edit text]" : "");
                if (!text.isEmpty())
                {
                    int tCol = (layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                        ? Colors.setA(layer.textColor, alpha)
                        : Colors.setA(Colors.GRAY, alpha * 0.7F);
                    FontRenderer font = context.batcher.getFont();
                    float targetFontSize = layer.fontSize > 0 ? layer.fontSize : 16;
                    float fontScale = targetFontSize / 9.0F;

                    float sX = Math.abs(effScaleX) < 0.001F ? Math.copySign(0.001F, effScaleX == 0F ? 1F : effScaleX) : effScaleX;
                    float sY = Math.abs(effScaleY) < 0.001F ? Math.copySign(0.001F, effScaleY == 0F ? 1F : effScaleY) : effScaleY;

                    /* Unscaled base dimensions inside textStack to avoid double scale multiplication */
                    float baseLayerW = layerW / (sX * Math.max(0.01F, fontScale));
                    float baseLayerH = layerH / (sY * Math.max(0.01F, fontScale));
                    int maxBoxW = (int) Math.abs(baseLayerW);

                    List<String> renderedLines = new java.util.ArrayList<>();
                    String[] rawLines = text.split("\n");

                    for (String rawLine : rawLines)
                    {
                        if (layer.lineWrapping && maxBoxW > 10)
                        {
                            List<String> wrapped = font.wrap(rawLine, maxBoxW);
                            renderedLines.addAll(wrapped);
                        }
                        else
                        {
                            renderedLines.add(rawLine);
                        }
                    }

                    MatrixStack textStack = context.batcher.getContext().getMatrices();
                    textStack.push();
                    textStack.translate(lx, ly, 0);
                    textStack.scale(fontScale * sX, fontScale * sY, 1.0F);

                    int unscaledLineH = font.getHeight() + 4;
                    float curY = (baseLayerH - renderedLines.size() * unscaledLineH) / 2.0F;

                    for (String line : renderedLines)
                    {
                        int lw = font.getWidth(line);
                        float lineX = (baseLayerW - lw) / 2.0F;
                        context.batcher.text(line, lineX, curY, tCol, layer.shadow);
                        curY += unscaledLineH;
                    }

                    textStack.pop();
                }
            }
            else if (layer.layerType == CatalystLayer.LayerType.AUDIO)
            {
                /* Audio layers don't draw canvas geometry */
            }
            else
            {
                context.batcher.box((int) lx, (int) ly, (int) (lx + layerW), (int) (ly + layerH), renderColor);
            }

            stack.pop();

            layer.lastRenderMs = (System.nanoTime() - layerStartNano) / 1_000_000.0;
        }
    }

    /* ════════════════════════════════════════════════════════
     *  BOTTOM AREA (Layers + Timeline)
     * ════════════════════════════════════════════════════════ */

    private void setupBottomArea()
    {
        this.bottomArea = new UIElement();
        this.bottomArea.relative(this).y(0.53F).w(1F).h(0.47F);

        /* ── Left: Layers list ── */
        this.layersContainer = new UIElement();
        this.layersContainer.relative(this.bottomArea).w(200).h(1F);

        this.layersHeader = new UIElement();
        this.layersHeader.relative(this.layersContainer).w(1F).h(20);
        this.layersHeader.add(new UIRenderable((context) ->
        {
            Area a = this.layersHeader.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.chromeSurface());
            context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), BBSSettings.dividerColor());

            CatalystComposition comp = (this.activeProject != null) ? this.activeProject.getActiveComposition() : null;
            int count = (comp != null) ? comp.layers.size() : 0;
            String headerTitle = UIKeys.CATALYST_LAYERS.get() + " (" + count + ")";
            context.batcher.textCard(headerTitle, a.x + 6, a.y + 4, Colors.WHITE, 0, 0, false);
        }));

        this.layersList = new UIScrollView(ScrollDirection.VERTICAL)
        {
            private CatalystLayer draggingPropLayer = null;
            private int draggingPropBit = 0;
            private int draggingPropComponent = 0;
            private int dragStartX = 0;
            private float dragInitialVal1 = 0F;
            private float dragInitialVal2 = 0F;

            /** Returns the total height of the layer panel content (sum of all expanded row heights). */
            private int getTotalContentHeight(CatalystComposition comp)
            {
                if (comp == null) return 0;
                int total = 0;
                for (CatalystLayer l : comp.layers)
                {
                    total += getExpandedLayerPanelHeight(l);
                }
                return total;
            }

            private int getExpandedLayerPanelHeight(CatalystLayer l)
            {
                if (!l.expanded) return 24;
                int count = Integer.bitCount(l.expandedProps);
                return 24 + count * 18;
            }

            /** Find the layer under the given scroll-adjusted Y. Returns the layer and sets layerTopY_out[0]. */
            private CatalystLayer getLayerAtY(CatalystComposition comp, int relY, int[] layerTopY_out)
            {
                if (comp == null) return null;
                int cy = 0;
                for (CatalystLayer l : comp.layers)
                {
                    int h = getExpandedLayerPanelHeight(l);
                    if (relY >= cy && relY < cy + h)
                    {
                        if (layerTopY_out != null) layerTopY_out[0] = cy;
                        return l;
                    }
                    cy += h;
                }
                return null;
            }

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 0 && this.area.isInside(context))
                {
                    CatalystComposition comp = (activeProject != null) ? activeProject.getActiveComposition() : null;
                    if (comp != null && !comp.layers.isEmpty())
                    {
                        int scroll = (int) this.scroll.getScroll();
                        int relY   = context.mouseY - this.area.y + scroll;
                        int mx     = context.mouseX;
                        int ax     = this.area.x;

                        int[] topY = {0};
                        CatalystLayer layer = getLayerAtY(comp, relY, topY);
                        if (layer == null) return super.subMouseClicked(context);

                        int ry = this.area.y + topY[0] - scroll; // screen Y of this layer's row top

                        /* Only handle clicks in the base row (first 24px) for switches */
                        if (context.mouseY < ry + 24)
                        {
                            /* EXPAND ARROW (x: 2..14) - Check first and consume cleanly */
                            if (mx >= ax + 2 && mx < ax + 14)
                            {
                                layer.expanded = !layer.expanded;
                                if (layer.expanded && layer.expandedProps == 0)
                                {
                                    layer.expandedProps =
                                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION |
                                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE    |
                                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION |
                                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY  |
                                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR;
                                }
                                else if (!layer.expanded)
                                {
                                    layer.expandedProps = 0;
                                }
                                return true;
                            }
                            /* EYE / VISIBLE (x: 14..26) */
                            if (mx >= ax + 14 && mx < ax + 26)
                            {
                                layer.visible = !layer.visible;
                                saveAndRefresh();
                                updateInspectorForm();
                                return true;
                            }
                            /* MUTE (x: 27..39) - Only active for audio/video layers */
                            if (mx >= ax + 27 && mx < ax + 39)
                            {
                                if (layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.VIDEO)
                                {
                                    layer.muted = !layer.muted;
                                    saveAndRefresh();
                                    return true;
                                }
                            }
                            /* SOLO (x: 40..52) */
                            if (mx >= ax + 40 && mx < ax + 52)
                            {
                                layer.solo = !layer.solo;
                                saveAndRefresh();
                                return true;
                            }
                            /* LOCK (x: 53..65) */
                            if (mx >= ax + 53 && mx < ax + 65)
                            {
                                layer.locked = !layer.locked;
                                saveAndRefresh();
                                updateInspectorForm();
                                return true;
                            }

                            /* Click on layer name area = select */
                            if (catalystTimeline != null)
                            {
                                catalystTimeline.setSelected(layer);
                                updateInspectorForm();
                            }
                            return true;
                        }
                        else
                        {
                            /* Click in property sub-row area */
                            if (layer.expanded && layer.expandedProps != 0)
                            {
                                int py = ry + 24;
                                int[] propBits = {
                                    mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION,
                                    mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE,
                                    mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION,
                                    mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY,
                                    mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR
                                };
                                for (int pBit : propBits)
                                {
                                    if ((layer.expandedProps & pBit) == 0) continue;
                                    if (context.mouseY >= py && context.mouseY < py + 18)
                                    {
                                        /* Check stopwatch click (x: ax + 4 .. ax + 20) */
                                        if (mx >= ax + 4 && mx < ax + 20)
                                        {
                                            pushUndo();
                                            layer.addKeyframe(pBit, currentFrame);
                                            saveAndRefresh();
                                            return true;
                                        }

                                        /* Check value area click/drag on the right side */
                                        if (mx >= ax + 105)
                                        {
                                            int valLeft = ax + 105;
                                            int valRight = this.area.ex() - 4;
                                            int valMid = valLeft + (valRight - valLeft) / 2;

                                            if (Window.isAltPressed())
                                            {
                                                /* Alt+Click on left or right half toggles single-axis keyframe */
                                                pushUndo();
                                                int componentIndex = (mx >= valMid) ? 1 : 0;
                                                int singleBit = CatalystLayer.getSinglePropBit(pBit, componentIndex);
                                                if (layer.hasKeyframeAtSingle(singleBit, currentFrame))
                                                {
                                                    layer.removeKeyframeSingle(singleBit, currentFrame);
                                                }
                                                else
                                                {
                                                    layer.addKeyframeSingle(singleBit, currentFrame);
                                                }
                                                saveAndRefresh();
                                                return true;
                                            }

                                            pushUndo();
                                            draggingPropLayer = layer;
                                            draggingPropBit = pBit;
                                            draggingPropComponent = (mx >= valMid) ? 1 : 0;
                                            dragStartX = mx;
                                            if (pBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION)
                                            {
                                                dragInitialVal1 = layer.posX;
                                                dragInitialVal2 = layer.posY;
                                            }
                                            else if (pBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE)
                                            {
                                                dragInitialVal1 = layer.scaleX;
                                                dragInitialVal2 = layer.scaleY;
                                            }
                                            else if (pBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION)
                                            {
                                                dragInitialVal1 = layer.rotation;
                                            }
                                            else if (pBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY)
                                            {
                                                dragInitialVal1 = (float) layer.opacity;
                                            }
                                            else if (pBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR)
                                            {
                                                dragInitialVal1 = layer.anchorX;
                                                dragInitialVal2 = layer.anchorY;
                                            }
                                            return true;
                                        }

                                        /* Click in prop row – select the layer */
                                        if (catalystTimeline != null)
                                        {
                                            catalystTimeline.setSelected(layer);
                                            updateInspectorForm();
                                        }
                                        return true;
                                    }
                                    py += 18;
                                }
                            }
                        }
                    }
                }
                return super.subMouseClicked(context);
            }

            @Override
            protected boolean subMouseReleased(UIContext context)
            {
                if (draggingPropLayer != null)
                {
                    draggingPropLayer = null;
                    draggingPropBit = 0;
                    draggingPropComponent = 0;
                    saveAndRefresh();
                    updateInspectorForm();
                    return true;
                }
                return super.subMouseReleased(context);
            }

            @Override
            public void render(UIContext context)
            {
                if (draggingPropLayer != null && draggingPropBit != 0)
                {
                    float dx = (context.mouseX - dragStartX);
                    float factor = Window.isShiftPressed() ? 0.1F : (Window.isCtrlPressed() ? 5.0F : 1.0F);

                    if (draggingPropBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION)
                    {
                        if (draggingPropComponent == 0)
                        {
                            float nv1 = dragInitialVal1 + dx * factor;
                            draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, nv1, dragInitialVal2);
                        }
                        else
                        {
                            float nv2 = dragInitialVal2 + dx * factor;
                            draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, dragInitialVal1, nv2);
                        }
                    }
                    else if (draggingPropBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE)
                    {
                        float sclDelta = dx * 0.01F * factor;
                        if (draggingPropComponent == 0)
                        {
                            float ns1 = Math.max(0.01F, dragInitialVal1 + sclDelta);
                            draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, ns1, dragInitialVal2);
                        }
                        else
                        {
                            float ns2 = Math.max(0.01F, dragInitialVal2 + sclDelta);
                            draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, dragInitialVal1, ns2);
                        }
                    }
                    else if (draggingPropBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION)
                    {
                        float nRot = (dragInitialVal1 + dx * factor) % 360F;
                        draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, nRot, 0F);
                    }
                    else if (draggingPropBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY)
                    {
                        float nOp = Math.max(0F, Math.min(100F, dragInitialVal1 + dx * factor));
                        draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, nOp, 0F);
                    }
                    else if (draggingPropBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR)
                    {
                        if (draggingPropComponent == 0)
                        {
                            float nAx = Math.max(0F, Math.min(1F, dragInitialVal1 + dx * 0.005F * factor));
                            draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, nAx, dragInitialVal2);
                        }
                        else
                        {
                            float nAy = Math.max(0F, Math.min(1F, dragInitialVal2 + dx * 0.005F * factor));
                            draggingPropLayer.updatePropertyValue(draggingPropBit, currentFrame, dragInitialVal1, nAy);
                        }
                    }
                }
                super.render(context);
            }
        };
        this.layersList.relative(this.layersContainer).y(20).w(1F).h(1F, -20);
        this.layersList.scroll.scrollSpeed = 16;

        this.layersList.add(new UIRenderable((context) ->
        {
            Area a = this.layersList.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.sunkenSurface());

            CatalystComposition comp = (this.activeProject != null) ? this.activeProject.getActiveComposition() : null;
            if (comp == null || comp.layers.isEmpty()) return;

            int scroll = this.layersList.scroll != null ? (int) this.layersList.scroll.getScroll() : 0;
            FontRenderer font = context.batcher.getFont();

            int cy = 0; // cumulative y within content (pre-scroll)
            for (int i = 0; i < comp.layers.size(); i++)
            {
                CatalystLayer layer = comp.layers.get(i);
                int rowH  = 24;
                int propH = layer.expanded ? Integer.bitCount(layer.expandedProps) * 18 : 0;
                int totalH = rowH + propH;
                int ry = a.y + cy - scroll;

                if (ry + totalH < a.y || ry > a.ey())
                {
                    cy += totalH;
                    continue;
                }

                boolean isSelected = this.catalystTimeline != null && this.catalystTimeline.isSelected(layer);
                int bg = isSelected ? 0xFF2A2E3B : 0xFF1A1A20;

                /* Base row background */
                context.batcher.box(a.x + 2, ry + 1, a.ex() - 2, ry + rowH - 1, bg);
                /* Color stripe */
                context.batcher.box(a.x + 2, ry + 1, a.x + 4, ry + rowH - 1, layer.color);

                if (isSelected)
                {
                    context.batcher.outline(a.x + 2, ry + 1, a.ex() - 2, ry + rowH - 1, Colors.WHITE, 1);
                }

                /* ── EXPAND ARROW (x=4..13) ── */
                boolean hasTransformProps = layer.layerType != CatalystLayer.LayerType.AUDIO;
                if (hasTransformProps)
                {
                    String arrow = layer.expanded ? "\u25BC" : "\u25BA"; // down vs right
                    context.batcher.text(arrow, a.x + 5, ry + 8, 0xFF8899AA, false);
                }

                /* ── LAYER SWITCHES (icons in 14-64 range) ── */
                int iconY = ry + 4;

                /* EYE icon (14..25) */
                context.batcher.icon(layer.visible ? Icons.VISIBLE : Icons.INVISIBLE,
                    layer.visible ? 0xFFFFFFFF : 0xFF555566, a.x + 14, iconY);

                /* MUTE icon (27..38) - SOUND icon, dimmed if muted */
                context.batcher.icon(Icons.SOUND,
                    layer.muted ? 0xFF444455 : (layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.VIDEO ? 0xFFFFFFFF : 0xFF888899),
                    a.x + 27, iconY);

                /* SOLO icon (40..51) - PLAYER icon, gold when active */
                context.batcher.icon(Icons.PLAYER,
                    layer.solo ? 0xFFE8C43A : 0xFF555566, a.x + 40, iconY);

                /* LOCK icon (53..64) */
                context.batcher.icon(layer.locked ? Icons.LOCKED : Icons.UNLOCKED,
                    layer.locked ? 0xFFE85F50 : 0xFF555566, a.x + 53, iconY);

                /* ── LAYER NAME (x=68) ── */
                int nameX = a.x + 68;
                int nameMaxW = a.ex() - nameX - 30; // leave space for badge
                String displayName = font.limitToWidth(layer.name, nameMaxW);
                int nameColor = isSelected ? Colors.WHITE : (layer.visible ? Colors.LIGHTEST_GRAY : 0xFF555566);
                context.batcher.text(displayName, nameX, ry + 8, nameColor, false);

                /* ── TYPE BADGE (right side) ── */
                String badge = layer.layerType != null ? layer.layerType.getBadge() : "";
                int bw = font.getWidth(badge);
                context.batcher.text(badge, a.ex() - bw - 4, ry + 8, 0xFF556677, false);

                /* ── PROPERTY SUB-ROWS (twirl-down) ── */
                if (layer.expanded && layer.expandedProps != 0)
                {
                    int py = ry + rowH;
                    String[] propNames   = {"Position (X, Y)", "Scale (X, Y)", "Rotation", "Opacity", "Anchor (X, Y)"};
                    float[][] propValues = {
                        {layer.posX,    layer.posY},
                        {layer.scaleX,  layer.scaleY},
                        {layer.rotation, Float.NaN},
                        {layer.opacity,  Float.NaN},
                        {layer.anchorX, layer.anchorY}
                    };
                    int[] propBits = {
                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION,
                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE,
                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION,
                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY,
                        mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR
                    };

                    for (int p = 0; p < propBits.length; p++)
                    {
                        if ((layer.expandedProps & propBits[p]) == 0) continue;

                        /* Sub-row background */
                        context.batcher.box(a.x + 4, py, a.ex() - 2, py + 17, 0xFF141418);
                        context.batcher.box(a.x + 4, py + 16, a.ex() - 2, py + 17, 0xFF222228);

                        /* STOPWATCH icon on left */
                        context.batcher.icon(Icons.STOPWATCH, 0xFF4477AA, a.x + 5, py + 1);

                        /* Property name */
                        context.batcher.text(propNames[p], a.x + 22, py + 4, 0xFF8899AA, false);

                        /* Value on right (Split into Sol yarı = X, Sağ yarı = Y) */
                        float[] vals = propValues[p];
                        int valLeft = a.x + 105;
                        int valRight = a.ex() - 4;
                        int valMid = valLeft + (valRight - valLeft) / 2;

                        if (!Float.isNaN(vals[1]))
                        {
                            String strX = String.format(java.util.Locale.ROOT, "%.1f", vals[0]);
                            String strY = String.format(java.util.Locale.ROOT, "%.1f", vals[1]);

                            int wX = font.getWidth(strX);
                            int wY = font.getWidth(strY);

                            /* Left half for X */
                            context.batcher.text(strX, valMid - wX - 4, py + 4, 0xFFCCDDEE, false);
                            /* Divider */
                            context.batcher.box(valMid - 1, py + 3, valMid, py + 14, 0x44FFFFFF);
                            /* Right half for Y */
                            context.batcher.text(strY, valRight - wY - 2, py + 4, 0xFFCCDDEE, false);
                        }
                        else if (propBits[p] == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY)
                        {
                            String valStr = (int) vals[0] + "%";
                            int vw = font.getWidth(valStr);
                            context.batcher.text(valStr, valRight - vw - 2, py + 4, 0xFFCCDDEE, false);
                        }
                        else
                        {
                            String valStr = String.format(java.util.Locale.ROOT, "%.1f°", vals[0]);
                            int vw = font.getWidth(valStr);
                            context.batcher.text(valStr, valRight - vw - 2, py + 4, 0xFFCCDDEE, false);
                        }

                        py += 18;
                    }
                }

                /* Row separator */
                context.batcher.box(a.x + 2, ry + rowH - 1, a.ex() - 2, ry + rowH, 0xFF2A2A30);

                cy += totalH;
            }

            context.batcher.box(a.ex() - 1, a.y, a.ex(), a.ey(), BBSSettings.dividerColor());
        }));

        this.layersContainer.add(this.layersHeader, this.layersList);

        /* ── Center: Catalyst Timeline (extends UITimelineCanvas) ── */
        this.catalystTimeline = new UICatalystTimeline(
            () -> this.activeProject != null ? this.activeProject.getActiveComposition() : null,
            () -> this.currentFrame,
            (frame) ->
            {
                this.seekToFrame(frame);
            },
            () ->
            {
                /* Pause on scrub */
                this.isPlaying  = false;
                this.lastTickMs = 0;
                this.stopAllAudio();
            },
            () ->
            {
                /* Auto-save on timeline modification */
                this.saveAndRefresh();
                this.updateInspectorForm();
            }
        );
        this.catalystTimeline.onPreModify = this::pushUndo;
        this.catalystTimeline.relative(this.bottomArea).x(200).w(1F, -420).h(1F);

        /* ── Right: Inspector Panel ── */
        this.setupInspectorPanel();

        this.bottomArea.add(this.layersContainer, this.catalystTimeline, this.inspectorContainer);
    }

    private void setupInspectorPanel()
    {
        this.inspectorContainer = new UIElement();
        this.inspectorContainer.relative(this.bottomArea).x(1F, -220).w(220).h(1F);

        this.inspectorHeader = new UIElement();
        this.inspectorHeader.relative(this.inspectorContainer).w(1F).h(20);
        this.inspectorHeader.add(new UIRenderable((context) ->
        {
            Area a = this.inspectorHeader.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.chromeSurface());
            context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), BBSSettings.dividerColor());
            context.batcher.box(a.x, a.y, a.x + 1, a.ey(), BBSSettings.dividerColor());

            context.batcher.icon(Icons.GEAR, Colors.WHITE, a.x + 4, a.my() - 8);
            context.batcher.text("INSPECTOR", a.x + 22, a.y + 5, Colors.WHITE, false);
        }));

        this.inspectorEmptyLabel = new UILabel(IKey.raw("No layer selected"), Colors.GRAY);

        this.layerTypeLabel = new UILabel(IKey.raw("Type: SOLID"), Colors.LIGHTEST_GRAY);
        this.layerTypeLabel.h(16);

        this.layerNameInput = new UITextbox(120, (text) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null && !text.trim().isEmpty())
            {
                sel.name = text.trim();
                this.saveAndRefresh();
            }
        });
        this.layerNameInput.h(20);

        /* Read-only source asset label */
        this.layerResourceLabel = new UILabel(IKey.raw("Source: (None)"), Colors.LIGHTEST_GRAY);
        this.layerResourceLabel.h(14);

        /* Text Layer Controls */
        this.layerTextarea = new UITextarea((java.util.function.Consumer<String>) (text) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.resourcePath = text;
                this.saveAndRefresh();
            }
        });
        this.layerTextarea.background().wrap(true).h(64);

        this.layerFontSizeInput = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.fontSize = (int) Math.round(v);
                this.saveAndRefresh();
            }
        });
        this.layerFontSizeInput.limit(6, 120, true).h(20);
        this.layerFontSizeInput.setValue(16);

        this.layerTextColorPicker = new UIColor((col) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.textColor = col;
                this.saveAndRefresh();
            }
        });
        this.layerTextColorPicker.withAlpha().h(20);

        this.layerLineWrapButton = new UIButton(IKey.raw("Wrap: ON"), (b) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.lineWrapping = !sel.lineWrapping;
                this.updateInspectorForm();
                this.saveAndRefresh();
            }
        });
        this.layerLineWrapButton.h(20);

        this.layerShadowButton = new UIButton(IKey.raw("Shadow: ON"), (b) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.shadow = !sel.shadow;
                this.updateInspectorForm();
                this.saveAndRefresh();
            }
        });
        this.layerShadowButton.h(20);

        this.textControlsGroup = UI.column(
            4,
            UI.label(IKey.raw("Text Content:")).h(14),
            this.layerTextarea,
            UI.label(IKey.raw("Font Size & Color:")).h(14),
            UI.row(this.layerFontSizeInput, this.layerTextColorPicker),
            UI.row(this.layerLineWrapButton, this.layerShadowButton)
        );

        /* Opacity & Color controls */
        this.layerOpacityInput = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.opacity = (int) Math.round(v);
                this.saveAndRefresh();
            }
        });
        this.layerOpacityInput.limit(0, 100, true).h(20);

        this.layerBlendButton = new UIButton(IKey.raw("Blend: NORMAL"), (b) -> this.cycleBlendMode());
        this.layerBlendButton.h(20);

        this.layerVisibleButton = new UIButton(IKey.raw("Visible: ON"), (b) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.visible = !sel.visible;
                this.updateInspectorForm();
                this.saveAndRefresh();
            }
        });
        this.layerVisibleButton.h(20);

        this.layerLockedButton = new UIButton(IKey.raw("Lock: OFF"), (b) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.locked = !sel.locked;
                this.updateInspectorForm();
                this.saveAndRefresh();
            }
        });
        this.layerLockedButton.h(20);

        /* Preset color swatches */
        int[] palette = new int[] {
            0xFF6B4A82, 0xFF2B5B84, 0xFF4A7C38, 0xFF88602A,
            0xFFD63031, 0xFF0984E3, 0xFF00B894, 0xFFE17055
        };

        this.layerColorRow = new UIElement();
        this.layerColorRow.h(18);

        for (int i = 0; i < palette.length; i++)
        {
            final int col = palette[i];
            UIButton swatch = new UIButton(IKey.raw(""), (btn) ->
            {
                CatalystLayer sel = this.getSelectedLayer();
                if (sel != null)
                {
                    sel.color = col;
                    this.layerColorPicker.setColor(col);
                    this.saveAndRefresh();
                }
            });
            swatch.color(col, col | 0x33FFFFFF);
            swatch.relative(this.layerColorRow).x(i * 24).w(20).h(18);
            this.layerColorRow.add(swatch);
        }

        this.layerColorPicker = new UIColor((col) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.color = col;
                this.saveAndRefresh();
            }
        });
        this.layerColorPicker.h(20);

        /* Transform / Pivot Controls */
        this.layerPosX = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.posX = (float) (double) v;
                if (sel.animPosX || !sel.channelPosX.isEmpty())
                {
                    sel.channelPosX.insert(this.currentFrame, sel.posX);
                }
                this.saveAndRefresh();
            }
        });
        this.layerPosX.h(20);

        this.layerPosY = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.posY = (float) (double) v;
                if (sel.animPosY || !sel.channelPosY.isEmpty())
                {
                    sel.channelPosY.insert(this.currentFrame, sel.posY);
                }
                this.saveAndRefresh();
            }
        });
        this.layerPosY.h(20);

        this.layerScaleX = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.scaleX = (float) (double) v;
                if (sel.animScaleX || !sel.channelScaleX.isEmpty())
                {
                    sel.channelScaleX.insert(this.currentFrame, sel.scaleX);
                }
                this.saveAndRefresh();
            }
        });
        this.layerScaleX.limit(-20.0, 20.0).h(20);
        this.layerScaleX.setValue(1.0);

        this.layerScaleY = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.scaleY = (float) (double) v;
                if (sel.animScaleY || !sel.channelScaleY.isEmpty())
                {
                    sel.channelScaleY.insert(this.currentFrame, sel.scaleY);
                }
                this.saveAndRefresh();
            }
        });
        this.layerScaleY.limit(-20.0, 20.0).h(20);
        this.layerScaleY.setValue(1.0);

        this.layerRotation = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.rotation = (float) (double) v;
                if (sel.animRotation || !sel.channelRotation.isEmpty())
                {
                    sel.channelRotation.insert(this.currentFrame, sel.rotation);
                }
                this.saveAndRefresh();
            }
        });
        this.layerRotation.limit(-360, 360).h(20);
        this.layerRotation.setValue(0);

        this.layerAnchorX = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.anchorX = (float) (double) v;
                if (sel.animAnchorX || !sel.channelAnchorX.isEmpty())
                {
                    sel.channelAnchorX.insert(this.currentFrame, sel.anchorX);
                }
                this.saveAndRefresh();
            }
        });
        this.layerAnchorX.limit(0.0, 1.0).h(20);
        this.layerAnchorX.setValue(0.5);

        this.layerAnchorY = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.anchorY = (float) (double) v;
                if (sel.animAnchorY || !sel.channelAnchorY.isEmpty())
                {
                    sel.channelAnchorY.insert(this.currentFrame, sel.anchorY);
                }
                this.saveAndRefresh();
            }
        });
        this.layerAnchorY.limit(0.0, 1.0).h(20);
        this.layerAnchorY.setValue(0.5);

        this.layerResetTransformBtn = new UIButton(IKey.raw("Reset"), (b) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                this.pushUndo();
                sel.posX = 0;
                sel.posY = 0;
                sel.scaleX = 1.0F;
                sel.scaleY = 1.0F;
                sel.rotation = 0;
                sel.anchorX = 0.5F;
                sel.anchorY = 0.5F;
                this.updateInspectorForm();
                this.saveAndRefresh();
            }
        });
        this.layerResetTransformBtn.h(16);

        this.transformGroup = UI.column(
            4,
            UI.row(UI.label(IKey.raw("Transform:")).h(16), this.layerResetTransformBtn).h(16),
            UI.label(IKey.raw("Position (X, Y):")).h(14),
            UI.row(this.layerPosX, this.layerPosY),
            UI.label(IKey.raw("Scale (X, Y):")).h(14),
            UI.row(this.layerScaleX, this.layerScaleY),
            UI.label(IKey.raw("Rotation (Deg):")).h(14),
            this.layerRotation,
            UI.label(IKey.raw("Anchor Point (X, Y):")).h(14),
            UI.row(this.layerAnchorX, this.layerAnchorY)
        );

        /* Media Volume & Offset controls */
        this.layerVolumeInput = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.volume = (float) (double) v;
                this.saveAndRefresh();
            }
        });
        this.layerVolumeInput.limit(0.0, 1.0).h(20);
        this.layerVolumeInput.setValue(1.0);

        this.layerPanInput = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.pan = (float) (double) v;
                this.saveAndRefresh();
            }
        });
        this.layerPanInput.limit(-1.0, 1.0).values(0.05, 0.1, 0.2).h(20);
        this.layerPanInput.setValue(0.0);

        this.layerAudioOffsetInput = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.audioOffset = (int) Math.round(v);
                this.saveAndRefresh();
            }
        });
        this.layerAudioOffsetInput.limit(-3600, 3600, true).h(20);
        this.layerAudioOffsetInput.setValue(0);

        this.layerExtendDurationBtn = new UIButton(IKey.raw("Extend to Media Length"), (b) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null && sel.mediaDuration > 0)
            {
                this.pushUndo();
                sel.duration = sel.mediaDuration;
                this.saveAndRefresh();
                this.updateInspectorForm();
            }
        });
        this.layerExtendDurationBtn.h(20);

        this.layerFilmFpsLabel = new UILabel(IKey.raw("Film FPS / Tick-Rate:"), Colors.LIGHTEST_GRAY);
        this.layerFilmFpsLabel.h(14);
        this.layerFilmFpsInput = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null && sel.layerType == CatalystLayer.LayerType.SCENE)
            {
                sel.filmFps = Math.max(1, (int) Math.round(v));
                this.saveAndRefresh();
            }
        });
        this.layerFilmFpsInput.limit(1, 240, true).values(1, 5, 10).h(20);
        this.layerFilmFpsInput.setValue(20);

        this.audioControlsGroup = UI.column(
            4,
            UI.label(IKey.raw("Volume (0.0 - 1.0):")).h(14),
            this.layerVolumeInput,
            UI.label(IKey.raw("Audio Pan (-1.0 Left ... 1.0 Right):")).h(14),
            this.layerPanInput,
            UI.label(IKey.raw("Audio Offset (Frames):")).h(14),
            this.layerAudioOffsetInput
        );

        this.mediaGroup = UI.column(
            4,
            this.audioControlsGroup,
            this.layerExtendDurationBtn,
            this.layerFilmFpsLabel,
            this.layerFilmFpsInput
        );

        this.inspectorForm = UI.column(
            4,
            this.layerTypeLabel,
            UI.label(IKey.raw("Layer Name:")).h(14),
            this.layerNameInput,
            this.textControlsGroup,
            this.layerResourceLabel,
            this.mediaGroup,
            UI.label(IKey.raw("Opacity (%):")).h(14),
            this.layerOpacityInput,
            UI.label(IKey.raw("Layer Color:")).h(14),
            this.layerColorRow,
            this.layerColorPicker,
            this.layerBlendButton,
            UI.row(this.layerVisibleButton, this.layerLockedButton),
            this.transformGroup
        );

        this.inspectorScroll = UI.scrollView(4, 6, this.inspectorEmptyLabel, this.inspectorForm);
        this.inspectorScroll.relative(this.inspectorContainer).y(20).w(1F).h(1F, -20);
        this.inspectorScroll.scroll.scrollSpeed = 16;
        this.inspectorScroll.preRender((context) ->
        {
            Area a = this.inspectorScroll.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), BBSSettings.deepSurface());
            context.batcher.box(a.x, a.y, a.x + 1, a.ey(), BBSSettings.dividerColor());

            CatalystLayer sel = this.getSelectedLayer();
            if (sel != this.lastInspectedLayer)
            {
                this.lastInspectedLayer = sel;
                this.updateInspectorForm();
            }
        });

        this.inspectorContainer.add(this.inspectorHeader, this.inspectorScroll);
    }

    public static void cleanupLayerResources(CatalystLayer layer)
    {
        if (layer == null)
        {
            return;
        }

        if (layer.layerType == CatalystLayer.LayerType.VIDEO)
        {
            try
            {
                BBSModClient.getVideos().release(layer);
            }
            catch (Exception ignored) {}
            layer.cachedVideoTexture = null;
            layer.lastVideoFrameIndex = -1;
        }
        else if (layer.layerType == CatalystLayer.LayerType.IMAGE)
        {
            if (layer.cachedImageTexture instanceof Texture)
            {
                Texture tex = (Texture) layer.cachedImageTexture;
                if (tex.isValid())
                {
                    try
                    {
                        tex.delete();
                    }
                    catch (Exception ignored) {}
                }
            }
            layer.cachedImageTexture = null;
            layer.cachedImagePath = null;
        }
        else if (layer.layerType == CatalystLayer.LayerType.SCENE
            && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
        {
            try
            {
                BBSModClient.getFilms().unfreeze(layer.resourcePath.trim());
            }
            catch (Exception ignored) {}
        }
    }

    public static void cleanupProjectResources(CatalystProject project)
    {
        if (project == null)
        {
            return;
        }

        for (CatalystComposition comp : project.compositions)
        {
            for (CatalystLayer layer : comp.layers)
            {
                cleanupLayerResources(layer);
            }
        }
    }

    private void cycleBlendMode()
    {
        CatalystLayer sel = this.getSelectedLayer();
        if (sel == null) return;

        String[] modes = new String[] {"NORMAL", "MULTIPLY", "SCREEN", "ADD", "OVERLAY"};
        int idx = 0;
        for (int i = 0; i < modes.length; i++)
        {
            if (modes[i].equalsIgnoreCase(sel.blendMode))
            {
                idx = (i + 1) % modes.length;
                break;
            }
        }
        sel.blendMode = modes[idx];
        this.updateInspectorForm();
        this.saveAndRefresh();
    }

    public void updateInspectorForm()
    {
        CatalystLayer sel = this.getSelectedLayer();

        if (sel == null)
        {
            this.inspectorEmptyLabel.setVisible(true);
            this.inspectorForm.setVisible(false);
        }
        else
        {
            this.inspectorEmptyLabel.setVisible(false);
            this.inspectorForm.setVisible(true);

            this.layerNameInput.setText(sel.name);
            this.layerTypeLabel.label = IKey.raw("Type: " + (sel.layerType != null ? sel.layerType.name() : "SOLID"));

            boolean isText = sel.layerType == CatalystLayer.LayerType.TEXT;
            boolean hasMedia = sel.layerType == CatalystLayer.LayerType.SCENE
                || sel.layerType == CatalystLayer.LayerType.VIDEO
                || sel.layerType == CatalystLayer.LayerType.IMAGE
                || sel.layerType == CatalystLayer.LayerType.AUDIO;

            this.textControlsGroup.setVisible(isText);
            if (isText)
            {
                this.layerTextarea.setText(sel.resourcePath != null ? sel.resourcePath : "");
                this.layerFontSizeInput.setValue(sel.fontSize > 0 ? sel.fontSize : 16);
                this.layerTextColorPicker.setColor(sel.textColor);
                this.layerLineWrapButton.label = IKey.raw("Wrap: " + (sel.lineWrapping ? "ON" : "OFF"));
                this.layerShadowButton.label = IKey.raw("Shadow: " + (sel.shadow ? "ON" : "OFF"));
            }

            this.layerResourceLabel.setVisible(hasMedia);
            if (hasMedia)
            {
                String sourceName = "(None)";
                if (sel.resourcePath != null && !sel.resourcePath.trim().isEmpty())
                {
                    String trimmed = sel.resourcePath.trim();
                    File f = new File(trimmed);
                    String name = f.getName();
                    sourceName = (!name.isEmpty()) ? name : trimmed;
                }
                this.layerResourceLabel.label = IKey.raw("Source: " + sourceName);
            }

            boolean hasAudioSettings = sel.layerType == CatalystLayer.LayerType.AUDIO || sel.layerType == CatalystLayer.LayerType.VIDEO;
            boolean isScene = sel.layerType == CatalystLayer.LayerType.SCENE;
            boolean hasMediaDuration = sel.mediaDuration > 0;
            boolean showMediaGroup = hasAudioSettings || hasMediaDuration || isScene;

            this.mediaGroup.setVisible(showMediaGroup);
            if (showMediaGroup)
            {
                this.audioControlsGroup.setVisible(hasAudioSettings);
                if (hasAudioSettings)
                {
                    this.layerVolumeInput.setValue(sel.volume);
                    this.layerPanInput.setValue(sel.pan);
                    this.layerAudioOffsetInput.setValue(sel.audioOffset);
                }
                this.layerExtendDurationBtn.setVisible(hasMediaDuration);
                if (hasMediaDuration)
                {
                    this.layerExtendDurationBtn.label = IKey.raw("Extend to Media Length (" + sel.mediaDuration + " f)");
                }
                this.layerFilmFpsLabel.setVisible(isScene);
                this.layerFilmFpsInput.setVisible(isScene);
                if (isScene)
                {
                    this.layerFilmFpsInput.setValue(sel.filmFps > 0 ? sel.filmFps : 20);
                }
            }

            boolean hasTransform = sel.layerType != CatalystLayer.LayerType.AUDIO;
            this.transformGroup.setVisible(hasTransform);
            if (hasTransform)
            {
                this.layerPosX.setValue(sel.posX);
                this.layerPosY.setValue(sel.posY);
                this.layerScaleX.setValue(sel.scaleX);
                this.layerScaleY.setValue(sel.scaleY);
                this.layerRotation.setValue(sel.rotation);
                this.layerAnchorX.setValue(sel.anchorX);
                this.layerAnchorY.setValue(sel.anchorY);
            }

            this.layerOpacityInput.setValue(sel.opacity);
            this.layerColorPicker.setColor(sel.color);
            this.layerBlendButton.label = IKey.raw("Blend: " + (sel.blendMode != null ? sel.blendMode : "NORMAL"));
            this.layerVisibleButton.label = IKey.raw(sel.visible ? "Visible: ON" : "Visible: OFF");
            this.layerLockedButton.label = IKey.raw(sel.locked ? "Lock: ON" : "Lock: OFF");
        }
    }

    public void openCompositionSettingsModal()
    {
        if (this.activeProject == null) return;
        CatalystComposition comp = this.activeProject.getActiveComposition();
        if (comp == null) return;

        UIOverlayPanel modal = new UIOverlayPanel(IKey.raw("Composition Settings"));

        UITextbox modalNameInput = new UITextbox(120, (t) -> {});
        modalNameInput.setText(comp.name);
        modalNameInput.h(20);

        UILabel modalDurCalc = new UILabel(IKey.raw(""), Colors.GRAY);
        modalDurCalc.h(14);

        UITrackpad modalFpsInput = new UITrackpad((v) -> {});
        modalFpsInput.limit(1, 240, true).setValue(comp.fps > 0 ? comp.fps : 60);
        modalFpsInput.h(20);

        double curSecs = comp.fps > 0 ? (double) comp.duration / comp.fps : 5.0;
        UITrackpad modalDurationInput = new UITrackpad((v) -> {});
        modalDurationInput.limit(0.1, 3600.0).setValue(curSecs);
        modalDurationInput.h(20);

        Runnable updateCalc = () ->
        {
            int fps = (int) modalFpsInput.getValue();
            double secs = modalDurationInput.getValue();
            int frames = (int) Math.round(secs * fps);
            modalDurCalc.label = IKey.raw(String.format("%.1f s (%d frames @ %d FPS)", secs, frames, fps));
        };
        modalFpsInput.callback = (v) -> updateCalc.run();
        modalDurationInput.callback = (v) -> updateCalc.run();
        updateCalc.run();

        UITrackpad modalWidthInput = new UITrackpad((v) -> {});
        modalWidthInput.limit(128, 7680, true).setValue(comp.width > 0 ? comp.width : 1920);
        modalWidthInput.h(20);

        UITrackpad modalHeightInput = new UITrackpad((v) -> {});
        modalHeightInput.limit(128, 4320, true).setValue(comp.height > 0 ? comp.height : 1080);
        modalHeightInput.h(20);

        UIButton applyCloseBtn = new UIButton(IKey.raw("Apply & Close"), (b) ->
        {
            this.pushUndo();
            String n = modalNameInput.getText().trim();
            if (!n.isEmpty()) comp.name = n;
            comp.fps = (int) modalFpsInput.getValue();
            double durS = modalDurationInput.getValue();
            comp.duration = (int) Math.round(durS * comp.fps);
            comp.width = (int) modalWidthInput.getValue();
            comp.height = (int) modalHeightInput.getValue();

            this.saveAndRefresh();
            this.rebuildCompTabs();
            this.updateTitleLabel();
            modal.close.clickItself();
        });
        applyCloseBtn.h(20);

        UIButton cancelBtn = new UIButton(IKey.raw("Cancel"), (b) -> modal.close.clickItself());
        cancelBtn.h(20);

        UIElement container = new UIElement();
        container.relative(modal.content).xy(10, 6).w(1F, -20).hTo(modal.content.area, 1F, -6);
        container.column(6).vertical().stretch();
        container.add(
            UI.label(IKey.raw("Composition Name:")).h(14),
            modalNameInput,
            UI.label(IKey.raw("Frame Rate (FPS):")).h(14),
            modalFpsInput,
            UI.label(IKey.raw("Duration (Seconds):")).h(14),
            modalDurationInput,
            modalDurCalc,
            UI.label(IKey.raw("Resolution (W x H):")).h(14),
            UI.row(modalWidthInput, modalHeightInput).h(20),
            UI.row(applyCloseBtn, cancelBtn).h(20)
        );

        modal.content.add(container);
        UIOverlay.addOverlay(this.getContext(), modal, 340, 260);
    }

    public void pushUndo()
    {
        if (this.activeProject != null && !this.isUndoingOrRedoing)
        {
            this.undoStack.add(this.activeProject.toData());
            if (this.undoStack.size() > 50)
            {
                this.undoStack.remove(0);
            }
            this.redoStack.clear();
        }
    }

    public void undo()
    {
        if (this.activeProject != null && !this.undoStack.isEmpty())
        {
            this.isUndoingOrRedoing = true;
            this.redoStack.add(this.activeProject.toData());
            mchorse.bbs_mod.data.types.MapType previous = this.undoStack.remove(this.undoStack.size() - 1);
            this.activeProject.fromData(previous);
            this.saveAndRefresh();
            this.rebuildCompTabs();
            this.updateInspectorForm();
            this.isUndoingOrRedoing = false;
        }
    }

    public void redo()
    {
        if (this.activeProject != null && !this.redoStack.isEmpty())
        {
            this.isUndoingOrRedoing = true;
            this.undoStack.add(this.activeProject.toData());
            mchorse.bbs_mod.data.types.MapType next = this.redoStack.remove(this.redoStack.size() - 1);
            this.activeProject.fromData(next);
            this.saveAndRefresh();
            this.rebuildCompTabs();
            this.updateInspectorForm();
            this.isUndoingOrRedoing = false;
        }
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.isExporting && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.cancelExport();
            return true;
        }

        if (!context.isFocused())
        {
            if (context.isPressed(GLFW.GLFW_KEY_SPACE))
            {
                this.togglePlayback();
                return true;
            }
            if (context.isPressed(GLFW.GLFW_KEY_Z) && Window.isCtrlPressed())
            {
                if (Window.isShiftPressed())
                {
                    this.redo();
                }
                else
                {
                    this.undo();
                }
                return true;
            }
            if (context.isPressed(GLFW.GLFW_KEY_Y) && Window.isCtrlPressed())
            {
                this.redo();
                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_LEFT))
            {
                int step = Window.isShiftPressed() ? 10 : 1;
                this.seekToFrame(Math.max(0, this.currentFrame - step));
                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_RIGHT))
            {
                int step = Window.isShiftPressed() ? 10 : 1;
                CatalystComposition comp = this.activeProject != null ? this.activeProject.getActiveComposition() : null;
                int maxFrame = comp != null ? Math.max(0, comp.duration - 1) : Integer.MAX_VALUE;
                this.seekToFrame(Math.min(maxFrame, this.currentFrame + step));
                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_N) && !Window.isCtrlPressed())
            {
                if (this.catalystTimeline != null)
                {
                    this.catalystTimeline.snapping = !this.catalystTimeline.snapping;
                    return true;
                }
            }

            if (context.isPressed(GLFW.GLFW_KEY_B) && !context.isFocused() && !Window.isCtrlPressed())
            {
                this.toggleMediaPool();
                return true;
            }

            /* ── AE-style property shortcuts (P/S/R/T/A/U) ── */
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null && !context.isFocused())
            {
                boolean handled = false;

                if (context.isPressed(GLFW.GLFW_KEY_P))
                {
                    /* Position */
                    handled = this.toggleLayerProp(sel, mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION);
                }
                else if (context.isPressed(GLFW.GLFW_KEY_S))
                {
                    /* Scale */
                    handled = this.toggleLayerProp(sel, mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE);
                }
                else if (context.isPressed(GLFW.GLFW_KEY_R))
                {
                    /* Rotation */
                    handled = this.toggleLayerProp(sel, mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION);
                }
                else if (context.isPressed(GLFW.GLFW_KEY_T))
                {
                    /* Opacity (Transparency) */
                    handled = this.toggleLayerProp(sel, mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY);
                }
                else if (context.isPressed(GLFW.GLFW_KEY_A))
                {
                    /* Anchor Point */
                    handled = this.toggleLayerProp(sel, mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR);
                }
                else if (context.isPressed(GLFW.GLFW_KEY_U))
                {
                    /* U = AE standard: toggle properties with keyframes */
                    if (sel.expanded && sel.expandedProps != 0)
                    {
                        sel.expanded = false;
                        sel.expandedProps = 0;
                    }
                    else
                    {
                        int mask = 0;
                        if (sel.hasAnyKeyframes(mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION))
                            mask |= mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION;
                        if (sel.hasAnyKeyframes(mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE))
                            mask |= mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE;
                        if (sel.hasAnyKeyframes(mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION))
                            mask |= mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION;
                        if (sel.hasAnyKeyframes(mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY))
                            mask |= mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY;
                        if (sel.hasAnyKeyframes(mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR))
                            mask |= mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR;

                        if (mask != 0)
                        {
                            sel.expanded = true;
                            sel.expandedProps = mask;
                        }
                        else
                        {
                            sel.expanded = false;
                            sel.expandedProps = 0;
                        }
                    }
                    handled = true;
                }
                else if (context.isPressed(GLFW.GLFW_KEY_J))
                {
                    /* J = Jump to previous keyframe */
                    if (this.catalystTimeline != null)
                    {
                        this.catalystTimeline.jumpToKeyframe(false);
                    }
                    handled = true;
                }
                else if (context.isPressed(GLFW.GLFW_KEY_K))
                {
                    /* K = Jump to next keyframe */
                    if (this.catalystTimeline != null)
                    {
                        this.catalystTimeline.jumpToKeyframe(true);
                    }
                    handled = true;
                }
                else if (context.isPressed(GLFW.GLFW_KEY_M))
                {
                    /* M = add marker at playhead */
                    if (this.catalystTimeline != null)
                    {
                        this.catalystTimeline.addMarkerAtPlayhead();
                    }
                    handled = true;
                }

                if (handled) return true;
            }
            else if (sel == null)
            {
                if (context.isPressed(GLFW.GLFW_KEY_J))
                {
                    if (this.catalystTimeline != null)
                    {
                        this.catalystTimeline.jumpToKeyframe(false);
                    }
                    return true;
                }
                if (context.isPressed(GLFW.GLFW_KEY_K))
                {
                    if (this.catalystTimeline != null)
                    {
                        this.catalystTimeline.jumpToKeyframe(true);
                    }
                    return true;
                }
                if (context.isPressed(GLFW.GLFW_KEY_M))
                {
                    /* M without selection: still add marker */
                    if (this.catalystTimeline != null)
                    {
                        this.catalystTimeline.addMarkerAtPlayhead();
                    }
                    return true;
                }
            }
        }
        return super.subKeyPressed(context);
    }

    /**
     * Toggles a property bit in a layer's expandedProps bitmask.
     * If the bit was already on, turns it off. Also ensures the layer is expanded.
     */
    private boolean toggleLayerProp(CatalystLayer layer, int propBit)
    {
        if (layer == null) return false;
        if (!layer.expanded)
        {
            layer.expanded = true;
            layer.expandedProps = propBit;
        }
        else if ((layer.expandedProps & propBit) != 0)
        {
            layer.expandedProps &= ~propBit;
            if (layer.expandedProps == 0) layer.expanded = false;
        }
        else
        {
            layer.expandedProps |= propBit;
        }
        return true;
    }


    public CatalystLayer getSelectedLayer()
    {
        return this.catalystTimeline != null ? this.catalystTimeline.getFirstSelectedLayer() : null;
    }

    public void saveAndRefresh()
    {
        if (this.activeProject != null)
        {
            CatalystProjectManager.saveProject(this.activeProject);
        }
    }

    public void cutSelectedClip()
    {
        if (this.catalystTimeline != null)
        {
            this.catalystTimeline.cutSelected();
            this.saveAndRefresh();
            this.updateInspectorForm();
        }
    }

    public void stopAllAudio()
    {
        if (this.activeProject != null)
        {
            for (CatalystComposition comp : this.activeProject.compositions)
            {
                for (CatalystLayer layer : comp.layers)
                {
                    BBSModClient.getSounds().stopOwned(layer);
                }
            }
        }
        for (SoundPlayer player : this.activeAudioPlayers.values())
        {
            if (player != null)
            {
                try
                {
                    player.stop();
                    player.delete();
                }
                catch (Exception ignored) {}
            }
        }
        this.activeAudioPlayers.clear();
        this.layerAudioLinks.clear();
        this.masterClockPlayer = null;
        BBSModClient.getSounds().stopOwned(this);

        if (this.scrubSource > 0)
        {
            try
            {
                AL10.alSourceStop(this.scrubSource);
                AL10.alSourcei(this.scrubSource, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(this.scrubSource);
            }
            catch (Exception ignored) {}
            this.scrubSource = -1;
        }
        if (this.scrubBuffer > 0)
        {
            try
            {
                AL10.alDeleteBuffers(this.scrubBuffer);
            }
            catch (Exception ignored) {}
            this.scrubBuffer = -1;
        }
        if (this.scrubByteBuffer != null)
        {
            MemoryUtil.memFree(this.scrubByteBuffer);
            this.scrubByteBuffer = null;
        }
    }

    /* ════════════════════════════════════════════════════════
     *  HELPERS & RENDER EXPORT
     * ════════════════════════════════════════════════════════ */

    public CatalystComposition getActiveComposition()
    {
        return this.activeProject != null ? this.activeProject.getActiveComposition() : null;
    }

    public void enterExportMode()
    {
        this.topBar.setVisible(false);
        this.compTabStrip.setVisible(false);
        this.bottomArea.setVisible(false);
        this.editorActions.setVisible(false);
        this.projectsView.setVisible(false);

        this.renderMonitorHud.setVisible(true);
        this.previewArea.relative(this).xy(0, 0).w(1F).h(1F);
        this.previewArea.resize();
        this.resize();
    }

    public void exitExportMode()
    {
        this.renderMonitorHud.setVisible(false);
        this.topBar.setVisible(true);
        this.compTabStrip.setVisible(true);
        this.bottomArea.setVisible(true);
        this.editorActions.setVisible(true);
        this.projectsView.setVisible(false);

        this.previewArea.relative(this).y(24).w(1F).h(0.53F, -46);
        this.updateVisibility();
        this.previewArea.resize();
        this.resize();
        if (this.getParent() != null)
        {
            this.getParent().resize();
        }
    }

    /* ════════════════════════════════════════════════════════
     *  OFFLINE EXPORT PIPELINE (Stage 48 / 48.2 Deliver Integration)
     * ════════════════════════════════════════════════════════ */

    public void openExportModal()
    {
        if (this.activeProject == null) return;
        CatalystComposition comp = this.activeProject.getActiveComposition();
        if (comp == null) return;

        UIDeliverOverlayPanel deliver = new UIDeliverOverlayPanel(this);
        UIOverlay.addOverlay(this.getContext(), deliver, 720, 450);
    }

    public void startExport(CatalystComposition comp, File outputFile, int width, int height, int fps, int bitrateMbps)
    {
        VideoExportProfile profile = VideoExportProfile.getBuiltInPresets().get(0).copy();
        profile.setWidth(width);
        profile.setHeight(height);
        profile.setFrameRate(fps);
        profile.setBitrate(bitrateMbps);
        this.startExportFromProfile(profile, outputFile, comp != null ? comp.duration : 100, null, null);
    }

    public void startExportFromProfile(VideoExportProfile profile, File outputFile, int duration, RenderJob job, Runnable onFinished)
    {
        if (this.isExporting || this.activeProject == null) return;
        CatalystComposition comp = this.activeProject.getActiveComposition();
        if (comp == null) return;

        /* Stop normal live playback and audio */
        this.isPlaying = false;
        this.stopAllAudio();

        int width = profile.getWidth() > 0 ? profile.getWidth() : (comp.width > 0 ? comp.width : 1920);
        int height = profile.getHeight() > 0 ? profile.getHeight() : (comp.height > 0 ? comp.height : 1080);
        double fps = profile.getFrameRate() > 0 ? profile.getFrameRate() : (comp.fps > 0 ? comp.fps : 60);

        if (width % 2 != 0) width++;
        if (height % 2 != 0) height++;

        this.exportWidth = width;
        this.exportHeight = height;
        this.exportFps = (int) Math.round(fps);
        this.exportTargetFile = outputFile;
        this.exportCurrentFrame = 0;
        this.exportTotalFrames = Math.max(1, duration);
        this.exportStartTime = System.currentTimeMillis();
        this.currentExportJob = job;
        this.onExportFinishedCallback = onFinished;
        this.activeExportProfile = profile;

        /* Ensure parent directory exists */
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists())
        {
            parentDir.mkdirs();
        }

        /* 1. Synchronous Video Decoding Flag for VideoPlayer */
        VideoPlayer.forcedRecording = true;

        /* 2. Audio Processing */
        File audioSourceFile = null;
        if (profile.isExportAudio())
        {
            boolean hasSolo = false;
            for (CatalystLayer l : comp.layers)
            {
                if (l.solo) { hasSolo = true; break; }
            }

            List<AudioClip> audioClips = new ArrayList<>();
            int compFps = comp.fps > 0 ? comp.fps : 60;

            for (CatalystLayer layer : comp.layers)
            {
                if (hasSolo && !layer.solo) continue;
                if (layer.muted || layer.volume <= 0) continue;
                if ((layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.VIDEO)
                    && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                {
                    try
                    {
                        AudioClip clip = new AudioClip();
                        clip.audio.set(Link.create(layer.resourcePath.trim()));
                        int tick = (int) Math.round((layer.startFrame * 20.0) / compFps);
                        int durTicks = (int) Math.round((layer.duration * 20.0) / compFps);
                        int offsetTicks = (int) Math.round(((layer.mediaOffset + layer.audioOffset) * 20.0) / compFps);
                        clip.tick.set(tick);
                        clip.duration.set(durTicks);
                        clip.offset.set(offsetTicks);
                        clip.volume.set(layer.volume);
                        clip.pan.set(layer.pan);
                        clip.enabled.set(true);
                        audioClips.add(clip);
                    }
                    catch (Exception ignored) {}
                }
            }

            if (!audioClips.isEmpty())
            {
                File tempWav = new File(parentDir != null ? parentDir : BBSMod.getGameFolder(), outputFile.getName() + ".audio_mix.wav");
                int totalDurationTicks = (int) Math.round((duration * 20.0) / compFps);
                float fromSec = 0F;
                float toSec = (float) duration / (float) compFps;
                if (AudioRenderer.renderAudio(tempWav, audioClips, totalDurationTicks, 48000, fromSec, toSec))
                {
                    audioSourceFile = tempWav;
                    this.exportTempAudioMixFile = tempWav;
                }
            }

            /* Fallback to direct single file if mix wasn't created */
            if (audioSourceFile == null)
            {
                for (CatalystLayer layer : comp.layers)
                {
                    if (hasSolo && !layer.solo) continue;
                    if (layer.muted || layer.volume <= 0) continue;
                    if ((layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.VIDEO)
                        && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
                    {
                        try
                        {
                            File f = new File(layer.resourcePath.trim());
                            if (f.exists() && f.isFile())
                            {
                                audioSourceFile = f;
                                break;
                            }
                            else
                            {
                                Link link = Link.create(layer.resourcePath.trim());
                                File linkedFile = new File(BBSMod.getAudioFolder(), link.path);
                                if (linkedFile.exists() && linkedFile.isFile())
                                {
                                    audioSourceFile = linkedFile;
                                    break;
                                }
                            }
                        }
                        catch (Exception ignored) {}
                    }
                }
            }
        }

        String baseName = outputFile.getName();
        String ext = profile.getFormat().getExtension();
        if (baseName.toLowerCase().endsWith(ext.toLowerCase()))
        {
            baseName = baseName.substring(0, baseName.length() - ext.length());
        }

        /* 3. Audio-only Export Mode */
        if (!profile.isExportVideo() && profile.isExportAudio())
        {
            if (audioSourceFile == null)
            {
                mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Catalyst] Timeline'da dışa aktarılacak aktif ses parçası bulunamadı!");
                VideoPlayer.forcedRecording = false;
                if (job != null) job.setStatus(RenderJob.Status.CANCELLED);
                return;
            }

            FFmpegCommandBuilder audioCmdBuilder = new FFmpegCommandBuilder(profile)
                .movieName(baseName)
                .outputFolder(outputFile.getParentFile())
                .audio(audioSourceFile);

            List<String> rawAudioArgs = audioCmdBuilder.buildAudioOnlyArgs(outputFile);
            List<String> audioCmd = new ArrayList<>();
            for (String a : rawAudioArgs)
            {
                audioCmd.add(a.replace("\"", "").trim());
            }

            try
            {
                File workDir = parentDir != null && parentDir.exists() ? parentDir : BBSMod.getGameFolder();
                ProcessBuilder pb = new ProcessBuilder(audioCmd);
                pb.directory(workDir);
                pb.redirectErrorStream(true);

                File logFile = new File(workDir, outputFile.getName() + ".audio_export.log");
                pb.redirectOutput(logFile);

                Process p = pb.start();
                p.waitFor(1, TimeUnit.MINUTES);
                p.destroy();

                if (job != null)
                {
                    job.setStatus(RenderJob.Status.COMPLETED);
                    if (RenderQueue.getActiveJob() == job)
                    {
                        RenderQueue.setActiveJob(null);
                    }
                }

                VideoPlayer.forcedRecording = false;
                if (this.exportTempAudioMixFile != null && this.exportTempAudioMixFile.exists())
                {
                    this.exportTempAudioMixFile.delete();
                    this.exportTempAudioMixFile = null;
                }

                mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§a[Catalyst] Ses dışa aktarma tamamlandı: " + outputFile.getName());
                UIUtils.playClick(0.5F);
                if (outputFile.getParentFile() != null && outputFile.getParentFile().exists())
                {
                    UIUtils.openFolder(outputFile.getParentFile());
                }

                if (onFinished != null)
                {
                    onFinished.run();
                }
                return;
            }
            catch (Exception e)
            {
                e.printStackTrace();
                VideoPlayer.forcedRecording = false;
                if (job != null) job.setStatus(RenderJob.Status.CANCELLED);
                mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Catalyst] Ses dışa aktarma hatası: " + e.getMessage());
                return;
            }
        }

        FFmpegCommandBuilder cmdBuilder = new FFmpegCommandBuilder(profile)
            .movieName(baseName)
            .inputResolution(width, height)
            .inputFramerate(fps)
            .outputFolder(outputFile.getParentFile());

        if (profile.isExportAudio() && audioSourceFile != null && profile.getFormat().isAudioSupported() && !profile.getAudioCodec().isNone())
        {
            cmdBuilder.audio(audioSourceFile);
        }

        List<String> rawArgs = cmdBuilder.buildRecordingArgs();
        List<String> cmd = new ArrayList<>();
        for (String a : rawArgs)
        {
            cmd.add(a.replace("\"", "").trim());
        }

        try
        {
            File workDir = parentDir != null && parentDir.exists() ? parentDir : BBSMod.getGameFolder();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            File logFile = new File(workDir, outputFile.getName() + ".export.log");
            pb.redirectOutput(logFile);

            this.exportProcess = pb.start();
            OutputStream os = this.exportProcess.getOutputStream();
            this.exportChannel = Channels.newChannel(os);

            /* Allocate RGBA pixel byte buffer (width * height * 4) */
            int bufSize = width * height * 4;
            if (this.exportBuffer != null)
            {
                MemoryUtil.memFree(this.exportBuffer);
                this.exportBuffer = null;
            }
            this.exportBuffer = MemoryUtil.memAlloc(bufSize);

            MinecraftClient mc = MinecraftClient.getInstance();
            int screenFboId = (mc != null && mc.getFramebuffer() != null) ? mc.getFramebuffer().fbo : 0;
            BBSModClient.getVideoRecorder().setExportFboId(screenFboId);

            this.renderMonitorHud.start(comp.name, profile, duration, duration, fps, this::cancelExport);
            this.enterExportMode();
            this.isExporting = true;
            UIUtils.playClick(2.0F);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            if (job != null)
            {
                job.setStatus(RenderJob.Status.CANCELLED);
            }
            this.cancelExport();
        }
    }

    public void stepExport(UIContext context)
    {
        if (!this.isExporting || this.activeProject == null) return;
        CatalystComposition comp = this.activeProject.getActiveComposition();
        if (comp == null || this.exportProcess == null || !this.exportProcess.isAlive())
        {
            this.cancelExport();
            return;
        }

        /* Flush any pending batches before switching GL framebuffers/matrices */
        context.batcher.flush();

        /* 1. Set current playhead frame for deterministic offline state */
        this.currentFrame = this.exportCurrentFrame;
        comp.playhead = this.exportCurrentFrame;

        /* 2. Bind active Minecraft screen framebuffer */
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.gl.Framebuffer screenFbo = (mc != null) ? mc.getFramebuffer() : null;
        int screenFboId = (screenFbo != null) ? screenFbo.fbo : 0;

        int prevFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevReadFbo = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissorEnabled)
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        if (screenFbo != null)
        {
            screenFbo.beginWrite(true);
        }
        else
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }

        /* Determine actual FBO texture dimensions (may differ from exportWidth/Height
         * if the window is not pixel-perfect, e.g. windowed mode with OS title bar). */
        int fboTexW = (screenFbo != null) ? screenFbo.textureWidth  : this.exportWidth;
        int fboTexH = (screenFbo != null) ? screenFbo.textureHeight : this.exportHeight;

        /* Set viewport to the FULL FBO texture so clear + render covers every pixel
         * that glReadPixels will later read.  If we set only (0,0,exportW,exportH)
         * and fboTexH > exportH, the rows above exportH are cleared but never written,
         * so glReadPixels picking them up produces a black bar at the top of the video. */
        com.mojang.blaze3d.systems.RenderSystem.viewport(0, 0, fboTexW, fboTexH);

        GL11.glColorMask(true, true, true, true);
        com.mojang.blaze3d.systems.RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, false);

        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        if (depthEnabled)
        {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        }

        /*
         * 3. Render composition into the batcher's GUI coordinate space.
         *
         * ROOT CAUSE A (VHS tracking): RenderSystem.setProjectionMatrix() has no
         *   effect; the batcher re-uploads ortho(0, guiW, guiH, 0) on flush().
         * ROOT CAUSE B (bottom-right overflow): the DrawContext matrix stack carries
         *   accumulated scroll / translation offsets from the surrounding UI panels.
         *   stack.peek().identity() only resets the top element AFTER any parent
         *   transforms were composed in; we must reset the WHOLE matrix via
         *   context.resetMatrix() (= getMatrices().loadIdentity()).
         *
         * FIX: reset the matrix to identity, then apply a uniform scale so that
         *   composition coords (0..exportWidth, 0..exportHeight) map to the
         *   batcher's GUI units (0..guiW, 0..guiH).
         */
        net.minecraft.client.util.Window window = mc.getWindow();
        int guiW = window.getScaledWidth();
        int guiH = window.getScaledHeight();
        float scaleX = (float) guiW / (float) this.exportWidth;
        float scaleY = (float) guiH / (float) this.exportHeight;

        /* Hard-scissor the draw area to [0,0,guiW,guiH] to catch any layer that
         * would overflow the GUI-unit viewport.  We drive the scissor directly
         * (in physical pixels) to avoid context.globalX/Y adding panel offsets. */
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        /* OpenGL scissor Y=0 is bottom; convert from top-left GUI coords:
         *   physY = framebufferHeight - guiH * guiScale  (= 0 here since guiH covers full screen)
         */
        int physScissorH = (screenFbo != null) ? screenFbo.textureHeight : (guiH * (int) window.getScaleFactor());
        GL11.glScissor(0, 0, (screenFbo != null) ? screenFbo.textureWidth : (guiW * (int) window.getScaleFactor()), physScissorH);

        /* Reset the WHOLE matrix (not just the top element) so no UI offsets leak in */
        context.resetMatrix();
        MatrixStack stack = context.batcher.getContext().getMatrices();
        stack.push();
        stack.scale(scaleX, scaleY, 1.0F);

        this.renderCompositionLayers(context, comp, this.exportCurrentFrame, this.exportWidth, this.exportHeight, this.exportFps);
        context.batcher.flush();
        GL11.glFinish();
        stack.pop();

        /* Restore identity after pop so the surrounding UI isn't shifted */
        context.resetMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (depthEnabled)
        {
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        }

        /* 4. Read back pixels from screen framebuffer.
         *
         * STRIDE FIX:
         *   GL_PACK_ALIGNMENT = 1 — no row padding bytes inserted by the driver.
         *   GL_PACK_ROW_LENGTH = exportWidth — destination row stride locked to
         *     exactly exportWidth pixels, preventing VHS-tracking diagonal shift
         *     if the FBO texture row is wider than the read rectangle.
         *   Clamp readW/readH to FBO texture size to prevent driver out-of-bounds.
         */
        int screenW = (screenFbo != null) ? screenFbo.textureWidth  : this.exportWidth;
        int screenH = (screenFbo != null) ? screenFbo.textureHeight : this.exportHeight;
        int offsetY = Math.max(0, screenH - this.exportHeight);
        int readW   = Math.min(this.exportWidth, screenW);
        int readH   = Math.min(this.exportHeight, screenH);

        this.exportBuffer.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,  1);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, this.exportWidth);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS,  0);
        GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, screenFboId);
        int readBufferMode = (screenFboId != 0) ? GL30.GL_COLOR_ATTACHMENT0 : GL11.GL_BACK;
        GL11.glReadBuffer(readBufferMode);

        GL11.glReadPixels(0, offsetY, readW, readH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.exportBuffer);

        /* If screen framebuffer was shorter than export height (e.g. small window),
         * stretch the read rows to fill the full export buffer so no unwritten black rows remain. */
        if (readH < this.exportHeight)
        {
            int rowBytes = this.exportWidth * 4;
            long baseAddr = MemoryUtil.memAddress(this.exportBuffer);
            for (int dstY = this.exportHeight - 1; dstY >= 0; dstY--)
            {
                int srcY = (int) ((long) dstY * readH / this.exportHeight);
                if (srcY != dstY)
                {
                    MemoryUtil.memCopy(
                        baseAddr + (long) srcY * rowBytes,
                        baseAddr + (long) dstY * rowBytes,
                        rowBytes
                    );
                }
            }
        }
        this.exportBuffer.rewind();

        /* Y-FLIP / BLACK BAR FIX:
         * The batcher's ortho projection (0, guiW, guiH, 0) maps the composition's
         * top-left to OpenGL's bottom-left corner of the FBO.
         * glReadPixels(0, 0, W, H) reads from the GL bottom-left upward, which is
         * exactly what we want — the full composition is at rows 0..readH-1.
         *
         * If the FBO textureHeight > readH (e.g. windowed mode with title bar
         * consuming some pixels from the logical window), the render still fills
         * rows 0..readH-1 correctly and the extra rows at the top of the FBO are
         * unused, so no correction is needed.
         *
         * However: FFmpeg expects top-down RGBA.  OpenGL bottom-up glReadPixels
         * means row 0 of the buffer = bottom of the image = TOP of composition
         * (because GUI Y=0 is top, which maps to GL Y=max i.e. the bottom of the
         * FBO rectangle — so the flip cancels out and the buffer is already
         * top-down).  No additional vertical flip is needed.
         */

        /* Restore GL pack state to driver defaults */
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,  4);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);

        if (this.exportCurrentFrame < 10)
        {
            byte r = this.exportBuffer.get(0);
            byte g = this.exportBuffer.get(1);
            byte b = this.exportBuffer.get(2);
            byte a = this.exportBuffer.get(3);
            System.out.println("[Catalyst Capture CHECK] Frame " + this.exportCurrentFrame
                + " -> R:" + (r & 0xFF) + " G:" + (g & 0xFF) + " B:" + (b & 0xFF) + " A:" + (a & 0xFF)
                + "  guiW=" + guiW + " guiH=" + guiH
                + "  scaleX=" + scaleX + " scaleY=" + scaleY
                + "  fboId=" + screenFboId + " readW=" + readW + " readH=" + readH);
        }
        this.exportBuffer.rewind();

        /* 5. Restore viewport, scissor, and previously bound FBO */
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
        com.mojang.blaze3d.systems.RenderSystem.viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        if (scissorEnabled)
        {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }

        /* 6. Write frame bytes to FFmpeg stdin channel.
         *
         * FULL-FRAME GUARANTEE: always write exactly exportWidth * exportHeight * 4
         * bytes.  If readW or readH was clamped (FBO smaller than export size), the
         * remaining bytes in exportBuffer are already 0 (buffer.clear() zeroed them
         * on DirectByteBuffer allocation) and we pad with those zeros so FFmpeg
         * never receives a partial frame, which would corrupt all subsequent frames.
         */
        int fullFrameBytes = this.exportWidth * this.exportHeight * 4;
        try
        {
            if (this.exportChannel != null && this.exportChannel.isOpen())
            {
                this.exportBuffer.position(0);
                this.exportBuffer.limit(fullFrameBytes);
                while (this.exportBuffer.hasRemaining())
                {
                    this.exportChannel.write(this.exportBuffer);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.cancelExport();
            return;
        }

        /* 8. Advance frame or complete export */
        this.exportCurrentFrame++;
        this.renderMonitorHud.updateProgress(this.exportCurrentFrame, this.exportCurrentFrame);

        if (this.exportCurrentFrame >= this.exportTotalFrames)
        {
            this.finishExport();
        }
    }

    public void cancelExport()
    {
        this.isExporting = false;
        VideoPlayer.forcedRecording = false;
        BBSRendering.setCustomSize(false);
        BBSModClient.getVideoRecorder().setExportFboId(-1);
        if (this.exportTempAudioMixFile != null && this.exportTempAudioMixFile.exists())
        {
            this.exportTempAudioMixFile.delete();
            this.exportTempAudioMixFile = null;
        }

        if (this.exportChannel != null)
        {
            try { this.exportChannel.close(); }
            catch (Exception ignored) {}
            this.exportChannel = null;
        }

        if (this.exportProcess != null)
        {
            try
            {
                this.exportProcess.destroyForcibly();
            }
            catch (Exception ignored) {}
            this.exportProcess = null;
        }

        if (this.exportBuffer != null)
        {
            MemoryUtil.memFree(this.exportBuffer);
            this.exportBuffer = null;
        }

        this.exportFbo = null;

        if (this.exportTexture != null)
        {
            this.exportTexture.delete();
            this.exportTexture = null;
        }

        if (this.currentExportJob != null)
        {
            this.currentExportJob.setStatus(RenderJob.Status.CANCELLED);
            if (RenderQueue.getActiveJob() == this.currentExportJob)
            {
                RenderQueue.setActiveJob(null);
            }
            this.currentExportJob = null;
        }

        this.exitExportMode();
    }

    public void finishExport()
    {
        this.isExporting = false;
        VideoPlayer.forcedRecording = false;
        BBSRendering.setCustomSize(false);
        BBSModClient.getVideoRecorder().setExportFboId(-1);
        if (this.exportTempAudioMixFile != null && this.exportTempAudioMixFile.exists())
        {
            this.exportTempAudioMixFile.delete();
            this.exportTempAudioMixFile = null;
        }

        /* Close stdin channel so FFmpeg knows video input is finished */
        if (this.exportChannel != null)
        {
            try { this.exportChannel.close(); }
            catch (Exception ignored) {}
            this.exportChannel = null;
        }

        /* Wait for FFmpeg to finish encoding container */
        if (this.exportProcess != null)
        {
            try
            {
                this.exportProcess.waitFor(2, TimeUnit.MINUTES);
                this.exportProcess.destroy();
            }
            catch (Exception ignored) {}
            this.exportProcess = null;
        }

        /* Cleanup buffers & FBO */
        if (this.exportBuffer != null)
        {
            MemoryUtil.memFree(this.exportBuffer);
            this.exportBuffer = null;
        }

        this.exportFbo = null;

        if (this.exportTexture != null)
        {
            this.exportTexture.delete();
            this.exportTexture = null;
        }

        if (this.currentExportJob != null)
        {
            this.currentExportJob.setStatus(RenderJob.Status.COMPLETED);
            if (RenderQueue.getActiveJob() == this.currentExportJob)
            {
                RenderQueue.setActiveJob(null);
            }
            this.currentExportJob = null;
        }

        this.exitExportMode();

        /* Play completion audio feedback & open destination folder */
        UIUtils.playClick(0.5F);
        if (this.exportTargetFile != null)
        {
            File folder = this.exportTargetFile.getParentFile();
            if (folder != null && folder.exists())
            {
                UIUtils.openFolder(folder);
            }
        }

        if (this.onExportFinishedCallback != null)
        {
            Runnable cb = this.onExportFinishedCallback;
            this.onExportFinishedCallback = null;
            cb.run();
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.draggedAsset != null)
        {
            /* If left mouse button was released, complete the drop operation */
            if (!Window.isMouseButtonPressed(0))
            {
                if (this.catalystTimeline != null && this.catalystTimeline.area.isInside(context))
                {
                    int rawTick = this.catalystTimeline.fromGraphTick(context.mouseX);
                    int dropTick = this.catalystTimeline.snapTick(rawTick);
                    if (dropTick < 0)
                    {
                        dropTick = 0;
                    }
                    this.dropAssetToTimeline(this.draggedAsset, dropTick);
                }
                else if (this.layersContainer != null && this.layersContainer.area.isInside(context))
                {
                    this.dropAssetToTimeline(this.draggedAsset, this.currentFrame);
                }
                this.draggedAsset = null;
            }
            else
            {
                /* Asset is still actively being dragged */
                FontRenderer font = context.batcher.getFont();

                /* If cursor is over timeline, draw magnetic snap guideline & frame badge */
                if (this.catalystTimeline != null && this.catalystTimeline.area.isInside(context))
                {
                    int rawTick = this.catalystTimeline.fromGraphTick(context.mouseX);
                    int hoverTick = this.catalystTimeline.snapTick(rawTick);
                    if (hoverTick < 0)
                    {
                        hoverTick = 0;
                    }
                    int lineX = this.catalystTimeline.toGraphX(hoverTick);
                    if (lineX >= this.catalystTimeline.area.x && lineX <= this.catalystTimeline.area.ex())
                    {
                        context.batcher.box(lineX - 1, this.catalystTimeline.area.y, lineX + 1, this.catalystTimeline.area.ey(), 0xAA00E5FF);
                        String badge = String.valueOf(hoverTick);
                        int badgeW = font.getWidth(badge) + 8;
                        int bx = lineX - badgeW / 2;
                        int by = this.catalystTimeline.area.y + 4;
                        context.batcher.box(bx, by, bx + badgeW, by + 13, 0xEE111625);
                        context.batcher.outline(bx, by, bx + badgeW, by + 13, 0xFF00E5FF, 1);
                        context.batcher.text(badge, bx + 4, by + 3, Colors.WHITE, false);
                    }
                }

                /* Render floating preview card next to mouse cursor */
                int previewW = 120;
                int previewH = 24;
                int px = context.mouseX + 12;
                int py = context.mouseY + 12;

                if (px + previewW > this.area.ex())
                {
                    px = context.mouseX - previewW - 4;
                }
                if (py + previewH > this.area.ey())
                {
                    py = context.mouseY - previewH - 4;
                }

                context.batcher.box(px, py, px + previewW, py + previewH, 0xEE181E29);
                context.batcher.outline(px, py, px + previewW, py + previewH, 0xFF00E5FF, 1);

                mchorse.bbs_mod.ui.utils.icons.Icon icon = Icons.IMAGE;
                int iconColor = 0xFFEEAA44;
                if (this.draggedAsset.type == CatalystMediaAsset.MediaType.VIDEO)
                {
                    icon = Icons.FILM;
                    iconColor = 0xFF5599FF;
                }
                else if (this.draggedAsset.type == CatalystMediaAsset.MediaType.AUDIO)
                {
                    icon = Icons.SOUND;
                    iconColor = 0xFF44DD88;
                }

                context.batcher.icon(icon, iconColor, px + 4, py + 4);

                String displayName = this.draggedAsset.name;
                int maxTextW = previewW - 28;
                if (font.getWidth(displayName) > maxTextW)
                {
                    while (displayName.length() > 3 && font.getWidth(displayName + "...") > maxTextW)
                    {
                        displayName = displayName.substring(0, displayName.length() - 1);
                    }
                    displayName += "...";
                }
                context.batcher.text(displayName, px + 24, py + 8, Colors.WHITE, false);
            }
        }
    }
}

