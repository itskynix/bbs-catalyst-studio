package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.function.Consumer;

/**
 * The view settings panel: what of the interface and the scene is shown, and what is overridden,
 * each in its own group that folds. Every change is written into the recap.
 */
public class UIRecapViewPanel extends UIScrollView
{
    private final Consumer<RecapOption> onChange;

    public UIRecapViewPanel(Consumer<RecapOption> onChange)
    {
        super();

        this.onChange = onChange;
        this.column(4).vertical().stretch().scroll().padding(6);
    }

    public void fill(RecapRecord record)
    {
        this.removeAll();

        if (record == null)
        {
            return;
        }

        this.section(record, "view.hud", RecapOption.Group.HUD);
        this.section(record, "view.scene", RecapOption.Group.SCENE);
        this.section(record, "view.override", RecapOption.Group.OVERRIDE);
        this.resize();
    }

    private void section(RecapRecord record, String title, RecapOption.Group group)
    {
        UISection section = UIRecapInfoPanel.section(title, "recap." + title);

        section.fields.column(3).stretch().vertical();
        this.add(section);

        /* The viewport draws the scene; the interface pieces are kept for when a recap is exported */
        if (group == RecapOption.Group.HUD)
        {
            section.fields.add(UI.label(UIRecapKeys.key("view.hud_note")).color(RecapStyle.dim()));
        }

        for (RecapOption option : RecapOption.values())
        {
            if (option.group != group)
            {
                continue;
            }

            IKey label = UIRecapKeys.key("view." + option.id);
            UIElement value = option.hasValue ? this.value(record, option) : null;
            UIToggle toggle = new UIToggle(label, record.isOn(option), (t) ->
            {
                record.setOn(option, t.getValue());

                if (value != null)
                {
                    value.setEnabled(t.getValue());
                }

                this.onChange.accept(option);
            });

            toggle.h(14);
            section.fields.add(toggle);

            if (value != null)
            {
                value.setEnabled(record.isOn(option));
                value.h(16);
                section.fields.add(value);
            }
        }
    }

    private UIElement value(RecapRecord record, RecapOption option)
    {
        if (option == RecapOption.FOG_COLOR)
        {
            UIColor color = new UIColor((c) ->
            {
                record.setValue(option, c & 0xffffff);
                this.onChange.accept(option);
            });

            color.setColor(record.getValue(option));

            return color;
        }

        if (option == RecapOption.WEATHER)
        {
            UICirculate weather = new UICirculate((b) ->
            {
                record.setValue(option, b.getValue());
                this.onChange.accept(option);
            });

            weather.addLabel(UIRecapKeys.key("view.weather_clear"));
            weather.addLabel(UIRecapKeys.key("view.weather_rain"));
            weather.addLabel(UIRecapKeys.key("view.weather_thunder"));
            weather.setValue(record.getValue(option));

            return weather;
        }

        UITrackpad trackpad = new UITrackpad((v) ->
        {
            record.setValue(option, v.intValue());
            this.onChange.accept(option);
        });

        trackpad.limit(option.min, option.max, true);
        trackpad.setValue(record.getValue(option));
        trackpad.tooltip(UIRecapKeys.key("view." + option.id + "_tooltip"));

        return trackpad;
    }
}
