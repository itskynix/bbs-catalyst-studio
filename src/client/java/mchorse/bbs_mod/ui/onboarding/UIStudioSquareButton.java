package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;

/**
 * The square button beside the title screen's menu: the studio's logo, one click to the Central Hub,
 * with no world needed. Drawn flat, in the accent colour when the cursor is on it.
 */
public class UIStudioSquareButton extends ButtonWidget
{
    private static final Identifier LOGO = new Identifier("bbs", "icon.png");

    /* The logo's size, read once; 0 when it could not be */
    private static int logoWidth = -1;
    private static int logoHeight;

    public UIStudioSquareButton(int x, int y, int size, Runnable action)
    {
        super(x, y, size, size, Text.empty(), (button) -> action.run(), DEFAULT_NARRATION_SUPPLIER);

        this.setTooltip(Tooltip.of(Text.literal(L10n.lang("bbs.hub.title_button").get())));
    }

    private static void measure()
    {
        if (logoWidth >= 0)
        {
            return;
        }

        logoWidth = 0;

        try (InputStream stream = MinecraftClient.getInstance().getResourceManager().open(LOGO); NativeImage image = NativeImage.read(stream))
        {
            logoWidth = image.getWidth();
            logoHeight = image.getHeight();
        }
        catch (Exception ignored)
        {}
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta)
    {
        int x = this.getX();
        int y = this.getY();
        int size = this.getWidth();
        boolean hover = this.isHovered();
        int accent = 0xFF000000 | BBSSettings.primaryColor.get();

        context.fill(x, y, x + size, y + size, hover ? 0xE0202838 : 0xC0101420);
        context.drawBorder(x, y, size, size, hover ? accent : 0x40FFFFFF);

        measure();

        if (logoWidth > 0)
        {
            int inner = size - 6;

            context.drawTexture(LOGO, x + 3, y + 3, inner, inner, 0F, 0F, logoWidth, logoHeight, logoWidth, logoHeight);
        }
        else
        {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, "B", x + size / 2, y + size / 2 - 4, accent);
        }
    }
}
