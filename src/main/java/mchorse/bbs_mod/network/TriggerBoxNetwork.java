package mchorse.bbs_mod.network;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.TriggerBoxBlockEntity;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.PermissionUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The trigger box editor's save: the whole box, client to server. The block entity sync brings it back out. */
public class TriggerBoxNetwork
{
    public static final Identifier SERVER_TRIGGER_BOX_SAVE = BBSNetworking.c2s(Ids.of(BBSMod.MOD_ID, "trigger_box_save"));

    private static final Map<UUID, ServerPacketCrusher> crushers = new ConcurrentHashMap<>();

    public static void setup()
    {
        BBSNetworking.registerServerReceiver(SERVER_TRIGGER_BOX_SAVE, TriggerBoxNetwork::handleSave);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> crushers.remove(handler.player.getUuid()));
    }

    private static void handleSave(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf)
    {
        if (!PermissionUtils.arePanelsAllowed(server, player) && !player.isCreative())
        {
            return;
        }

        crushers.computeIfAbsent(player.getUuid(), (uuid) -> new ServerPacketCrusher()).receive(buf, (bytes, packet) ->
        {
            BlockPos pos = packet.readBlockPos();

            try
            {
                BaseType data = DataStorageUtils.readFromBytes(bytes);

                server.execute(() ->
                {
                    BlockEntity be = player.getWorld().getBlockEntity(pos);

                    if (be instanceof TriggerBoxBlockEntity box && data != null && data.isMap() && player.squaredDistanceTo(pos.toCenterPos()) < 128 * 128)
                    {
                        box.apply(data.asMap());
                    }
                });
            }
            catch (Exception e)
            {
                BBSMod.LOGGER.error("Failed to read a trigger box save!", e);
            }
        });
    }
}
