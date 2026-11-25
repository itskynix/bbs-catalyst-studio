package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.actions.ActionRelocation;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import net.minecraft.util.math.BlockPos;

public abstract class BlockActionClip extends ActionClip
{
    public final ValueInt x = new ValueInt("x", 0);
    public final ValueInt y = new ValueInt("y", 0);
    public final ValueInt z = new ValueInt("z", 0);

    public BlockActionClip()
    {
        super();

        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
    }

    @Override
    public void shift(double dx, double dy, double dz)
    {
        super.shift(dx, dy, dz);

        this.x.set((int) Math.floor(this.x.get() + dx + 0.5D));
        this.y.set((int) Math.floor(this.y.get() + dy + 0.5D));
        this.z.set((int) Math.floor(this.z.get() + dz + 0.5D));
    }

    @Override
    public void relocate(ActionRelocation relocation)
    {
        BlockPos pos = relocation.block(this.x.get(), this.y.get(), this.z.get());

        this.x.set(pos.getX());
        this.y.set(pos.getY());
        this.z.set(pos.getZ());
    }
}