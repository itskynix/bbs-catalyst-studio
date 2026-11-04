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
 * Editor for the {@code lattice_points} keyframe track: the control points of the form's lattice at
 * that moment. A point of the stored 3x3x3 grid is picked by its position along each axis (1 to 3;
 * a 2x2x2 lattice uses 1 and 3) and its shift is edited, on every selected keyframe at once.
 */
public class UILatticeKeyframeFactory extends UIKeyframeFactory<float[]>
{
    public UITrackpad cellX;
    public UITrackpad cellY;
    public UITrackpad cellZ;
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad z;

    private boolean syncing;

    public UILatticeKeyframeFactory(Keyframe<float[]> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.cellX = this.cell();
        this.cellY = this.cell();
        this.cellZ = this.cell();
        this.x = this.axis(0, Colors.RED);
        this.y = this.axis(1, Colors.GREEN);
        this.z = this.axis(2, Colors.BLUE);

        this.scroll.add(UI.column(
            UI.label(key("cell")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.cellX, this.cellY, this.cellZ),
            UI.label(key("shift")).marginTop(UIConstants.SECTION_GAP),
            UI.row(this.x, this.y, this.z)
        ));

        this.display();
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.keyframes.lattice." + id);
    }

    private UITrackpad cell()
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.display());

        trackpad.integer().limit(1, 3).increment(1D).values(1D, 1D, 1D);
        trackpad.tooltip(key("cell_tooltip"));
        trackpad.setValue(1D);

        return trackpad;
    }

    private UITrackpad axis(int axis, int color)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.edit(axis, v.floatValue())).increment(0.0625D).values(0.0625D, 0.01D, 0.25D);

        trackpad.textbox.setColor(color);
        trackpad.tooltip(key("shift_tooltip"));

        return trackpad;
    }

    /** The first float of the picked point in the stored grid */
    private int slot()
    {
        int i = (int) Math.round(this.cellX.getValue()) - 1;
        int j = (int) Math.round(this.cellY.getValue()) - 1;
        int k = (int) Math.round(this.cellZ.getValue()) - 1;

        return ((i * 3 + j) * 3 + k) * 3;
    }

    private void display()
    {
        float[] points = this.keyframe.getValue();
        int slot = this.slot();

        this.syncing = true;

        try
        {
            this.x.setValue(points != null && slot + 2 < points.length ? points[slot] : 0D);
            this.y.setValue(points != null && slot + 2 < points.length ? points[slot + 1] : 0D);
            this.z.setValue(points != null && slot + 2 < points.length ? points[slot + 2] : 0D);
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(int axis, float value)
    {
        if (this.syncing)
        {
            return;
        }

        int slot = this.slot() + axis;

        UIReplaysEditorUtils.<float[]>forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            float[] points = selected.getValue();

            if (points == null || slot >= LatticeKeyframeFactory.LENGTH)
            {
                return;
            }

            if (points.length < LatticeKeyframeFactory.LENGTH)
            {
                points = Arrays.copyOf(points, LatticeKeyframeFactory.LENGTH);
                selected.setValue(points);
            }

            selected.preNotify();
            points[slot] = value;
            selected.postNotify();
        });
    }
}
