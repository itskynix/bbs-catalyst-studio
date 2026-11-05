package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovShakeClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;

/** The camera shake clip's panel: only its dope sheet (hurt time and its max, the tilt's yaw, the death time) */
public class UIPovShakeClip extends UIPovClip<PovShakeClip>
{
    public UIPovShakeClip(PovShakeClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("shake.title"), this.editKeyframes));
    }
}
