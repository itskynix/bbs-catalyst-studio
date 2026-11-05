package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** The entries of a POV clip that holds a list (status effects, screen effects), in drawing order */
public class UIPovEntryList<T> extends UIList<T>
{
    private final Function<T, String> label;

    public UIPovEntryList(Function<T, String> label, Consumer<List<T>> callback)
    {
        super(callback);

        this.label = label;
        this.scroll.scrollItemSize = UIConstants.LIST_ITEM_HEIGHT;
    }

    @Override
    protected String elementToString(UIContext context, int i, T element)
    {
        return this.label.apply(element);
    }
}
