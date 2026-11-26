package mchorse.bbs_mod.physics.client.scene;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.ShapeResult;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.readonly.ConstShape;
import mchorse.bbs_mod.physics.BBSPhysics;
import mchorse.bbs_mod.physics.engine.PhysicsLayers;
import mchorse.bbs_mod.physics.engine.PhysicsWorld;
import mchorse.bbs_mod.physics.engine.JoltRelease;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the blocks around a scene into static bodies, so physics lands on the world the film is
 * actually shot in instead of on an invented floor.
 *
 * <p>Built once, from a box of blocks around <em>every place the film keeps something simulated</em>
 * — the scene's origin, and the starting spot of each actor whose tree carries any physics. One
 * area was not enough, and that was found the hard way rather than reasoned about: a balloon
 * placed ninety blocks from the film's first actor fell through the world, because the world had
 * only ever been collected around that first actor — from the viewport, indistinguishable from
 * collision being broken. Overlapping areas are collected once.</p>
 *
 * <p><b>In sections, so the world can change under a scene.</b> The blocks go into one static body
 * per sixteen-block cube of the world ({@link Section}), every cube the areas reach having a body
 * from the start, an empty one holding only a placeholder far under the world. A block broken or
 * placed while a film is open is then one section rebuilt from the world as it is now and swapped
 * into its body ({@link #rebuild}) — a few thousand blocks read at most, never the whole window — and
 * the set of bodies never changes, so the scene's checkpoints stay valid around the edit.</p>
 *
 * <p>Blocks with every neighbour a full cube are skipped. Nothing can ever touch them, and in a
 * region this size they are the overwhelming majority — everything under the surface. Skipping
 * them is the difference between a few thousand boxes and a few tens of thousands.</p>
 */
public final class WorldCollider
{
    /** The edge of a section, in blocks. */
    public static final int SECTION = 16;

    /** One sixteen-block cube of the collected world: its place, and the shape its body stands on now. */
    public static final class Section
    {
        public final int sx;
        public final int sy;
        public final int sz;
        ConstShape shape;
        int boxes;
        /** The batch of world edits this section was last rebuilt for, so scenes sharing the window build it once. */
        int generation = -1;

        Section(int sx, int sy, int sz)
        {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        public boolean holds(int x, int y, int z)
        {
            return Math.floorDiv(x, SECTION) == this.sx && Math.floorDiv(y, SECTION) == this.sy && Math.floorDiv(z, SECTION) == this.sz;
        }
    }

    /**
     * The regions of the world a scene collected its collision from, and how many boxes came out.
     * Each area is the block its region is centred on, in the scene's own coordinates.
     *
     * <p>Kept by the scene rather than recomputed from the settings, because the settings can be
     * changed while a film is open and the region a body is being judged against has to be the one
     * that was actually built.</p>
     *
     * <p>Along with the sections the blocks went into, empty when there were none: a scene built for
     * the same film after BBS rebuilt its cast stands on the same blocks without collecting them
     * again (see {@link #adopt}). A shape belongs to no world, so it outlives the one it was built
     * for. And with a digest of every box in it, in the order they went in, and of every edit
     * since: two windows with the same digest are the same ground to the last box, which is what a
     * recording kept on disk is checked against (see {@link RecordingStore}).</p>
     */
    public static final class Window
    {
        private final int radius;
        private final int below;
        private final int above;
        private int boxes;
        private final List<int[]> areas;
        /** The areas in world coordinates: [baseX, baseY, baseZ, minY, maxY], as they were collected. */
        private final List<int[]> accepted;
        private final List<Section> sections;
        private byte[] ground;
        private final double originX;
        private final double originY;
        private final double originZ;

        Window(int radius, int below, int above, int boxes, List<int[]> areas, List<int[]> accepted, List<Section> sections, byte[] ground, double originX, double originY, double originZ)
        {
            this.radius = radius;
            this.below = below;
            this.above = above;
            this.boxes = boxes;
            this.areas = areas;
            this.accepted = accepted;
            this.sections = sections;
            this.ground = ground;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
        }

        public int radius()
        {
            return this.radius;
        }

        public int below()
        {
            return this.below;
        }

        public int above()
        {
            return this.above;
        }

        public int boxes()
        {
            return this.boxes;
        }

        public List<int[]> areas()
        {
            return this.areas;
        }

        public List<Section> sections()
        {
            return this.sections;
        }

        public byte[] ground()
        {
            return this.ground;
        }

        /**
         * Lets go of the shapes, once no scene will stand on them again: a scene going without
         * handing them on, or a handover nobody took. The bodies standing on them keep their own
         * hold for as long as their world lasts.
         */
        public void release()
        {
            for (Section section : this.sections)
            {
                JoltRelease.release(section.shape);
                section.shape = null;
            }
        }

        /**
         * Whether every one of {@code centers} sits inside a region of this window with the margin a
         * build would have skipped it by — so a build from those centres would have collected
         * nothing this window lacks. In world coordinates, against the scene's origin.
         */
        public boolean covers(List<double[]> centers, double originX, double originY, double originZ)
        {
            for (double[] center : centers)
            {
                int x = (int) Math.floor(center[0]) - (int) Math.floor(originX);
                int y = (int) Math.floor(center[1]) - (int) Math.floor(originY);
                int z = (int) Math.floor(center[2]) - (int) Math.floor(originZ);

                if (!isCovered(this.areas, x, y, z, this.radius, this.below, this.above))
                {
                    return false;
                }
            }

            return true;
        }

        /**
         * Whether a point in the scene's own coordinates still has blocks under it. Outside every
         * region there is nothing to land on and a body falls forever, which from the viewport is
         * indistinguishable from a body that fell through the floor — telling the two apart is the
         * whole reason this is asked. Approximate at the world's ceiling and bedrock, where a
         * region is clamped to what exists; a distinction that does not matter to the question.
         */
        public boolean contains(double x, double y, double z)
        {
            for (int[] area : this.areas)
            {
                if (x >= area[0] - this.radius && x <= area[0] + this.radius + 1
                    && z >= area[2] - this.radius && z <= area[2] + this.radius + 1
                    && y >= area[1] - this.below && y <= area[1] + this.above + 1)
                {
                    return true;
                }
            }

            return false;
        }

        /** Whether a block of the world (world coordinates) is one this window collected. */
        public boolean collects(int x, int y, int z)
        {
            return claimedBy(this.accepted, this.accepted.size(), x, y, z, this.radius);
        }

        /** The index of the section holding a block of the world, or -1 when no section does. */
        public int sectionOf(int x, int y, int z)
        {
            for (int i = 0; i < this.sections.size(); i++)
            {
                if (this.sections.get(i).holds(x, y, z))
                {
                    return i;
                }
            }

            return -1;
        }

        /** The shape section {@code index} stands on after the batch of edits {@code generation}: built for it the first time it is asked, the same shape after. */
        public ConstShape shapeFor(World world, int index, int generation)
        {
            Section section = this.sections.get(index);

            if (section.generation != generation)
            {
                section.generation = generation;

                return this.rebuild(world, index);
            }

            return section.shape;
        }

        /**
         * Builds section {@code index} again from the world as it is now and keeps the new shape,
         * letting go of the old one; returns the new shape for the caller's own body, or null when
         * it could not be built. The digest takes the edit in, so a recording kept on disk is no
         * longer taken for this ground.
         */
        public ConstShape rebuild(World world, int index)
        {
            Section section = this.sections.get(index);
            Fingerprint edit = new Fingerprint().put(this.ground).put(section.sx).put(section.sy).put(section.sz);
            StaticCompoundShapeSettings compound = new StaticCompoundShapeSettings();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            BlockPos.Mutable probe = new BlockPos.Mutable();
            int boxes = 0;
            int x0 = section.sx * SECTION;
            int y0 = section.sy * SECTION;
            int z0 = section.sz * SECTION;

            for (int y = y0; y < y0 + SECTION; y++)
            {
                for (int x = x0; x < x0 + SECTION; x++)
                {
                    for (int z = z0; z < z0 + SECTION; z++)
                    {
                        if (!this.collects(x, y, z))
                        {
                            continue;
                        }

                        pos.set(x, y, z);

                        BlockState state = world.getBlockState(pos);

                        if (state.isAir())
                        {
                            continue;
                        }

                        VoxelShape shape = state.getCollisionShape(world, pos);

                        if (shape.isEmpty() || (Block.isShapeFullCube(shape) && isBuried(world, probe, x, y, z)))
                        {
                            continue;
                        }

                        for (Box box : shape.getBoundingBoxes())
                        {
                            if (addBox(compound, edit, box, x - this.originX, y - this.originY, z - this.originZ))
                            {
                                boxes += 1;
                            }
                        }
                    }
                }
            }

            if (boxes == 0)
            {
                addPlaceholder(compound, section, world, this.originX, this.originY, this.originZ);
            }

            ShapeResult result = compound.create();

            if (result.hasError())
            {
                BBSPhysics.LOGGER.error("Could not rebuild a section of world collision: {}", result.getError());
                JoltRelease.release(result, compound);

                return null;
            }

            ConstShape shape = JoltRelease.take(result);

            JoltRelease.release(compound, section.shape);
            this.boxes += boxes - section.boxes;
            section.shape = shape;
            section.boxes = boxes;
            this.ground = edit.put(boxes).digest();

            return shape;
        }
    }

    /**
     * Jolt rounds a box's corners by this much to make contacts cheaper, and the radius may not
     * exceed the box's smallest half extent — thin blocks like pressure plates are thinner than
     * the default, so it is clamped per shape.
     */
    private static final float CONVEX_RADIUS = 0.05F;

    /** Thinner than this and the box is not worth handing to the solver. */
    private static final float MIN_HALF_EXTENT = 0.005F;

    /** How far under the world's floor an empty section keeps its placeholder, where nothing ever reaches. */
    private static final int PLACEHOLDER_DEPTH = 256;

    private WorldCollider()
    {}

    /**
     * Adds the world's collision around {@code centers} to the scene — the first centre is the
     * scene's origin and is always collected; the rest are the starting spots of whatever else the
     * film simulates. A centre already covered by an earlier region, with a quarter-radius to
     * spare, brings nothing of its own and is skipped; a block two overlapping regions both reach
     * is collected once. How far each region reaches is the film's — see {@link
     * mchorse.bbs_mod.physics.engine.FilmPhysics#worldRadius}.
     *
     * @param bodies where the ids of the bodies made are put, one per section in the window's order
     * @return the regions that were collected, with the number of boxes in them — zero boxes
     *         meaning there was nothing solid to add and no body was created
     */
    public static Window build(PhysicsWorld physics, World world, double originX, double originY, double originZ, List<double[]> centers,
        int radius, int below, int above, List<Integer> bodies)
    {
        List<int[]> areas = new ArrayList<>(1);
        Fingerprint ground = new Fingerprint().put(radius).put(below).put(above);

        if (world == null)
        {
            return new Window(radius, below, above, 0, areas, new ArrayList<>(0), new ArrayList<>(0), ground.put("no world").digest(), originX, originY, originZ);
        }

        /* Which centres deserve a region of their own: [baseX, baseY, baseZ, minY, maxY] each. */
        List<int[]> accepted = new ArrayList<>(1);

        for (double[] center : centers)
        {
            int baseX = (int) Math.floor(center[0]);
            int baseY = (int) Math.floor(center[1]);
            int baseZ = (int) Math.floor(center[2]);

            if (isCovered(accepted, baseX, baseY, baseZ, radius, below, above))
            {
                continue;
            }

            int minY = Math.max(baseY - below, world.getBottomY());
            int maxY = Math.min(baseY + above, world.getTopY() - 1);

            if (minY <= maxY)
            {
                accepted.add(new int[] {baseX, baseY, baseZ, minY, maxY});
            }
        }

        if (accepted.isEmpty())
        {
            return new Window(radius, below, above, 0, areas, accepted, new ArrayList<>(0), ground.put("nothing accepted").digest(), originX, originY, originZ);
        }

        /* Every section the areas reach, in a fixed order, each with its own compound */
        Map<Long, Section> sections = new LinkedHashMap<>();
        Map<Long, StaticCompoundShapeSettings> compounds = new LinkedHashMap<>();

        for (int[] area : accepted)
        {
            for (int sy = Math.floorDiv(area[3], SECTION); sy <= Math.floorDiv(area[4], SECTION); sy++)
            {
                for (int sx = Math.floorDiv(area[0] - radius, SECTION); sx <= Math.floorDiv(area[0] + radius, SECTION); sx++)
                {
                    for (int sz = Math.floorDiv(area[2] - radius, SECTION); sz <= Math.floorDiv(area[2] + radius, SECTION); sz++)
                    {
                        long key = key(sx, sy, sz);

                        if (!sections.containsKey(key))
                        {
                            sections.put(key, new Section(sx, sy, sz));
                            compounds.put(key, new StaticCompoundShapeSettings());
                        }
                    }
                }
            }
        }

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable probe = new BlockPos.Mutable();
        int boxes = 0;

        for (int at = 0; at < accepted.size(); at++)
        {
            int[] area = accepted.get(at);
            int baseX = area[0];
            int baseZ = area[2];
            int minY = area[3];
            int maxY = area[4];

            int spanX = radius * 2 + 1;
            int spanY = maxY - minY + 1;
            int spanZ = radius * 2 + 1;

            /* Which blocks of the region are full cubes, measured once. The burial test asks about
             * six neighbours per block, and asking the world for them would re-read and re-shape
             * most of the region six times over — a million lookups for a region this size, every
             * time a scene is built, which in the editor is every change to the cast. */
            boolean[] full = new boolean[spanX * spanY * spanZ];

            for (int y = minY; y <= maxY; y++)
            {
                for (int x = baseX - radius; x <= baseX + radius; x++)
                {
                    for (int z = baseZ - radius; z <= baseZ + radius; z++)
                    {
                        pos.set(x, y, z);

                        BlockState state = world.getBlockState(pos);

                        if (state.isAir())
                        {
                            continue;
                        }

                        full[index(x - baseX, y - minY, z - baseZ, spanX, spanZ, radius)] = Block.isShapeFullCube(state.getCollisionShape(world, pos));
                    }
                }
            }

            for (int y = minY; y <= maxY; y++)
            {
                for (int x = baseX - radius; x <= baseX + radius; x++)
                {
                    for (int z = baseZ - radius; z <= baseZ + radius; z++)
                    {
                        /* An earlier region already collected this block: once is enough. */
                        if (claimedBy(accepted, at, x, y, z, radius))
                        {
                            continue;
                        }

                        if (full[index(x - baseX, y - minY, z - baseZ, spanX, spanZ, radius)]
                            && isBuried(world, probe, full, baseX, minY, baseZ, x, y, z, spanX, spanY, spanZ, radius))
                        {
                            continue;
                        }

                        pos.set(x, y, z);

                        BlockState state = world.getBlockState(pos);

                        if (state.isAir())
                        {
                            continue;
                        }

                        VoxelShape shape = state.getCollisionShape(world, pos);

                        if (shape.isEmpty())
                        {
                            continue;
                        }

                        long key = key(Math.floorDiv(x, SECTION), Math.floorDiv(y, SECTION), Math.floorDiv(z, SECTION));
                        StaticCompoundShapeSettings compound = compounds.get(key);
                        Section section = sections.get(key);

                        for (Box box : shape.getBoundingBoxes())
                        {
                            if (addBox(compound, ground, box, x - originX, y - originY, z - originZ))
                            {
                                boxes += 1;
                                section.boxes += 1;
                            }
                        }
                    }
                }
            }

            areas.add(new int[] {baseX - (int) Math.floor(originX), area[1] - (int) Math.floor(originY), baseZ - (int) Math.floor(originZ)});
        }

        if (boxes == 0)
        {
            for (StaticCompoundShapeSettings compound : compounds.values())
            {
                JoltRelease.release(compound);
            }

            return new Window(radius, below, above, 0, areas, accepted, new ArrayList<>(0), ground.put("no boxes").digest(), originX, originY, originZ);
        }

        List<Section> list = new ArrayList<>(sections.size());

        for (Map.Entry<Long, Section> entry : sections.entrySet())
        {
            Section section = entry.getValue();
            StaticCompoundShapeSettings compound = compounds.get(entry.getKey());

            if (section.boxes == 0)
            {
                addPlaceholder(compound, section, world, originX, originY, originZ);
            }

            ShapeResult result = compound.create();

            if (result.hasError())
            {
                BBSPhysics.LOGGER.error("Could not build world collision for a physics scene: {}", result.getError());
                JoltRelease.release(result, compound);

                for (Section done : list)
                {
                    JoltRelease.release(done.shape);
                }

                return new Window(radius, below, above, 0, new ArrayList<>(0), accepted, new ArrayList<>(0), ground.put("failed").digest(), originX, originY, originZ);
            }

            /* A hold of its own on the compound, kept by the window for as long as some scene stands
             * on it (see Window#release); the result and the settings are done with. */
            section.shape = JoltRelease.take(result);
            JoltRelease.release(compound);
            list.add(section);
        }

        for (Section section : list)
        {
            bodies.add(addGround(physics, section.shape));
        }

        int[] first = accepted.get(0);

        BBSPhysics.LOGGER.debug("World collision built from {} boxes in {} area(s) and {} sections, the first around ({}, {}, {}).",
            boxes, accepted.size(), list.size(), first[0], first[1], first[2]);

        return new Window(radius, below, above, boxes, areas, accepted, list, ground.put(boxes).digest(), originX, originY, originZ);
    }

    /**
     * Stands a scene on the blocks another scene of the same film collected — the same bodies, made
     * the same way, without reading a block of the world. Only for a scene centred where that one
     * was, since the shapes sit around its origin.
     */
    public static Window adopt(PhysicsWorld physics, Window window, List<Integer> bodies)
    {
        for (Section section : window.sections)
        {
            bodies.add(addGround(physics, section.shape));
        }

        return window;
    }

    /** One body at the scene's origin; every block of its section sits inside it as a child shape. */
    private static int addGround(PhysicsWorld physics, ConstShape shape)
    {
        BodyCreationSettings settings = new BodyCreationSettings(shape, new RVec3(0D, 0D, 0D), Quat.sIdentity(), EMotionType.Static, PhysicsLayers.STATIC);

        settings.setFriction(0.6F);

        int id = physics.getBodies().createAndAddBody(settings, EActivation.DontActivate);

        JoltRelease.release(settings);

        return id;
    }

    /** What an empty section stands on: one small box far under the world, where nothing ever goes. */
    private static void addPlaceholder(StaticCompoundShapeSettings compound, Section section, World world, double originX, double originY, double originZ)
    {
        BoxShape leaf = new BoxShape(new Vec3(0.25F, 0.25F, 0.25F), 0.05F);

        compound.addShape(new Vec3(
            (float) (section.sx * SECTION + SECTION / 2 - originX),
            (float) (world.getBottomY() - PLACEHOLDER_DEPTH - originY),
            (float) (section.sz * SECTION + SECTION / 2 - originZ)), Quat.sIdentity(), leaf);
        JoltRelease.release(leaf);
    }

    private static long key(int sx, int sy, int sz)
    {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sy & 0xFFFFF) << 22) | (sz & 0x3FFFFF);
    }

    /**
     * Whether this centre sits inside an already-accepted region with a quarter of the radius to
     * spare — close enough that a region of its own would be almost entirely the same blocks. The
     * margin is what keeps something standing near a region's edge from losing the ground just
     * beyond it.
     */
    private static boolean isCovered(List<int[]> accepted, int baseX, int baseY, int baseZ, int radius, int below, int above)
    {
        int margin = radius / 4;

        for (int[] area : accepted)
        {
            if (Math.abs(baseX - area[0]) <= radius - margin
                && Math.abs(baseZ - area[2]) <= radius - margin
                && baseY - area[1] >= -(below - below / 4)
                && baseY - area[1] <= above - above / 4)
            {
                return true;
            }
        }

        return false;
    }

    /** Whether a block is inside one of the first {@code count} regions — for {@code count} = the region at hand, already collected by an earlier one. */
    private static boolean claimedBy(List<int[]> accepted, int count, int x, int y, int z, int radius)
    {
        for (int i = 0; i < count; i++)
        {
            int[] area = accepted.get(i);

            if (Math.abs(x - area[0]) <= radius && Math.abs(z - area[2]) <= radius && y >= area[3] && y <= area[4])
            {
                return true;
            }
        }

        return false;
    }

    private static int index(int x, int y, int z, int spanX, int spanZ, int radius)
    {
        return ((y * spanX) + (x + radius)) * spanZ + (z + radius);
    }

    /**
     * Whether every side of this block is covered by a full cube, which makes it unreachable and
     * so not worth simulating. Checked only for blocks that are full cubes themselves — a slab or
     * a fence is part of the surface however it is surrounded.
     *
     * <p>Answered from the measured grid, and only from the world for a neighbour just outside it
     * — the region's own outer shell, a few per cent of it. Treating those as open instead would
     * wall the whole region in boxes nothing can ever reach.</p>
     */
    private static boolean isBuried(World world, BlockPos.Mutable probe, boolean[] full, int baseX, int minY, int baseZ, int x, int y, int z, int spanX, int spanY, int spanZ, int radius)
    {
        for (Direction direction : Direction.values())
        {
            int nx = x + direction.getOffsetX();
            int ny = y + direction.getOffsetY();
            int nz = z + direction.getOffsetZ();

            int lx = nx - baseX;
            int ly = ny - minY;
            int lz = nz - baseZ;

            boolean neighbour;

            if (lx < -radius || lx > radius || lz < -radius || lz > radius || ly < 0 || ly >= spanY)
            {
                probe.set(nx, ny, nz);

                neighbour = Block.isShapeFullCube(world.getBlockState(probe).getCollisionShape(world, probe));
            }
            else
            {
                neighbour = full[index(lx, ly, lz, spanX, spanZ, radius)];
            }

            if (!neighbour)
            {
                return false;
            }
        }

        return true;
    }

    /** The same test against the world alone, for one section rebuilt after an edit. */
    private static boolean isBuried(World world, BlockPos.Mutable probe, int x, int y, int z)
    {
        for (Direction direction : Direction.values())
        {
            probe.set(x + direction.getOffsetX(), y + direction.getOffsetY(), z + direction.getOffsetZ());

            if (!Block.isShapeFullCube(world.getBlockState(probe).getCollisionShape(world, probe)))
            {
                return false;
            }
        }

        return true;
    }

    private static boolean addBox(StaticCompoundShapeSettings compound, Fingerprint ground, Box box, double blockX, double blockY, double blockZ)
    {
        float halfX = (float) (box.maxX - box.minX) * 0.5F;
        float halfY = (float) (box.maxY - box.minY) * 0.5F;
        float halfZ = (float) (box.maxZ - box.minZ) * 0.5F;

        if (halfX < MIN_HALF_EXTENT || halfY < MIN_HALF_EXTENT || halfZ < MIN_HALF_EXTENT)
        {
            return false;
        }

        float radius = Math.min(CONVEX_RADIUS, Math.min(halfX, Math.min(halfY, halfZ)));

        float centerX = (float) (blockX + (box.minX + box.maxX) * 0.5D);
        float centerY = (float) (blockY + (box.minY + box.maxY) * 0.5D);
        float centerZ = (float) (blockZ + (box.minZ + box.maxZ) * 0.5D);

        ground.put(centerX).put(centerY).put(centerZ).put(halfX).put(halfY).put(halfZ).put(radius);

        BoxShape leaf = new BoxShape(new Vec3(halfX, halfY, halfZ), radius);

        /* The compound holds every box it is given; thousands of them a world, each kept alive by
         * this hold alone once the world was gone, until it was let go of here. */
        compound.addShape(new Vec3(centerX, centerY, centerZ), Quat.sIdentity(), leaf);
        JoltRelease.release(leaf);

        return true;
    }
}
