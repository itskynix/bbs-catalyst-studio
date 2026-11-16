package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.catalyst.CatalystProject;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Shown when a project is opened and some of its media is not where it was: every missing file with
 * its name and old path, each with Relink (pick where it is now) and Leave Offline, and Leave All
 * Offline for the rest. What stays offline is drawn as such in the pool, on the timeline and in the
 * monitor; nothing is removed from the project, so a file that comes back is simply there again.
 */
public class UIMissingMediaOverlayPanel extends UIOverlayPanel
{
    private static final int FOOTER = 34;
    private static final int WARNING = 0xffe81123;

    private final CatalystProject project;
    private final Runnable changed;
    private final List<String> missing;
    private final UIScrollView rows;
    private final UILabel summary;

    public UIMissingMediaOverlayPanel(CatalystProject project, List<String> missing, Runnable changed)
    {
        super(UIKeys.CATALYST_NLE_MISSING_TITLE);

        this.project = project;
        this.missing = new ArrayList<>(missing);
        this.changed = changed;

        this.icon(Icons.EXCLAMATION);
        this.footer(FOOTER);

        this.summary = UI.label(IKey.EMPTY).color(0xffff9a9a);
        this.summary.relative(this.content).xy(12, 12).w(1F, -24).h(12);

        this.rows = UI.scrollView(4, 6);
        this.rows.relative(this.content).xy(6, 28).w(1F, -12).h(1F, -32);

        UIButton offline = new UIButton(UIKeys.CATALYST_NLE_LEAVE_ALL_OFFLINE, (b) -> this.close());

        offline.relative(this).x(1F, -192).y(1F, -FOOTER + 7).w(180).h(20);

        this.content.add(this.summary, this.rows);
        this.add(offline);
        this.rebuild();
    }

    private void rebuild()
    {
        this.rows.removeAll();
        this.summary.label = IKey.raw(UIKeys.CATALYST_NLE_MISSING_TEXT.get() + ": " + this.missing.size());

        for (String path : this.missing)
        {
            this.rows.add(new Row(path));
        }

        this.rows.resize();

        if (this.missing.isEmpty())
        {
            this.close();
        }
    }

    private void relink(String path)
    {
        File old = new File(path);
        File folder = old.getParentFile() != null && old.getParentFile().isDirectory() ? old.getParentFile() : new File(System.getProperty("user.home", "."));
        String[] filters = new String[] {"*.mp4", "*.mov", "*.wav", "*.mp3", "*.ogg", "*.png", "*.jpg", "*.jpeg"};

        UIFileDialogs.pickFile(UIKeys.CATALYST_NLE_RELINK_PICK, folder, filters, UIKeys.CATALYST_NLE_IMPORT_FILTER, (file) ->
        {
            if (file == null || !file.isFile())
            {
                return;
            }

            this.project.relinkMedia(path, file);
            this.missing.remove(path);
            CatalystMedia.refresh();
            this.changed.run();
            this.rebuild();
        });
    }

    private void leaveOffline(String path)
    {
        this.missing.remove(path);
        this.rebuild();
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        /* The warning: a red line under the title bar */
        context.batcher.box(this.area.x + 1, this.area.y + TITLE_HEIGHT, this.area.ex() - 1, this.area.y + TITLE_HEIGHT + 2, WARNING);
    }

    @Override
    public int getMinWidth()
    {
        return 420;
    }

    @Override
    public int getMinHeight()
    {
        return 220;
    }

    /** One missing file: its name, where it used to be, and what to do about it */
    private class Row extends UIElement
    {
        private final String path;

        public Row(String path)
        {
            this.path = path;
            this.h(34);

            UIButton relink = new UIButton(UIKeys.CATALYST_NLE_RELINK, (b) -> UIMissingMediaOverlayPanel.this.relink(path)).primary();
            UIButton offline = new UIButton(UIKeys.CATALYST_NLE_LEAVE_OFFLINE, (b) -> UIMissingMediaOverlayPanel.this.leaveOffline(path));

            relink.relative(this).x(1F, -214).y(8).w(96).h(18);
            offline.relative(this).x(1F, -114).y(8).w(110).h(18);

            this.add(relink, offline);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            int room = Math.max(20, this.area.w - 232);

            RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 4, RoundedDraw.ALL, 0xcc3a0d12);
            RoundedDraw.outline(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 4, RoundedDraw.ALL, 0xff7a1a22);

            context.batcher.text(font.limitToWidth(new File(this.path).getName(), room), this.area.x + 8, this.area.y + 6, 0xffffffff, false);
            context.batcher.text(font.limitToWidth(this.path, room), this.area.x + 8, this.area.y + 19, 0xffc08a8a, false);

            super.render(context);
        }
    }
}
