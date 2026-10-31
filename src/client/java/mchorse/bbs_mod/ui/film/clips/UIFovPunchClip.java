package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.FovPunchClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

/** The panel of the FOV punch: how hard, how long, and how it settles. */
public class UIFovPunchClip extends UIClip<FovPunchClip>
{
    public UITrackpad intensity;
    public UITrackpad punchDuration;
    public UICirculate decay;

    public UIFovPunchClip(FovPunchClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.camera.punch." + name);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.intensity = this.trackpad(this.clip.intensity);
        this.intensity.limit(this.clip.intensity).tooltip(key("intensity_tooltip"));

        this.punchDuration = this.trackpad(this.clip.punchDuration);
        this.punchDuration.limit(this.clip.punchDuration).tooltip(key("duration_tooltip"));

        this.decay = new UICirculate((b) -> this.editor.editMultiple(this.clip.decay, (value) -> value.set(b.getValue())));
        this.decay.addLabel(key("decay.exponential"));
        this.decay.addLabel(key("decay.elastic"));
        this.decay.tooltip(key("decay_tooltip"));
        this.bind(this.decay, () -> this.decay.setValue(this.clip.decay.get()));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("intensity"), this.intensity));
        this.panels.add(this.section(key("duration"), this.punchDuration));
        this.panels.add(this.section(key("decay"), this.decay));
    }
}
