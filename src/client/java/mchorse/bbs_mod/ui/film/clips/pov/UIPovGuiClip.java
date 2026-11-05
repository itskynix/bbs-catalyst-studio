package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovGuiClip;
import mchorse.bbs_mod.film.pov.PovSlotSet;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The interface clip's panel: the screen, picked from a grid of every screen (GUI Settings), and
 * the dope sheet of what that screen keys (GUI Keyframes): the three rows every screen has, then
 * only the screen's own (a chest its slots, a beacon its powers, the inventory its armor, crafting
 * grid and recipe book), so the timeline holds nothing the screen does not show.
 */
public class UIPovGuiClip extends UIPovClip<PovGuiClip>
{
    public UIButton screen;

    public UIPovGuiClip(PovGuiClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.screen = new UIButton(IKey.EMPTY, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIPovGuiPicker(this.clip.screen.get(), this::pick), 0.7F, 0.8F);
        });
        this.bind(this.screen, () -> this.screen.label = IKey.constant(key("gui.screen").get() + ": " + UIPovGuiPicker.name(this.clip.screen.get()).get()));
    }

    private void pick(String type)
    {
        this.editor.editMultiple(this.clip.screen, (v) -> v.set(type));

        /* A death screen starts with its buttons greyed out for the game's second */
        if (type.equals("death") && this.clip.buttonsActive.isEmpty())
        {
            BaseValue.edit(this.clip, (clip) -> clip.seedDeathButtons());
        }

        this.updateSheets();
    }

    @Override
    protected List<KeyframeChannel<?>> getSheetChannels()
    {
        List<KeyframeChannel<?>> channels = new ArrayList<>();

        for (String id : PovGuiClip.channelsFor(this.clip.screen.get()))
        {
            if (this.clip.get(id) instanceof KeyframeChannel<?> channel)
            {
                channels.add(channel);
            }
        }

        return channels;
    }

    /** A chest's slot group goes by the chest's name for it */
    @Override
    protected IKey getChannelTitle(String id)
    {
        if (id.equals("container_slots") && PovGuiClip.STORAGE.contains(this.clip.screen.get()) && !this.clip.screen.get().equals("hopper") && !this.clip.screen.get().equals("dispenser"))
        {
            return key("channels.chest_slots");
        }

        return super.getChannelTitle(id);
    }

    @Override
    protected Supplier<Object> getChannelSeed(KeyframeChannel<?> channel)
    {
        String id = channel.getId();

        if (channel.getFactory() == KeyframeFactories.POV_SLOTS)
        {
            int size = this.clip.groupSize(id);

            return () -> PovSlotSet.ofSize(size);
        }

        return switch (id)
        {
            case "gui_opacity", "darkness_opacity", "red_background", "sleep_darkness" -> () -> 1D;
            case "leave_bed", "buttons_active" -> () -> true;
            case "selected_recipe" -> () -> -1;
            default -> super.getChannelSeed(channel);
        };
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("gui.settings"), this.screen));
        this.panels.add(this.section(key("gui.keyframes"), this.editKeyframes));
    }
}
