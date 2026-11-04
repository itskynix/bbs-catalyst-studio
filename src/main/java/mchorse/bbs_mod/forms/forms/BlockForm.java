package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.forms.utils.FormFxValues;
import mchorse.bbs_mod.forms.forms.utils.FormLatticeValues;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.settings.values.mc.ValueBlockState;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.registry.Registries;

public class BlockForm extends Form implements ILatticeForm
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.BLOCK;

    public final ValueBlockState blockState = new ValueBlockState("block_state");
    public final ValueColor color = new ValueColor("color", Color.white());
    /** Dissolve and shatter: the block burning away or breaking apart. */
    public final FormFxValues fx = new FormFxValues(this);
    /** Free-form deformation: the block bent, crushed or stretched by a lattice of control points */
    public final FormLatticeValues latticeValues = new FormLatticeValues(this);

    @Override
    public ValueLattice getLattice()
    {
        return this.latticeValues.lattice;
    }

    public BlockForm()
    {
        this.add(this.blockState);
        this.add(this.color);
    }

    @Override
    protected String getDefaultDisplayName()
    {
        return Registries.BLOCK.getId(this.blockState.get().getBlock()).toString();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}