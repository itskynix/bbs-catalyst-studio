package mchorse.bbs_mod.actions;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player's hand did to a door, trapdoor, gate or chest before the click that finished the job:
 * the client moves the openable a share of the way at a time, and when the hold ends it tells the
 * server how it went and then clicks. The recorder turns the click into a clip, and takes this to put
 * in it, so that playing the take back swings the openable the way it was swung.
 */
public final class OpenableSwings
{
    public record Swing(BlockPos pos, float from, float to, int ticks, float angle) {}

    private static final Map<UUID, Swing> PENDING = new ConcurrentHashMap<>();

    private OpenableSwings()
    {}

    public static void receive(ServerPlayerEntity player, PacketByteBuf buf)
    {
        BlockPos pos = buf.readBlockPos();
        float from = buf.readFloat();
        float to = buf.readFloat();
        int ticks = Math.max(0, Math.min(2400, buf.readInt()));
        float angle = Math.max(15F, Math.min(135F, buf.readFloat()));

        if (Float.isFinite(from) && Float.isFinite(to))
        {
            PENDING.put(player.getUuid(), new Swing(pos, Math.max(0F, Math.min(1F, from)), Math.max(0F, Math.min(1F, to)), ticks, angle));
        }
    }

    /** The swing waiting for a click on {@code pos}, which is then used up; null when there is none */
    public static Swing take(ServerPlayerEntity player, BlockPos pos)
    {
        Swing swing = PENDING.remove(player.getUuid());

        return swing != null && swing.pos().equals(pos) ? swing : null;
    }

    public static void forget(UUID player)
    {
        PENDING.remove(player);
    }
}
