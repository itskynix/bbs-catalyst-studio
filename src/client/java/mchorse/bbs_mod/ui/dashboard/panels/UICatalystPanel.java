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
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystCompTab;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystHubScreen;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystProjectList;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTab;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UIMediaPoolPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
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
import mchorse.bbs_mod.utils.Direction;
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
import mchorse.bbs_mod.utils.PNGEncoder;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import mchorse.bbs_mod.utils.UnsafeUtils;
import sun.misc.Unsafe;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
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
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystAiTools;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystDock;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UIAudioGainOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystLayouts;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystMedia;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UINleInspector;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UINleLibrary;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UINleTimeline;
import mchorse.bbs_mod.ui.dashboard.panels.catalyst.UINleToolbar;

public class UICatalystPanel extends UIDashboardPanel implements mchorse.bbs_mod.ui.dashboard.menu.IGlobalMenuProvider
{
    @Override
    public boolean hasEditMenu()
    {
        return true;
    }

    @Override
    public void fillEditMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        menu.action(Icons.UNDO, UIKeys.MENU_EDIT_UNDO, this::undo).key(org.lwjgl.glfw.GLFW.GLFW_KEY_Z, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
        menu.action(Icons.REDO, UIKeys.MENU_EDIT_REDO, this::redo).key(org.lwjgl.glfw.GLFW.GLFW_KEY_Y, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL);
    }

    /** The Window and Layout menu is the edit page's own while it is up: panels, templates, reset and lock */
    @Override
    public boolean hasWindowMenu()
    {
        return this.currentTab == CatalystTab.EDITOR && this.activeProject != null && this.catalystDock != null;
    }

    @Override
    public void fillWindowMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        this.catalystDock.fillWindowMenu(menu);
    }

    /** The menu bar's tools menu is the smart tools while the edit page is up */
    @Override
    public boolean hasToolsMenu()
    {
        return this.currentTab == CatalystTab.EDITOR && this.activeProject != null;
    }

    @Override
    public mchorse.bbs_mod.l10n.keys.IKey getToolsMenuTitle()
    {
        return UIKeys.CATALYST_NLE_AI_TOOLS;
    }

    @Override
    public void fillToolsMenu(mchorse.bbs_mod.ui.utils.context.ContextMenuManager menu)
    {
        menu.action(Icons.CUT, UIKeys.CATALYST_NLE_AI_AUTO_CUT, this.aiTools::autoCut);
        menu.action(Icons.SHIFT_BACKWARD, UIKeys.CATALYST_NLE_AI_J_CUT, this.aiTools::jCut);
        menu.action(Icons.SHIFT_FORWARD, UIKeys.CATALYST_NLE_AI_L_CUT, this.aiTools::lCut);
        menu.action(Icons.BOOKMARK, UIKeys.CATALYST_NLE_AI_BEAT_SYNC, this.aiTools::beatSync);
        menu.action(Icons.SOUND, UIKeys.CATALYST_NLE_AI_DUCKING, this.aiTools::ducking);
        menu.action(Icons.FILM, UIKeys.CATALYST_NLE_AI_SCENE_CUT, this.aiTools::sceneCuts);
        menu.action(Icons.SPARKLES, UIKeys.CATALYST_NLE_AI_CLOSE_GAPS, this.aiTools::closeAllGaps);
    }

    public boolean isAiBusy()
    {
        return this.aiBusy;
    }

    /** A tool starts: the timeline is locked and the playhead will sweep from one frame to the other as the work goes */
    public void beginAi(mchorse.bbs_mod.l10n.keys.IKey name, int from, int to)
    {
        this.isPlaying = false;
        this.stopAllAudio();
        this.aiName = name;
        this.aiFrom = Math.max(0, from);
        this.aiTo = Math.max(this.aiFrom + 1, to);
        this.aiProgress = 0D;
        this.aiCancelled = false;
        this.aiBusy = true;
    }

    public boolean isAiCancelled()
    {
        return this.aiCancelled;
    }

    /** Stops the tool: its reading ends, and nothing it found is put on the timeline */
    public void cancelAi()
    {
        this.aiCancelled = true;
    }

    public void setAiProgress(double progress)
    {
        this.aiProgress = Math.max(0D, Math.min(1D, progress));
    }

    /** The tool is done: the timeline is free again and the playhead is back at the start */
    public void endAi()
    {
        CatalystComposition comp = this.getActiveComposition();

        this.aiBusy = false;
        this.currentFrame = 0;

        if (comp != null)
        {
            comp.playhead = 0;
        }
    }

    /** A glass badge over the monitor: the name of the tool and its progress */
    private void renderAiBadge(UIContext context, Area area)
    {
        if (!this.aiBusy)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        String title = "AI " + this.aiName.get();
        String percent = Math.round(this.aiProgress * 100D) + "%";
        int w = Math.min(Math.max(120, area.w - 24), 300);
        int x = area.mx() - w / 2;
        int y = area.y + 14;
        int accent = Colors.A100 | BBSSettings.primaryColor.get();

        RoundedDraw.fill(context.batcher, x, y, x + w, y + 38, 8, RoundedDraw.ALL, 0xd911161f);
        RoundedDraw.outline(context.batcher, x, y, x + w, y + 38, 8, RoundedDraw.ALL, 0x40ffffff);
        /* The stop button at the right corner */
        this.aiStop[0] = x + w - 26;
        this.aiStop[1] = y + 4;
        this.aiStop[2] = x + w - 8;
        this.aiStop[3] = y + 20;

        boolean stopHover = context.mouseX >= this.aiStop[0] && context.mouseX < this.aiStop[2] && context.mouseY >= this.aiStop[1] && context.mouseY < this.aiStop[3];

        RoundedDraw.fill(context.batcher, this.aiStop[0], this.aiStop[1], this.aiStop[2], this.aiStop[3], 4, RoundedDraw.ALL, stopHover ? 0xccd9302c : 0x33ffffff);
        context.batcher.icon(Icons.CLOSE, 0xffffffff, this.aiStop[0] + 1, this.aiStop[1], 1F, 0F);

        context.batcher.text(font.limitToWidth(title, w - 90), x + 12, y + 8, 0xffe8edf5, false);
        context.batcher.text(percent, x + w - 34 - font.getWidth(percent), y + 8, 0xffbfe0ff, false);
        context.batcher.box(x + 12, y + 24, x + w - 12, y + 28, 0x33ffffff);
        context.batcher.box(x + 12, y + 24, x + 12 + (int) Math.round((w - 24) * this.aiProgress), y + 28, accent);
    }

    /** Work for the render thread, from a thread that read a file */
    public void runLater(Runnable task)
    {
        this.mainQueue.add(task);
    }

    /** The tools are done with their changes: the length follows the clips, the project is saved */
    public void toolsDone()
    {
        this.ensureDuration();
        this.saveAndRefresh();
        this.updateInspectorForm();
    }

    /** G: the gain window for the picked sound clips */
    public void openGainDialog()
    {
        List<CatalystLayer> sounds = new ArrayList<>();

        for (CatalystLayer layer : this.catalystTimeline.getSelectedLayers())
        {
            if (layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.NESTED_AUDIO)
            {
                sounds.add(layer);
            }
        }

        if (sounds.isEmpty())
        {
            this.getContext().notifyError(UIKeys.CATALYST_NLE_GAIN_NEED_AUDIO);

            return;
        }

        this.pushUndo();
        UIAudioGainOverlayPanel.open(this.getContext(), this, sounds);
    }

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
    public UIIcon          splitButton;
    public UIIcon          settingsButton;
    public UIIcon          renderExportButton;
    public UIIcon          fullscreenButton;
    public UIMediaPoolPanel mediaPoolPanel;
    public CatalystMediaAsset draggedAsset = null;
    private org.lwjgl.glfw.GLFWDropCallback prevDropCallback = null;

    /* ── View 1: Projects ── */
    public UICatalystHubScreen    hub;
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

    /* ── View 2: Editor (the edit page) ── */
    public UIElement     previewArea;

    /* The composition tab strip of the layer stack days: kept off screen, the monitor's timeline menu replaced it */
    public UIElement     compTabStrip;
    public UIScrollView  compTabScroll;
    public UIIcon        addCompButton;

    /** Library (left), inspector (right, full height), the strip over the timeline, the timeline and the page bar */
    public UINleLibrary  library;
    public UINleInspector inspector;
    public UINleToolbar  nleToolbar;
    public UINleTimeline catalystTimeline;
    public UIElement     monitorBar;
    public UIElement     transportBar;

    /* The dockable edit page: its host, the monitor and the timeline panel (each made of several parts), and the dock */
    public UIElement     editor;
    public UIElement     monitorPanel;
    public UIElement     timelinePanel;
    public CatalystDock  catalystDock;

    /* The keyframe and curve editor under the timeline: whether it is open, and for which clip it was last filled */
    public UIKeyframeEditor keyEditor;
    private boolean      keysOpen = false;
    private CatalystLayer keysShown;
    private int          keysSignature;
    private double       syncZoom = -1D;
    /** Which headings of the curve editor are folded: they start open, and stay as the user left them */
    private final mchorse.bbs_mod.ui.framework.elements.input.items.FoldState<String> keyFolds = new mchorse.bbs_mod.ui.framework.elements.input.items.FoldState<>(true);
    private double       syncShift = -1D;
    private boolean      exportLayout = false;

    /* The smart tools at work: the page shows what is running, the timeline is locked and the playhead sweeps the clips */
    private volatile boolean aiBusy = false;
    private volatile boolean aiCancelled = false;
    /** Where the stop button of the badge was drawn last: x, y, to x, to y */
    private final int[] aiStop = new int[4];
    private volatile double  aiProgress = 0D;
    private mchorse.bbs_mod.l10n.keys.IKey aiName = mchorse.bbs_mod.l10n.keys.IKey.EMPTY;
    private int              aiFrom;
    private int              aiTo;

    /* One player per file for the pieces of a cut: the stream goes on from one piece to the next */
    private static final Map<String, Object> PATH_OWNERS = new HashMap<>();

    /* The smart tools, and the work they hand back to the render thread when a thread of theirs is done reading */
    private final CatalystAiTools aiTools = new CatalystAiTools(this);
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> mainQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** The project and timeline shown in the top bar, as the bar cuts it to fit */
    private String       projectPath = "";

    /* Nested timelines: how deep the monitor is inside them, the opacity they hand down, and the stand-ins for the sounds inside */
    /**
     * What owns the video player of a clip. The pieces a file was cut into all share one player, so the
     * ffmpeg stream that is decoding carries on from piece to piece instead of one being started for each.
     * A clip that overlaps another piece of the same file in time, or that sits in a nested timeline, keeps a player of its own.
     */
    private Object videoOwner(CatalystLayer layer)
    {
        CatalystComposition comp = this.getActiveComposition();

        if (comp == null || layer.resourcePath == null || layer.resourcePath.trim().isEmpty())
        {
            return layer;
        }

        String key = layer.resourcePath.trim();
        boolean found = false;

        for (CatalystLayer other : comp.layers)
        {
            if (other == layer)
            {
                found = true;
            }
            else if (other.layerType == CatalystLayer.LayerType.VIDEO && other.resourcePath != null && key.equals(other.resourcePath.trim())
                && other.startFrame < layer.startFrame + layer.duration && layer.startFrame < other.startFrame + other.duration)
            {
                return layer;
            }
        }

        return found ? PATH_OWNERS.computeIfAbsent(key, (k) -> new Object()) : layer;
    }

    private int nestDepth = 0;
    private float nestAlpha = 1F;
    private final Map<String, CatalystLayer> nestedAudioCache = new HashMap<>();
    private final Map<CatalystLayer, Integer> nestedAudioShift = new HashMap<>();
    private boolean      safeAreasBeforeFullscreen = true;

    /** The program monitor's zoom: 0 fits the frame, otherwise a percentage of its pixels */
    public static int    monitorZoom = 0;
    public boolean       loopPlayback = true;
    public float         masterVolume = 1F;


    /* Saving after edits, a moment after the last one, and one undo step per run of edits to one thing */
    private long         dirtyAt = 0L;
    private String       lastUndoKey = null;
    public UIElement     fullscreenBar;
    private int          lastMouseX = -1;
    private int          lastMouseY = -1;
    private long         lastMouseMoveAt = 0L;
    private float        volumeBeforeMute = 1F;
    /** The fullscreen bar goes away this long after the mouse last moved */
    private static final long FULLSCREEN_BAR_MS = 3000L;
    /** When Ctrl+S last saved, for the badge that says so */
    private long         savedAt = 0L;
    private long         lastUndoAt = 0L;

    public static final int MONITOR_BAR_H = 20;
    public static final int TRANSPORT_H = 24;

    private boolean isUpdatingLayout = false;

    /* ── Preview Quality / Resolution (Stage 84) ── */
    public enum PreviewResolution
    {
        FULL("Full", 1.0F, "1/1"),
        HALF("Half", 0.5F, "1/2"),
        THIRD("Third", 1.0F / 3.0F, "1/3"),
        QUARTER("Quarter", 0.25F, "1/4");

        public final String title;
        public final float scale;
        public final String fraction;

        PreviewResolution(String title, float scale, String fraction)
        {
            this.title = title;
            this.scale = scale;
            this.fraction = fraction;
        }

        public PreviewResolution next()
        {
            PreviewResolution[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    public static PreviewResolution previewResolution = PreviewResolution.FULL;
    public UIButton previewResolutionBtn;
    public static boolean showSafeAreas = true;
    public UIIcon safeAreasToggleBtn;
    private Framebuffer previewFbo = null;
    private Texture previewFboTexture = null;

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
    private File exportTempVideoFile = null;
    private File exportAudioSourceFile = null;
    private File exportLogFile = null;
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
        this.add(this.topBar, this.editor, this.fullscreenBar, this.projectsView);

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
            if (this.activeProject != null && this.currentTab == CatalystTab.EDITOR)
            {
                this.captureProjectThumbnail(this.activeProject);
            }
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
            if (this.previewFbo != null)
            {
                this.previewFbo.delete();
                this.previewFbo = null;
            }
            if (this.previewFboTexture != null)
            {
                this.previewFboTexture.delete();
                this.previewFboTexture = null;
            }
            this.deleteFxBuffers();
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
            if (this.previewFbo != null)
            {
                this.previewFbo.delete();
                this.previewFbo = null;
            }
            if (this.previewFboTexture != null)
            {
                this.previewFboTexture.delete();
                this.previewFboTexture = null;
            }
            this.deleteFxBuffers();
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

        if (this.currentTab == CatalystTab.EDITOR && tab == CatalystTab.PROJECTS && this.activeProject != null)
        {
            this.captureProjectThumbnail(this.activeProject);
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

        /* The edit page's panels live in the dock; its host shows and hides them all together */
        if (this.editor != null)
        {
            this.layoutPreview(this.isFullscreen && isEditor);
            this.editor.setVisible(isEditor && !this.isFullscreen && !this.exportLayout);
        }

        this.editorActions.setVisible(isEditor);
        this.updateTitleLabel();

        if (isEditor)
        {
            this.updateEditorLayout();
        }
    }

    private void updateTitleLabel()
    {
        if (this.activeProject != null)
        {
            CatalystComposition comp = this.activeProject.getActiveComposition();
            String compName = (comp != null) ? " > " + comp.name : "";
            int fps = (comp != null && comp.fps > 0) ? comp.fps : this.activeProject.fps;
            double durS = (comp != null && comp.duration > 0 && fps > 0)
                ? (double) comp.duration / (double) fps
                : this.activeProject.durationSeconds;
            this.projectPath = this.activeProject.name + (comp != null ? " › " + comp.name : "") + " [" + fps + " FPS]";
        }
        else
        {
            this.projectPath = "";
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

            /* Every timeline is as long as its last clip */
            for (CatalystComposition timeline : project.compositions)
            {
                timeline.refreshDuration();
            }

            project.syncMediaPoolWithLayers();
            if (this.mediaPoolPanel != null)
            {
                this.mediaPoolPanel.rebuildList();
            }
            this.currentFrame = 0;

            if (this.nameInput != null) this.nameInput.setText(project.name);
            if (this.fpsInput != null) this.fpsInput.setValue(project.fps);
            if (this.durationSecondsInput != null) this.durationSecondsInput.setValue(project.durationSeconds);
            if (this.widthInput != null) this.widthInput.setValue(project.width);
            if (this.heightInput != null) this.heightInput.setValue(project.height);
            this.updateDurationCalcLabel();

            if (this.openProjectButton != null) this.openProjectButton.label = UIKeys.CATALYST_PROJECTS_OPEN;
            if (this.deleteProjectButton != null) this.deleteProjectButton.setVisible(true);

            this.rebuildCompTabs();
            this.scanMissingMedia(project);
        }

        this.updateTitleLabel();
    }

    /** As a project opens: every file it uses is looked for, and what is missing is put to the user to relink or leave offline */
    private void scanMissingMedia(CatalystProject project)
    {
        mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystMedia.refresh();

        List<String> missing = project.collectMissingMedia();
        UIContext context = this.getContext();

        if (missing.isEmpty() || context == null)
        {
            return;
        }

        UIOverlay.addOverlay(context, new mchorse.bbs_mod.ui.dashboard.panels.catalyst.UIMissingMediaOverlayPanel(project, missing, () ->
        {
            cleanupProjectResources(project);
            CatalystProjectManager.saveProject(project);

            if (this.mediaPoolPanel != null)
            {
                this.mediaPoolPanel.rebuildList();
            }
        }), 580, 320);
    }

    public void refreshProjects()
    {
        if (this.hub != null)
        {
            this.hub.refresh();
        }

        if (this.projectList != null)
        {
            List<CatalystProject> projects = CatalystProjectManager.loadAllProjects();
            this.projectList.setList(projects);

            if (this.activeProject != null)
            {
                this.projectList.setCurrent(this.activeProject);
            }
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
            if (this.isTrackSilent(comp, layer)) continue;
            if (layer.layerType == CatalystLayer.LayerType.VIDEO && layer.muteOwnAudio) continue;
            if (mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystMedia.isOffline(layer)) continue;
            if ((layer.layerType == CatalystLayer.LayerType.AUDIO || layer.layerType == CatalystLayer.LayerType.VIDEO)
                && frame >= layer.startFrame && frame < layer.startFrame + layer.duration
                && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty()
                && (layer.animVolume || layer.computeVolumeWithGain(frame) > 0))
            {
                if (layer.layerType == CatalystLayer.LayerType.VIDEO)
                {
                    Link link = Link.create(layer.resourcePath.trim());
                    VideoPlayer player = BBSModClient.getVideos().get(link);
                    if (player != null)
                    {
                        player.ensureProbed();
                        if (player.isValid() && !player.hasAudio())
                        {
                            continue;
                        }
                    }
                }
                list.add(layer);
            }
        }
        /* The sound of nested timelines: a stand-in clip for each sound inside, placed on this timeline */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.layerType == CatalystLayer.LayerType.NESTED_AUDIO && !layer.muted && !(hasSolo && !layer.solo)
                && !this.isTrackSilent(comp, layer) && frame >= layer.startFrame && frame < layer.startFrame + layer.duration)
            {
                this.collectNestedAudio(layer, layer.startFrame - layer.mediaOffset, layer.startFrame, layer.startFrame + layer.duration, 0, layer.id, layer.computeVolumeWithGain(frame), frame, list);
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
                        float effOpacity = layer.computeEffective(this.currentFrame)[5];
                        float vol = layer.computeVolumeWithGain(this.currentFrame) * (effOpacity / 100.0F) * this.masterVolume;
                        player.setVolume(vol);
                        player.setPan(layer.computeEffectivePan(this.currentFrame));

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
                    float effOpacity = layer.computeEffective(this.currentFrame)[5];
                    newPlayer.setPan(layer.computeEffectivePan(this.currentFrame));
                    newPlayer.setPlaybackPosition(relSec);
                    newPlayer.setVolume(layer.computeVolumeWithGain(this.currentFrame) * (effOpacity / 100.0F) * this.masterVolume);

                    if (this.isPlaying)
                    {
                        newPlayer.play();
                    }
                    this.activeAudioPlayers.put(layer, newPlayer);
                    this.layerAudioLinks.put(layer, audioLink);
                }
            }
            catch (Exception e)
            {
                BBSMod.LOGGER.warn("Error synchronizing audio player for layer " + layer.name, e);
            }
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
        /* The end is where the last clip ends: a stale length must not keep the clock running past it */
        int endFrame = Math.max(1, comp.endFrame() > 0 ? comp.endFrame() : comp.duration);
        this.syncAudioPlayback(comp, false);
        this.prewarmUpcoming(comp, fps);

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

        /* A clock that reads a place outside its own clip is not telling the time of the timeline */
        if (masterLayer != null && masterPlayer != null)
        {
            int probe = (masterLayer.startFrame - masterLayer.mediaOffset) + (int) Math.round(masterPlayer.getPlaybackPosition() * fps) - masterLayer.audioOffset;

            if (probe < masterLayer.startFrame - fps || probe > masterLayer.startFrame + masterLayer.duration + fps)
            {
                masterLayer = null;
                masterPlayer = null;
            }
        }

        if (masterLayer != null && masterPlayer != null)
        {
            this.masterClockPlayer = masterPlayer;
            float audioSec = masterPlayer.getPlaybackPosition();
            int mediaRelFrame = (int) Math.round(audioSec * fps) - masterLayer.audioOffset;
            int syncedFrame = Math.max(0, (masterLayer.startFrame - masterLayer.mediaOffset) + mediaRelFrame);

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

            if (this.currentFrame >= endFrame)
            {
                if (!this.loopPlayback)
                {
                    this.currentFrame = Math.max(0, endFrame - 1);
                    this.isPlaying = false;
                    this.stopAllAudio();
                    comp.playhead = this.currentFrame;

                    return;
                }

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

            if (this.currentFrame >= endFrame)
            {
                if (!this.loopPlayback)
                {
                    this.currentFrame = Math.max(0, endFrame - 1);
                    this.isPlaying = false;
                    this.stopAllAudio();
                    comp.playhead = this.currentFrame;

                    return;
                }

                this.currentFrame = 0; /* loop */
                this.stopAllAudio();
            }

            comp.playhead = this.currentFrame;
            this.syncAudioPlayback(comp, true);
        }
    }

    /* ── Keyframe and curve editor ── */

    /** Opens or closes the keyframe editor over the lower half of the timeline panel */
    public void toggleKeys()
    {
        this.keysOpen = !this.keysOpen;
        this.keyEditor.setVisible(this.keysOpen);
        this.catalystTimeline.relative(this.timelinePanel).x(0).y(UINleToolbar.HEIGHT).w(1F).h(this.keysOpen ? 0.5F : 1F, -UINleToolbar.HEIGHT);
        this.timelinePanel.resize();
        this.keysShown = null;
        this.syncZoom = -1D;
        this.refreshKeyEditor();
    }

    /** Fills the editor with the channels of the picked clip, and of the effects on it, when either has changed */
    private void refreshKeyEditor()
    {
        if (!this.keysOpen || this.keyEditor == null)
        {
            return;
        }

        CatalystLayer layer = this.getSelectedLayer();
        int signature = 0;

        if (layer != null)
        {
            signature = layer.isAudioClip() ? 1 : 0;

            for (mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer effect : layer.effects.getAllTyped())
            {
                signature = signature * 31 + effect.effect.get().hashCode();
            }
        }

        if (layer == this.keysShown && signature == this.keysSignature)
        {
            return;
        }

        this.keysShown = layer;
        this.keysSignature = signature;

        List<KeyframeChannel<?>> channels = new ArrayList<>();
        Map<KeyframeChannel<?>, IKey> titles = new java.util.IdentityHashMap<>();
        Map<KeyframeChannel<?>, UIKeyframeSheet.Section> sections = new java.util.IdentityHashMap<>();

        if (layer != null)
        {
            if (layer.isAudioClip())
            {
                UIKeyframeSheet.Section audio = new UIKeyframeSheet.Section("cat_audio", UIKeys.CATALYST_NLE_KEY_SECTION_AUDIO, Icons.SOUND, 0x2eb67d);

                this.keyTitle(channels, titles, sections, layer.channelVolume, UIKeys.CATALYST_NLE_KEY_VOLUME, audio);
                this.keyTitle(channels, titles, sections, layer.channelPan, UIKeys.CATALYST_NLE_KEY_PAN, audio);
            }
            else
            {
                UIKeyframeSheet.Section transform = new UIKeyframeSheet.Section("cat_transform", UIKeys.CATALYST_NLE_KEY_SECTION_TRANSFORM, Icons.MAXIMIZE, 0x4aa3ff);
                UIKeyframeSheet.Section composite = new UIKeyframeSheet.Section("cat_composite", UIKeys.CATALYST_NLE_KEY_SECTION_COMPOSITE, Icons.VISIBLE, 0xe8c43a);

                this.keyTitle(channels, titles, sections, layer.channelPosX, UIKeys.CATALYST_NLE_KEY_POS_X, transform);
                this.keyTitle(channels, titles, sections, layer.channelPosY, UIKeys.CATALYST_NLE_KEY_POS_Y, transform);
                this.keyTitle(channels, titles, sections, layer.channelScaleX, UIKeys.CATALYST_NLE_KEY_ZOOM_X, transform);
                this.keyTitle(channels, titles, sections, layer.channelScaleY, UIKeys.CATALYST_NLE_KEY_ZOOM_Y, transform);
                this.keyTitle(channels, titles, sections, layer.channelRotation, UIKeys.CATALYST_NLE_KEY_ROTATION, transform);
                this.keyTitle(channels, titles, sections, layer.channelAnchorX, UIKeys.CATALYST_NLE_KEY_ANCHOR_X, transform);
                this.keyTitle(channels, titles, sections, layer.channelAnchorY, UIKeys.CATALYST_NLE_KEY_ANCHOR_Y, transform);
                this.keyTitle(channels, titles, sections, layer.channelOpacity, UIKeys.CATALYST_NLE_KEY_OPACITY, composite);
            }

            int number = 0;

            for (mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer effect : layer.effects.getAllTyped())
            {
                mchorse.bbs_mod.camera.clips.fx.PostEffect definition = mchorse.bbs_mod.camera.clips.fx.PostEffect.get(effect.effect.get());

                if (definition == null)
                {
                    continue;
                }

                /* Each effect is a heading of its own, named for it, under the effects */
                String name = UIKeys.CATALYST_NLE_KEY_SECTION_EFFECTS.get() + " \u203a " + mchorse.bbs_mod.ui.film.clips.UIPostEffectPicker.effectName(definition).get();
                UIKeyframeSheet.Section section = new UIKeyframeSheet.Section("cat_fx_" + number, IKey.raw(name), Icons.SPARKLES, 0xc77dff);

                number += 1;
                this.keyTitle(channels, titles, sections, effect.intensityChannel, UIKeys.POST_FX_INTENSITY, section);

                for (int i = 0; i < definition.params.length; i++)
                {
                    this.keyTitle(channels, titles, sections, effect.paramChannels[i], mchorse.bbs_mod.ui.film.clips.UIPostEffectPicker.paramName(definition.params[i].name()), section);
                }
            }
        }

        this.keyEditor.setChannels(channels, (channel) -> titles.getOrDefault(channel, IKey.raw(channel.getId())));

        for (UIKeyframeSheet sheet : this.keyEditor.view.getGraph().getSheets())
        {
            sheet.section = sections.get(sheet.channel);
        }

        /* The headings are in: the dope sheet counts its rows again, with them open, and the view is laid out afresh */
        this.keyEditor.view.getDopeSheet().setExpanded(this.keyFolds);
        this.keyEditor.view.resetView();
        this.keyEditor.resize();
    }

    private void keyTitle(List<KeyframeChannel<?>> channels, Map<KeyframeChannel<?>, IKey> titles, Map<KeyframeChannel<?>, UIKeyframeSheet.Section> sections, KeyframeChannel<?> channel, IKey title, UIKeyframeSheet.Section section)
    {
        channels.add(channel);
        titles.put(channel, title);
        sections.put(channel, section);
    }

    /**
     * The curve editor and the timeline share one horizontal view: the zoom and the first visible
     * frame follow whichever of the two was moved. The timeline leads when the editor opens.
     */
    private void syncKeyAxis()
    {
        mchorse.bbs_mod.ui.utils.Scale axis = this.keyEditor.view.getXAxis();
        UINleTimeline timeline = this.catalystTimeline;

        if (this.syncZoom < 0D)
        {
            axis.setZoom(timeline.zoom);
            axis.setShift(timeline.scrollX);
        }
        else if (axis.getZoom() != this.syncZoom || axis.getShift() != this.syncShift)
        {
            timeline.setZoom(axis.getZoom());
            timeline.scrollX = Math.max(0D, axis.getShift());
        }
        else if (timeline.zoom != this.syncZoom || timeline.scrollX != this.syncShift)
        {
            axis.setZoom(timeline.zoom);
            axis.setShift(timeline.scrollX);
        }

        this.syncZoom = axis.getZoom();
        this.syncShift = axis.getShift();
    }

    /** Keys made, moved or taken away in the editor: a channel with keys animates its property from here on */
    private void keysEdited()
    {
        if (this.keysShown != null)
        {
            this.keysShown.syncAnimFlags();
        }

        this.markDirty();
    }

    /** The jump arrows of the inspector's fields: the playhead goes to a frame and the clips stop */
    public void jumpToFrame(int frame)
    {
        this.seekAndStop(frame);
    }

    /* ── Nested timelines ── */

    /** The frame of the nested timeline that the host's playhead falls on, at the nested timeline's own rate */
    private int nestedFrame(CatalystLayer layer, CatalystComposition child, int playhead, int hostFps)
    {
        int childFps = child.fps > 0 ? child.fps : Math.max(1, hostFps);
        int rel = playhead - layer.startFrame + layer.mediaOffset;

        return Math.max(0, Math.round(rel * (float) childFps / Math.max(1, hostFps)));
    }

    /** The timeline inside a nested clip, drawn into the clip's frame: its layers, its effects, its own nested timelines */
    private void renderNested(UIContext context, CatalystLayer layer, float lx, float ly, float layerW, float layerH, int compW, int compH, int playhead, int fps, float alpha)
    {
        CatalystComposition child = this.activeProject == null ? null : this.activeProject.getComposition(layer.resourcePath);

        if (child == null || this.nestDepth >= 6)
        {
            return;
        }

        MatrixStack stack = context.batcher.getContext().getMatrices();
        float previous = this.nestAlpha;

        stack.push();
        stack.translate(lx, ly, 0);
        stack.scale(layerW / Math.max(1, compW), layerH / Math.max(1, compH), 1F);
        this.nestDepth += 1;
        this.nestAlpha = alpha;

        try
        {
            this.renderCompositionLayers(context, child, this.nestedFrame(layer, child, playhead, fps), compW, compH, fps);
        }
        finally
        {
            this.nestAlpha = previous;
            this.nestDepth -= 1;
            stack.pop();
        }
    }

    /** Whether a timeline has a sound in it: a sound clip, a video with its own, or a nested timeline with one */
    private boolean timelineHasSound(CatalystComposition timeline, int depth)
    {
        for (CatalystLayer layer : timeline.layers)
        {
            if (layer.layerType == CatalystLayer.LayerType.AUDIO || (layer.layerType == CatalystLayer.LayerType.VIDEO && !layer.muteOwnAudio) || layer.layerType == CatalystLayer.LayerType.NESTED_AUDIO)
            {
                return true;
            }
        }

        return false;
    }

    private CatalystLayer nestedClip(CatalystComposition child, CatalystLayer.LayerType type, int start, int duration)
    {
        CatalystLayer clip = new CatalystLayer();

        clip.layerType = type;
        clip.name = child.name;
        clip.resourcePath = child.id;
        clip.color = type.defaultColor();
        clip.startFrame = Math.max(0, start);
        clip.duration = Math.max(1, duration);
        clip.mediaDuration = clip.duration;

        return clip;
    }

    /** The timeline's clip on a video track, and its sound, if it has any, linked to it on an audio track */
    public void addNestedClip(CatalystComposition child, int start)
    {
        CatalystComposition comp = this.getActiveComposition();

        if (comp == null || child == null)
        {
            return;
        }

        if (!this.activeProject.canNest(comp, child))
        {
            this.getContext().notifyError(UIKeys.CATALYST_NLE_NEST_LOOP);

            return;
        }

        this.pushUndo();
        child.refreshDuration();

        int hostFps = comp.fps > 0 ? comp.fps : 60;
        int childFps = child.fps > 0 ? child.fps : hostFps;
        int frames = Math.max(1, Math.round(child.duration * (float) hostFps / childFps));
        CatalystLayer video = this.nestedClip(child, CatalystLayer.LayerType.NESTED, start, frames);

        video.track = this.catalystTimeline.freeTrack(comp, false, video.startFrame, video.duration);
        comp.layers.add(video);

        if (this.timelineHasSound(child, 0))
        {
            CatalystLayer sound = this.nestedClip(child, CatalystLayer.LayerType.NESTED_AUDIO, start, frames);

            sound.linkId = video.linkId = java.util.UUID.randomUUID().toString().substring(0, 8);
            sound.track = this.catalystTimeline.freeTrack(comp, true, sound.startFrame, sound.duration);
            comp.layers.add(sound);
        }

        this.catalystTimeline.setSelected(video);
        this.ensureDuration();
        this.saveAndRefresh();
        this.updateInspectorForm();
    }

    /** The selected clips move into a new timeline of their own, and one nested clip takes their place */
    public void nestSelection(java.util.Set<CatalystLayer> selected)
    {
        CatalystComposition comp = this.getActiveComposition();

        if (comp == null || selected.isEmpty())
        {
            return;
        }

        this.pushUndo();

        int first = Integer.MAX_VALUE;
        int last = 0;
        List<CatalystLayer> moved = new ArrayList<>();

        for (CatalystLayer layer : selected)
        {
            if (comp.layers.contains(layer))
            {
                moved.add(layer);
                first = Math.min(first, layer.startFrame);
                last = Math.max(last, layer.startFrame + layer.duration);
            }
        }

        if (moved.isEmpty())
        {
            return;
        }

        CatalystComposition inner = this.activeProject.newComposition(UIKeys.CATALYST_NLE_NEST_NAME.get() + " " + this.activeProject.compositions.size());

        inner.width = comp.width;
        inner.height = comp.height;
        inner.fps = comp.fps;
        inner.ensureTracks();

        while (inner.videoTracks.size() < comp.videoTracks.size()) inner.videoTracks.add(new mchorse.bbs_mod.catalyst.CatalystTrack());
        while (inner.audioTracks.size() < comp.audioTracks.size()) inner.audioTracks.add(new mchorse.bbs_mod.catalyst.CatalystTrack());

        for (CatalystLayer layer : moved)
        {
            comp.layers.remove(layer);
            layer.startFrame -= first;
            layer.moveKeyframes(-first);
            layer.linkId = "";
            inner.layers.add(layer);
        }

        for (CatalystLayer layer : moved)
        {
            cleanupLayerResources(layer);
        }

        inner.refreshDuration();

        CatalystLayer video = this.nestedClip(inner, CatalystLayer.LayerType.NESTED, first, last - first);

        video.track = this.catalystTimeline.freeTrack(comp, false, video.startFrame, video.duration);
        comp.layers.add(video);

        if (this.timelineHasSound(inner, 0))
        {
            CatalystLayer sound = this.nestedClip(inner, CatalystLayer.LayerType.NESTED_AUDIO, first, last - first);

            sound.linkId = video.linkId = java.util.UUID.randomUUID().toString().substring(0, 8);
            sound.track = this.catalystTimeline.freeTrack(comp, true, sound.startFrame, sound.duration);
            comp.layers.add(sound);
        }

        this.catalystTimeline.setSelected(video);
        this.ensureDuration();
        this.library.refresh();
        this.saveAndRefresh();
        this.updateInspectorForm();
    }

    /** A double click on a nested clip: the timeline inside it opens for editing */
    public void openNested(CatalystLayer layer)
    {
        if (this.activeProject == null)
        {
            return;
        }

        CatalystComposition child = this.activeProject.getComposition(layer.resourcePath);
        int index = this.activeProject.compositions.indexOf(child);

        if (child == null || index < 0)
        {
            return;
        }

        this.stopAllAudio();
        this.activeProject.activeCompositionIndex = index;
        this.currentFrame = Math.max(0, Math.min(child.playhead, Math.max(0, child.duration - 1)));
        this.catalystTimeline.clearSelection();
        this.updateTitleLabel();
        this.updateInspectorForm();
    }

    /**
     * The sounds inside a nested clip as stand-in clips placed on the host timeline: each keeps one
     * stand-in between frames, so its player lives on. {@code shift} turns a frame of the nested timeline into
     * the host's, {@code lo} and {@code hi} are what the clip covers, {@code onlyAt} (-1 for none) the frame wanted.
     */
    private void collectNestedAudio(CatalystLayer nested, int shift, int lo, int hi, int depth, String path, float gain, int onlyAt, List<CatalystLayer> out)
    {
        CatalystComposition inner = this.activeProject == null ? null : this.activeProject.getComposition(nested.resourcePath);

        if (inner == null || depth > 5)
        {
            return;
        }

        for (CatalystLayer l : inner.layers)
        {
            boolean deeper = l.layerType == CatalystLayer.LayerType.NESTED_AUDIO;
            boolean sound = l.layerType == CatalystLayer.LayerType.AUDIO || (l.layerType == CatalystLayer.LayerType.VIDEO && !l.muteOwnAudio);

            if ((!deeper && !sound) || l.muted || this.isTrackSilent(inner, l))
            {
                continue;
            }

            int start = l.startFrame + shift;
            int from = Math.max(start, lo);
            int to = Math.min(start + l.duration, hi);

            if (to <= from || (onlyAt >= 0 && (onlyAt < from || onlyAt >= to)))
            {
                continue;
            }

            if (deeper)
            {
                this.collectNestedAudio(l, shift + l.startFrame - l.mediaOffset, from, to, depth + 1, path + "/" + l.id, gain * l.volume, onlyAt, out);

                continue;
            }

            if (l.resourcePath == null || l.resourcePath.trim().isEmpty() || CatalystMedia.isOffline(l))
            {
                continue;
            }

            CatalystLayer stand = this.nestedAudioCache.computeIfAbsent(path + "/" + l.id, (key) ->
            {
                CatalystLayer copy = l.duplicate();

                copy.layerType = CatalystLayer.LayerType.AUDIO;
                copy.linkId = "";

                return copy;
            });

            stand.track = -1;
            stand.solo = true;
            stand.muted = false;
            stand.opacity = 100;
            stand.animOpacity = false;
            stand.resourcePath = l.resourcePath;
            stand.startFrame = from;
            stand.duration = to - from;
            stand.mediaOffset = l.mediaOffset + (from - start);
            stand.audioOffset = l.audioOffset;
            stand.gainDb = 0F;
            stand.volume = l.volume * l.gainFactor() * gain;
            stand.pan = l.pan;

            /* Its keys count in the nested timeline's frames: they ride along to the host's */
            int placed = this.nestedAudioShift.getOrDefault(stand, 0);

            if (placed != shift)
            {
                stand.moveKeyframes(shift - placed);
                this.nestedAudioShift.put(stand, shift);
            }

            out.add(stand);
        }
    }

    /** What the export mixes: the timeline's clips, and the sounds inside its nested clips as stand-ins */
    private List<CatalystLayer> exportAudioSources(CatalystComposition comp)
    {
        List<CatalystLayer> list = new ArrayList<>(comp.layers);

        for (CatalystLayer layer : comp.layers)
        {
            if (layer.layerType == CatalystLayer.LayerType.NESTED_AUDIO && !layer.muted && !this.isTrackSilent(comp, layer))
            {
                this.collectNestedAudio(layer, layer.startFrame - layer.mediaOffset, layer.startFrame, layer.startFrame + layer.duration, 0, layer.id, layer.volume * layer.gainFactor(), -1, list);
            }
        }

        return list;
    }

    /* ── Loading and pre-buffering ── */

    /**
     * A video clip about to start under the playhead, within a second and a half, is asked for ahead of
     * time: its player begins decoding at its first frame, so the cut finds the picture waiting
     * instead of holding the last one while the decoder starts.
     */
    private void prewarmUpcoming(CatalystComposition comp, int fps)
    {
        int window = Math.max(1, Math.round(fps * 1.5F));
        int compW = comp.width > 0 ? comp.width : 1920;
        int compH = comp.height > 0 ? comp.height : 1080;

        /* The players of the clips that are playing now: a shared one is not to be sent ahead to another place */
        java.util.Set<Object> playing = new java.util.HashSet<>();

        for (CatalystLayer other : comp.layers)
        {
            if (other.layerType == CatalystLayer.LayerType.VIDEO && this.currentFrame >= other.startFrame && this.currentFrame < other.startFrame + other.duration)
            {
                playing.add(this.videoOwner(other));
            }
        }

        for (CatalystLayer layer : comp.layers)
        {
            if (layer.layerType != CatalystLayer.LayerType.VIDEO || layer.startFrame <= this.currentFrame || layer.startFrame > this.currentFrame + window
                || layer.resourcePath == null || layer.resourcePath.trim().isEmpty()
                || CatalystMedia.isOffline(layer) || this.isTrackHidden(comp, layer))
            {
                continue;
            }

            Object owner = this.videoOwner(layer);

            if (playing.contains(owner))
            {
                continue;
            }

            try
            {
                VideoPlayer player = BBSModClient.getVideos().getPlayer(owner, Link.create(layer.resourcePath.trim()));

                if (player != null)
                {
                    player.setMaxSize(compW, compH);
                    player.prewarm((float) (layer.mediaOffset + layer.audioOffset) / fps);
                }
            }
            catch (Exception ignored)
            {}
        }
    }

    /** Whether a clip's picture or sound is still being made ready, for the throbber on its edge in the timeline */
    private boolean isClipLoading(CatalystLayer layer)
    {
        if (layer.resourcePath == null || layer.resourcePath.trim().isEmpty())
        {
            return false;
        }

        if (layer.layerType == CatalystLayer.LayerType.VIDEO)
        {
            CatalystComposition comp = this.getActiveComposition();
            int fps = comp != null && comp.fps > 0 ? comp.fps : 60;
            boolean near = this.currentFrame >= layer.startFrame - Math.round(fps * 1.5F) && this.currentFrame < layer.startFrame + layer.duration;
            VideoPlayer player = near ? BBSModClient.getVideos().peekPlayer(this.videoOwner(layer)) : null;

            return player != null && !player.isInvalid() && !player.isWarm();
        }

        if (layer.layerType == CatalystLayer.LayerType.AUDIO)
        {
            return layer.isWaveformLoading || BBSModClient.getSounds().isLoading(Link.create(layer.resourcePath.trim()));
        }

        return false;
    }

    /** Whether a video clip has a frame on hand, for the render strip over the ruler */
    private boolean isClipBuffered(CatalystLayer layer)
    {
        VideoPlayer player = BBSModClient.getVideos().peekPlayer(this.videoOwner(layer));

        return player != null && !player.isInvalid() && player.isWarm();
    }

    /** What the monitor is waiting for right now: the picture of a video clip, the sound of a clip */
    private List<String> loadingNow(CatalystComposition comp)
    {
        List<String> now = new ArrayList<>();
        boolean video = false;
        boolean audio = false;

        if (comp == null)
        {
            return now;
        }

        for (CatalystLayer layer : comp.layers)
        {
            if (this.currentFrame < layer.startFrame || this.currentFrame >= layer.startFrame + layer.duration
                || layer.resourcePath == null || layer.resourcePath.trim().isEmpty() || CatalystMedia.isOffline(layer))
            {
                continue;
            }

            if (layer.layerType == CatalystLayer.LayerType.VIDEO)
            {
                VideoPlayer player = BBSModClient.getVideos().peekPlayer(this.videoOwner(layer));

                video |= player != null && !player.isInvalid() && !player.isWarm();
            }

            if ((layer.layerType == CatalystLayer.LayerType.VIDEO && !layer.muteOwnAudio) || layer.layerType == CatalystLayer.LayerType.AUDIO)
            {
                audio |= BBSModClient.getSounds().isLoading(Link.create(layer.resourcePath.trim()));
            }
        }

        if (video)
        {
            now.add(UIKeys.CATALYST_NLE_VIDEO_LOADING.get());
        }

        if (audio)
        {
            now.add(UIKeys.CATALYST_NLE_AUDIO_LOADING.get());
        }

        return now;
    }

    /** A slim badge in the corner of the monitor for each thing it is waiting on, with a light running along its foot */
    private void renderLoadingBadge(UIContext context, Area area, CatalystComposition comp)
    {
        List<String> now = this.loadingNow(comp);

        if (now.isEmpty())
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        double phase = (System.currentTimeMillis() % 1000L) / 1000D;
        int y = area.y + 8;

        for (String text : now)
        {
            int w = font.getWidth(text) + 26;
            int x = area.ex() - w - 8;
            int segment = Math.max(12, w / 3);
            int from = x + 4 + (int) Math.round(phase * (w - 8 + segment)) - segment;

            RoundedDraw.fill(context.batcher, x, y, x + w, y + 18, 5, RoundedDraw.ALL, 0xd911161f);
            RoundedDraw.outline(context.batcher, x, y, x + w, y + 18, 5, RoundedDraw.ALL, 0x40ffffff);
            context.batcher.box(x + 4, y + 15, x + w - 4, y + 17, 0x40ffffff);
            context.batcher.box(Math.max(x + 4, from), y + 15, Math.min(x + w - 4, from + segment), y + 17, 0xf0bfe0ff);
            context.batcher.text(text, x + 13, y + 4, 0xffe8edf5, false);

            y += 22;
        }
    }

    /** Scrubbing (manual timeline slide): moves the playhead and seeks the audio; the video follows when the monitor draws */
    public void seekToFrame(int frame)
    {
        CatalystComposition comp = this.activeProject != null ? this.activeProject.getActiveComposition() : null;
        if (comp == null) return;

        this.currentFrame = Math.max(0, Math.min(frame, comp.duration - 1));
        comp.playhead = this.currentFrame;

        int fps = comp.fps > 0 ? comp.fps : 60;

        /* The video clips need nothing here: the monitor asks each player for the playhead's frame
         * as it draws, and the player's prefetch worker jumps there off the render thread. Decoding
         * here, once per mouse move and for every clip, was what made scrubbing stall. */

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

                float effOpacity = layer.computeEffective(frame)[5];
                float vol = layer.computeVolumeWithGain(frame) * (effOpacity / 100.0F) * this.masterVolume;
                float pan = Math.max(-1.0F, Math.min(1.0F, layer.computeEffectivePan(frame)));
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

            /* The name of the studio, in the middle of the bar */
            FontRenderer titleFont = context.batcher.getFont();
            String title = "Catalyst Studio";

            context.batcher.text(title, a.mx() - titleFont.getWidth(title) / 2, a.my() - titleFont.getHeight() / 2, 0xffe8edf5, false);
        }));

        /* Tab buttons */
        this.tabProjects = new UICatalystTab(Icons.FOLDER, UIKeys.CATALYST_TAB_PROJECTS,
            () -> this.currentTab == CatalystTab.PROJECTS,
            (b) -> this.setTab(CatalystTab.PROJECTS));
        FontRenderer tabFont = mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D.getDefaultTextRenderer();
        int projectsW = 6 + 18 + tabFont.getWidth(UIKeys.CATALYST_TAB_PROJECTS.get()) + 10;
        int editorW = 6 + 18 + tabFont.getWidth(UIKeys.CATALYST_TAB_EDITOR.get()) + 10;
        int pathX = 4 + projectsW + 4 + editorW + 14;

        this.tabProjects.relative(this.topBar).x(4).y(2).w(projectsW).h(20);

        this.tabEditor = new UICatalystTab(Icons.FILM, UIKeys.CATALYST_TAB_EDITOR,
            () -> this.currentTab == CatalystTab.EDITOR,
            (b) -> this.setTab(CatalystTab.EDITOR));
        this.tabEditor.relative(this.topBar).x(4 + projectsW + 4).y(2).w(editorW).h(20);

        /* The path of the project and its timeline, cut to the room between the tabs and the studio's name */
        this.activeProjectLabel = new UILabel(IKey.raw(""))
        {
            @Override
            public void render(UIContext context)
            {
                this.label = IKey.raw(context.batcher.getFont().limitToWidth(UICatalystPanel.this.projectPath, Math.max(0, this.area.w)));
                super.render(context);
            }
        };
        this.activeProjectLabel.labelAnchor(0F, 0.5F);
        this.activeProjectLabel.relative(this.topBar).x(pathX).y(0).w(0.5F, -(pathX + 76)).h(24);

        /* Action buttons — editor-only group */
        this.editorActions = new UIElement();
        this.editorActions.relative(this.topBar).x(1F, -50).y(2).w(46).h(20);

        this.renderExportButton = new UIIcon(Icons.VIDEO_CAMERA, (b) -> this.openExportModal());
        this.renderExportButton.tooltip(UIKeys.CATALYST_NLE_EXPORT_VIDEO);

        this.fullscreenButton = new UIIcon(Icons.FULLSCREEN, (b) -> this.toggleFullscreen());
        this.fullscreenButton.tooltip(UIKeys.CATALYST_NLE_FULLSCREEN);

        this.renderExportButton.relative(this.editorActions).x(0).w(20).h(20);
        this.fullscreenButton.relative(this.editorActions).x(22).w(20).h(20);

        this.editorActions.add(this.renderExportButton, this.fullscreenButton);

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

    /**
     * The edit page: the dock holds the library, the monitor, the inspector and the timeline. Here it is
     * shown or hidden as a whole, and the monitor's picture is lifted out of it for fullscreen and for
     * an export, where it fills the page.
     */
    public void updateEditorLayout()
    {
        boolean isEditor = (this.currentTab == CatalystTab.EDITOR && this.activeProject != null);

        if (!isEditor || this.editor == null || this.isUpdatingLayout)
        {
            return;
        }

        this.isUpdatingLayout = true;

        try
        {
            boolean full = this.isFullscreen;

            this.layoutPreview(full || this.exportLayout);
            this.editor.setVisible(!full && !this.exportLayout);

            if (!this.exportLayout)
            {
                this.topBar.setVisible(!full);
            }

            if (full)
            {
                /* The monitor alone, edge to edge; its controls are the bar that comes with the mouse */
                this.fullscreenBar.relative(this).x(0.5F).y(1F, -52).w(0.7F).h(42).anchorX(0.5F);
            }
            else
            {
                this.fullscreenBar.setVisible(false);
            }

            this.resize();
        }
        finally
        {
            this.isUpdatingLayout = false;
        }
    }

    /**
     * The monitor's picture sits in the monitor panel; lifted out it is a child of the page itself,
     * under the fullscreen bar, and fills it.
     */
    private void layoutPreview(boolean lifted)
    {
        UIElement parent = this.previewArea.getParent();

        if (lifted)
        {
            if (parent != this)
            {
                this.previewArea.removeFromParent();
                this.addBefore(this.fullscreenBar, this.previewArea);
            }

            this.previewArea.relative(this).xy(0, 0).w(1F).h(1F);
        }
        else
        {
            if (parent != this.monitorPanel)
            {
                this.previewArea.removeFromParent();
                this.monitorPanel.add(this.previewArea);
            }

            this.previewArea.relative(this.monitorPanel).x(0).y(MONITOR_BAR_H).w(1F).h(1F, -MONITOR_BAR_H - TRANSPORT_H);
        }
    }

    public void updateMediaPoolLayout()
    {
        this.updateEditorLayout();
    }

    public void toggleMediaPool()
    {
        this.catalystDock.togglePanel(CatalystLayouts.MEDIA);
    }

    public void toggleFullscreen()
    {
        this.isFullscreen = !this.isFullscreen;
        this.lastMouseMoveAt = System.currentTimeMillis();

        /* The guides are off while the monitor is fullscreen, and come back as they were */
        if (this.isFullscreen)
        {
            this.safeAreasBeforeFullscreen = showSafeAreas;
            showSafeAreas = false;
        }
        else
        {
            showSafeAreas = this.safeAreasBeforeFullscreen;
        }

        this.updateEditorLayout();
    }

    /** Mute: the master volume goes to nothing and comes back to what it was */
    public void toggleMute()
    {
        if (this.masterVolume > 0.0001F)
        {
            this.volumeBeforeMute = this.masterVolume;
            this.masterVolume = 0F;
        }
        else
        {
            this.masterVolume = this.volumeBeforeMute > 0.0001F ? this.volumeBeforeMute : 1F;
        }
    }

    /**
     * The fullscreen player's bar: a scrubber across it, play, loop, mute and the volume, the
     * timecode and the way out. It is only there while the monitor is fullscreen and the mouse has
     * moved in the last ten seconds.
     */
    private void setupFullscreenBar()
    {
        this.fullscreenBar = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                return this.area.isInside(context);
            }

            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                CatalystComposition comp = UICatalystPanel.this.getActiveComposition();
                int fps = comp != null && comp.fps > 0 ? comp.fps : 60;
                FontRenderer font = context.batcher.getFont();
                String code = UINleTimeline.timecode(UICatalystPanel.this.currentFrame, fps) + " / " + UINleTimeline.timecode(UICatalystPanel.this.timelineEnd(), fps);

                mchorse.bbs_mod.ui.framework.elements.windows.GlassChrome.shadow(context, a.x, a.y, a.ex(), a.ey(), 8, 0.45F);
                mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.fill(context.batcher, a.x, a.y, a.ex(), a.ey(), 6, mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, 0xd911161f);
                mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.outline(context.batcher, a.x, a.y, a.ex(), a.ey(), 6, mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, 0x40ffffff);
                context.batcher.text(code, a.mx() - font.getWidth(code) / 2, a.y + 24, 0xffe8edf5, false);

                super.render(context);
            }
        };

        UINleToolbar.Slider scrub = new UINleToolbar.Slider(
            () -> this.currentFrame / (double) Math.max(1, this.timelineEnd()),
            (v) -> this.seekAndStop((int) Math.round(v * this.timelineEnd())));
        scrub.relative(this.fullscreenBar).x(12).y(5).w(1F, -24).h(12);

        UIElement left = new UIElement();
        left.relative(this.fullscreenBar).x(10).y(20).w(4 * 24 + 70).h(18);

        this.transportButton(left, 0, () -> this.isPlaying ? Icons.PAUSE : Icons.PLAY, UIKeys.CATALYST_NLE_PLAY, this::togglePlayback, () -> this.isPlaying);
        this.transportButton(left, 1, () -> Icons.REFRESH, UIKeys.CATALYST_NLE_LOOP, () -> this.loopPlayback = !this.loopPlayback, () -> this.loopPlayback);
        this.transportButton(left, 2, () -> Icons.SOUND, UIKeys.CATALYST_NLE_MUTE, this::toggleMute, () -> this.masterVolume <= 0.0001F);

        UINleToolbar.Slider volume = new UINleToolbar.Slider(() -> this.masterVolume / 2D, (v) -> this.masterVolume = (float) (v * 2D));
        volume.tooltip(UIKeys.CATALYST_NLE_MASTER_VOLUME);
        volume.relative(left).x(3 * 24 + 4).y(3).w(64).h(12);
        left.add(volume);

        UIElement right = new UIElement();
        right.relative(this.fullscreenBar).x(1F, -34).y(20).w(24).h(18);
        this.transportButton(right, 0, () -> Icons.MINIMIZE, UIKeys.CATALYST_NLE_EXIT_FULLSCREEN, this::toggleFullscreen, () -> false);

        /* The preview quality, which is not on the picture while it is fullscreen */
        UIButton quality = new UIButton(IKey.EMPTY, (b) -> this.cyclePreviewResolution())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                this.label = IKey.raw("[" + previewResolution.title + "]");
                super.renderSkin(context);
            }
        };
        quality.tooltip(UIKeys.CATALYST_NLE_PREVIEW_QUALITY);
        quality.relative(this.fullscreenBar).x(1F, -110).y(20).w(68).h(18);

        this.fullscreenBar.add(scrub, left, right, quality);
        this.fullscreenBar.setVisible(false);
    }

    public void setPreviewResolution(PreviewResolution res)
    {
        if (previewResolution != res)
        {
            previewResolution = res;
            if (this.previewResolutionBtn != null)
            {
                this.previewResolutionBtn.label = IKey.raw("[" + res.title + "]");
            }

            if (this.previewFbo != null)
            {
                this.previewFbo.delete();
                this.previewFbo = null;
            }
            if (this.previewFboTexture != null)
            {
                this.previewFboTexture.delete();
                this.previewFboTexture = null;
            }
        }
    }

    public void cyclePreviewResolution()
    {
        this.setPreviewResolution(previewResolution.next());
    }

    public void toggleSafeAreas()
    {
        showSafeAreas = !showSafeAreas;
    }

    private Framebuffer getOrCreatePreviewFramebuffer(int targetW, int targetH)
    {
        targetW = Math.max(16, (targetW / 2) * 2);
        targetH = Math.max(16, (targetH / 2) * 2);

        if (this.previewFboTexture == null)
        {
            this.previewFboTexture = new Texture();
            this.previewFboTexture.setFormat(TextureFormat.RGBA_U8);
            this.previewFboTexture.setFilter(GL11.GL_NEAREST);
        }

        if (this.previewFbo == null || this.previewFboTexture.width != targetW || this.previewFboTexture.height != targetH)
        {
            if (this.previewFbo != null)
            {
                this.previewFbo.delete();
                this.previewFbo = null;
            }

            this.previewFboTexture.bind();
            this.previewFboTexture.setSize(targetW, targetH);
            this.previewFboTexture.setFilter(GL11.GL_NEAREST);
            this.previewFboTexture.unbind();

            this.previewFbo = new Framebuffer();
            this.previewFbo.attach(this.previewFboTexture, GL30.GL_COLOR_ATTACHMENT0);
            this.previewFbo.attachments(1);
            this.previewFbo.unbind();
        }

        return this.previewFbo;
    }

    /* ════════════════════════════════════════════════════════
     *  PROJECTS VIEW
     * ════════════════════════════════════════════════════════ */

    private void setupProjectsView()
    {
        this.hub = new UICatalystHubScreen(this);
        this.projectsView = this.hub;
        this.projectsView.relative(this).y(24).w(1F).h(1F, -24);

        /* Initialize legacy form fields in background to ensure non-null references */
        this.projectList = new UICatalystProjectList((selected) ->
        {
            if (!selected.isEmpty()) this.setActiveProject(selected.get(0));
        });
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
        this.openProjectButton = new UIButton(UIKeys.CATALYST_PROJECTS_CREATE_OPEN, (b) -> this.openOrCreateProject());
        this.clearFormButton = new UIButton(UIKeys.CATALYST_PROJECTS_NEW, (b) -> this.clearForm());
        this.deleteProjectButton = new UIButton(UIKeys.CATALYST_PROJECTS_DELETE, (b) -> this.deleteSelectedProject());
    }

    private long lastThumbnailCaptureTime = 0L;

    public void captureProjectThumbnail(CatalystProject project)
    {
        if (project == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastThumbnailCaptureTime < 1000L)
        {
            return;
        }
        this.lastThumbnailCaptureTime = now;

        int textureId = 0;
        int width = 0;
        int height = 0;

        if (this.previewFboTexture != null && this.previewFboTexture.id > 0 && this.previewFboTexture.width > 0 && this.previewFboTexture.height > 0)
        {
            textureId = this.previewFboTexture.id;
            width = this.previewFboTexture.width;
            height = this.previewFboTexture.height;
        }

        if (textureId <= 0 || width <= 0 || height <= 0)
        {
            return;
        }

        try
        {
            FloatBuffer pixelData = BufferUtils.createFloatBuffer(width * height * 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, pixelData);
            pixelData.rewind();

            int targetW = 320;
            int targetH = Math.max(1, (int) Math.round((double) height * targetW / width));

            if (width <= targetW)
            {
                targetW = width;
                targetH = height;
            }

            int[] targetPixels = new int[targetW * targetH];

            for (int ty = 0; ty < targetH; ty++)
            {
                int sy = (height - 1) - (ty * height / targetH);

                for (int tx = 0; tx < targetW; tx++)
                {
                    int sx = tx * width / targetW;
                    int offset = (sy * width + sx) * 4;

                    float r = pixelData.get(offset) * 255.0F;
                    float g = pixelData.get(offset + 1) * 255.0F;
                    float b = pixelData.get(offset + 2) * 255.0F;

                    int ir = Math.min(255, Math.max(0, (int) r));
                    int ig = Math.min(255, Math.max(0, (int) g));
                    int ib = Math.min(255, Math.max(0, (int) b));

                    targetPixels[ty * targetW + tx] = 0xFF000000 | (ir << 16) | (ig << 8) | ib;
                }
            }

            final String projectName = project.name;
            final int fw = targetW;
            final int fh = targetH;

            Thread thread = new Thread(() ->
            {
                try
                {
                    File folder = CatalystProjectManager.getProjectsFolder();
                    if (folder != null)
                    {
                        if (!folder.exists())
                        {
                            folder.mkdirs();
                        }
                        File thumbFolder = CatalystProjectManager.getThumbnailsFolder();
                        if (!thumbFolder.exists())
                        {
                            thumbFolder.mkdirs();
                        }
                        File thumbFile = new File(thumbFolder, projectName + ".png");

                        Pixels pixels = Pixels.fromIntArray(fw, fh, targetPixels);
                        try
                        {
                            PNGEncoder.writeToFile(pixels, thumbFile);
                        }
                        finally
                        {
                            pixels.delete();
                        }

                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc != null)
                        {
                            mc.execute(() -> UICatalystHubScreen.invalidateThumbnail(projectName));
                        }
                        else
                        {
                            UICatalystHubScreen.invalidateThumbnail(projectName);
                        }
                    }
                }
                catch (Throwable t)
                {
                    /* Silent ignore */
                }
            }, "BBS-Catalyst-Thumbnail-Saver");

            thread.setDaemon(true);
            thread.start();
        }
        catch (Throwable t)
        {
            /* Silent ignore */
        }
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
        if (this.nameInput != null) this.nameInput.setText("New Project");
        if (this.fpsInput != null) this.fpsInput.setValue(60);
        if (this.durationSecondsInput != null) this.durationSecondsInput.setValue(5.0);
        if (this.widthInput != null) this.widthInput.setValue(1920);
        if (this.heightInput != null) this.heightInput.setValue(1080);
        this.updateDurationCalcLabel();
        if (this.openProjectButton != null) this.openProjectButton.label = UIKeys.CATALYST_PROJECTS_CREATE_OPEN;
        if (this.deleteProjectButton != null) this.deleteProjectButton.setVisible(false);
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

        this.library = new UINleLibrary(new UINleLibrary.Host()
        {
            @Override
            public void addFilm(String filmId)
            {
                UICatalystPanel.this.addFilmClip(filmId, UICatalystPanel.this.currentFrame);
            }

            @Override
            public void addEffectToSelection(mchorse.bbs_mod.camera.clips.fx.PostEffect effect)
            {
                if (UICatalystPanel.this.getSelectedLayer() == null)
                {
                    UICatalystPanel.this.getContext().notifyError(UIKeys.CATALYST_NLE_NEED_CLIP);

                    return;
                }

                UICatalystPanel.this.inspector.addEffect(effect);
            }

            @Override
            public void addAdjustmentClip()
            {
                UICatalystPanel.this.catalystTimeline.addClip(CatalystLayer.LayerType.ADJUSTMENT, UICatalystPanel.this.currentFrame, 1);
            }

            @Override
            public void setTransition(String transition)
            {
                CatalystLayer layer = UICatalystPanel.this.getSelectedLayer();

                if (layer == null)
                {
                    UICatalystPanel.this.getContext().notifyError(UIKeys.CATALYST_NLE_NEED_CLIP);

                    return;
                }

                UICatalystPanel.this.pushUndo();
                layer.transitionIn = transition;
                UICatalystPanel.this.markDirty();
            }

            @Override
            public void addTitle(boolean lowerThird)
            {
                CatalystLayer layer = UICatalystPanel.this.catalystTimeline.addClip(CatalystLayer.LayerType.TEXT, UICatalystPanel.this.currentFrame, 1);
                CatalystComposition comp = UICatalystPanel.this.getActiveComposition();

                if (layer != null && lowerThird && comp != null)
                {
                    layer.name = UIKeys.CATALYST_NLE_TITLE_LOWER_THIRD.get();
                    layer.resourcePath = UIKeys.CATALYST_NLE_TITLE_LOWER_THIRD.get();
                    layer.fontSize = 28;
                    layer.posY = comp.height * 0.32F;
                    layer.scaleY = 0.2F;
                }
            }

            @Override
            public java.util.List<CatalystComposition> timelines()
            {
                return UICatalystPanel.this.activeProject == null ? new ArrayList<>() : UICatalystPanel.this.activeProject.compositions;
            }

            @Override
            public void dragTimeline(CatalystComposition timeline)
            {
                CatalystMediaAsset asset = new CatalystMediaAsset();

                asset.type = CatalystMediaAsset.MediaType.TIMELINE;
                asset.path = timeline.id;
                asset.name = timeline.name;
                UICatalystPanel.this.draggedAsset = asset;
            }
        }, this.mediaPoolPanel);

        this.catalystTimeline = new UINleTimeline(this::getActiveComposition, () -> this.currentFrame, (frame) ->
        {
            this.isPlaying = false;
            this.lastTickMs = 0;
            this.stopAllAudio();
            this.seekToFrame(frame);
        });
        this.catalystTimeline.onPreModify = this::pushUndo;
        this.catalystTimeline.onModified = () ->
        {
            this.ensureDuration();
            this.saveAndRefresh();
            this.updateInspectorForm();
        };
        this.catalystTimeline.onSelectionChanged = this::updateInspectorForm;
        this.catalystTimeline.onNest = this::nestSelection;
        this.catalystTimeline.onGain = this::openGainDialog;
        this.catalystTimeline.interactionLocked = () -> this.aiBusy;
        this.catalystTimeline.onOpenNested = this::openNested;

        this.nleToolbar = new UINleToolbar(this.catalystTimeline, () -> this.currentFrame,
            () -> this.getActiveComposition() != null && this.getActiveComposition().fps > 0 ? this.getActiveComposition().fps : 60,
            () -> this.masterVolume, (v) -> this.masterVolume = (float) v, this::toggleMute);

        this.inspector = new UINleInspector(this);

        this.catalystTimeline.loading = this::isClipLoading;
        this.catalystTimeline.buffered = this::isClipBuffered;

        this.setupMonitorBars();
        this.setupFullscreenBar();

        /* The monitor panel is its bar, the picture and the transport; the timeline panel is the tool strip over the tracks */
        this.monitorPanel = new UIElement();
        this.monitorBar.relative(this.monitorPanel).x(0).y(0).w(1F).h(MONITOR_BAR_H);
        this.previewArea.relative(this.monitorPanel).x(0).y(MONITOR_BAR_H).w(1F).h(1F, -MONITOR_BAR_H - TRANSPORT_H);
        this.transportBar.relative(this.monitorPanel).x(0).y(1F, -TRANSPORT_H).w(1F).h(TRANSPORT_H);
        this.monitorPanel.add(this.monitorBar, this.previewArea, this.transportBar);

        this.timelinePanel = new UIElement();
        this.nleToolbar.relative(this.timelinePanel).x(0).y(0).w(1F).h(UINleToolbar.HEIGHT);
        this.catalystTimeline.relative(this.timelinePanel).x(0).y(UINleToolbar.HEIGHT).w(1F).h(1F, -UINleToolbar.HEIGHT);
        this.timelinePanel.add(this.nleToolbar, this.catalystTimeline);

        /* The dope sheet and the flow graph of BBS, on the channels of the picked clip */
        this.keyEditor = new UIKeyframeEditor((consumer) -> new UIKeyframes(consumer)
        {
            @Override
            public float getPlayheadTick(UIContext context)
            {
                return UICatalystPanel.this.currentFrame;
            }

            /* The ruler scrubs: the playhead and the monitor follow the mouse */
            @Override
            protected boolean hasCursor()
            {
                return true;
            }

            @Override
            protected void moveNoKeyframes(UIContext context)
            {
                UICatalystPanel.this.jumpToFrame(Math.max(0, (int) Math.round(this.fromGraphX(context.mouseX))));
            }

            /* The names of the channels need room: never narrower than 170 pixels */
            @Override
            public int getLabelWidth()
            {
                return Math.max(170, super.getLabelWidth());
            }

            /* The timeline's own dark palette */
            @Override
            public int baseSurface()
            {
                return 0xff181d27;
            }

            @Override
            public int deepSurface()
            {
                return 0xff12161e;
            }

            @Override
            public int sunkenSurface()
            {
                return 0xff0e1218;
            }

            @Override
            protected void renderOverlay(UIContext context)
            {
                CatalystComposition comp = UICatalystPanel.this.getActiveComposition();
                int fps = comp != null && comp.fps > 0 ? comp.fps : 60;

                context.batcher.clip(this.graphArea, context);
                UITimelineCanvas.renderCursor(context, UINleTimeline.timecode(UICatalystPanel.this.currentFrame, fps), this.area, this.toGraphX(UICatalystPanel.this.currentFrame) - 1);
                context.batcher.unclip(context);

                super.renderOverlay(context);
            }
        });
        this.keyEditor.view.duration(() ->
        {
            CatalystComposition comp = this.getActiveComposition();

            return comp == null ? 300 : Math.max(1, comp.duration);
        });
        this.keyEditor.view.changed(this::keysEdited);
        this.keyEditor.relative(this.timelinePanel).x(0).y(0.5F).w(1F).h(0.5F);
        this.keyEditor.setVisible(false);
        this.timelinePanel.add(this.keyEditor);

        this.nleToolbar.keysOpen = () -> this.keysOpen;
        this.nleToolbar.toggleKeys = this::toggleKeys;

        this.editor = new UIElement();
        this.editor.relative(this).y(24).w(1F).h(1F, -24);
        this.editor.setVisible(false);
        this.catalystDock = new CatalystDock(this.editor, this.library, this.monitorPanel, this.inspector, this.timelinePanel);
    }

    /* ════════════════════════════════════════════════════════
     *  EDIT PAGE: MONITOR, TRANSPORT, PAGE BAR
     * ════════════════════════════════════════════════════════ */

    private void setupMonitorBars()
    {
        this.monitorBar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                CatalystComposition comp = UICatalystPanel.this.getActiveComposition();
                int fps = comp != null && comp.fps > 0 ? comp.fps : 60;
                FontRenderer font = context.batcher.getFont();
                String code = UINleTimeline.timecode(UICatalystPanel.this.currentFrame, fps);

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xff181d27);
                context.batcher.box(a.x, a.ey() - 1, a.ex(), a.ey(), 0xff262d3a);
                context.batcher.text(code, a.ex() - font.getWidth(code) - 8, a.my() - 4, 0xffe8edf5, false);

                super.render(context);
            }
        };

        UIButton zoom = new UIButton(IKey.EMPTY, (b) -> this.monitorZoomMenu())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                this.label = monitorZoom <= 0 ? UIKeys.CATALYST_NLE_ZOOM_FIT : IKey.raw("%" + monitorZoom);
                super.renderSkin(context);
            }
        };
        zoom.tooltip(UIKeys.CATALYST_NLE_MONITOR_ZOOM);
        zoom.relative(this.monitorBar).x(4).y(2).w(52).h(16);

        UIButton timeline = new UIButton(IKey.EMPTY, (b) -> this.timelineMenu())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                CatalystComposition comp = UICatalystPanel.this.getActiveComposition();

                this.label = IKey.raw(context.batcher.getFont().limitToWidth(comp == null ? "-" : comp.name, Math.max(10, this.area.w - 30)));
                super.renderSkin(context);
                context.batcher.icon(Icons.ARROW_DOWN, 0xccffffff, this.area.ex() - 18, this.area.my() - 8);
            }
        };
        timeline.background(false);
        timeline.tooltip(UIKeys.CATALYST_NLE_TIMELINES);
        timeline.relative(this.monitorBar).x(0.5F).y(2).w(160).h(16).anchorX(0.5F);

        this.monitorBar.add(zoom, timeline);

        this.transportBar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                Area a = this.area;
                CatalystComposition comp = UICatalystPanel.this.getActiveComposition();
                FontRenderer font = context.batcher.getFont();

                context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xff181d27);
                context.batcher.box(a.x, a.y, a.ex(), a.y + 1, 0xff262d3a);

                /* The frame's size, rate and preview quality on the left, the frame count on the right, off the picture */
                if (comp != null && a.w > 360)
                {
                    String info = String.format(java.util.Locale.ROOT, "%dx%d  %d FPS  %s", comp.width, comp.height, comp.fps, previewResolution.fraction);

                    context.batcher.text(info, a.x + 8, a.my() - 4, 0xff8c96aa, false);
                }

                super.render(context);
            }
        };

        UIElement buttons = new UIElement();
        buttons.relative(this.transportBar).x(0.5F).y(3).w(6 * 24).h(18).anchorX(0.5F);

        this.transportButton(buttons, 0, () -> Icons.SHIFT_BACKWARD, UIKeys.CATALYST_NLE_GO_START, () -> this.seekAndStop(0), () -> false);
        this.transportButton(buttons, 1, () -> Icons.FRAME_PREV, UIKeys.CATALYST_NLE_PREV_FRAME, () -> this.seekAndStop(Math.max(0, this.currentFrame - 1)), () -> false);
        this.transportButton(buttons, 2, () -> this.isPlaying ? Icons.PAUSE : Icons.PLAY, UIKeys.CATALYST_NLE_PLAY, this::togglePlayback, () -> this.isPlaying);
        this.transportButton(buttons, 3, () -> Icons.FRAME_NEXT, UIKeys.CATALYST_NLE_NEXT_FRAME, () -> this.seekAndStop(this.currentFrame + 1), () -> false);
        this.transportButton(buttons, 4, () -> Icons.SHIFT_FORWARD, UIKeys.CATALYST_NLE_GO_END, () -> this.seekAndStop(this.timelineEnd()), () -> false);
        this.transportButton(buttons, 5, () -> Icons.REFRESH, UIKeys.CATALYST_NLE_LOOP, () -> this.loopPlayback = !this.loopPlayback, () -> this.loopPlayback);

        this.transportBar.add(buttons);
    }

    private void transportButton(UIElement row, int index, java.util.function.Supplier<mchorse.bbs_mod.ui.utils.icons.Icon> icon, IKey tooltip, Runnable action, java.util.function.BooleanSupplier lit)
    {
        UIIcon button = new UIIcon(icon, (b) -> action.run())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (lit.getAsBoolean() || this.hover)
                {
                    mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 4,
                        mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, lit.getAsBoolean() ? Colors.setA(BBSSettings.primaryColor.get(), 0.55F) : 0x22ffffff);
                }

                super.renderSkin(context);
            }
        };

        button.tooltip(tooltip);
        button.relative(row).x(index * 24).y(0).wh(20, 18);
        row.add(button);
    }

    private void seekAndStop(int frame)
    {
        this.isPlaying = false;
        this.lastTickMs = 0;
        this.stopAllAudio();
        this.seekToFrame(Math.max(0, frame));
    }

    /** The end of the last clip, or of the composition when it is empty */
    private int timelineEnd()
    {
        CatalystComposition comp = this.getActiveComposition();

        if (comp == null)
        {
            return 0;
        }

        int end = 0;

        for (CatalystLayer layer : comp.layers)
        {
            end = Math.max(end, layer.startFrame + layer.duration);
        }

        return Math.max(0, (end > 0 ? end : comp.duration) - 1);
    }

    /** The composition runs at least to the end of its last clip */
    private void ensureDuration()
    {
        CatalystComposition comp = this.getActiveComposition();

        if (comp != null)
        {
            comp.refreshDuration();
        }
    }

    private void monitorZoomMenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(monitorZoom <= 0 ? Icons.CHECKMARK : Icons.NONE, UIKeys.CATALYST_NLE_ZOOM_FIT, () -> monitorZoom = 0);

            for (int percent : new int[] {25, 50, 100, 200})
            {
                menu.action(monitorZoom == percent ? Icons.CHECKMARK : Icons.NONE, IKey.raw("%" + percent), () -> monitorZoom = percent);
            }

            menu.action(showSafeAreas ? Icons.CHECKMARK : Icons.NONE, UIKeys.CATALYST_NLE_SAFE_AREAS, this::toggleSafeAreas);

            for (PreviewResolution res : PreviewResolution.values())
            {
                menu.action(res == previewResolution ? Icons.CHECKMARK : Icons.NONE, IKey.raw(res.fraction), () -> this.setPreviewResolution(res));
            }
        });
    }

    /** The timelines of the project (its compositions): switch, add one, or change the picked one's settings */
    private void timelineMenu()
    {
        if (this.activeProject == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            for (int i = 0; i < this.activeProject.compositions.size(); i++)
            {
                int index = i;
                CatalystComposition comp = this.activeProject.compositions.get(i);

                menu.action(index == this.activeProject.activeCompositionIndex ? Icons.CHECKMARK : Icons.FILM, IKey.raw(comp.name), () ->
                {
                    this.stopAllAudio();
                    this.activeProject.activeCompositionIndex = index;
                    this.currentFrame = this.activeProject.getActiveComposition().playhead;
                    this.catalystTimeline.clearSelection();
                    this.updateTitleLabel();
                });
            }

            menu.action(Icons.ADD, UIKeys.CATALYST_NLE_NEW_TIMELINE, () ->
            {
                this.activeProject.addComposition(UIKeys.CATALYST_NLE_TIMELINE.get() + " " + (this.activeProject.compositions.size() + 1));
                this.currentFrame = 0;
                this.catalystTimeline.clearSelection();
                CatalystProjectManager.saveProject(this.activeProject);
                this.updateTitleLabel();
            });
            menu.action(Icons.GEAR, UIKeys.CATALYST_NLE_TIMELINE_SETTINGS, this::openCompositionSettingsModal);
        });
    }

    /* ════════════════════════════════════════════════════════
     *  EDIT HELPERS
     * ════════════════════════════════════════════════════════ */

    public int getCurrentFrame()
    {
        return this.currentFrame;
    }

    /** Undo for a run of edits to one thing (a trackpad being dragged): one step, not one per frame */
    public void pushUndoFor(String key)
    {
        long now = System.currentTimeMillis();

        if (!key.equals(this.lastUndoKey) || now - this.lastUndoAt > 1500L)
        {
            this.pushUndo();
        }

        this.lastUndoKey = key;
        this.lastUndoAt = now;
    }

    /** An edit landed: saved a moment after the last one, not on every step of a drag */
    public void markDirty()
    {
        this.dirtyAt = System.currentTimeMillis();
    }

    /** A BBS film as a clip on a free video track */
    public void addFilmClip(String filmId, int frame)
    {
        CatalystComposition comp = this.getActiveComposition();

        if (comp == null || filmId == null || filmId.isEmpty())
        {
            return;
        }

        this.pushUndo();

        CatalystLayer layer = new CatalystLayer();
        int fps = comp.fps > 0 ? comp.fps : 60;

        layer.layerType = CatalystLayer.LayerType.SCENE;
        layer.color = CatalystLayer.LayerType.SCENE.defaultColor();
        layer.name = filmId;
        layer.resourcePath = filmId;
        layer.startFrame = Math.max(0, frame);

        try
        {
            mchorse.bbs_mod.film.Film film = BBSMod.getFilms().load(filmId);
            int ticks = film == null ? 0 : film.camera.calculateDuration();

            layer.duration = ticks > 0 ? Math.max(1, Math.round(ticks / 20F * fps)) : fps * 5;
            layer.mediaDuration = ticks > 0 ? layer.duration : 0;
        }
        catch (Exception e)
        {
            layer.duration = fps * 5;
        }

        layer.track = this.catalystTimeline.freeTrack(comp, false, layer.startFrame, layer.duration);
        comp.layers.add(layer);
        this.catalystTimeline.setSelected(layer);
        this.ensureDuration();
        this.saveAndRefresh();
    }

    /** Whether a clip's track keeps it from being heard: an audio track that is muted, or not soloed while another is */
    private boolean isTrackSilent(CatalystComposition comp, CatalystLayer layer)
    {
        if (comp == null || !layer.isAudioClip())
        {
            return false;
        }

        mchorse.bbs_mod.catalyst.CatalystTrack track = comp.trackOf(layer);
        boolean solo = false;

        for (mchorse.bbs_mod.catalyst.CatalystTrack t : comp.audioTracks)
        {
            solo |= t.solo;
        }

        return track != null && (track.muted || (solo && !track.solo));
    }

    /** Whether a clip's track keeps it from being seen: a hidden video track, or not soloed while another is */
    private boolean isTrackHidden(CatalystComposition comp, CatalystLayer layer)
    {
        if (comp == null || layer.isAudioClip())
        {
            return false;
        }

        mchorse.bbs_mod.catalyst.CatalystTrack track = comp.trackOf(layer);
        boolean solo = false;

        for (mchorse.bbs_mod.catalyst.CatalystTrack t : comp.videoTracks)
        {
            solo |= t.solo;
        }

        return track != null && (track.muted || (solo && !track.solo));
    }

    /** How large the monitor draws the frame: fitted with a margin, or at the zoom's share of its pixels */
    public float previewScale(Area area, int compW, int compH)
    {
        if (monitorZoom > 0)
        {
            float gui = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();

            return monitorZoom / 100F / Math.max(gui, 0.01F);
        }

        int margin = 12;
        float scale = Math.min((float) (area.w - margin * 2) / compW, (float) (area.h - margin * 2) / compH);

        return scale <= 0.0001F ? 1F : scale;
    }

    private void setupMediaPoolPanel()
    {
        this.mediaPoolPanel = new UIMediaPoolPanel(
            this,
            () -> this.activeProject,
            (asset) -> this.dropAssetToTimeline(asset, this.currentFrame),
            (asset) -> this.draggedAsset = asset
        );
    }

    public void dropAssetToTimeline(CatalystMediaAsset asset, int startFrame)
    {
        CatalystComposition comp = this.activeProject != null ? this.activeProject.getActiveComposition() : null;
        if (comp == null || asset == null || this.aiBusy)
        {
            return;
        }

        /* A timeline dropped from the library plays on a video track as one clip */
        if (asset.type == CatalystMediaAsset.MediaType.TIMELINE)
        {
            this.addNestedClip(this.activeProject.getComposition(asset.path), Math.max(0, startFrame));

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
            int frames = asset.durationSeconds > 0
                ? Math.max(1, (int) Math.round(asset.durationSeconds * (comp.fps > 0 ? comp.fps : 60)))
                : (asset.durationFrames > 0 ? asset.durationFrames : 150);
            layer.duration = frames;
            layer.mediaDuration = layer.duration;
            layer.color = 0x3366BB;
        }
        else if (asset.type == CatalystMediaAsset.MediaType.AUDIO)
        {
            layer.layerType = CatalystLayer.LayerType.AUDIO;
            int frames = asset.durationSeconds > 0
                ? Math.max(1, (int) Math.round(asset.durationSeconds * (comp.fps > 0 ? comp.fps : 60)))
                : (asset.durationFrames > 0 ? asset.durationFrames : 150);
            layer.duration = frames;
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

        layer.track = this.catalystTimeline.freeTrack(comp, layer.isAudioClip(), layer.startFrame, layer.duration);
        comp.layers.add(layer);

        /* A video with sound: the sound goes onto the first free audio track as a clip of its own,
         * linked to the picture, and the video clip itself plays none */
        if (layer.layerType == CatalystLayer.LayerType.VIDEO && this.videoHasAudio(normPath))
        {
            CatalystLayer audio = new CatalystLayer();

            audio.layerType = CatalystLayer.LayerType.AUDIO;
            audio.name = asset.name;
            audio.resourcePath = normPath;
            audio.color = 0x228855;
            audio.startFrame = layer.startFrame;
            audio.duration = layer.duration;
            audio.mediaDuration = layer.mediaDuration;
            audio.linkId = layer.linkId = java.util.UUID.randomUUID().toString().substring(0, 8);
            audio.track = this.catalystTimeline.freeTrack(comp, true, audio.startFrame, audio.duration);
            layer.muteOwnAudio = true;

            comp.layers.add(audio);
            UICatalystTimeline.ensureWaveformLoaded(audio);
        }

        this.catalystTimeline.setSelected(layer);
        this.ensureDuration();
        this.saveAndRefresh();
        this.updateInspectorForm();
    }

    private boolean videoHasAudio(String path)
    {
        try
        {
            VideoPlayer player = BBSModClient.getVideos().get(Link.create(path));

            if (player != null)
            {
                player.ensureProbed();

                return player.isValid() && player.hasAudio();
            }
        }
        catch (Exception e)
        {}

        return false;
    }

    private void setupPreviewArea()
    {
        this.previewArea = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (aiBusy)
                {
                    if (context.mouseButton == 0 && context.mouseX >= aiStop[0] && context.mouseX < aiStop[2] && context.mouseY >= aiStop[1] && context.mouseY < aiStop[3])
                    {
                        cancelAi();
                    }

                    return this.area.isInside(context);
                }

                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    CatalystLayer sel = getSelectedLayer();
                    CatalystComposition comp = activeProject != null ? activeProject.getActiveComposition() : null;
                    if (sel != null && comp != null)
                    {
                        int compW = comp.width > 0 ? comp.width : 1920;
                        int compH = comp.height > 0 ? comp.height : 1080;
                        float scale = UICatalystPanel.this.previewScale(this.area, compW, compH);
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
                        int compW = comp.width > 0 ? comp.width : 1920;
                        int compH = comp.height > 0 ? comp.height : 1080;
                        float scale = UICatalystPanel.this.previewScale(this.area, compW, compH);

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
                    if (previewResolutionBtn != null) previewResolutionBtn.setVisible(false);
                    if (safeAreasToggleBtn != null) safeAreasToggleBtn.setVisible(false);
                    stepExport(context);
                }
                else
                {
                    if (previewResolutionBtn != null) previewResolutionBtn.setVisible(!UICatalystPanel.this.isFullscreen);
                    if (safeAreasToggleBtn != null) safeAreasToggleBtn.setVisible(!UICatalystPanel.this.isFullscreen);
                    tickPlayback(comp);
                }
                try
                {
                    renderPreviewCanvas(context, this.area, comp);
                }
                finally
                {
                    /* Whatever the film clips left in the GL state (they drive the world render), the
                     * rest of the interface is drawn in the menu's own */
                    context.batcher.flush();
                    mchorse.bbs_mod.utils.GuiState.restoreInMenu();
                }

                if (!isExporting)
                {
                    renderLoadingBadge(context, this.area, comp);
                    renderAiBadge(context, this.area);
                }

                super.render(context);
            }
        };
        this.renderMonitorHud.relative(this.previewArea).y(1F, -50).w(1F).h(50);
        this.renderMonitorHud.setVisible(false);
        this.previewArea.add(this.renderMonitorHud);

        this.safeAreasToggleBtn = new UIIcon(Icons.OUTLINE, (b) -> this.toggleSafeAreas())
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 1)
                {
                    context.replaceContextMenu((menu) ->
                    {
                        menu.action(showSafeAreas ? Icons.CHECKMARK : Icons.NONE,
                            UIKeys.CATALYST_NLE_SAFE_AREAS,
                            () -> toggleSafeAreas());
                    });
                    return true;
                }
                return super.subMouseClicked(context);
            }
        };
        this.safeAreasToggleBtn.tooltip(UIKeys.CATALYST_NLE_SAFE_AREAS);
        this.safeAreasToggleBtn.highlight(() -> showSafeAreas, Direction.BOTTOM);
        this.safeAreasToggleBtn.relative(this.previewArea).x(1F, -100).y(1F, -26).w(20).h(18);
        this.previewArea.add(this.safeAreasToggleBtn);

        this.previewResolutionBtn = new UIButton(IKey.raw("[" + previewResolution.title + "]"), (b) -> this.cyclePreviewResolution())
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 1)
                {
                    context.replaceContextMenu((menu) ->
                    {
                        for (PreviewResolution res : PreviewResolution.values())
                        {
                            menu.action(res == previewResolution ? Icons.CHECKMARK : Icons.NONE,
                                IKey.raw(res.title + " (" + res.fraction + ")"),
                                () -> setPreviewResolution(res));
                        }
                    });
                    return true;
                }
                return super.subMouseClicked(context);
            }
        };
        this.previewResolutionBtn.tooltip(UIKeys.CATALYST_NLE_PREVIEW_QUALITY);
        this.previewResolutionBtn.relative(this.previewArea).x(1F, -76).y(1F, -26).w(68).h(18);
        this.previewArea.add(this.previewResolutionBtn);

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
                    VideoPlayer player = BBSModClient.getVideos().getPlayer(this.videoOwner(layer), link);
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

        float scale = this.previewScale(area, compW, compH);
        float offsetX = area.x + (area.w - compW * scale) / 2.0F;
        float offsetY = area.y + (area.h - compH * scale) / 2.0F;

        int canvasX = (int) Math.floor(offsetX);
        int canvasY = (int) Math.floor(offsetY);
        int canvasW = (int) Math.ceil(compW * scale);
        int canvasH = (int) Math.ceil(compH * scale);
        Area canvasArea = new Area(canvasX, canvasY, canvasW, canvasH);

        boolean renderedViaFbo = false;
        if (previewResolution != PreviewResolution.FULL || this.compositionNeedsFx(comp, playhead))
        {
            float q = previewResolution.scale;
            int fboW = Math.max(16, ((int) (compW * q) / 2) * 2);
            int fboH = Math.max(16, ((int) (compH * q) / 2) * 2);

            Framebuffer fbo = this.getOrCreatePreviewFramebuffer(fboW, fboH);
            if (fbo != null)
            {
                context.batcher.flush();

                MinecraftClient mc = MinecraftClient.getInstance();
                net.minecraft.client.util.Window window = mc.getWindow();
                int guiW = window.getScaledWidth();
                int guiH = window.getScaledHeight();

                int prevFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                int[] prevViewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
                boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
                if (prevScissor)
                {
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                }

                try
                {
                    fbo.applyClear();
                    com.mojang.blaze3d.systems.RenderSystem.viewport(0, 0, fboW, fboH);
                    GL11.glColorMask(true, true, true, true);
                    com.mojang.blaze3d.systems.RenderSystem.clearColor(13F / 255F, 13F / 255F, 17F / 255F, 1.0F);
                    com.mojang.blaze3d.systems.RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, false);

                    MatrixStack fboStack = context.batcher.getContext().getMatrices();
                    fboStack.push();
                    context.resetMatrix();
                    fboStack.scale((float) guiW / (float) compW, (float) guiH / (float) compH, 1.0F);

                    long compStartNano = System.nanoTime();
                    this.renderCompositionLayers(context, comp, playhead, compW, compH, targetFps);
                    context.batcher.flush();
                    this.lastCompRenderMs = (System.nanoTime() - compStartNano) / 1_000_000.0;

                    fboStack.pop();
                    renderedViaFbo = true;
                }
                catch (Exception ignored)
                {
                    renderedViaFbo = false;
                }
                finally
                {
                    fbo.unbind();
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
                    com.mojang.blaze3d.systems.RenderSystem.viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
                    if (prevScissor)
                    {
                        GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    }
                }

                if (renderedViaFbo && this.previewFboTexture != null)
                {
                    context.batcher.clip(canvasArea, context);
                    context.batcher.texturedBox(this.previewFboTexture, Colors.WHITE, canvasX, canvasY, canvasW, canvasH, 0, fboH, fboW, 0, fboW, fboH);
                    context.batcher.unclip(context);
                }
            }
        }

        if (!renderedViaFbo)
        {
            context.batcher.clip(canvasArea, context);

            MatrixStack viewStack = context.batcher.getContext().getMatrices();
            viewStack.push();
            viewStack.translate(offsetX, offsetY, 0);
            viewStack.scale(scale, scale, 1.0F);

            /* Canvas surface in virtual composition space */
            context.batcher.box(0, 0, compW, compH, 0xFF0D0D11);

            long compStartNano = System.nanoTime();
            this.renderCompositionLayers(context, comp, playhead, compW, compH, targetFps);
            this.lastCompRenderMs = (System.nanoTime() - compStartNano) / 1_000_000.0;

            viewStack.pop();
            context.batcher.unclip(context);
        }

        /* ── Overlay: Grid and Bounding Box in Virtual Composition Space ── */
        MatrixStack overlayStack = context.batcher.getContext().getMatrices();
        overlayStack.push();
        overlayStack.translate(offsetX, offsetY, 0);
        overlayStack.scale(scale, scale, 1.0F);

        context.batcher.clip(canvasArea, context);
        context.batcher.outline(0, 0, compW, compH, 0xFF3E3E50, Math.max(1, Math.round(1.0F / scale)));

        if (showSafeAreas)
        {
            int lw = Math.max(2, Math.round(2.0F / scale));
            int shadow = Math.max(1, Math.round(1.0F / scale));
            int half = lw / 2;
            int shadowCol = 0x88000000;
            int gridCol = 0xAAFFFFFF;
            int actionCol = 0xCC00E5FF;
            int titleCol = 0xAA00E5FF;
            int crossCol = 0xCCFFFFFF;

            /* ── Rule of Thirds grid ── */
            int thirdW = compW / 3;
            int thirdH = compH / 3;

            /* Vertical lines at 1/3 and 2/3 */
            int[] vLines = new int[] {thirdW, thirdW * 2};
            for (int vx : vLines)
            {
                context.batcher.box(vx - half - shadow, 0, vx - half + lw + shadow, compH, shadowCol);
                context.batcher.box(vx - half,          0, vx - half + lw,          compH, gridCol);
            }

            /* Horizontal lines at 1/3 and 2/3 */
            int[] hLines = new int[] {thirdH, thirdH * 2};
            for (int hy : hLines)
            {
                context.batcher.box(0, hy - half - shadow, compW, hy - half + lw + shadow, shadowCol);
                context.batcher.box(0, hy - half,          compW, hy - half + lw,          gridCol);
            }

            /* ── Action Safe Area (90% - 5% border) ── */
            int actionMarginX = (int) (compW * 0.05F);
            int actionMarginY = (int) (compH * 0.05F);
            int actionMaxX = compW - actionMarginX;
            int actionMaxY = compH - actionMarginY;
            context.batcher.outline(actionMarginX - shadow, actionMarginY - shadow, actionMaxX + shadow, actionMaxY + shadow, shadowCol, lw + shadow * 2);
            context.batcher.outline(actionMarginX, actionMarginY, actionMaxX, actionMaxY, actionCol, lw);

            /* ── Title Safe Area (80% - 10% border) ── */
            int titleMarginX = (int) (compW * 0.10F);
            int titleMarginY = (int) (compH * 0.10F);
            int titleMaxX = compW - titleMarginX;
            int titleMaxY = compH - titleMarginY;
            context.batcher.outline(titleMarginX - shadow, titleMarginY - shadow, titleMaxX + shadow, titleMaxY + shadow, shadowCol, lw + shadow * 2);
            context.batcher.outline(titleMarginX, titleMarginY, titleMaxX, titleMaxY, titleCol, lw);

            /* ── Center Crosshair ── */
            int cx = compW / 2;
            int cy = compH / 2;
            int arm = Math.max(10, Math.round(16 / scale));
            /* Shadow */
            context.batcher.box(cx - arm - shadow, cy - half - shadow, cx + arm + shadow, cy - half + lw + shadow, shadowCol);
            context.batcher.box(cx - half - shadow, cy - arm - shadow, cx - half + lw + shadow, cy + arm + shadow, shadowCol);
            /* Main cross */
            context.batcher.box(cx - arm, cy - half, cx + arm, cy - half + lw, crossCol);
            context.batcher.box(cx - half, cy - arm, cx - half + lw, cy + arm, crossCol);
        }

        context.batcher.unclip(context);

        CatalystLayer selectedLayer = this.getSelectedLayer();
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

        overlayStack.pop();

        context.batcher.box(area.x, area.ey() - 1, area.ex(), area.ey(), BBSSettings.dividerColor());
    }

    /* ════════════════════════════════════════════════════════
     *  CLIP EFFECTS, BLEND MODES, ADJUSTMENT CLIPS
     * ════════════════════════════════════════════════════════ */

    private static final String[] BLEND_MODES = {"NORMAL", "ADD", "MULTIPLY", "SCREEN", "OVERLAY"};

    /** Scratch buffers at the composition's size: the clip drawn alone, its effects' ping-pong, a copy of what is under it */
    private final mchorse.bbs_mod.graphics.Framebuffer[] fxFbos = new mchorse.bbs_mod.graphics.Framebuffer[3];
    private final Texture[] fxTextures = new Texture[3];
    private int fxTarget;
    private final int[] fxViewport = new int[4];
    private boolean fxScissor;

    private static boolean hasActiveEffects(CatalystLayer layer)
    {
        for (mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer effect : layer.effects.getAllTyped())
        {
            if (effect.enabled.get() && (effect.intensity.get() > 0.001F || !effect.intensityChannel.isEmpty()) && mchorse.bbs_mod.camera.clips.fx.PostEffect.get(effect.effect.get()) != null)
            {
                return true;
            }
        }

        return false;
    }

    private static boolean isTextShaped(CatalystLayer layer)
    {
        return layer.layerType == CatalystLayer.LayerType.TEXT
            && (layer.flipH || layer.flipV || layer.cropLeft > 0F || layer.cropRight > 0F || layer.cropTop > 0F || layer.cropBottom > 0F);
    }

    /** Whether a clip is drawn on its own and laid over: effects, a blend mode other than normal, or a cropped or flipped text */
    private static boolean needsLayerPass(CatalystLayer layer)
    {
        if (layer.layerType == CatalystLayer.LayerType.ADJUSTMENT || layer.layerType == CatalystLayer.LayerType.NULL || layer.layerType == CatalystLayer.LayerType.NESTED || layer.isAudioClip())
        {
            return false;
        }

        return hasActiveEffects(layer) || !"NORMAL".equalsIgnoreCase(layer.blendMode) || isTextShaped(layer);
    }

    /** Whether the frame at the playhead needs the buffers: with none of it the monitor draws straight to the screen */
    public boolean compositionNeedsFx(CatalystComposition comp, int playhead)
    {
        if (comp == null || mchorse.bbs_mod.client.BBSShaders.getPostFxLibraryProgram() == null)
        {
            return false;
        }

        for (CatalystLayer layer : comp.layers)
        {
            if (layer.visible && playhead >= layer.startFrame && playhead < layer.startFrame + layer.duration
                && (needsLayerPass(layer) || (layer.layerType == CatalystLayer.LayerType.ADJUSTMENT && hasActiveEffects(layer))))
            {
                return true;
            }

            /* A nested timeline needs the buffers when something inside it does */
            if (layer.layerType == CatalystLayer.LayerType.NESTED && layer.visible && this.activeProject != null && this.nestDepth < 6
                && playhead >= layer.startFrame && playhead < layer.startFrame + layer.duration)
            {
                CatalystComposition child = this.activeProject.getComposition(layer.resourcePath);

                if (child != null)
                {
                    this.nestDepth += 1;

                    try
                    {
                        if (this.compositionNeedsFx(child, this.nestedFrame(layer, child, playhead, comp.fps)))
                        {
                            return true;
                        }
                    }
                    finally
                    {
                        this.nestDepth -= 1;
                    }
                }
            }
        }

        return false;
    }

    private mchorse.bbs_mod.graphics.Framebuffer fxBuffer(int index, int w, int h)
    {
        if (this.fxTextures[index] == null)
        {
            this.fxTextures[index] = new Texture();
            this.fxTextures[index].setFormat(TextureFormat.RGBA_U8);
            this.fxTextures[index].setFilter(GL11.GL_LINEAR);
        }

        Texture texture = this.fxTextures[index];

        if (this.fxFbos[index] == null || texture.width != w || texture.height != h)
        {
            if (this.fxFbos[index] != null)
            {
                this.fxFbos[index].delete();
            }

            texture.bind();
            texture.setSize(w, h);
            texture.setFilter(GL11.GL_LINEAR);
            texture.unbind();

            this.fxFbos[index] = new mchorse.bbs_mod.graphics.Framebuffer();
            this.fxFbos[index].attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            this.fxFbos[index].attachments(1);
            this.fxFbos[index].unbind();
        }

        return this.fxFbos[index];
    }

    /** Frees the scratch buffers (the panel going away) */
    private void deleteFxBuffers()
    {
        for (int i = 0; i < this.fxFbos.length; i++)
        {
            if (this.fxFbos[i] != null) this.fxFbos[i].delete();
            if (this.fxTextures[i] != null) this.fxTextures[i].delete();

            this.fxFbos[i] = null;
            this.fxTextures[i] = null;
        }
    }

    /** The buffer being drawn into and its size; false when it is the screen, which the passes don't work on */
    private boolean captureTarget()
    {
        this.fxTarget = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.fxViewport);

        /* Blits and the passes are cut by the scissor test: off while they run */
        this.fxScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        return this.fxTarget != 0 && this.fxViewport[2] > 0 && this.fxViewport[3] > 0;
    }

    private void restoreTarget()
    {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fxTarget);
        com.mojang.blaze3d.systems.RenderSystem.viewport(this.fxViewport[0], this.fxViewport[1], this.fxViewport[2], this.fxViewport[3]);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        if (this.fxScissor)
        {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    /**
     * Runs a clip's effects over a buffer, ping-ponging between the scratch buffers from
     * {@code source}; the last one is drawn into {@code finalTarget} when given. Returns the index
     * of the buffer holding the result, or -1 when it went into {@code finalTarget}.
     */
    private int runEffects(CatalystLayer layer, int source, int finalTarget, float scale, int playhead, int fps)
    {
        int w = this.fxViewport[2];
        int h = this.fxViewport[3];
        float seconds = (playhead - layer.startFrame) / (float) Math.max(1, fps);
        List<mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer> active = new ArrayList<>();

        for (mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer effect : layer.effects.getAllTyped())
        {
            if (effect.enabled.get() && effect.value(-1, playhead) > 0.001D && mchorse.bbs_mod.camera.clips.fx.PostEffect.get(effect.effect.get()) != null)
            {
                active.add(effect);
            }
        }

        int current = source;

        for (int i = 0; i < active.size(); i++)
        {
            mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer effect = active.get(i);
            mchorse.bbs_mod.camera.clips.fx.PostEffect definition = mchorse.bbs_mod.camera.clips.fx.PostEffect.get(effect.effect.get());
            boolean last = i == active.size() - 1 && finalTarget != 0;
            int next = current == 0 ? 1 : 0;
            int target = last ? finalTarget : this.fxBuffer(next, w, h).id;
            int color = definition.hasColor() ? effect.color.get() : 0xffffff;

            mchorse.bbs_mod.ui.film.PostFxRenderer.drawLibrary(target, w, h, this.fxTextures[current].id, this.fxTextures[current].id,
                definition.mode, Math.min(1F, (float) effect.value(-1, playhead) * scale),
                (float) effect.value(0, playhead), (float) effect.value(1, playhead), (float) effect.value(2, playhead), (float) effect.value(3, playhead),
                ((color >> 16) & 0xff) / 255F, ((color >> 8) & 0xff) / 255F, (color & 0xff) / 255F, seconds, 0F, 0F);

            current = last ? -1 : next;
        }

        return current;
    }

    /** An adjustment clip: its effects over a copy of what is under it, written back */
    private void applyAdjustment(UIContext context, CatalystLayer layer, float alpha, int playhead, int fps)
    {
        if (!hasActiveEffects(layer))
        {
            return;
        }

        context.batcher.flush();

        if (!this.captureTarget())
        {
            return;
        }

        int w = this.fxViewport[2];
        int h = this.fxViewport[3];

        try
        {
            mchorse.bbs_mod.graphics.Framebuffer copy = this.fxBuffer(0, w, h);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.fxTarget);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.id);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            this.runEffects(layer, 0, this.fxTarget, alpha, playhead, fps);
        }
        finally
        {
            this.restoreTarget();
        }
    }

    /** Before a clip that is drawn on its own: into a clear buffer of the composition's size */
    private boolean beginLayerPass(UIContext context, CatalystLayer layer)
    {
        if (!needsLayerPass(layer) || mchorse.bbs_mod.client.BBSShaders.getPostFxLibraryProgram() == null)
        {
            return false;
        }

        context.batcher.flush();

        if (!this.captureTarget())
        {
            return false;
        }

        mchorse.bbs_mod.graphics.Framebuffer alone = this.fxBuffer(0, this.fxViewport[2], this.fxViewport[3]);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, alone.id);
        com.mojang.blaze3d.systems.RenderSystem.viewport(0, 0, this.fxViewport[2], this.fxViewport[3]);
        com.mojang.blaze3d.systems.RenderSystem.clearColor(0F, 0F, 0F, 0F);
        com.mojang.blaze3d.systems.RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, false);

        /* Premultiplied: the colour times its alpha, and the alpha itself */
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        return true;
    }

    /** After it: its effects, then laid over the composition with its blend mode, its crop and (text) flips */
    private void endLayerPass(UIContext context, CatalystLayer layer, float lx, float ly, float layerW, float layerH, int compW, int compH, int playhead, int fps)
    {
        context.batcher.flush();

        int w = this.fxViewport[2];
        int h = this.fxViewport[3];

        try
        {
            int result = this.runEffects(layer, 0, 0, 1F, playhead, fps);

            /* What is under the clip, for the blend */
            mchorse.bbs_mod.graphics.Framebuffer under = this.fxBuffer(2, w, h);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.fxTarget);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, under.id);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            /* The text's crop and flips, in the buffer's uv (y up); the rest crop through their texture */
            float u1 = 0F, v1 = 0F, u2 = 1F, v2 = 1F, flipH = 0F, flipV = 0F;
            float cu = (lx + layerW / 2F) / compW;
            float cv = 1F - (ly + layerH / 2F) / compH;

            if (layer.layerType == CatalystLayer.LayerType.TEXT)
            {
                float x1 = lx + layerW * layer.cropLeft;
                float x2 = lx + layerW * (1F - layer.cropRight);
                float y1 = ly + layerH * layer.cropTop;
                float y2 = ly + layerH * (1F - layer.cropBottom);
                boolean cropped = layer.cropLeft > 0F || layer.cropRight > 0F || layer.cropTop > 0F || layer.cropBottom > 0F;

                if (cropped)
                {
                    u1 = Math.min(x1, x2) / compW;
                    u2 = Math.max(x1, x2) / compW;
                    v1 = 1F - Math.max(y1, y2) / compH;
                    v2 = 1F - Math.min(y1, y2) / compH;
                }

                flipH = layer.flipH ? 1F : 0F;
                flipV = layer.flipV ? 1F : 0F;
            }

            int mode = 0;

            for (int i = 0; i < BLEND_MODES.length; i++)
            {
                if (BLEND_MODES[i].equalsIgnoreCase(layer.blendMode))
                {
                    mode = i;
                }
            }

            mchorse.bbs_mod.ui.film.PostFxRenderer.drawLibrary(this.fxTarget, w, h, this.fxTextures[2].id, this.fxTextures[result].id,
                100 + mode, 1F, u1, v1, u2, v2, flipH, flipV, 0F, 0F, cu, cv);
        }
        finally
        {
            this.restoreTarget();
        }
    }

    /** A clip whose file is gone: a dark red frame with a warning triangle and MEDIA OFFLINE, never a crash */
    private void renderOffline(UIContext context, float lx, float ly, float layerW, float layerH, float alpha)
    {
        int x1 = (int) Math.min(lx, lx + layerW);
        int x2 = (int) Math.max(lx, lx + layerW);
        int y1 = (int) Math.min(ly, ly + layerH);
        int y2 = (int) Math.max(ly, ly + layerH);
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int size = Math.max(12, Math.min(x2 - x1, y2 - y1) / 5);
        int red = Colors.setA(0xe81123, alpha);

        context.batcher.box(x1, y1, x2, y2, Colors.setA(0x2a0a0e, alpha));
        context.batcher.outline(x1, y1, x2, y2, red, Math.max(2, size / 12));

        /* The triangle, a row at a time, with its exclamation mark cut out in the dark */
        int top = cy - size;

        for (int row = 0; row < size; row += 2)
        {
            int half = row / 2 + 1;

            context.batcher.box(cx - half, top + row, cx + half, top + row + 2, red);
        }

        int bar = Math.max(2, size / 10);

        context.batcher.box(cx - bar / 2, top + size * 2 / 5, cx + bar / 2 + 1, top + size * 3 / 4, Colors.setA(0x2a0a0e, alpha));
        context.batcher.box(cx - bar / 2, top + size * 4 / 5, cx + bar / 2 + 1, top + size * 9 / 10, Colors.setA(0x2a0a0e, alpha));

        FontRenderer font = context.batcher.getFont();
        String text = UIKeys.CATALYST_NLE_MEDIA_OFFLINE.get();
        float scale = Math.max(1F, size / 12F);
        MatrixStack textStack = context.batcher.getContext().getMatrices();

        textStack.push();
        textStack.translate(cx - font.getWidth(text) * scale / 2F, cy + size * 0.2F, 0);
        textStack.scale(scale, scale, 1F);
        context.batcher.text(text, 0, 0, Colors.setA(0xff8a8a, alpha), false);
        textStack.pop();
    }

    /** Whether a film clip's render failure was logged already: once a session, not every frame */
    private static boolean filmErrorLogged;

    /** The clips that draw a picture, lowest video track first; on one track, later clips over earlier ones */
    private List<CatalystLayer> drawOrder(CatalystComposition comp)
    {
        List<CatalystLayer> order = new ArrayList<>();

        for (CatalystLayer layer : comp.layers)
        {
            if (!layer.isAudioClip())
            {
                order.add(layer);
            }
        }

        order.sort(java.util.Comparator.comparingInt((CatalystLayer layer) -> layer.track));

        return order;
    }

    /**
     * A picture with the clip's crop and flips: the crop cuts the drawn rectangle and the texture
     * alike, as fractions of each side, and a flip mirrors which part of the texture lands where.
     * {@code flippedSource} is for textures stored upside down (the world's framebuffer).
     */
    private void drawCropped(UIContext context, int texture, int texW, int texH, int color, CatalystLayer layer, float x, float y, float w, float h, boolean flippedSource)
    {
        float cl = Math.max(0F, layer.cropLeft);
        float cr = Math.max(0F, layer.cropRight);
        float ct = Math.max(0F, layer.cropTop);
        float cb = Math.max(0F, layer.cropBottom);

        if (cl + cr >= 0.999F || ct + cb >= 0.999F)
        {
            return;
        }

        float x1 = x + w * cl;
        float x2 = x + w * (1F - cr);
        float y1 = y + h * ct;
        float y2 = y + h * (1F - cb);

        /* Where a fraction across the drawn picture reads the texture */
        float uFrom = layer.flipH ? 1F - cl : cl;
        float uTo = layer.flipH ? cr : 1F - cr;
        boolean invertV = flippedSource != layer.flipV;
        float vFrom = invertV ? 1F - ct : ct;
        float vTo = invertV ? cb : 1F - cb;

        context.batcher.texturedBox(texture, color, x1, y1, x2 - x1, y2 - y1, uFrom * texW, vFrom * texH, uTo * texW, vTo * texH, texW, texH);
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

        for (CatalystLayer layer : this.drawOrder(comp))
        {
            if (hasSolo && !layer.solo) continue;
            if (!layer.visible) continue;
            if (this.isTrackHidden(comp, layer)) continue;
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

            float alpha = Math.max(0F, Math.min(1F, effOpacity / 100.0F)) * this.nestAlpha;
            int sinceStart = playhead - layer.startFrame;
            float enter = !layer.transitionIn.isEmpty() && sinceStart < layer.transitionFrames
                ? Math.min(1F, (sinceStart + 1F) / Math.max(1, layer.transitionFrames)) : 1F;

            if ("cross_dissolve".equals(layer.transitionIn))
            {
                alpha *= enter;
            }
            else if ("slide".equals(layer.transitionIn))
            {
                effPosX += compW * (1F - enter) * (1F - enter);
            }

            if (alpha <= 0.001F) continue;

            /* An adjustment clip: its effects go over everything drawn under it so far */
            if (layer.layerType == CatalystLayer.LayerType.ADJUSTMENT)
            {
                this.applyAdjustment(context, layer, alpha, playhead, targetFps);
                continue;
            }

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

            /* Effects, a blend mode or a text crop: the clip is drawn on its own first */
            boolean layerPass = this.beginLayerPass(context, layer);

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

            if (mchorse.bbs_mod.ui.dashboard.panels.catalyst.CatalystMedia.isOffline(layer))
            {
                this.renderOffline(context, lx, ly, layerW, layerH, alpha);
            }
            else if (layer.layerType == CatalystLayer.LayerType.ADJUSTMENT || layer.layerType == CatalystLayer.LayerType.NULL)
            {
                /* Nothing of its own to draw */
            }
            else if (layer.layerType == CatalystLayer.LayerType.NESTED)
            {
                this.renderNested(context, layer, lx, ly, layerW, layerH, compW, compH, playhead, targetFps, alpha);
            }
            else if (layer.layerType == CatalystLayer.LayerType.SOLID)
            {
                float cx1 = lx + layerW * layer.cropLeft;
                float cx2 = lx + layerW * (1F - layer.cropRight);
                float cy1 = ly + layerH * layer.cropTop;
                float cy2 = ly + layerH * (1F - layer.cropBottom);

                context.batcher.box((int) Math.min(cx1, cx2), (int) Math.min(cy1, cy2), (int) Math.max(cx1, cx2), (int) Math.max(cy1, cy2), renderColor);
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
                            this.drawCropped(context, texture.id, texture.width, texture.height, imgColor, layer, lx, ly, layerW, layerH, false);
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
                        VideoPlayer player = BBSModClient.getVideos().getPlayer(this.videoOwner(layer), link);
                        if (player != null)
                        {
                            player.setMaxSize(compW, compH);
                            int relativeFrame = (playhead - layer.startFrame) + layer.mediaOffset;
                            float relSec = (float) (relativeFrame + layer.audioOffset) / Math.max(1, targetFps);
                            float playerFps = player.getFps() > 0 ? player.getFps() : 30F;
                            int videoFrameIdx = Math.max(0, (int) (relSec * playerFps));

                            /* An export wants the exact frame and waits for it; the monitor never waits:
                             * playing, paused or scrubbing, it shows the closest frame the player's
                             * prefetch worker has decoded and lets the worker catch up or jump */
                            Texture frameTex = this.isExporting ? player.getFrame(relSec) : player.getFramePrefetched(relSec);

                            if (frameTex != null && frameTex.isValid())
                            {
                                layer.cachedVideoTexture = frameTex;
                                layer.lastVideoFrameIndex = videoFrameIdx;
                            }
                            else if (layer.cachedVideoTexture instanceof Texture && ((Texture) layer.cachedVideoTexture).isValid())
                            {
                                frameTex = (Texture) layer.cachedVideoTexture;
                            }

                            if (frameTex != null && frameTex.isValid())
                            {
                                int videoColor = Colors.setA(0xFFFFFFFF, alpha);
                                this.drawCropped(context, frameTex.id, frameTex.width, frameTex.height, videoColor, layer, lx, ly, layerW, layerH, false);
                                rendered = true;
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        BBSMod.LOGGER.error("Error rendering video layer: " + layer.resourcePath, e);
                    }
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

                            /* The world as the film camera saw it: the live framebuffer, the way the film
                             * editor's preview reads it. BBSRendering.getTexture() is only filled while a
                             * video is exported, so outside of that it held nothing to show */
                            int filmColor = Colors.setA(0xFFFFFFFF, alpha);
                            net.minecraft.client.gl.Framebuffer world = BBSRendering.getFramebuffer();
                            Texture texture = BBSRendering.getTexture();

                            if (!this.isExporting && world != null && world.getColorAttachment() > 0 && world.textureWidth > 0)
                            {
                                this.drawCropped(context, world.getColorAttachment(), world.textureWidth, world.textureHeight, filmColor, layer, lx, ly, layerW, layerH, true);
                                filmRendered = true;
                            }
                            else if (texture != null && texture.isValid() && texture.width > 0 && texture.height > 0)
                            {
                                this.drawCropped(context, texture.id, texture.width, texture.height, filmColor, layer, lx, ly, layerW, layerH, true);
                                filmRendered = true;
                            }
                        }
                    }
                    catch (Throwable e)
                    {
                        /* A film that fails to draw must not take the interface with it: once in the log, then a placeholder */
                        if (!filmErrorLogged)
                        {
                            filmErrorLogged = true;
                            BBSMod.LOGGER.error("[Catalyst] Film clip '" + layer.resourcePath + "' failed to render; it shows a placeholder until fixed", e);
                        }
                    }
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

            if (layerPass)
            {
                this.endLayerPass(context, layer, lx, ly, layerW, layerH, compW, compH, playhead, targetFps);
            }

            if ("dip_to_black".equals(layer.transitionIn) && enter < 1F)
            {
                context.batcher.box(0, 0, compW, compH, Colors.setA(0, 1F - enter));
            }

            layer.lastRenderMs = (System.nanoTime() - layerStartNano) / 1_000_000.0;
        }
    }


    public void applyPropValue(CatalystLayer layer, int propBit, int componentIndex, float val)
    {
        if (layer == null) return;

        if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_POSITION)
        {
            float v1 = (componentIndex == 0) ? val : layer.posX;
            float v2 = (componentIndex == 1) ? val : layer.posY;
            layer.updatePropertyValue(propBit, this.currentFrame, v1, v2);
        }
        else if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_SCALE)
        {
            float v1 = (componentIndex == 0) ? Math.max(0.01F, val) : layer.scaleX;
            float v2 = (componentIndex == 1) ? Math.max(0.01F, val) : layer.scaleY;
            layer.updatePropertyValue(propBit, this.currentFrame, v1, v2);
        }
        else if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ROTATION)
        {
            layer.updatePropertyValue(propBit, this.currentFrame, val % 360F, 0F);
        }
        else if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_OPACITY)
        {
            float v = Math.max(0F, Math.min(100F, val));
            layer.updatePropertyValue(propBit, this.currentFrame, v, 0F);
        }
        else if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_ANCHOR)
        {
            float v1 = (componentIndex == 0) ? Math.max(0F, Math.min(1F, val)) : layer.anchorX;
            float v2 = (componentIndex == 1) ? Math.max(0F, Math.min(1F, val)) : layer.anchorY;
            layer.updatePropertyValue(propBit, this.currentFrame, v1, v2);
        }
        else if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_VOLUME)
        {
            float v = val;
            if (v > 2.0F && v <= 100.0F) v = v / 100.0F;
            v = Math.max(0F, Math.min(2.0F, v));
            layer.updatePropertyValue(propBit, this.currentFrame, v, 0F);
        }
        else if (propBit == mchorse.bbs_mod.ui.dashboard.panels.catalyst.UICatalystTimeline.PROP_PAN)
        {
            float v = Math.max(-1.0F, Math.min(1.0F, val));
            layer.updatePropertyValue(propBit, this.currentFrame, v, 0F);
        }
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

                if (layer.resourcePath != null && PATH_OWNERS.containsKey(layer.resourcePath.trim()))
                {
                    BBSModClient.getVideos().release(PATH_OWNERS.get(layer.resourcePath.trim()));
                }
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

    public void cycleBlendMode(CatalystLayer layer)
    {
        if (layer == null) return;

        this.pushUndo();
        String[] modes = new String[] {"NORMAL", "MULTIPLY", "SCREEN", "ADD", "OVERLAY"};
        int idx = 0;
        for (int i = 0; i < modes.length; i++)
        {
            if (modes[i].equalsIgnoreCase(layer.blendMode))
            {
                idx = (i + 1) % modes.length;
                break;
            }
        }
        layer.blendMode = modes[idx];
        this.updateInspectorForm();
        this.saveAndRefresh();
    }

    private void cycleBlendMode()
    {
        this.cycleBlendMode(this.getSelectedLayer());
    }

    private String getBlendModeShort(String mode)
    {
        if (mode == null) return "NORM";
        switch (mode.toUpperCase())
        {
            case "MULTIPLY": return "MULT";
            case "SCREEN":   return "SCRN";
            case "ADD":      return "ADD";
            case "OVERLAY":  return "OVER";
            default:         return "NORM";
        }
    }

    public void updateInspectorForm()
    {
        if (this.inspector != null)
        {
            this.inspector.refresh();
        }

        this.refreshKeyEditor();
    }

    public void openCompositionSettingsModal()
    {
        if (this.activeProject == null) return;
        CatalystComposition comp = this.activeProject.getActiveComposition();
        if (comp == null) return;

        UIOverlayPanel modal = new UIOverlayPanel(UIKeys.CATALYST_NLE_TIMELINE_SETTINGS);

        UITextbox modalNameInput = new UITextbox(120, (t) -> {});
        modalNameInput.setText(comp.name);
        modalNameInput.h(20);

        UITrackpad modalFpsInput = new UITrackpad((v) -> {});
        modalFpsInput.limit(1, 240, true).setValue(comp.fps > 0 ? comp.fps : 60);
        modalFpsInput.h(20);

        UITrackpad modalWidthInput = new UITrackpad((v) -> {});
        modalWidthInput.limit(128, 7680, true).setValue(comp.width > 0 ? comp.width : 1920);
        modalWidthInput.h(20);

        UITrackpad modalHeightInput = new UITrackpad((v) -> {});
        modalHeightInput.limit(128, 4320, true).setValue(comp.height > 0 ? comp.height : 1080);
        modalHeightInput.h(20);

        UIButton applyCloseBtn = new UIButton(UIKeys.CATALYST_NLE_APPLY, (b) ->
        {
            this.pushUndo();
            String n = modalNameInput.getText().trim();
            if (!n.isEmpty()) comp.name = n;
            comp.fps = (int) modalFpsInput.getValue();
            comp.width = (int) modalWidthInput.getValue();
            comp.height = (int) modalHeightInput.getValue();
            comp.refreshDuration();

            this.activeProject.fps = comp.fps;
            this.activeProject.width = comp.width;
            this.activeProject.height = comp.height;

            this.saveAndRefresh();
            this.rebuildCompTabs();
            this.updateTitleLabel();
            modal.close.clickItself();
        });
        applyCloseBtn.h(20);

        UIButton cancelBtn = new UIButton(UIKeys.SANDBOX_CANCEL, (b) -> modal.close.clickItself());
        cancelBtn.h(20);

        UIElement container = new UIElement();
        container.relative(modal.content).xy(10, 6).w(1F, -20).hTo(modal.content.area, 1F, -6);
        container.column(6).vertical().stretch();
        container.add(
            UI.label(UIKeys.CATALYST_NLE_SETTINGS_NAME).h(14),
            modalNameInput,
            UI.label(UIKeys.CATALYST_NLE_SETTINGS_FPS).h(14),
            modalFpsInput,
            UI.label(UIKeys.CATALYST_NLE_SETTINGS_RESOLUTION).h(14),
            UI.row(modalWidthInput, modalHeightInput).h(20),
            UI.row(applyCloseBtn, cancelBtn).h(20)
        );

        modal.content.add(container);
        UIOverlay.addOverlay(this.getContext(), modal, 340, 214);
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

        if (this.isFullscreen && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.toggleFullscreen();
            return true;
        }

        if (this.aiBusy)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.cancelAi();
            }

            return true;
        }

        if (context.isFocused() || this.currentTab != CatalystTab.EDITOR || this.activeProject == null)
        {
            return super.subKeyPressed(context);
        }

        boolean ctrl = Window.isCtrlPressed();

        if (context.isPressed(GLFW.GLFW_KEY_SPACE))
        {
            this.togglePlayback();
            return true;
        }

        if (ctrl && context.isPressed(GLFW.GLFW_KEY_Z))
        {
            if (Window.isShiftPressed()) this.redo();
            else this.undo();

            return true;
        }

        if (ctrl && context.isPressed(GLFW.GLFW_KEY_Y))
        {
            this.redo();
            return true;
        }

        if (ctrl && context.isPressed(GLFW.GLFW_KEY_S))
        {
            this.ensureDuration();
            this.saveAndRefresh();
            this.dirtyAt = 0L;
            this.savedAt = System.currentTimeMillis();

            return true;
        }

        if (ctrl && context.isPressed(GLFW.GLFW_KEY_D))
        {
            this.catalystTimeline.duplicateSelected();
            return true;
        }

        if (ctrl && context.isPressed(GLFW.GLFW_KEY_B))
        {
            this.cutSelectedClip();
            return true;
        }

        if (ctrl)
        {
            return super.subKeyPressed(context);
        }

        if (context.isPressed(GLFW.GLFW_KEY_LEFT) || context.isPressed(GLFW.GLFW_KEY_RIGHT))
        {
            int step = Window.isShiftPressed() ? 10 : 1;
            int direction = context.isPressed(GLFW.GLFW_KEY_LEFT) ? -1 : 1;

            this.seekAndStop(Math.max(0, this.currentFrame + step * direction));
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_UP) || context.isPressed(GLFW.GLFW_KEY_DOWN))
        {
            this.catalystTimeline.jumpToEdge(context.isPressed(GLFW.GLFW_KEY_UP) ? -1 : 1);
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_HOME))
        {
            this.seekAndStop(0);
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_END))
        {
            this.seekAndStop(this.timelineEnd());
            return true;
        }

        /* The edit tools: V select, T trim, C blade, S snapping, M marker */
        if (context.isPressed(GLFW.GLFW_KEY_V))
        {
            this.catalystTimeline.tool = UINleTimeline.Tool.SELECT;
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_T))
        {
            this.catalystTimeline.tool = UINleTimeline.Tool.TRIM;
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_C))
        {
            this.catalystTimeline.tool = UINleTimeline.Tool.BLADE;
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_S))
        {
            this.catalystTimeline.snapping = !this.catalystTimeline.snapping;
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_M))
        {
            this.catalystTimeline.addMarkerAtPlayhead();
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_DELETE) || context.isPressed(GLFW.GLFW_KEY_BACKSPACE))
        {
            /* A gap between clips that is selected: closed, the clips after it glide left */
            if (this.catalystTimeline.closeSelectedGap())
            {
                return true;
            }

            /* Shift: a ripple delete, the rest of the track closes the gap */
            this.catalystTimeline.deleteSelected(Window.isShiftPressed());
            return true;
        }

        /* The ripple trims at the playhead: Q cuts away what is before it, W what is after */
        if (context.isPressed(GLFW.GLFW_KEY_Q))
        {
            this.catalystTimeline.rippleTrim(true);
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_W))
        {
            this.catalystTimeline.rippleTrim(false);
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_G))
        {
            this.openGainDialog();
            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_B))
        {
            this.toggleMediaPool();
            return true;
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
            this.captureProjectThumbnail(this.activeProject);
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
        this.exportLayout = true;
        this.topBar.setVisible(false);
        this.editorActions.setVisible(false);
        this.projectsView.setVisible(false);
        this.editor.setVisible(false);

        this.renderMonitorHud.setVisible(true);
        this.layoutPreview(true);
        this.previewArea.resize();
        this.resize();
    }

    public void exitExportMode()
    {
        this.exportLayout = false;
        this.renderMonitorHud.setVisible(false);
        this.topBar.setVisible(true);
        this.editorActions.setVisible(true);
        this.projectsView.setVisible(false);

        this.updateVisibility();
        this.updateEditorLayout();
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
        UIOverlay.addOverlay(this.getContext(), deliver, 720, 480);
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

    private void dumpFFmpegLog(File logFile, int exitCode)
    {
        String errDetail = "";
        System.err.println("[Catalyst-FFmpeg] FFmpeg process terminated unexpectedly (exit code: " + exitCode + ")!");
        if (logFile != null && logFile.exists())
        {
            try
            {
                List<String> logLines = Files.readAllLines(logFile.toPath());
                System.err.println("[FFmpeg-Export-Stderr] --- FFmpeg Log Dump (" + logLines.size() + " lines) ---");
                for (String line : logLines)
                {
                    System.err.println("  [FFmpeg-Export-Stderr] " + line);
                    String lower = line.toLowerCase();
                    if (lower.contains("error") || lower.contains("unknown") || lower.contains("failed") || lower.contains("invalid") || lower.contains("cannot") || lower.contains("unable to"))
                    {
                        errDetail = line.trim();
                    }
                }
                System.err.println("[FFmpeg-Export-Stderr] --- End FFmpeg Log Dump ---");
                if (errDetail.isEmpty() && !logLines.isEmpty())
                {
                    errDetail = logLines.get(logLines.size() - 1).trim();
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
        String failReason = exitCode + (errDetail.isEmpty() ? "" : " / " + errDetail);
        mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Catalyst] FFmpeg Hatası: " + failReason);
        mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Catalyst] Render durduruldu: FFmpeg süreci kapandı (kod " + exitCode + ")");
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

        /* Ensure parent directory exists with fail-fast check */
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists())
        {
            if (!parentDir.mkdirs() && !parentDir.exists())
            {
                System.err.println("[Catalyst] Failed to create output directory: " + parentDir.getAbsolutePath());
                mchorse.bbs_mod.utils.VideoRecorder.sendChatMessage("§c[Catalyst] Dışa aktarma klasörü oluşturulamadı: " + parentDir.getAbsolutePath());
                VideoPlayer.forcedRecording = false;
                if (job != null) job.setStatus(RenderJob.Status.CANCELLED);
                return;
            }
        }

        /* 1. Synchronous Video Decoding Flag for VideoPlayer */
        VideoPlayer.forcedRecording = true;

        /* Pre-warm video layers for frame 0 so the first frame is guaranteed ready */
        for (CatalystLayer layer : comp.layers)
        {
            if (layer.layerType == CatalystLayer.LayerType.VIDEO && layer.resourcePath != null && !layer.resourcePath.trim().isEmpty())
            {
                try
                {
                    Link link = Link.create(layer.resourcePath.trim());
                    VideoPlayer player = BBSModClient.getVideos().getPlayer(this.videoOwner(layer), link);
                    if (player != null)
                    {
                        player.setMaxSize(width, height);
                        int relativeFrame = (0 - layer.startFrame) + layer.mediaOffset;
                        float relSec = (float) (relativeFrame + layer.audioOffset) / Math.max(1, this.exportFps);
                        Texture frameTex = player.seekFrame(Math.max(0F, relSec));
                        if (frameTex != null && frameTex.isValid())
                        {
                            layer.cachedVideoTexture = frameTex;
                            float playerFps = player.getFps() > 0 ? player.getFps() : 30F;
                            layer.lastVideoFrameIndex = Math.max(0, (int) (Math.max(0F, relSec) * playerFps));
                        }
                    }
                }
                catch (Exception ignored) {}
            }
        }

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

            for (CatalystLayer layer : this.exportAudioSources(comp))
            {
                if (hasSolo && !layer.solo) continue;
                if (layer.muted || layer.volume <= 0 || this.isTrackSilent(comp, layer)) continue;
                if (layer.layerType == CatalystLayer.LayerType.VIDEO && layer.muteOwnAudio) continue;
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
                        clip.volume.set(layer.volume * layer.gainFactor());
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
                    if (layer.muted || layer.volume <= 0 || this.isTrackSilent(comp, layer)) continue;
                if (layer.layerType == CatalystLayer.LayerType.VIDEO && layer.muteOwnAudio) continue;
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
                File logParent = logFile.getParentFile();
                if (logParent != null && !logParent.exists())
                {
                    logParent.mkdirs();
                }
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

        this.exportAudioSourceFile = null;
        this.exportTempVideoFile = null;
        boolean needsAudioMux = profile.isExportAudio() && audioSourceFile != null && profile.getFormat().isAudioSupported() && !profile.getAudioCodec().isNone();
        if (needsAudioMux)
        {
            this.exportAudioSourceFile = audioSourceFile;
            String tempPattern = profile.getFormat().getOutputPattern(baseName + ".tmp_video");
            this.exportTempVideoFile = new File(outputFile.getParentFile(), tempPattern);
            File tempParent = this.exportTempVideoFile.getParentFile();
            if (tempParent != null && !tempParent.exists())
            {
                tempParent.mkdirs();
            }
        }

        String captureMovieName = needsAudioMux ? (baseName + ".tmp_video") : baseName;
        FFmpegCommandBuilder cmdBuilder = new FFmpegCommandBuilder(profile)
            .movieName(captureMovieName)
            .inputResolution(width, height)
            .inputFramerate(fps)
            .outputFolder(outputFile.getParentFile())
            .audio(null); // Pure video during capture stage (two-stage mux parity)

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

            this.exportLogFile = new File(workDir, outputFile.getName() + ".export.log");
            File logParent = this.exportLogFile.getParentFile();
            if (logParent != null && !logParent.exists())
            {
                logParent.mkdirs();
            }
            pb.redirectOutput(this.exportLogFile);

            this.exportProcess = pb.start();

            // Give FFmpeg a brief moment to validate parameters and codec initialization
            try
            {
                Thread.sleep(120);
            }
            catch (InterruptedException ignored) {}

            if (!this.exportProcess.isAlive())
            {
                int exitCode = this.exportProcess.exitValue();
                this.dumpFFmpegLog(this.exportLogFile, exitCode);
                this.cancelExport();
                return;
            }

            OutputStream os = this.exportProcess.getOutputStream();
            Unsafe unsafe = UnsafeUtils.getUnsafe();

            if (os instanceof FilterOutputStream)
            {
                try
                {
                    Field outField = FilterOutputStream.class.getDeclaredField("out");
                    os = (OutputStream) unsafe.getObject(os, unsafe.objectFieldOffset(outField));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            os = new BufferedOutputStream(os, 256 * 1024);
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
            this.dumpFFmpegLog(this.exportLogFile, -1);
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
            if (this.exportProcess != null && !this.exportProcess.isAlive())
            {
                int exitCode = this.exportProcess.exitValue();
                this.dumpFFmpegLog(this.exportLogFile, exitCode);
            }
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
            if (this.exportBuffer == null || this.exportBuffer.capacity() < fullFrameBytes)
            {
                System.err.println("[Catalyst] Invalid exportBuffer state! Cancelling export.");
                this.cancelExport();
                return;
            }

            if (this.exportChannel != null && this.exportChannel.isOpen())
            {
                this.exportBuffer.position(0);
                this.exportBuffer.limit(fullFrameBytes);
                while (this.exportBuffer.hasRemaining())
                {
                    int written = this.exportChannel.write(this.exportBuffer);
                    if (written == 0 && !this.exportProcess.isAlive())
                    {
                        throw new java.io.IOException("FFmpeg process died unexpectedly during write");
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.dumpFFmpegLog(this.exportLogFile, this.exportProcess != null && !this.exportProcess.isAlive() ? this.exportProcess.exitValue() : -1);
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
        if (this.exportTempVideoFile != null && this.exportTempVideoFile.exists())
        {
            this.exportTempVideoFile.delete();
            this.exportTempVideoFile = null;
        }
        this.exportAudioSourceFile = null;

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

        /* Close stdin channel so FFmpeg knows video input is finished */
        if (this.exportChannel != null)
        {
            try { this.exportChannel.close(); }
            catch (Exception ignored) {}
            this.exportChannel = null;
        }

        /* Wait for FFmpeg to finish encoding video container */
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

        /* Stage 2: Fast Audio MUX pass (parity with Film Editor / VideoMuxer) */
        if (this.exportTempVideoFile != null && this.exportTempVideoFile.exists() && this.exportAudioSourceFile != null && this.exportAudioSourceFile.exists() && this.activeExportProfile != null && this.exportTargetFile != null)
        {
            String baseName = this.exportTargetFile.getName();
            String ext = this.activeExportProfile.getFormat().getExtension();
            if (baseName.toLowerCase().endsWith(ext.toLowerCase()))
            {
                baseName = baseName.substring(0, baseName.length() - ext.length());
            }

            FFmpegCommandBuilder muxCmdBuilder = new FFmpegCommandBuilder(this.activeExportProfile)
                .movieName(baseName)
                .outputFolder(this.exportTargetFile.getParentFile());

            List<String> rawMuxArgs = muxCmdBuilder.buildMuxArgs(this.exportTempVideoFile, this.exportAudioSourceFile, baseName);
            List<String> cleanMuxArgs = new ArrayList<>();
            for (String a : rawMuxArgs)
            {
                cleanMuxArgs.add(a.replace("\"", "").trim());
            }

            try
            {
                File workDir = this.exportTargetFile.getParentFile() != null && this.exportTargetFile.getParentFile().exists() ? this.exportTargetFile.getParentFile() : BBSMod.getGameFolder();
                ProcessBuilder muxPb = new ProcessBuilder(cleanMuxArgs);
                muxPb.directory(workDir);
                muxPb.redirectErrorStream(true);
                File muxLog = new File(workDir, this.exportTargetFile.getName() + ".mux.log");
                File muxLogParent = muxLog.getParentFile();
                if (muxLogParent != null && !muxLogParent.exists())
                {
                    muxLogParent.mkdirs();
                }
                muxPb.redirectOutput(muxLog);

                Process muxProcess = muxPb.start();
                muxProcess.getOutputStream().close();
                boolean completed = muxProcess.waitFor(2, TimeUnit.MINUTES);

                if (completed && muxProcess.exitValue() == 0 && this.exportTargetFile.exists())
                {
                    this.exportTempVideoFile.delete();
                }
                else
                {
                    this.dumpFFmpegLog(muxLog, muxProcess.isAlive() ? -1 : muxProcess.exitValue());
                    /* Fallback: if mux failed, rename temp video so rendered footage is not lost */
                    if (this.exportTempVideoFile.exists() && !this.exportTargetFile.exists())
                    {
                        this.exportTempVideoFile.renameTo(this.exportTargetFile);
                    }
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                if (this.exportTempVideoFile.exists() && !this.exportTargetFile.exists())
                {
                    this.exportTempVideoFile.renameTo(this.exportTargetFile);
                }
            }
            finally
            {
                this.exportTempVideoFile = null;
                this.exportAudioSourceFile = null;
            }
        }

        if (this.exportTempAudioMixFile != null && this.exportTempAudioMixFile.exists())
        {
            this.exportTempAudioMixFile.delete();
            this.exportTempAudioMixFile = null;
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
        if (BBSSettings.videoPlaySoundAfterExport.get())
        {
            if (BBSModClient.getSounds().play(Link.assets("sounds/render_complete.ogg")) == null)
            {
                UIUtils.playClick(0.5F);
            }
        }
        else
        {
            UIUtils.playClick(0.5F);
        }
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

    /* ════════════════════════════════════════════════════════
     *  RESIZABLE COLUMNS: library | monitor | inspector, and the monitor over the timeline
     * ════════════════════════════════════════════════════════ */

    /** "Project Saved" with a check, fading in and out over two seconds after Ctrl+S */
    private void renderSavedBadge(UIContext context)
    {
        long elapsed = System.currentTimeMillis() - this.savedAt;

        if (this.savedAt <= 0L || elapsed > 2000L)
        {
            return;
        }

        float alpha = elapsed < 150L ? elapsed / 150F : (elapsed > 1500L ? 1F - (elapsed - 1500L) / 500F : 1F);

        if (alpha <= 0.04F)
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        String text = UIKeys.CATALYST_NLE_SAVED.get();
        int w = font.getWidth(text) + 34;
        int x = this.area.mx() - w / 2;
        int y = this.area.y + 30;

        mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.fill(context.batcher, x, y, x + w, y + 20, 6, mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, Colors.setA(0x1c2433, 0.92F * alpha));
        mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.outline(context.batcher, x, y, x + w, y + 20, 6, mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, Colors.setA(0x2eb67d, alpha));
        context.batcher.icon(Icons.CHECKMARK, Colors.setA(0x2eb67d, alpha), x + 5, y + 2);
        context.batcher.text(text, x + 24, y + 6, Colors.setA(Colors.WHITE, alpha), false);
    }

    @Override
    public void render(UIContext context)
    {
        /* While a tool reads, the playhead runs over the clips it is reading */
        if (this.aiBusy)
        {
            CatalystComposition running = this.getActiveComposition();

            if (running != null)
            {
                this.currentFrame = Math.max(0, this.aiFrom + (int) Math.round((this.aiTo - this.aiFrom) * this.aiProgress));
                running.playhead = this.currentFrame;
            }
        }

        Runnable queued;

        while ((queued = this.mainQueue.poll()) != null)
        {
            queued.run();
        }

        if (this.keysOpen)
        {
            this.refreshKeyEditor();
            this.syncKeyAxis();
        }

        /* Edits are saved a moment after the last one */
        if (this.dirtyAt > 0L && System.currentTimeMillis() - this.dirtyAt > 600L)
        {
            this.dirtyAt = 0L;
            this.ensureDuration();
            this.saveAndRefresh();
        }

        /* The fullscreen bar shows while the mouse moves, or rests on it, and for three seconds after */
        if (context.mouseX != this.lastMouseX || context.mouseY != this.lastMouseY || (this.fullscreenBar != null && this.fullscreenBar.isVisible() && this.fullscreenBar.area.isInside(context)))
        {
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;
            this.lastMouseMoveAt = System.currentTimeMillis();
        }

        if (this.fullscreenBar != null)
        {
            boolean show = this.isFullscreen && !this.isExporting && this.currentTab == CatalystTab.EDITOR && this.activeProject != null
                && System.currentTimeMillis() - this.lastMouseMoveAt < FULLSCREEN_BAR_MS;

            if (show != this.fullscreenBar.isVisible())
            {
                this.fullscreenBar.setVisible(show);
            }
        }

        super.render(context);

        this.renderSavedBadge(context);

        if (this.draggedAsset == null)
        {
            return;
        }

        UINleTimeline timeline = this.catalystTimeline;
        boolean overTimeline = timeline != null && timeline.isVisible() && timeline.area.isInside(context) && context.mouseX >= timeline.lanesLeft();

        if (!Window.isMouseButtonPressed(0))
        {
            /* Dropped: on the timeline at the frame under the mouse, on the monitor at the playhead */
            if (overTimeline)
            {
                this.dropAssetToTimeline(this.draggedAsset, Math.max(0, timeline.snapTick(timeline.fromGraphTick(context.mouseX))));
            }
            else if (this.previewArea.area.isInside(context))
            {
                this.dropAssetToTimeline(this.draggedAsset, this.currentFrame);
            }

            this.draggedAsset = null;

            return;
        }

        FontRenderer font = context.batcher.getFont();

        if (overTimeline)
        {
            int frame = Math.max(0, timeline.snapTick(timeline.fromGraphTick(context.mouseX)));
            int x = timeline.toGraphX(frame);

            context.batcher.box(x - 1, timeline.area.y, x + 1, timeline.area.ey(), Colors.setA(BBSSettings.primaryColor.get(), 0.7F));
        }

        /* The card that follows the mouse */
        int w = 130;
        int h = 22;
        int px = Math.min(context.mouseX + 12, this.area.ex() - w);
        int py = Math.min(context.mouseY + 12, this.area.ey() - h);
        mchorse.bbs_mod.ui.utils.icons.Icon icon = this.draggedAsset.type == CatalystMediaAsset.MediaType.VIDEO ? Icons.FILM
            : this.draggedAsset.type == CatalystMediaAsset.MediaType.AUDIO ? Icons.SOUND : Icons.IMAGE;

        mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.fill(context.batcher, px, py, px + w, py + h, 4, mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, 0xee1c2433);
        mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.outline(context.batcher, px, py, px + w, py + h, 4, mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw.ALL, Colors.A100 | BBSSettings.primaryColor.get());
        context.batcher.icon(icon, Colors.WHITE, px + 3, py + 3);
        context.batcher.text(font.limitToWidth(this.draggedAsset.name, w - 28), px + 22, py + 7, Colors.WHITE, false);
    }
}