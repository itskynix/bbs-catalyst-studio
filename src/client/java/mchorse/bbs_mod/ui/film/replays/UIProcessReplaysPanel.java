package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIPanelBase;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.ui.utils.Label;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batch processing of the picked replays: scatter them randomly, line them up, shape them into
 * squares, circles, cubes and spheres, drop them onto the ground, aim them at a target, shift
 * them — or drive the chosen channels with a MoLang expression. Split out of the replay list:
 * the list is a list, and this is half an editor of its own with two views and remembered state.
 */
public class UIProcessReplaysPanel extends UIConfirmOverlayPanel
{
    /** What the dialog remembers between openings — the last operation and its inputs. */
    private static final ProcessReplaysState PROCESS_STATE = new ProcessReplaysState();

    /** The follow target that stands for the camera of the film rather than for a replay */
    private static final int FOLLOW_CAMERA = -2;

    private final UIFilmPanel filmPanel;

    /** The picked replays in view order, snapshotted at opening (the overlay is modal). */
    private final List<Replay> replays;

    private final UIStringList properties = new UIStringList(null)
    {
        @Override
        protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            int h = this.scroll.scrollItemSize;
            int color = UIReplaysEditor.getColor(element);
            Icon icon = UIReplaysEditor.getIcon(element);

            RowStyle.swatch(context.batcher, x, y, h, color);
            context.batcher.icon(icon, RowStyle.iconColor(hover || selected), x + 2, y + h / 2F, 0F, 0.5F);
            context.batcher.textShadow(this.elementToString(context, i, element), x + 24, y + (h - context.batcher.getFont().getHeight()) / 2, RowStyle.textColor(hover || selected));
        }

        @Override
        protected String elementToString(UIContext context, int i, String element)
        {
            return channelTitle(element).get();
        }
    };

    /** Stands where the channel list does when the chosen operation takes no channels */
    private final UIText noChannels = new UIText(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_NO_CHANNELS_NOTE).padding(0, 0).lineHeight(10);

    private final UIPanelBase<UIElement> modes = new UIPanelBase<>(Direction.TOP);
    /* Built in the constructor, after filmPanel is set: the views read the film while they are made */
    private final UINormalProcessView normal;
    private final UIAdvancedProcessView advanced;

    /** Channels that have a plain name of their own; the rest are listed by their id */
    private static final Set<String> NAMED_CHANNELS = Set.of("x", "y", "z", "pitch", "yaw", "headYaw", "bodyYaw", "look_at_weight", "look_at_x", "look_at_y", "look_at_z", "sneaking", "sprinting", "grounded", "swimming");

    private static final Map<String, IKey> CHANNEL_TITLES = new HashMap<>();

    private static IKey channelTitle(String id)
    {
        return CHANNEL_TITLES.computeIfAbsent(id, (i) -> NAMED_CHANNELS.contains(i) ? L10n.lang("bbs.ui.scene.replays.context.process.channel." + i) : IKey.constant(i));
    }

    private static class ProcessReplaysState
    {
        public String expression = "v";
        public List<String> properties = new ArrayList<>(List.of("x"));
        public boolean advanced;
        public boolean fill = false;
        public int lookAtTarget = -1;

        /** The follow target: an index in the film's replays, FOLLOW_CAMERA for the camera, -1 for none */
        public int followTarget = -1;
        public double followX;
        public double followY;
        public double followZ = 0;
        public double followMinDistance = 1.2;
        public double followTriggerRange = 0;
        public double followIntensity = 1;
        public boolean followOrganic = false;
        public double followSmooth = 0;
        public boolean followRotate = true;
        public boolean followKeepGround = false;

        public NormalOperation operation = NormalOperation.RANDOM;
        public double randomMin = -1;
        public double randomMax = 1;
        public double lineOffset = 1;
        public double size = 3;
        public double shift = 1;
    }

    public UIProcessReplaysPanel(UIFilmPanel filmPanel, List<Replay> replays)
    {
        super(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_TITLE, IKey.EMPTY, null);

        this.filmPanel = filmPanel;
        this.replays = replays;
        this.normal = new UINormalProcessView();
        this.advanced = new UIAdvancedProcessView();

        this.message.setVisible(false);

        this.properties.scroll.scrollItemSize = 16;

        for (String id : collectProcessChannelIds(replays.get(0)))
        {
            this.properties.add(id);
        }

        this.properties.background().multi();
        this.properties.update();

        if (!PROCESS_STATE.properties.isEmpty())
        {
            this.properties.setCurrentScroll(PROCESS_STATE.properties.get(0));
        }

        for (String property : PROCESS_STATE.properties)
        {
            this.properties.addIndex(this.properties.getList().indexOf(property));
        }

        this.modes.registerPanel(this.normal, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_MODE_NORMAL, Icons.SHAPES);
        this.modes.registerPanel(this.advanced, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_MODE_ADVANCED, Icons.CODE);
        this.modes.setPanel(PROCESS_STATE.advanced ? this.advanced : this.normal);

        UIElement body = new UIElement();
        body.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -40);

        this.modes.relative(body).x(0).y(0).w(1F, -220).h(1F);

        this.properties.relative(body).x(1F, -214).y(20).w(214).h(1F, -20);
        this.noChannels.relative(body).x(1F, -214).y(24).w(214);

        this.confirm.w(1F, -10);
        this.content.add(body);
        body.add(this.modes, this.properties, this.noChannels);
    }

    @Override
    public void render(UIContext context)
    {
        /* Operations that take no channels leave nothing to pick from: the list gives way to a note */
        boolean needed = this.modes.view == this.advanced || this.normal.getSelectedOperation() == null || this.normal.getSelectedOperation().channels;

        this.properties.setVisible(needed);
        this.noChannels.setVisible(!needed);

        super.render(context);
    }

    /** Numeric channels of a replay, curated first, in the order the timeline shows them. */
    private static List<String> collectProcessChannelIds(Replay replay)
    {
        ArrayList<String> out = new ArrayList<>();
        HashSet<String> added = new HashSet<>();

        for (String id : ReplayKeyframes.CURATED_CHANNELS)
        {
            BaseValue baseValue = replay.keyframes.get(id);

            if (baseValue instanceof KeyframeChannel<?> channel && KeyframeFactories.isNumeric(channel.getFactory()))
            {
                out.add(id);
                added.add(id);
            }
        }

        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
        {
            if (!KeyframeFactories.isNumeric(channel.getFactory()) || added.contains(channel.getId()))
            {
                continue;
            }

            out.add(channel.getId());
        }

        return out;
    }

    @Override
    public void confirm()
    {
        if (this.apply())
        {
            super.confirm();
        }
    }

    private boolean apply()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return false;
        }

        List<String> selectedProperties = new ArrayList<>(this.properties.getCurrent());

        List<ReplayBatchProcessor.VisibleReplay> visible = this.collectVisibleReplays();

        if (visible.isEmpty())
        {
            return false;
        }

        boolean isAdvanced = this.modes.view == this.advanced;

        PROCESS_STATE.advanced = isAdvanced;

        if (isAdvanced)
        {
            if (selectedProperties.isEmpty())
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_CHANNELS);

                return false;
            }

            PROCESS_STATE.properties = new ArrayList<>(selectedProperties);

            if (!this.applyAdvanced(context, visible, selectedProperties))
            {
                return false;
            }
        }
        else
        {
            NormalOperation operation = this.normal.getSelectedOperation();

            if (operation == null)
            {
                operation = NormalOperation.RANDOM;
            }

            /* Fitting to the ground only moves Y, and the list is out of the way for it */
            if (operation == NormalOperation.FIT_HEIGHT)
            {
                selectedProperties = new ArrayList<>(List.of("y"));
            }

            if (operation.channels && selectedProperties.isEmpty())
            {
                context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_CHANNELS);

                return false;
            }

            if (operation.channels)
            {
                PROCESS_STATE.properties = new ArrayList<>(selectedProperties);
            }

            if (!this.applyNormal(context, visible, selectedProperties))
            {
                return false;
            }
        }

        this.filmPanel.getController().createEntities();
        this.filmPanel.replayEditor.updateChannelsList();

        return true;
    }

    private List<ReplayBatchProcessor.VisibleReplay> collectVisibleReplays()
    {
        /* The stagger is ordered by the film's own replay list, not by visible rows: a
         * selected replay whose folder is collapsed has no row, and indexing by rows used
         * to silently drop it from the batch (and shift everyone else's offset).
         *
         * The index is taken by identity: ValueGroup compares by content, so List.indexOf
         * hands back the first replay that merely looks the same - and a batch is usually
         * made of duplicates, which all reported index 0 and landed in one spot. */
        List<Replay> all = this.filmPanel.getData().replays.getList();
        int min = Integer.MAX_VALUE;

        for (Replay replay : this.replays)
        {
            int index = CollectionUtils.getIndex(all, replay);

            if (index >= 0)
            {
                min = Math.min(min, index);
            }
        }

        if (min == Integer.MAX_VALUE)
        {
            return new ArrayList<>();
        }

        List<ReplayBatchProcessor.VisibleReplay> out = new ArrayList<>();

        for (Replay replay : this.replays)
        {
            int index = CollectionUtils.getIndex(all, replay);

            if (index >= 0)
            {
                out.add(new ReplayBatchProcessor.VisibleReplay(replay, index, index - min));
            }
        }

        return out;
    }

    private boolean applyAdvanced(UIContext context, List<ReplayBatchProcessor.VisibleReplay> selected, List<String> selectedProperties)
    {
        String expressionText = this.advanced.expression.getText();
        ReplayBatchProcessor.Error error = ReplayBatchProcessor.applyAdvanced(selected, selectedProperties, expressionText);

        if (error == ReplayBatchProcessor.Error.INVALID_EXPRESSION)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_INVALID_EXPRESSION);
            return false;
        }

        return error == null;
    }

    private boolean applyNormal(UIContext context, List<ReplayBatchProcessor.VisibleReplay> selected, List<String> selectedProperties)
    {
        NormalOperation operation = this.normal.getSelectedOperation();

        if (operation == null)
        {
            operation = NormalOperation.RANDOM;
        }

        PROCESS_STATE.operation = operation;

        if (operation == NormalOperation.RANDOM)
        {
            PROCESS_STATE.randomMin = this.normal.randomMin.getValue();
            PROCESS_STATE.randomMax = this.normal.randomMax.getValue();
        }
        else if (operation == NormalOperation.LINE)
        {
            PROCESS_STATE.lineOffset = this.normal.lineOffset.getValue();
        }
        else if (operation == NormalOperation.SQUARE || operation == NormalOperation.SQUARE_OUTLINE || operation == NormalOperation.CUBE || operation == NormalOperation.CIRCLE || operation == NormalOperation.CIRCLE_OUTLINE || operation == NormalOperation.SPHERE)
        {
            PROCESS_STATE.size = this.normal.size.getValue();
        }
        else if (operation == NormalOperation.SHIFT)
        {
            PROCESS_STATE.shift = this.normal.shift.getValue();
        }

        ReplayBatchProcessor.NormalParams params = new ReplayBatchProcessor.NormalParams();
        params.randomMin = PROCESS_STATE.randomMin;
        params.randomMax = PROCESS_STATE.randomMax;
        params.lineOffset = PROCESS_STATE.lineOffset;
        params.size = PROCESS_STATE.size;
        params.shift = PROCESS_STATE.shift;
        params.fill = PROCESS_STATE.fill;
        params.lookAtTarget = this.resolveLookAtTargetReplay();

        if (operation == NormalOperation.FOLLOW_TARGET)
        {
            PROCESS_STATE.followX = this.normal.followX.getValue();
            PROCESS_STATE.followY = this.normal.followY.getValue();
            PROCESS_STATE.followZ = this.normal.followZ.getValue();
            PROCESS_STATE.followSmooth = this.normal.followSmooth.getValue();
            PROCESS_STATE.followMinDistance = this.normal.followMinDistance.getValue();
            PROCESS_STATE.followTriggerRange = this.normal.followTriggerRange.getValue();
            PROCESS_STATE.followIntensity = this.normal.followIntensity.getValue();

            Film film = this.filmPanel.getData();

            params.followTarget = PROCESS_STATE.followTarget == FOLLOW_CAMERA ? null : this.resolveReplay(PROCESS_STATE.followTarget);
            params.followCamera = PROCESS_STATE.followTarget == FOLLOW_CAMERA && film != null ? film.camera : null;
            params.followX = PROCESS_STATE.followX;
            params.followY = PROCESS_STATE.followY;
            params.followZ = PROCESS_STATE.followZ;
            params.followSmooth = PROCESS_STATE.followSmooth;
            params.followMinDistance = PROCESS_STATE.followMinDistance;
            params.followTriggerRange = PROCESS_STATE.followTriggerRange;
            params.followIntensity = PROCESS_STATE.followIntensity;
            params.followOrganic = PROCESS_STATE.followOrganic;
            params.followRotate = PROCESS_STATE.followRotate;
            params.followKeepGround = PROCESS_STATE.followKeepGround;
            params.followDuration = film == null ? 0 : film.calculateDuration();
        }
        params.groundProvider = operation == NormalOperation.FIT_HEIGHT ? this.createGroundProvider() : null;

        ReplayBatchProcessor.Error error = ReplayBatchProcessor.applyNormal(selected, selectedProperties, operation.op, params);

        if (error == ReplayBatchProcessor.Error.NEED_TWO_CHANNELS)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_TWO_CHANNELS);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_THREE_CHANNELS)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_THREE_CHANNELS);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_Y_CHANNEL)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_Y_CHANNEL);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_TARGET)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_TARGET);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NO_WORLD)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NO_WORLD);
            return false;
        }
        else if (error == ReplayBatchProcessor.Error.NEED_POSITION_CHANNELS)
        {
            context.notifyError(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_ERROR_NEED_THREE_CHANNELS);
            return false;
        }

        return error == null;
    }

    private ReplayBatchProcessor.GroundProvider createGroundProvider()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return null;
        }

        HashMap<Long, Double> cache = new HashMap<>();

        return (x, z) ->
        {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            long key = (((long) bx) << 32) ^ (bz & 0xffffffffL);

            Double cached = cache.get(key);

            if (cached != null)
            {
                return cached;
            }

            double top = world.getTopY() + 5;
            Vec3d pos = new Vec3d(x, top, z);
            BlockHitResult result = RayTracing.rayTrace(world, pos, new Vec3d(0D, -1D, 0D), top - world.getBottomY() + 5D);

            double y = Double.NaN;

            if (result != null && result.getType() != HitResult.Type.MISS)
            {
                y = result.getPos().y;
            }

            cache.put(key, y);

            return y;
        };
    }

    /** Whether a replay is one of those being processed; compared by identity, as a batch is mostly look-alikes */
    private boolean isProcessed(Replay replay)
    {
        for (Replay processed : this.replays)
        {
            if (processed == replay)
            {
                return true;
            }
        }

        return false;
    }

    private Replay resolveLookAtTargetReplay()
    {
        return PROCESS_STATE.operation == NormalOperation.LOOK_AT ? this.resolveReplay(PROCESS_STATE.lookAtTarget) : null;
    }

    private Replay resolveReplay(int index)
    {
        Film film = this.filmPanel.getData();

        if (film == null)
        {
            return null;
        }

        List<Replay> replays = film.replays.getList();

        if (index < 0 || index >= replays.size())
        {
            return null;
        }

        return replays.get(index);
    }

    private class UINormalProcessView extends UIElement
    {
        private final UILabelList<NormalOperation> operations;

        private final UITrackpad randomMin;
        private final UITrackpad randomMax;
        private final UITrackpad lineOffset;
        private final UITrackpad size;
        private final UITrackpad shift;
        private final UIToggle fill;
        private final UIButton lookAtTarget;
        private final UIButton followTarget;
        private final UITrackpad followX;
        private final UITrackpad followY;
        private final UITrackpad followZ;
        private final UITrackpad followSmooth;
        private final UITrackpad followMinDistance;
        private final UITrackpad followTriggerRange;
        private final UITrackpad followIntensity;
        private final UIToggle followOrganic;
        private final UIToggle followRotate;
        private final UIToggle followKeepGround;

        private final UIElement params = new UIElement();
        private final UIText hint = new UIText(IKey.EMPTY).padding(0, 0).lineHeight(10);

        public UINormalProcessView()
        {
            super();

            this.operations = new UILabelList<>((l) -> this.updateOperation())
            {
                @Override
                protected void renderElementPart(UIContext context, Label<NormalOperation> element, int i, int x, int y, boolean hover, boolean selected)
                {
                    int h = this.scroll.scrollItemSize;
                    Icon icon = element.value.icon;

                    context.batcher.icon(icon, RowStyle.iconColor(hover || selected), x + 3, y + (h - 16) / 2F);
                    context.batcher.textShadow(element.title.get(), x + 22, y + (h - context.batcher.getFont().getHeight()) / 2, RowStyle.textColor(hover || selected));
                }
            };
            this.operations.background();
            this.operations.scroll.scrollItemSize = UIConstants.CONTROL_HEIGHT;

            for (NormalOperation operation : NormalOperation.values())
            {
                this.operations.add(operation.title, operation);
            }

            this.randomMin = new UITrackpad();
            this.randomMin.limit(-10000, 10000, false);
            this.randomMin.setValue(PROCESS_STATE.randomMin);

            this.randomMax = new UITrackpad();
            this.randomMax.limit(-10000, 10000, false);
            this.randomMax.setValue(PROCESS_STATE.randomMax);

            this.lineOffset = new UITrackpad();
            this.lineOffset.limit(-10000, 10000, false);
            this.lineOffset.setValue(PROCESS_STATE.lineOffset);

            this.size = new UITrackpad();
            this.size.limit(0, 10000, false);
            this.size.setValue(PROCESS_STATE.size);

            this.shift = new UITrackpad();
            this.shift.limit(-10000, 10000, false);
            this.shift.setValue(PROCESS_STATE.shift);

            this.fill = new UIToggle(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FILL, PROCESS_STATE.fill, (b) -> PROCESS_STATE.fill = b.getValue());
            this.fill.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FILL_TOOLTIP);

            this.lookAtTarget = new UIButton(IKey.EMPTY, (b) -> this.openTargetMenu());
            this.lookAtTarget.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_LOOK_AT_TARGET_TOOLTIP);

            this.followTarget = new UIButton(IKey.EMPTY, (b) -> this.openFollowMenu());
            this.followTarget.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_TARGET_TOOLTIP);

            this.followX = new UITrackpad();
            this.followX.limit(-10000, 10000, false);
            this.followX.setValue(PROCESS_STATE.followX);
            this.followX.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_OFFSET_TOOLTIP);

            this.followY = new UITrackpad();
            this.followY.limit(-10000, 10000, false);
            this.followY.setValue(PROCESS_STATE.followY);
            this.followY.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_OFFSET_TOOLTIP);

            this.followZ = new UITrackpad();
            this.followZ.limit(-10000, 10000, false);
            this.followZ.setValue(PROCESS_STATE.followZ);
            this.followZ.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_OFFSET_TOOLTIP);

            this.followSmooth = new UITrackpad();
            this.followSmooth.limit(0, 40, true);
            this.followSmooth.setValue(PROCESS_STATE.followSmooth);
            this.followSmooth.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_SMOOTH_TOOLTIP);

            this.followMinDistance = new UITrackpad();
            this.followMinDistance.limit(0, 16, false);
            this.followMinDistance.setValue(PROCESS_STATE.followMinDistance);
            this.followMinDistance.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_MIN_DISTANCE_TOOLTIP);

            this.followTriggerRange = new UITrackpad();
            this.followTriggerRange.limit(0, 128, false);
            this.followTriggerRange.setValue(PROCESS_STATE.followTriggerRange);
            this.followTriggerRange.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_TRIGGER_RANGE_TOOLTIP);

            this.followIntensity = new UITrackpad();
            this.followIntensity.limit(0, 2, false);
            this.followIntensity.setValue(PROCESS_STATE.followIntensity);
            this.followIntensity.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_INTENSITY_TOOLTIP);

            this.followOrganic = new UIToggle(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_ORGANIC, PROCESS_STATE.followOrganic, (b) -> PROCESS_STATE.followOrganic = b.getValue());
            this.followOrganic.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_ORGANIC_TOOLTIP);

            this.followRotate = new UIToggle(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_ROTATE, PROCESS_STATE.followRotate, (b) -> PROCESS_STATE.followRotate = b.getValue());
            this.followRotate.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_ROTATE_TOOLTIP);

            this.followKeepGround = new UIToggle(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_GROUND, PROCESS_STATE.followKeepGround, (b) -> PROCESS_STATE.followKeepGround = b.getValue());
            this.followKeepGround.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_GROUND_TOOLTIP);

            /* The list scrolls: eleven operations and what they ask for would not fit one panel */
            int opsHeight = UIConstants.CONTROL_HEIGHT * Math.min(NormalOperation.values().length, 7);

            this.operations.relative(this).xy(0, 0).w(1F).h(opsHeight);
            this.params.relative(this.operations).y(1F, UIConstants.MARGIN * 2).w(1F).h(UIConstants.CONTROL_HEIGHT * 5 + UIConstants.MARGIN * 4);
            this.hint.relative(this.params).y(1F, UIConstants.MARGIN).w(1F);

            this.add(this.operations, this.params, this.hint);

            this.operations.setCurrentValue(PROCESS_STATE.operation);
            this.updateOperation();
        }

        private void updateOperation()
        {
            NormalOperation operation = this.getSelectedOperation();

            this.params.removeAll();
            this.updateTargetLabels();

            int rows = 0;

            if (operation == null)
            {
                this.hint.text(IKey.EMPTY);
            }
            else
            {
                this.hint.text(operation.hint);

                switch (operation)
                {
                    case RANDOM ->
                    {
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_MIN, this.randomMin);
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_MAX, this.randomMax);
                    }
                    case LINE -> rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_OFFSET, this.lineOffset);
                    case SQUARE, SQUARE_OUTLINE, CUBE, SPHERE, CIRCLE, CIRCLE_OUTLINE -> rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SIZE, this.size);
                    case LOOK_AT -> rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_LOOK_AT_TARGET, this.lookAtTarget);
                    case SHIFT -> rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_SHIFT, this.shift);
                    case FOLLOW_TARGET ->
                    {
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_TARGET, this.followTarget);
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_OFFSET, this.followX, this.followY, this.followZ);
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_SMOOTH, this.followSmooth);
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_MIN_DISTANCE, this.followMinDistance);
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_TRIGGER_RANGE, this.followTriggerRange);
                        rows = this.addRow(rows, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_INTENSITY, this.followIntensity);
                    }
                    default ->
                    {}
                }
            }

            if (operation == NormalOperation.CUBE || operation == NormalOperation.SPHERE)
            {
                this.fill.relative(this.params).x(0).y(rows * (UIConstants.CONTROL_HEIGHT + UIConstants.MARGIN)).w(1F).h(UIConstants.CONTROL_HEIGHT);
                this.params.add(this.fill);
                rows += 1;
            }

            if (operation == NormalOperation.FOLLOW_TARGET)
            {
                for (UIToggle toggle : new UIToggle[] {this.followRotate, this.followKeepGround, this.followOrganic})
                {
                    toggle.relative(this.params).x(0).y(rows * (UIConstants.CONTROL_HEIGHT + UIConstants.MARGIN)).w(1F).h(UIConstants.CONTROL_HEIGHT);
                    this.params.add(toggle);
                    rows += 1;
                }
            }

            int height = rows <= 0 ? 0 : rows * UIConstants.CONTROL_HEIGHT + (rows - 1) * UIConstants.MARGIN;

            this.params.h(height);
            this.resize();
        }

        /** One labelled row of the parameters, stacked under the ones before it. */
        private int addRow(int rows, IKey label, UIElement... fields)
        {
            UIElement[] elements = new UIElement[fields.length + 1];

            elements[0] = this.paramLabel(label, 104);
            System.arraycopy(fields, 0, elements, 1, fields.length);

            UIElement row = UI.row(elements);

            row.relative(this.params).y(rows * (UIConstants.CONTROL_HEIGHT + UIConstants.MARGIN)).w(1F).h(UIConstants.CONTROL_HEIGHT).resize();
            this.params.add(row);

            return rows + 1;
        }

        private NormalOperation getSelectedOperation()
        {
            Label<NormalOperation> operationLabel = this.operations.getCurrentFirst();

            return operationLabel == null ? null : operationLabel.value;
        }

        private void updateTargetLabels()
        {
            if (UIProcessReplaysPanel.this.filmPanel == null || UIProcessReplaysPanel.this.filmPanel.getData() == null)
            {
                return;
            }

            this.setTargetLabel(this.lookAtTarget, PROCESS_STATE.lookAtTarget);

            if (PROCESS_STATE.followTarget == FOLLOW_CAMERA)
            {
                this.followTarget.label = UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_CAMERA;
            }
            else
            {
                this.setTargetLabel(this.followTarget, PROCESS_STATE.followTarget);
            }
        }

        private void setTargetLabel(UIButton button, int index)
        {
            Film film = UIProcessReplaysPanel.this.filmPanel == null ? null : UIProcessReplaysPanel.this.filmPanel.getData();

            if (film == null || index < 0 || index >= film.replays.getList().size())
            {
                button.label = IKey.constant("-");

                return;
            }

            button.label = IKey.constant(film.replays.getList().get(index).getName());
        }

        /** The replays of the film to pick from, for the one to look at. */
        private void openTargetMenu()
        {
            UIContext context = this.getContext();
            Film film = UIProcessReplaysPanel.this.filmPanel.getData();

            if (context == null || film == null)
            {
                return;
            }

            context.replaceContextMenu((manager) ->
            {
                manager.autoKeys();

                List<Replay> replays = film.replays.getList();

                for (int i = 0; i < replays.size(); i++)
                {
                    int index = i;

                    manager.action(Icons.FILM, IKey.constant(replays.get(i).getName()), () ->
                    {
                        PROCESS_STATE.lookAtTarget = index;

                        this.updateTargetLabels();
                    });
                }
            });
        }

        /**
         * The camera and the replays of the film to follow. The replays that are being processed are
         * left out: one cannot follow itself, and one that is itself being moved is no target.
         */
        private void openFollowMenu()
        {
            UIContext context = this.getContext();
            Film film = UIProcessReplaysPanel.this.filmPanel.getData();

            if (context == null || film == null)
            {
                return;
            }

            context.replaceContextMenu((manager) ->
            {
                manager.autoKeys();

                manager.action(Icons.CAMERA, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_PARAM_FOLLOW_CAMERA, () ->
                {
                    PROCESS_STATE.followTarget = FOLLOW_CAMERA;

                    this.updateTargetLabels();
                });

                List<Replay> replays = film.replays.getList();

                for (int i = 0; i < replays.size(); i++)
                {
                    int index = i;
                    Replay replay = replays.get(i);

                    if (UIProcessReplaysPanel.this.isProcessed(replay))
                    {
                        continue;
                    }

                    manager.action(Icons.FILM, IKey.constant(replay.getName()), () ->
                    {
                        PROCESS_STATE.followTarget = index;

                        this.updateTargetLabels();
                    });
                }
            });
        }

        private UILabel paramLabel(IKey key, int width)
        {
            UILabel label = UI.label(key, UIConstants.CONTROL_HEIGHT);
            label.w(width);

            return label.labelAnchor(0F, 0.5F);
        }
    }

    private class UIAdvancedProcessView extends UIElement
    {
        private final UITextbox expression = new UITextbox((t) -> PROCESS_STATE.expression = t);
        private final UIText description = new UIText(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_DESCRIPTION).padding(0, 0);

        public UIAdvancedProcessView()
        {
            super();

            this.expression.setText(PROCESS_STATE.expression);
            this.expression.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_EXPRESSION_TOOLTIP);
            this.expression.relative(this).xy(0, 0).w(1F).h(20);
            this.description.relative(this.expression).y(1F, 6).w(1F);

            this.add(this.expression, this.description);
        }
    }

    private enum NormalOperation
    {
        RANDOM(ReplayBatchProcessor.Operation.RANDOM, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_RANDOM, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_RANDOM, Icons.SIX_STAR, true),
        LINE(ReplayBatchProcessor.Operation.LINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_LINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_LINE, Icons.LINE, true),
        SQUARE(ReplayBatchProcessor.Operation.SQUARE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SQUARE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SQUARE, Icons.SQUARE, true),
        SQUARE_OUTLINE(ReplayBatchProcessor.Operation.SQUARE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SQUARE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SQUARE_OUTLINE, Icons.OUTLINE, true),
        CUBE(ReplayBatchProcessor.Operation.CUBE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CUBE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_CUBE, Icons.BLOCK, true),
        CIRCLE(ReplayBatchProcessor.Operation.CIRCLE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CIRCLE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_CIRCLE, Icons.CIRCLE, true),
        CIRCLE_OUTLINE(ReplayBatchProcessor.Operation.CIRCLE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_CIRCLE_OUTLINE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_CIRCLE_OUTLINE, Icons.OUTLINE_SPHERE, true),
        SPHERE(ReplayBatchProcessor.Operation.SPHERE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SPHERE, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SPHERE, Icons.SPHERE, true),
        FIT_HEIGHT(ReplayBatchProcessor.Operation.FIT_HEIGHT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_FIT_HEIGHT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_FIT_HEIGHT, Icons.ARROW_DOWN, false),
        LOOK_AT(ReplayBatchProcessor.Operation.LOOK_AT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_LOOK_AT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_LOOK_AT, Icons.LOOKING, false),
        SHIFT(ReplayBatchProcessor.Operation.SHIFT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_SHIFT, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_SHIFT, Icons.SHIFT_TO, true),
        FOLLOW_TARGET(ReplayBatchProcessor.Operation.FOLLOW_TARGET, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_OP_FOLLOW_TARGET, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_HINT_FOLLOW_TARGET, Icons.MOVE_TO, false);

        public final ReplayBatchProcessor.Operation op;
        public final IKey title;
        public final IKey hint;
        public final Icon icon;

        /** Whether the operation works on channels the user picks; the others know which ones they need */
        public final boolean channels;

        NormalOperation(ReplayBatchProcessor.Operation op, IKey title, IKey hint, Icon icon, boolean channels)
        {
            this.channels = channels;
            this.op = op;
            this.title = title;
            this.hint = hint;
            this.icon = icon;
        }
    }
}
