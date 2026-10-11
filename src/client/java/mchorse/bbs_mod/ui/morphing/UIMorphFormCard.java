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

/**
 * Grid card component for a morphable form in the Morphing Studio Catalog.
 * Features smooth hover lift (-3px lerp), glow aura, 3D preview thumbnail,
 * single-click selection, double-click direct morph, and right-click context menu.
 */
public class UIMorphFormCard extends UIClickable<UIMorphFormCard>
{
    private final UIMorphStudioScreen studio;
    public final Form form;

    private float hoverY = 0F;
    private float glowAlpha = 0F;
    private long lastClickTime = 0L;

    public UIMorphFormCard(UIMorphStudioScreen studio, Form form)
    {
        super((c) -> studio.selectForm(form));

        this.studio = studio;
        this.form = form;

        String name = form.getDisplayName();
        String type = UIMorphCategory.getFormTypeLabel(form);

        this.tooltip(IKey.raw(name + " (" + type + ")"));

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
        int border = isSelected ? (Colors.A100 | primary) : (isHovered ? 0x60ffffff : 0x22ffffff);

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

        /* 3D Preview Kutusu */
        int prevPad = 4;
        int prevX = x + prevPad;
        int prevY = y + prevPad;
        int prevEx = ex - prevPad;
        int prevEy = y + 68;

        context.batcher.box(prevX, prevY, prevEx, prevEy, 0x55000000);
        context.batcher.outline(prevX, prevY, prevEx, prevEy, isHovered ? 0x30ffffff : 0x14ffffff);

        FormUtilsClient.renderPreview(this.form, context, prevX, prevY, prevEx, prevEy);

        /* Favori Yıldız Rozeti */
        if (this.studio.isFavorite(this.form))
        {
            context.batcher.icon(Icons.FAVORITE, ex - 18, y + 6, 0xffffd54f);
        }

        /* Form Adı ve Kategori Altyazısı */
        FontRenderer font = context.batcher.getFont();
        String name = font.limitToWidth(this.form.getDisplayName(), a.w - 8);
        context.batcher.text(name, x + 4, y + 72, Colors.WHITE, false);

        String typeStr = font.limitToWidth(UIMorphCategory.getFormTypeShort(this.form), a.w - 8);
        context.batcher.text(typeStr, x + 4, y + 86, Colors.setA(Colors.WHITE, 0.50F), false);
    }
}
