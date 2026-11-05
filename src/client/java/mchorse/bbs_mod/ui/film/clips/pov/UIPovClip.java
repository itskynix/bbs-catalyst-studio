package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.pov.PovClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.List;
import java.util.function.Supplier;

/**
 * The shared part of the POV action clips' panels: their keyframe channels in a dope sheet of
 * their own, opened in the timeline's place by the "Edit keyframes" button. The rows go by the
 * channels' names in the user's language, with an icon by the kind of value; a panel says which channels are shown (all of
 * the clip's by default; the picked entry's, for the clips that hold a list).
 */
public abstract class UIPovClip<T extends PovClip> extends UIClip<T>
{
    private static final int[] COLORS = {0xff5555, 0x55ff55, 0x5599ff, 0xffaa33, 0xaa55ff, 0x33dddd, 0xffdd55, 0xff66cc};

    public UIKeyframeEditor keyframes;
    public UIButton editKeyframes;

    public UIPovClip(T clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    protected static IKey key(String id)
    {
        return L10n.lang("bbs.ui.film.pov_clips." + id);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("pov_clip_keyframes");

        this.editKeyframes = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.updateSheets();
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        });
        this.editKeyframes.keys().register(Keys.FORMS_EDIT, () -> this.editKeyframes.clickItself());
    }

    /** The channels the dope sheet shows */
    protected List<KeyframeChannel<?>> getSheetChannels()
    {
        return this.clip.getChannels();
    }

    protected void updateSheets()
    {
        this.keyframes.view.removeAllSheets();

        List<KeyframeChannel<?>> channels = this.getSheetChannels();

        for (int i = 0; i < channels.size(); i++)
        {
            KeyframeChannel<?> channel = channels.get(i);
            UIKeyframeSheet sheet = new UIKeyframeSheet(channel.getId(), this.getChannelTitle(channel.getId()), COLORS[i % COLORS.length], channel, null);
            Icon icon = this.getChannelIcon(channel);
            Supplier<Object> seed = this.getChannelSeed(channel);

            if (icon != null)
            {
                sheet.icon(icon);
            }

            if (seed != null)
            {
                sheet.seed(seed);
            }

            this.keyframes.view.addSheet(sheet);
        }
    }

    /** A row's name: the channel's own, in the user's language */
    protected IKey getChannelTitle(String id)
    {
        return key("channels." + id);
    }

    /** A row's icon, by the kind of value it keys */
    protected Icon getChannelIcon(KeyframeChannel<?> channel)
    {
        IKeyframeFactory<?> factory = channel.getFactory();

        if (factory == KeyframeFactories.ITEM_STACK || factory == KeyframeFactories.POV_SLOTS) return Icons.BLOCK;
        if (factory == KeyframeFactories.BOOLEAN) return Icons.VISIBLE;
        if (factory == KeyframeFactories.STRING) return Icons.FONT;
        if (factory == KeyframeFactories.TRANSFORM) return Icons.LAYOUT;
        if (factory == KeyframeFactories.COLOR) return Icons.COLOR;

        return null;
    }

    /** What a fresh keyframe of a row starts at, where the factory's empty value would be wrong; null for that one */
    protected Supplier<Object> getChannelSeed(KeyframeChannel<?> channel)
    {
        if (channel.getFactory() == KeyframeFactories.TRANSFORM)
        {
            return Transform::new;
        }

        return null;
    }

    @Override
    public boolean openSubEditor()
    {
        return this.openSubEditor(this.editKeyframes);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.updateSheets();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("pov_clip"))
        {
            this.updateSheets();
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "pov_clip");
        }
    }
}
