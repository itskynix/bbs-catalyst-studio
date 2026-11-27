package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapStore;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.recap.RecapWorlds;
import mchorse.bbs_mod.ui.dashboard.panels.landing.LandingBackdrop;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.windows.UITheme;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Where a recap is picked: totals across the top, a reel of recaps on the left, each with its own
 * little timeline of marked moments, and what the picked one holds on the right with what can be
 * done with it.
 */
public class UIRecapHome extends UIElement
{
    private static final int HEADER = 58;
    private static final int MARGIN = 16;
    private static final int ROW_H = 46;
    private static final int PANEL_HEAD = 18;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm").withZone(ZoneId.systemDefault());

    private final UIRecapPanel panel;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIScrollView list;
    private final UIElement details;
    private final UIButton open;
    private final UIButton rename;
    private final UIButton delete;

    private List<RecapRecord> records = List.of();
    private RecapRecord picked;

    public UIRecapHome(UIRecapPanel panel)
    {
        this.panel = panel;

        UIIcon folder = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(RecapStore.folder()));
        UIIcon reload = new UIIcon(Icons.REFRESH, (b) -> this.refresh());

        folder.tooltip(UIRecapKeys.key("home.folder"));
        folder.relative(this).x(1F, -MARGIN - 18).y(20).wh(18, 18);
        reload.tooltip(UIRecapKeys.key("home.refresh"));
        reload.relative(this).x(1F, -MARGIN - 38).y(20).wh(18, 18);

        this.list = new UIScrollView();
        this.list.relative(this).x(MARGIN + 1).y(HEADER + PANEL_HEAD + 1).w(0.6F, -MARGIN - 2).h(1F, -HEADER - MARGIN - PANEL_HEAD - 2);
        this.list.column(4).vertical().stretch().scroll().padding(4);

        this.details = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                UIRecapHome.this.renderDetails(context, this.area);
                super.render(context);
            }
        };
        this.details.relative(this).x(0.6F, 8).y(HEADER).w(0.4F, -MARGIN - 8).h(1F, -HEADER - MARGIN);

        this.open = new UIButton(UIRecapKeys.key("home.open"), (b) -> this.openPicked());
        this.rename = new UIButton(UIRecapKeys.key("home.rename"), (b) -> this.renamePicked());
        this.delete = new UIButton(UIRecapKeys.key("home.delete"), (b) -> this.deletePicked());

        this.open.primary();
        this.open.relative(this.details).x(8).y(1F, -52).w(1F, -16).h(20);
        this.rename.relative(this.details).x(8).y(1F, -28).w(0.5F, -10).h(20);
        this.delete.relative(this.details).x(0.5F, 2).y(1F, -28).w(0.5F, -10).h(20);
        this.details.add(this.open, this.rename, this.delete);

        this.add(folder, reload, this.list, this.details);
    }

    public void refresh()
    {
        String keep = this.picked == null ? "" : this.picked.id;

        this.records = RecapStore.list();
        this.picked = this.records.isEmpty() ? null : this.records.get(0);

        for (RecapRecord record : this.records)
        {
            if (record.id.equals(keep))
            {
                this.picked = record;
            }
        }

        this.list.removeAll();

        for (RecapRecord record : this.records)
        {
            this.list.add(new UIReelRow(record));
        }

        this.updateButtons();
        this.resize();
    }

    private void updateButtons()
    {
        boolean has = this.picked != null;

        this.open.setEnabled(has);
        this.rename.setEnabled(has);
        this.delete.setEnabled(has);
    }

    private void pick(RecapRecord record)
    {
        this.picked = record;
        this.updateButtons();
    }

    private void openPicked()
    {
        RecapRecord record = this.picked;

        if (record == null)
        {
            return;
        }

        if (RecapWorlds.isCurrent(record))
        {
            this.panel.openEditor(record);

            return;
        }

        if (!RecapWorlds.canReach(record))
        {
            this.getContext().notifyError(UIRecapKeys.key("home.world_missing"));

            return;
        }

        if (MinecraftClient.getInstance().world == null)
        {
            RecapWorlds.travel(record);

            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
            UIRecapKeys.key("home.travel_title"),
            UIRecapKeys.key("home.travel_message"),
            (confirmed) ->
            {
                if (confirmed)
                {
                    RecapWorlds.travel(record);
                }
            }));
    }

    private void renamePicked()
    {
        RecapRecord record = this.picked;

        if (record == null)
        {
            return;
        }

        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(UIRecapKeys.key("home.rename"), UIRecapKeys.key("home.rename_message"), (name) ->
        {
            String trimmed = name.trim();

            if (!trimmed.isEmpty())
            {
                record.name = trimmed;
                RecapStore.save(record);
                this.refresh();
            }
        });

        prompt.text.setText(record.name);
        UIOverlay.addOverlay(this.getContext(), prompt);
    }

    private void deletePicked()
    {
        RecapRecord record = this.picked;

        if (record == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
            UIRecapKeys.key("home.delete"),
            UIRecapKeys.key("home.delete_message"),
            (confirmed) ->
            {
                if (confirmed)
                {
                    RecapStore.delete(record);
                    this.picked = null;
                    this.refresh();
                }
            }));
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        /* The glass themes keep the moving backdrop of the landing; the others their own plain ground */
        if (UITheme.glass())
        {
            this.backdrop.render(context, this.area);
        }
        else
        {
            RecapStyle.backdrop(context.batcher, this.area);
        }

        FontRenderer font = context.batcher.getFont();
        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();
        stack.translate(this.area.x + MARGIN, this.area.y + 14, 0F);
        stack.scale(1.6F, 1.6F, 1F);
        context.batcher.text("Recap Studio", 0, 0, RecapStyle.text(), !RecapStyle.light() && !UITheme.flat());
        stack.pop();

        context.batcher.text(UIRecapKeys.text("home.subtitle"), this.area.x + MARGIN, this.area.y + 34, RecapStyle.dim(), false);

        int total = 0;
        int marks = 0;

        for (RecapRecord record : this.records)
        {
            total += record.ticks;
            marks += record.markers.size();
        }

        int right = this.area.ex() - MARGIN - 46;

        right = this.renderChip(context, font, right, UIRecapKeys.text("home.markers"), String.valueOf(marks));
        right = this.renderChip(context, font, right, UIRecapKeys.text("home.total_time"), RecapTime.format(total));
        this.renderChip(context, font, right, UIRecapKeys.text("home.count"), String.valueOf(this.records.size()));

        this.renderPanel(context, this.list.area.x - 1, this.list.area.y - PANEL_HEAD - 1, this.list.area.ex() + 1, this.list.area.ey() + 1, Icons.FILM, UIRecapKeys.text("home.list"));
        super.render(context);

        if (this.records.isEmpty())
        {
            this.renderEmpty(context, this.list.area);
        }
    }

    /** One total in the header, drawn to the left of {@code right}; returns where the next one ends. */
    private int renderChip(UIContext context, FontRenderer font, int right, String label, String value)
    {
        int width = Math.max(font.getWidth(label), font.getWidth(value)) + 16;
        int x = right - width;
        int y = this.area.y + 14;

        if (x < this.area.x + 190)
        {
            return x;
        }

        RecapStyle.surface(context.batcher, x, y, x + width, y + 28, 0, false);
        context.batcher.box(x + 1, y + 4, x + 3, y + 24, RecapStyle.accent());
        context.batcher.text(label, x + 8, y + 4, RecapStyle.dim(), false);
        context.batcher.text(value, x + 8, y + 16, RecapStyle.text(), false);

        return x - 6;
    }

    /** A panel of the picker, in the theme's material, with its title on its header. */
    private void renderPanel(UIContext context, int x1, int y1, int x2, int y2, mchorse.bbs_mod.ui.utils.icons.Icon icon, String title)
    {
        int color = RecapStyle.header();

        RecapStyle.surface(context.batcher, x1, y1, x2, y2, PANEL_HEAD, false);
        context.batcher.icon(icon, color, x1 + 5, y1 + PANEL_HEAD / 2F, 0F, 0.5F);
        context.batcher.text(context.batcher.getFont().limitToWidth(title, x2 - x1 - 30), x1 + 23, y1 + (PANEL_HEAD - 8) / 2F, color, RecapStyle.headerShadow());
    }

    private void renderEmpty(UIContext context, Area a)
    {
        FontRenderer font = context.batcher.getFont();
        int width = Math.max(80, a.w - 40);
        List<String> lines = font.wrap(UIRecapKeys.text("home.empty"), width);
        int y = a.my() - lines.size() * (font.getHeight() + 3) / 2;

        context.batcher.icon(Icons.FILM, RecapStyle.faint(), a.mx(), y - 20, 0.5F, 0.5F);

        for (String line : lines)
        {
            context.batcher.text(line, a.mx() - font.getWidth(line) / 2, y, RecapStyle.dim(), false);
            y += font.getHeight() + 3;
        }
    }

    private void renderDetails(UIContext context, Area a)
    {
        this.renderPanel(context, a.x, a.y, a.ex(), a.ey(), Icons.FILE, UIRecapKeys.text("home.picked"));

        if (this.picked == null)
        {
            return;
        }

        RecapRecord record = this.picked;
        FontRenderer font = context.batcher.getFont();
        int x = a.x + 10;
        int width = a.w - 20;
        int y = a.y + PANEL_HEAD + 8;

        context.batcher.text(font.limitToWidth(record.name, width), x, y, RecapStyle.text(), !RecapStyle.light() && !UITheme.flat());
        y += 18;

        renderStrip(context, record, x, y, width, 18);
        y += 28;

        y = this.renderInfo(context, font, x, y, width, "home.world", record.worldName);
        y = this.renderInfo(context, font, x, y, width, "home.kind", UIRecapKeys.text(record.instant ? "home.kind_instant" : "home.kind_manual"));
        y = this.renderInfo(context, font, x, y, width, "home.duration", RecapTime.format(record.ticks));
        y = this.renderInfo(context, font, x, y, width, "home.date", record.created <= 0 ? "-" : DATE.format(Instant.ofEpochMilli(record.created)));
        this.renderInfo(context, font, x, y, width, "home.markers", String.valueOf(record.markers.size()));
    }

    private int renderInfo(UIContext context, FontRenderer font, int x, int y, int width, String label, String value)
    {
        context.batcher.text(UIRecapKeys.text(label), x, y, RecapStyle.dim(), false);

        String limited = font.limitToWidth(value, Math.max(20, width - 90));

        context.batcher.text(limited, x + width - font.getWidth(limited), y, RecapStyle.text(), false);

        return y + 14;
    }

    /** A recap's length as a bar, with a line where each moment was marked. */
    static void renderStrip(UIContext context, RecapRecord record, int x, int y, int width, int height)
    {
        int accent = RecapStyle.accent();

        context.batcher.box(x, y, x + width, y + height, RecapStyle.light() ? 0x402b7ab8 : 0xff1b212e);
        context.batcher.box(x, y + height - 2, x + width, y + height, Colors.setA(accent, 0.7F));

        for (int marker : record.markers)
        {
            int mx = x + (record.ticks <= 0 ? 0 : (int) ((long) marker * (width - 1) / record.ticks));

            context.batcher.box(mx, y, mx + 1, y + height, RecapStyle.marker());
        }
    }

    /** One recap in the reel. */
    private class UIReelRow extends UIClickable<UIReelRow>
    {
        private final RecapRecord record;
        private long lastClick;

        private UIReelRow(RecapRecord record)
        {
            super((b) -> {});

            this.record = record;
            this.h(ROW_H);
            this.callback = (row) ->
            {
                long now = System.currentTimeMillis();
                boolean twice = UIRecapHome.this.picked == this.record && now - this.lastClick < 400L;

                this.lastClick = now;
                UIRecapHome.this.pick(this.record);

                if (twice)
                {
                    UIRecapHome.this.openPicked();
                }
            };
        }

        @Override
        protected UIReelRow get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            Area a = this.area;
            FontRenderer font = context.batcher.getFont();
            boolean selected = UIRecapHome.this.picked == this.record;
            boolean light = RecapStyle.light();

            RecapStyle.row(context.batcher, a.x, a.y, a.ex(), a.ey(), selected, this.hover);

            /* The perforations of a film strip down the left edge */
            context.batcher.box(a.x + 1, a.y + 2, a.x + 9, a.ey() - 2, light ? 0x502b7ab8 : 0xff0a0d13);

            for (int hole = a.y + 5; hole < a.ey() - 5; hole += 9)
            {
                context.batcher.box(a.x + 3, hole, a.x + 7, hole + 4, light ? 0xd0ffffff : 0xff2a3140);
            }

            int x = a.x + 16;
            int infoWidth = Math.max(40, a.w - 26);
            String time = RecapTime.format(this.record.ticks);

            context.batcher.text(font.limitToWidth(this.record.name, infoWidth - font.getWidth(time) - 8), x, a.y + 6, RecapStyle.text(), false);
            context.batcher.text(time, a.ex() - 8 - font.getWidth(time), a.y + 6, RecapStyle.text(), false);

            String meta = this.record.worldName + "  ·  " + (this.record.created <= 0 ? "-" : DATE.format(Instant.ofEpochMilli(this.record.created)))
                + "  ·  " + this.record.markers.size() + " " + UIRecapKeys.text("home.markers");

            context.batcher.text(font.limitToWidth(meta, infoWidth), x, a.y + 18, RecapStyle.dim(), false);
            renderStrip(context, this.record, x, a.y + 31, infoWidth - 6, 9);
        }
    }
}
