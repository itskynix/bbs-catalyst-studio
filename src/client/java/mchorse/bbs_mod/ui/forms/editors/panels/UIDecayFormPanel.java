package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.DecayForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Supplier;

/**
 * The decay's panel: where it works and in which pattern, then how far it has spread and how it looks.
 * The progress, the softness of its edge, the scale of the pattern and the opacity are tracks of the
 * form, so the dope sheet keys them - the progress above all, from nothing to the whole wall.
 */
public class UIDecayFormPanel extends UIFormPanel<DecayForm>
{
    /** The colours each preset starts with: the main one and the worn rim */
    private static final int[][] PRESET_COLORS = {
        {0xff14110f, 0xff544c40},
        {0xff8a3d12, 0xffc9741f},
        {0xff3d6b22, 0xff7aa53a},
        {0xff2a2620, 0xff4a4033},
        {0xffffffff, 0xffcccccc}
    };

    public final UIButton pick;

    private final UICirculate mode;
    private final UICirculate preset;
    private final UIElement pickRow;
    private final UIElement sizeRows;

    public UIDecayFormPanel(UIForm editor)
    {
        super(editor);

        this.mode = new UICirculate((c) ->
        {
            this.form.mode.set(c.getValue());
            this.refreshVisibility();
        });
        this.mode.addLabel(UIKeys.FORMS_DECAY_MODE_SURFACE);
        this.mode.addLabel(UIKeys.FORMS_DECAY_MODE_OBJECT);
        this.mode.tooltip(UIKeys.FORMS_DECAY_MODE_TOOLTIP);

        this.preset = new UICirculate((c) ->
        {
            int value = c.getValue();

            this.form.preset.set(value);

            /* Each pattern starts in its own colours; they stay the player's to change afterwards */
            int[] colors = PRESET_COLORS[Math.max(0, Math.min(PRESET_COLORS.length - 1, value))];

            this.form.primaryColor.set(Color.rgba(colors[0]));
            this.form.edgeColor.set(Color.rgba(colors[1]));
            this.refreshVisibility();
        });
        this.preset.addLabel(UIKeys.FORMS_DECAY_PRESET_CRACKS);
        this.preset.addLabel(UIKeys.FORMS_DECAY_PRESET_RUST);
        this.preset.addLabel(UIKeys.FORMS_DECAY_PRESET_MOSS);
        this.preset.addLabel(UIKeys.FORMS_DECAY_PRESET_STAIN);
        this.preset.addLabel(UIKeys.FORMS_DECAY_PRESET_CUSTOM);
        this.preset.tooltip(UIKeys.FORMS_DECAY_PRESET_TOOLTIP);

        this.pick = new UIButton(UIKeys.FORMS_DECAY_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        UIValues.resettable(this.pick, () -> this.form.texture, null);
        this.pickRow = UI.row(this.pick);

        UISection style = this.section(UIKeys.FORMS_DECAY_STYLE, "decay.style", true);

        style.fields.add(
            UI.labelRow(UIKeys.FORMS_DECAY_MODE, this.mode),
            UI.labelRow(UIKeys.FORMS_DECAY_PRESET, this.preset),
            this.pickRow
        );

        UISection spread = this.section(UIKeys.FORMS_DECAY_SPREAD, "decay.spread", true);

        spread.fields.add(
            this.row("decay_progress", () -> this.form.decayProgress, 0D, 1D),
            this.row("edge_softness", () -> this.form.edgeSoftness, 0.01D, 0.5D),
            this.row("noise_scale", () -> this.form.noiseScale, 0.2D, 10D),
            this.row("depth_inset", () -> this.form.depthInset, 0D, 0.2D)
        );

        UISection look = this.section(UIKeys.FORMS_DECAY_LOOK, "decay.look", true);

        look.fields.add(
            UI.labelRow(key("primary_color"), UIValues.color(() -> this.form.primaryColor).direction(Direction.LEFT)),
            UI.labelRow(key("edge_color"), UIValues.color(() -> this.form.edgeColor).direction(Direction.LEFT)),
            this.row("opacity", () -> this.form.opacity, 0D, 1D),
            this.row("seed", () -> this.form.seed, 0D, 100000D)
        );

        UITrackpad width = this.trackpad("width", () -> this.form.width, 0.2D, 64D);
        UITrackpad length = this.trackpad("length", () -> this.form.length, 0.2D, 64D);
        UITrackpad height = this.trackpad("height", () -> this.form.height, 0.2D, 64D);

        this.sizeRows = UI.column(
            UI.labelRow(key("width"), width),
            UI.labelRow(key("length"), length),
            UI.labelRow(key("height"), height)
        );

        UISection volume = this.section(UIKeys.FORMS_DECAY_VOLUME, "decay.volume", true);

        volume.fields.add(this.sizeRows, UIValues.toggle(UIKeys.FORMS_DECAY_GUIDES, () -> this.form.showGuides));

        this.options.add(style, spread, look, volume);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.decay." + id);
    }

    private UITrackpad trackpad(String id, Supplier<? extends BaseValueNumber<?>> value, double min, double max)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max);
        trackpad.tooltip(L10n.lang("bbs.ui.forms.editors.decay." + id + "-tooltip"));

        return trackpad;
    }

    private UIElement row(String id, Supplier<? extends BaseValueNumber<?>> value, double min, double max)
    {
        return UI.labelRow(key(id), this.trackpad(id, value, min, max));
    }

    /** The picture is only asked for by the Custom Texture preset, the box's size only by the surface mode */
    private void refreshVisibility()
    {
        this.pickRow.setVisible(this.form != null && this.form.preset.get() == DecayForm.PRESET_CUSTOM);
        this.sizeRows.setVisible(this.form == null || this.form.mode.get() == DecayForm.MODE_SURFACE);
        this.options.resize();
    }

    @Override
    public void startEdit(DecayForm form)
    {
        super.startEdit(form);

        this.mode.setValue(Math.max(0, Math.min(1, form.mode.get())));
        this.preset.setValue(Math.max(0, Math.min(4, form.preset.get())));
        this.refreshVisibility();
    }
}
