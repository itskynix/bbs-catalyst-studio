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
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A picker slot for a block, an item, a sound, a particle or an entity type: a field with a slot
 * square on its left that shows the block or item (a speaker for sounds, a spark for particles, the
 * spawn egg for an entity) and its name beside it. Clicked, it opens BBS's block or item picker, or a
 * searchable list of every sound (each played as it is picked), particle or entity that can be
 * summoned. The value is kept as the registry id ({@code minecraft:stone}), as the commands that use
 * it take it; a particle that needs more (dust, block, item) gets its arguments after the id. Right
 * clicked, an id can be typed in by hand, for modded ones the lists may not show.
 */
public class UIFlowSlot extends UIElement
{
    public static final String BLOCK = "block";
    public static final String ITEM = "item";
    public static final String SOUND = "sound";
    public static final String PARTICLE = "particle";
    public static final String ENTITY_TYPE = "entity_type";

    /**
     * What the particles that need more than their id are given when picked, so the particle command
     * takes them as they are: a colour and size, a block or an item. Others (vibration) are typed.
     */
    private static final Map<String, String> PARTICLE_ARGUMENTS = Map.of(
        "minecraft:dust", "1 0 0 1",
        "minecraft:dust_color_transition", "1 0 0 1 0 0 1",
        "minecraft:block", "minecraft:stone",
        "minecraft:block_marker", "minecraft:barrier",
        "minecraft:falling_dust", "minecraft:sand",
        "minecraft:item", "minecraft:diamond",
        "minecraft:sculk_charge", "0",
        "minecraft:shriek", "0"
    );

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
        return type.equals(BLOCK) || type.equals(ITEM) || type.equals(SOUND) || type.equals(PARTICLE) || type.equals(ENTITY_TYPE);
    }

    /** Whether the slot picks from a list of ids (sounds, particles, entity types) rather than BBS's block or item picker. */
    private boolean listed()
    {
        return this.kind.equals(SOUND) || this.kind.equals(PARTICLE) || this.kind.equals(ENTITY_TYPE);
    }

    /** The value's id alone, without the arguments a particle may carry after it. */
    private String id()
    {
        int space = this.value.indexOf(' ');

        return space < 0 ? this.value : this.value.substring(0, space);
    }

    /** Every id the list offers: each sound, each particle the command can show, each entity that can be summoned. */
    private List<String> ids()
    {
        List<String> ids = new ArrayList<>();

        if (this.kind.equals(SOUND))
        {
            for (Identifier id : Registries.SOUND_EVENT.getIds())
            {
                ids.add(id.toString());
            }
        }
        else if (this.kind.equals(PARTICLE))
        {
            for (Identifier id : Registries.PARTICLE_TYPE.getIds())
            {
                String text = id.toString();

                if (Registries.PARTICLE_TYPE.get(id) instanceof DefaultParticleType || PARTICLE_ARGUMENTS.containsKey(text))
                {
                    ids.add(text);
                }
            }
        }
        else
        {
            for (Identifier id : Registries.ENTITY_TYPE.getIds())
            {
                if (Registries.ENTITY_TYPE.get(id).isSummonable())
                {
                    ids.add(id.toString());
                }
            }
        }

        ids.sort(String::compareTo);

        return ids;
    }

    /** A picked id as the field keeps it: a particle that needs arguments gets the ones it starts with. */
    private String picked(String id)
    {
        if (this.kind.equals(PARTICLE) && PARTICLE_ARGUMENTS.containsKey(id))
        {
            /* Picking the same particle again keeps the arguments it was given */
            return id.equals(this.id()) && this.value.contains(" ") ? this.value : id + " " + PARTICLE_ARGUMENTS.get(id);
        }

        return id;
    }

    private EntityType<?> entityType()
    {
        Identifier id = Identifier.tryParse(this.id());

        return id == null || !Registries.ENTITY_TYPE.containsId(id) ? null : Registries.ENTITY_TYPE.get(id);
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

        if (this.listed())
        {
            UIStringOverlayPanel panel = new UIStringOverlayPanel(FlowFields.key("pick_" + this.kind), false, this.ids(), (id) ->
            {
                this.pick(this.picked(id));

                if (this.kind.equals(SOUND))
                {
                    preview(id);
                }
            });

            panel.set(this.id());
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

        if (this.kind.equals(ENTITY_TYPE))
        {
            /* An entity shows as its spawn egg; one without an egg shows as a figure */
            EntityType<?> type = this.entityType();
            SpawnEggItem egg = type == null ? null : SpawnEggItem.forEntity(type);

            return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
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

        if (this.kind.equals(SOUND) || this.kind.equals(PARTICLE))
        {
            return this.value.startsWith("minecraft:") ? this.value.substring(10) : this.value;
        }

        if (this.kind.equals(ENTITY_TYPE))
        {
            EntityType<?> type = this.entityType();

            return type == null ? this.value : type.getName().getString();
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

        if (this.kind.equals(SOUND) || this.kind.equals(PARTICLE))
        {
            context.batcher.icon(this.kind.equals(SOUND) ? Icons.SOUND : Icons.PARTICLE, FlowTheme.typeColor(this.kind), x + slot / 2, this.area.my(), 0.5F, 0.5F);
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
                context.batcher.icon(this.kind.equals(BLOCK) ? Icons.BLOCK : this.kind.equals(ENTITY_TYPE) ? Icons.USER : Icons.LIST, FlowTheme.dim(), x + slot / 2, this.area.my(), 0.5F, 0.5F);
            }
        }

        String shown = context.batcher.getFont().limitToWidth(this.label(), this.area.w - slot - 8);

        context.batcher.text(shown, x + slot + 4, this.area.my() - 4, this.value.isEmpty() ? FlowTheme.dim() : FlowTheme.text());

        super.render(context);
    }
}
