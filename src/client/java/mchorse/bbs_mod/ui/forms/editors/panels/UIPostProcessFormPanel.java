package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.camera.clips.fx.PostEffectStack;
import mchorse.bbs_mod.forms.forms.PostProcessForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIPostEffectPicker;
import mchorse.bbs_mod.ui.film.clips.pov.UIPovEntryList;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The post-process volume's panel: the box and how it blends in, then the effect stack - added from
 * the same library window as the post-process effects clip - with the picked layer's switch,
 * intensity, parameters and colour. The intensity and the parameters are the volume's slot
 * properties, so what is set here is what a replay keyframes.
 */
public class UIPostProcessFormPanel extends UIFormPanel<PostProcessForm>
{
    /* Stands in while no layer is picked, so a bound field always has something to read and write */
    private static final ValueFloat NOTHING = new ValueFloat("nothing", 0F);
    private static final ValueBoolean NOTHING_ON = new ValueBoolean("nothing", false);

    private final UIPovEntryList<PostEffectStack.Layer> list;
    private final UILabel empty;
    private final UIElement fields;
    private final UILabel[] paramLabels = new UILabel[PostEffectStack.PARAMS];
    private final UITrackpad[] params = new UITrackpad[PostEffectStack.PARAMS];
    private final UILabel colorLabel;
    private final UIColor color;

    public UIPostProcessFormPanel(UIForm editor)
    {
        super(editor);

        UITrackpad width = UIValues.trackpad(() -> this.form.width);
        width.limit(0.5D, 512D);
        UITrackpad length = UIValues.trackpad(() -> this.form.length);
        length.limit(0.5D, 512D);
        UITrackpad height = UIValues.trackpad(() -> this.form.height);
        height.limit(0.5D, 512D);

        UITrackpad blend = UIValues.trackpad(() -> this.form.blendRadius);
        blend.limit(0D, 64D).tooltip(UIKeys.FORMS_POST_VOLUME_BLEND_TOOLTIP);

        UITrackpad priority = UIValues.trackpad(() -> this.form.priority);
        priority.limit(-1000D, 1000D, true).tooltip(UIKeys.FORMS_POST_VOLUME_PRIORITY_TOOLTIP);

        UITrackpad intensity = UIValues.trackpad(() -> this.form.intensity);
        intensity.limit(0D, 1D).tooltip(UIKeys.FORMS_POST_VOLUME_INTENSITY_TOOLTIP);

        UIToggle unbound = UIValues.toggle(UIKeys.FORMS_POST_VOLUME_UNBOUND, () -> this.form.unbound);
        unbound.tooltip(UIKeys.FORMS_POST_VOLUME_UNBOUND_TOOLTIP);
        UIToggle guides = UIValues.toggle(UIKeys.FORMS_POST_VOLUME_GUIDES, () -> this.form.showGuides);
        guides.tooltip(UIKeys.FORMS_POST_VOLUME_GUIDES_TOOLTIP);

        /* The stack */
        this.list = new UIPovEntryList<>(this::layerLabel, (l) -> this.pick(l.isEmpty() ? null : l.get(0)));
        this.list.background();
        this.list.h(16 * 6);
        this.list.context((menu) -> menu.action(Icons.ADD, UIKeys.POST_FX_ADD, this::openPicker));

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.openPicker());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.withPicked((layer) -> this.form.stack.remove(layer)));
        UIIcon up = new UIIcon(Icons.MOVE_UP, (b) -> this.withPicked((layer) -> this.form.stack.move(layer, -1)));
        UIIcon down = new UIIcon(Icons.MOVE_DOWN, (b) -> this.withPicked((layer) -> this.form.stack.move(layer, 1)));

        add.tooltip(UIKeys.POST_FX_ADD);
        remove.tooltip(UIKeys.POST_FX_REMOVE);
        up.tooltip(UIKeys.POST_FX_MOVE_UP);
        down.tooltip(UIKeys.POST_FX_MOVE_DOWN);

        this.empty = UI.label(UIKeys.POST_FX_EMPTY).color(0x88ffffff);

        UIToggle enabled = UIValues.toggle(UIKeys.POST_FX_ENABLED, () ->
        {
            PostEffectStack.Layer layer = this.picked();

            return layer == null ? NOTHING_ON : layer.enabled;
        });

        UITrackpad layerIntensity = UIValues.trackpad(() ->
        {
            PostEffectStack.Layer layer = this.picked();

            return layer == null || layer.slot.get() < 0 ? NOTHING : this.form.slotIntensity[layer.slot.get()];
        });
        layerIntensity.limit(0D, 1D);

        UIElement column = UI.column(enabled, UI.label(UIKeys.POST_FX_INTENSITY), layerIntensity);

        for (int i = 0; i < PostEffectStack.PARAMS; i++)
        {
            int index = i;

            this.paramLabels[i] = UI.label(IKey.EMPTY);
            this.params[i] = UIValues.trackpad(() ->
            {
                PostEffectStack.Layer layer = this.picked();

                return layer == null || layer.slot.get() < 0 ? NOTHING : this.form.slotParams[layer.slot.get()][index];
            });

            column.add(this.paramLabels[i], this.params[i]);
        }

        this.colorLabel = UI.label(UIKeys.POST_FX_COLOR);
        this.color = new UIColor((c) ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                layer.color.set(c & Colors.RGB);
            }
        });
        this.color.valueBinding(() ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.color.setColor(layer.color.get());
            }
        });

        column.add(this.colorLabel, this.color);
        this.fields = column;

        this.options.add(
            UI.label(UIKeys.FORMS_POST_VOLUME_BOX),
            UI.row(width, length),
            UI.labelRow(UIKeys.VIDEO_SETTINGS_HEIGHT, height),
            UI.labelRow(UIKeys.FORMS_POST_VOLUME_BLEND, blend),
            unbound,
            UI.label(UIKeys.FORMS_POST_VOLUME_MIX).marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(UIKeys.FORMS_POST_VOLUME_INTENSITY, intensity),
            UI.labelRow(UIKeys.FORMS_POST_VOLUME_PRIORITY, priority),
            guides,
            UI.label(UIKeys.POST_FX_STACK).marginTop(UIConstants.SECTION_GAP),
            UI.row(add, remove, up, down),
            this.list,
            this.empty,
            this.fields
        );
    }

    private PostEffectStack.Layer picked()
    {
        return this.list == null ? null : this.list.getCurrentFirst();
    }

    private String layerLabel(PostEffectStack.Layer layer)
    {
        PostEffect effect = PostEffect.get(layer.effect.get());
        String name = effect == null ? layer.effect.get() : UIPostEffectPicker.effectName(effect).get();

        return layer.enabled.get() ? name : "- " + name;
    }

    private void openPicker()
    {
        if (this.form.stack.getAllTyped().size() >= PostProcessForm.SLOTS)
        {
            this.getContext().notifyError(UIKeys.FORMS_POST_VOLUME_FULL);

            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIPostEffectPicker((effect) ->
        {
            PostEffectStack.Layer layer = this.form.addEffect(effect);

            this.refresh();

            if (layer != null)
            {
                this.list.setCurrentScroll(layer);
                this.pick(layer);
            }
        }), 560, 380);
    }

    private void withPicked(Consumer<PostEffectStack.Layer> action)
    {
        PostEffectStack.Layer layer = this.picked();

        if (layer != null)
        {
            action.accept(layer);
            this.refresh();

            boolean kept = this.form.stack.getAllTyped().contains(layer);

            if (kept)
            {
                this.list.setCurrentScroll(layer);
            }

            this.pick(kept ? layer : null);
        }
    }

    private void refresh()
    {
        this.list.setList(this.form.stack.getAllTyped());
        this.empty.setVisible(this.form.stack.getAllTyped().isEmpty());
    }

    /** Shows only the parameters the picked layer's effect has, with their names and ranges */
    private void pick(PostEffectStack.Layer layer)
    {
        PostEffect effect = layer == null ? null : PostEffect.get(layer.effect.get());

        this.fields.setVisible(effect != null);

        if (effect != null)
        {
            for (int i = 0; i < PostEffectStack.PARAMS; i++)
            {
                boolean has = i < effect.params.length;

                this.paramLabels[i].setVisible(has);
                this.params[i].setVisible(has);

                if (has)
                {
                    PostEffect.Param param = effect.params[i];

                    this.paramLabels[i].label = UIPostEffectPicker.paramName(param.name());
                    this.params[i].limit(param.min(), param.max());
                }
            }

            this.colorLabel.setVisible(effect.hasColor());
            this.color.setVisible(effect.hasColor());
        }

        if (this.options.hasParent())
        {
            this.options.resize();
        }
    }

    @Override
    public void startEdit(PostProcessForm form)
    {
        super.startEdit(form);

        this.refresh();
        this.pick(this.picked());
    }
}
