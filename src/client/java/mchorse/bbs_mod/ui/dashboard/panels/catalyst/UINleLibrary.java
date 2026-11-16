package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystMediaAsset;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIPostEffectPicker;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The library on the left of the edit page, under four tabs: the media pool (with a narrow column of
 * folders - everything, BBS films, images, sounds), the effect library with the adjustment clip, the
 * transitions and the titles. Media is dragged or double clicked onto the timeline; an effect goes
 * onto the picked clip, a transition onto its start, a title onto a free video track at the playhead.
 */
public class UINleLibrary extends UIElement
{
    public static final int TABS_H = 20;
    private static final int FOLDERS_W = 94;
    private static final int ROW_H = 18;

    /** What the library asks of the edit page */
    public interface Host
    {
        void addFilm(String filmId);
        void addEffectToSelection(PostEffect effect);
        void addAdjustmentClip();
        void setTransition(String transition);
        void addTitle(boolean lowerThird);

        /** The timelines of the project, for the Timelines folder */
        List<CatalystComposition> timelines();

        /** Picks a timeline up to be dropped on a video track as a nested clip */
        void dragTimeline(CatalystComposition timeline);
    }

    private final Host host;
    private final UIMediaPoolPanel mediaPool;
    private final IKey[] tabs = {UIKeys.CATALYST_NLE_LIB_MEDIA, UIKeys.CATALYST_NLE_LIB_EFFECTS, UIKeys.CATALYST_NLE_LIB_TRANSITIONS, UIKeys.CATALYST_NLE_LIB_TITLES};
    private final IKey[] folders = {UIKeys.CATALYST_NLE_FOLDER_ALL, UIKeys.CATALYST_NLE_FOLDER_FILMS, UIKeys.CATALYST_NLE_FOLDER_IMAGES, UIKeys.CATALYST_NLE_FOLDER_SOUNDS, UIKeys.CATALYST_NLE_FOLDER_TIMELINES};

    private int tab;
    private int folder;

    private final UIScrollView list;
    private final UITextbox search;
    private final UIIcon importButton;

    public UINleLibrary(Host host, UIMediaPoolPanel mediaPool)
    {
        this.host = host;
        this.mediaPool = mediaPool;

        this.mediaPool.relative(this).x(FOLDERS_W).y(TABS_H).w(1F, -FOLDERS_W).h(1F, -TABS_H);

        this.search = new UITextbox(100, (t) -> this.rebuild());
        this.search.placeholder(UIKeys.CATALYST_NLE_SEARCH);
        this.search.relative(this).x(4).y(TABS_H + 3).w(1F, -30).h(18);

        this.importButton = new UIIcon(Icons.ADD, (b) -> this.mediaPool.importMediaDialog());
        this.importButton.tooltip(UIKeys.CATALYST_NLE_IMPORT);
        this.importButton.relative(this).x(1F, -22).y(TABS_H + 3).wh(18, 18);

        this.list = UI.scrollView(2, 4);
        this.list.relative(this).y(TABS_H + 24).w(1F).h(1F, -TABS_H - 24);

        this.add(this.mediaPool, this.search, this.importButton, this.list);
        this.update();
    }

    /** Opens a tab: 0 media, 1 effects, 2 transitions, 3 titles */
    public void setTab(int tab)
    {
        this.tab = Math.max(0, Math.min(this.tabs.length - 1, tab));
        this.update();
    }

    private void update()
    {
        boolean pool = this.tab == 0 && this.folder != 1 && this.folder != 4;

        this.mediaPool.setVisible(pool);
        this.mediaPool.typeFilter = this.folder == 2 ? CatalystMediaAsset.MediaType.IMAGE : this.folder == 3 ? CatalystMediaAsset.MediaType.AUDIO : null;
        this.mediaPool.rebuildList();

        boolean films = this.tab == 0 && (this.folder == 1 || this.folder == 4);

        this.list.setVisible(!pool);
        this.search.setVisible(!pool);
        this.importButton.setVisible(false);

        /* The films list sits next to the folder column, like the pool */
        if (films)
        {
            this.search.relative(this).x(FOLDERS_W + 4).y(TABS_H + 3).w(1F, -FOLDERS_W - 8).h(18);
            this.list.relative(this).x(FOLDERS_W).y(TABS_H + 24).w(1F, -FOLDERS_W).h(1F, -TABS_H - 24);
        }
        else
        {
            this.search.relative(this).x(4).y(TABS_H + 3).w(1F, -8).h(18);
            this.list.relative(this).x(0).y(TABS_H + 24).w(1F).h(1F, -TABS_H - 24);
        }

        this.rebuild();

        if (this.hasParent())
        {
            this.resize();
        }
    }

    private void rebuild()
    {
        this.list.removeAll();

        String query = this.search.getText().trim().toLowerCase(Locale.ROOT);

        if (this.tab == 0 && this.folder == 4)
        {
            /* The project's timelines: each is dragged onto a video track and plays there as one clip */
            int shown = 0;

            for (CatalystComposition timeline : this.host.timelines())
            {
                if (query.isEmpty() || timeline.name.toLowerCase(Locale.ROOT).contains(query))
                {
                    this.timelineEntry(timeline).tooltip(UIKeys.CATALYST_NLE_TIMELINE_HINT);
                    shown += 1;
                }
            }

            if (shown == 0)
            {
                this.list.add(UI.label(UIKeys.CATALYST_NLE_NOTHING).color(0x88ffffff));
            }
        }
        else if (this.tab == 0 && this.folder == 1)
        {
            List<String> films = new ArrayList<>();

            try
            {
                for (String key : BBSMod.getFilms().getKeys())
                {
                    if (!key.endsWith("/") && (query.isEmpty() || key.toLowerCase(Locale.ROOT).contains(query)))
                    {
                        films.add(key);
                    }
                }
            }
            catch (Exception ignored)
            {}

            films.sort(String::compareToIgnoreCase);

            for (String film : films)
            {
                this.entry(IKey.raw(film), Icons.FILM, (b) -> this.host.addFilm(film)).tooltip(UIKeys.CATALYST_NLE_ADD_HINT);
            }

            if (films.isEmpty())
            {
                this.list.add(UI.label(UIKeys.CATALYST_NLE_NOTHING).color(0x88ffffff));
            }
        }
        else if (this.tab == 1)
        {
            this.entry(UIKeys.CATALYST_NLE_CLIP_ADJUSTMENT, Icons.SPARKLES, (b) -> this.host.addAdjustmentClip()).tooltip(UIKeys.CATALYST_NLE_ADJUSTMENT_HINT);

            for (String category : PostEffect.CATEGORIES)
            {
                boolean header = false;

                for (PostEffect effect : PostEffect.inCategory(category))
                {
                    String name = UIPostEffectPicker.effectName(effect).get();

                    if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query))
                    {
                        continue;
                    }

                    if (!header)
                    {
                        header = true;
                        this.list.add(UI.label(UIPostEffectPicker.categoryName(category)).color(Colors.A100 | BBSSettings.primaryColor.get()).marginTop(4));
                    }

                    this.entry(IKey.raw(name), UIPostEffectPicker.categoryIcon(category), (b) -> this.host.addEffectToSelection(effect)).tooltip(UIKeys.CATALYST_NLE_EFFECT_HINT);
                }
            }
        }
        else if (this.tab == 2)
        {
            this.entry(UIKeys.CATALYST_NLE_TRANSITION_CROSS_DISSOLVE, Icons.FADING, (b) -> this.host.setTransition("cross_dissolve")).tooltip(UIKeys.CATALYST_NLE_TRANSITION_HINT);
            this.entry(UIKeys.CATALYST_NLE_TRANSITION_DIP_TO_BLACK, Icons.FADING, (b) -> this.host.setTransition("dip_to_black")).tooltip(UIKeys.CATALYST_NLE_TRANSITION_HINT);
            this.entry(UIKeys.CATALYST_NLE_TRANSITION_SLIDE, Icons.ARROW_RIGHT, (b) -> this.host.setTransition("slide")).tooltip(UIKeys.CATALYST_NLE_TRANSITION_HINT);
            this.entry(UIKeys.CATALYST_NLE_TRANSITION_NONE, Icons.CLOSE, (b) -> this.host.setTransition(""));
        }
        else if (this.tab == 3)
        {
            this.entry(UIKeys.CATALYST_NLE_TITLE_BASIC, Icons.FONT, (b) -> this.host.addTitle(false)).tooltip(UIKeys.CATALYST_NLE_TITLE_HINT);
            this.entry(UIKeys.CATALYST_NLE_TITLE_LOWER_THIRD, Icons.FONT, (b) -> this.host.addTitle(true)).tooltip(UIKeys.CATALYST_NLE_TITLE_HINT);
        }

        this.list.resize();
    }

    /** The list is made again, for a timeline that was added, renamed or taken out */
    public void refresh()
    {
        if (this.tab == 0 && this.folder == 4)
        {
            this.rebuild();
        }
    }

    /** A timeline's row: pressed, it is picked up and follows the mouse until it is dropped */
    private UIButton timelineEntry(CatalystComposition timeline)
    {
        UIButton button = new UIButton(IKey.raw(timeline.name), (b) -> {})
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);
                context.batcher.icon(Icons.FILM, 0xccffffff, this.area.x + 2, this.area.my() - 8);
            }

            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 0 && this.area.isInside(context))
                {
                    UINleLibrary.this.host.dragTimeline(timeline);

                    return true;
                }

                return super.subMouseClicked(context);
            }
        };

        button.h(ROW_H);
        this.list.add(button);

        return button;
    }

    private UIButton entry(IKey label, mchorse.bbs_mod.ui.utils.icons.Icon icon, Consumer<UIButton> action)
    {
        UIButton button = new UIButton(label, action)
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                super.renderSkin(context);
                context.batcher.icon(icon, 0xccffffff, this.area.x + 2, this.area.my() - 8);
            }
        };

        button.h(ROW_H);
        this.list.add(button);

        return button;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton != 0 || !this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        if (context.mouseY < this.area.y + TABS_H)
        {
            int width = this.area.w / this.tabs.length;

            this.tab = Math.min(this.tabs.length - 1, (context.mouseX - this.area.x) / Math.max(1, width));
            this.update();

            return true;
        }

        if (this.tab == 0 && context.mouseX < this.area.x + FOLDERS_W)
        {
            int index = (context.mouseY - this.area.y - TABS_H - 3) / ROW_H;

            if (index >= 0 && index < this.folders.length)
            {
                this.folder = index;
                this.update();
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        int accent = BBSSettings.primaryColor.get();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff151a23);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + TABS_H, 0xff1a1f2a);

        int width = this.area.w / this.tabs.length;

        for (int i = 0; i < this.tabs.length; i++)
        {
            int x1 = this.area.x + i * width + 2;
            int x2 = x1 + width - 4;
            boolean active = i == this.tab;
            boolean hover = context.mouseX >= x1 && context.mouseX < x2 && context.mouseY >= this.area.y && context.mouseY < this.area.y + TABS_H;
            String label = font.limitToWidth(this.tabs[i].get(), width - 8);

            if (active || hover)
            {
                RoundedDraw.fill(context.batcher, x1, this.area.y + 2, x2, this.area.y + TABS_H - 2, 4, RoundedDraw.ALL, active ? Colors.setA(accent, 0.5F) : 0x18ffffff);
            }

            context.batcher.text(label, (x1 + x2) / 2 - font.getWidth(label) / 2, this.area.y + 6, active ? Colors.WHITE : 0xffaab4c4, false);
        }

        if (this.tab == 0)
        {
            int columnEnd = this.area.x + FOLDERS_W;

            context.batcher.box(this.area.x, this.area.y + TABS_H, columnEnd, this.area.ey(), 0xff12161e);
            context.batcher.box(columnEnd - 1, this.area.y + TABS_H, columnEnd, this.area.ey(), 0xff262d3a);

            for (int i = 0; i < this.folders.length; i++)
            {
                int y = this.area.y + TABS_H + 3 + i * ROW_H;
                boolean active = i == this.folder;
                boolean hover = context.mouseX >= this.area.x && context.mouseX < columnEnd && context.mouseY >= y && context.mouseY < y + ROW_H;

                if (active || hover)
                {
                    RoundedDraw.fill(context.batcher, this.area.x + 3, y + 1, columnEnd - 4, y + ROW_H - 1, 4, RoundedDraw.ALL, active ? Colors.setA(accent, 0.4F) : 0x14ffffff);
                }

                context.batcher.text(font.limitToWidth(this.folders[i].get(), FOLDERS_W - 12), this.area.x + 7, y + 5, active ? Colors.WHITE : 0xffaab4c4, false);
            }
        }

        context.batcher.box(this.area.ex() - 1, this.area.y, this.area.ex(), this.area.ey(), 0xff262d3a);

        super.render(context);
    }
}
