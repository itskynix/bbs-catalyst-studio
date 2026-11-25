package mchorse.bbs_mod.actions;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * How a replay was moved: where it stood and looked when an action happened, and where it stands and
 * looks now. Actions that keep world coordinates (a broken or placed block, a clicked face, a dropped
 * item) are carried along with it, so a copy of a character acts on the block in front of itself, not
 * on the one the original acted on.
 *
 * <p>Points turn around the replay's own position by the change of yaw. Anything bound to the block
 * grid (block states, clicked faces) turns by the nearest quarter turn.</p>
 */
public class ActionRelocation
{
    private static final double EPSILON = 1E-6D;

    private final Vec3d from;
    private final Vec3d to;
    private final double yaw;

    public ActionRelocation(Vec3d from, Vec3d to, double yawDelta)
    {
        this.from = from;
        this.to = to;
        this.yaw = MathHelper.wrapDegrees(yawDelta);
    }

    /** Whether the replay did not move at all, so nothing needs touching. */
    public boolean isIdentity()
    {
        return this.from.squaredDistanceTo(this.to) < EPSILON * EPSILON && Math.abs(this.yaw) < EPSILON;
    }

    /** A point of the world, carried with the replay. */
    public Vec3d point(double x, double y, double z)
    {
        return this.to.add(this.vector(x - this.from.x, y - this.from.y, z - this.from.z));
    }

    /** A direction or velocity: only turned, never moved. */
    public Vec3d vector(double x, double y, double z)
    {
        if (Math.abs(this.yaw) < EPSILON)
        {
            return new Vec3d(x, y, z);
        }

        double radians = Math.toRadians(this.yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new Vec3d(x * cos - z * sin, y, x * sin + z * cos);
    }

    /** The block whose centre the old block's centre was carried to. */
    public BlockPos block(int x, int y, int z)
    {
        Vec3d center = this.point(x + 0.5D, y + 0.5D, z + 0.5D);

        return BlockPos.ofFloored(center.x + EPSILON, center.y + EPSILON, center.z + EPSILON);
    }

    /** How many clockwise quarter turns the change of yaw rounds to, 0 to 3. */
    public int quarterTurns()
    {
        return Math.floorMod((int) Math.round(this.yaw / 90D), 4);
    }

    /** A point inside or on a block, kept at the same place of the carried block. */
    public Vec3d hit(Vec3d hit, BlockPos oldBlock, BlockPos newBlock)
    {
        double x = hit.x - (oldBlock.getX() + 0.5D);
        double z = hit.z - (oldBlock.getZ() + 0.5D);

        for (int i = this.quarterTurns(); i > 0; i--)
        {
            double previous = x;

            x = -z;
            z = previous;
        }

        return new Vec3d(newBlock.getX() + 0.5D + x, newBlock.getY() + (hit.y - oldBlock.getY()), newBlock.getZ() + 0.5D + z);
    }

    public Direction direction(Direction direction)
    {
        if (direction.getAxis() == Direction.Axis.Y)
        {
            return direction;
        }

        for (int i = this.quarterTurns(); i > 0; i--)
        {
            direction = direction.rotateYClockwise();
        }

        return direction;
    }

    public BlockRotation rotation()
    {
        return switch (this.quarterTurns())
        {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }
}
