package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.dev.DevAccessGuard;
import mchorse.bbs_mod.dev.DevConfig;
import mchorse.bbs_mod.dev.DevStage;
import mchorse.bbs_mod.dev.DevTools;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.notifications.Notification;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;
import org.qualet.irl.light.VlGlobalsBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * The page of the developer tab: the conflict warning, the live mod stage, the telemetry mode of
 * every metric and a text view of the embedded IRLights state. It is built only when
 * {@link DevAccessGuard#hasAccess()} holds.
 */
public class UIDevSettingsPage
{
    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.settings.dev." + name);
    }

    public static List<UIElement> create()
    {
        List<UIElement> elements = new ArrayList<>();

        if (DevAccessGuard.externalIrlModLoaded())
        {
            elements.add(new UIConflictWarning());
        }

        elements.add(UI.label(key("stage")));

        UICirculate stage = new UICirculate((b) ->
        {
            DevConfig.setStage(DevStage.values()[b.getValue()]);
            mchorse.bbs_mod.dev.DevStageBadge.refreshWindowTitle();
        });

        for (DevStage value : DevStage.values())
        {
            stage.addLabel(L10n.lang("bbs.ui.settings.dev.stage." + value.name().toLowerCase()));
        }

        stage.setValue(DevConfig.getStage().ordinal());
        elements.add(stage);

        elements.add(UI.label(key("tick")).marginTop(10));

        UICirculate tick = new UICirculate((b) -> DevTools.setTickMode(DevTools.TickMode.values()[b.getValue()]));

        for (DevTools.TickMode value : DevTools.TickMode.values())
        {
            tick.addLabel(L10n.lang("bbs.ui.settings.dev.tick." + value.name().toLowerCase()));
        }

        tick.setValue(DevTools.getTickMode().ordinal());
        elements.add(tick);

        elements.add(UI.label(key("log")).marginTop(10));

        UICirculate log = new UICirculate((b) ->
        {
            DevTools.LogLevel level = DevTools.LogLevel.values()[b.getValue()];

            DevConfig.setLogLevel(level);
            DevTools.applyLogLevel(level);
        });

        for (DevTools.LogLevel value : DevTools.LogLevel.values())
        {
            log.addLabel(L10n.lang("bbs.ui.settings.dev.log." + value.name().toLowerCase()));
        }

        log.setValue(DevConfig.getLogLevel().ordinal());
        elements.add(log);

        UIButton purge = new UIButton(key("purge"), (b) ->
        {
            DevTools.purgeBuffers();

            UIContext context = b.getContext();

            if (context != null)
            {
                context.notifySuccess(key("purge_done"));

                List<Notification> list = context.notifications.notifications;

                /* Two seconds instead of the usual four */
                list.get(list.size() - 1).tick = 40;
            }
        });

        purge.marginTop(10);
        elements.add(purge);

        elements.add(UI.label(key("telemetry")).marginTop(10));

        String[] ids = {"fps", "tps", "playhead", "memory"};

        for (int i = 0; i < ids.length; i++)
        {
            int metric = i;
            UICirculate mode = new UICirculate((b) -> DevConfig.setMode(metric, b.getValue()));

            mode.addLabel(key("mode_off"));
            mode.addLabel(key("mode_text"));
            mode.addLabel(key("mode_graph"));
            mode.addLabel(key("mode_both"));
            mode.setValue(DevConfig.getMode(metric));

            elements.add(UI.row(UI.label(key("metric_" + ids[i])), mode));
        }

        elements.add(UI.label(key("live")).marginTop(10));
        elements.add(new UILiveStatus());

        return elements;
    }

    /** The yellow and red box that says an external IRLights / IRLCore is next to the embedded one. */
    public static class UIConflictWarning extends UIElement
    {
        public UIConflictWarning()
        {
            this.h(70);
            this.marginBottom(8);
        }

        @Override
        public void render(UIContext context)
        {
            int x1 = this.area.x;
            int y1 = this.area.y;
            int x2 = this.area.ex();
            int y2 = this.area.ey();

            context.batcher.box(x1, y1, x2, y2, 0xc0402a00);
            context.batcher.box(x1, y1, x1 + 3, y2, 0xffff4040);
            context.batcher.outline(x1, y1, x2, y2, 0xffffc233);
            context.batcher.wallText(key("conflict").get(), x1 + 10, y1 + 8, 0xffffd966, this.area.w - 20, 11);

            super.render(context);
        }
    }

    /** Text lines of what the embedded IRLights engine is handing the shader, refreshed every frame. */
    public static class UILiveStatus extends UIElement
    {
        public UILiveStatus()
        {
            this.h(84);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x60000000);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0x30ffffff);

            int y = this.area.y + 6;

            for (String line : VlGlobalsBuffer.liveStatus())
            {
                context.batcher.text(font.limitToWidth(line, this.area.w - 16), this.area.x + 8, y, Colors.WHITE, false);

                y += font.getHeight() + 3;
            }

            super.render(context);
        }
    }
}
