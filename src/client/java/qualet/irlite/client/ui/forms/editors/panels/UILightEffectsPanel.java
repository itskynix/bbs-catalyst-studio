package qualet.irlite.client.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.LightEffectsRegistration;
import qualet.irlite.forms.LightEffects;
import qualet.irlite.forms.LightForm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A light's own volumetric settings (one tab) or its own outline settings plus the
 * outline replay list (the other tab). Shared by the point light and spotlight editors.
 *
     * <p>With the "own settings" switch off the light follows the global IRLights settings
     * unless numeric keyframes activate that section. "Copy global settings" seeds it from
 * the current globals and switches the light over, so tweaking from the global look is
 * one click. Every number here is also a track in the replay's Light tab.</p>
 */
public final class UILightEffectsPanel<T extends LightForm> extends UIFormPanel<T>
{
    private final List<Runnable> refresh = new ArrayList<>();
    private final LightReplayWidgets outlineReplays;

    /**
     * @param vl true = the volumetric tab, false = the outline tab
     */
    public UILightEffectsPanel(UIForm editor, boolean vl)
    {
        super(editor);

        if (vl)
        {
            this.outlineReplays = null;

            UIElement[] beam = {
                UI.label(L10n.lang("irlights.forms.vl.hint", "Off: global settings. Keyframes activate own settings.")),
                this.toggle(L10n.lang("irlights.forms.vl.custom", "Own volumetric settings"), (e) -> e.customVl),
                new UIButton(L10n.lang("irlights.forms.copy_globals", "Copy global settings"), (b) ->
                {
                    LightEffectsRegistration.copyGlobals(this.form.effects, true);
                    this.refreshAll();
                }),
                this.toggle(L10n.lang("irlights.forms.vl.enabled", "Beam"), (e) -> e.vlEnabled),
                UI.label(L10n.lang("irlights.forms.vl.intensity", "Beam intensity")), this.trackpad((e) -> e.vlIntensity, 0, 5),
                UI.label(L10n.lang("irlights.forms.vl.max_dist", "Max distance")), this.trackpad((e) -> e.vlMaxDist, 1, 256),
                this.toggle(L10n.lang("irlights.forms.vl.shadows", "Beam shadows"), (e) -> e.vlShadows),
                UI.label(L10n.lang("irlights.forms.vl.tip_boost", "Tip glow")), this.trackpad((e) -> e.vlTipBoost, 0, 4),
                UI.label(L10n.lang("irlights.forms.vl.tip_radius", "Tip radius")), this.trackpad((e) -> e.vlTipRadius, 0.01, 4)
            };
            UIElement[] noise = {
                this.toggle(L10n.lang("irlights.forms.vl.noise", "Beam noise"), (e) -> e.vlNoise),
                UI.label(L10n.lang("irlights.forms.vl.noise_amount", "Noise amount")), this.trackpad((e) -> e.vlNoiseAmount, 0, 1),
                UI.label(L10n.lang("irlights.forms.vl.noise_scale", "Noise scale")), this.trackpad((e) -> e.vlNoiseScale, 0.01, 6),
                UI.label(L10n.lang("irlights.forms.vl.noise_speed", "Drift speed")), this.trackpad((e) -> e.vlNoiseSpeed, 0, 3),
                UI.label(L10n.lang("irlights.forms.vl.noise_morph", "Noise morph")), this.trackpad((e) -> e.vlNoiseMorph, 0, 3)
            };

            this.layout(L10n.lang("irlights.forms.tab.volumetric", "Volumetric (this light)"), beam, L10n.lang("irlights.forms.vl.section_noise", "Beam noise"), noise, null, null);
        }
        else
        {
            UICirculate target = new UICirculate((c) -> this.form.effects.outlineTarget.set(c.getValue()));

            target.addLabel(L10n.lang("irlights.forms.outline.target_all", "Draw on: all"));
            target.addLabel(L10n.lang("irlights.forms.outline.target_entities", "Draw on: entities"));
            target.addLabel(L10n.lang("irlights.forms.outline.target_blocks", "Draw on: blocks"));
            this.refresh.add(() -> target.setValue(this.form.effects.outlineTarget.get()));

            this.outlineReplays = new LightReplayWidgets(this,
                L10n.lang("irlights.forms.replays.outline_selected_only", "Outline: selected replays only"),
                L10n.lang("irlights.forms.replays.choose_outlined", "Choose outlined replays..."),
                () -> this.form.effects.selectedReplays, () -> this.form.effects.outlineReplays);

            UIElement[] rim = {
                UI.label(() -> (this.form.effects.customOutline.get()
                    ? L10n.lang("irlights.forms.outline.status_custom", "Own outline settings are active.")
                    : (IrliteConfig.outline()
                        ? L10n.lang("irlights.forms.outline.status_global_on", "Global outline: ON. Keyframes activate own settings.")
                        : L10n.lang("irlights.forms.outline.status_global_off", "Global outline: OFF. Keyframes activate own settings."))).get()),
                this.toggle(L10n.lang("irlights.forms.outline.custom", "Own outline settings"), (e) -> e.customOutline),
                new UIButton(L10n.lang("irlights.forms.copy_globals", "Copy global settings"), (b) ->
                {
                    LightEffectsRegistration.copyGlobals(this.form.effects, false);
                    this.refreshAll();
                }),
                this.toggle(L10n.lang("irlights.forms.outline.enabled", "Outline"), (e) -> e.outline),
                target,
                UI.label(L10n.lang("irlights.forms.outline.strength", "Strength")), this.trackpad((e) -> e.outlineStrength, 0, 3),
                UI.label(L10n.lang("irlights.forms.outline.thickness", "Thickness (px, 0 = global)")), this.trackpad((e) -> e.outlinePixelSize, 0, 6),
                UI.label(L10n.lang("irlights.forms.outline.fresnel", "Fresnel falloff")), this.trackpad((e) -> e.outlineFresnel, 1, 4),
                UI.label(L10n.lang("irlights.forms.outline.back", "Backlight rim")), this.trackpad((e) -> e.outlineBack, 0, 2)
            };
            UIElement[] extras = {
                this.toggle(L10n.lang("irlights.forms.outline.front", "Front catch-light"), (e) -> e.outlineFront),
                UI.label(L10n.lang("irlights.forms.outline.front_strength", "Front strength")), this.trackpad((e) -> e.outlineFrontStrength, 0, 1.5),
                this.toggle(L10n.lang("irlights.forms.outline.glow", "Inner glow"), (e) -> e.outlineGlow),
                UI.label(L10n.lang("irlights.forms.outline.glow_strength", "Glow strength")), this.trackpad((e) -> e.outlineGlowStrength, 0, 0.75)
            };

            /* Inherited values are inactive: don't present local controls as working overrides. */
            for (int i = 3; i < rim.length; i++)
            {
                this.ownOutlineOnly(rim[i]);
            }
            for (UIElement element : extras)
            {
                this.ownOutlineOnly(element);
            }

            this.layout(L10n.lang("irlights.forms.tab.outline", "Outline (this light)"), rim, L10n.lang("irlights.forms.outline.section_extras", "Front rim & glow"), extras, L10n.lang("irlights.forms.outline.section_replays", "Replays"), this.outlineReplays.elements());
        }
    }

    private void ownOutlineOnly(UIElement element)
    {
        element.valueBinding(() -> element.setEnabled(this.form.effects.customOutline.get()));
    }

    private void layout(IKey first, UIElement[] a, IKey second, UIElement[] b, IKey third, UIElement[] c)
    {
        // Collapsible sections need BBS's UISection (newer builds only); see IrliteBbsCompat.
        if (IrliteBbsCompat.SECTIONS)
        {
            this.options.add(IrliteFormSections.section(first, a), IrliteFormSections.spaced(second, b));

            if (c != null)
            {
                this.options.add(IrliteFormSections.spaced(third, c));
            }
        }
        else
        {
            this.options.add(a);
            this.options.add(b);

            if (c != null)
            {
                this.options.add(c);
            }
        }
    }

    private UIToggle toggle(IKey label, Function<LightEffects, ValueBoolean> value)
    {
        UIToggle toggle = new UIToggle(label, (b) -> value.apply(this.form.effects).set(b.getValue()));

        this.refresh.add(() -> toggle.setValue(value.apply(this.form.effects).get()));

        return toggle;
    }

    private UITrackpad trackpad(Function<LightEffects, ValueFloat> value, double min, double max)
    {
        UITrackpad trackpad = IrliteTrackpads.create((v) -> value.apply(this.form.effects).set(v.floatValue()), min, max);

        this.refresh.add(() -> trackpad.setValue(value.apply(this.form.effects).get()));

        return trackpad;
    }

    private void refreshAll()
    {
        for (Runnable runnable : this.refresh)
        {
            runnable.run();
        }

        if (this.outlineReplays != null)
        {
            this.outlineReplays.refresh();
        }
    }

    @Override
    public void startEdit(T form)
    {
        super.startEdit(form);

        this.refreshAll();
    }
}
