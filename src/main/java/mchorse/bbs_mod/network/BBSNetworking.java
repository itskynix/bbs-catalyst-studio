package mchorse.bbs_mod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
//? if >=1.21 {
/*import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.HashSet;
import java.util.Set;
*///?} else {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?}

/**
 * Raw {@link PacketByteBuf} channels over the Fabric networking API. Since 1.20.5 every channel
 * needs a registered payload type, so the packets here are wrapped in a payload that just carries
 * the bytes; the handlers keep working with plain buffers on every supported version.
 */
public class BBSNetworking
{
    public interface ServerReceiver
    {
        void receive(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf);
    }

    //? if >=1.21 {
    /*public record Payload(CustomPayload.Id<Payload> type, byte[] data) implements CustomPayload
    {
        @Override
        public Id<? extends CustomPayload> getId()
        {
            return this.type;
        }

        public PacketByteBuf toBuffer()
        {
            return new PacketByteBuf(Unpooled.wrappedBuffer(this.data));
        }

        public static Payload of(Identifier id, PacketByteBuf buf)
        {
            byte[] bytes = new byte[buf.readableBytes()];

            buf.getBytes(buf.readerIndex(), bytes);

            return new Payload(new CustomPayload.Id<>(id), bytes);
        }

        public static PacketCodec<RegistryByteBuf, Payload> codec(Identifier id)
        {
            CustomPayload.Id<Payload> type = new CustomPayload.Id<>(id);

            return PacketCodec.of((payload, buf) -> buf.writeBytes(payload.data()), (buf) ->
            {
                byte[] bytes = new byte[buf.readableBytes()];

                buf.readBytes(bytes);

                return new Payload(type, bytes);
            });
        }
    }

    private static final Set<Identifier> registeredS2C = new HashSet<>();
    private static final Set<Identifier> registeredC2S = new HashSet<>();
    *///?}

    /** Declares a server -> client channel and returns its id. */
    public static Identifier s2c(Identifier id)
    {
        //? if >=1.21 {
        /*if (registeredS2C.add(id))
        {
            PayloadTypeRegistry.playS2C().register(new CustomPayload.Id<>(id), Payload.codec(id));
        }
        *///?}

        return id;
    }

    /** Declares a client -> server channel and returns its id. */
    public static Identifier c2s(Identifier id)
    {
        //? if >=1.21 {
        /*if (registeredC2S.add(id))
        {
            PayloadTypeRegistry.playC2S().register(new CustomPayload.Id<>(id), Payload.codec(id));
        }
        *///?}

        return id;
    }

    public static void registerServerReceiver(Identifier id, ServerReceiver receiver)
    {
        //? if >=1.21 {
        /*ServerPlayNetworking.registerGlobalReceiver(new CustomPayload.Id<Payload>(id), (payload, context) ->
        {
            receiver.receive(context.server(), context.player(), payload.toBuffer());
        });
        *///?} else {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buf, responder) -> receiver.receive(server, player, buf));
        //?}
    }

    //? if >=1.21 {
    /*public static Payload payload(Identifier id, PacketByteBuf buf)
    {
        return Payload.of(id, buf);
    }
    *///?}

    public static void send(ServerPlayerEntity player, Identifier id, PacketByteBuf buf)
    {
        //? if >=1.21 {
        /*ServerPlayNetworking.send(player, Payload.of(id, buf));
        *///?} else {
        ServerPlayNetworking.send(player, id, buf);
        //?}
    }
}
