package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.utils.PermissionUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
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
            java.util.Set<BlockPos> all = new java.util.LinkedHashSet<>();

            /* The whole of it, whatever the client sent: both halves of a door, both sides of a double chest */
            for (BlockPos pos : positions)
            {
                if (pos.getSquaredDistance(player.getPos()) > REACH * REACH)
                {
                    continue;
                }

                BlockState state = world.getBlockState(pos);

                all.add(pos);

                if (state.getBlock() instanceof DoorBlock)
                {
                    all.add(state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down());
                }
                else if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE)
                {
                    all.add(pos.offset(ChestBlock.getFacing(state)));
                }
            }

            for (BlockPos pos : all)
            {
                if (world.getBlockState(pos).isAir())
                {
                    continue;
                }

                /* The block entity first, so a chest has nothing left to scatter: no items drop */
                world.removeBlockEntity(pos);

                /* No neighbour updates (the other half must not break off on its own and drop), no drops, told to every client at once */
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
                player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, pos));
            }
        });
    }
}
