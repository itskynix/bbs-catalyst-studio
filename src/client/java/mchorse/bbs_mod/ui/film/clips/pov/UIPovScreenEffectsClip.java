package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovScreenEffectsClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The visual effects clip's panel: the effects in drawing order, added from a grid of them with
 * their pictures (under the three kinds: environment and vision, hazards, status and pop-ups), and the
 * dope sheet of the picked effect, showing only the channels its kind is driven by.
 */
public class UIPovScreenEffectsClip extends UIPovClip<PovScreenEffectsClip>
{
    public UIPovEntryList<PovScreenEffectsClip.Effect> list;
    /** What the picked totem pops up; shown only for a totem */
    public UIItemStack item;
    private UIElement header;

    public UIPovScreenEffectsClip(PovScreenEffectsClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.list = new UIPovEntryList<>((entry) -> key("screen.effects." + entry.type.get()).get(), (l) -> this.pick());
        this.list.background();
        this.list.h(16 * 5);
        this.list.context((menu) -> menu.action(Icons.ADD, key("screen.add"), this::openPicker));

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.openPicker());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.withPicked((entry) -> this.clip.effects.remove(entry)));
        UIIcon up = new UIIcon(Icons.MOVE_UP, (b) -> this.withPicked((entry) -> this.clip.effects.move(entry, -1)));
        UIIcon down = new UIIcon(Icons.MOVE_DOWN, (b) -> this.withPicked((entry) -> this.clip.effects.move(entry, 1)));

        add.tooltip(key("screen.add"));
        remove.tooltip(key("screen.remove"));

        this.header = UI.row(add, remove, up, down);

        this.item = new UIItemStack((stack) ->
        {
            PovScreenEffectsClip.Effect entry = this.list.getCurrentFirst();

            if (entry != null)
            {
                entry.item.set(stack == null ? ItemStack.EMPTY : stack.copy());
            }
        });
        this.item.tooltip(key("screen.totem_item"));
    }

    /** The grid of the effects with their pictures, under the three kinds */
    private void openPicker()
    {
        UIOverlay.addOverlay(this.getContext(), new UIPovVisualEffectPicker((type) ->
        {
            PovScreenEffectsClip.Effect entry = this.clip.effects.add(type);

            this.list.setList(this.clip.effects.getAllTyped());
            this.list.setCurrentScroll(entry);
            this.pick();
        }), 0.6F, 0.7F);
    }

    private void withPicked(Consumer<PovScreenEffectsClip.Effect> action)
    {
        PovScreenEffectsClip.Effect entry = this.list.getCurrentFirst();

        if (entry != null)
        {
            action.accept(entry);
            this.list.setList(this.clip.effects.getAllTyped());

            if (this.clip.effects.getAllTyped().contains(entry))
            {
                this.list.setCurrentScroll(entry);
            }
            else
            {
                this.list.deselect();
            }

            this.pick();
        }
    }

    private void pick()
    {
        PovScreenEffectsClip.Effect entry = this.list.getCurrentFirst();
        boolean totem = entry != null && entry.type.get().equals("totem");

        this.editKeyframes.setEnabled(entry != null);
        this.item.setVisible(totem);

        if (totem)
        {
            /* Empty is the totem of undying: shown as it, so the slot says what will pop up */
            ItemStack stack = entry.item.get();

            this.item.setStack(stack == null || stack.isEmpty() ? new ItemStack(Items.TOTEM_OF_UNDYING) : stack);
        }

        this.updateSheets();

        if (this.panels.hasParent())
        {
            this.panels.resize();
        }
    }

    @Override
    protected List<KeyframeChannel<?>> getSheetChannels()
    {
        PovScreenEffectsClip.Effect entry = this.list == null ? null : this.list.getCurrentFirst();

        if (entry == null)
        {
            return Collections.emptyList();
        }

        List<KeyframeChannel<?>> channels = new ArrayList<>();

        for (String id : PovScreenEffectsClip.channelsFor(entry.type.get()))
        {
            if (entry.get(id) instanceof KeyframeChannel<?> channel)
            {
                channels.add(channel);
            }
        }

        return channels;
    }

    @Override
    public void fillData()
    {
        this.list.setList(this.clip.effects.getAllTyped());
        this.pick();

        super.fillData();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("screen.title"), this.header, this.list, this.item, this.editKeyframes));
    }
}
