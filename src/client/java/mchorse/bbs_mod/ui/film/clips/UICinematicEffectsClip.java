package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.camera.clips.fx.CinematicEffectsClip;
import mchorse.bbs_mod.camera.clips.fx.FxClip;
import mchorse.bbs_mod.camera.clips.fx.PostEffectStack;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.pov.UIPovEntryList;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The post-process effects clip's panel: the clip's own film and lens channels as before, and under
 * them the effect stack (added from the library window, switched on and off, moved up and down) with
 * the picked layer's intensity, parameters and colour. The dope sheet lists the clip's channels and,
 * for every effect in the stack, its channels under a heading of their own; an effect that is not in
 * the stack has no rows.
 */
public class UICinematicEffectsClip extends UIFxClip<CinematicEffectsClip>
{
    private static final int[] CATEGORY_COLORS = {0xf5a623, 0xe0457b, 0x9b59b6, 0x3d7fd0, 0x2eb67d, 0xd82253};

    public UIPovEntryList<PostEffectStack.Layer> list;
    public UIToggle enabled;
    public UITrackpad intensity;
    /* Filled in registerUI, not here: the base constructor calls registerUI before this class's
     * field initialisers have run, so an array made here would still be null in there */
    public UILabel[] paramLabels;
    public UITrackpad[] params;
    public UIColor color;
    public UILabel colorLabel;
    public UILabel empty;

    private UIElement header;
    private UIElement fields;

    public UICinematicEffectsClip(CinematicEffectsClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    private PostEffectStack.Layer picked()
    {
        return this.list == null ? null : this.list.getCurrentFirst();
    }

    private static PostEffect effectOf(PostEffectStack.Layer layer)
    {
        return layer == null ? null : PostEffect.get(layer.effect.get());
    }

    private static String layerLabel(PostEffectStack.Layer layer)
    {
        PostEffect effect = effectOf(layer);
        String name = effect == null ? layer.effect.get() : UIPostEffectPicker.effectName(effect).get();

        return layer.enabled.get() ? name : "- " + name;
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.paramLabels = new UILabel[PostEffectStack.PARAMS];
        this.params = new UITrackpad[PostEffectStack.PARAMS];

        this.list = new UIPovEntryList<>(UICinematicEffectsClip::layerLabel, (l) -> this.pick(l.isEmpty() ? null : l.get(0)));
        this.list.background();
        this.list.h(16 * 6);
        this.list.context((menu) -> menu.action(Icons.ADD, UIKeys.POST_FX_ADD, this::openPicker));

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.openPicker());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.withPicked((layer) -> this.clip.stack.remove(layer)));
        UIIcon up = new UIIcon(Icons.MOVE_UP, (b) -> this.withPicked((layer) -> this.clip.stack.move(layer, -1)));
        UIIcon down = new UIIcon(Icons.MOVE_DOWN, (b) -> this.withPicked((layer) -> this.clip.stack.move(layer, 1)));

        add.tooltip(UIKeys.POST_FX_ADD);
        remove.tooltip(UIKeys.POST_FX_REMOVE);
        up.tooltip(UIKeys.POST_FX_MOVE_UP);
        down.tooltip(UIKeys.POST_FX_MOVE_DOWN);

        this.header = UI.row(add, remove, up, down);

        this.empty = UI.label(UIKeys.POST_FX_EMPTY).color(0x88ffffff);

        this.enabled = this.bind(new UIToggle(UIKeys.POST_FX_ENABLED, (b) ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.editor.editMultiple(layer.enabled, (v) -> v.set(b.getValue()));
            }
        }), () ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.enabled.setValue(layer.enabled.get());
            }
        });

        this.intensity = this.bind(new UITrackpad((v) ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.editor.editMultiple(layer.intensity, (f) -> f.set(v.floatValue()));
            }
        }), () ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.intensity.setValue(layer.intensity.get());
            }
        });
        this.intensity.limit(0D, 1D);

        UIElement column = UI.column(UI.label(UIKeys.POST_FX_INTENSITY), this.intensity);

        for (int i = 0; i < PostEffectStack.PARAMS; i++)
        {
            int index = i;

            this.paramLabels[i] = UI.label(IKey.EMPTY);
            this.params[i] = this.bind(new UITrackpad((v) ->
            {
                PostEffectStack.Layer layer = this.picked();

                if (layer != null)
                {
                    this.editor.editMultiple(layer.values[index], (f) -> f.set(v.floatValue()));
                }
            }), () ->
            {
                PostEffectStack.Layer layer = this.picked();

                if (layer != null)
                {
                    this.params[index].setValue(layer.values[index].get());
                }
            });

            column.add(this.paramLabels[i], this.params[i]);
        }

        this.colorLabel = UI.label(UIKeys.POST_FX_COLOR);
        this.color = this.bind(new UIColor((c) ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.editor.editMultiple(layer.color, (v) -> v.set(c));
            }
        }), () ->
        {
            PostEffectStack.Layer layer = this.picked();

            if (layer != null)
            {
                this.color.setColor(layer.color.get());
            }
        });

        column.add(this.colorLabel, this.color);

        this.fields = UI.column(this.enabled, column);
    }

    @Override
    protected void addFxPanels()
    {
        this.panels.add(this.section(UIKeys.POST_FX_STACK, this.header, this.list, this.empty));
        this.panels.add(this.section(UIKeys.POST_FX_PARAMETERS, this.fields));
    }

    private void openPicker()
    {
        UIOverlay.addOverlay(this.getContext(), new UIPostEffectPicker((effect) ->
        {
            PostEffectStack.Layer layer = this.clip.stack.add(effect);

            this.refreshList();
            this.list.setCurrentScroll(layer);
            this.pick(layer);
            this.rebuildSheets();
        }), 560, 380);
    }

    private void withPicked(Consumer<PostEffectStack.Layer> action)
    {
        PostEffectStack.Layer layer = this.picked();

        if (layer != null)
        {
            action.accept(layer);
            this.refreshList();

            boolean kept = this.clip.stack.getAllTyped().contains(layer);

            if (kept)
            {
                this.list.setCurrentScroll(layer);
            }

            this.pick(kept ? layer : null);
            this.rebuildSheets();
        }
    }

    private void refreshList()
    {
        this.list.setList(this.clip.stack.getAllTyped());
        this.empty.setVisible(this.clip.stack.getAllTyped().isEmpty());
    }

    /** Shows the picked layer's fields: only the parameters its effect has, with their names and ranges */
    private void pick(PostEffectStack.Layer layer)
    {
        PostEffect effect = effectOf(layer);

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

        if (this.panels.hasParent())
        {
            this.panels.resize();
        }
    }

    /**
     * The dope sheet: the clip's own channels, then the intensity and the parameters of every layer
     * in the stack under a heading that names the effect and its category. Effects that are not in
     * the stack have no rows at all.
     */
    private void rebuildSheets()
    {
        this.keyframes.view.removeAllSheets();

        /* The clip's own film and lens channels, under a heading that folds like the layers' */
        UIKeyframeSheet.Section cinematic = new UIKeyframeSheet.Section("post_fx_cinematic", UIKeys.POST_FX_CINEMATIC, Icons.CAMERA, 0xf5a623);
        int index = 0;

        for (FxClip.Channel fx : this.clip.fxChannels)
        {
            UIKeyframeSheet sheet = new UIKeyframeSheet(fx.channel.getId(), L10n.lang("bbs.ui.camera.fx.channel." + fx.channel.getId()), UIKeyframeEditor.getChannelColor(fx.channel, index), fx.channel, null);

            sheet.section = cinematic;
            this.keyframes.view.addSheet(sheet);
            index += 1;
        }

        for (PostEffectStack.Layer layer : this.clip.stack.getAllTyped())
        {
            PostEffect effect = effectOf(layer);

            if (effect == null)
            {
                continue;
            }

            int categoryIndex = java.util.Arrays.asList(PostEffect.CATEGORIES).indexOf(effect.category);
            UIKeyframeSheet.Section section = new UIKeyframeSheet.Section(
                "post_fx_layer_" + layer.getId(),
                IKey.constant(UIPostEffectPicker.effectName(effect).get() + " / " + UIPostEffectPicker.categoryName(effect.category).get()),
                UIPostEffectPicker.categoryIcon(effect.category),
                CATEGORY_COLORS[Math.max(0, categoryIndex) % CATEGORY_COLORS.length]
            );

            for (int i = -1; i < effect.params.length; i++)
            {
                KeyframeChannel<Double> channel = layer.channel(i);
                IKey param = i < 0 ? UIKeys.POST_FX_INTENSITY : UIPostEffectPicker.paramName(effect.params[i].name());
                UIKeyframeSheet sheet = new UIKeyframeSheet("layer_" + layer.getId() + "_" + channel.getId(), param, UIKeyframeEditor.getChannelColor(channel, index), channel, null);

                sheet.section = section;
                this.keyframes.view.addSheet(sheet);
                index += 1;
            }
        }

        this.keyframes.view.pickKeyframe(null);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.refreshList();
        this.pick(this.picked());
        this.rebuildSheets();
    }
}
