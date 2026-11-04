package mchorse.bbs_mod.audio.occlusion;

import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Calculates acoustic occlusion (sound muffling) between the listener / camera
 * and a 3D audio source by raymarching intervening blocks and accumulating
 * physical transmission loss coefficients.
 */
public class AudioOcclusionManager
{
    private static final Map<Block, Float> ACOUSTIC_MATRIX = new HashMap<>();

    static
    {
        /* Explicit acoustic material definitions */
        ACOUSTIC_MATRIX.put(Blocks.GLASS, 0.15F);
        ACOUSTIC_MATRIX.put(Blocks.TINTED_GLASS, 0.25F);
        ACOUSTIC_MATRIX.put(Blocks.GLASS_PANE, 0.12F);

        /* Iron / heavy metals */
        ACOUSTIC_MATRIX.put(Blocks.IRON_BLOCK, 0.85F);
        ACOUSTIC_MATRIX.put(Blocks.IRON_DOOR, 0.65F);
        ACOUSTIC_MATRIX.put(Blocks.IRON_TRAPDOOR, 0.60F);
        ACOUSTIC_MATRIX.put(Blocks.ANVIL, 0.90F);
        ACOUSTIC_MATRIX.put(Blocks.CHIPPED_ANVIL, 0.88F);
        ACOUSTIC_MATRIX.put(Blocks.DAMAGED_ANVIL, 0.85F);

        /* Heavy stone and obsidian */
        ACOUSTIC_MATRIX.put(Blocks.OBSIDIAN, 0.90F);
        ACOUSTIC_MATRIX.put(Blocks.CRYING_OBSIDIAN, 0.88F);
        ACOUSTIC_MATRIX.put(Blocks.BEDROCK, 0.98F);
    }

    /**
     * Compute occlusion from current client listener position to sound coordinate.
     */
    public static float calculateOcclusion(Vec3d soundPos)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || soundPos == null)
        {
            return 0.0F;
        }

        Vec3d listenerPos = null;

        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null)
        {
            listenerPos = mc.gameRenderer.getCamera().getPos();
        }
        else if (mc.player != null)
        {
            listenerPos = mc.player.getEyePos();
        }

        if (listenerPos == null)
        {
            return 0.0F;
        }

        return calculateOcclusion(mc.world, listenerPos, soundPos);
    }

    /**
     * Compute occlusion from a specific listener coordinate to sound coordinate in the client world.
     */
    public static float calculateOcclusion(Vec3d listener, Vec3d soundPos)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || listener == null || soundPos == null)
        {
            return 0.0F;
        }

        return calculateOcclusion(mc.world, listener, soundPos);
    }

    /**
     * Calculate acoustic obstruction between listener and sound origin across blocks.
     *
     * @param world Target world
     * @param listener Camera or listener point
     * @param soundPos 3D sound location
     * @return Normalized occlusion factor in [0.0, 1.0], where 0.0 is open air and 1.0 is heavily muffled.
     */
    public static float calculateOcclusion(World world, Vec3d listener, Vec3d soundPos)
    {
        if (world == null || listener == null || soundPos == null)
        {
            return 0.0F;
        }

        double distance = listener.distanceTo(soundPos);

        if (distance < 0.1D)
        {
            return 0.0F;
        }

        /* Raymarch intervening voxels without duplicate BlockPos hits. The last stretch at each end is
         * left out: a source standing on the ground sits exactly on a block's top face, and the
         * slightest rounding put the ray's last sample either in the air above or in the block below
         * - so the muffle snapped on and off as the head turned. Only what lies between the two
         * points can stand between them. */
        Set<BlockPos> visited = new LinkedHashSet<>();
        double stepSize = 0.35D;
        double margin = Math.min(0.3D, distance * 0.25D) / distance;
        int steps = (int) Math.ceil(distance / stepSize);

        for (int i = 0; i <= steps; i++)
        {
            double progress = margin + (steps == 0 ? 0.0D : (double) i / steps) * (1.0D - margin * 2.0D);
            double x = listener.x + (soundPos.x - listener.x) * progress;
            double y = listener.y + (soundPos.y - listener.y) * progress;
            double z = listener.z + (soundPos.z - listener.z) * progress;

            visited.add(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        float totalAbsorption = 0.0F;

        for (BlockPos pos : visited)
        {
            BlockState state = world.getBlockState(pos);

            if (state.isAir())
            {
                continue;
            }

            float coefficient = getBlockAcousticCoefficient(world, pos, state);

            totalAbsorption += coefficient;

            /* Early exit if sound is already completely blocked */
            if (totalAbsorption >= 3.5F)
            {
                totalAbsorption = 3.5F;
                break;
            }
        }

        /* Exponential acoustic attenuation: 1 - exp(-total) provides smooth normalized [0.0, 1.0] response */
        float normalized = 1.0F - (float) Math.exp(-totalAbsorption);

        return MathUtils.clamp(normalized, 0.0F, 1.0F);
    }

    /**
     * Determine acoustic absorption coefficient for a given block.
     * Incorporates acoustic matrix lookups, material properties, and logarithmic hardness fallback.
     */
    public static float getBlockAcousticCoefficient(BlockView world, BlockPos pos, BlockState state)
    {
        Block block = state.getBlock();

        /* 1. Direct matrix hit */
        Float mapped = ACOUSTIC_MATRIX.get(block);

        if (mapped != null)
        {
            return mapped;
        }

        /* 2. Glass varieties (high transmission / low muffling) */
        if (block instanceof TransparentBlock || block instanceof StainedGlassBlock
            || block instanceof PaneBlock || block instanceof StainedGlassPaneBlock
            || block == Blocks.GLASS)
        {
            return 0.15F;
        }

        /* 3. Wool / Carpets (very high acoustic damping & sound absorption) */
        if (state.isIn(BlockTags.WOOL) || state.isIn(BlockTags.WOOL_CARPETS))
        {
            return 0.70F;
        }

        /* 4. Water / Fluids */
        if (state.getFluidState() != null && state.getFluidState().isIn(FluidTags.WATER))
        {
            return 0.35F;
        }

        /* 5. Doors, trapdoors and fence gates */
        if (block instanceof DoorBlock)
        {
            boolean open = state.contains(DoorBlock.OPEN) && state.get(DoorBlock.OPEN);

            if (open)
            {
                return 0.05F;
            }

            return block == Blocks.IRON_DOOR ? 0.65F : 0.30F;
        }

        if (block instanceof TrapdoorBlock)
        {
            boolean open = state.contains(TrapdoorBlock.OPEN) && state.get(TrapdoorBlock.OPEN);

            if (open)
            {
                return 0.05F;
            }

            return block == Blocks.IRON_TRAPDOOR ? 0.60F : 0.25F;
        }

        if (block instanceof FenceGateBlock)
        {
            boolean open = state.contains(FenceGateBlock.OPEN) && state.get(FenceGateBlock.OPEN);

            return open ? 0.03F : 0.20F;
        }

        /* 6. Foliage / Leaves */
        if (block instanceof LeavesBlock || state.isIn(BlockTags.LEAVES))
        {
            return 0.08F;
        }

        /* 7. Fallback: Logarithmic hardness scaling for undefined solid blocks */
        float hardness = state.getHardness(world, pos);

        if (hardness < 0.0F)
        {
            /* Indestructible blocks (bedrock, portals) */
            return 0.95F;
        }
        else if (hardness == 0.0F)
        {
            /* Pass-through or instant-break blocks (torch, flowers, grass) */
            return 0.02F;
        }

        /* Logarithmic curve: log(1 + hardness) / 3.0 */
        float logHardness = (float) (Math.log(1.0D + hardness) / 3.0D);

        return MathUtils.clamp(logHardness, 0.05F, 0.85F);
    }
}
