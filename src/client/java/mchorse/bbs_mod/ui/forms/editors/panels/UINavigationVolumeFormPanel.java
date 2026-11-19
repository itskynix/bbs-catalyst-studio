package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.NavigationVolumeForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.function.Supplier;

/**
 * The navigation volume's panel: the box, the agent it is meant for, the button that scans the mesh
 * again, and how the mesh is shown with the P key.
 */
public class UINavigationVolumeFormPanel extends UIFormPanel<NavigationVolumeForm>
{
    public UINavigationVolumeFormPanel(UIForm editor)
    {
        super(editor);

        UISection box = this.section(UIKeys.FORMS_NAV_VOLUME_BOX, "nav_volume.box", true);

        box.fields.add(
            this.row("width", () -> this.form.width, 1D, 128D),
            this.row("length", () -> this.form.length, 1D, 128D),
            this.row("height", () -> this.form.height, 1D, 64D)
        );

        UISection agent = this.section(UIKeys.FORMS_NAV_VOLUME_AGENT, "nav_volume.agent", true);

        agent.fields.add(
            this.row("agent_radius", () -> this.form.agentRadius, 0.1D, 1.5D),
            this.row("agent_height", () -> this.form.agentHeight, 0.5D, 4D),
            this.row("max_step_height", () -> this.form.maxStepHeight, 0.1D, 1.25D)
        );

        UIButton rebuild = new UIButton(UIKeys.FORMS_NAV_VOLUME_REBUILD, (b) -> this.form.rebuild.set(this.form.rebuild.get() + 1));

        rebuild.tooltip(UIKeys.FORMS_NAV_VOLUME_REBUILD_TOOLTIP);

        UISection mesh = this.section(UIKeys.FORMS_NAV_VOLUME_MESH, "nav_volume.mesh", true);

        mesh.fields.add(
            this.toggle("auto_update", () -> this.form.autoUpdate),
            rebuild,
            this.toggle("show_borders", () -> this.form.showBorders),
            this.row("preview_opacity", () -> this.form.previewOpacity, 0.1D, 1D),
            this.toggle("show_guides", () -> this.form.showGuides),
            UI.label(UIKeys.FORMS_NAV_VOLUME_PREVIEW_HINT).color(0x88ffffff)
        );

        this.options.add(box, agent, mesh);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.navigation_volume." + id);
    }

    private UIElement row(String id, Supplier<? extends BaseValueNumber<?>> value, double min, double max)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max);
        trackpad.tooltip(L10n.lang("bbs.ui.forms.editors.navigation_volume." + id + "-tooltip"));

        return UI.labelRow(key(id), trackpad);
    }

    private UIElement toggle(String id, Supplier<ValueBoolean> value)
    {
        UIElement toggle = UIValues.toggle(key(id), value);

        toggle.tooltip(L10n.lang("bbs.ui.forms.editors.navigation_volume." + id + "-tooltip"));

        return toggle;
    }

    @Override
    public void startEdit(NavigationVolumeForm form)
    {
        super.startEdit(form);

        this.options.resize();
    }
}
