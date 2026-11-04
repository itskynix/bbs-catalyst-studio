package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.forms.forms.utils.FormLatticeValues;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.forms.values.ValueModelTransformationMode;
import mchorse.bbs_mod.settings.values.mc.ValueItemStack;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.registry.Registries;

public class ItemForm extends Form implements ILatticeForm
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.LINE;

    public final ValueItemStack stack = new ValueItemStack("item_stack");
    public final ValueModelTransformationMode modelTransform = new ValueModelTransformationMode("modelTransform", ModelTransformationMode.NONE);
    public final ValueColor color = new ValueColor("color", Color.white());
    /** Dissolve and shatter: the item burning away or breaking apart. */
    public final FormFxValues fx = new FormFxValues(this);
    /** Free-form deformation: the item bent, crushed or stretched by a lattice of control points */
    public final FormLatticeValues latticeValues = new FormLatticeValues(this);

    @Override
    public ValueLattice getLattice()
    {
        return this.latticeValues.lattice;
    }

    public ItemForm()
    {
        this.add(this.stack);
        this.add(this.modelTransform);
        this.add(this.color);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return Registries.ITEM.getId(this.stack.get().getItem()).toString();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}