package mchorse.bbs_mod.nav;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * The walkable surfaces of a box of the world, for an agent of a given size: a grid of one-block
 * columns, each with a node for every floor an agent can stand on in it.
 *
 * <p>A floor is the top of a block - a full block, a slab, a stair, a carpet - with room above it
 * for the agent's height. It is not one where the ground is fluid, burns, stings or is higher than a
 * block (a fence). Two floors in neighbouring columns are joined when they differ by no more than the
 * step height, up or down, so a wall higher than a step, a drop deeper than one, a gap and a low
 * ceiling are all where the mesh ends. A node whose neighbour is missing on a side is an edge. The
 * agent's radius then shaves off as many rings of edge as it is wider than a block's half.</p>
 */
public final class NavGrid
{
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** One walkable floor: the block column it is in, and the height of the floor in the world */
    public static final class Node
    {
        public final int x;
        public final int z;
        public final double y;
        public boolean edge;
        boolean removed;
        int index;

        Node(int x, int z, double y)
        {
            this.x = x;
            this.z = z;
            this.y = y;
        }

        public double centerX()
        {
            return this.x + 0.5D;
        }

        public double centerZ()
        {
            return this.z + 0.5D;
        }
    }

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final float maxStep;

    private final Map<Long, List<Node>> columns = new HashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private long signature;

    private NavGrid(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, float maxStep)
    {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.maxStep = maxStep;
    }

    /** What a scan of the same blocks gives, whatever it was scanned at: changes when a block that matters changes */
    public long getSignature()
    {
        return this.signature;
    }

    public List<Node> getNodes()
    {
        return this.nodes;
    }

    private static long key(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Scans {@code [minX, minX+sizeX) x [minY, minY+sizeY) x [minZ, minZ+sizeZ)}.
     *
     * @param radius the agent's radius in blocks
     * @param height the agent's height in blocks
     * @param maxStep the tallest step in blocks
     */
    public static NavGrid build(World world, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, float radius, float height, float maxStep)
    {
        NavGrid grid = new NavGrid(minX, minY, minZ, sizeX, sizeY, sizeZ, maxStep);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = minX; x < minX + sizeX; x++)
        {
            for (int z = minZ; z < minZ + sizeZ; z++)
            {
                for (int y = minY; y < minY + sizeY; y++)
                {
                    double floor = floorAt(world, pos, x, y, z, height);

                    if (!Double.isNaN(floor))
                    {
                        Node node = new Node(x, z, floor);

                        grid.columns.computeIfAbsent(key(x, z), (k) -> new ArrayList<>(1)).add(node);
                    }
                }
            }
        }

        grid.refresh();

        /* The agent's radius: each ring of edge it does not fit through is taken off */
        int rings = Math.max(0, (int) Math.ceil(radius - 0.5F));

        for (int i = 0; i < rings; i++)
        {
            boolean any = false;

            for (Node node : grid.nodes)
            {
                if (node.edge)
                {
                    node.removed = true;
                    any = true;
                }
            }

            if (!any)
            {
                break;
            }

            for (List<Node> column : grid.columns.values())
            {
                column.removeIf((node) -> node.removed);
            }

            grid.refresh();
        }

        return grid;
    }

    /**
     * The height of the floor the block at ({@code x}, {@code y}, {@code z}) is, or NaN when it is not
     * one an agent of {@code height} can stand on.
     */
    private static double floorAt(World world, BlockPos.Mutable pos, int x, int y, int z, float height)
    {
        BlockState ground = world.getBlockState(pos.set(x, y, z));

        if (ground.isAir() || !ground.getFluidState().isEmpty() || hurts(ground.getBlock()))
        {
            return Double.NaN;
        }

        VoxelShape shape = ground.getCollisionShape(world, pos);

        if (shape.isEmpty())
        {
            return Double.NaN;
        }

        double top = shape.getMax(Direction.Axis.Y);

        /* A fence or a wall is more than a block: not something to stand on */
        if (top > 1.0001D)
        {
            return Double.NaN;
        }

        double floor = y + top;

        /* Room above, in every block the agent's height reaches into */
        int last = (int) Math.floor(floor + height - 1.0E-4D);

        for (int cy = y; cy <= last; cy++)
        {
            BlockState state = world.getBlockState(pos.set(x, cy, z));

            if (cy == y)
            {
                if (!state.getFluidState().isEmpty())
                {
                    return Double.NaN;
                }

                continue;
            }

            if (!state.getFluidState().isEmpty() && state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA))
            {
                return Double.NaN;
            }

            if (hurts(state.getBlock()))
            {
                return Double.NaN;
            }

            VoxelShape above = state.getCollisionShape(world, pos);

            if (!above.isEmpty() && cy + above.getMin(Direction.Axis.Y) < floor + height - 1.0E-4D && cy + above.getMax(Direction.Axis.Y) > floor + 1.0E-4D)
            {
                return Double.NaN;
            }
        }

        return floor;
    }

    private static boolean hurts(Block block)
    {
        return block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK || block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.CAMPFIRE
            || block == Blocks.SOUL_CAMPFIRE || block == Blocks.WITHER_ROSE || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POWDER_SNOW
            || block == Blocks.COBWEB || block == Blocks.LAVA;
    }

    /** Numbers the nodes, marks the edges and takes the signature */
    private void refresh()
    {
        this.nodes.clear();

        for (List<Node> column : this.columns.values())
        {
            this.nodes.addAll(column);
        }

        /* A fixed order whatever the map's, so that what is built on the mesh does not depend on it */
        Collections.sort(this.nodes, (a, b) -> a.x != b.x ? Integer.compare(a.x, b.x) : (a.z != b.z ? Integer.compare(a.z, b.z) : Double.compare(a.y, b.y)));

        long hash = 1125899906842597L;

        for (int i = 0; i < this.nodes.size(); i++)
        {
            Node node = this.nodes.get(i);

            node.index = i;
            hash = 31L * hash + node.x;
            hash = 31L * hash + node.z;
            hash = 31L * hash + Math.round(node.y * 16D);
        }

        this.signature = hash;

        for (Node node : this.nodes)
        {
            node.edge = false;

            for (int[] d : CARDINAL)
            {
                if (this.neighbor(node, d[0], d[1]) == null)
                {
                    node.edge = true;

                    break;
                }
            }
        }
    }

    /** The node a step from {@code node} by ({@code dx}, {@code dz}) reaches, or null; diagonals need both ways round open */
    public Node neighbor(Node node, int dx, int dz)
    {
        if (dx != 0 && dz != 0)
        {
            Node a = this.neighbor(node, dx, 0);
            Node b = this.neighbor(node, 0, dz);

            if (a == null || b == null)
            {
                return null;
            }
        }

        List<Node> column = this.columns.get(key(node.x + dx, node.z + dz));

        if (column == null)
        {
            return null;
        }

        Node best = null;
        double distance = Double.MAX_VALUE;

        for (Node other : column)
        {
            double d = Math.abs(other.y - node.y);

            if (d <= this.maxStep + 1.0E-4D && d < distance)
            {
                best = other;
                distance = d;
            }
        }

        return best;
    }

    /** The node nearest a point, looking a few columns round it, or null when there is none */
    public Node nearest(double x, double y, double z)
    {
        int cx = (int) Math.floor(x);
        int cz = (int) Math.floor(z);
        Node best = null;
        double distance = Double.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++)
        {
            for (int dz = -3; dz <= 3; dz++)
            {
                List<Node> column = this.columns.get(key(cx + dx, cz + dz));

                if (column == null)
                {
                    continue;
                }

                for (Node node : column)
                {
                    double ex = node.centerX() - x;
                    double ez = node.centerZ() - z;
                    double ey = (node.y - y) * 2D;
                    double d = ex * ex + ez * ez + ey * ey;

                    if (d < distance)
                    {
                        best = node;
                        distance = d;
                    }
                }
            }
        }

        if (best != null)
        {
            return best;
        }

        /* Nothing close by: the nearest floor of the whole mesh, so an actor that starts outside the volume walks in */
        for (Node node : this.nodes)
        {
            double ex = node.centerX() - x;
            double ez = node.centerZ() - z;
            double ey = node.y - y;
            double d = ex * ex + ez * ez + ey * ey;

            if (d < distance)
            {
                best = node;
                distance = d;
            }
        }

        return best;
    }

    /** The floor under a point: the node of its column nearest in height within a step, or NaN */
    public double floorAt(double x, double y, double z)
    {
        List<Node> column = this.columns.get(key((int) Math.floor(x), (int) Math.floor(z)));

        if (column == null)
        {
            return Double.NaN;
        }

        double best = Double.NaN;
        double distance = Double.MAX_VALUE;

        for (Node node : column)
        {
            double d = Math.abs(node.y - y);

            if (d <= this.maxStep + 0.5D && d < distance)
            {
                best = node.y;
                distance = d;
            }
        }

        return best;
    }

    public Node random(Random random)
    {
        return this.nodes.isEmpty() ? null : this.nodes.get(random.nextInt(this.nodes.size()));
    }

    /** The shortest way between two nodes (A*, eight ways), start to goal inclusive, or null when there is none */
    public List<Node> path(Node start, Node goal)
    {
        if (start == null || goal == null)
        {
            return null;
        }

        if (start == goal)
        {
            return new ArrayList<>(List.of(start));
        }

        int count = this.nodes.size();
        double[] cost = new double[count];
        int[] from = new int[count];
        boolean[] closed = new boolean[count];

        java.util.Arrays.fill(cost, Double.MAX_VALUE);
        java.util.Arrays.fill(from, -1);

        record Entry(int index, double score) {}

        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Double.compare(a.score(), b.score()));

        cost[start.index] = 0D;
        open.add(new Entry(start.index, heuristic(start, goal)));

        while (!open.isEmpty())
        {
            Entry entry = open.poll();

            if (closed[entry.index()])
            {
                continue;
            }

            closed[entry.index()] = true;

            Node node = this.nodes.get(entry.index());

            if (node == goal)
            {
                List<Node> path = new ArrayList<>();

                for (int i = goal.index; i >= 0; i = from[i])
                {
                    path.add(this.nodes.get(i));
                }

                Collections.reverse(path);

                return path;
            }

            for (int pass = 0; pass < 2; pass++)
            {
                for (int[] d : pass == 0 ? CARDINAL : DIAGONAL)
                {
                    Node next = this.neighbor(node, d[0], d[1]);

                    if (next == null || closed[next.index])
                    {
                        continue;
                    }

                    /* Stay off the edges where there is room: walls and drops are walked beside, not along */
                    double step = (pass == 0 ? 1D : 1.4142D) + Math.abs(next.y - node.y) * 0.5D + (next.edge ? 0.4D : 0D);
                    double through = cost[node.index] + step;

                    if (through < cost[next.index])
                    {
                        cost[next.index] = through;
                        from[next.index] = node.index;
                        open.add(new Entry(next.index, through + heuristic(next, goal)));
                    }
                }
            }
        }

        return null;
    }

    private static double heuristic(Node a, Node b)
    {
        double dx = Math.abs(a.x - b.x);
        double dz = Math.abs(a.z - b.z);

        return Math.max(dx, dz) + 0.4142D * Math.min(dx, dz);
    }
}
