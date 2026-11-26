package mchorse.bbs_mod.triggers;

import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Where the trigger box, an entity and a block are, how they are turned and how big they are, in
 * the world or relative to the box. The Get Location, Rotation, Scale and Transform nodes of a flow
 * read it, and the Java the code view writes calls the very same methods.
 *
 * <p>The box's own frame: its anchor is the middle of its floor (the block's centre across, the
 * block's bottom, moved by the box's offset), it is not turned (a trigger box is always lined up with
 * the world's axes), and its size is its width, height and depth. So, relative to the box:</p>
 * <ul>
 *     <li>a location is how far from the anchor, in blocks (a block's own location counts from the
 *     middle of its bottom, so the block under the anchor reads 0 0 0, the one east of it 1 0 0);</li>
 *     <li>a rotation is the rotation less the box's, which is none, so it reads as in the world;</li>
 *     <li>a scale is the size over the box's size (an entity half as tall as the box reads 0.5 in y).</li>
 * </ul>
 * <p>The box itself, relative to its own start: its location is its offset from its block, and its
 * scale its size over the size a new box has (3 2 3), so an untouched box reads 1 1 1.</p>
 *
 * <p>A rotation is pitch, yaw and roll in degrees, as x y z. An entity's is where it looks; a block's
 * comes from its facing, its rotation (signs, banners, heads) or its axis (logs, pillars).</p>
 */
public final class FlowTransforms
{
    /** The size a trigger box has when placed, which its relative scale counts from. */
    public static final Vec3d DEFAULT_SIZE = new Vec3d(3, 2, 3);

    private FlowTransforms()
    {}

    /* The trigger box */

    public static Vec3d triggerLocation(TriggerBoxBlockEntity box, boolean relative)
    {
        if (box == null)
        {
            return Vec3d.ZERO;
        }

        return relative ? new Vec3d(box.offsetX, box.offsetY, box.offsetZ) : anchor(box);
    }

    public static Vec3d triggerRotation(TriggerBoxBlockEntity box, boolean relative)
    {
        return Vec3d.ZERO;
    }

    public static Vec3d triggerScale(TriggerBoxBlockEntity box, boolean relative)
    {
        Vec3d size = size(box);

        return relative ? divide(size, DEFAULT_SIZE) : size;
    }

    /* An entity: itself, or anything FlowEntities resolves (@entity, a name, a UUID, a selector) */

    public static Vec3d entityLocation(TriggerBoxBlockEntity box, Entity source, Object value, boolean relative)
    {
        Entity entity = entity(box, source, value);

        if (entity == null)
        {
            return Vec3d.ZERO;
        }

        return relative ? entity.getPos().subtract(anchor(box)) : entity.getPos();
    }

    public static Vec3d entityRotation(TriggerBoxBlockEntity box, Entity source, Object value, boolean relative)
    {
        Entity entity = entity(box, source, value);

        if (entity == null)
        {
            return Vec3d.ZERO;
        }

        Vec3d rotation = new Vec3d(entity.getPitch(), MathHelper.wrapDegrees(entity.getYaw()), 0);

        return relative ? rotation.subtract(triggerRotation(box, false)) : rotation;
    }

    public static Vec3d entityScale(TriggerBoxBlockEntity box, Entity source, Object value, boolean relative)
    {
        Entity entity = entity(box, source, value);

        if (entity == null)
        {
            return Vec3d.ZERO;
        }

        /* The size the entity stands in: a baby, a small slime or a crouching player each read smaller */
        Vec3d size = new Vec3d(entity.getWidth(), entity.getHeight(), entity.getWidth());

        return relative ? divide(size, size(box)) : size;
    }

    /* A block: the one at a position ("x y z", ~ counting from the source, or a Vec3d) */

    public static Vec3d blockLocation(TriggerBoxBlockEntity box, Entity source, Object position, boolean relative)
    {
        BlockPos pos = BlockPos.ofFloored(position(box, source, position));

        if (relative)
        {
            return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D).subtract(anchor(box));
        }

        return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3d blockRotation(TriggerBoxBlockEntity box, Entity source, Object position, boolean relative)
    {
        World world = world(box, source);

        if (world == null)
        {
            return Vec3d.ZERO;
        }

        Vec3d rotation = rotationOf(world.getBlockState(BlockPos.ofFloored(position(box, source, position))));

        return relative ? rotation.subtract(triggerRotation(box, false)) : rotation;
    }

    public static Vec3d blockScale(TriggerBoxBlockEntity box, Entity source, Object position, boolean relative)
    {
        World world = world(box, source);

        if (world == null)
        {
            return Vec3d.ZERO;
        }

        BlockPos pos = BlockPos.ofFloored(position(box, source, position));
        VoxelShape shape = world.getBlockState(pos).getOutlineShape(world, pos);
        Vec3d size = Vec3d.ZERO;

        if (!shape.isEmpty())
        {
            /* The block's outline: a full block 1 1 1, a slab 1 0.5 1, air nothing */
            Box bounds = shape.getBoundingBox();

            size = new Vec3d(bounds.getLengthX(), bounds.getLengthY(), bounds.getLengthZ());
        }

        return relative ? divide(size, size(box)) : size;
    }

    /** A block's rotation from its state: pitch, yaw and roll in degrees. */
    public static Vec3d rotationOf(BlockState state)
    {
        Direction facing = null;

        if (state.contains(Properties.HORIZONTAL_FACING))
        {
            facing = state.get(Properties.HORIZONTAL_FACING);
        }
        else if (state.contains(Properties.FACING))
        {
            facing = state.get(Properties.FACING);
        }
        else if (state.contains(Properties.HOPPER_FACING))
        {
            facing = state.get(Properties.HOPPER_FACING);
        }

        if (facing != null)
        {
            if (facing == Direction.UP)
            {
                return new Vec3d(-90, 0, 0);
            }

            if (facing == Direction.DOWN)
            {
                return new Vec3d(90, 0, 0);
            }

            return new Vec3d(0, MathHelper.wrapDegrees(facing.asRotation()), 0);
        }

        if (state.contains(Properties.ROTATION))
        {
            return new Vec3d(0, MathHelper.wrapDegrees(state.get(Properties.ROTATION) * 22.5F), 0);
        }

        if (state.contains(Properties.AXIS))
        {
            return switch (state.get(Properties.AXIS))
            {
                case X -> new Vec3d(0, 0, 90);
                case Z -> new Vec3d(90, 0, 0);
                default -> Vec3d.ZERO;
            };
        }

        return Vec3d.ZERO;
    }

    /* The box's frame */

    /** The middle of the box's floor, where its relative locations count from. */
    public static Vec3d anchor(TriggerBoxBlockEntity box)
    {
        if (box == null)
        {
            return Vec3d.ZERO;
        }

        BlockPos pos = box.getPos();

        return new Vec3d(pos.getX() + 0.5D + box.offsetX, pos.getY() + box.offsetY, pos.getZ() + 0.5D + box.offsetZ);
    }

    public static Vec3d size(TriggerBoxBlockEntity box)
    {
        return box == null ? DEFAULT_SIZE : new Vec3d(box.boxWidth, box.boxHeight, box.boxDepth);
    }

    private static Entity entity(TriggerBoxBlockEntity box, Entity source, Object value)
    {
        /* Nothing found is nothing: not the source, as an empty text value would read */
        if (value == null)
        {
            return null;
        }

        ServerWorld world = box != null && box.getWorld() instanceof ServerWorld w ? w : null;

        return FlowEntities.resolveEntity(source, world, box == null ? null : Vec3d.ofCenter(box.getPos()), value);
    }

    private static World world(TriggerBoxBlockEntity box, Entity source)
    {
        if (box != null && box.getWorld() != null)
        {
            return box.getWorld();
        }

        return source == null ? null : source.getWorld();
    }

    /** A position as the box reads one: a Vec3d, or "x y z" where ~ counts from the source, else the box. */
    private static Vec3d position(TriggerBoxBlockEntity box, Entity source, Object value)
    {
        if (value instanceof Vec3d vec)
        {
            return vec;
        }

        Vec3d base = source != null ? source.getPos() : box != null ? Vec3d.ofCenter(box.getPos()) : Vec3d.ZERO;
        String[] split = (value == null ? "" : String.valueOf(value).trim()).split("[ ,]+");
        double[] xyz = {base.x, base.y, base.z};

        for (int i = 0; i < 3 && i < split.length; i++)
        {
            String part = split[i];

            if (part.isEmpty())
            {
                continue;
            }

            xyz[i] = part.startsWith("~") ? xyz[i] + FlowValues.parse(part.substring(1), 0) : FlowValues.parse(part, xyz[i]);
        }

        return new Vec3d(xyz[0], xyz[1], xyz[2]);
    }

    private static Vec3d divide(Vec3d a, Vec3d b)
    {
        return new Vec3d(b.x == 0 ? 0 : a.x / b.x, b.y == 0 ? 0 : a.y / b.y, b.z == 0 ? 0 : a.z / b.z);
    }
}
