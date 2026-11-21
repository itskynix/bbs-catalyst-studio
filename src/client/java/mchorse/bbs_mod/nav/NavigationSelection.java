package mchorse.bbs_mod.nav;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the navigation wand is pointing out and has not saved yet, purely client-side: a box of two
 * corners, A and B, that can be pushed and slid the way the structure wand's can, and the path
 * blocks painted since the last save. Saving moves both into the scene's network and clears them.
 */
public final class NavigationSelection
{
    private static BlockPos a;
    private static BlockPos b;

    /** The path blocks painted and not yet saved */
    private static final Set<BlockPos> PATH = new LinkedHashSet<>();

    private NavigationSelection()
    {}

    @Nullable
    public static BlockPos getA()
    {
        return a;
    }

    @Nullable
    public static BlockPos getB()
    {
        return b;
    }

    public static void setA(BlockPos pos)
    {
        a = pos;
    }

    public static void setB(BlockPos pos)
    {
        b = pos;
    }

    public static Set<BlockPos> getPath()
    {
        return PATH;
    }

    /** Both corners are down: there is a box to show, reshape and save */
    public static boolean isReady()
    {
        return a != null && b != null;
    }

    public static boolean isEmpty()
    {
        return a == null && b == null && PATH.isEmpty();
    }

    public static void clear()
    {
        a = null;
        b = null;
        PATH.clear();
    }

    @Nullable
    public static BlockPos getMin()
    {
        return isReady() ? new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())) : null;
    }

    @Nullable
    public static BlockPos getMax()
    {
        return isReady() ? new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())) : null;
    }

    @Nullable
    public static Vec3i getSize()
    {
        BlockPos min = getMin();

        return min == null ? null : getMax().subtract(min).add(1, 1, 1);
    }

    /** The box in world space, block faces included, or null while a corner is missing */
    @Nullable
    public static Box getBox()
    {
        BlockPos min = getMin();

        if (min == null)
        {
            return null;
        }

        BlockPos max = getMax();

        return new Box(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /** Pushes the face on a side of the box along its normal, outward for a positive amount; the corner on it moves and stops at the opposite face */
    public static boolean push(Direction face, int amount)
    {
        if (!isReady() || amount == 0)
        {
            return false;
        }

        Direction.Axis axis = face.getAxis();
        boolean positive = face.getDirection() == Direction.AxisDirection.POSITIVE;
        int av = a.getComponentAlongAxis(axis);
        int bv = b.getComponentAlongAxis(axis);
        boolean moveA = positive ? av > bv : av < bv;
        int corner = moveA ? av : bv;
        int other = moveA ? bv : av;
        int moved = corner + (positive ? amount : -amount);

        if (positive ? moved < other : moved > other)
        {
            moved = other;
        }

        if (moved == corner)
        {
            return false;
        }

        if (moveA)
        {
            a = withAxis(a, axis, moved);
        }
        else
        {
            b = withAxis(b, axis, moved);
        }

        return true;
    }

    /** Slides the whole box along a face's normal */
    public static boolean move(Direction face, int amount)
    {
        if (!isReady() || amount == 0)
        {
            return false;
        }

        a = a.offset(face, amount);
        b = b.offset(face, amount);

        return true;
    }

    private static BlockPos withAxis(BlockPos pos, Direction.Axis axis, int value)
    {
        return switch (axis)
        {
            case X -> new BlockPos(value, pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), value, pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), value);
        };
    }
}
