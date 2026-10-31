package mchorse.bbs_mod.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? if >=1.21 {
/*import net.minecraft.network.packet.CustomPayload;
*///?}

/**
 * Client half of {@link BBSNetworking}.
 */
public class BBSClientNetworking
{
    public interface ClientReceiver
    {
        void receive(MinecraftClient client, PacketByteBuf buf);
    }

    public static void registerReceiver(Identifier id, ClientReceiver receiver)
    {
        //? if >=1.21 {
        /*ClientPlayNetworking.registerGlobalReceiver(new CustomPayload.Id<BBSNetworking.Payload>(id), (payload, context) ->
        {
            receiver.receive(context.client(), payload.toBuffer());
        });
        *///?} else {
        ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buf, responseSender) -> receiver.receive(client, buf));
        //?}
    }

    public static void send(Identifier id, PacketByteBuf buf)
    {
        //? if >=1.21 {
        /*ClientPlayNetworking.send(BBSNetworking.Payload.of(id, buf));
        *///?} else {
        ClientPlayNetworking.send(id, buf);
        //?}
    }
}
