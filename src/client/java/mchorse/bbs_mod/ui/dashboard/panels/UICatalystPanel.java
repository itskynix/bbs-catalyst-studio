package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.catalyst.CatalystProjectManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.SoundPlayer;
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
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.film.clips.UIVideoClip;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.camera.controller.CatalystSceneCameraController;
import mchorse.bbs_mod.video.VideoPlayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

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
    public UIIcon          fullscreenButton;

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

    /* Resource picker & media controls */
    public UIElement     layerResourceRow;
    public UILabel       layerResourceLabel;
    public UITextbox     layerResourceInput;
    public UIButton      layerPickResourceBtn;
    public UIIcon        layerOpenFolderBtn;
    public UIElement     mediaGroup;
    public UITrackpad    layerVolumeInput;
    public UITrackpad    layerAudioOffsetInput;
    public UIButton      layerExtendDurationBtn;

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

    /* ── Audio Playback State ── */
    private SoundPlayer activeAudioPlayer = null;
    private Link lastPlayedAudioLink = null;
    private CatalystLayer lastPlayedAudioLayer = null;
    private boolean isScrubbing = false;
    private double lastCompRenderMs = 0.0;

    /* ── SCENE Layer Camera State ── */
    /** Drives the BBS world-render camera to the film's position for SCENE layers. */
    private final CatalystSceneCameraController sceneCamera = new CatalystSceneCameraController();
    /** ID of the film currently bound to sceneCamera; used to detect film switching. */
    private String lastSceneFilmId = null;

    /* ── Constructor ── */

    public UICatalystPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.setupTopBar();
        this.setupProjectsView();
        this.setupEditorView();

        /* Order matters: projectsView on top of editor elements so no bleed */
        this.add(this.topBar,
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

            this.resize();
        });

        this.onDisappear(() ->
        {
            this.isPlaying = false;
            this.stopAllAudio();
            /* Release off-screen FBO; UIFilmPanel.enterEditing() will re-enable it if needed */
            BBSRendering.setCustomSize(false);
            /* Deactivate scene camera and clean up frozen film state */
            this.sceneCamera.setFilmCamera(null);
            BBSModClient.getCameraController().remove(CatalystSceneCameraController.class);
            if (this.lastSceneFilmId != null)
            {
                BBSModClient.getFilms().unfreeze(this.lastSceneFilmId);
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
        this.activeProject = project;

        if (project != null)
        {
            project.ensureCompositions();
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
     *  Playback helpers (Audio-Driven Master Clock & A/V Sync)
     * ════════════════════════════════════════════════════════ */

    private CatalystLayer getActiveAudioLayer(CatalystComposition comp, int frame)
    {
        if (comp == null) return null;

        /* Pass 1: dedicated AUDIO layers always win — they are the primary audio track. */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.visible && layer.layerType == CatalystLayer.LayerType.AUDIO
                && frame >= layer.startFrame && frame < layer.startFrame + layer.duration
                && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
            {
                return layer;
            }
        }

        /* Pass 2: VIDEO layers provide audio only if no dedicated AUDIO layer is active and video volume > 0. */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.visible && layer.layerType == CatalystLayer.LayerType.VIDEO
                && frame >= layer.startFrame && frame < layer.startFrame + layer.duration
                && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty()
                && layer.volume > 0)
            {
                return layer;
            }
        }

        return null;
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
        CatalystLayer activeMedia = this.getActiveAudioLayer(comp, this.currentFrame);

        /* 1. If an active audio/video stream with audio is running, follow OpenAL hardware clock */
        if (activeMedia != null)
        {
            this.ensureAudioPlaying(activeMedia, comp, false);

            if (this.activeAudioPlayer != null && this.activeAudioPlayer.isPlaying())
            {
                float audioSec = this.activeAudioPlayer.getPlaybackPosition();
                int mediaRelFrame = (int) Math.round(audioSec * fps) - activeMedia.audioOffset;
                int syncedFrame = (activeMedia.startFrame - activeMedia.mediaOffset) + mediaRelFrame;

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
                    activeMedia = this.getActiveAudioLayer(comp, this.currentFrame);
                    if (activeMedia != null)
                    {
                        this.ensureAudioPlaying(activeMedia, comp, true);
                    }
                }

                comp.playhead = this.currentFrame;
                this.lastTickMs = System.currentTimeMillis();
                return;
            }
        }
        else
        {
            /* No audio layer currently active at this frame */
            if (this.activeAudioPlayer != null && this.activeAudioPlayer.isPlaying())
            {
                this.activeAudioPlayer.pause();
            }
        }

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

            CatalystLayer nextAudio = this.getActiveAudioLayer(comp, this.currentFrame);
            if (nextAudio != null)
            {
                this.ensureAudioPlaying(nextAudio, comp, true);
            }
        }
    }

    private void ensureAudioPlaying(CatalystLayer layer, CatalystComposition comp, boolean forceSeek)
    {
        if (layer == null || comp == null || layer.resourcePath == null || layer.resourcePath.trim().isEmpty())
        {
            return;
        }

        try
        {
            Link audioLink = Link.create(layer.resourcePath.trim());
            int fps = comp.fps > 0 ? comp.fps : 60;
            int relativeFrame = (this.currentFrame - layer.startFrame) + layer.mediaOffset;
            float relSec = (float) (relativeFrame + layer.audioOffset) / fps;

            if (relSec < 0)
            {
                if (this.activeAudioPlayer != null && this.activeAudioPlayer.isPlaying())
                {
                    this.activeAudioPlayer.pause();
                }
                return;
            }

            if (this.activeAudioPlayer != null)
            {
                if (audioLink.equals(this.lastPlayedAudioLink) && layer == this.lastPlayedAudioLayer)
                {
                    float vol = layer.volume * (layer.opacity / 100.0F);
                    this.activeAudioPlayer.setVolume(vol);

                    if (forceSeek || !this.activeAudioPlayer.isPlaying() || Math.abs(this.activeAudioPlayer.getPlaybackPosition() - relSec) > 0.5F)
                    {
                        this.activeAudioPlayer.setPlaybackPosition(relSec);
                    }
                    if (!this.activeAudioPlayer.isPlaying())
                    {
                        this.activeAudioPlayer.play();
                    }
                    return;
                }
                else
                {
                    this.activeAudioPlayer.stop();
                    this.activeAudioPlayer.delete();
                    this.activeAudioPlayer = null;
                    this.lastPlayedAudioLink = null;
                    this.lastPlayedAudioLayer = null;
                }
            }

            SoundBuffer buffer = BBSModClient.getSounds().get(audioLink, false);
            if (buffer != null)
            {
                this.activeAudioPlayer = new SoundPlayer(buffer);
                /* 2D stereo: source relative to listener at origin — no 3D distance attenuation,
                 * preserves left/right panning of a stereo (AL_FORMAT_STEREO16) buffer. */
                this.activeAudioPlayer.setRelative(true);
                this.activeAudioPlayer.setPosition(0F, 0F, 0F);
                this.activeAudioPlayer.setPlaybackPosition(relSec);
                this.activeAudioPlayer.setVolume(layer.volume * (layer.opacity / 100.0F));
                this.activeAudioPlayer.play();
                this.lastPlayedAudioLink = audioLink;
                this.lastPlayedAudioLayer = layer;
            }
        }
        catch (Exception ignored) {}
    }

    /** Scrubbing (manual timeline slide) seeks audio and video instantly */
    public void seekToFrame(int frame)
    {
        CatalystComposition comp = this.activeProject != null ? this.activeProject.getActiveComposition() : null;
        if (comp == null) return;

        this.currentFrame = Math.max(0, Math.min(frame, comp.duration - 1));
        comp.playhead = this.currentFrame;

        CatalystLayer mediaLayer = this.getActiveAudioLayer(comp, this.currentFrame);
        if (mediaLayer != null)
        {
            try
            {
                Link audioLink = Link.create(mediaLayer.resourcePath.trim());
                int fps = comp.fps > 0 ? comp.fps : 60;
                int relativeFrame = (this.currentFrame - mediaLayer.startFrame) + mediaLayer.mediaOffset;
                float relSec = (float) (relativeFrame + mediaLayer.audioOffset) / fps;

                if (relSec >= 0)
                {
                    if (this.activeAudioPlayer == null || !audioLink.equals(this.lastPlayedAudioLink) || mediaLayer != this.lastPlayedAudioLayer)
                    {
                        if (this.activeAudioPlayer != null)
                        {
                            this.activeAudioPlayer.stop();
                            this.activeAudioPlayer.delete();
                            this.activeAudioPlayer = null;
                        }
                        SoundBuffer buffer = BBSModClient.getSounds().get(audioLink, false);
                        if (buffer != null)
                        {
                            this.activeAudioPlayer = new SoundPlayer(buffer);
                            this.activeAudioPlayer.setRelative(true);
                            this.activeAudioPlayer.setPosition(0F, 0F, 0F);
                            this.lastPlayedAudioLink = audioLink;
                            this.lastPlayedAudioLayer = mediaLayer;
                        }
                    }

                    if (this.activeAudioPlayer != null)
                    {
                        this.activeAudioPlayer.setPlaybackPosition(relSec);
                        this.activeAudioPlayer.setVolume(mediaLayer.volume * (mediaLayer.opacity / 100.0F));
                        if (this.isPlaying && !this.activeAudioPlayer.isPlaying())
                        {
                            this.activeAudioPlayer.play();
                        }
                    }
                }
            }
            catch (Exception ignored) {}
        }
        else
        {
            if (this.activeAudioPlayer != null && this.activeAudioPlayer.isPlaying())
            {
                this.activeAudioPlayer.pause();
            }
        }
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
        this.editorActions.relative(this.topBar).x(1F, -74).y(2).w(70).h(20);

        this.playPauseButton = new UIIcon(() -> this.isPlaying ? Icons.PAUSE : Icons.PLAY, (b) -> this.togglePlayback());
        this.playPauseButton.tooltip(IKey.raw("Play / Pause (Space)"));

        this.settingsButton = new UIIcon(Icons.GEAR, (b) -> this.openCompositionSettingsModal());
        this.settingsButton.tooltip(IKey.raw("Composition Settings"));

        this.fullscreenButton = new UIIcon(Icons.FULLSCREEN, (b) -> this.toggleFullscreen());
        this.fullscreenButton.tooltip(IKey.raw("Maximize Viewport"));

        this.playPauseButton.relative(this.editorActions).x(0).w(20).h(20);
        this.settingsButton.relative(this.editorActions).x(22).w(20).h(20);
        this.fullscreenButton.relative(this.editorActions).x(44).w(20).h(20);

        this.editorActions.add(this.playPauseButton,
                               this.settingsButton, this.fullscreenButton);

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
            this.previewArea.relative(this).y(24).w(1F).h(1F, -24);
        }
        else
        {
            this.compTabStrip.setVisible(true);
            this.bottomArea.setVisible(true);
            this.previewArea.relative(this).y(24).w(1F).h(0.53F, -46);
        }

        this.resize();
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
            CatalystProjectManager.deleteProject(this.activeProject);
            this.activeProject = null;
            this.clearForm();
            this.refreshProjects();
        }
    }

    private void clearForm()
    {
        this.activeProject = null;
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
        this.setupPreviewArea();
        this.setupCompTabStrip();
        this.setupBottomArea();
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

                        float[] sdims = getLayerDimensions(sel, compW, compH);
                        float selW = sdims[0];
                        float selH = sdims[1];
                        float bx = (compW / 2.0F + sel.posX) - selW * sel.anchorX;
                        float by = (compH / 2.0F + sel.posY) - selH * sel.anchorY;
                        float pivotX = bx + selW * sel.anchorX;
                        float pivotY = by + selH * sel.anchorY;

                        /* Check corner handles first (resize) */
                        int handleS = Math.max(6, Math.round(12 / scale));
                        int[][] corners = new int[][] {
                            {(int) bx, (int) by},
                            {(int) (bx + selW), (int) by},
                            {(int) (bx + selW), (int) (by + selH)},
                            {(int) bx, (int) (by + selH)}
                        };

                        double rad = Math.toRadians(sel.rotation);
                        double cos = Math.cos(rad);
                        double sin = Math.sin(rad);

                        for (int[] pt : corners)
                        {
                            double rptX = pivotX + (pt[0] - pivotX) * cos - (pt[1] - pivotY) * sin;
                            double rptY = pivotY + (pt[0] - pivotX) * sin + (pt[1] - pivotY) * cos;
                            if (Math.abs(vMouseX - rptX) <= handleS && Math.abs(vMouseY - rptY) <= handleS)
                            {
                                viewportDragMode = 2; // Resize
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
                        if (unRotX >= bx && unRotX <= bx + selW && unRotY >= by && unRotY <= by + selH)
                        {
                            viewportDragMode = 1; // Move
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
                    viewportDragMode = 0;
                    saveAndRefresh();
                    updateInspectorForm();
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
                            if (Window.isShiftPressed())
                            {
                                float factorX = 1.0F + vDx / (compW * 0.25F);
                                float factorY = 1.0F + vDy / (compH * 0.25F);
                                sel.scaleX = Math.max(0.05F, initialLayerScaleX * factorX);
                                sel.scaleY = Math.max(0.05F, initialLayerScaleY * factorY);
                            }
                            else
                            {
                                float factor = 1.0F + (vDx + vDy) / ((compW + compH) * 0.25F);
                                sel.scaleX = Math.max(0.05F, initialLayerScaleX * factor);
                                sel.scaleY = Math.max(0.05F, initialLayerScaleY * factor);
                            }
                        }
                    }
                }
                super.render(context);
                CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                tickPlayback(comp);
                renderPreviewCanvas(context, this.area, comp);
            }
        };
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

    public float[] getLayerDimensions(CatalystLayer layer, int compW, int compH)
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
                    Link link = Link.create(layer.resourcePath.trim());
                    Texture texture = BBSModClient.getTextures().getTexture(link);
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

        float w = (layer != null) ? baseW * Math.max(0.01F, layer.scaleX) : baseW;
        float h = (layer != null) ? baseH * Math.max(0.01F, layer.scaleY) : baseH;
        return new float[] {w, h};
    }

    private void renderPreviewCanvas(UIContext context, Area area, CatalystComposition comp)
    {
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.deepSurface());

        int margin = 16;
        int maxW   = area.w - margin * 2;
        int maxH   = area.h - margin * 2;

        if (maxW <= 0 || maxH <= 0) return;

        int compW     = (comp != null && comp.width > 0) ? comp.width : 1920;
        int compH     = (comp != null && comp.height > 0) ? comp.height : 1080;
        int targetFps = (comp != null && comp.fps > 0) ? comp.fps : 60;
        int totalDur  = (comp != null) ? comp.duration : 300;
        int playhead  = this.currentFrame;

        float scale = Math.min((float) maxW / compW, (float) maxH / compH);
        if (scale <= 0.0001F) scale = 1.0F;
        float offsetX = area.x + (area.w - compW * scale) / 2.0F;
        float offsetY = area.y + (area.h - compH * scale) / 2.0F;

        MatrixStack viewStack = context.batcher.getContext().getMatrices();
        viewStack.push();
        viewStack.translate(offsetX, offsetY, 0);
        viewStack.scale(scale, scale, 1.0F);

        /* Canvas surface in virtual composition space */
        context.batcher.box(0, 0, compW, compH, 0xFF0D0D11);

        CatalystLayer selectedLayer = this.getSelectedLayer();

        /* Render Active Layers (Back to Front: reverse order of comp.layers) */
        long compStartNano = System.nanoTime();

        if (comp != null && !comp.layers.isEmpty())
        {
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


            for (int i = comp.layers.size() - 1; i >= 0; i--)
            {
                CatalystLayer layer = comp.layers.get(i);
                if (!layer.visible) continue;
                if (playhead < layer.startFrame || playhead >= layer.startFrame + layer.duration) continue;

                float alpha = Math.max(0F, Math.min(1F, layer.opacity / 100.0F));
                if (alpha <= 0.001F) continue;

                long layerStartNano = System.nanoTime();

                int renderColor = Colors.setA(layer.color, alpha);

                /* Calculate Layer Transform Rect with dynamic aspect ratio in virtual space */
                float[] dims = this.getLayerDimensions(layer, compW, compH);
                float layerW = dims[0];
                float layerH = dims[1];
                float lx = (compW / 2.0F + layer.posX) - layerW * layer.anchorX;
                float ly = (compH / 2.0F + layer.posY) - layerH * layer.anchorY;

                /* Viewport Culling: Skip layers completely outside virtual canvas (with rotation margin) */
                float rotMargin = Math.max(layerW, layerH) * 0.75F;
                if (layer.layerType != CatalystLayer.LayerType.AUDIO &&
                    (lx + layerW + rotMargin < 0 || lx - rotMargin > compW || ly + layerH + rotMargin < 0 || ly - rotMargin > compH))
                {
                    layer.lastRenderMs = 0;
                    continue;
                }

                MatrixStack stack = context.batcher.getContext().getMatrices();
                stack.push();
                float px = lx + layerW * layer.anchorX;
                float py = ly + layerH * layer.anchorY;
                stack.translate(px, py, 0);
                if (layer.rotation != 0)
                {
                    stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(layer.rotation));
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
                            Link link = Link.create(layer.resourcePath.trim());
                            Texture texture = BBSModClient.getTextures().getTexture(link);
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

                                /* ── 1. Tick conversion: comp frames → BBS 20-TPS ticks ── */
                                int relFrame = (playhead - layer.startFrame) + layer.mediaOffset;
                                float filmTickF = (relFrame / (float) targetFps) * 20.0f;
                                int filmTick = Math.max(0, (int) filmTickF);
                                int filmDuration = film.calculateDuration();
                                if (filmDuration > 0) filmTick = Math.min(filmTick, filmDuration - 1);

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

                                /* ── 3. Update camera tick so next world render uses correct position ── */
                                this.sceneCamera.setTick(filmTick);

                                /* ── 4. Freeze replay entities at this tick (actors, props) ── */
                                BBSModClient.getFilms().freeze(film, filmTick, false);

                                /* ── 5. Ensure the off-screen FBO is active ── */
                                BBSRendering.setCustomSize(true);

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

                        List<String> renderedLines = new java.util.ArrayList<>();
                        String[] rawLines = text.split("\n");
                        int maxBoxW = (int) (layerW / Math.max(0.01F, fontScale));

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
                        textStack.scale(fontScale, fontScale, 1.0F);

                        float baseLayerW = layerW / Math.max(0.01F, fontScale);
                        float baseLayerH = layerH / Math.max(0.01F, fontScale);
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

        this.lastCompRenderMs = (System.nanoTime() - compStartNano) / 1_000_000.0;

        context.batcher.outline(0, 0, compW, compH, 0xFF2A2A38, 1);

        /* Rule of Thirds grid */
        int thirdW = compW / 3;
        int thirdH = compH / 3;
        context.batcher.box(thirdW,     0, thirdW + 1,     compH, 0x1AFFFFFF);
        context.batcher.box(thirdW * 2, 0, thirdW * 2 + 1, compH, 0x1AFFFFFF);
        context.batcher.box(0, thirdH,     compW, thirdH + 1,     0x1AFFFFFF);
        context.batcher.box(0, thirdH * 2, compW, thirdH * 2 + 1, 0x1AFFFFFF);

        /* ── Bounding Box & Handles for Selected Layer ── */
        if (selectedLayer != null && selectedLayer.visible)
        {
            float[] sdims = this.getLayerDimensions(selectedLayer, compW, compH);
            float selW = sdims[0];
            float selH = sdims[1];
            float bx = (compW / 2.0F + selectedLayer.posX) - selW * selectedLayer.anchorX;
            float by = (compH / 2.0F + selectedLayer.posY) - selH * selectedLayer.anchorY;

            MatrixStack bstack = context.batcher.getContext().getMatrices();
            bstack.push();
            float bpx = bx + selW * selectedLayer.anchorX;
            float bpy = by + selH * selectedLayer.anchorY;
            bstack.translate(bpx, bpy, 0);
            if (selectedLayer.rotation != 0)
            {
                bstack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selectedLayer.rotation));
            }
            bstack.translate(-bpx, -bpy, 0);

            /* White outline */
            context.batcher.outline((int) bx, (int) by, (int) (bx + selW), (int) (by + selH), Colors.WHITE, 1);

            /* 4 Corner handles */
            int handleS = Math.max(4, Math.round(8 / scale));
            int halfH = handleS / 2;
            int hCol = Colors.WHITE;
            int hBorder = 0xFF000000;

            int[][] corners = new int[][] {
                {(int) bx, (int) by},
                {(int) (bx + selW), (int) by},
                {(int) (bx + selW), (int) (by + selH)},
                {(int) bx, (int) (by + selH)}
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
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 0 && this.area.isInside(context))
                {
                    CatalystComposition comp = (activeProject != null) ? activeProject.getActiveComposition() : null;
                    if (comp != null && !comp.layers.isEmpty())
                    {
                        int rowH = 24;
                        int scroll = (int) this.scroll.getScroll();
                        int clickedIndex = (context.mouseY - this.area.y + scroll) / rowH;
                        if (clickedIndex >= 0 && clickedIndex < comp.layers.size())
                        {
                            CatalystLayer layer = comp.layers.get(clickedIndex);
                            if (catalystTimeline != null)
                            {
                                catalystTimeline.setSelected(layer);
                                updateInspectorForm();
                            }
                            return true;
                        }
                    }
                }
                return super.subMouseClicked(context);
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

            int rowH = 24;
            int scroll = this.layersList.scroll != null ? (int) this.layersList.scroll.getScroll() : 0;

            for (int i = 0; i < comp.layers.size(); i++)
            {
                CatalystLayer layer = comp.layers.get(i);
                int ry = a.y + i * rowH - scroll;
                if (ry + rowH < a.y || ry > a.ey()) continue;

                boolean isSelected = this.catalystTimeline != null && this.catalystTimeline.isSelected(layer);
                int bg = isSelected ? 0xFF2A2E3B : 0xFF1C1C22;

                context.batcher.box(a.x + 2, ry + 1, a.ex() - 2, ry + rowH - 1, bg);
                context.batcher.box(a.x + 2, ry + 1, a.x + 6,    ry + rowH - 1, layer.color);

                if (isSelected)
                {
                    context.batcher.outline(a.x + 2, ry + 1, a.ex() - 2, ry + rowH - 1, Colors.WHITE, 1);
                }

                /* Visibility & lock icons (text stand-in) */
                String vis = layer.visible ? "\u25CF" : "\u25CB";
                context.batcher.text(vis,       a.x + 8,  ry + 7, layer.visible ? layer.color : Colors.GRAY, false);
                context.batcher.text(layer.name, a.x + 20, ry + 7, isSelected ? Colors.WHITE : (layer.visible ? Colors.LIGHTEST_GRAY : Colors.GRAY), false);

                /* Type badge */
                String badge = layer.layerType != null ? layer.layerType.getBadge() : "";
                int bw = context.batcher.getFont().getWidth(badge);
                context.batcher.text(badge, a.ex() - bw - 6, ry + 7, Colors.GRAY, false);

                /* Profiling: Render time (ms) */
                if (layer.lastRenderMs > 0.01)
                {
                    String msStr = String.format(java.util.Locale.ROOT, "%.1f ms", layer.lastRenderMs);
                    int mw = context.batcher.getFont().getWidth(msStr);
                    context.batcher.text(msStr, a.ex() - bw - mw - 12, ry + 7, 0x88999999, false);
                }

                context.batcher.box(a.x + 2, ry + rowH - 1, a.ex() - 2, ry + rowH, 0xFF2A2A30);
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

        /* Resource picker row */
        this.layerResourceLabel = new UILabel(IKey.raw("Resource / File:"), Colors.LIGHTEST_GRAY);
        this.layerResourceLabel.h(14);

        this.layerResourceInput = new UITextbox(160, (text) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.resourcePath = text.trim();
                this.saveAndRefresh();
            }
        });
        this.layerResourceInput.h(20);

        this.layerPickResourceBtn = new UIButton(IKey.raw("Browse..."), (b) -> this.openMediaPickerForSelectedLayer());
        this.layerPickResourceBtn.h(20);

        this.layerOpenFolderBtn = new UIIcon(Icons.FOLDER, (b) -> this.openMediaFolderForSelectedLayer());
        this.layerOpenFolderBtn.tooltip(IKey.raw("Open Media Folder"));
        this.layerOpenFolderBtn.wh(20, 20);

        this.layerResourceRow = UI.row(this.layerPickResourceBtn, this.layerOpenFolderBtn);
        this.layerResourceRow.h(20);

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
                this.saveAndRefresh();
            }
        });
        this.layerScaleX.limit(0.01, 20.0).h(20);
        this.layerScaleX.setValue(1.0);

        this.layerScaleY = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.scaleY = (float) (double) v;
                this.saveAndRefresh();
            }
        });
        this.layerScaleY.limit(0.01, 20.0).h(20);
        this.layerScaleY.setValue(1.0);

        this.layerRotation = new UITrackpad((v) ->
        {
            CatalystLayer sel = this.getSelectedLayer();
            if (sel != null)
            {
                sel.rotation = (float) (double) v;
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

        this.mediaGroup = UI.column(
            4,
            UI.label(IKey.raw("Volume (0.0 - 1.0):")).h(14),
            this.layerVolumeInput,
            UI.label(IKey.raw("Audio Offset (Frames):")).h(14),
            this.layerAudioOffsetInput,
            this.layerExtendDurationBtn
        );

        this.inspectorForm = UI.column(
            4,
            this.layerTypeLabel,
            UI.label(IKey.raw("Layer Name:")).h(14),
            this.layerNameInput,
            this.textControlsGroup,
            this.layerResourceLabel,
            this.layerResourceRow,
            this.layerResourceInput,
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

    private void openMediaPickerForSelectedLayer()
    {
        CatalystLayer sel = this.getSelectedLayer();
        if (sel == null) return;

        if (sel.layerType == CatalystLayer.LayerType.IMAGE)
        {
            Link currentLink = (sel.resourcePath != null && !sel.resourcePath.trim().isEmpty()) ? Link.create(sel.resourcePath.trim()) : null;
            UITexturePicker.open(this.getContext(), currentLink, (link) ->
            {
                if (link != null)
                {
                    this.pushUndo();
                    sel.resourcePath = link.toString();
                    this.saveAndRefresh();
                    this.updateInspectorForm();
                }
            });
        }
        else if (sel.layerType == CatalystLayer.LayerType.VIDEO)
        {
            Link currentLink = (sel.resourcePath != null && !sel.resourcePath.trim().isEmpty()) ? Link.create(sel.resourcePath.trim()) : null;
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(
                IKey.raw("Pick Video"),
                UIVideoClip.getVideoLinks(),
                (link) ->
                {
                    if (link != null)
                    {
                        this.pushUndo();
                        sel.resourcePath = link.toString();
                        try
                        {
                            VideoPlayer player = BBSModClient.getVideos().getPlayer(sel, link);
                            if (player != null)
                            {
                                player.ensureProbed();
                                float durSec = player.getDuration();
                                if (durSec > 0)
                                {
                                    CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                                    int fps = comp != null && comp.fps > 0 ? comp.fps : 60;
                                    sel.mediaDuration = (int) Math.round(durSec * fps);
                                    sel.duration = sel.mediaDuration;
                                }
                            }
                        }
                        catch (Exception ignored) {}
                        this.saveAndRefresh();
                        this.updateInspectorForm();
                    }
                }
            );
            UIOverlay.addOverlay(this.getContext(), panel.set(currentLink));
        }
        else if (sel.layerType == CatalystLayer.LayerType.AUDIO)
        {
            Link currentLink = (sel.resourcePath != null && !sel.resourcePath.trim().isEmpty()) ? Link.create(sel.resourcePath.trim()) : null;
            UISoundOverlayPanel panel = new UISoundOverlayPanel(
                (link) ->
                {
                    if (link != null)
                    {
                        this.pushUndo();
                        sel.resourcePath = link.toString();
                        try
                        {
                            SoundBuffer buffer = BBSModClient.getSounds().get(link, true);
                            if (buffer != null)
                            {
                                float durSec = buffer.getDuration();
                                if (durSec > 0)
                                {
                                    CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                                    int fps = comp != null && comp.fps > 0 ? comp.fps : 60;
                                    sel.mediaDuration = (int) Math.round(durSec * fps);
                                    sel.duration = sel.mediaDuration;
                                }
                            }
                        }
                        catch (Exception ignored) {}
                        this.saveAndRefresh();
                        this.updateInspectorForm();
                    }
                },
                this.getContext()
            );
            UIOverlay.addOverlay(this.getContext(), panel.set(currentLink));
        }
        else if (sel.layerType == CatalystLayer.LayerType.SCENE)
        {
            List<String> films = new java.util.ArrayList<>(BBSMod.getFilms().getKeys());
            UIStringOverlayPanel panel = new UIStringOverlayPanel(
                IKey.raw("Pick Film"),
                films,
                (filmId) ->
                {
                    if (filmId != null && !filmId.trim().isEmpty())
                    {
                        this.pushUndo();
                        sel.resourcePath = filmId.trim();
                        try
                        {
                            Film film = BBSMod.getFilms().load(filmId);
                            if (film != null)
                            {
                                int dur = film.camera.calculateDuration();
                                if (dur > 0)
                                {
                                    sel.duration = dur;
                                    sel.mediaDuration = dur;
                                }
                            }
                        }
                        catch (Exception ignored) {}
                        this.saveAndRefresh();
                        this.updateInspectorForm();
                    }
                }
            );
            UIOverlay.addOverlay(this.getContext(), panel.set(sel.resourcePath));
        }
    }

    private void openMediaFolderForSelectedLayer()
    {
        CatalystLayer sel = this.getSelectedLayer();
        if (sel == null) return;

        if (sel.layerType == CatalystLayer.LayerType.AUDIO)
        {
            File folder = BBSMod.getAudioFolder();
            folder.mkdirs();
            UIUtils.openFolder(folder);
        }
        else if (sel.layerType == CatalystLayer.LayerType.VIDEO)
        {
            File folder = new File(BBSMod.getAssetsFolder(), "video");
            folder.mkdirs();
            UIUtils.openFolder(folder);
        }
        else if (sel.layerType == CatalystLayer.LayerType.IMAGE)
        {
            File folder = new File(BBSMod.getAssetsFolder(), "textures");
            folder.mkdirs();
            UIUtils.openFolder(folder);
        }
        else if (sel.layerType == CatalystLayer.LayerType.SCENE)
        {
            File folder = BBSMod.getFilms().getFolder();
            folder.mkdirs();
            UIUtils.openFolder(folder);
        }
        else
        {
            UIUtils.openFolder(CatalystProjectManager.getProjectsFolder());
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
            this.layerResourceRow.setVisible(hasMedia);
            this.layerResourceInput.setVisible(hasMedia);
            if (hasMedia)
            {
                this.layerResourceInput.setText(sel.resourcePath != null ? sel.resourcePath : "");
            }

            boolean hasAudioSettings = sel.layerType == CatalystLayer.LayerType.AUDIO || sel.layerType == CatalystLayer.LayerType.VIDEO;
            boolean hasMediaDuration = sel.mediaDuration > 0;
            boolean showMediaGroup = hasAudioSettings || hasMediaDuration;

            this.mediaGroup.setVisible(showMediaGroup);
            if (showMediaGroup)
            {
                this.layerVolumeInput.setVisible(hasAudioSettings);
                this.layerAudioOffsetInput.setVisible(hasAudioSettings);
                if (hasAudioSettings)
                {
                    this.layerVolumeInput.setValue(sel.volume);
                    this.layerAudioOffsetInput.setValue(sel.audioOffset);
                }
                this.layerExtendDurationBtn.setVisible(hasMediaDuration);
                if (hasMediaDuration)
                {
                    this.layerExtendDurationBtn.label = IKey.raw("Extend to Media Length (" + sel.mediaDuration + " f)");
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
        }
        return super.subKeyPressed(context);
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
        if (this.activeAudioPlayer != null)
        {
            try
            {
                this.activeAudioPlayer.stop();
                this.activeAudioPlayer.delete();
            }
            catch (Exception ignored) {}
            this.activeAudioPlayer = null;
            this.lastPlayedAudioLink = null;
        }
        BBSModClient.getSounds().stopOwned(this);
    }
}
