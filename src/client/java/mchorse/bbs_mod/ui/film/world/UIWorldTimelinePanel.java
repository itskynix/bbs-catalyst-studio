package mchorse.bbs_mod.ui.film.world;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.world.WorldEventBlock;
import mchorse.bbs_mod.film.world.WorldTimeline;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel;

/**
 * World Editor / Timeline panel hosting {@link UIWorldTimeline} and the right-side {@link UIWorldEventBlockPanel}.
 */
public class UIWorldTimelinePanel extends UITimelinePanel
{
    public final UIFilmPanel filmPanel;
    public final UIWorldTimeline timeline;

    private UIWorldEventBlockPanel panel;
    private boolean hasTimeline;

    public UIWorldTimelinePanel(UIFilmPanel filmPanel)
    {
        super();

        this.filmPanel = filmPanel;
        this.timeline = new UIWorldTimeline(this);

        this.add(this.timeline.full(this));
    }

    public UIWorldTimelinePanel target(UIElement target)
    {
        this.setTarget(target);
        this.setEmptyState(this::getEmptyLabel);

        return this;
    }

    private IKey getEmptyLabel()
    {
        if (!this.hasTimeline)
        {
            return null;
        }

        WorldTimeline worldTimeline = this.timeline.getTimeline();

        return worldTimeline == null || worldTimeline.getBlocks().isEmpty()
            ? UIKeys.WORLD_TIMELINE_EMPTY_ADD
            : UIKeys.WORLD_TIMELINE_EMPTY_PICK;
    }

    @Override
    protected UIElement getPropertiesPanel()
    {
        return this.panel;
    }

    @Override
    protected UIElement getTimeline()
    {
        return this.timeline;
    }

    public void setTimeline(WorldTimeline worldTimeline)
    {
        this.hasTimeline = worldTimeline != null;
        this.timeline.setTimeline(worldTimeline);
        this.timeline.setVisible(this.hasTimeline && this.timelineVisible);
    }

    @Override
    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.timeline.setVisible(this.hasTimeline && visible);
    }

    public void pickBlock(WorldEventBlock block)
    {
        if (this.panel != null)
        {
            if (this.panel.block == block)
            {
                if (!this.panel.hasParent())
                {
                    this.attachPropertiesPanel(this.panel, 160);
                }

                this.resize();
                this.resizeTarget();
                this.panel.setVisible(this.propertiesVisible);
                this.panel.fillData();

                return;
            }
            else
            {
                this.panel.removeFromParent();
            }
        }

        if (block == null)
        {
            this.panel = null;
            this.timeline.w(1F, 0);
            this.resize();
            this.resizeTarget();

            return;
        }

        this.panel = new UIWorldEventBlockPanel(this, block);
        this.panel.setUndoId("world_block_panel");

        this.attachPropertiesPanel(this.panel, 160);
        this.timeline.w(1F, this.target == null ? -160 : 0);
        this.resize();
        this.resizeTarget();
        this.panel.fillData();
        this.panel.setVisible(this.propertiesVisible);
    }

    public void openSubDopeSheet(WorldEventBlock block)
    {
        if (block == null)
        {
            this.timeline.embedView(null);

            return;
        }

        this.pickBlock(block);
        this.timeline.embedView(new UISubDopeSheet(this.timeline, block));
    }

    public void fillData()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    private void resizeTarget()
    {
        if (this.target != null)
        {
            this.target.resize();
        }
    }

    public Film getFilm()
    {
        return this.filmPanel.getData();
    }

    public int getCursor()
    {
        return this.filmPanel.getCursor();
    }

    public float getCursor(float transition)
    {
        return this.filmPanel.getCursor(transition);
    }

    public void setCursor(float tick)
    {
        this.filmPanel.setCursor(tick);
    }

    public boolean isRunning()
    {
        return this.filmPanel.isRunning();
    }
}
