package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The effect library window of the post-process clip: the six categories down the left, the effects
 * of the picked one as rounded glass buttons on the right, and a search over all of them on top.
 * Picking an effect hands it back and closes the window.
 */
public class UIPostEffectPicker extends UIOverlayPanel
{
    private static final int SIDE = 168;

    /** The category the window opens on: the last one looked at */
    private static String lastCategory = "";

    private final Consumer<PostEffect> callback;
    private final UITextbox search;
    private final UIScrollView effects;
    private final List<UIButton> categoryButtons = new ArrayList<>();
    private String category = lastCategory;

    public static IKey effectName(PostEffect effect)
    {
        return L10n.lang("bbs.ui.post_fx.effect." + effect.id);
    }

    public static IKey categoryName(String category)
    {
        return category.isEmpty() ? UIKeys.POST_FX_ALL : L10n.lang("bbs.ui.post_fx.category." + category);
    }

    public static IKey paramName(String param)
    {
        return L10n.lang("bbs.ui.post_fx.param." + param);
    }

    public static Icon categoryIcon(String category)
    {
        switch (category)
        {
            case PostEffect.OPTICS: return Icons.CAMERA;
            case PostEffect.GLITCH: return Icons.EXCHANGE;
            case PostEffect.ARTISTIC: return Icons.GALLERY;
            case PostEffect.GEOMETRIC: return Icons.FULLSCREEN;
            case PostEffect.ATMOSPHERE: return Icons.GLOBE;
            case PostEffect.PSYCHOLOGICAL: return Icons.VISIBLE;
            default: return Icons.SPARKLES;
        }
    }

    public UIPostEffectPicker(Consumer<PostEffect> callback)
    {
        super(UIKeys.POST_FX_PICKER_TITLE);

        this.callback = callback;
        this.icon(Icons.SPARKLES);

        this.search = new UITextbox(100, (t) -> this.rebuild());
        this.search.placeholder(UIKeys.POST_FX_SEARCH);
        this.search.relative(this.content).xy(8, 8).w(1F, -16).h(20);

        UIElement side = new UIElement();

        side.relative(this.content).xy(8, 36).w(SIDE).h(1F, -44).column(4).vertical().stretch();

        this.addCategory(side, "");

        for (String id : PostEffect.CATEGORIES)
        {
            this.addCategory(side, id);
        }

        this.effects = new UIScrollView();
        this.effects.relative(this.content).x(SIDE + 16).y(36).w(1F, -SIDE - 24).h(1F, -44);

        GridResizer grid = this.effects.grid(6);

        /* scroll(): the grid tells the view how tall its rows are in all (scrollItemSize 1, the size
         * in pixels) on every layout; without it the view thinks it has nothing to scroll */
        grid.scroll();
        grid.padding(2);
        grid.width(150).height(24);

        this.content.add(this.search, side, this.effects);
        this.rebuild();
    }

    private void addCategory(UIElement side, String id)
    {
        int count = id.isEmpty() ? PostEffect.all().size() : PostEffect.inCategory(id).size();
        UIButton button = new UIButton(IKey.raw(categoryName(id).get() + "  " + count), (b) ->
        {
            this.category = id;
            lastCategory = id;
            this.rebuild();
        });

        button.h(22);
        button.tooltip(categoryName(id));
        side.add(button);
        this.categoryButtons.add(button);
    }

    private void rebuild()
    {
        String query = this.search.getText().trim().toLowerCase(Locale.ROOT);

        for (int i = 0; i < this.categoryButtons.size(); i++)
        {
            String id = i == 0 ? "" : PostEffect.CATEGORIES[i - 1];

            this.categoryButtons.get(i).primaryStyle = id.equals(this.category) && query.isEmpty();
        }

        this.effects.removeAll();

        for (PostEffect effect : PostEffect.all())
        {
            String name = effectName(effect).get();

            /* A search looks through every category */
            if (!query.isEmpty() ? !name.toLowerCase(Locale.ROOT).contains(query) && !effect.id.contains(query) : !this.category.isEmpty() && !effect.category.equals(this.category))
            {
                continue;
            }

            UIButton button = new UIButton(IKey.raw(name), (b) ->
            {
                this.close();
                this.callback.accept(effect);
            });

            button.tooltip(effect.depth ? IKey.raw(categoryName(effect.category).get() + "\n" + UIKeys.POST_FX_USES_DEPTH.get()) : categoryName(effect.category));
            this.effects.add(button);
        }

        /* A new set of effects starts at its top; the layout below measures it and sets the scroll size */
        this.effects.scroll.scrollTo(0);
        this.effects.resize();
    }

    @Override
    public int getMinWidth()
    {
        return 420;
    }

    @Override
    public int getMinHeight()
    {
        return 300;
    }
}
