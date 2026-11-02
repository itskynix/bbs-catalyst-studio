package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.cubic.constraints.JitterControl;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * Keyframe editor for the procedural jitter / tremor track on the timeline.
 */
public class UIJitterKeyframeFactory extends UIKeyframeFactory<JitterControl>
{
    public UIToggle enabled;
    public UISliderTrackpad intensity;
    public UISliderTrackpad frequency;
    public UICirculate target;

    private boolean syncing;

    public UIJitterKeyframeFactory(Keyframe<JitterControl> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.enabled = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_JITTER_ENABLE, (b) -> this.edit((c) -> c.enabled = b.getValue()));
        this.enabled.tooltip(UIKeys.FORMS_EDITORS_MODEL_JITTER_ENABLE_TOOLTIP);

        this.intensity = new UISliderTrackpad((v) -> this.edit((c) -> c.intensity = v.floatValue()));
        this.intensity.limit(0D, 30D).values(0.1D, 0.5D, 1.0D);
        this.intensity.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_JITTER_INTENSITY_TOOLTIP);

        this.frequency = new UISliderTrackpad((v) -> this.edit((c) -> c.frequency = v.floatValue()));
        this.frequency.limit(0.1D, 20D).values(0.1D, 0.5D, 1.0D);
        this.frequency.tooltip(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_JITTER_FREQUENCY_TOOLTIP);

        this.target = new UICirculate((b) -> this.edit((c) -> c.target = b.getValue()));
        this.target.addLabel(UIKeys.FORMS_EDITORS_MODEL_JITTER_TARGET_ALL);
        this.target.addLabel(UIKeys.FORMS_EDITORS_MODEL_JITTER_TARGET_ARMS);
        this.target.addLabel(UIKeys.FORMS_EDITORS_MODEL_JITTER_TARGET_HEAD);
        this.target.addLabel(UIKeys.FORMS_EDITORS_MODEL_JITTER_TARGET_ARMS_AND_HEAD);
        this.target.tooltip(UIKeys.FORMS_EDITORS_MODEL_JITTER_TARGET_TOOLTIP);

        this.scroll.add(UI.column(
            this.enabled,
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_JITTER_INTENSITY, this.intensity).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_JITTER_FREQUENCY, this.frequency),
            UI.labelRow(UIKeys.FORMS_EDITORS_MODEL_JITTER_TARGET, this.target)
        ));

        this.display();
    }

    private void display()
    {
        JitterControl control = this.keyframe.getValue();

        if (control == null)
        {
            control = JitterControl.DEFAULT;
        }

        this.syncing = true;

        try
        {
            this.enabled.setValue(control.enabled || control.intensity > 0.0001F);
            this.intensity.setValue(control.intensity);
            this.frequency.setValue(control.frequency);
            this.target.setValue(Math.max(0, Math.min(3, control.target)));
        }
        finally
        {
            this.syncing = false;
        }
    }

    private void edit(Consumer<JitterControl> consumer)
    {
        if (this.syncing)
        {
            return;
        }

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            JitterControl control = (JitterControl) selected.getValue();

            if (control == null)
            {
                return;
            }

            selected.preNotify();
            consumer.accept(control);
            selected.postNotify();
        });
    }

    @Override
    public void update()
    {
        super.update();
        this.display();
    }
}
