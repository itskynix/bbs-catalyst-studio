package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.forms.PortalForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIPortalFormPanel extends UIFormPanel<PortalForm>
{
    public UICirculate shape;
    public UIColor frameColor;
    public UITrackpad frequency;
    public UILabel status;

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.portal." + name);
    }

    public UIPortalFormPanel(UIForm editor)
    {
        super(editor);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        UITrackpad height = UIValues.trackpad(() -> this.form.height);

        width.limit(0, 1000).values(0.1F, 0.01F, 0.5F).tooltip(key("width_tooltip"));
        height.limit(0, 1000).values(0.1F, 0.01F, 0.5F).tooltip(key("height_tooltip"));

        /* A third portal on a frequency is refused: the field keeps what it had */
        this.frequency = UIValues.trackpad(() -> this.form.frequency);
        this.frequency.limit(1, 99, true).tooltip(key("frequency_tooltip"));

        Consumer<Double> set = this.frequency.callback;

        this.frequency.callback = (value) ->
        {
            int requested = (int) Math.round(value);

            if (FormPortals.isTaken(this.form, requested))
            {
                this.frequency.setValue(this.form.frequency.get());
                this.getContext().notifyError(key("frequency_taken"));

                return;
            }

            set.accept(value);
        };

        this.shape = new UICirculate((b) -> this.form.shape.set(b.getValue()));
        this.shape.addLabel(key("shape.rectangle"));
        this.shape.addLabel(key("shape.circle"));
        this.shape.tooltip(key("shape_tooltip"));
        UIValues.resettable(this.shape, () -> this.form.shape, () -> this.shape.setValue(this.form.shape.get()));

        UITrackpad quality = UIValues.trackpad(() -> this.form.qualityScale);

        quality.limit(10, 250).values(5F, 1F, 25F).tooltip(key("quality_scale_tooltip"));

        this.frameColor = UIValues.color(() -> this.form.frameColor).withAlpha();

        /* Whether the portal has a partner, as the scene draws it right now */
        this.status = new UILabel(key("status.waiting"))
        {
            @Override
            public void render(UIContext context)
            {
                PortalForm form = UIPortalFormPanel.this.form;

                if (form != null && form.mirror.get())
                {
                    this.label = key("status.mirror");
                    this.color = Colors.GREEN;
                }
                else if (form != null && FormPortals.isConflicted(form))
                {
                    this.label = key("status.invalid");
                    this.color = Colors.RED;
                }
                else if (form != null && FormPortals.getLinked(form) != null)
                {
                    this.label = key("status.linked");
                    this.color = Colors.GREEN;
                }
                else
                {
                    this.label = key("status.waiting");
                    this.color = Colors.ORANGE;
                }

                super.render(context);
            }
        };

        this.options.add(
            UI.label(key("width")), width,
            UI.label(key("height")), height,
            UI.labelRow(key("shape"), this.shape),
            UI.label(key("frequency")), this.frequency,
            this.status,
            UIValues.toggle(key("mirror"), () -> this.form.mirror).tooltip(key("mirror_tooltip")),
            UI.label(key("quality_scale")), quality,
            UIValues.toggle(key("show_frame"), () -> this.form.showFrame).tooltip(key("show_frame_tooltip")),
            UI.labelRow(key("frame_color"), this.frameColor)
        );
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.frameColor.picker.removeFromParent();
    }
}
