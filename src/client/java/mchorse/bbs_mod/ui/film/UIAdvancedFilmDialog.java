package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Advanced Film Creation & Folder Management Dialog.
 * Dual-pane interface:
 * - Left Pane: Folder hierarchy tree with Add Folder and Open in Explorer actions.
 * - Right Pane: New film name input and card/grid preview of existing films in the selected folder.
 */
public class UIAdvancedFilmDialog extends UIOverlayPanel
{
    public final UIDataDashboardPanel<Film> panel;
    public final MapType defaultData;
    public final Consumer<String> callback;

    private String selectedFolder = "";
    private final List<String> allKeys = new ArrayList<>();
    private final List<String> folders = new ArrayList<>();

    /* Left pane elements */
    private final UIElement leftPane;
    private final UIList<String> folderList;

    /* Right pane elements */
    private final UIElement rightPane;
    private final UILabel locationLabel;
    public final UITextbox nameBox;
    public final UIButton createBtn;
    private final UILabel sectionLabel;
    private final UIScrollView filmsScroll;
    private final GridResizer filmsGrid;

    public UIAdvancedFilmDialog(UIDataDashboardPanel<Film> panel, MapType defaultData, Consumer<String> callback)
    {
        super(UIKeys.FILM_CREATE_DIALOG_TITLE);

        this.panel = panel;
        this.defaultData = defaultData;
        this.callback = callback;

        /* Left Pane: Folders */
        this.leftPane = new UIElement();
        this.leftPane.relative(this.content).xy(8, 4).w(180).h(1F, -8);

        UIElement leftHeader = new UIElement();
        leftHeader.relative(this.leftPane).xy(0, 0).w(1F).h(20);

        UILabel foldersLabel = UI.label(UIKeys.FILM_CREATE_DIALOG_FOLDERS);
        foldersLabel.relative(leftHeader).xy(0, 2).w(1F, -44).h(16);

        UIIcon addFolderBtn = new UIIcon(Icons.ADD, (b) -> this.promptNewFolder());
        addFolderBtn.tooltip(UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE);
        addFolderBtn.relative(leftHeader).x(1F, -40).y(0).wh(18, 18);

        UIIcon openFolderBtn = new UIIcon(Icons.FOLDER, (b) -> this.openFolderInExplorer());
        openFolderBtn.tooltip(UIKeys.FILM_CREATE_DIALOG_OPEN_FOLDER);
        openFolderBtn.relative(leftHeader).x(1F, -18).y(0).wh(18, 18);

        leftHeader.add(foldersLabel, addFolderBtn, openFolderBtn);

        this.folderList = new UIList<String>((list) ->
        {
            if (!list.isEmpty())
            {
                this.selectedFolder = list.get(0);
                this.updateFolderView();
            }
        })
        {
            @Override
            protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
            {
                context.batcher.icon(Icons.FOLDER, RowStyle.iconColor(hover || selected), x, y + this.rowHeight() / 2F, 0F, 0.5F);
                super.renderElementPart(context, element, i, x + 16, y, hover, selected);
            }

            @Override
            protected String elementToString(UIContext context, int i, String element)
            {
                if (element.isEmpty())
                {
                    return UIKeys.FILM_CREATE_DIALOG_ROOT_FOLDER.get();
                }
                return element + "/";
            }
        };
        this.folderList.background();
        this.folderList.scroll.scrollItemSize = 20;
        this.folderList.relative(this.leftPane).xy(0, 24).w(1F).h(1F, -24);

        this.leftPane.add(leftHeader, this.folderList);

        /* Separator */
        UIRenderable separator = new UIRenderable((context) ->
        {
            int sepX = this.content.area.x + 196;
            int y1 = this.content.area.y + 4;
            int y2 = this.content.area.ey() - 4;
            context.batcher.box(sepX, y1, sepX + 1, y2, 0x2affffff);
        });

        /* Right Pane: Project Input & Grid Preview */
        this.rightPane = new UIElement();
        this.rightPane.relative(this.content).xy(204, 4).w(1F, -212).h(1F, -8);

        /* 1. Target Folder & Input Row */
        UIElement creationBar = new UIElement();
        creationBar.relative(this.rightPane).xy(0, 0).w(1F).h(48);

        this.locationLabel = UI.label(IKey.raw(""));
        this.locationLabel.relative(creationBar).xy(0, 0).w(1F).h(14);

        UIElement inputRow = new UIElement();
        inputRow.relative(creationBar).xy(0, 18).w(1F).h(24);

        this.nameBox = new UITextbox(null).placeholder(UIKeys.FILM_CREATE_DIALOG_NAME_PLACEHOLDER);
        this.nameBox.filename();
        this.nameBox.relative(inputRow).xy(0, 0).w(1F, -80).h(24);

        this.createBtn = new UIButton(UIKeys.FILM_CREATE_DIALOG_CREATE, (b) -> this.createFilm());
        this.createBtn.relative(inputRow).x(1F, -76).y(0).w(76).h(24);

        inputRow.add(this.nameBox, this.createBtn);
        creationBar.add(this.locationLabel, inputRow);

        /* 2. Section Header */
        this.sectionLabel = UI.label(UIKeys.FILM_CREATE_DIALOG_EXISTING_FILMS);
        this.sectionLabel.relative(this.rightPane).xy(0, 52).w(1F).h(16);

        /* 3. Existing Films Grid */
        UIElement filmsContainer = new UIElement();
        filmsContainer.relative(this.rightPane).xy(0, 72).w(1F).h(1F, -72);
        filmsContainer.add(new UIRenderable((context) ->
        {
            Area a = filmsContainer.area;
            context.batcher.box(a.x, a.y, a.ex(), a.ey(), 0xb00c1017);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), 0x2affffff);
        }));

        this.filmsScroll = new UIScrollView();
        this.filmsScroll.relative(filmsContainer).xy(2, 2).w(1F, -4).h(1F, -4);

        this.filmsGrid = this.filmsScroll.grid(6);
        this.filmsGrid.padding(4);
        this.filmsGrid.width(108).height(88);

        filmsContainer.add(this.filmsScroll);

        this.rightPane.add(creationBar, this.sectionLabel, filmsContainer);

        this.content.add(this.leftPane, separator, this.rightPane);
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);
        this.refreshData();
        parent.getContext().focus(this.nameBox);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ENTER) && this.nameBox.isFocused())
        {
            this.createFilm();
            return true;
        }

        return super.subKeyPressed(context);
    }

    private void promptNewFolder()
    {
        UIContext context = this.getContext();
        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (folderName) ->
            {
                String trimmed = folderName.trim();
                if (trimmed.isEmpty())
                {
                    context.notifyError(UIKeys.PANELS_MODALS_EMPTY);
                    return;
                }

                String targetFolder = this.selectedFolder.isEmpty() ? trimmed : (this.selectedFolder + "/" + trimmed);
                this.panel.getType().getRepository().addFolder(targetFolder, (success) ->
                {
                    if (success)
                    {
                        this.selectedFolder = targetFolder;
                        this.refreshData();
                    }
                });
            }
        );
        prompt.text.filename();
        UIOverlay.addOverlay(context, prompt);
    }

    private void openFolderInExplorer()
    {
        File root = this.panel.getType().getRepository().getFolder();
        if (root != null)
        {
            File target = this.selectedFolder.isEmpty() ? root : new File(root, this.selectedFolder);
            target.mkdirs();
            UIUtils.openFolder(target);
        }
    }

    public void refreshData()
    {
        this.panel.getType().getRepository().requestKeys((keys) ->
        {
            this.allKeys.clear();
            this.allKeys.addAll(keys);

            Set<String> uniqueFolders = new TreeSet<>((a, b) -> NaturalOrderComparator.compare(true, a, b));
            uniqueFolders.add("");

            for (String key : keys)
            {
                if (key.endsWith("/"))
                {
                    String folderPath = key.substring(0, key.length() - 1);
                    this.addFolderAndAncestors(uniqueFolders, folderPath);
                }
                else if (key.contains("/"))
                {
                    String folderPath = key.substring(0, key.lastIndexOf('/'));
                    this.addFolderAndAncestors(uniqueFolders, folderPath);
                }
            }

            this.folders.clear();
            this.folders.addAll(uniqueFolders);

            if (!this.folders.contains(this.selectedFolder))
            {
                this.selectedFolder = "";
            }

            this.folderList.setList(this.folders);
            this.folderList.setCurrentScroll(this.selectedFolder);
            this.folderList.update();

            this.updateFolderView();
        });
    }

    private void addFolderAndAncestors(Set<String> set, String folderPath)
    {
        if (folderPath.isEmpty())
        {
            return;
        }

        set.add(folderPath);
        int slash = folderPath.lastIndexOf('/');

        while (slash > 0)
        {
            folderPath = folderPath.substring(0, slash);
            set.add(folderPath);
            slash = folderPath.lastIndexOf('/');
        }
    }

    private void updateFolderView()
    {
        String pathDisplay = this.selectedFolder.isEmpty() ? "" : this.selectedFolder + "/";
        this.locationLabel.label = IKey.raw(String.format(UIKeys.FILM_CREATE_DIALOG_LOCATION.get(), pathDisplay));

        /* Populate films in selected folder */
        this.filmsScroll.removeAll();
        List<String> inFolder = new ArrayList<>();

        for (String key : this.allKeys)
        {
            if (key.endsWith("/"))
            {
                continue;
            }

            if (this.selectedFolder.isEmpty())
            {
                if (!key.contains("/"))
                {
                    inFolder.add(key);
                }
            }
            else
            {
                if (key.startsWith(this.selectedFolder + "/"))
                {
                    String rest = key.substring(this.selectedFolder.length() + 1);
                    if (!rest.contains("/"))
                    {
                        inFolder.add(key);
                    }
                }
            }
        }

        this.sectionLabel.label = IKey.raw(UIKeys.FILM_CREATE_DIALOG_EXISTING_FILMS.get() + " (" + inFolder.size() + ")");

        if (inFolder.isEmpty())
        {
            UILabel emptyLabel = UI.label(UIKeys.FILM_CREATE_DIALOG_EMPTY).color(Colors.setA(Colors.WHITE, 0.45F));
            emptyLabel.labelAnchor(0.5F, 0.5F);
            emptyLabel.relative(this.filmsScroll).xy(0.5F, 0.5F).w(1F).h(20).anchor(0.5F, 0.5F);
            this.filmsScroll.add(emptyLabel);
        }
        else
        {
            for (String filmId : inFolder)
            {
                this.filmsScroll.add(new UIMiniFilmCard(this, filmId));
            }
        }

        this.filmsScroll.resize();
    }

    private void createFilm()
    {
        String name = this.nameBox.getText().trim();

        if (name.isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
            return;
        }

        String targetId = this.selectedFolder.isEmpty() ? name : (this.selectedFolder + "/" + name);

        if (this.allKeys.contains(targetId) || this.allKeys.stream().anyMatch(k -> k.startsWith(targetId + "/")))
        {
            this.getContext().notifyError(UIKeys.FILM_BACKUPS_EXISTS);
            return;
        }

        this.close();

        if (this.callback != null)
        {
            this.callback.accept(targetId);
        }
    }

    /**
     * Mini card representation for films within UIAdvancedFilmDialog.
     */
    public static class UIMiniFilmCard extends UIClickable<UIMiniFilmCard>
    {
        private final UIAdvancedFilmDialog dialog;
        public final String filmId;
        public final String displayName;

        public UIMiniFilmCard(UIAdvancedFilmDialog dialog, String filmId)
        {
            super((c) ->
            {
                UIUtils.playClick();
                dialog.close();
                dialog.panel.pickData(filmId);
            });

            this.dialog = dialog;
            this.filmId = filmId;
            this.displayName = new DataPath(filmId).getLast();

            this.tooltip(IKey.raw(filmId));
            this.context((menu) ->
            {
                menu.action(Icons.FILM, UIKeys.PANELS_LANDING_OPEN, () ->
                {
                    dialog.close();
                    dialog.panel.pickData(filmId);
                });
                menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () ->
                {
                    dialog.nameBox.setText(this.displayName);
                });
            });
        }

        @Override
        protected UIMiniFilmCard get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Area a = this.area;
            boolean hovered = this.hover;
            int primary = BBSSettings.primaryColor.get();

            int bg = hovered ? 0xe01a2232 : 0xc010141d;
            int border = hovered ? (Colors.A100 | primary) : 0x2affffff;

            if (hovered)
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 6, Colors.setA(primary, 0.35F), 0);
            }
            else
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 3, Colors.A50, 0);
            }

            context.batcher.box(a.x, a.y, a.ex(), a.ey(), bg);
            context.batcher.outline(a.x, a.y, a.ex(), a.ey(), border);

            /* Preview box */
            int pad = 4;
            int previewH = a.h - 22;
            int px = a.x + pad;
            int py = a.y + pad;
            int pw = a.w - pad * 2;
            int ph = previewH - pad;

            context.batcher.box(px, py, px + pw, py + ph, 0xf0080a10);
            context.batcher.outline(px, py, px + pw, py + ph, hovered ? 0x40ffffff : 0x18ffffff);

            Texture thumb = UIFilmHubScreen.getThumbnail(this.filmId);

            if (thumb != null)
            {
                context.batcher.clip(px, py, pw, ph, context);
                context.batcher.texturedBox(thumb, Colors.WHITE, px, py, pw, ph, 0, 0, thumb.width, thumb.height, thumb.width, thumb.height);
                context.batcher.unclip(context);
            }
            else
            {
                int iconColor = hovered ? (Colors.A100 | primary) : Colors.setA(Colors.WHITE, 0.45F);
                context.batcher.icon(Icons.FILM, iconColor, px + pw / 2, py + ph / 2, 0.5F, 0.5F);
            }

            /* Label */
            FontRenderer font = context.batcher.getFont();
            String trimmed = font.limitToWidth(this.displayName, a.w - 8);
            int textX = a.mx() - font.getWidth(trimmed) / 2;
            int textY = a.ey() - 13;
            int textColor = hovered ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.85F);
            context.batcher.text(trimmed, textX, textY, textColor, false);
        }
    }
}
