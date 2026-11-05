package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovSlotSet;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * A slot group keyframe: its slots as a grid of cells, the game's way round (nine to a row, a
 * crafting grid's result above its grid). A click on a cell picks its item; a right click empties it.
 */
public class UIPovSlotSetKeyframeFactory extends UIKeyframeFactory<PovSlotSet>
{
    private static final int CELL = 20;

    private final UIElement grid = new UIElement();

    public UIPovSlotSetKeyframeFactory(Keyframe<PovSlotSet> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        PovSlotSet set = keyframe.getValue() == null ? PovSlotSet.EMPTY : keyframe.getValue();
        int size = set.size();
        boolean crafting = size == 5 || size == 10;
        int columns = crafting ? (size == 5 ? 2 : 3) : Math.max(1, Math.min(9, size));
        int rows = 0;

        for (int i = 0; i < size; i++)
        {
            int x;
            int y;

            if (crafting)
            {
                /* The result on a row of its own, the grid under it */
                x = i == 0 ? 0 : ((i - 1) % columns) * CELL;
                y = i == 0 ? 0 : (1 + (i - 1) / columns) * CELL + 4;
            }
            else
            {
                x = (i % columns) * CELL;
                y = (i / columns) * CELL;
            }

            Cell cell = new Cell(i);

            cell.relative(this.grid).xy(x, y).wh(CELL, CELL);
            cell.tooltip(crafting && i == 0 ? L10n.lang("bbs.ui.film.pov_clips.slot_result") : IKey.constant(String.valueOf(i + 1)));
            this.grid.add(cell);
            rows = Math.max(rows, y + CELL);
        }

        this.grid.h(Math.max(rows, CELL));
        this.scroll.add(this.grid);
    }

    private PovSlotSet current()
    {
        PovSlotSet set = this.keyframe.getValue();

        return set == null ? PovSlotSet.EMPTY : set;
    }

    private void setSlot(int slot, ItemStack stack)
    {
        this.setValue(this.current().with(slot, stack == null ? ItemStack.EMPTY : stack.copy()));
    }

    private class Cell extends UIElement
    {
        private final int slot;

        Cell(int slot)
        {
            this.slot = slot;

            this.context((menu) -> menu.action(Icons.CLOSE, UIKeys.ITEM_STACK_CONTEXT_RESET, () -> setSlot(this.slot, ItemStack.EMPTY)));
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forItem((stack) -> setSlot(this.slot, stack), current().get(this.slot));

                UIOverlay.addOverlay(this.getContext(), panel, 0.5F, 0.75F);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            ItemStack stack = current().get(this.slot);
            boolean hover = this.area.isInside(context);

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), hover ? Colors.WHITE : Colors.LIGHTER_GRAY);
            context.batcher.box(this.area.x + 1, this.area.y + 1, this.area.ex() - 1, this.area.ey() - 1, Colors.A50);

            if (!stack.isEmpty())
            {
                FormUtilsClient.getProvider().setUI(true);
                context.batcher.getContext().drawItem(stack, this.area.x + 2, this.area.y + 2);
                context.batcher.getContext().drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, this.area.x + 2, this.area.y + 2);
                FormUtilsClient.getProvider().setUI(false);
            }

            super.render(context);
        }
    }
}
