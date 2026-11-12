package mchorse.bbs_mod.dev;

import mchorse.bbs_mod.l10n.L10n;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.OrderedText;

import java.util.List;

/**
 * A yellow and red box on the main menu when an external IRLights / IRLCore mod is installed next
 * to the embedded engine. Nothing is disabled and nothing crashes; the box only says why shader
 * errors may follow.
 */
public final class DevConflictWarning
{
    private static boolean registered;

    private DevConflictWarning()
    {}

    public static void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        if (!DevAccessGuard.externalIrlModLoaded())
        {
            return;
        }

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
        {
            if (!(screen instanceof TitleScreen))
            {
                return;
            }

            ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) ->
            {
                TextRenderer font = MinecraftClient.getInstance().textRenderer;
                int boxW = Math.min(width - 20, 360);
                List<OrderedText> lines = font.wrapLines(net.minecraft.text.Text.literal(L10n.lang("bbs.ui.settings.dev.conflict").get()), boxW - 16);
                int boxH = lines.size() * (font.fontHeight + 2) + 12;
                int x = (width - boxW) / 2;
                int y = 6;

                context.fill(x, y, x + boxW, y + boxH, 0xd0402a00);
                context.fill(x, y, x + 3, y + boxH, 0xffff4040);
                context.drawBorder(x, y, boxW, boxH, 0xffffc233);

                int ty = y + 6;

                for (OrderedText line : lines)
                {
                    context.drawText(font, line, x + 10, ty, 0xffffd966, true);
                    ty += font.fontHeight + 2;
                }
            });
        });
    }
}
