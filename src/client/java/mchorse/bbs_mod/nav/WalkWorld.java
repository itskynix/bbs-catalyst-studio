package mchorse.bbs_mod.nav;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Walking on the real blocks of the world, with nothing to go on but the blocks: where a body of a
 * player's size (0.6 wide, 1.8 tall) fits, what floor it stands on, how it moves without going into
 * anything, and how it finds its way across a stretch of ground.
 *
 * <p>It is what keeps an actor out of the walls where the scene has no navigation volume, or the
 * actor is outside of it. With a volume the mesh is the better map; this is the guard under it, that
 * no position is ever a place the body does not fit.</p>
 *
 * <ul>
 * <li><b>Fitting:</b> the box of the body, with no solid block, slab, stair or fence in it.</li>
 * <li><b>The floor:</b> the top of the first thing to stand on, looking from one block above the feet
 * down a few; the body must fit on it. So a step of one block up is a floor, two is a wall, a fence a
 * wall, and a drop is a floor a little lower down.</li>
 * <li><b>Moving:</b> in small steps, the whole way, and if that is blocked then along the one axis and
 * along the other, so a wall is slid along rather than stopped at.</li>
 * </ul>
 */
public final class WalkWorld
{
    private static final double HALF = 0.3D;
    private static final double HEIGHT = 1.8D;

    /** The tallest step up, in blocks: a jump */
    private static final double STEP = 1.0D;

    /** The deepest drop to a floor, in blocks */
    private static final double DROP = 3.0D;

    /** How far a single move goes before the body is checked again, in blocks */
    private static final double SUBSTEP = 0.25D;

    /** A route found over the ground: the places to go through, x and z, and whether it reaches the goal or only gets as near as it can */
    public record Route(List<double[]> points, boolean complete) {}

    private WalkWorld()
    {}

    /** Whether the body fits with its feet at the point: no collision shape in its box */
    public static boolean fits(World world, double x, double y, double z)
    {
        return world.isSpaceEmpty(new Box(x - HALF, y + 1.0E-3D, z - HALF, x + HALF, y + HEIGHT, z + HALF));
    }

    /**
     * The height of the floor the body would stand on at ({@code x}, {@code z}), coming from {@code y}:
     * up to a block higher or a few lower. NaN where there is none, or where the body does not fit on it.
     */
    public static double floor(World world, double x, double y, double z)
    {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(y + STEP + 1.0E-4D);
        int bottom = (int) Math.floor(y - DROP);

        for (int by = top; by >= Math.max(bottom, world.getBottomY()); by--)
        {
            pos.set(bx, by, bz);

            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(world, pos);

            if (shape.isEmpty())
            {
                continue;
            }

            double surface = by + shape.getMax(Direction.Axis.Y);

            /* Higher than a step: not a floor, and what is under it is under a wall */
            if (surface > y + STEP + 1.0E-4D)
            {
                continue;
            }

            return fits(world, x, surface, z) ? surface : Double.NaN;
        }

        return Double.NaN;
    }

    /**
     * Moves the feet from ({@code x}, {@code y}, {@code z}) by ({@code dx}, {@code dz}) as far as the
     * blocks let them: returns the new x, y and z. A body that is already inside something is let go
     * where it is asked to, the one way out there is.
     */
    public static double[] move(World world, double x, double y, double z, double dx, double dz)
    {
        double length = Math.hypot(dx, dz);

        if (length < 1.0E-9D)
        {
            return new double[] {x, y, z};
        }

        if (!fits(world, x, y, z))
        {
            return new double[] {x + dx, y, z + dz};
        }

        int parts = Math.max(1, (int) Math.ceil(length / SUBSTEP));
        double sx = dx / parts;
        double sz = dz / parts;

        for (int i = 0; i < parts; i++)
        {
            double[][] tries = {{sx, sz}, {sx, 0D}, {0D, sz}};
            boolean moved = false;

            for (double[] attempt : tries)
            {
                if (Math.abs(attempt[0]) + Math.abs(attempt[1]) < 1.0E-9D)
                {
                    continue;
                }

                double floor = floor(world, x + attempt[0], y, z + attempt[1]);

                if (!Double.isNaN(floor))
                {
                    x += attempt[0];
                    z += attempt[1];
                    y = floor;
                    moved = true;

                    break;
                }
            }

            if (!moved)
            {
                break;
            }
        }

        return new double[] {x, y, z};
    }

    /** Whether the way in a straight line from one point to another can be walked: a floor all the way, no more than a step at a time */
    private static boolean clear(World world, double ax, double ay, double az, double bx, double bz)
    {
        double length = Math.hypot(bx - ax, bz - az);
        int parts = Math.max(1, (int) Math.ceil(length / 0.2D));
        double y = ay;

        for (int i = 1; i <= parts; i++)
        {
            double f = i / (double) parts;
            double floor = floor(world, ax + (bx - ax) * f, y, az + (bz - az) * f);

            if (Double.isNaN(floor))
            {
                return false;
            }

            y = floor;
        }

        return true;
    }

    private static final class Cell
    {
        final int x;
        final int z;
        final double y;
        double g;
        double f;
        Cell parent;
        boolean closed;

        Cell(int x, int z, double y)
        {
            this.x = x;
            this.z = z;
            this.y = y;
        }
    }

    private static long key(int x, int z, double y)
    {
        return (((long) x * 73856093L) ^ ((long) z * 19349663L)) * 31L + Math.round(y * 2D);
    }

    private static double heuristic(int x, int z, int gx, int gz)
    {
        double dx = Math.abs(x - gx);
        double dz = Math.abs(z - gz);

        return Math.max(dx, dz) + 0.4142D * Math.min(dx, dz);
    }

    /**
     * A route over the ground from the feet at ({@code sx}, {@code sy}, {@code sz}) to a point, found
     * (A*, eight ways) on the cells of the world that the body can stand in and walk between, at most
     * {@code limit} cells looked at and not further than 40 blocks away. When the point cannot be
     * reached the route goes as near as it got and is not complete. The route is then pulled straight
     * wherever a straight line can be walked.
     */
    public static Route path(World world, double sx, double sy, double sz, double gx, double gz, int limit)
    {
        int startX = (int) Math.floor(sx);
        int startZ = (int) Math.floor(sz);
        int goalX = (int) Math.floor(gx);
        int goalZ = (int) Math.floor(gz);
        Map<Long, Cell> cells = new HashMap<>();
        PriorityQueue<Cell> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Cell start = new Cell(startX, startZ, sy);
        Cell best = start;

        start.f = heuristic(startX, startZ, goalX, goalZ);
        cells.put(key(startX, startZ, sy), start);
        open.add(start);

        Cell reached = null;
        int looked = 0;

        while (!open.isEmpty() && looked < limit)
        {
            Cell cell = open.poll();

            if (cell.closed)
            {
                continue;
            }

            cell.closed = true;
            looked++;

            if (cell.x == goalX && cell.z == goalZ)
            {
                reached = cell;

                break;
            }

            if (heuristic(cell.x, cell.z, goalX, goalZ) < heuristic(best.x, best.z, goalX, goalZ))
            {
                best = cell;
            }

            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0)
                    {
                        continue;
                    }

                    int nx = cell.x + dx;
                    int nz = cell.z + dz;

                    if (Math.abs(nx - startX) > 40 || Math.abs(nz - startZ) > 40)
                    {
                        continue;
                    }

                    double floor = floor(world, nx + 0.5D, cell.y, nz + 0.5D);

                    if (Double.isNaN(floor))
                    {
                        continue;
                    }

                    /* A corner is not cut: both of the ways round it have to be open */
                    if (dx != 0 && dz != 0 && (Double.isNaN(floor(world, cell.x + dx + 0.5D, cell.y, cell.z + 0.5D)) || Double.isNaN(floor(world, cell.x + 0.5D, cell.y, cell.z + dz + 0.5D))))
                    {
                        continue;
                    }

                    long id = key(nx, nz, floor);
                    Cell next = cells.get(id);

                    if (next == null)
                    {
                        next = new Cell(nx, nz, floor);
                        next.g = Double.MAX_VALUE;
                        cells.put(id, next);
                    }

                    if (next.closed)
                    {
                        continue;
                    }

                    double through = cell.g + (dx != 0 && dz != 0 ? 1.4142D : 1D) + Math.abs(floor - cell.y) * 0.5D;

                    if (through < next.g)
                    {
                        next.g = through;
                        next.f = through + heuristic(nx, nz, goalX, goalZ);
                        next.parent = cell;
                        open.add(next);
                    }
                }
            }
        }

        Cell end = reached != null ? reached : best;
        List<Cell> chain = new ArrayList<>();

        for (Cell cell = end; cell != null; cell = cell.parent)
        {
            chain.add(cell);
        }

        Collections.reverse(chain);

        List<double[]> points = new ArrayList<>();

        for (Cell cell : chain)
        {
            points.add(new double[] {cell.x + 0.5D, cell.z + 0.5D, cell.y});
        }

        return new Route(pull(world, points), reached != null);
    }

    /** Takes out the points a straight, walkable line skips over, so a route is not a staircase of cell centres */
    private static List<double[]> pull(World world, List<double[]> points)
    {
        if (points.size() < 3)
        {
            return points;
        }

        List<double[]> out = new ArrayList<>();
        int at = 0;

        out.add(points.get(0));

        while (at < points.size() - 1)
        {
            int far = at + 1;

            for (int i = points.size() - 1; i > at + 1; i--)
            {
                double[] a = points.get(at);
                double[] b = points.get(i);

                if (clear(world, a[0], a[2], a[1], b[0], b[1]))
                {
                    far = i;

                    break;
                }
            }

            out.add(points.get(far));
            at = far;
        }

        return out;
    }
}
