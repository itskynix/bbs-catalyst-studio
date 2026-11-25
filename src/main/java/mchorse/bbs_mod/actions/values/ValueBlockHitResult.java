package mchorse.bbs_mod.actions.values;

import mchorse.bbs_mod.actions.ActionRelocation;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.EnumUtils;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ValueBlockHitResult extends ValueGroup
{
    public final ValueInt x = new ValueInt("x", 0);
    public final ValueInt y = new ValueInt("y", 0);
    public final ValueInt z = new ValueInt("z", 0);
    public final ValueDouble hitX = new ValueDouble("hitX", 0D);
    public final ValueDouble hitY = new ValueDouble("hitY", 0D);
    public final ValueDouble hitZ = new ValueDouble("hitZ", 0D);
    public final ValueInt direction = new ValueInt("direction", 0);
    public final ValueBoolean inside = new ValueBoolean("inside", false);

    public ValueBlockHitResult(String id)
    {
        super(id);

        this.add(this.x);
        this.add(this.y);
        this.add(this.z);
        this.add(this.hitX);
        this.add(this.hitY);
        this.add(this.hitZ);
        this.add(this.direction);
        this.add(this.inside);
    }

    public void setHitResult(BlockHitResult result)
    {
        this.x.set(result.getBlockPos().getX());
        this.y.set(result.getBlockPos().getY());
        this.z.set(result.getBlockPos().getZ());
        this.hitX.set(result.getPos().x);
        this.hitY.set(result.getPos().y);
        this.hitZ.set(result.getPos().z);
        this.inside.set(result.isInsideBlock());
        this.direction.set(result.getSide().ordinal());
    }

    public void setHitResult(ItemUsageContext context)
    {
        this.x.set(context.getBlockPos().getX());
        this.y.set(context.getBlockPos().getY());
        this.z.set(context.getBlockPos().getZ());
        this.hitX.set(context.getHitPos().x);
        this.hitY.set(context.getHitPos().y);
        this.hitZ.set(context.getHitPos().z);
        this.inside.set(context.hitsInsideBlock());
        this.direction.set(context.getSide().ordinal());
    }

    public void shift(double x, double y, double z)
    {
        /* The block moves by whole blocks, and the clicked point with it, so it stays on the block */
        int dx = (int) Math.floor(x + 0.5D);
        int dy = (int) Math.floor(y + 0.5D);
        int dz = (int) Math.floor(z + 0.5D);

        this.x.set(this.x.get() + dx);
        this.y.set(this.y.get() + dy);
        this.z.set(this.z.get() + dz);
        this.hitX.set(this.hitX.get() + dx);
        this.hitY.set(this.hitY.get() + dy);
        this.hitZ.set(this.hitZ.get() + dz);
    }

    public void relocate(ActionRelocation relocation)
    {
        BlockPos old = this.getBlockPos();
        BlockPos pos = relocation.block(old.getX(), old.getY(), old.getZ());
        Vec3d hit = relocation.hit(new Vec3d(this.hitX.get(), this.hitY.get(), this.hitZ.get()), old, pos);
        Direction side = relocation.direction(EnumUtils.getValue(this.direction.get(), Direction.values(), Direction.UP));

        this.x.set(pos.getX());
        this.y.set(pos.getY());
        this.z.set(pos.getZ());
        this.hitX.set(hit.x);
        this.hitY.set(hit.y);
        this.hitZ.set(hit.z);
        this.direction.set(side.ordinal());
    }

    public BlockPos getBlockPos()
    {
        return new BlockPos(this.x.get(), this.y.get(), this.z.get());
    }

    public BlockHitResult getHitResult()
    {
        Vec3d vec = new Vec3d(this.hitX.get(), this.hitY.get(), this.hitZ.get());

        return new BlockHitResult(vec, EnumUtils.getValue(this.direction.get(), Direction.values(), Direction.UP), this.getBlockPos(), this.inside.get());
    }
}