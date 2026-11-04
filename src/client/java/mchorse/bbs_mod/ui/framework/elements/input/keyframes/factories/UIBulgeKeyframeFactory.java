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
 * Editor for the {@code lattice_bulge} keyframe track: where the bulge sphere is, how big and how
 * strong, at that moment, on every selected keyframe at once. Keyed one place after another along a
 * model, it sends a swelling down it.
 */
public class UIBulgeKeyframeFactory extends UIKeyframeFactory<float[]>
{
    public final UITrackpad[] pads = new UITrackpad[LatticeKeyframeFactory.BULGE_LENGTH];

    private boolean syncing;

    public UIBulgeKeyframeFactory(Keyframe<float[]> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.WHITE, Colors.WHITE};

        for (int i = 0; i < this.pads.length; i++)
        {
            final int index = i;
            UITrackpad pad = new UITrackpad((v) -> this.edit(index, v.floatValue())).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);

            pad.textbox.setColor(colors[i]);
            pad.tooltip(key(new String[] {"x", "y", "z", "radius", "strength"}[i]));
            this.pads[i] = pad;
        }

        this.pads[3].limit(0D, 16D);
        this.pads[4].limit(-8D, 8D);

        this.scroll.add(UI.column(
            UI.label(key("center")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.pads[0], this.pads[1], this.pads[2]),
            UI.label(key("size")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.pads[3], this.pads[4])
        ));

        this.display();
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.keyframes.bulge." + id);
    }

    private void display()
    {
        float[] sphere = this.keyframe.getValue();

        this.syncing = true;

        try
        {
            for (int i = 0; i < this.pads.length; i++)
            {
                this.pads[i].setValue(sphere != null && i < sphere.length ? sphere[i] : 0D);
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
            float[] sphere = selected.getValue();

            if (sphere == null)
            {
                return;
            }

            if (sphere.length < LatticeKeyframeFactory.BULGE_LENGTH)
            {
                sphere = Arrays.copyOf(sphere, LatticeKeyframeFactory.BULGE_LENGTH);
                selected.setValue(sphere);
            }

            selected.preNotify();
            sphere[index] = value;
            selected.postNotify();
        });
    }
}
