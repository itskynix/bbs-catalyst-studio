package mchorse.bbs_mod.ui.film.clips.pov;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.film.pov.PovScreenEffectsClip;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The visual effects clip's picker: each effect as a cell with a picture of what it lays over the
 * frame (the vignette's dark rim, the pumpkin's mask, the frost, the portal swirl of the nausea, the
 * totem), its name and its kind, with the three kinds as tabs.
 */
public class UIPovVisualEffectPicker extends UIPovGridPicker<String>
{
    private static final int[] COLORS = {0x5599ff, 0xffaa33, 0xaa55ff};

    private final Consumer<String> callback;

    public UIPovVisualEffectPicker(Consumer<String> callback)
    {
        super(L10n.lang("bbs.ui.film.pov_clips.screen.picker"));

        this.callback = callback;
        this.refresh();
    }

    private static int kindOf(String type)
    {
        return PovScreenEffectsClip.VISION.contains(type) ? 0 : PovScreenEffectsClip.HAZARDS.contains(type) ? 1 : 2;
    }

    private static IKey name(String type)
    {
        return L10n.lang("bbs.ui.film.pov_clips.screen.effects." + type);
    }

    @Override
    protected List<IKey> getTabs()
    {
        return List.of(
            L10n.lang("bbs.ui.film.pov_clips.screen.tabs.vision"),
            L10n.lang("bbs.ui.film.pov_clips.screen.tabs.hazards"),
            L10n.lang("bbs.ui.film.pov_clips.screen.tabs.popups"));
    }

    @Override
    protected boolean inTab(String entry, int tab)
    {
        return kindOf(entry) == tab;
    }

    @Override
    protected int tabColor(int tab)
    {
        return COLORS[tab];
    }

    @Override
    protected List<String> getEntries()
    {
        List<String> types = new ArrayList<>(PovScreenEffectsClip.VISION);

        types.addAll(PovScreenEffectsClip.HAZARDS);
        types.addAll(PovScreenEffectsClip.POPUPS);

        return types;
    }

    @Override
    protected boolean matches(String entry, String needle)
    {
        return entry.contains(needle) || name(entry).get().toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    protected void pick(String entry)
    {
        this.callback.accept(entry);
    }

    @Override
    protected void renderCell(UIContext context, String type, int x, int y, int w, int h, boolean hovered)
    {
        FontRenderer font = context.batcher.getFont();
        int px = x + 4;
        int py = y + 4;
        int pw = w - 8;
        int ph = h - 30;

        /* A patch of grass green standing for the world under the effect */
        context.batcher.box(px, py, px + pw, py + ph, 0xff4f7d3a);
        this.renderPreview(context.batcher.getContext(), type, px, py, pw, ph);

        /* The name without its kind; the kind is the line under it */
        String name = name(type).get();
        int colon = name.indexOf(": ");
        int kind = kindOf(type);

        context.batcher.textShadow(font.limitToWidth(colon >= 0 ? name.substring(colon + 2) : name, w - 8), x + 4, y + h - 23, Colors.WHITE);
        context.batcher.textShadow(font.limitToWidth(this.getTabs().get(kind).get(), w - 8), x + 4, y + h - 13, Colors.A100 | COLORS[kind]);
    }

    private void renderPreview(DrawContext context, String type, int x, int y, int w, int h)
    {
        switch (type)
        {
            /* The vignette darkens what is under it by its own colour, as the game blends it */
            case "vignette" -> this.texture(context, "textures/misc/vignette.png", x, y, w, h, 256, 256, true);
            case "pumpkin" -> this.texture(context, "textures/misc/pumpkinblur.png", x, y, w, h, 256, 256, false);
            case "freeze" -> this.texture(context, "textures/misc/powder_snow_outline.png", x, y, w, h, 256, 256, false);
            case "nausea" -> this.texture(context, "textures/block/nether_portal.png", x, y, w, h, 16, 16, false);
            case "totem" ->
            {
                int size = Math.max(16, Math.min(w, h) - 8);
                float scale = size / 16F;

                context.getMatrices().push();
                context.getMatrices().translate(x + (w - size) / 2F, y + (h - size) / 2F, 0F);
                context.getMatrices().scale(scale, scale, 1F);
                FormUtilsClient.getProvider().setUI(true);
                context.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), 0, 0);
                FormUtilsClient.getProvider().setUI(false);
                context.getMatrices().pop();
            }
            default -> {}
        }
    }

    /** A texture (or the first frame of an animated one) stretched over the preview */
    private void texture(DrawContext context, String path, int x, int y, int w, int h, int tw, int th, boolean darken)
    {
        int frames = path.contains("nether_portal") ? 32 : 1;

        context.draw();
        RenderSystem.enableBlend();

        if (darken)
        {
            RenderSystem.blendFunc(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR);
        }
        else
        {
            RenderSystem.defaultBlendFunc();
        }

        context.getMatrices().push();
        context.getMatrices().translate(0F, 0F, 1F);
        context.drawTexture(Ids.of("minecraft", path), x, y, w, h, 0F, 0F, tw, th, tw, th * frames);
        context.getMatrices().pop();
        context.draw();
        RenderSystem.defaultBlendFunc();
    }
}
