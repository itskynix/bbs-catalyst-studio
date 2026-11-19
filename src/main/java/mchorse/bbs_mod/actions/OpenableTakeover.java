package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.utils.PermissionUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * The server's half of turning a door, trapdoor, fence gate or chest into a film actor: the client has
 * put an actor where the block was, and the block itself is taken out of the world here, so the two are
 * never seen on top of each other. Only for someone allowed to edit films, and only near them.
 */
public final class OpenableTakeover
{
    private static final int MAX_BLOCKS = 4;
    private static final double REACH = 16D;

    private OpenableTakeover()
    {}

    public static void receive(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        int count = Math.min(MAX_BLOCKS, buf.readVarInt());
        BlockPos[] positions = new BlockPos[count];

        for (int i = 0; i < count; i++)
        {
            positions[i] = buf.readBlockPos();
        }

        server.execute(() ->
        {
            if (!PermissionUtils.arePanelsAllowed(server, player))
            {
                return;
            }

            ServerWorld world = player.getServerWorld();

            for (BlockPos pos : positions)
            {
                if (pos.getSquaredDistance(player.getPos()) > REACH * REACH || world.getBlockState(pos).isAir())
                {
                    continue;
                }

                /* No neighbour updates: the other half of a door must not break off and drop on its own */
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
            }
        });
    }
}
