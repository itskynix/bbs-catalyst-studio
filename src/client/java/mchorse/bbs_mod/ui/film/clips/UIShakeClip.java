package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.UITextboxHelp;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIShakeClip extends UIClip<ShakeClip>
{
    public UICirculate preset;
    public UICirculate mode;
    public UITrackpad frequency;
    public UITrackpad amplitude;
    public UITrackpad rotationalWeight;
    public UITrackpad positionalWeight;
    public UITrackpad macroWobble;
    public UITrackpad microJitter;
    public UITrackpad rollWeight;
    public UITrackpad fovWeight;
    public UITextboxHelp expression;
    public UIBitToggle active;
    public UIElement expressionSection;

    public UIKeyframeEditor keyframes;
    public UIButton editKeyframes;

    public UIShakeClip(ShakeClip modifier, IUIClipsDelegate editor)
    {
        super(modifier, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.preset = new UICirculate((b) ->
        {
            this.clip.applyPreset(b.getValue());
            this.fillData();
        });
        this.preset.addLabel(UIKeys.CAMERA_PANELS_SHAKE_PRESET_CUSTOM);
        this.preset.addLabel(UIKeys.CAMERA_PANELS_SHAKE_PRESET_HANDHELD);
        this.preset.addLabel(UIKeys.CAMERA_PANELS_SHAKE_PRESET_PANIC);
        this.preset.addLabel(UIKeys.CAMERA_PANELS_SHAKE_PRESET_IMPACT);
        this.preset.tooltip(UIKeys.CAMERA_PANELS_SHAKE_PRESET, Direction.BOTTOM);

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

        this.macroWobble = this.trackpad(this.clip.macroWobble);
        this.macroWobble.tooltip(UIKeys.CAMERA_PANELS_SHAKE_MACRO_WOBBLE, Direction.BOTTOM);

        this.microJitter = this.trackpad(this.clip.microJitter);
        this.microJitter.tooltip(UIKeys.CAMERA_PANELS_SHAKE_MICRO_JITTER, Direction.BOTTOM);

        this.rollWeight = this.trackpad(this.clip.rollWeight);
        this.rollWeight.tooltip(UIKeys.CAMERA_PANELS_SHAKE_ROLL_WEIGHT, Direction.BOTTOM);

        this.fovWeight = this.trackpad(this.clip.fovWeight);
        this.fovWeight.tooltip(UIKeys.CAMERA_PANELS_SHAKE_FOV_WEIGHT, Direction.BOTTOM);

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

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("shake_keyframes");

        this.editKeyframes = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.updateKeyframeSheets();
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            if (this.keyframes.view.getGraph() != null)
            {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
        this.editKeyframes.keys().register(Keys.FORMS_EDIT, () -> this.editKeyframes.clickItself());
    }

    private void updateKeyframeSheets()
    {
        this.keyframes.view.removeAllSheets();
        this.keyframes.view.addSheet(new UIKeyframeSheet("shake_intensity", UIKeys.CAMERA_PANELS_SHAKE_AMOUNT, 0xFF5555, this.clip.intensityChannel, null).icon(Icons.SOUND).seed(() -> 1.0));
        this.keyframes.view.addSheet(new UIKeyframeSheet("shake_frequency", UIKeys.CAMERA_PANELS_SHAKE_FREQUENCY, 0xFFAA00, this.clip.frequencyChannel, null).icon(Icons.REFRESH).seed(() -> 1.0));
        this.keyframes.view.addSheet(new UIKeyframeSheet("shake_fov", UIKeys.CAMERA_PANELS_SHAKE_FOV_WEIGHT, 0x55AAFF, this.clip.fovChannel, null).icon(Icons.FULLSCREEN).seed(() -> 1.0));
        this.keyframes.view.addSheet(new UIKeyframeSheet("shake_roll", UIKeys.CAMERA_PANELS_SHAKE_ROLL_WEIGHT, 0xAA55FF, this.clip.rollChannel, null).icon(Icons.ORBIT).seed(() -> 1.0));
    }

    @Override
    public boolean openSubEditor()
    {
        return this.openSubEditor(this.editKeyframes);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.expressionSection = this.section(UIKeys.CAMERA_PANELS_EXPRESSION, this.expression);

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:shake"),
            UI.row(UIConstants.MARGIN, 0, 20, this.preset, this.mode),
            UI.row(UIConstants.MARGIN, 0, 20, this.frequency, this.amplitude),
            UI.row(UIConstants.MARGIN, 0, 20, this.rotationalWeight, this.positionalWeight),
            UI.row(UIConstants.MARGIN, 0, 20, this.macroWobble, this.microJitter),
            UI.row(UIConstants.MARGIN, 0, 20, this.rollWeight, this.fovWeight),
            this.editKeyframes
        ));
        this.panels.add(this.expressionSection);
        this.panels.add(this.active);

        this.updateModeVisibility();
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.updateDuration(this.clip.duration.get());
        this.updateKeyframeSheets();
        this.preset.setValue(this.clip.preset.get());
        this.mode.setValue(this.clip.mode.get());
        this.updateModeVisibility();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("shake"))
        {
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
            data.putString("embed", "shake");
        }
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