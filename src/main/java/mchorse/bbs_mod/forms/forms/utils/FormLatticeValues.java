package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ValueLattice;

/**
 * The lattice of a form that has none of its own: its settings, and its points, bulge sphere and
 * centre as the keyframe tracks the dope sheet shows. A form creates one and hands itself over,
 * which puts the values in its own property list - the same names a model's lattice uses, so the
 * tracks fold into the same group.
 */
public class FormLatticeValues
{
    public final ValueLattice.Points points = new ValueLattice.Points("lattice_points");
    public final ValueLattice.Bulge bulge = new ValueLattice.Bulge("lattice_bulge");
    public final ValueLattice.Center center = new ValueLattice.Center("lattice_center");
    public final ValueLattice lattice = new ValueLattice("lattice", this.points, this.bulge, this.center);

    public FormLatticeValues(Form form)
    {
        this.lattice.invisible();
        form.add(this.lattice);
        form.add(this.points);
        form.add(this.bulge);
        form.add(this.center);
    }
}
