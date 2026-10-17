package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UICentralHubScreen;

/**
 * What a "Central Hub" tab of the global strip shows: the cards every editor is entered from.
 */
public class UICentralHubPanel extends UIDashboardPanel
{
    public UICentralHubPanel(UIDashboard dashboard)
    {
        super(dashboard);

        UICentralHubScreen screen = new UICentralHubScreen(dashboard::openHubTarget);

        screen.full(this);
        this.add(screen);
    }
}
