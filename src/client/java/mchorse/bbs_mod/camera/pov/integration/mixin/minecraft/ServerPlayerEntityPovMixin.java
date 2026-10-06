/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.damage.DamageSource
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.text.Text
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.chat.ChatMorphHelper;
import mchorse.bbs_mod.camera.pov.playback.PovPlayerProtection;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ServerPlayerEntity.class})
public abstract class ServerPlayerEntityPovMixin {
    @Inject(method={"damage"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$cancelPlaybackDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (PovPlayerProtection.contains((ServerPlayerEntity)(Object)this)) {
            info.setReturnValue(false);
        }
    }

    @Inject(method={"getPlayerListName"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$getMorphedPlayerListName(CallbackInfoReturnable<Text> cir) {
        String replayName = ChatMorphHelper.getActiveReplayName();
        if (replayName != null && !replayName.isEmpty()) {
            cir.setReturnValue(Text.literal((String)replayName));
            return;
        }
        ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
        String morphName = ChatMorphHelper.getPlayerMorphName((PlayerEntity)self);
        if (morphName != null && !morphName.isEmpty()) {
            cir.setReturnValue(Text.literal((String)morphName));
        }
    }
}

