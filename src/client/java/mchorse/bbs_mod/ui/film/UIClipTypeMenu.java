package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.factory.IFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The menu that adds a clip: a slim strip of category tabs on top (with a step back at its left end),
 * a search box under it, and then the clip types of the open tab, each with the colour it has on the
 * timeline running down its left edge. A search looks through every tab at once.
 *
 * <p>Types are sorted into tabs by their id; one a tab does not name lands in the last, general
 * tab, so whatever an addon registers is still on offer. Types that belong to other overlays of
 * the film (subtitles, HUD pieces) are not offered here at all.</p>
 */
public class UIClipTypeMenu extends UIContextMenu
{
    private static final int WIDTH = 210;
    private static final int TAB_H = 18;
    private static final int BACK_W = 22;
    private static final int SEARCH_H = 16;
    private static final int HEADER_H = 14;
    private static final int ROW_H = 20;
    private static final int PAD = 4;

    private static final String[][] CATEGORIES = {
        {"idle", "dolly", "path", "keyframe", "pro_camera", "ai_camera", "pov"},
        {"audio", "video", "image"},
        {"color_grade", "cinematic_effects", "vignette", "letterbox", "grain", "screen_shader_graph", "eye"},
        {"translate", "angle", "drag", "shake", "math", "look", "orbit", "remapper", "tracker", "dolly_zoom"},
        {}
    };

    private static final Icon[] TAB_ICONS = {Icons.CAMERA, Icons.FOLDER, Icons.CODE, Icons.REFRESH, Icons.MORE};

    private final IFactory<Clip, ClipFactoryData> factory;
    private final Consumer<Link> pick;
    private final List<List<Link>> tabs = new ArrayList<>();
    private final UITextbox search;
    private final int visibleRows;

    private int tab;
    private int scroll;

    /** Whether this factory is the camera clips one, the only one the tabs are about. */
    public static boolean applies(IFactory<Clip, ClipFactoryData> factory)
    {
        return factory.getKeys().contains(Link.bbs("keyframe"));
    }

    private static boolean excluded(Link type)
    {
        String path = type.path;

        return path.equals("subtitle") || path.contains("hotbar") || path.contains("boss");
    }

    public UIClipTypeMenu(IFactory<Clip, ClipFactoryData> factory, Consumer<Link> pick)
    {
        this.factory = factory;
        this.pick = pick;

        for (int i = 0; i < CATEGORIES.length; i++)
        {
            this.tabs.add(new ArrayList<>());
        }

        List<Link> unsorted = new ArrayList<>();

        for (Link type : factory.getKeys())
        {
            if (!excluded(type))
            {
                unsorted.add(type);
            }
        }

        List<Link> sorted = new ArrayList<>();

        for (int i = 0; i < CATEGORIES.length - 1; i++)
        {
            for (String id : CATEGORIES[i])
            {
                for (Link type : unsorted)
                {
                    if (type.path.equals(id))
                    {
                        this.tabs.get(i).add(type);
                        sorted.add(type);
                    }
                }
            }
        }

        for (Link type : unsorted)
        {
            if (!sorted.contains(type))
            {
                this.tabs.get(CATEGORIES.length - 1).add(type);
            }
        }

        int rows = 1;

        for (List<Link> list : this.tabs)
        {
            rows = Math.max(rows, list.size());
        }

        this.visibleRows = rows;

        this.search = new UITextbox(64, (text) -> this.scroll = 0);
        this.search.placeholder(L10n.lang("bbs.ui.camera.fx.search"));
        this.search.relative(this).x(PAD).y(TAB_H + 2).w(1F, -PAD * 2).h(SEARCH_H);
        this.add(this.search);

        this.w(WIDTH).h(TAB_H + SEARCH_H + 4 + HEADER_H + rows * ROW_H + PAD * 2);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY()).bounds(context.menu.overlay, 5);
    }

    private String query()
    {
        return this.search.textbox.getText().trim().toLowerCase();
    }

    /** What the list shows: the open tab, or every type matching the search wherever it sits. */
    private List<Link> shown()
    {
        String query = this.query();

        if (query.isEmpty())
        {
            return this.tabs.get(this.tab);
        }

        List<Link> found = new ArrayList<>();

        for (List<Link> list : this.tabs)
        {
            for (Link type : list)
            {
                if (UIKeys.C_CLIP.get(type).get().toLowerCase().contains(query))
                {
                    found.add(type);
                }
            }
        }

        return found;
    }

    private int listTop()
    {
        return this.area.y + TAB_H + SEARCH_H + 4 + HEADER_H;
    }

    private int tabAt(UIContext context)
    {
        int x = context.mouseX - this.area.x - BACK_W;
        int width = this.area.w - BACK_W;

        if (context.mouseY < this.area.y || context.mouseY >= this.area.y + TAB_H || x < 0 || x >= width)
        {
            return -1;
        }

        return x * TAB_ICONS.length / width;
    }

    private boolean onBack(UIContext context)
    {
        return context.mouseY >= this.area.y && context.mouseY < this.area.y + TAB_H
            && context.mouseX >= this.area.x && context.mouseX < this.area.x + BACK_W;
    }

    private int rowAt(UIContext context)
    {
        int top = this.listTop();
        int row = (context.mouseY - top) / ROW_H;

        if (context.mouseY < top || !this.area.isInside(context) || row >= this.visibleRows || this.scroll + row >= this.shown().size())
        {
            return -1;
        }

        return this.scroll + row;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        int max = Math.max(0, this.shown().size() - this.visibleRows);

        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(context.mouseWheel)));

        return true;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || context.mouseButton != 0)
        {
            return super.subMouseClicked(context);
        }

        if (this.onBack(context))
        {
            /* A step back clears the search first, then leaves the menu */
            if (!this.query().isEmpty())
            {
                this.search.setText("");
                this.scroll = 0;
            }
            else if (context.canGoBack())
            {
                context.backContextMenu();
            }
            else
            {
                this.removeFromParent();
            }

            return true;
        }

        int tab = this.tabAt(context);

        if (tab >= 0)
        {
            this.tab = tab;
            this.scroll = 0;
            this.search.setText("");

            return true;
        }

        int row = this.rowAt(context);

        if (row >= 0)
        {
            this.pick.accept(this.shown().get(row));
            this.dismiss();

            return true;
        }

        /* The search box and the empty parts: let the children have it */
        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        /* The menu draws its own background and the search box first, so everything below lands on top */
        super.render(context);

        FontRenderer font = context.batcher.getFont();
        int primary = Colors.A100 | BBSSettings.primaryColor.get();
        int hoverTab = this.tabAt(context);
        int tabW = (this.area.w - BACK_W) / TAB_ICONS.length;
        boolean searching = !this.query().isEmpty();

        /* The tab strip */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + TAB_H, Colors.setA(0, 0.25F));
        context.batcher.icon(Icons.ARROW_LEFT, this.onBack(context) ? Colors.WHITE : Colors.LIGHTER_GRAY, this.area.x + BACK_W / 2, this.area.y + TAB_H / 2, 0.5F, 0.5F);

        for (int i = 0; i < TAB_ICONS.length; i++)
        {
            int x = this.area.x + BACK_W + i * tabW;
            boolean active = i == this.tab && !searching;

            if (active)
            {
                context.batcher.box(x, this.area.y, x + tabW, this.area.y + TAB_H, Colors.setA(primary, 0.12F));
                context.batcher.box(x + 3, this.area.y + TAB_H - 1, x + tabW - 3, this.area.y + TAB_H, primary);
            }
            else if (i == hoverTab)
            {
                context.batcher.box(x, this.area.y, x + tabW, this.area.y + TAB_H, Colors.setA(Colors.WHITE, 0.08F));
            }

            context.batcher.icon(TAB_ICONS[i], active ? Colors.WHITE : Colors.LIGHTER_GRAY, x + tabW / 2, this.area.y + TAB_H / 2, 0.5F, 0.5F);
        }

        /* The name of the open tab, or that these are search results */
        IKey title = searching ? L10n.lang("bbs.ui.camera.fx.results") : L10n.lang("bbs.ui.camera.fx.tab." + this.tab);

        context.batcher.text(title.get(), this.area.x + PAD * 2, this.area.y + TAB_H + SEARCH_H + 4 + (HEADER_H - font.getHeight()) / 2, Colors.LIGHTER_GRAY);

        /* The rows */
        List<Link> types = this.shown();
        int hover = this.rowAt(context);
        int top = this.listTop();

        for (int i = this.scroll; i < Math.min(types.size(), this.scroll + this.visibleRows); i++)
        {
            Link type = types.get(i);
            ClipFactoryData data = this.factory.getData(type);
            int y = top + (i - this.scroll) * ROW_H;

            if (i == hover)
            {
                context.batcher.box(this.area.x + PAD, y, this.area.ex() - PAD, y + ROW_H, Colors.setA(Colors.WHITE, 0.1F));
            }

            /* The colour the clip has on the timeline */
            context.batcher.box(this.area.x + PAD, y + 1, this.area.x + PAD + 3, y + ROW_H - 1, Colors.A100 | data.color);
            context.batcher.icon(data.icon, Colors.WHITE, this.area.x + PAD + 8, y + 2);
            context.batcher.text(UIKeys.C_CLIP.get(type).get(), this.area.x + PAD + 30, y + (ROW_H - font.getHeight()) / 2 + 1, Colors.WHITE);
        }

        if (types.isEmpty())
        {
            context.batcher.text(L10n.lang("bbs.ui.camera.fx.tab_empty").get(), this.area.x + PAD * 2, top + 4, Colors.GRAY);
        }
    }
}
