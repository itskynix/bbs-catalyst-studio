package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Grid card component for a morphable form in the Morphing Studio Catalog.
 * Features smooth hover lift (-3px lerp), glow aura, 3D preview thumbnail,
 * single-click selection, double-click direct morph, and right-click context menu.
 */
public class UIMorphFormCard extends UIClickable<UIMorphFormCard>
{
    private final UIMorphStudioScreen studio;
    public final Form form;

    private final String title;
    private final String code;
    private final String type;

    private float hoverY = 0F;
    private float glowAlpha = 0F;
    private long lastClickTime = 0L;

    public UIMorphFormCard(UIMorphStudioScreen studio, Form form)
    {
        super((c) -> studio.selectForm(form));

        this.studio = studio;
        this.form = form;

        this.title = UIMorphNaming.title(form);
        this.code = UIMorphNaming.code(form);
        this.type = UIMorphCategory.getFormTypeShort(form);

        this.tooltip(IKey.raw(this.title));

        this.context((menu) ->
        {
            menu.action(Icons.MORPH, UIKeys.MORPH_STUDIO_MORPH_BUTTON, () -> studio.morphInto(form));
            menu.action(Icons.EDIT, UIKeys.MORPH_STUDIO_EDIT, () -> studio.openEditor(form));

            boolean isFav = studio.isFavorite(form);
            menu.action(Icons.FAVORITE, isFav ? UIKeys.MORPH_STUDIO_UNFAVORITE : UIKeys.MORPH_STUDIO_FAVORITE, () -> studio.toggleFavorite(form));
        });
    }

    @Override
    protected UIMorphFormCard get()
    {
        return this;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            long now = System.currentTimeMillis();

            if (now - this.lastClickTime < 320)
            {
                UIUtils.playClick();
                this.studio.morphInto(this.form);
                this.lastClickTime = 0L;

                return true;
            }

            this.lastClickTime = now;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        Area a = this.area;
        boolean isSelected = this.studio.getSelectedForm() == this.form;
        boolean isHovered = this.hover;
        int primary = BBSSettings.primaryColor.get();

        /* Hover Lift (Mikro-Animasyon): 0 -> -3px yumuşak lerp yükselme */
        float targetY = isHovered ? -3F : 0F;
        this.hoverY = Lerps.lerp(this.hoverY, targetY, 0.25F);

        float targetGlow = isHovered ? 0.35F : (isSelected ? 0.28F : 0F);
        this.glowAlpha = Lerps.lerp(this.glowAlpha, targetGlow, 0.20F);

        int yOff = (int) this.hoverY;
        int x = a.x;
        int y = a.y + yOff;
        int ex = a.ex();
        int ey = a.ey() + yOff;

        int bg = isSelected ? 0xd8182232 : (isHovered ? 0xb0131924 : 0x750c1018);
        int border = isSelected ? (Colors.A100 | primary) : (isHovered ? Colors.setA(primary, 0.8F) : 0x22ffffff);

        if (this.glowAlpha > 0.02F)
        {
            context.batcher.dropShadow(x, y, ex, ey, 6, Colors.setA(primary, this.glowAlpha), 0);
        }
        else
        {
            context.batcher.dropShadow(x, y, ex, ey, 4, Colors.A50, 0);
        }

        context.batcher.box(x, y, ex, ey, bg);
        context.batcher.outline(x, y, ex, ey, border);

        if (isSelected || isHovered)
        {
            /* A second ring inside, so the state reads at a glance */
            context.batcher.outline(x + 1, y + 1, ex - 1, ey - 1, Colors.setA(isSelected ? primary : Colors.WHITE, isSelected ? 0.55F : 0.18F));
        }

        /*
         * The text under the picture takes what the card's size allows: the name, the technical name
         * and the kind on a large card, the name and the technical name on a middling one, the name
         * alone on a small one. The picture gets the rest.
         */
        int block = a.h >= 100 ? 36 : (a.h >= 80 ? 24 : 14);
        boolean showCode = block >= 24;
        boolean showType = block >= 36;

        /* 3D Preview Kutusu */
        int prevPad = 4;
        int prevX = x + prevPad;
        int prevY = y + prevPad;
        int prevEx = ex - prevPad;
        int prevEy = ey - block;

        context.batcher.box(prevX, prevY, prevEx, prevEy, 0x55000000);
        context.batcher.outline(prevX, prevY, prevEx, prevEy, isHovered ? 0x30ffffff : 0x14ffffff);

        /* Big models must not spill over the neighbouring cards: the picture is cut to its box */
        context.batcher.clipBox(prevX, prevY, prevEx, prevEy, context);
        FormUtilsClient.renderPreview(this.form, context, prevX, prevY, prevEx, prevEy);
        context.batcher.unclip(context);

        /* Favori Yıldız Rozeti */
        if (this.studio.isFavorite(this.form))
        {
            context.batcher.icon(Icons.FAVORITE, ex - 18, y + 6, 0xffffd54f);
        }

        /* Title, then the technical name smaller and dimmer, then the kind */
        FontRenderer font = context.batcher.getFont();
        int titleY = ey - block + 2;
        String name = font.limitToWidth(this.title, "...", a.w - 8);
        context.batcher.text(name, x + 4, titleY, Colors.WHITE, false);

        if (showCode)
        {
            MatrixStack matrices = context.batcher.getContext().getMatrices();
            float small = 0.8F;

            matrices.push();
            matrices.scale(small, small, 1F);
            context.batcher.text(font.limitToWidth(this.code, "...", (int) ((a.w - 8) / small)), (int) ((x + 4) / small), (int) ((titleY + 12) / small), Colors.setA(Colors.WHITE, 0.38F), false);
            matrices.pop();
        }

        if (showType)
        {
            context.batcher.text(font.limitToWidth(this.type, "...", a.w - 8), x + 4, titleY + 22, Colors.setA(Colors.WHITE, 0.55F), false);
        }
    }
}
