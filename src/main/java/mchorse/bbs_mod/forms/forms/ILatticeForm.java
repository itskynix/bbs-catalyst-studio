package mchorse.bbs_mod.forms.forms;

/**
 * A form a free-form deformation lattice can bend: models, blocks and items. The lattice is the same
 * value group for all of them ({@link ValueLattice}); what differs is the box it is laid over, which
 * the renderer of each form works out for itself.
 */
public interface ILatticeForm
{
    ValueLattice getLattice();
}
