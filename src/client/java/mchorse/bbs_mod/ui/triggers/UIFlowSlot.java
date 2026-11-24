package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A picker slot for a block, an item or a sound: a field with a slot square on its left that shows
 * the block or item (a speaker for sounds) and its name beside it. Clicked, it opens BBS's block or
 * item picker, or a searchable list of every sound, which plays each sound as it is picked. The value
 * is kept as the registry id ({@code minecraft:stone}), as the commands that use it take it. Right
 * clicked, an id can be typed in by hand, for modded ones the lists may not show.
 */
public class UIFlowSlot extends UIElement
{
    public static final String BLOCK = "block";
    public static final String ITEM = "item";
    public static final String SOUND = "sound";

    private final String kind;
    private final Consumer<String> setter;
    private String value;
    private boolean opened;

    public UIFlowSlot(String kind, String value, Consumer<String> setter)
    {
        this.kind = kind;
        this.value = value == null ? "" : value.trim();
        this.setter = setter;
        this.h(20);

        this.context((menu) -> menu.action(Icons.EDIT, FlowFields.key("combo_type"), () ->
        {
            UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(FlowFields.key("combo_type"), FlowFields.key("hint.type_" + this.kind), this::pick);

            prompt.text.setText(this.value);
            UIOverlay.addOverlay(this.getContext(), prompt);
        }));
    }

    public static boolean isSlot(String type)
    {
        return type.equals(BLOCK) || type.equals(ITEM) || type.equals(SOUND);
    }

    public String getValue()
    {
        return this.value;
    }

    public void pick(String value)
    {
        this.value = value == null ? "" : value.trim();
        this.setter.accept(this.value);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || context.mouseButton != 0)
        {
            return false;
        }

        this.opened = true;

        if (this.kind.equals(SOUND))
        {
            List<String> ids = new ArrayList<>();

            for (Identifier id : Registries.SOUND_EVENT.getIds())
            {
                ids.add(id.toString());
            }

            UIStringOverlayPanel panel = new UIStringOverlayPanel(FlowFields.key("pick_sound"), false, ids, (id) ->
            {
                this.pick(id);
                preview(id);
            });

            panel.set(this.value);
            panel.onClose((e) -> this.opened = false);
            UIOverlay.addOverlay(context, panel, 0.5F, 0.75F);
        }
        else
        {
            UIUnifiedPickOverlayPanel panel = this.kind.equals(BLOCK)
                ? UIUnifiedPickOverlayPanel.forBlock((state) -> this.pick(state == null || state.isAir() ? "" : Registries.BLOCK.getId(state.getBlock()).toString()), this.block().getDefaultState())
                : UIUnifiedPickOverlayPanel.forItem((stack) -> this.pick(stack == null || stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString()), this.stack());

            panel.onClose((e) -> this.opened = false);
            UIOverlay.addOverlay(context, panel, 0.5F, 0.75F);
        }

        UIUtils.playClick();

        return true;
    }

    /** Plays a sound to the player alone, so picking one lets it be heard. */
    public static void preview(String id)
    {
        Identifier identifier = Identifier.tryParse(id);

        if (identifier != null && Registries.SOUND_EVENT.containsId(identifier))
        {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(identifier), 1F));
        }
    }

    private Block block()
    {
        Identifier id = Identifier.tryParse(this.value.contains("[") ? this.value.substring(0, this.value.indexOf('[')) : this.value);

        return id == null || !Registries.BLOCK.containsId(id) ? Blocks.AIR : Registries.BLOCK.get(id);
    }

    /** What the slot shows: the item, or the block as an item; empty when there is none. */
    private ItemStack stack()
    {
        if (this.kind.equals(BLOCK))
        {
            return new ItemStack(this.block().asItem());
        }

        Identifier id = Identifier.tryParse(this.value);

        return id == null || !Registries.ITEM.containsId(id) ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(id));
    }

    /** The name of what is picked, in the game's language; the id when the game does not know it. */
    private String label()
    {
        if (this.value.isEmpty())
        {
            return FlowFields.key("slot_empty").get();
        }

        if (this.kind.equals(SOUND))
        {
            return this.value.startsWith("minecraft:") ? this.value.substring(10) : this.value;
        }

        if (this.kind.equals(BLOCK))
        {
            Block block = this.block();

            return block == Blocks.AIR && !this.value.equals("minecraft:air") ? this.value : block.getName().getString();
        }

        ItemStack stack = this.stack();

        return stack.isEmpty() || stack.isOf(Items.AIR) ? this.value : stack.getName().getString();
    }

    @Override
    public void render(UIContext context)
    {
        boolean hover = this.area.isInside(context);
        int x = this.area.x;
        int y = this.area.y;
        int ex = this.area.ex();
        int ey = this.area.ey();
        int slot = this.area.h;
        int edge = this.opened || hover ? FlowTheme.accent() : FlowTheme.border();

        FlowTheme.rounded(context.batcher, x, y, ex, ey, 3, edge, true, true);
        FlowTheme.rounded(context.batcher, x + 1, y + 1, ex - 1, ey - 1, 2, FlowTheme.field(), true, true);
        context.batcher.box(x + slot - 1, y + 1, x + slot, ey - 1, edge);

        if (this.kind.equals(SOUND))
        {
            context.batcher.icon(Icons.SOUND, FlowTheme.typeColor(SOUND), x + slot / 2, this.area.my(), 0.5F, 0.5F);
        }
        else
        {
            ItemStack stack = this.stack();

            if (!stack.isEmpty())
            {
                MatrixStack matrices = context.batcher.getContext().getMatrices();
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                matrices.push();
                consumers.setUI(true);
                context.batcher.getContext().drawItem(stack, x + (slot - 16) / 2, this.area.my() - 8);
                consumers.setUI(false);
                matrices.pop();
            }
            else
            {
                context.batcher.icon(this.kind.equals(BLOCK) ? Icons.BLOCK : Icons.LIST, FlowTheme.dim(), x + slot / 2, this.area.my(), 0.5F, 0.5F);
            }
        }

        String shown = context.batcher.getFont().limitToWidth(this.label(), this.area.w - slot - 8);

        context.batcher.text(shown, x + slot + 4, this.area.my() - 4, this.value.isEmpty() ? FlowTheme.dim() : FlowTheme.text());

        super.render(context);
    }
}
