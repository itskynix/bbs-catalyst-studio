package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.utils.UITextboxHelp;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIShakeClip extends UIClip<ShakeClip>
{
    public UICirculate mode;
    public UITrackpad frequency;
    public UITrackpad amplitude;
    public UITrackpad rotationalWeight;
    public UITrackpad positionalWeight;
    public UITextboxHelp expression;
    public UIBitToggle active;
    public UIElement expressionSection;

    public UIShakeClip(ShakeClip modifier, IUIClipsDelegate editor)
    {
        super(modifier, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.mode = new UICirculate((b) ->
        {
            this.clip.mode.set(b.getValue());
            this.updateModeVisibility();
        });
        this.mode.addLabel(IKey.raw("Sine"));
        this.mode.addLabel(IKey.raw("Cosine"));
        this.mode.addLabel(IKey.raw("Perlin Noise"));
        this.mode.addLabel(IKey.raw("Math Expression"));
        this.mode.tooltip(UIKeys.CAMERA_PANELS_SHAKE_MODE, Direction.BOTTOM);

        this.frequency = this.trackpad(this.clip.frequency);
        this.frequency.tooltip(UIKeys.CAMERA_PANELS_SHAKE_FREQUENCY, Direction.BOTTOM);

        this.amplitude = this.trackpad(this.clip.amplitude);
        this.amplitude.tooltip(UIKeys.CAMERA_PANELS_SHAKE_AMOUNT, Direction.BOTTOM);

        this.rotationalWeight = this.trackpad(this.clip.rotationalWeight);
        this.rotationalWeight.tooltip(UIKeys.CAMERA_PANELS_SHAKE_ROTATIONAL_WEIGHT, Direction.BOTTOM);

        this.positionalWeight = this.trackpad(this.clip.positionalWeight);
        this.positionalWeight.tooltip(UIKeys.CAMERA_PANELS_SHAKE_POSITIONAL_WEIGHT, Direction.BOTTOM);

        this.expression = new UITextboxHelp(1000, (str) ->
        {
            this.clip.expression.setExpression(str);
            this.expression.setColor(!this.clip.expression.isErrored() ? Colors.WHITE : Colors.RED);
        });
        this.expression.link("https://github.com/mchorse/aperture/wiki/Math-Expressions").tooltip(UIKeys.CAMERA_PANELS_MATH);

        this.bindOnDemand(this.expression, () ->
        {
            this.expression.setText(this.clip.expression.toString());
            this.expression.setColor(Colors.WHITE);
        });

        this.active = this.bind(new UIBitToggle((value) -> this.clip.active.set(value)).all(), () -> this.active.setValue(this.clip.active.get()));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.expressionSection = this.section(UIKeys.CAMERA_PANELS_EXPRESSION, this.expression);

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:shake"),
            this.mode,
            UI.row(UIConstants.MARGIN, 0, 20, this.frequency, this.amplitude),
            UI.row(UIConstants.MARGIN, 0, 20, this.rotationalWeight, this.positionalWeight)
        ));
        this.panels.add(this.expressionSection);
        this.panels.add(this.active);

        this.updateModeVisibility();
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.mode.setValue(this.clip.mode.get());
        this.updateModeVisibility();
    }

    private void updateModeVisibility()
    {
        if (this.expressionSection != null)
        {
            this.expressionSection.setVisible(this.clip.mode.get() == ShakeClip.MODE_MATH);
            if (this.panels != null)
            {
                this.panels.resize();
            }
        }
    }
}