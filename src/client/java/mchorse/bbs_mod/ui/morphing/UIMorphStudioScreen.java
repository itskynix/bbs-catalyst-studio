package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.categories.RecentFormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.LandingBackdrop;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modern Morphing Studio & Live 3D Podium Screen.
 * Features a split 2-pane studio layout:
 * - Left Pane (38%): Illuminated 3D Podium scene with turntable idle rotation, scale pop, metadata & Morph action.
 * - Right Pane (62%): Search bar, category filter pills, symmetric card grid with smooth hover lift and instant morphing.
 */
public class UIMorphStudioScreen extends UIElement
{
    public final UIMorphingPanel panel;
    private final LandingBackdrop backdrop = new LandingBackdrop();

    /* Sol Panel (38% - Canlı Podyum) */
    private final UIElement leftPanel;
    public final UIMorphPodiumRenderer podium;
    private final UILabel formNameLabel;
    private final UILabel formTypeLabel;
    private final UIIcon favoriteBtn;
    private final UIIcon editBtn;
    private final UIButton morphBtn;

    /* Sağ Panel (62% - Form Kataloğu) */
    private final UIElement rightPanel;
    private final UITextbox searchBox;
    private final UIElement pillsRow;
    private final List<UICategoryPill> pills = new ArrayList<>();
    private final UILabel sectionTitle;
    private final UIScrollView cardsScroll;
    private final GridResizer cardsGrid;

    /* Veri ve Durum */
    private final Set<String> favorites = new HashSet<>();
    private final List<Form> allForms = new ArrayList<>();
    private final List<Form> recentForms = new ArrayList<>();
    private final List<Form> displayedForms = new ArrayList<>();
    private Form selectedForm;
    private UIMorphCategory currentCategory = UIMorphCategory.ALL;
    private String searchQuery = "";

    public UIMorphStudioScreen(UIMorphingPanel panel)
    {
        super();

        this.panel = panel;

        this.loadFavorites();

        /* 1. Arka plan atmosferik tuval */
        this.add(new UIRenderable((context) -> this.backdrop.render(context, this.area)));

        /* 2. Üst Araç ve Başlık Çubuğu */
        UIElement topBar = new UIElement();
        topBar.relative(this).x(10).y(6).w(1F, -20).h(20);

        topBar.add(new UIRenderable(this::renderTopHeader));

        UIIcon demorphBtn = new UIIcon(Icons.POSE, (b) -> this.demorph());
        demorphBtn.tooltip(UIKeys.MORPHING_DEMORPH, Direction.TOP);
        demorphBtn.relative(topBar).x(1F, -68).y(0).wh(20, 20);

        UIIcon fromMobBtn = new UIIcon(Icons.MORPH, (b) -> this.morphFromMob());
        fromMobBtn.tooltip(UIKeys.MORPHING_FROM_MOB, Direction.TOP);
        fromMobBtn.relative(topBar).x(1F, -46).y(0).wh(20, 20);

        UIIcon classicViewBtn = new UIIcon(Icons.MORE, (b) -> this.panel.showClassicView());
        classicViewBtn.tooltip(UIKeys.MORPH_STUDIO_CLASSIC_VIEW, Direction.TOP);
        classicViewBtn.relative(topBar).x(1F, -22).y(0).wh(20, 20);

        topBar.add(demorphBtn, fromMobBtn, classicViewBtn);
        this.add(topBar);

        /* 3. Sol Panel (%38 - Canlı Podyum) */
        this.leftPanel = new UIElement();
        this.leftPanel.relative(this).x(10).y(30).w(0.38F, -14).h(1F, -40);

        this.leftPanel.add(new UIRenderable((context) ->
        {
            Area a = this.leftPanel.area;
            int bg = 0xf00b0f16;
            int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);

            context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 8, Colors.A50, 0);
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);
        }));

        UILabel podiumHeader = UI.label(UIKeys.MORPH_STUDIO_PODIUM_TITLE).color(Colors.setA(Colors.WHITE, 0.65F));
        podiumHeader.labelAnchor(0, 0.5F);
        podiumHeader.relative(this.leftPanel).xy(10, 6).w(1F, -20).h(16);

        this.podium = new UIMorphPodiumRenderer();
        this.podium.relative(this.leftPanel).x(6).y(24).w(1F, -12).hTo(this.leftPanel.getFlex(), 1F, -114);

        /* Podyum altı metadata kartı ve aksiyon butonları */
        UIElement metaPanel = new UIElement();
        metaPanel.relative(this.leftPanel).x(8).y(1F, -110).w(1F, -16).h(104);

        this.formNameLabel = UI.label(UIKeys.MORPH_STUDIO_NO_FORM).color(Colors.WHITE);
        this.formNameLabel.labelAnchor(0, 0.5F);
        this.formNameLabel.relative(metaPanel).xy(2, 2).w(1F, -4).h(16);

        this.formTypeLabel = UI.label(IKey.raw("")).color(Colors.setA(Colors.WHITE, 0.55F));
        this.formTypeLabel.labelAnchor(0, 0.5F);
        this.formTypeLabel.relative(metaPanel).xy(2, 18).w(1F, -4).h(14);

        UIElement metaActions = new UIElement();
        metaActions.relative(metaPanel).x(0).y(36).w(1F).h(20);

        this.favoriteBtn = new UIIcon(Icons.FAVORITE, (b) -> this.toggleFavorite(this.selectedForm));
        this.favoriteBtn.tooltip(UIKeys.MORPH_STUDIO_FAVORITE);
        this.favoriteBtn.relative(metaActions).x(0).y(0).wh(20, 20);

        this.editBtn = new UIIcon(Icons.EDIT, (b) -> this.openEditor(this.selectedForm));
        this.editBtn.tooltip(UIKeys.MORPH_STUDIO_EDIT);
        this.editBtn.relative(metaActions).x(24).y(0).wh(20, 20);

        metaActions.add(this.favoriteBtn, this.editBtn);

        this.morphBtn = new UIButton(UIKeys.MORPH_STUDIO_MORPH_BUTTON, (b) -> this.morphInto(this.selectedForm))
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                int primary = BBSSettings.primaryColor.get();
                int bg = this.hover ? (0xf0000000 | primary) : (0xcc000000 | primary);

                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.WHITE);

                super.renderSkin(context);
            }
        };
        this.morphBtn.relative(metaPanel).x(0).y(62).w(1F).h(26);

        metaPanel.add(this.formNameLabel, this.formTypeLabel, metaActions, this.morphBtn);
        this.leftPanel.add(podiumHeader, this.podium, metaPanel);
        this.add(this.leftPanel);

        /* 4. Sağ Panel (%62 - Form Kataloğu) */
        this.rightPanel = new UIElement()
        {
            @Override
            protected boolean subMouseScrolled(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseWheel != 0)
                {
                    double speed = cardsScroll.scroll.scrollSpeed * BBSSettings.scrollingSensitivity.get();
                    cardsScroll.scroll.scrollBy((int) (-Math.copySign(speed, context.mouseWheel)));

                    return true;
                }

                return false;
            }
        };
        this.rightPanel.relative(this).x(0.38F, 2).y(30).w(0.62F, -12).h(1F, -40);

        this.rightPanel.add(new UIRenderable((context) ->
        {
            Area a = this.rightPanel.area;
            int bg = 0xf00c1017;
            int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);

            context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 8, Colors.A50, 0);
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);
        }));

        /* Arama kutusu */
        UIElement searchContainer = new UIElement();
        searchContainer.relative(this.rightPanel).x(8).y(8).w(1F, -16).h(22);

        this.searchBox = new UITextbox(this::onSearchChanged).placeholder(UIKeys.MORPH_STUDIO_SEARCH);
        this.searchBox.background(false);
        this.searchBox.relative(searchContainer).x(24).y(0).w(1F, -26).h(1F);

        searchContainer.add(new UIRenderable((context) ->
        {
            Area a = searchContainer.area;
            int bg = 0xd010141e;
            int border = this.searchBox.isFocused() ? (Colors.A100 | BBSSettings.primaryColor.get()) : 0x38ffffff;

            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);
            context.batcher.icon(Icons.SEARCH, a.x + 4, a.y + 3, Colors.setA(Colors.WHITE, 0.7F));
        }));
        searchContainer.add(this.searchBox);

        /* Kategori Filtre Butonları (Pills) */
        this.pillsRow = new UIElement();
        this.pillsRow.relative(this.rightPanel).x(8).y(34).w(1F, -16).h(20);
        this.pillsRow.row(4);

        for (UIMorphCategory cat : UIMorphCategory.values())
        {
            UICategoryPill pill = new UICategoryPill(this, cat);
            this.pills.add(pill);
            this.pillsRow.add(pill);
        }

        /* Dinamik Bölüm Başlığı */
        this.sectionTitle = UI.label(UIKeys.MORPH_STUDIO_RECENT_TITLE).color(Colors.setA(Colors.WHITE, 0.65F));
        this.sectionTitle.labelAnchor(0, 0.5F);
        this.sectionTitle.relative(this.rightPanel).xy(10, 58).w(1F, -20).h(16);

        /* Kaydırılabilir Simetrik Kart Izgarası */
        this.cardsScroll = new UIScrollView();
        this.cardsScroll.relative(this.rightPanel).x(6).y(76).w(1F, -12).h(1F, -82);
        this.cardsScroll.scroll.scrollSpeed = 24;
        this.cardsScroll.scroll.scrollItemSize = 1;
        this.cardsGrid = this.cardsScroll.grid(6);
        this.cardsGrid.padding(4);
        this.cardsGrid.center();
        this.cardsGrid.scroll();
        this.cardsGrid.width(104).height(104);

        this.rightPanel.add(searchContainer, this.pillsRow, this.sectionTitle, this.cardsScroll);
        this.rightPanel.add(new UIRenderable(this::renderEmptyHint));
        this.add(this.rightPanel);

        this.refresh();
    }

    public void setCategory(UIMorphCategory category)
    {
        this.currentCategory = category;
        this.refresh();
    }

    private void onSearchChanged(String query)
    {
        this.searchQuery = query == null ? "" : query.trim();
        this.refresh();
    }

    public void selectForm(Form form)
    {
        this.selectedForm = form;
        this.podium.setForm(form);
        this.updatePodiumMetadata();
    }

    public void morphInto(Form form)
    {
        if (form != null)
        {
            this.selectForm(form);
            this.panel.setForm(form);
            UIUtils.playClick();
        }
    }

    public void demorph()
    {
        this.selectForm(null);
        this.panel.setForm(null);
        UIUtils.playClick();
    }

    public void morphFromMob()
    {
        this.panel.morphFromMob();
    }

    public void openEditor(Form form)
    {
        if (form != null)
        {
            this.panel.openEditor(form);
        }
    }

    public Form getSelectedForm()
    {
        return this.selectedForm;
    }

    public boolean isFavorite(Form form)
    {
        if (form == null)
        {
            return false;
        }

        return this.favorites.contains(UIMorphCategory.getFormKey(form));
    }

    public void toggleFavorite(Form form)
    {
        if (form == null)
        {
            return;
        }

        String key = UIMorphCategory.getFormKey(form);

        if (this.favorites.contains(key))
        {
            this.favorites.remove(key);
        }
        else
        {
            this.favorites.add(key);
        }

        this.saveFavorites();
        this.updatePodiumMetadata();
    }

    public void setSelectedForm(Form form)
    {
        this.selectedForm = form;
        this.podium.setForm(form);
        this.updatePodiumMetadata();
    }

    private void updatePodiumMetadata()
    {
        if (this.selectedForm == null)
        {
            this.formNameLabel.label = UIKeys.MORPH_STUDIO_NO_FORM;
            this.formTypeLabel.label = IKey.raw("");
            this.favoriteBtn.setEnabled(false);
            this.editBtn.setEnabled(false);
            this.morphBtn.setEnabled(false);

            return;
        }

        this.formNameLabel.label = IKey.raw(this.selectedForm.getDisplayName());
        this.formTypeLabel.label = IKey.raw(UIMorphCategory.getFormTypeLabel(this.selectedForm));
        this.favoriteBtn.setEnabled(true);
        this.editBtn.setEnabled(true);
        this.morphBtn.setEnabled(true);

        boolean fav = this.isFavorite(this.selectedForm);
        this.favoriteBtn.tooltip(fav ? UIKeys.MORPH_STUDIO_UNFAVORITE : UIKeys.MORPH_STUDIO_FAVORITE);
    }

    public void setupForms(FormCategories formCategories)
    {
        this.allForms.clear();
        this.recentForms.clear();

        if (formCategories == null)
        {
            return;
        }

        /* Recent forms */
        for (FormCategory category : formCategories.getRecentForms().getCategories())
        {
            for (Form f : category.getForms())
            {
                if (!this.recentForms.contains(f))
                {
                    this.recentForms.add(f);
                }
            }
        }

        /* Full catalog */
        for (FormCategory category : formCategories.getAllCategories())
        {
            if (category instanceof RecentFormCategory)
            {
                continue;
            }

            for (Form f : category.getForms())
            {
                if (!this.allForms.contains(f))
                {
                    this.allForms.add(f);
                }
            }
        }

        this.refresh();
    }

    public void refresh()
    {
        this.cardsScroll.removeAll();
        this.displayedForms.clear();

        String query = this.searchQuery.toLowerCase();

        /* Arama veya kategori filtreleme */
        for (Form form : this.allForms)
        {
            if (!this.currentCategory.matches(form, this.favorites))
            {
                continue;
            }

            if (!query.isEmpty())
            {
                boolean matchName = form.getDisplayName().toLowerCase().contains(query);
                boolean matchCustom = form.name.get().toLowerCase().contains(query);
                boolean matchId = form.getFormId().toLowerCase().contains(query);

                if (!matchName && !matchCustom && !matchId)
                {
                    continue;
                }
            }

            this.displayedForms.add(form);
        }

        /* Öncelikli sıralama: Eğer ALL ve arama yoksa Recent & Favorite formları başa al */
        if (this.currentCategory == UIMorphCategory.ALL && query.isEmpty())
        {
            this.displayedForms.sort((a, b) ->
            {
                boolean favA = this.isFavorite(a);
                boolean favB = this.isFavorite(b);

                if (favA != favB)
                {
                    return favA ? -1 : 1;
                }

                boolean recA = this.recentForms.contains(a);
                boolean recB = this.recentForms.contains(b);

                if (recA != recB)
                {
                    return recA ? -1 : 1;
                }

                return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            });
        }
        else
        {
            this.displayedForms.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        }

        /* Başlık güncelleme */
        if (!query.isEmpty())
        {
            this.sectionTitle.label = UIKeys.MORPH_STUDIO_SEARCH_TITLE.format(this.searchQuery);
        }
        else if (this.currentCategory != UIMorphCategory.ALL)
        {
            this.sectionTitle.label = this.currentCategory.title;
        }
        else
        {
            this.sectionTitle.label = UIKeys.MORPH_STUDIO_RECENT_TITLE;
        }

        /* Kartları ekle */
        for (Form form : this.displayedForms)
        {
            this.cardsScroll.add(new UIMorphFormCard(this, form));
        }

        this.updateGridCentering();
        this.cardsScroll.resize();
        this.updateGridCentering();
        this.cardsScroll.scroll.clamp();
    }

    private void updateGridCentering()
    {
        int cardW = 104;
        int gap = 6;
        int scrollW = Math.max(cardW, this.cardsScroll.area.w);
        int cols = Math.max(1, (scrollW + gap) / (cardW + gap));
        int totalW = cols * cardW + (cols - 1) * gap;
        int padX = Math.max(4, (scrollW - totalW) / 2);

        this.cardsGrid.padding(padX, 4);

        int count = this.cardsScroll.getChildren().size();
        int rows = count == 0 ? 0 : (count + cols - 1) / cols;
        int totalH = rows == 0 ? 0 : rows * 104 + (rows - 1) * gap + 8;

        this.cardsScroll.scroll.scrollItemSize = 1;
        this.cardsScroll.scroll.setSize(totalH);
        this.cardsScroll.scroll.clamp();
    }

    @Override
    public void resize()
    {
        super.resize();

        this.updateGridCentering();
        this.cardsScroll.resize();
        this.cardsScroll.scroll.clamp();
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyCode() == GLFW.GLFW_KEY_ENTER && context.getKeyAction() == KeyAction.PRESSED)
        {
            if (!this.cardsScroll.getChildren().isEmpty())
            {
                IUIElement first = this.cardsScroll.getChildren().get(0);

                if (first instanceof UIMorphFormCard card)
                {
                    this.morphInto(card.form);

                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

    /* Rendering */

    private void renderTopHeader(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        String title = "MORPHING STUDIO";
        int primary = BBSSettings.primaryColor.get();

        context.batcher.text(title, this.area.x + 12, this.area.y + 10, Colors.WHITE, true);
        context.batcher.text("BBS CS", this.area.x + 14 + font.getWidth(title), this.area.y + 10, Colors.setA(primary, 0.9F), true);
    }

    private void renderEmptyHint(UIContext context)
    {
        if (this.cardsScroll.getChildren().isEmpty())
        {
            Area a = this.rightPanel.area;
            IKey key = this.searchQuery.isEmpty() ? UIKeys.MORPH_STUDIO_EMPTY.format("") : UIKeys.MORPH_STUDIO_EMPTY.format(this.searchQuery);
            String msg = key.get();
            FontRenderer font = context.batcher.getFont();
            int w = font.getWidth(msg);

            context.batcher.text(msg, a.mx() - w / 2, a.my(), Colors.setA(Colors.WHITE, 0.45F));
        }
    }

    /* Favoriler Kalıcılığı */

    private void loadFavorites()
    {
        try
        {
            File file = new File(BBSMod.getSettingsFolder(), "morph_favorites.json");

            if (file.exists())
            {
                BaseType base = DataToString.read(file);

                if (base != null && base.isMap())
                {
                    ListType list = base.asMap().getList("favorites");

                    for (BaseType element : list)
                    {
                        this.favorites.add(element.asString());
                    }
                }
            }
        }
        catch (Exception e)
        {
            /* Göz ardı et */
        }
    }

    private void saveFavorites()
    {
        try
        {
            File file = new File(BBSMod.getSettingsFolder(), "morph_favorites.json");
            MapType data = new MapType();
            ListType list = new ListType();

            for (String fav : this.favorites)
            {
                list.add(new StringType(fav));
            }

            data.put("favorites", list);
            DataToString.writeSilently(file, data, true);
        }
        catch (Exception e)
        {
            /* Göz ardı et */
        }
    }

    /* Kategori Hapı (Pill) Bileşeni */

    public static class UICategoryPill extends UIClickable<UICategoryPill>
    {
        private final UIMorphStudioScreen studio;
        public final UIMorphCategory category;

        public UICategoryPill(UIMorphStudioScreen studio, UIMorphCategory category)
        {
            super((c) ->
            {
                UIUtils.playClick();
                studio.setCategory(category);
            });

            this.studio = studio;
            this.category = category;
            this.h(20);
        }

        @Override
        protected UICategoryPill get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Area a = this.area;
            boolean active = this.studio.currentCategory == this.category;
            boolean hover = this.hover;
            int primary = BBSSettings.primaryColor.get();

            int bg = active ? (0xd8000000 | (this.category.color & 0x223246)) : (hover ? 0xc0141a24 : 0x700e121a);
            int border = active ? (Colors.A100 | this.category.color) : (hover ? 0x60ffffff : 0x24ffffff);
            int textColor = active ? Colors.WHITE : (hover ? 0xe0ffffff : 0x90ffffff);

            if (active || hover)
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 4, active ? Colors.setA(this.category.color, 0.4F) : Colors.A50, 0);
            }

            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);

            String label = this.category.title.get();
            FontRenderer font = context.batcher.getFont();
            int tw = font.getWidth(label);

            context.batcher.text(label, a.mx() - tw / 2, a.my() - font.getHeight() / 2, textColor, false);
        }
    }
}
