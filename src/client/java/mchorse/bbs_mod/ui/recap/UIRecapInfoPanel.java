package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.recap.RecapTime;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The record panel: what the recap is, and its marked moments, each a row that takes the cursor
 * there. Both groups fold.
 */
public class UIRecapInfoPanel extends UIScrollView
{
    /** Which groups are folded, kept for the session. */
    static final Map<String, Boolean> FOLDS = new HashMap<>();

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm").withZone(ZoneId.systemDefault());

    private final UIRecapEditor editor;

    public UIRecapInfoPanel(UIRecapEditor editor)
    {
        this.editor = editor;
        this.column(4).vertical().stretch().scroll().padding(6);
    }

    public void fill(RecapRecord record)
    {
        this.removeAll();

        if (record == null)
        {
            return;
        }

        UISection about = section("info.about", "recap.info.about");

        about.fields.add(line("home.world", () -> record.worldName));
        about.fields.add(line("home.kind", () -> UIRecapKeys.text(record.instant ? "home.kind_instant" : "home.kind_manual")));
        about.fields.add(line("home.duration", () -> RecapTime.format(record.ticks)));
        about.fields.add(line("home.date", () -> record.created <= 0 ? "-" : DATE.format(Instant.ofEpochMilli(record.created))));

        UISection markers = section("info.markers", "recap.info.markers");

        if (record.markers.isEmpty())
        {
            markers.fields.add(UI.label(UIRecapKeys.key("info.no_markers")).color(RecapStyle.dim()));
        }

        for (int i = 0; i < record.markers.size(); i++)
        {
            markers.fields.add(new UIMarkerRow(i + 1, record.markers.get(i)));
        }

        this.add(about, markers);
        this.resize();
    }

    static UISection section(String title, String fold)
    {
        UISection section = new UISection(UIRecapKeys.key(title));

        section.remember(FOLDS, fold, true);
        section.fields.column(2).stretch().vertical().height(14);

        return section;
    }

    /** A name and a value, on one line. */
    private static UIElement line(String label, Supplier<String> value)
    {
        UIElement line = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                FontRenderer font = context.batcher.getFont();
                String text = font.limitToWidth(value.get(), Math.max(20, this.area.w - 70));

                context.batcher.text(UIRecapKeys.text(label), this.area.x + 2, this.area.y + 3, RecapStyle.dim(), false);
                context.batcher.text(text, this.area.ex() - 2 - font.getWidth(text), this.area.y + 3, RecapStyle.text(), false);
            }
        };

        line.h(14);

        return line;
    }

    /** A marked moment; clicked, the cursor goes there. */
    private class UIMarkerRow extends UIClickable<UIMarkerRow>
    {
        private final int number;
        private final int tick;

        private UIMarkerRow(int number, int tick)
        {
            super((b) -> {});

            this.number = number;
            this.tick = tick;
            this.callback = (b) -> UIRecapInfoPanel.this.editor.seek(this.tick);
            this.h(16);
        }

        @Override
        protected UIMarkerRow get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean here = UIRecapInfoPanel.this.editor.getCursor() == this.tick;
            String time = RecapTime.formatWithTicks(this.tick);

            RecapStyle.row(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), here, this.hover);
            context.batcher.box(this.area.x + 4, this.area.y + 4, this.area.x + 8, this.area.ey() - 4, RecapStyle.marker());
            context.batcher.text("#" + this.number, this.area.x + 13, this.area.y + 4, RecapStyle.dim(), false);
            context.batcher.text(time, this.area.ex() - 4 - context.batcher.getFont().getWidth(time), this.area.y + 4, RecapStyle.text(), false);
        }
    }
}
