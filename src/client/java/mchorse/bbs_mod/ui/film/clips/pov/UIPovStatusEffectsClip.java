package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovClip;
import mchorse.bbs_mod.film.pov.PovStatusEffectsClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

/**
 * The potion effects clip's panel: the effects in the order they are drawn, added from a grid of
 * the game's effects (under the beneficial, the harmful and the non-potion tabs), with
 * the picked one's ambient switch and the dope sheet of its time left and its fade.
 */
public class UIPovStatusEffectsClip extends UIPovClip<PovStatusEffectsClip>
{
    public UIPovEntryList<PovStatusEffectsClip.Effect> list;
    public UIToggle ambient;
    private UIElement entryFields;
    private UIElement panelsHeader;

    public UIPovStatusEffectsClip(PovStatusEffectsClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public static String name(String id)
    {
        Identifier identifier = Identifier.tryParse(id);
        StatusEffect effect = identifier == null ? null : Registries.STATUS_EFFECT.get(identifier);

        return effect == null ? id : Text.translatable(effect.getTranslationKey()).getString();
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.list = new UIPovEntryList<>((entry) -> name(entry.effect.get()), (l) -> this.pick(l.isEmpty() ? null : l.get(0)));
        this.list.background();
        this.list.h(16 * 5);
        this.list.context((menu) -> menu.action(Icons.ADD, key("status.add"), this::openPicker));

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.openPicker());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.withPicked((entry) -> this.clip.effects.remove(entry)));
        UIIcon up = new UIIcon(Icons.MOVE_UP, (b) -> this.withPicked((entry) -> this.clip.effects.move(entry, -1)));
        UIIcon down = new UIIcon(Icons.MOVE_DOWN, (b) -> this.withPicked((entry) -> this.clip.effects.move(entry, 1)));

        add.tooltip(key("status.add"));
        remove.tooltip(key("status.remove"));

        this.ambient = new UIToggle(key("status.ambient"), (b) ->
        {
            PovStatusEffectsClip.Effect entry = this.list.getCurrentFirst();

            if (entry != null)
            {
                entry.ambient.set(b.getValue());
            }
        });

        this.entryFields = UI.column(this.ambient, this.editKeyframes);
        this.panelsHeader = UI.row(add, remove, up, down);
    }

    /** The grid of the game's effects, under the beneficial, the harmful and the non-potion tabs */
    private void openPicker()
    {
        UIOverlay.addOverlay(this.getContext(), new UIPovEffectPicker((id) ->
        {
            PovStatusEffectsClip.Effect entry = this.clip.effects.add(id.toString());

            this.refreshList();
            this.list.setCurrentScroll(entry);
            this.pick(entry);
        }), 0.7F, 0.8F);
    }

    private void withPicked(java.util.function.Consumer<PovStatusEffectsClip.Effect> action)
    {
        PovStatusEffectsClip.Effect entry = this.list.getCurrentFirst();

        if (entry != null)
        {
            action.accept(entry);
            this.refreshList();
            this.list.setCurrentScroll(entry);
            this.pick(this.clip.effects.getAllTyped().contains(entry) ? entry : null);
        }
    }

    private void refreshList()
    {
        this.list.setList(this.clip.effects.getAllTyped());
    }

    private void pick(PovStatusEffectsClip.Effect entry)
    {
        this.entryFields.setVisible(entry != null);

        if (entry != null)
        {
            this.ambient.setValue(entry.ambient.get());
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
        PovStatusEffectsClip.Effect entry = this.list == null ? null : this.list.getCurrentFirst();

        return entry == null ? Collections.emptyList() : PovClip.channelsOf(entry);
    }

    @Override
    public void fillData()
    {
        this.refreshList();
        this.pick(this.list.getCurrentFirst());

        super.fillData();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("status.title"), this.panelsHeader, this.list, this.entryFields));
    }
}
