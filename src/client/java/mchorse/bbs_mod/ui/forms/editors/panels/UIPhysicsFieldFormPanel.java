package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.PhysicsFieldForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The physics field's panel: which kind of field it is and where it reaches, then the gravity, the
 * drag and, for the Big Rip, the pull. The values that can change during a shot are tracks of the
 * form, so the dope sheet keys them: the gravity scale and the pull strength above all.
 */
public class UIPhysicsFieldFormPanel extends UIFormPanel<PhysicsFieldForm>
{
    private final UICirculate mode;
    private final UICirculate shape;

    public UIPhysicsFieldFormPanel(UIForm editor)
    {
        super(editor);

        this.mode = new UICirculate((c) -> this.form.mode.set(c.getValue()));
        this.mode.addLabel(UIKeys.FORMS_PHYSICS_FIELD_MODE_ZERO_G);
        this.mode.addLabel(UIKeys.FORMS_PHYSICS_FIELD_MODE_BIG_RIP);
        this.mode.tooltip(UIKeys.FORMS_PHYSICS_FIELD_MODE_TOOLTIP);

        this.shape = new UICirculate((c) -> this.form.shape.set(c.getValue()));
        this.shape.addLabel(UIKeys.FORMS_PHYSICS_FIELD_SHAPE_SPHERE);
        this.shape.addLabel(UIKeys.FORMS_PHYSICS_FIELD_SHAPE_BOX);
        this.shape.addLabel(UIKeys.FORMS_PHYSICS_FIELD_SHAPE_SCENE);
        this.shape.tooltip(UIKeys.FORMS_PHYSICS_FIELD_SHAPE_TOOLTIP);

        UISection region = this.section(UIKeys.FORMS_PHYSICS_FIELD_REGION, "physics_field.region", true);

        region.fields.add(
            UI.labelRow(UIKeys.FORMS_PHYSICS_FIELD_MODE, this.mode),
            UI.labelRow(UIKeys.FORMS_PHYSICS_FIELD_SHAPE, this.shape),
            this.row("radius", () -> this.form.radius, 0.5D, 512D),
            this.row("width", () -> this.form.width, 0.5D, 512D),
            this.row("length", () -> this.form.length, 0.5D, 512D),
            this.row("height", () -> this.form.height, 0.5D, 512D),
            UIValues.toggle(UIKeys.FORMS_PHYSICS_FIELD_GUIDES, () -> this.form.showGuides)
        );

        UISection gravity = this.section(UIKeys.FORMS_PHYSICS_FIELD_GRAVITY, "physics_field.gravity", true);

        gravity.fields.add(
            this.row("gravity_scale", () -> this.form.gravityScale, -2D, 2D),
            this.row("damping", () -> this.form.damping, 0D, 5D)
        );

        UISection rip = this.section(UIKeys.FORMS_PHYSICS_FIELD_RIP, "physics_field.rip", true);

        rip.fields.add(
            this.row("pull_strength", () -> this.form.pullStrength, -200D, 200D),
            this.row("falloff", () -> this.form.falloff, 0D, 4D),
            this.row("turbulence", () -> this.form.turbulence, 0D, 50D)
        );

        this.options.add(region, gravity, rip);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.physics_field." + id);
    }

    private UIElement row(String id, Supplier<? extends BaseValueNumber<?>> value, double min, double max)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max);
        trackpad.tooltip(L10n.lang("bbs.ui.forms.editors.physics_field." + id + "-tooltip"));

        return UI.labelRow(key(id), trackpad);
    }

    @Override
    public void startEdit(PhysicsFieldForm form)
    {
        super.startEdit(form);

        this.mode.setValue(Math.max(0, Math.min(1, form.mode.get())));
        this.shape.setValue(Math.max(0, Math.min(2, form.shape.get())));
        this.options.resize();
    }
}
