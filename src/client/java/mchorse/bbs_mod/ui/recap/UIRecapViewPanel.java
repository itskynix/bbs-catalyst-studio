package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapRecord;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The editor's right panel: what of the interface and the scene is shown, and what is overridden.
 * Every change is written into the recap.
 */
public class UIRecapViewPanel extends UIScrollView
{
    private final Runnable onChange;

    public UIRecapViewPanel(Runnable onChange)
    {
        super();

        this.onChange = onChange;
        this.column(4).vertical().stretch().scroll().padding(8);
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
    }

    private void section(RecapRecord record, String title, RecapOption.Group group)
    {
        this.add(UI.label(UIRecapKeys.key(title)).color(Colors.setA(Colors.WHITE, 0.55F)));

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

                this.onChange.run();
            });

            toggle.h(14);
            this.add(toggle);

            if (value != null)
            {
                value.setEnabled(record.isOn(option));
                value.h(16);
                this.add(value);
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
                this.onChange.run();
            });

            color.setColor(record.getValue(option));

            return color;
        }

        if (option == RecapOption.WEATHER)
        {
            UICirculate weather = new UICirculate((b) ->
            {
                record.setValue(option, b.getValue());
                this.onChange.run();
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
            this.onChange.run();
        });

        trackpad.limit(option.min, option.max, true);
        trackpad.setValue(record.getValue(option));
        trackpad.tooltip(UIRecapKeys.key("view." + option.id + "_tooltip"));

        return trackpad;
    }
}
