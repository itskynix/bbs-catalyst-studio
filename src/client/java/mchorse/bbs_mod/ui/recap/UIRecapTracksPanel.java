package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.recap.RecapTrack;
import mchorse.bbs_mod.recap.RecapTrackKind;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The timeline panel's content: a slim bar of track controls over the studio's keyframe timeline
 * with one row per shown track.
 *
 * <p>The bar works on the <i>active</i> track: the one of the selected keyframe, else the one last
 * picked in the tracks menu. Previous and next keyframe move the cursor onto its keyframes; the
 * keyframe button puts one at the cursor, or takes away the one the cursor is on. The tracks menu
 * picks the active track, shows or hides tracks and moves them up and down the list.</p>
 *
 * <p>A new keyframe starts from what the recap shows at the cursor: the camera where the camera
 * is, the lens as it is, the speed at one, so putting one down never jumps the picture.</p>
 */
public class UIRecapTracksPanel extends UIElement
{
    private static final int BAR = 20;
    private static final int BUTTON = 20;

    private final UIRecapEditor editor;
    public final UIKeyframeEditor keyframes;
    private RecapTrackKind active = RecapTrackKind.CAMERA;

    public UIRecapTracksPanel(UIRecapEditor editor)
    {
        this.editor = editor;
        this.keyframes = new UIKeyframeEditor((callback) -> new UIRecapKeyframes(editor, callback));
        this.keyframes.relative(this).y(BAR).w(1F).h(1F, -BAR);
        this.add(this.keyframes);

        UIRecapButton tracks = new UIRecapButton(() -> Icons.LIST, UIRecapKeys.key("tracks.menu"), this::openTracksMenu);
        UIRecapButton previous = new UIRecapButton(() -> Icons.ARROW_LEFT, UIRecapKeys.key("tracks.previous"), () -> this.jump(-1));
        UIRecapButton toggle = new UIRecapButton(() -> this.keyframeAtCursor() != null ? Icons.REMOVE : Icons.ADD, UIRecapKeys.key("tracks.toggle"), this::toggleKeyframe)
            .primary(() -> this.keyframeAtCursor() != null);
        UIRecapButton next = new UIRecapButton(() -> Icons.ARROW_RIGHT, UIRecapKeys.key("tracks.next"), () -> this.jump(1));
        UIRecapButton interpolation = new UIRecapButton(() -> Icons.GRAPH, UIRecapKeys.key("tracks.interpolation"), this::openInterpolation)
            .active(() -> this.keyframes.view.getGraph().getSelected() != null);
        UIRecapButton graph = new UIRecapButton(() -> Icons.CURVES, UIRecapKeys.key("tracks.graph"), () -> this.keyframes.view.toggleGraphEditor());

        UIRecapButton[] bar = {tracks, previous, toggle, next, interpolation, graph};

        for (int i = 0; i < bar.length; i++)
        {
            bar[i].relative(this).x(2 + i * (BUTTON + 2) + (i > 0 ? 6 : 0) + (i > 3 ? 6 : 0)).y(1).w(BUTTON).h(BAR - 2);
            this.add(bar[i]);
        }

    }

    private UIRecapKeyframes view()
    {
        return (UIRecapKeyframes) this.keyframes.view;
    }

    /* Tracks */

    /** Builds the rows again from the recap: after it is opened, a step of the history, or a change of the list. */
    public void rebuild()
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null)
        {
            this.keyframes.view.removeAllSheets();

            return;
        }

        List<RecapTrackKind> shown = record.tracks.getShown();
        List<KeyframeChannel<?>> channels = new ArrayList<>();

        for (RecapTrackKind kind : shown)
        {
            channels.add(record.tracks.get(kind));
        }

        this.keyframes.setChannels(channels, (channel) -> UIRecapKeys.key("track." + channel.getId()));

        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            RecapTrackKind kind = RecapTrackKind.byId(sheet.channel.getId());

            if (kind != null)
            {
                sheet.icon(kind.icon);
                sheet.seed(this.seed(kind));
            }
        }

        if (!shown.contains(this.active) && !shown.isEmpty())
        {
            this.active = shown.get(0);
        }

        this.keyframes.view.resize();
    }

    /** The track the bar works on. */
    public RecapTrackKind getActive()
    {
        Keyframe<?> selected = this.keyframes.view.getGraph().getSelected();

        if (selected != null && selected.getParent() instanceof KeyframeChannel<?> channel)
        {
            RecapTrackKind kind = RecapTrackKind.byId(channel.getId());

            if (kind != null)
            {
                this.active = kind;
            }
        }

        return this.active;
    }

    private UIKeyframeSheet sheetOf(RecapTrackKind kind)
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if (sheet.channel.getId().equals(kind.id))
            {
                return sheet;
            }
        }

        return null;
    }

    private void openTracksMenu()
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null)
        {
            return;
        }

        RecapTrackKind active = this.getActive();

        this.getContext().replaceContextMenu((menu) ->
        {
            for (RecapTrackKind kind : record.tracks.getOrder())
            {
                boolean hidden = record.tracks.isHidden(kind);

                menu.action(kind == active ? Icons.CHECKMARK : (hidden ? Icons.INVISIBLE : kind.icon), UIRecapKeys.key("track." + kind.id), () ->
                {
                    /* Picking a hidden track brings it back */
                    this.active = kind;
                    this.keyframes.view.getGraph().clearSelection();

                    if (record.tracks.isHidden(kind))
                    {
                        record.tracks.setHidden(kind, false);
                        this.structureChanged();
                    }
                });
            }

            menu.action(Icons.ARROW_UP, UIRecapKeys.key("tracks.move_up"), () ->
            {
                if (record.tracks.move(active, -1))
                {
                    this.structureChanged();
                }
            });
            menu.action(Icons.ARROW_DOWN, UIRecapKeys.key("tracks.move_down"), () ->
            {
                if (record.tracks.move(active, 1))
                {
                    this.structureChanged();
                }
            });
            menu.action(Icons.INVISIBLE, UIRecapKeys.key("tracks.hide"), () ->
            {
                record.tracks.setHidden(active, true);
                this.structureChanged();
            });
            menu.action(Icons.VISIBLE, UIRecapKeys.key("tracks.show_all"), () ->
            {
                for (RecapTrackKind kind : RecapTrackKind.values())
                {
                    record.tracks.setHidden(kind, false);
                }

                this.structureChanged();
            });
        });
    }

    private void structureChanged()
    {
        this.rebuild();
        this.editor.commitTracks();
    }

    /* Keyframes */

    /** The keyframe of the active track the cursor is on, or null. */
    private Keyframe<?> keyframeAtCursor()
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null)
        {
            return null;
        }

        KeyframeChannel<?> channel = record.tracks.get(this.getActive());
        int cursor = this.editor.getCursor();

        for (Keyframe<?> keyframe : channel.getKeyframes())
        {
            if (Math.abs(keyframe.getTick() - cursor) < 0.5F)
            {
                return keyframe;
            }
        }

        return null;
    }

    /** A keyframe at the cursor on the active track, or, when the cursor is on one, that one taken away. */
    private void toggleKeyframe()
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null)
        {
            return;
        }

        RecapTrackKind kind = this.getActive();

        if (record.tracks.isHidden(kind))
        {
            record.tracks.setHidden(kind, false);
            this.rebuild();
        }

        Keyframe<?> existing = this.keyframeAtCursor();

        if (existing != null)
        {
            this.keyframes.view.getGraph().removeKeyframe(existing);
        }
        else
        {
            UIKeyframeSheet sheet = this.sheetOf(kind);

            if (sheet != null)
            {
                this.keyframes.view.getGraph().addKeyframeManually(sheet, this.editor.getCursor(), null);
            }
        }

        this.editor.commitTracks();
    }

    /** The cursor to the previous or next keyframe of the active track, or of any shown one if it has none. */
    private void jump(int direction)
    {
        RecapRecord record = this.editor.getRecord();

        if (record == null)
        {
            return;
        }

        List<KeyframeChannel<?>> channels = new ArrayList<>();
        KeyframeChannel<?> activeChannel = record.tracks.get(this.getActive());

        if (!activeChannel.isEmpty())
        {
            channels.add(activeChannel);
        }
        else
        {
            for (RecapTrackKind kind : record.tracks.getShown())
            {
                channels.add(record.tracks.get(kind));
            }
        }

        int cursor = this.editor.getCursor();
        Float best = null;

        for (KeyframeChannel<?> channel : channels)
        {
            for (Keyframe<?> keyframe : channel.getKeyframes())
            {
                float tick = keyframe.getTick();
                boolean ahead = direction > 0 ? tick > cursor + 0.5F : tick < cursor - 0.5F;

                if (ahead && (best == null || (direction > 0 ? tick < best : tick > best)))
                {
                    best = tick;
                }
            }
        }

        if (best != null)
        {
            this.editor.stopPlayback();
            this.editor.seek(Math.round(best));
        }
    }

    private void openInterpolation()
    {
        Keyframe<?> selected = this.keyframes.view.getGraph().getSelected();

        if (selected != null)
        {
            this.view().openInterpolation(selected);
        }
    }

    /** Selects a keyframe and opens its value in the properties panel. */
    public void showProperties(Keyframe<?> keyframe)
    {
        this.keyframes.setPropertiesVisible(true);
        this.keyframes.view.getGraph().clearSelection();
        this.keyframes.view.getGraph().selectKeyframe(keyframe);
        this.keyframes.view.getGraph().pickSelected();
    }

    /* Where new keyframes start */

    private Supplier<Object> seed(RecapTrackKind kind)
    {
        return () ->
        {
            RecapTrack.Sample sample = new RecapTrack.Sample();
            boolean known = RecapScene.INSTANCE.base(this.editor.getCursor(), sample);

            return switch (kind)
            {
                case CAMERA ->
                {
                    Transform transform = new Transform();

                    if (known)
                    {
                        transform.translate.set((float) sample.x, (float) sample.y, (float) sample.z);
                        transform.rotate.set(MathUtils.toRad(sample.pitch), MathUtils.toRad(sample.yaw), 0F);
                    }

                    yield transform;
                }
                case CAMERA_ORBIT -> new Vector4f(known ? sample.yaw : 0F, 25F, 4F, 0F);
                case TRACK_ENTITY -> this.nearestEntityName(sample, known);
                case CAMERA_SHAKE -> 20F;
                case FOV -> known ? sample.fov : 70F;
                case SPEED -> 1F;
                case TIMELAPSE -> 40F;
                case TIME_OF_DAY -> 6000F;
                case FREEZE -> Boolean.TRUE;
                case AUDIO -> "minecraft:block.note_block.bell";
            };
        };
    }

    /** The name of the living entity nearest to the camera, the player left out, or nothing. */
    private String nearestEntityName(RecapTrack.Sample sample, boolean known)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || !known)
        {
            return "";
        }

        Entity best = null;
        double distance = 64D * 64D;

        for (Entity entity : mc.world.getEntities())
        {
            if (entity == mc.player || !(entity instanceof LivingEntity))
            {
                continue;
            }

            double d = entity.squaredDistanceTo(sample.x, sample.y, sample.z);

            if (d < distance)
            {
                distance = d;
                best = entity;
            }
        }

        return best == null ? "" : best.getName().getString();
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        boolean result = super.subMouseReleased(context);

        /* Also a release over the properties panel: a value dragged there is a step */
        this.editor.commitTracks();

        return result;
    }

    @Override
    public void render(UIContext context)
    {
        RecapStyle.well(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.y + BAR);

        String label = UIRecapKeys.text("tracks.active") + ": " + UIRecapKeys.text("track." + this.getActive().id);
        int x = this.area.x + 2 + 6 * (BUTTON + 2) + 18;

        if (x + 20 < this.area.ex())
        {
            context.batcher.text(context.batcher.getFont().limitToWidth(label, this.area.ex() - x - 4), x, this.area.y + 6, RecapStyle.text(), false);
        }

        super.render(context);
    }
}
