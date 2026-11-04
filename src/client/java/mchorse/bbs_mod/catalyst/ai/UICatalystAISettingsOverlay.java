package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

/**
 * The keys of the hosted models, one block per provider: the API key (masked, with an eye to
 * show it), the model, and for the OpenAI compatible one its base address. Written to
 * {@code bbs/catalyst_ai.json} when the window closes.
 */
public class UICatalystAISettingsOverlay extends UIOverlayPanel
{
    public UICatalystAISettingsOverlay(Runnable saved)
    {
        super(L10n.lang("bbs.catalyst_ai.settings.title"));

        CatalystAIConfig config = CatalystAIConfig.get();
        UIScrollView scroll = new UIScrollView();

        scroll.full(this.content);
        scroll.column(4).vertical().stretch().scroll().padding(8);

        scroll.add(UI.label(L10n.lang("bbs.catalyst_ai.settings.note")).color(Colors.LIGHTER_GRAY));

        for (CatalystAIProvider provider : CatalystAIProvider.values())
        {
            if (!provider.isOnline())
            {
                continue;
            }

            UIPasswordTextbox key = new UIPasswordTextbox((t) -> config.setKey(provider, t));
            UIIcon eye = new UIIcon(() -> key.masked ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> key.masked = !key.masked);
            UITextbox custom = new UITextbox(200, (t) -> config.setModel(provider, t));
            String current = config.getModel(provider);
            boolean preset = provider.models.contains(current);

            key.setText(config.getKey(provider));
            key.placeholder(L10n.lang("bbs.catalyst_ai.settings.key"));
            eye.tooltip(L10n.lang("bbs.catalyst_ai.settings.show_hide"));
            eye.wh(20, 20);
            custom.setText(preset ? "" : current);
            custom.placeholder(L10n.lang("bbs.catalyst_ai.settings.custom_model_name"));
            custom.setVisible(!preset);

            /* A drop-down of the provider's models, and "Custom model..." for any other name typed below it */
            UIButton model = new UIButton(modelLabel(preset ? current : null), null);

            model.callback = (b) -> this.getContext().replaceContextMenu((menu) ->
            {
                for (String name : provider.models)
                {
                    menu.action(Icons.SPARKLES, modelLabel(name), name.equals(config.getModel(provider)) && !custom.isVisible(), () ->
                    {
                        config.setModel(provider, name);
                        model.label = modelLabel(name);
                        custom.setVisible(false);
                        scroll.resize();
                    });
                }

                menu.action(Icons.EDIT, modelLabel(null), custom.isVisible(), () ->
                {
                    config.setModel(provider, custom.getText());
                    model.label = modelLabel(null);
                    custom.setVisible(true);
                    scroll.resize();
                });
            });

            UIElement keyRow = UI.row(4, key, eye);

            key.w(1F, -24);
            eye.w(20);

            scroll.add(UI.label(L10n.lang(provider.getLangKey())).background().marginTop(8));
            scroll.add(keyRow);
            scroll.add(UI.row(4, UI.label(L10n.lang("bbs.catalyst_ai.settings.model")), model));
            scroll.add(custom);

            if (provider == CatalystAIProvider.CUSTOM)
            {
                UITextbox url = new UITextbox(300, (t) -> config.setUrl(provider, t));

                url.setText(config.getUrl(provider));
                url.placeholder(L10n.lang("bbs.catalyst_ai.settings.url"));
                scroll.add(UI.row(4, UI.label(L10n.lang("bbs.catalyst_ai.settings.url")), url));
            }
        }

        this.content.add(scroll);
        this.onClose((e) ->
        {
            config.save();

            if (saved != null)
            {
                saved.run();
            }
        });
    }

    /** A model's name as the list shows it, the default one marked; null is the "custom model" entry. */
    private static IKey modelLabel(String model)
    {
        if (model == null)
        {
            return L10n.lang("bbs.catalyst_ai.settings.custom_model");
        }

        for (CatalystAIProvider provider : CatalystAIProvider.values())
        {
            if (model.equals(provider.getDefaultModel()))
            {
                return L10n.lang("bbs.catalyst_ai.settings.default_model").format(model);
            }
        }

        return IKey.constant(model);
    }

    /** A textbox that draws stars over what is typed until the eye next to it is clicked. */
    public static class UIPasswordTextbox extends UITextbox
    {
        public boolean masked = true;

        public UIPasswordTextbox(Consumer<String> callback)
        {
            super(1000, callback);
        }

        @Override
        public void render(UIContext context)
        {
            super.render(context);

            if (!this.masked || this.textbox.getText().isEmpty())
            {
                return;
            }

            int x = this.textbox.area.x + 4;
            int y = this.textbox.area.my() - this.textbox.font.getHeight() / 2;
            int w = this.textbox.area.w - 8;
            int h = this.textbox.font.getHeight();
            String stars = "*".repeat(Math.min(this.textbox.getText().length(), 40));

            context.batcher.box(x, y - 1, x + w, y + h + 1, BBSSettings.inputSurface());
            context.batcher.textShadow(stars, x, y, Colors.WHITE);
        }
    }
}
