package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.film.pov.PovToastClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.minecraft.advancement.AdvancementFrame;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * The toast clip's panel: the preset (advancement made, goal reached, challenge complete, or a
 * notice), the title and description written over the preset's, the icon as an item or a texture,
 * and the dope sheet of the slide, for a toast that comes and goes other than at the clip's ends.
 * Any of the game's advancements can be picked from a grid of them and loaded into the clip at once.
 */
public class UIPovToastClip extends UIPovClip<PovToastClip>
{
    public UICirculate preset;
    public UITextbox titleText;
    public UITextbox description;
    public UIItemStack item;
    public UIButton texture;
    public UIButton clearTexture;
    public UIButton advancements;

    public UIPovToastClip(PovToastClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.preset = new UICirculate((b) -> this.editor.editMultiple(this.clip.preset, (v) -> v.set(b.getValue())));
        this.preset.addLabel(key("toast.presets.task"));
        this.preset.addLabel(key("toast.presets.goal"));
        this.preset.addLabel(key("toast.presets.challenge"));
        this.preset.addLabel(key("toast.presets.notice"));
        this.bind(this.preset, () -> this.preset.setValue(this.clip.preset.get()));

        this.titleText = this.textbox(200, this.clip.titleText);
        this.titleText.textbox.setPlaceholder(key("toast.toast_title"));
        this.description = this.textbox(200, this.clip.description);
        this.description.textbox.setPlaceholder(key("toast.description"));
        this.item = this.itemStack(this.clip.item);
        this.item.tooltip(key("toast.item"));

        this.texture = new UIButton(key("toast.texture"), (b) ->
        {
            UITexturePicker.open(this.getContext(), this.clip.texture.get(), (link) -> this.editor.editMultiple(this.clip.texture, (v) -> v.set(link)));
        });
        this.clearTexture = new UIButton(key("toast.clear_texture"), (b) -> this.editor.editMultiple(this.clip.texture, (v) -> v.set(null)));

        /* Any of the game's advancements, as one edit: its kind, its name, its icon */
        this.advancements = new UIButton(key("toast.pick_advancement"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIPovToastPicker((entry) ->
            {
                AdvancementFrame frame = entry.display().getFrame();

                BaseValue.edit(this.clip, (clip) ->
                {
                    clip.preset.set(frame == AdvancementFrame.GOAL ? PovToastClip.GOAL : frame == AdvancementFrame.CHALLENGE ? PovToastClip.CHALLENGE : PovToastClip.TASK);
                    clip.titleText.set("");
                    clip.description.set(entry.display().getTitle().getString());
                    clip.item.set(entry.display().getIcon().copy());
                    clip.texture.set(null);
                });

                this.fillData();
            }), 0.7F, 0.8F);
        });
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("toast.title"), this.advancements, this.preset, this.titleText, this.description,
            this.item, UI.row(this.texture, this.clearTexture), this.editKeyframes));
    }
}
