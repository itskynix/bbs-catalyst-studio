package mchorse.bbs_mod.ui.film.replays.pov;

import mchorse.bbs_mod.film.replays.PovBodyParts;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.List;
import java.util.function.Consumer;

/** The POV body parts of a replay, each as its form and the rig bone it hangs on. */
public class UIPovBodyPartList extends UIList<PovBodyParts.Part>
{
    public UIPovBodyPartList(Consumer<List<PovBodyParts.Part>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = UIConstants.LIST_ITEM_HEIGHT;
    }

    @Override
    protected String elementToString(UIContext context, int i, PovBodyParts.Part element)
    {
        Form form = element.form.get();
        String name = form == null ? "-" : form.getDisplayName();

        return (element.enabled.get() ? "" : "(") + name + " → " + element.bone.get() + (element.enabled.get() ? "" : ")");
    }
}
