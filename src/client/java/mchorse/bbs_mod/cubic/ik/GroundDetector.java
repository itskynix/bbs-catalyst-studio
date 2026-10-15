package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finds the real walkable surface under a point of the world by scanning the block collision
 * boxes of its column, so slabs, stairs, snow layers and the like report their true top instead
 * of a block grid height. Used by foot grounding: the IK reads the terrain here and bends the
 * legs onto it.
 *
 * <p>The scan is a pure function of the world and the point — no history — so scrubbing a film
 * or exporting it gives the same legs every time.
 */
public final class GroundDetector
{
    /** How far above the actor's floor a surface may sit and still be stepped onto, blocks. */
    public static final float MAX_STEP_UP = 1F;

    /** How far below the actor's floor a surface may sit and still be reached down to, blocks. */
    public static final float MAX_STEP_DOWN = 1.25F;

    /**
     * Lift of the sole above the surface, blocks. The reach point rides the foot bone's pivot, and
     * the sole it stands for sinks a few pixels under that once the foot is tilted by the solve, so
     * a small margin keeps the heel on top of the block instead of in it (0.05 blocks = 0.8 px).
     */
    public static final float FOOT_OFFSET = 0.05F;

    /** The gap between the actor's floor and the ground under it where the grounding starts to fade out, blocks. */
    private static final float FADE_START = 0.1F;

    /** The gap by which the grounding is fully gone, blocks. */
    private static final float FADE_END = 0.5F;

    private static final double EPS = 1.0e-4;

    /** Ticks over which the grounding eases in after the actor lands. */
    private static final float LANDING_TICKS = 3F;

    /** Taps of the footprint smoothing across one axis (a tent: 1-2-3-2-1) and their spacing, blocks. */
    private static final float[] TAPS = {1F, 2F, 3F, 2F, 1F};
    private static final float TAP_SPACING = 0.15F;

    /**
     * When each actor was last seen airborne, in ticks (age + transition). The only memory of the
     * grounding: it is read as a plain time difference, so a forward playback (the render, the
     * export) lands on the same ramp every time; a clock that runs backwards (a scrub) forgets the
     * landing instead of holding the grounding off.
     */
    private static final Map<IEntity, Float> LAST_AIRBORNE = new WeakHashMap<>();

    /** Time constant of the surface height's glide, ticks: ~63% of a step is climbed in this long. */
    private static final float HEIGHT_TAU = 1.2F;

    /** Gaps in the clock longer than this (a pause, take-off to landing, a cut) re-seed the glide on the spot, ticks. */
    private static final float HEIGHT_GAP = 3F;

    /** How far under the true surface the glide may let the sole sink while it climbs, blocks. */
    public static final float MAX_SINK = 0.12F;

    /** Per actor, per chain: the last glided surface height and the time it was taken at. */
    private static final Map<IEntity, Map<String, float[]>> HEIGHTS = new WeakHashMap<>();

    /**
     * The world frame a grounded solve works in: the world, the model-to-world matrix the render
     * establishes, and its inverse (cached, the solve converts several points).
     */
    public record Context(World world, Matrix4f base, Matrix4f inverse, float influence, IEntity entity, float time)
    {
        /**
         * The surface height a foot should stand on, glided in TIME: an exponential approach to the
         * measured height ({@code 1 − e^(−dt/τ)}, dt in ticks), so a foot crossing a block edge
         * climbs onto the step along a curve instead of snapping to it. Several solves in one frame
         * see the same clock and get the same answer; a clock that runs backwards (a scrub) or leaps
         * ahead re-seeds the glide at the measured height, so playback from any frame is exact.
         */
        public float glide(String key, float target)
        {
            Map<String, float[]> byKey = HEIGHTS.computeIfAbsent(this.entity, (e) -> new HashMap<>());
            float[] state = byKey.get(key);

            if (state == null)
            {
                byKey.put(key, new float[] {this.time, target});

                return target;
            }

            float dt = this.time - state[0];

            if (dt == 0F)
            {
                return state[1];
            }

            if (dt < 0F || dt > HEIGHT_GAP)
            {
                state[1] = target;
            }
            else
            {
                state[1] += (target - state[1]) * (1F - (float) Math.exp(-dt / HEIGHT_TAU));
            }

            state[0] = this.time;

            return state[1];
        }

        /**
         * Null (= no grounding at all) when the actor is airborne or has no ground close below:
         * a jump or a fall must hand the legs back to the FK animation, not reach for a floor. A
         * walking actor on solid ground gets full influence; a short hop fades out smoothly as
         * the gap under it opens, so the legs never snap. Judged from the actor's own origin, a
         * pure function of the world — no history.
         */
        public static Context of(World world, Matrix4f base, IEntity entity, float transition)
        {
            if (world == null || base == null || entity == null)
            {
                return null;
            }

            float time = entity.getAge() + transition;

            if (!entity.isOnGround())
            {
                LAST_AIRBORNE.put(entity, time);

                return null;
            }

            /* Landing damping: the grounding eases in over a few ticks after the touchdown instead of
             * taking the whole step (feet and pelvis drop) in the first grounded frame. */
            float ramp = 1F;
            Float airborne = LAST_AIRBORNE.get(entity);

            if (airborne != null)
            {
                float elapsed = time - airborne;

                if (elapsed < 0F)
                {
                    LAST_AIRBORNE.remove(entity);
                }
                else
                {
                    float u = Math.min(1F, elapsed / LANDING_TICKS);

                    ramp = u * u * (3F - 2F * u);
                }
            }

            Matrix4f inverse = new Matrix4f(base);

            if (Math.abs(inverse.determinant()) < 1.0e-9f)
            {
                return null;
            }

            org.joml.Vector3f origin = base.transformPosition(new org.joml.Vector3f());
            float hit = find(world, origin.x, origin.z, origin.y);

            if (Float.isNaN(hit))
            {
                return null;
            }

            float t = Math.max(0F, Math.min(1F, (origin.y - hit - FADE_START) / (FADE_END - FADE_START)));
            float influence = (1F - t * t * (3F - 2F * t)) * ramp;

            return influence < 0.001F ? null : new Context(world, new Matrix4f(base), inverse.invert(), influence, entity, time);
        }
    }

    private GroundDetector()
    {
    }

    /**
     * {@link #find} smoothed over the foot's footprint: a 5×5 tent-weighted average of the surface
     * heights around the point (taps 0.15 blocks apart, so it spans ±0.3). Across a block edge the
     * height then climbs as a ramp over about 0.6 blocks instead of jumping, and the foot slides up
     * the step as it walks onto it. Neighbours with no surface in the window take the centre's
     * height; no centre, no ground. A pure function of the world and the point.
     */
    public static float findSmooth(World world, double x, double z, double floorY)
    {
        float center = find(world, x, z, floorY);

        if (Float.isNaN(center))
        {
            return Float.NaN;
        }

        double sum = 0;
        double weight = 0;
        int half = TAPS.length / 2;

        for (int i = -half; i <= half; i++)
        {
            for (int j = -half; j <= half; j++)
            {
                float height = i == 0 && j == 0 ? center : find(world, x + i * TAP_SPACING, z + j * TAP_SPACING, floorY);
                float w = TAPS[i + half] * TAPS[j + half];

                sum += (Float.isNaN(height) ? center : height) * w;
                weight += w;
            }
        }

        return (float) (sum / weight);
    }

    /**
     * The highest collision surface under {@code (x, z)} that lies within
     * [{@code floorY − MAX_STEP_DOWN}, {@code floorY + MAX_STEP_UP}]; {@code NaN} when there is
     * none (a pit deeper than the step, a wall taller than it, an unloaded chunk).
     */
    public static float find(World world, double x, double z, double floorY)
    {
        double top = floorY + MAX_STEP_UP + EPS;
        double bottom = floorY - MAX_STEP_DOWN - EPS;
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int by = MathHelper.floor(top); by >= MathHelper.floor(bottom); by--)
        {
            pos.set(bx, by, bz);

            if (!world.isChunkLoaded(pos))
            {
                continue;
            }

            BlockState state = world.getBlockState(pos);

            if (state == null)
            {
                continue;
            }

            VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.absent());

            if (shape.isEmpty())
            {
                continue;
            }

            double best = Double.NaN;

            for (Box box : shape.getBoundingBoxes())
            {
                if (x < bx + box.minX || x > bx + box.maxX || z < bz + box.minZ || z > bz + box.maxZ)
                {
                    continue;
                }

                double surface = by + box.maxY;

                if (surface > top || surface < bottom)
                {
                    continue;
                }

                best = Double.isNaN(best) ? surface : Math.max(best, surface);
            }

            if (!Double.isNaN(best))
            {
                return (float) best;
            }
        }

        return Float.NaN;
    }
}
