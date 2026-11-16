package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

/** A row that is there to be seen and cannot be taken: grey, with no pill under the mouse and nothing to run */
public class DisabledContextAction extends ContextAction
{
    public DisabledContextAction(Icon icon, IKey label)
    {
        super(icon, label, () -> {});
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        int color = Colors.setA(Colors.WHITE, 0.35F);

        context.batcher.icon(this.icon, color, x + 14, y + h / 2F, 0.5F, 0.5F);
        context.batcher.text(this.label.get(), x + 28, y + (h - font.getHeight()) / 2 + 1, color, false);
    }
}
