package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.FormPortals;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerPortalMixin
{
    /* The server confirms a portal jump with a position packet, which can take the player's momentum with it */
    @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
    public void onPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo info)
    {
        FormPortals.onServerPosition();
    }
}
