package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.LatticeKeyframeFactory;

import java.util.Arrays;

/**
 * Editor for the {@code lattice_center} keyframe track: where the whole cage stands at that moment,
 * an offset in blocks on top of the lattice's shift, on every selected keyframe at once.
 */
public class UILatticeCenterKeyframeFactory extends UIKeyframeFactory<float[]>
{
    public final UITrackpad[] pads = new UITrackpad[LatticeKeyframeFactory.CENTER_LENGTH];

    private boolean syncing;

    public UILatticeCenterKeyframeFactory(Keyframe<float[]> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE};
        String[] axes = {"x", "y", "z"};

        for (int i = 0; i < this.pads.length; i++)
        {
            final int index = i;
            UITrackpad pad = new UITrackpad((v) -> this.edit(index, v.floatValue())).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);

            pad.textbox.setColor(colors[i]);
            pad.tooltip(L10n.lang("bbs.ui.keyframes.bulge." + axes[i]));
            this.pads[i] = pad;
        }

        this.scroll.add(UI.column(
            UI.label(key("title")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.pads[0], this.pads[1], this.pads[2])
        ));

        this.display();
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.keyframes.lattice_center." + id);
    }

    private void display()
    {
        float[] center = this.keyframe.getValue();

        this.syncing = true;

        try
        {
            for (int i = 0; i < this.pads.length; i++)
            {
                this.pads[i].setValue(center != null && i < center.length ? center[i] : 0D);
            }
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(int index, float value)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.<float[]>forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            float[] center = selected.getValue();

            if (center == null)
            {
                return;
            }

            if (center.length < LatticeKeyframeFactory.CENTER_LENGTH)
            {
                center = Arrays.copyOf(center, LatticeKeyframeFactory.CENTER_LENGTH);
                selected.setValue(center);
            }

            selected.preNotify();
            center[index] = value;
            selected.postNotify();
        });
    }
}
