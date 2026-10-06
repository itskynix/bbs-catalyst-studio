package mchorse.bbs_mod.actions;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
//? if >=1.20.4
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import javax.annotation.Nullable;

public class SuperFakePlayerNetworkHandler extends ServerPlayNetworkHandler
{
    private static final ClientConnection FAKE_CONNECTION = new FakeClientConnection();

    public SuperFakePlayerNetworkHandler(ServerPlayerEntity player)
    {
        //? if >=1.20.4 {
        super(player.getServer(), FAKE_CONNECTION, player, ConnectedClientData.createDefault(player.getGameProfile()));
        //?} else {
        /*super(player.getServer(), FAKE_CONNECTION, player);
        *///?}
    }

    //? if >=1.20.4 {
    @Override
    public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks)
    {}
    //?} else {
    /*@Override
    public void sendPacket(Packet<?> packet, @Nullable PacketCallbacks callbacks)
    {}
    *///?}

    private static final class FakeClientConnection extends ClientConnection
    {
        private FakeClientConnection()
        {
            super(NetworkSide.CLIENTBOUND);
        }

        @Override
        public void setPacketListener(PacketListener packetListener)
        {}
    }
}