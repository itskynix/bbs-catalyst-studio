package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Recap Studio: the picker of saved recaps, and the editor of the one that was picked. It needs no
 * world to list, rename or delete recaps.
 */
public class UIRecapPanel extends UIDashboardPanel
{
    public final UIRecapHome home;
    public final UIRecapEditor editor;

    public UIRecapPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.home = new UIRecapHome(this);
        this.editor = new UIRecapEditor(this);

        this.home.relative(this).w(1F).h(1F);
        this.editor.relative(this).w(1F).h(1F);
        this.editor.setVisible(false);

        this.add(this.home, this.editor);

        this.onAppear(this::refresh);
        this.onDisappear(this.editor::stopPlayback);
    }

    private void refresh()
    {
        if (this.home.isVisible())
        {
            this.home.refresh();
        }
    }

    public void openEditor(RecapRecord record)
    {
        this.editor.open(record);
        this.home.setVisible(false);
        this.editor.setVisible(true);
        this.editor.resize();
    }

    public void openHome()
    {
        this.editor.stopPlayback();
        this.editor.setVisible(false);
        this.home.setVisible(true);
        this.home.refresh();
    }

    @Override
    public void update()
    {
        super.update();

        if (this.editor.isVisible())
        {
            this.editor.tick();
        }
    }

    @Override
    public void renderPanelBackground(UIContext context)
    {
        super.renderPanelBackground(context);
    }
}
