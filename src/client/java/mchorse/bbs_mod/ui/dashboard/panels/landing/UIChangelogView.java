package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * The "what is new" window of {@link HubChangelog} on its own, over whatever editor is up — opened
 * from the menu bar's Help menu and the About window. It takes every click and the wheel while it is
 * open, and removes itself once the window has faded out.
 */
public class UIChangelogView extends UIElement
{
    private final HubChangelog changelog = new HubChangelog();

    public UIChangelogView()
    {
        this.keys().register(Keys.CLOSE, this.changelog::close);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        this.changelog.mouseClicked(context);

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        this.changelog.mouseScrolled(context);

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        this.changelog.render(context, this.area);

        if (this.changelog.isGone())
        {
            this.removeFromParent();
        }
    }
}
