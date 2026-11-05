package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovBossBarClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The boss bar clip's panel: the preset (a custom bar, or the dragon's, the wither's, a raid's),
 * and the dope sheet of its name, health, colour and style. The colour and the style are numbers
 * in the game's order, which the tooltips spell out.
 */
public class UIPovBossBarClip extends UIPovClip<PovBossBarClip>
{
    public UIButton preset;

    public UIPovBossBarClip(PovBossBarClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.preset = new UIButton(key("boss_bar.preset"), (b) -> this.getContext().replaceContextMenu((menu) ->
        {
            for (String preset : PovBossBarClip.PRESETS)
            {
                menu.action(Icons.SKULL, key("boss_bar.presets." + preset), preset.equals(this.clip.preset.get()), () ->
                {
                    this.editor.editMultiple(this.clip.preset, (v) -> v.set(preset));
                });
            }
        }));
        this.bind(this.preset, () -> this.preset.label = key("boss_bar.presets." + this.clip.preset.get()));
        this.editKeyframes.tooltip(key("boss_bar.channels"));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("boss_bar.title"), this.preset, this.editKeyframes));
    }
}
