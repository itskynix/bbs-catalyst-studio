package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.api.client.editor.TrackCategories;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.Waveform;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.IKBake;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackStyle;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIBakeIKOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCategoryBar;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.ui.utils.IBoneSelectionHost;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UIReplaysEditor extends UIElement implements IBoneSelectionHost
{
    private final BoneSelection boneSelection = new BoneSelection();

    private static String lastFilm = "";
    private static int lastReplay;

    public UIReplaysListPanel replaysList;
    public UIReplayPropertiesPanel replayProperties;

    private static final int CATEGORY_BAR_WIDTH = 20;
    private final UIElement partHeader = new UIElement()
    {
        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            /* Scrolled track labels can lie behind the ruler corner. */
            return this.area.isInside(context);
        }
    };

    public UITimelineCategoryBar iconBar;
    public Map<TrackCategory, UIIcon> tabButtons = new HashMap<>();
    private TrackCategory category = TrackCategory.REPLAY;

    /* Keyframes */
    public UIKeyframeEditor keyframeEditor;

    /**
     * The gizmo target for the replay's own placement in the world. It has no fields on
     * screen — the record is edited by dragging the actor, not by typing — but it is a
     * child all the same, so it can reach the UI context; its gesture is driven by
     * {@link mchorse.bbs_mod.ui.utils.GizmoInteraction#update} rather than by a render.
     * It outlives the keyframe editor, which is rebuilt on every replay and category
     * switch, so a running drag survives whatever the selection does underneath it.
     */
    public final UIReplayPropTransform replayTransform = new UIReplayPropTransform();

    /* Action clips share the timeline area; the toggle below the categories switches to them. */
    private UIClipsPanel actionTimeline;
    private UIIcon actionsToggle;
    private UIIcon graphToggle;
    private boolean actionsMode;
    /* «All tracks» view: shows every category's tracks at once, bypassing the category filter. */
    private UIIcon allToggle;
    private UIIcon sectionsToggle;
    private boolean allMode;

    /*
     * The POV timeline: the replay seen from inside its own head. Its pages sit on a bar of their own
     * in place of the categories, and its overlay clips share the area the way the action clips do.
     * Only player-shaped replays have one (see ReplayPov.supports).
     */
    private UITimelineCategoryBar povBar;
    private final Map<PovPage, UIIcon> povButtons = new EnumMap<>(PovPage.class);
    private UIClipsPanel povActionTimeline;
    private UIIcon povToggle;
    private UIIcon povActionsToggle;
    private UIIcon povBack;
    private boolean povMode;
    private boolean povActionsMode;
    private PovPage povPage = PovPage.HOTBAR;

    /* Clips */
    private UIFilmPanel filmPanel;
    private Film film;
    private Replay replay;
    private boolean settingReplay;
    private Pair<Form, String> pendingPick;
    private boolean timelineVisible = true;
    private boolean propertiesVisible = true;
    private Set<String> keys = new LinkedHashSet<>();
    private int poseOverlayCount;
    private int transformOverlayCount;
    /**
     * Which rows the user left unfolded, per replay. Every rebuild of the timeline throws the dope
     * sheet away — switching category, toggling "all tracks", changing the track filter — so this
     * state is handed to each new sheet, which folds in it directly rather than keeping a copy.
     */
    private final Map<String, FoldState<String>> expandedTracksByReplay = new HashMap<>();
    private final Map<String, String> selectedPartsByReplay = new HashMap<>();
    private String selectedPart = "";
    private Form lastSelectedTargetForm;

    public static Icon getIcon(String key)
    {
        return TrackStyle.icon(key);
    }

    public static int getColor(String key)
    {
        return TrackStyle.color(key);
    }

    /** The key a sheet is identified by in track filters (global and per-form) and in name/colour overrides. */
    public static String getSheetFilterKey(UIKeyframeSheet sheet)
    {
        return sheet.getFilterKey();
    }

    /** The form a sheet belongs to, whether it backs a form property or carries its owner directly (bones, materials, IK). */
    public static Form getSheetForm(UIKeyframeSheet sheet)
    {
        if (sheet.form != null)
        {
            return sheet.form;
        }

        return sheet.property == null ? null : FormUtils.getForm(sheet.property);
    }

    /** Single home of the category rule: tabs only filter now, so collectors always gather and this decides where a sheet lands. */
    public static TrackCategory categoryOf(UIKeyframeSheet sheet)
    {
        Form owner = getSheetForm(sheet);

        if (owner != null && !(owner instanceof mchorse.bbs_mod.forms.forms.AudioForm))
        {
            TrackCategory cat = categoryOf(sheet.id, true);

            return cat == TrackCategory.AUDIO ? TrackCategory.FORM : cat;
        }

        return categoryOf(sheet.id, sheet.property != null || sheet.form != null);
    }

    /**
     * @param owned whether the track belongs to a form at all — a replay's own curated channels
     *              (position, hotbar, sticks) do not, and they are the Replay tab
     */
    public static TrackCategory categoryOf(TrackId track, boolean owned)
    {
        return TrackCategories.categoryOf(track, owned);
    }

    private static TrackCategory categoryOf(String id, boolean owned)
    {
        return TrackCategories.categoryOf(id == null ? null : TrackId.parse(id), owned);
    }

    public static void renderRuler(UIContext context, UIKeyframes keyframes, UIClipsPanel clipsPanel, Clips camera, int clipOffset)
    {
        Area area = keyframes.graphArea;

        int rulerBottom = TimelineRulerRenderer.getRulerBottom(area);

        if (rulerBottom <= area.y)
        {
            return;
        }

        context.batcher.clipBox(area.x, area.y, area.ex(), rulerBottom, context);

        renderRulerAudio(context, keyframes, camera, clipOffset, area, rulerBottom);
        renderRulerClipGradient(context, keyframes, clipsPanel, clipOffset, area, rulerBottom);

        context.batcher.unclip(context);
    }

    private static boolean renderRulerAudio(UIContext context, UIKeyframes keyframes, Clips camera, int clipOffset, Area area, int rulerBottom)
    {
        if (!BBSSettings.audioWaveformVisibleInKeyframes.get())
        {
            return false;
        }

        Scale scale = keyframes.getXAxis();
        boolean renderedOnce = false;
        int y = area.y + 1;
        int h = Math.max(1, rulerBottom - y);

        for (Clip clip : camera.get())
        {
            if (!(clip instanceof AudioClip audioClip))
            {
                continue;
            }

            Link link = audioClip.audio.get();

            if (link == null)
            {
                continue;
            }

            SoundBuffer buffer = BBSModClient.getSounds().get(link, true);

            if (buffer == null || buffer.getWaveform() == null)
            {
                continue;
            }

            Waveform wave = buffer.getWaveform();
            int audioOffset = audioClip.offset.get();
            float offset = audioClip.tick.get() - clipOffset;
            int duration = Math.min((int) (wave.getDuration() * 20), clip.duration.get());
            int x1 = (int) scale.to(offset);
            int x2 = (int) scale.to(offset + duration);

            if (x2 <= area.x || x1 >= area.ex())
            {
                continue;
            }

            wave.render(context.batcher, Colors.WHITE, x1, y, x2 - x1, h, TimeUtils.toSeconds(audioOffset), TimeUtils.toSeconds(audioOffset + duration));

            renderedOnce = true;
        }

        return renderedOnce;
    }

    private static void renderRulerClipGradient(UIContext context, UIKeyframes keyframes, UIClipsPanel clipsPanel, int clipOffset, Area area, int rulerBottom)
    {
        Clip clip = clipsPanel.getClip();

        if (clip == null || clip instanceof AudioClip || !BBSSettings.editorClipPreview.get())
        {
            return;
        }

        Scale scale = keyframes.getXAxis();
        int x1 = (int) scale.to(clip.tick.get() - clipOffset);
        int x2 = (int) scale.to(clip.tick.get() + clip.duration.get() - clipOffset);

        if (x2 <= area.x || x1 >= area.ex())
        {
            return;
        }

        int color = clipsPanel.clips.getFactory().getData(clip).color;
        int left = Math.max(area.x, x1);
        int right = Math.min(area.ex(), x2);
        int top = area.y + 1;
        int bottom = Math.max(top + 1, rulerBottom);

        context.batcher.gradientVBox(left, top, right, bottom, Colors.setA(color, 0.03F), Colors.setA(color, 0.78F));
        context.batcher.box(left, Math.max(top, bottom - 2), right, bottom, Colors.setA(color, 0.92F));
    }

    public UIReplaysEditor(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;
        this.replayProperties = new UIReplayPropertiesPanel(filmPanel);
        this.replaysList = new UIReplaysListPanel(filmPanel, (l) -> this.setReplay(l.isEmpty() ? null : l.get(0), false, OrbitReaction.SWITCH), this.replayProperties.getFormConsumer(), this::selectBodyPart);
        this.replayProperties.attachReplayList(this.replaysList.replays);

        this.iconBar = new UITimelineCategoryBar(CATEGORY_BAR_WIDTH * 3);
        this.iconBar.relative(this);

        /* «All tracks» heads the bar: it is not one of the categories but what you see instead of
         * them, so it sits above the rule that separates the two. */
        this.allToggle = new UIIcon(Icons.LIST, b -> this.setAllTracks());
        this.allToggle.tooltip(UIKeys.FILM_REPLAY_ALL_TRACKS, Direction.RIGHT);
        this.allToggle.highlight(() -> !this.actionsMode && this.allMode, Direction.LEFT);

        this.iconBar.add(this.allToggle);

        for (TrackCategory category : TrackCategories.values())
        {
            UIIcon button = new UIIcon(category.icon, b -> this.setCategory(category));

            button.tooltip(category.tooltip, Direction.RIGHT);
            button.highlight(() -> !this.actionsMode && !this.allMode && this.category == category, Direction.LEFT);
            this.iconBar.add(button);
            this.tabButtons.put(category, button);
        }

        this.sectionsToggle = new UIIcon(Icons.COLLAPSE_ALL, b -> this.toggleAllSections());
        this.sectionsToggle.tooltip(L10n.lang("bbs.ui.film.replays.collapse_all"), Direction.RIGHT);

        /* Actions timeline, pinned to the bottom of the bar. */
        this.actionsToggle = new UIIcon(Icons.ACTION, b -> this.toggleActionsMode());
        this.actionsToggle.tooltip(UIKeys.FILM_REPLAY_ACTIONS_TIMELINE, Direction.RIGHT);
        this.actionsToggle.highlight(() -> this.actionsMode, Direction.LEFT);

        /* Dope sheet <-> graph editor, for the track under the cursor or the selected one. */
        this.graphToggle = new UIIcon(Icons.CURVES, b -> this.toggleGraphEditor());
        this.graphToggle.tooltip(UIKeys.KEYFRAMES_GRAPH_EDITOR, Direction.RIGHT);
        this.graphToggle.highlight(() -> this.keyframeEditor != null && this.keyframeEditor.view.isEditing(), Direction.LEFT);

        /* Into the POV timeline, right above the action clips */
        this.povToggle = new UIIcon(Icons.USER, b -> this.setPovMode(true));
        this.povToggle.tooltip(UIKeys.FILM_REPLAY_POV_TIMELINE, Direction.RIGHT);

        this.povBar = new UITimelineCategoryBar(CATEGORY_BAR_WIDTH * 4).noSeparator();
        this.povBar.relative(this);

        for (PovPage page : PovPage.values())
        {
            UIIcon button = new UIIcon(page.icon, b -> this.setPovPage(page));

            button.tooltip(L10n.lang("bbs.ui.film.replays.pov.pages." + page.id()), Direction.RIGHT);
            button.highlight(() -> this.povMode && !this.povActionsMode && this.povPage == page, Direction.LEFT);
            this.povBar.add(button);
            this.povButtons.put(page, button);
        }

        /* Inside it: its overlay clips, and below them the way back to the replay's tracks */
        this.povActionsToggle = new UIIcon(Icons.ACTION, b -> this.setPovActionsMode(!this.povActionsMode));
        this.povActionsToggle.tooltip(UIKeys.FILM_REPLAY_POV_ACTIONS_TIMELINE, Direction.RIGHT);
        this.povActionsToggle.highlight(() -> this.povMode && this.povActionsMode, Direction.LEFT);

        this.povBack = new UIIcon(Icons.ARROW_LEFT, b -> this.setPovMode(false));
        this.povBack.tooltip(UIKeys.FILM_REPLAY_POV_BACK, Direction.RIGHT);
        this.layoutActionsToggle();

        /* Everything at once is the view to open on: a category is a way to narrow down, and
         * narrowing before the animator has seen what there is hides tracks they came for. */
        this.setAllTracks();

        TrackCategories.registerShortcuts(this.keys(), () -> TrackCategories.values().stream()
            .filter(category -> this.tabButtons.get(category).getParent() != null).toList(), this::setCategory);

        this.add(this.iconBar, this.povBar, this.sectionsToggle, this.graphToggle, this.actionsToggle, this.povToggle, this.povActionsToggle, this.povBack, this.replayTransform);
        this.partHeader.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(120).h(TimelineRulerRenderer.RULER_BLOCK_HEIGHT);
        this.partHeader.add(new UIRenderable(context ->
        {
            Area area = this.partHeader.area;

            area.render(context.batcher, BBSSettings.baseSurface());
        }));
        UILabel partName = new UILabel(this::getSelectedPartName).color(0xffaaaaaa, false).labelAnchor(0F, 0.5F);
        partName.relative(this.partHeader).x(5).y(0).w(1F, -10).h(1F);
        partName.tooltip(() -> L10n.lang("bbs.ui.film.replays.selected_body_part").format(this.getSelectedPartName()).get());
        this.partHeader.add(partName);
        this.add(this.partHeader);
        this.markContainer();
    }

    private void toggleAllSections()
    {
        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.view.getDopeSheet().setAllSectionsExpanded(
                !this.keyframeEditor.view.getDopeSheet().hasExpandedSections());
        }
    }

    private void setCategory(TrackCategory c)
    {
        this.actionsMode = false;
        this.allMode = false;
        this.povMode = false;
        this.povActionsMode = false;
        this.category = c;
        this.updateChannelsList();
    }

    /**
     * Bring the replay's own tracks into view, for when something outside the timeline starts
     * writing to them — dragging the replay gizmo. Without this the keys land on x/y/z and the
     * angles while the timeline is showing bones or materials, and the edit happens off screen.
     *
     * <p>"All tracks" already shows them, so it is left alone: it is the wider view, and
     * dropping out of it into a single category would be a step back, not forward.
     */
    public void showReplayTracks()
    {
        if (this.povMode || this.actionsMode || (!this.allMode && this.category != TrackCategory.REPLAY))
        {
            this.setCategory(TrackCategory.REPLAY);
        }
    }

    /** Show every category's tracks at once, bypassing the category filter. */
    private void setAllTracks()
    {
        this.actionsMode = false;
        this.allMode = true;
        this.povMode = false;
        this.povActionsMode = false;
        this.updateChannelsList();
    }

    public TrackCategory getCategory()
    {
        return this.category;
    }

    public void pickReplayCategory()
    {
        if (this.category != TrackCategory.REPLAY)
        {
            this.setCategory(TrackCategory.REPLAY);
        }
    }

    public void setFilm(Film film)
    {
        this.expandedTracksByReplay.clear();
        this.selectedPartsByReplay.clear();
        this.replaysList.setBodyPartsReplay(null, "");
        this.film = film;
        this.filmPanel.getController().orbit.reset();

        if (film != null)
        {
            List<Replay> replays = film.replays.getList();
            int index = film.getId().equals(lastFilm) ? lastReplay : 0;

            if (!CollectionUtils.inRange(replays, index))
            {
                index = 0;
            }

            this.replaysList.replays.refreshReplayList();
            this.setReplay(replays.isEmpty() ? null : replays.get(index), true, OrbitReaction.SWITCH);
        }
        else
        {
            this.setReplay(null, false, OrbitReaction.KEEP);
        }
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public String getSelectedPartName()
    {
        for (var entry : this.replaysList.bodyParts.getList())
        {
            if (entry.getPath().equals(this.selectedPart))
            {
                return entry.toString();
            }
        }

        return "-";
    }

    public void selectBodyPart(String path)
    {
        if (this.replay == null)
        {
            return;
        }

        this.setActionsMode(false);

        if (this.selectedPart.equals(path))
        {
            return;
        }

        this.selectedPart = path;
        this.pendingPick = null;
        this.selectedPartsByReplay.put(this.replay.getId(), path);
        this.autoSelectAudioCategoryIfApplicable();
        this.updateChannelsList();
    }

    private void autoSelectAudioCategoryIfApplicable()
    {
        if (this.replay == null || this.replay.form.get() == null)
        {
            return;
        }

        Form targetForm = this.selectedPart == null || this.selectedPart.isEmpty()
            ? this.replay.form.get()
            : FormUtils.getForm(this.replay.form.get(), this.selectedPart);

        if (targetForm instanceof mchorse.bbs_mod.forms.forms.AudioForm)
        {
            this.actionsMode = false;
            this.allMode = false;
            this.category = TrackCategory.AUDIO;
        }
    }

    public void setReplay(Replay replay)
    {
        this.setReplay(replay, true, OrbitReaction.SWITCH);
    }

    public void setReplay(Replay replay, boolean select, OrbitReaction orbit)
    {
        /* Guard against re-entry: scrollToReplay() below picks the replay in the list,
         * which fires the list's selection callback and calls setReplay() again. The
         * outermost call owns the orbit reaction, so the nested call is redundant and
         * must not override it (otherwise undo would teleport via the SWITCH callback). */
        if (this.settingReplay)
        {
            return;
        }

        this.settingReplay = true;

        try
        {
            this.pendingPick = null;
            this.replay = replay;
            this.selectedPart = replay == null ? "" : this.selectedPartsByReplay.getOrDefault(replay.getId(), "");
            this.autoSelectAudioCategoryIfApplicable();

            if (orbit == OrbitReaction.RESET)
            {
                this.filmPanel.getController().orbit.reset();
            }
            else if (orbit == OrbitReaction.SWITCH && replay != null && BBSSettings.editorOrbitTeleportOnSwitch.get())
            {
                this.filmPanel.getController().orbit.teleportPivotToReplay();
            }

            this.replayProperties.setReplay(replay);
            this.filmPanel.actionEditor.setClips(replay == null ? null : replay.actions);

            if (this.povActionTimeline != null)
            {
                this.povActionTimeline.setClips(ReplayPov.supports(replay) ? replay.pov.actions : null);
            }

            this.updateChannelsList();

            if (select && replay != null)
            {
                this.replaysList.replays.scrollToReplay(replay);
            }
        }
        finally
        {
            this.settingReplay = false;
        }
    }

    public void moveReplay(double x, double y, double z)
    {
        if (this.replay != null)
        {
            UIContext context = this.getContext();
            float cursor = this.filmPanel.getKeyframeCursor(context == null ? 0F : context.getTransition());

            this.replay.keyframes.x.insert(cursor, x);
            this.replay.keyframes.y.insert(cursor, y);
            this.replay.keyframes.z.insert(cursor, z);
        }
    }

    public void updateChannelsList()
    {
        this.poseOverlayCount = BBSSettings.recordingPoseOverlays.get();
        this.transformOverlayCount = BBSSettings.recordingTransformOverlays.get();
        this.selectedPart = this.replaysList.setBodyPartsReplay(this.replay, this.selectedPart);
        this.replaysList.resize();

        UIKeyframes lastEditor = this.keyframeEditor != null ? this.keyframeEditor.view : null;

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.removeFromParent();
            this.keyframeEditor = null;
        }

        /* Another replay picked, or its form changed, and there is no point of view left to show */
        if (this.povMode && !ReplayPov.supports(this.replay))
        {
            this.povMode = false;
            this.povActionsMode = false;
            this.updateTimelineModeVisibility();
        }

        this.layoutActionsToggle();

        if (this.replay == null)
        {
            this.lastSelectedTargetForm = null;
            return;
        }

        Form currentTargetForm = this.selectedPart == null || this.selectedPart.isEmpty()
            ? this.replay.form.get()
            : FormUtils.getForm(this.replay.form.get(), this.selectedPart);

        if (currentTargetForm != this.lastSelectedTargetForm)
        {
            this.lastSelectedTargetForm = currentTargetForm;
            this.autoSelectAudioCategoryIfApplicable();
        }

        this.selectedPartsByReplay.put(this.replay.getId(), this.selectedPart);
        List<TrackDescriptor> catalog = TrackCatalog.forPart(this.replay.form.get(), this.replay.properties, this.selectedPart);

        List<UIKeyframeSheet> sheets = new ArrayList<>();

        if (this.povMode)
        {
            /* The POV timeline shows its page and nothing else; the category tabs are put away with their bar */
            this.collectPovSheets(sheets);
        }
        else
        {
            /* Replay channels remain accessible alongside any selected part's own properties. */
            this.collectCuratedSheets(sheets);
            UIReplaysEditorUtils.buildSheets(catalog, sheets);

            for (TrackCategory category : TrackCategories.values())
            {
                if (category != TrackCategory.REPLAY && category != TrackCategory.FORM && category != TrackCategory.POSE)
                    this.updateTab(category, sheets);
            }
            /* Reattach in registry order, including tabs which became visible again. */
            for (TrackCategory category : TrackCategories.values())
            {
                UIIcon button = this.tabButtons.get(category);
                if (button.getParent() != null)
                {
                    button.removeFromParent();
                    this.iconBar.add(button);
                }
            }
        }

        this.keys.clear();

        for (UIKeyframeSheet sheet : sheets)
        {
            this.keys.add(getSheetFilterKey(sheet));
        }

        Set<String> disabled = BBSSettings.disabledSheets.get();

        /* The body-part tree already chose the owner; tabs narrow down its properties. */
        sheets.removeIf((v) -> !this.povMode && !this.allMode && categoryOf(v) != this.category);

        /* The tab isn't empty by itself - so if the filter empties it, the timeline has to stay (see below). */
        boolean hadTracks = !sheets.isEmpty();

        sheets.removeIf((v) ->
        {
            String filterKey = getSheetFilterKey(v);

            for (String s : disabled)
            {
                if (filterKey.equals(s) || v.id.equals(s) || v.id.endsWith("/" + s))
                {
                    return true;
                }
            }

            Form owner = getSheetForm(v);

            if (owner != null)
            {
                Set<String> ownerDisabled = owner.disabledTracks.get();

                return ownerDisabled.contains(Form.DISABLED_ALL) || ownerDisabled.contains(filterKey);
            }

            return false;
        });

        /*
         * Filtering every track off used to drop the timeline itself, and the track filter lives in its
         * context menu - so «disable all» locked the user out of the only way back. Keep the (empty)
         * timeline whenever the tab had tracks before the filter ran; the dope sheet says why it's blank.
         */
        boolean filteredOutEverything = hadTracks && sheets.isEmpty();

        UIReplaysEditorUtils.pruneTree(sheets);

        if (!sheets.isEmpty() || filteredOutEverything)
        {
            this.keyframeEditor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.filmPanel.cameraEditor, consumer).absolute())
                .target(this.filmPanel.editArea);
            this.keyframeEditor.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
            this.keyframeEditor.setUndoId("replay_keyframe_editor");
            this.keyframeEditor.view.getDopeSheet().setEmptyState(UIKeys.KEYFRAMES_EMPTY_FILTERED, UIKeys.KEYFRAMES_EMPTY_FILTERED_HINT);

            this.layoutActionsToggle();

            /* Reset */
            if (lastEditor != null)
            {
                this.keyframeEditor.view.copyViewport(lastEditor);
            }

            this.keyframeEditor.view.rulerRenderer((context) -> renderRuler(context, this.keyframeEditor.view, this.filmPanel.cameraEditor, this.film.camera, 0));
            this.keyframeEditor.view.duration(() -> this.film.camera.calculateDuration());
            this.keyframeEditor.view.context(menu ->
            {
                int mouseY = this.getContext().mouseY;
                UIKeyframeSheet sheet = this.keyframeEditor.view.getGraph().getSheet(mouseY);

                UIReplaysEditorUtils.addOverlayTrackAction(menu, sheet, parent ->
                {
                    this.getExpandedTracks().set(parent.toKey(), true);
                    this.updateChannelsList();
                });

                ModelForm poseModelForm = sheet == null ? null : sheet.getPoseForm();
                IPosedForm posedForm = sheet == null ? null : sheet.getPosedForm();

                if (poseModelForm != null)
                {
                    menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () ->
                    {
                        ModelInstance model = ModelFormRenderer.getModel(poseModelForm);

                        if (model != null)
                        {
                            UIOverlay.addOverlay(
                                this.getContext(),
                                new UIAnimationToPoseOverlayPanel(
                                    (animationKey, onlyKeyframes, length, step) ->
                                    {
                                        float current = this.keyframeEditor.view.getTick();
                                        IEntity entity = this.filmPanel.getController().getCurrentEntity();

                                        UIReplaysEditorUtils.animationToPoseKeyframes(this.keyframeEditor, sheet, poseModelForm, entity, current, animationKey, onlyKeyframes, length, step);
                                    },
                                poseModelForm, sheet), 200, 197
                            );
                        }
                    });

                }

                /* Not gated on a MODEL form: baking a pose into per-bone tracks is a pose
                 * operation, and a mob form has a skeleton to bake onto just the same. */
                if (posedForm != null && sheet.selection.hasAny() && posedForm.hasBoneTracks())
                {
                    menu.action(Icons.LIMB, UIKeys.FILM_REPLAY_CONTEXT_POSES_TO_LIMBS, () ->
                    {
                        if (UIReplaysEditorUtils.posesToLimbTracks(this.replay.properties, sheet))
                        {
                            this.getExpandedTracks().set(sheet.id, true);
                            this.updateChannelsList();
                        }
                    });
                }

                if (this.replay.form.get() instanceof ModelForm modelForm)
                {
                    ModelInstance instance = ModelFormRenderer.getModel(modelForm);
                    List<String> controllers = ModelIKRuntime.getControllers(instance);
                    if (!controllers.isEmpty())
                    {
                        menu.action(Icons.CLOSE, UIKeys.FILM_REPLAY_CONTEXT_CLEAR_IK, () ->
                        {
                            UIReplaysEditorUtils.clearIKTracks(this.replay, modelForm);
                            this.updateChannelsList();
                        });
                    }

                    Map<String, List<String>> chains = instance == null
                        ? Collections.emptyMap()
                        : ModelIKRuntime.getChains(instance.model, modelForm);

                    if (!chains.isEmpty())
                    {
                        menu.action(Icons.KEY, UIKeys.FILM_REPLAY_CONTEXT_BAKE_IK, () ->
                        {
                            /* The range on offer ends where the replay's own motion ends — past its
                             * last keyframe the solve only repeats itself, and a film is usually
                             * longer than any one replay in it. A replay without keyframes gets the film. */
                            int lastTick = (int) Math.ceil(this.replay.getLastKeyframeTick());

                            if (lastTick < 0)
                            {
                                lastTick = Math.max(0, this.film.calculateDuration() - 1);
                            }

                            UIOverlay.addOverlay(this.getContext(), new UIBakeIKOverlayPanel(chains.keySet(), lastTick, (tips, start, end, step, disable) ->
                            {
                                if (IKBake.bake(this.film, this.replay, tips, start, end, step, disable))
                                {
                                    this.updateChannelsList();
                                }
                            }), 240, 240);
                        });
                    }
                }

                if (this.keyframeEditor.view.getGraph() instanceof UIKeyframeDopeSheet)
                {
                    menu.action(Icons.FILTER, UIKeys.FILM_REPLAY_FILTER_SHEETS, () ->
                    {
                        Set<String> disabledSet = BBSSettings.disabledSheets.get();
                        Map<String, Integer> keyToColor = new HashMap<>();
                        for (UIKeyframeSheet listed : this.keyframeEditor.view.getGraph().getSheets())
                        {
                            keyToColor.put(getSheetFilterKey(listed), listed.color);
                        }
                        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(
                                disabledSet,
                                this.keys,
                                keyToColor
                        );

                        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

                        panel.onClose(e ->
                        {
                            BBSSettings.disabledSheets.set(disabledSet);
                            this.updateChannelsList();
                        });
                    });
                }
            });

            for (UIKeyframeSheet sheet : sheets)
            {
                this.keyframeEditor.view.addSheet(sheet);
            }

            this.keyframeEditor.view.getDopeSheet().setExpanded(this.getExpandedTracks());

            this.add(this.keyframeEditor);
            /* Category bar + actions toggle on top so they overlay the track names column. */
            this.bringBarToFront();
            this.updateTimelineModeVisibility();
        }

        this.resize();

        if (this.keyframeEditor != null && lastEditor == null)
        {
            this.keyframeEditor.view.resetView();
        }
    }

    private void collectCuratedSheets(List<UIKeyframeSheet> sheets)
    {
        String[] groups = {"position_rotation", "states", "hotbar", "equipment", "controls", "velocity"};
        Icon[] icons = {Icons.PLAYER, Icons.ACTION, getIcon("item_slot_0"), Icons.ARMOR_CHESTPLATE, getIcon("stick_lx"), Icons.FORWARD};
        int[] colors = {0x40bfff, Colors.ORANGE, Colors.YELLOW, Colors.BLUE, 0xb580ff, Colors.GREEN};

        for (int i = 0; i < groups.length; i++)
        {
            String group = groups[i];
            UIKeyframeSheet.Section section = new UIKeyframeSheet.Section("replay_section/" + group,
                L10n.lang("bbs.ui.film.replay.sections." + group), icons[i], colors[i]);

            for (String key : ReplayKeyframes.CURATED_CHANNELS)
            {
                if (replaySection(key).equals(group))
                {
                    KeyframeChannel channel = (KeyframeChannel) this.replay.keyframes.get(key);
                    UIKeyframeSheet sheet = new UIKeyframeSheet(getColor(key), channel, null).icon(getIcon(key));
                    sheet.section = section;
                    sheets.add(sheet);
                }
            }
        }
    }

    /**
     * One page of the POV timeline, folded into sections. Its own channels go by their ids like the
     * replay's do, and the hotbar slots and the head rotation are the replay's own channels, borrowed
     * under the names they already have — a slot keyed here is the slot the replay plays.
     */
    private void collectPovSheets(List<UIKeyframeSheet> sheets)
    {
        ReplayPov pov = this.replay.pov;
        ReplayKeyframes keyframes = this.replay.keyframes;

        switch (this.povPage)
        {
            case HOTBAR ->
            {
                List<KeyframeChannel<?>> slots = new ArrayList<>();

                slots.add(keyframes.selectedSlot);
                slots.addAll(keyframes.hotbar);
                slots.add(keyframes.offHand);
                slots.add(pov.inventorySlots);

                this.addPovSection(sheets, "layout", Icons.HOTBAR, Colors.YELLOW, List.of(pov.hudVisible, pov.statusBars, pov.crosshair, pov.gameMode));
                this.addPovSection(sheets, "cursor", Icons.POINTER, 0xb580ff, List.of(pov.cursorVisible, pov.cursorX, pov.cursorY, pov.cursorItem));
                this.addPovSection(sheets, "slots", getIcon("item_slot_0"), Colors.ORANGE, slots);
                this.addPovSection(sheets, "status", Icons.HEART, Colors.RED, List.of(pov.health, pov.maxHealth, pov.absorption, pov.hardcore,
                    pov.armor, pov.hunger, pov.air, pov.mountHealth, pov.experience, pov.experienceLevel));
            }
            case HANDS ->
            {
                this.addPovSection(sheets, "visibility", Icons.VISIBLE, Colors.GREEN, List.of(pov.rightHandVisible, pov.leftHandVisible));
                this.addPovSection(sheets, "swing", Icons.LIMB, Colors.ORANGE, List.of(pov.rightSwing, pov.leftSwing, pov.mainEquip, pov.offhandEquip));
                this.addPovSection(sheets, "bobbing", Icons.ARC, 0x40bfff, List.of(pov.bobPhase, pov.bobStrength));
                this.addPovSection(sheets, "render", Icons.ORBIT, 0xb580ff, List.of(pov.renderPitch, pov.renderYaw));
            }
            case VISION ->
            {
                this.addPovSection(sheets, "eye", Icons.VISIBLE, 0x2fa8a0, List.of(pov.eyePitch, pov.eyeYaw, pov.eyeRoll, pov.eyeOffsetX, pov.eyeOffsetY, pov.eyeOffsetZ));
                this.addPovSection(sheets, "lens", Icons.CAMERA, Colors.YELLOW, List.of(pov.fov));
                this.addPovSection(sheets, "head", Icons.PLAYER, 0x40bfff, List.of(keyframes.pitch, keyframes.headYaw));
            }
        }
    }

    private void addPovSection(List<UIKeyframeSheet> sheets, String group, Icon icon, int color, List<? extends KeyframeChannel<?>> channels)
    {
        UIKeyframeSheet.Section section = new UIKeyframeSheet.Section("pov_section/" + this.povPage.id() + "/" + group,
            L10n.lang("bbs.ui.film.replays.pov.sections." + group), icon, color);
        List<KeyframeChannel<?>> own = this.replay.pov.getChannels();

        for (KeyframeChannel<?> channel : channels)
        {
            String key = channel.getId();
            boolean borrowed = !own.contains(channel);
            UIKeyframeSheet sheet = new UIKeyframeSheet(borrowed ? getColor(key) : color, channel, null).icon(borrowed ? getIcon(key) : icon);
            Object seed = povSeed(key);

            if (seed != null)
            {
                sheet.seed(() -> seed);
            }

            sheet.section = section;
            sheets.add(sheet);
        }
    }

    /** What a fresh keyframe holds on a POV channel: the value the game would show unanimated. */
    private static Object povSeed(String key)
    {
        return switch (key)
        {
            case "hud_visible", "status_bars", "crosshair", "cursor_visible", "right_hand_visible", "left_hand_visible" -> true;
            case "game_mode" -> 0;
            case "inventory_slots" -> mchorse.bbs_mod.film.pov.PovSlotSet.ofSize(27);
            case "health", "max_health", "hunger" -> 20D;
            case "air" -> 300D;
            case "fov" -> 70D;
            case "bob_strength" -> 1D;
            default -> null;
        };
    }

    private static String replaySection(String key)
    {
        if (key.startsWith("item_slot_") || key.equals("selected_slot") || key.equals("item_off_hand")) return "hotbar";
        if (key.startsWith("item_")) return "equipment";
        if (key.startsWith("stick_") || key.startsWith("trigger_") || key.startsWith("extra")) return "controls";

        return switch (key)
        {
            case "x", "y", "z", "pitch", "yaw", "headYaw", "bodyYaw" -> "position_rotation";
            case "vX", "vY", "vZ" -> "velocity";
            default -> "states";
        };
    }

    /**
     * Show a category's tab only while the replay actually has tracks of that kind, and bounce the
     * active category back to Model when it does not. Asked of the collected sheets, so "does this replay have
     * IK" is the same question as "which tracks land in the IK tab" — it used to be a separate walk
     * of the form tree per category, with its own idea of the answer.
     */
    private void updateTab(TrackCategory category, List<UIKeyframeSheet> sheets)
    {
        UIIcon button = this.tabButtons.get(category);

        if (button == null)
        {
            return;
        }

        boolean has = false;

        for (UIKeyframeSheet sheet : sheets)
        {
            if (categoryOf(sheet) == category)
            {
                has = true;

                break;
            }
        }

        boolean present = button.getParent() != null;

        if (has != present)
        {
            if (has)
            {
                this.iconBar.add(button);
            }
            else
            {
                button.removeFromParent();
            }

            this.resize();
        }

        if (!has && this.category == category)
        {
            this.category = TrackCategory.FORM;
        }
    }

    /**
     * Rows the user has unfolded in this replay's timeline. Handed to the dope sheet as-is, so folding
     * a row there lands here directly — there is nothing to read back out when the timeline is rebuilt,
     * which is what the old save-and-restore step existed for (and it had to know which tracks the
     * current category could even answer for).
     */
    public FoldState<String> getExpandedTracks()
    {
        return this.expandedTracksByReplay.computeIfAbsent(this.replay == null ? "" : this.replay.getId(), (k) ->
        {
            FoldState<String> folds = new FoldState<>();
            folds.set("replay_section/position_rotation", true);
            folds.set("pov_section/hotbar/slots", true);
            folds.set("pov_section/hands/visibility", true);
            folds.set("pov_section/vision/eye", true);
            return folds;
        });
    }

    /** Pose tracks unfolded right now — what {@code insertFrame} keys by, per limb or as a whole pose. */
    public FoldState<String> getExpandedPoseTabIds()
    {
        return this.getExpandedTracks();
    }

    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.updateTimelineModeVisibility();
    }

    public void setPropertiesVisible(boolean visible)
    {
        this.propertiesVisible = visible;
        this.updateTimelineModeVisibility();
    }

    /** The action-clips timeline shares this editor's timeline area; the actions toggle switches to it. */
    public void attachActionTimeline(UIClipsPanel actionTimeline)
    {
        this.actionTimeline = actionTimeline;
        actionTimeline.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
        this.add(actionTimeline);
        this.bringBarToFront();
        this.updateTimelineModeVisibility();
    }

    /** The POV overlay clips share this editor's timeline area too; the POV timeline's own toggle switches to them. */
    public void attachPovActionTimeline(UIClipsPanel povActionTimeline)
    {
        this.povActionTimeline = povActionTimeline;
        povActionTimeline.relative(this).x(CATEGORY_BAR_WIDTH).y(0).w(1F, -CATEGORY_BAR_WIDTH).h(1F);
        this.add(povActionTimeline);
        this.bringBarToFront();
        this.updateTimelineModeVisibility();
    }

    public boolean isPovMode()
    {
        return this.povMode;
    }

    /** Open or close the POV timeline; only a replay with a point of view has one to open. */
    public void setPovMode(boolean povMode)
    {
        if (povMode && !ReplayPov.supports(this.replay))
        {
            return;
        }

        if (this.povMode == povMode && !this.povActionsMode)
        {
            return;
        }

        this.povMode = povMode;
        this.povActionsMode = false;
        this.actionsMode = false;
        this.updateChannelsList();
        this.updateTimelineModeVisibility();
    }

    private void setPovPage(PovPage page)
    {
        this.povPage = page;
        this.povActionsMode = false;
        this.updateChannelsList();
        this.updateTimelineModeVisibility();
    }

    public void setPovActionsMode(boolean povActionsMode)
    {
        if (!this.povMode || this.povActionsMode == povActionsMode)
        {
            return;
        }

        this.povActionsMode = povActionsMode;
        this.updateTimelineModeVisibility();
    }

    /**
     * The POV editor button: the POV timeline of the picked replay, or of the first replay in the
     * film with a point of view when the picked one has none. False when the film has none at all.
     */
    public boolean openPov()
    {
        if (!ReplayPov.supports(this.replay) && this.film != null)
        {
            for (Replay replay : this.film.replays.getList())
            {
                if (ReplayPov.supports(replay))
                {
                    this.setReplay(replay);

                    break;
                }
            }
        }

        if (!ReplayPov.supports(this.replay))
        {
            return false;
        }

        this.povMode = false;
        this.setPovMode(true);

        return true;
    }

    /** Whether the timeline area is showing clips (the replay's actions or the POV's overlays) rather than keyframes. */
    private boolean isClipsView()
    {
        return this.povMode ? this.povActionsMode : this.actionsMode;
    }

    public boolean isActionsMode()
    {
        return this.actionsMode;
    }

    private void toggleActionsMode()
    {
        this.setActionsMode(!this.actionsMode);
    }

    public void setActionsMode(boolean actionsMode)
    {
        if (this.actionsMode == actionsMode)
        {
            return;
        }

        this.actionsMode = actionsMode;

        /* The replay's action clips are the replay timeline's, so asking for them leaves the POV one */
        if (actionsMode && this.povMode)
        {
            this.povMode = false;
            this.povActionsMode = false;
            this.updateChannelsList();
        }

        this.updateTimelineModeVisibility();
    }

    /**
     * Show either the keyframe timeline or the action-clips timeline in the same area;
     * their parameters share editArea, so only the active mode's panel is shown.
     */
    private void updateTimelineModeVisibility()
    {
        boolean keyframes = !this.isClipsView();
        boolean actions = this.actionsMode && !this.povMode;
        boolean povActions = this.povMode && this.povActionsMode;

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.setTimelineVisible(this.timelineVisible && keyframes);
            this.keyframeEditor.setPropertiesVisible(this.propertiesVisible && keyframes);
        }

        if (this.actionTimeline != null)
        {
            this.actionTimeline.setVisible((this.timelineVisible || this.propertiesVisible) && actions);
            this.actionTimeline.setTimelineVisible(this.timelineVisible && actions);
            this.actionTimeline.setPropertiesVisible(this.propertiesVisible && actions);
        }

        if (this.povActionTimeline != null)
        {
            this.povActionTimeline.setVisible((this.timelineVisible || this.propertiesVisible) && povActions);
            this.povActionTimeline.setTimelineVisible(this.timelineVisible && povActions);
            this.povActionTimeline.setPropertiesVisible(this.propertiesVisible && povActions);
        }
    }

    /** Keep the category bar and the actions toggle above the timelines. */
    private void bringBarToFront()
    {
        UIElement[] front = {this.iconBar, this.povBar, this.sectionsToggle, this.graphToggle, this.actionsToggle,
            this.povToggle, this.povActionsToggle, this.povBack, this.partHeader};

        for (UIElement element : front)
        {
            if (element.getParent() != null)
            {
                element.removeFromParent();
            }
        }

        this.add(front);
    }

    /**
     * Pin the timeline switches below the category buttons: the action clips at the very bottom and
     * the POV timeline above them — or, inside the POV timeline, the way back at the bottom and its
     * overlay clips above it. Folding and the graph stack on top of whichever pair is there.
     */
    private void layoutActionsToggle()
    {
        int bottom = this.povMode || ReplayPov.supports(this.replay) ? 40 : 20;

        this.actionsToggle.relative(this).x(0).y(1F, -20).wh(CATEGORY_BAR_WIDTH, 20);
        this.povBack.relative(this).x(0).y(1F, -20).wh(CATEGORY_BAR_WIDTH, 20);
        this.povToggle.relative(this).x(0).y(1F, -40).wh(CATEGORY_BAR_WIDTH, 20);
        this.povActionsToggle.relative(this).x(0).y(1F, -40).wh(CATEGORY_BAR_WIDTH, 20);
        this.sectionsToggle.relative(this).x(0).y(1F, -bottom - 20).wh(CATEGORY_BAR_WIDTH, 20);
        this.graphToggle.relative(this).x(0).y(1F, -bottom - 40).wh(CATEGORY_BAR_WIDTH, 20);
    }

    private void toggleGraphEditor()
    {
        if (this.keyframeEditor != null && !this.isClipsView())
        {
            this.keyframeEditor.view.toggleGraphEditor();
        }
    }

    public void pickForm(Form form, String bone)
    {
        this.pickFormBone(form, bone, false);
    }

    /**
     * Bone pick from the 3D viewport with the Shift / Ctrl offer gestures. The gizmo
     * sphere's deferred pick goes through here so clicking a bone on the sphere behaves
     * like a direct bone click — Shift opens the hierarchy menu, Ctrl multi-selects —
     * instead of only doing a plain select.
     */
    public void pickFormWithOffers(UIContext context, Form form, String bone)
    {
        UIReplaysEditorUtils.pickFormWithOffers(context, new Pair<>(form, bone), this::pickFormBone);
    }

    /**
     * Picking a model bone in the viewport is a pose edit, but the pose/bone tracks
     * only exist in the {@link TrackCategory#POSE} category. So when another category
     * is open, jump to Pose first (and out of actions mode) before delegating to the
     * shared pick logic — otherwise the click finds no pose sheet in the current graph
     * and silently does nothing, forcing a manual tab switch.
     */

    @Override
    public BoneSelection getBoneSelection()
    {
        return this.boneSelection;
    }

    private void pickFormBone(Form form, String bone, boolean insert)
    {
        if (form == null)
        {
            return;
        }

        this.selectBodyPart(FormUtils.getPath(form));

        if (!(form instanceof IPosedForm) || (bone != null && !bone.isEmpty()))
        {
            /* A bone is posed on the replay timeline, not the POV one */
            if (this.povMode)
            {
                this.setPovMode(false);
            }

            if (this.allMode)
            {
                this.setActionsMode(false);
            }
            else if (this.category != TrackCategory.POSE || this.actionsMode)
            {
                this.setCategory(TrackCategory.POSE);
            }
        }

        UIReplaysEditorUtils.pickForm(this.keyframeEditor, this.filmPanel, form, bone, insert);
    }

    public boolean clickViewport(UIContext context, Area area)
    {
        /* In flight the buttons are the flight camera's, so the left one is left for it to
         * pick up as free look; only the middle one has to be handed over by hand. */
        if (this.filmPanel.isFlying() && area.isInside(context) && context.mouseButton == 2)
        {
            this.filmPanel.dashboard.orbit.start(2, context.mouseX, context.mouseY);

            return true;
        }

        if (this.filmPanel.isFlying())
        {
            return false;
        }

        if (area.isInside(context) && context.mouseButton == 2 && this.filmPanel.getController().orbit.enabled)
        {
            this.filmPanel.getController().orbit.start(context);

            return true;
        }

        StencilFormFramebuffer stencil = this.filmPanel.getController().getStencil();

        /* In orbit mode left-drag rotates the camera anywhere, even over a form.
         * The form selection is deferred to release, so a click still selects but a
         * drag orbits instead of being swallowed by the form under the cursor. */
        if (area.isInside(context) && context.mouseButton == 0 && this.filmPanel.getController().orbit.enabled)
        {
            this.pendingPick = stencil.hasPicked() ? stencil.getPicked() : null;
            this.filmPanel.getController().orbit.start(context);

            return true;
        }

        if (stencil.hasPicked())
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null && (context.mouseButton < 2 || (context.mouseButton == 2 && Window.isCtrlPressed())))
            {
                if (!this.isVisible())
                {
                    this.filmPanel.showPanel(this);
                }

                if (UIReplaysEditorUtils.pickFormWithOffers(context, pair, this::pickFormBone))
                {
                    return true;
                }
            }
        }
        else if (context.mouseButton == 1 && this.isVisible())
        {
            World world = MinecraftClient.getInstance().world;
            Camera camera = this.filmPanel.getCamera();

            Vector3f rayOffset = new Vector3f();
            Vector3f rayDirection = CameraUtils.getMouseRay(camera.projection, camera.view, context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, rayOffset);

            BlockHitResult blockHitResult = RayTracing.rayTrace(
                world,
                RayTracing.fromVector3d(new Vector3d(camera.position).add(rayOffset.x, rayOffset.y, rayOffset.z)),
                RayTracing.fromVector3f(rayDirection),
                256F
            );

            if (blockHitResult.getType() != HitResult.Type.MISS)
            {
                Vector3d vec = new Vector3d(blockHitResult.getPos().x, blockHitResult.getPos().y, blockHitResult.getPos().z);

                if (Window.isShiftPressed())
                {
                    vec = new Vector3d(Math.floor(vec.x) + 0.5D, Math.round(vec.y), Math.floor(vec.z) + 0.5D);
                }

                final Vector3d finalVec = vec;

                context.replaceContextMenu(menu ->
                {
                    float pitch = 0F;
                    float yaw = MathUtils.toDeg(camera.rotation.y);

                    menu.icon(MenuVerb.ADD, () -> this.replaysList.replays.addReplay(finalVec, pitch, yaw)).label(UIKeys.FILM_REPLAY_CONTEXT_ADD);
                    menu.action(Icons.POINTER, UIKeys.FILM_REPLAY_CONTEXT_MOVE_HERE, () -> this.moveReplay(finalVec.x, finalVec.y, finalVec.z));
                });

                return true;
            }
        }

        return false;
    }

    public void releaseViewport(UIContext context, boolean dragged)
    {
        Pair<Form, String> pending = this.pendingPick;

        this.pendingPick = null;

        if (pending == null || dragged || context.mouseButton != 0)
        {
            return;
        }

        if (!this.isVisible())
        {
            this.filmPanel.showPanel(this);
        }

        UIReplaysEditorUtils.pickFormWithOffers(context, pending, this::pickFormBone);
    }

    public void close()
    {
        if (this.film != null)
        {
            lastFilm = this.film.getId();
            Replay r = this.getReplay();

            lastReplay = r == null ? 0 : this.film.replays.getList().indexOf(r);
        }
    }

    public void teleport()
    {
        if (this.filmPanel.getData() == null)
        {
            return;
        }

        PlayerUtils.teleportToReplay(this.getReplay(), this.filmPanel.getCursor());
    }

    @Override
    public void render(UIContext context)
    {
        /* Settings can change while this timeline remains open behind another panel. */
        if (this.replay != null && (this.poseOverlayCount != BBSSettings.recordingPoseOverlays.get()
            || this.transformOverlayCount != BBSSettings.recordingTransformOverlays.get()))
        {
            this.updateChannelsList();
        }

        /* Hide category bar + actions toggle while the "edit track" overlay is open */
        boolean notEditing = this.keyframeEditor == null || !this.keyframeEditor.view.isEditing();

        boolean bar = this.timelineVisible && notEditing;
        boolean clips = this.isClipsView();

        this.iconBar.setVisible(bar && !this.povMode);
        this.povBar.setVisible(bar && this.povMode);
        this.actionsToggle.setVisible(bar && !this.povMode);
        this.povToggle.setVisible(bar && !this.povMode && ReplayPov.supports(this.replay));
        this.povActionsToggle.setVisible(bar && this.povMode);
        this.povBack.setVisible(bar && this.povMode);
        this.graphToggle.setVisible(this.timelineVisible && !clips && this.keyframeEditor != null);
        boolean sectionsAvailable = !clips && this.keyframeEditor != null
            && this.keyframeEditor.view.getGraph() == this.keyframeEditor.view.getDopeSheet()
            && this.keyframeEditor.view.getDopeSheet().hasSections();

        boolean foldingButtonFits = true;

        for (UIIcon button : (this.povMode ? this.povBar : this.iconBar).getChildren(UIIcon.class))
        {
            if (button.isVisible() && button.area.ey() >= this.sectionsToggle.area.y)
            {
                foldingButtonFits = false;
                break;
            }
        }

        this.sectionsToggle.setVisible(this.timelineVisible && notEditing && foldingButtonFits);
        this.sectionsToggle.setEnabled(sectionsAvailable);
        boolean collapseSections = sectionsAvailable && this.keyframeEditor.view.getDopeSheet().hasExpandedSections();

        this.sectionsToggle.both(collapseSections ? Icons.COLLAPSE_ALL : Icons.EXPAND_ALL);
        this.sectionsToggle.tooltip(L10n.lang(collapseSections
            ? "bbs.ui.film.replays.collapse_all" : "bbs.ui.film.replays.expand_all"), Direction.RIGHT);
        this.partHeader.setVisible(this.timelineVisible && notEditing && !clips && !this.povMode && this.replay != null
            && this.keyframeEditor != null && this.keyframeEditor.view.getGraph() == this.keyframeEditor.view.getDopeSheet());

        if (this.partHeader.isVisible())
        {
            int width = Math.min(this.keyframeEditor.view.getLabelWidth(), this.keyframeEditor.view.area.w);

            if (this.partHeader.area.w != width)
            {
                this.partHeader.w(width).resize();
            }
        }

        UIReplaysEditorUtils.configureFilmHotkeyDrag(this.filmPanel, context);

        super.render(context);
    }

    @Override
    protected void afterResizeApplied()
    {
        super.afterResizeApplied();

        int width = (this.povMode ? this.povBar : this.iconBar).getWidthForHeight(this.area.h);

        this.iconBar.w(width);
        this.povBar.w(width);
        this.partHeader.x(width);

        if (this.keyframeEditor != null)
        {
            this.keyframeEditor.x(width).w(1F, -width);
        }

        if (this.actionTimeline != null)
        {
            this.actionTimeline.x(width).w(1F, -width);
        }

        if (this.povActionTimeline != null)
        {
            this.povActionTimeline.x(width).w(1F, -width);
        }

        this.layoutActionsToggle();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (this.film == null || this.film.replays == null)
        {
            return;
        }

        List<Integer> selection = DataStorageUtils.intListFromData(data.getList("selection"));
        List<Integer> currentIndices = this.replaysList.replays.getCurrentIndices();

        Replay replay = CollectionUtils.getSafe(this.film.replays.getList(), data.getInt("replay"));

        if (replay != null)
        {
            this.selectedPartsByReplay.put(replay.getId(), data.getString("body_part"));
        }

        this.setReplay(replay, true, OrbitReaction.KEEP);

        currentIndices.clear();
        currentIndices.addAll(selection);
        this.replaysList.replays.update();
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        /* The undo chain walks every element, including a replay editor no film has been given yet */
        if (this.film == null || this.film.replays == null)
        {
            return;
        }

        int index = this.film.replays.getList().indexOf(this.getReplay());

        data.putInt("replay", index);
        data.putString("body_part", this.selectedPart);
        data.put("selection", DataStorageUtils.intListToData(this.replaysList.replays.getCurrentIndices()));
    }

    /** A page of the POV timeline, each with its own channels. */
    public enum PovPage
    {
        HOTBAR(Icons.HOTBAR), HANDS(Icons.LIMB), VISION(Icons.VISIBLE);

        public final Icon icon;

        PovPage(Icon icon)
        {
            this.icon = icon;
        }

        public String id()
        {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * How the orbit camera should react when the selected replay is set.
     */
    public enum OrbitReaction
    {
        /** Reset the orbit camera to its default position. */
        RESET,
        /** Treat it as a user switching replays — teleport the pivot onto the replay if the setting allows. */
        SWITCH,
        /** Leave the orbit camera untouched (used when restoring selection during undo/redo). */
        KEEP
    }
}
