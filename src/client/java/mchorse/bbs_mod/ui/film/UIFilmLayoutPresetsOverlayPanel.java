package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.onboarding.welcome.NleLayouts;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The film editor's layout presets: the seven layouts of the setup wizard as cards, each drawn as
 * a small schematic, and below them the user's own saved layouts, with a way to save the current
 * one under a name. "Custom" is the layout the editor had when this was opened, so trying the
 * others always has a way back.
 */
public class UIFilmLayoutPresetsOverlayPanel extends UIOverlayPanel
{
    private static final PresetManager MANAGER = PresetManager.LAYOUTS;

    private final Supplier<MapType> current;
    private final Consumer<MapType> apply;
    private final EditorLayoutNode original;
    private final Map<String, EditorLayoutNode> saved = new LinkedHashMap<>();

    public UIFilmLayoutPresetsOverlayPanel(Supplier<MapType> current, Consumer<MapType> apply)
    {
        super(UIKeys.FILM_LAYOUT_PRESETS);

        this.current = current;
        this.apply = apply;
        this.original = treeOf(current.get());

        UIIcon save = new UIIcon(Icons.SAVED, (b) -> promptSave(this.getContext(), this.current, this::refresh));
        UIIcon folder = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(MANAGER.getFolder()));

        save.tooltip(L10n.lang("bbs.ui.film.layout_save_preset"), Direction.LEFT);
        folder.tooltip(UIKeys.PRESETS_OPEN, Direction.LEFT);
        this.icons.add(save, folder);

        UIElement grid = new Grid();

        grid.full(this.content);
        this.content.add(grid);
        this.refresh();
    }

    private static EditorLayoutNode treeOf(MapType data)
    {
        BaseType layout = data == null ? null : data.get("film_layout");

        return layout == null ? null : EditorLayoutNode.fromData(layout);
    }

    private static MapType dataOf(EditorLayoutNode tree)
    {
        MapType data = new MapType();

        data.put("film_layout", tree.toData());

        return data;
    }

    /** Ask for a name, then keep the current layout under it */
    public static void promptSave(UIContext context, Supplier<MapType> current, Runnable after)
    {
        UIPromptOverlayPanel pane = new UIPromptOverlayPanel(UIKeys.PRESETS_SAVE_TITLE, UIKeys.PRESETS_SAVE_DESCRIPTION, (name) ->
        {
            MapType data = current.get();

            if (name != null && !name.trim().isEmpty() && data != null)
            {
                MANAGER.save(name.trim(), data);

                if (after != null)
                {
                    after.run();
                }
            }
        });

        pane.text.filename();
        UIOverlay.addOverlay(context, pane);
    }

    private void refresh()
    {
        this.saved.clear();

        for (String id : MANAGER.getKeys())
        {
            EditorLayoutNode tree = treeOf(MANAGER.load(id));

            if (tree != null)
            {
                this.saved.put(id, tree);
            }
        }
    }

    private class Grid extends UIElement
    {
        private final List<Object[]> hits = new ArrayList<>();
        private float scroll;
        private float target;
        private float max;
        private long lastNanos;
        private float clock;

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton != 0 || !this.area.isInside(context))
            {
                return false;
            }

            for (Object[] hit : this.hits)
            {
                int[] r = (int[]) hit[0];

                if (context.mouseX >= r[0] && context.mouseX < r[2] && context.mouseY >= r[1] && context.mouseY < r[3])
                {
                    UIUtils.playClick();
                    ((Runnable) hit[1]).run();

                    return true;
                }
            }

            return false;
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            if (!this.area.isInside(context))
            {
                return false;
            }

            this.target = Math.max(0F, Math.min(this.max, this.target - (float) context.mouseWheel * 24F));

            return true;
        }

        private boolean hover(UIContext c, int x, int y, int w, int h)
        {
            return this.area.isInside(c) && c.mouseX >= x && c.mouseX < x + w && c.mouseY >= y && c.mouseY < y + h;
        }

        @Override
        public void render(UIContext c)
        {
            long now = System.nanoTime();
            float dt = this.lastNanos == 0L ? 0F : Math.min((now - this.lastNanos) / 1.0E9F, 0.1F);
            float smoothing = BBSSettings.getScrollSmoothingIntensity();

            this.lastNanos = now;
            this.clock += dt;
            this.scroll = smoothing > 0F ? this.scroll + (this.target - this.scroll) * Math.min(1F, dt * 20F / (2.5F * smoothing)) : this.target;
            this.hits.clear();

            FontRenderer font = c.batcher.getFont();
            int accent = Colors.A100 | BBSSettings.primaryColor.get();
            int pad = 8;
            int x = this.area.x + pad;
            int w = this.area.w - pad * 2;
            int y = this.area.y + pad - Math.round(this.scroll);

            c.batcher.clip(this.area, c);

            /* The seven layouts, four to a row */
            c.batcher.textShadow(L10n.lang("bbs.ui.film.layout_presets.builtin").get(), x, y, Colors.WHITE);
            y += 14;

            int columns = 4;
            int gap = 6;
            int cardW = (w - gap * (columns - 1)) / columns;
            int previewH = Math.round(cardW * 0.56F);
            int cardH = previewH + 22;
            String hoverDescription = null;

            for (int i = 0; i < NleLayouts.IDS.length; i++)
            {
                int cx = x + (i % columns) * (cardW + gap);
                int cy = y + (i / columns) * (cardH + gap);
                boolean hover = this.hover(c, cx, cy, cardW, cardH);
                EditorLayoutNode tree = NleLayouts.tree(i, UIFilmLayoutPresetsOverlayPanel.this.original);
                final EditorLayoutNode chosen = tree;

                c.batcher.box(cx, cy, cx + cardW, cy + cardH, hover ? 0xFF262C3C : 0xFF1A1E2A);
                c.batcher.outline(cx, cy, cx + cardW, cy + cardH, hover ? accent : Colors.setA(Colors.WHITE, 0.1F));
                c.batcher.box(cx + 4, cy + 4, cx + cardW - 4, cy + 4 + previewH, 0xFF14131C);
                NleLayouts.schematic(c, tree, cx + 5, cy + 5, cardW - 10, previewH - 2, i == NleLayouts.REELS, this.clock, 1F, accent);
                c.batcher.text(font.limitToWidth(NleLayouts.name(i).get(), cardW - 10), cx + 6, cy + previewH + 9, hover ? Colors.WHITE : 0xFFCCCCCC, false);

                if (hover)
                {
                    hoverDescription = NleLayouts.description(i).get();
                }

                this.hits.add(new Object[] {new int[] {cx, cy, cx + cardW, cy + cardH}, (Runnable) () -> UIFilmLayoutPresetsOverlayPanel.this.apply.accept(dataOf(chosen))});
            }

            y += ((NleLayouts.IDS.length + columns - 1) / columns) * (cardH + gap) + 2;

            /* What the card under the cursor is */
            String description = hoverDescription == null ? "" : hoverDescription;
            int used = c.batcher.wallText(description, x, y, 0xFF9A9FAE, w, 11);

            y += Math.max(used, 11) + 10;

            /* The user's own */
            String savedTitle = L10n.lang("bbs.ui.film.layout_presets.saved").get();
            String saveLabel = L10n.lang("bbs.ui.film.layout_save_preset").get();
            int saveW = font.getWidth(saveLabel) + 16;
            int saveX = x + w - saveW;
            boolean saveHover = this.hover(c, saveX, y - 4, saveW, 16);

            c.batcher.textShadow(savedTitle, x, y, Colors.WHITE);
            c.batcher.box(saveX, y - 4, saveX + saveW, y + 12, saveHover ? Colors.lerp(accent, Colors.WHITE, 0.15F) : accent);
            c.batcher.text(saveLabel, saveX + 8, y, Colors.WHITE, false);
            this.hits.add(new Object[] {new int[] {saveX, y - 4, saveX + saveW, y + 12}, (Runnable) () -> promptSave(this.getContext(), UIFilmLayoutPresetsOverlayPanel.this.current, UIFilmLayoutPresetsOverlayPanel.this::refresh)});
            y += 18;

            if (UIFilmLayoutPresetsOverlayPanel.this.saved.isEmpty())
            {
                y += c.batcher.wallText(L10n.lang("bbs.ui.film.layout_presets.empty").get(), x, y, 0xFF9A9FAE, w, 11);
            }

            int rowH = 34;

            for (Map.Entry<String, EditorLayoutNode> entry : UIFilmLayoutPresetsOverlayPanel.this.saved.entrySet())
            {
                boolean hover = this.hover(c, x, y, w, rowH);
                final EditorLayoutNode tree = entry.getValue();

                c.batcher.box(x, y, x + w, y + rowH, hover ? 0xFF262C3C : 0xFF1A1E2A);
                c.batcher.box(x, y, x + 2, y + rowH, hover ? accent : Colors.setA(accent, 0.5F));
                c.batcher.box(x + 6, y + 3, x + 6 + 50, y + rowH - 3, 0xFF14131C);
                NleLayouts.schematic(c, tree, x + 7, y + 4, 48, rowH - 8, false, this.clock, 1F, accent);
                c.batcher.text(font.limitToWidth(entry.getKey(), w - 70), x + 64, y + rowH / 2 - 4, hover ? Colors.WHITE : 0xFFCCCCCC, false);
                this.hits.add(new Object[] {new int[] {x, y, x + w, y + rowH}, (Runnable) () -> UIFilmLayoutPresetsOverlayPanel.this.apply.accept(dataOf(tree))});
                y += rowH + 4;
            }

            int content = y + Math.round(this.scroll) - this.area.y + pad;

            this.max = Math.max(0, content - this.area.h);
            this.target = Math.min(this.target, this.max);

            if (this.max > 0F)
            {
                int bar = Math.max(16, (int) (this.area.h * (float) this.area.h / content));
                int at = (int) ((this.area.h - bar) * this.scroll / this.max);

                c.batcher.box(this.area.ex() - 3, this.area.y + at, this.area.ex(), this.area.y + at + bar, accent);
            }

            c.batcher.unclip(c);

            /* Hits outside the visible part of the list do not count */
            this.hits.removeIf((hit) ->
            {
                int[] r = (int[]) hit[0];

                r[1] = Math.max(r[1], this.area.y);
                r[3] = Math.min(r[3], this.area.ey());

                return r[3] <= r[1];
            });

            super.render(c);
        }
    }
}
