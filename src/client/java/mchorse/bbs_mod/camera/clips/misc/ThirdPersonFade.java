package mchorse.bbs_mod.camera.clips.misc;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * The thin blocks (leaves, fences, bars, posts) the third person camera looks through right now.
 *
 * <p>The chunk meshes are baked, so a block cannot be drawn half see-through in a mesh: the blocks in
 * the way are left out of the section's rebuild while they are in the way, and the section is rebuilt
 * with them the moment the way clears. A block fades for as long as the camera submits it, a quarter
 * of a second after the last submit it is back. The set is read on the chunk builder threads, so it is
 * swapped as a whole, never edited.</p>
 */
public class ThirdPersonFade
{
    private static final long EXPIRE_MS = 250L;

    private static volatile Set<Long> hidden = Set.of();
    private static long lastSubmit;

    public static boolean isHidden(BlockPos pos)
    {
        Set<Long> set = hidden;

        return !set.isEmpty() && set.contains(pos.asLong());
    }

    /** The camera's current set, called once per applied camera tick, on the main thread. */
    public static void submit(Set<Long> positions)
    {
        lastSubmit = System.currentTimeMillis();

        swap(positions);
    }

    /** Frees the blocks once the camera stopped talking, called every client tick. */
    public static void tick()
    {
        if (!hidden.isEmpty() && System.currentTimeMillis() - lastSubmit > EXPIRE_MS)
        {
            swap(Set.of());
        }
    }

    private static void swap(Set<Long> positions)
    {
        Set<Long> previous = hidden;

        if (previous.equals(positions))
        {
            return;
        }

        hidden = positions.isEmpty() ? Set.of() : Set.copyOf(positions);

        /* Every block that went in or out has its section rebuilt */
        Set<Long> changed = new HashSet<>(previous);

        for (Long l : positions)
        {
            if (!changed.remove(l))
            {
                changed.add(l);
            }
        }

        rebuild(changed);
    }

    private static void rebuild(Set<Long> changed)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null || mc.worldRenderer == null)
        {
            return;
        }

        for (Long l : changed)
        {
            BlockPos pos = BlockPos.fromLong(l);
            BlockState state = world.getBlockState(pos);

            /* Flag 8 asks for the rebuild on the main thread, so the change shows in this frame */
            mc.worldRenderer.updateBlock(world, pos, state, state, 8);
            mc.worldRenderer.scheduleBlockRender(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
