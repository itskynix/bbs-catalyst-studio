package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovChatClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;

/** The chat clip's panel: its dope sheet (the text, how much is typed, the bar, the cursor, the sent lines, the scroll) and the command suggestions */
public class UIPovChatClip extends UIPovClip<PovChatClip>
{
    public UIToggle suggestions;

    public UIPovChatClip(PovChatClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.suggestions = this.toggle(key("chat.suggestions"), this.clip.suggestions);
        this.suggestions.tooltip(key("chat.suggestions_tooltip"));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("chat.title"), this.editKeyframes, this.suggestions));
    }
}
