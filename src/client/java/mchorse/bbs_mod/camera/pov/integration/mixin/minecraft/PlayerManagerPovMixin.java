/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.server.PlayerManager
 *  net.minecraft.server.network.ServerPlayerEntity
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.chat.ChatMorphHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayerManager.class})
public class PlayerManagerPovMixin {
    @Shadow
    @Final
    private List<ServerPlayerEntity> field_14351;

    @Inject(method={"getPlayer(Ljava/lang/String;)Lnet/minecraft/server/network/ServerPlayerEntity;"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$getPlayerByMorphOrReplay(String name, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        if (cir.getReturnValue() == null && name != null && !name.isEmpty()) {
            for (ServerPlayerEntity player : this.field_14351) {
                String replayName = ChatMorphHelper.getActiveReplayName();
                if (replayName != null && replayName.equalsIgnoreCase(name)) {
                    cir.setReturnValue(player);
                    return;
                }
                String morphName = ChatMorphHelper.getPlayerMorphName((PlayerEntity)player);
                if (morphName == null || !morphName.equalsIgnoreCase(name)) continue;
                cir.setReturnValue(player);
                return;
            }
        }
    }

    @Inject(method={"getPlayerNames"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$getPlayerNames(CallbackInfoReturnable<String[]> cir) {
        ArrayList<String> result = new ArrayList<String>();
        String replayName = ChatMorphHelper.getActiveReplayName();
        for (ServerPlayerEntity player : this.field_14351) {
            if (replayName != null && !replayName.isEmpty()) {
                result.add(replayName);
                continue;
            }
            String morphName = ChatMorphHelper.getPlayerMorphName((PlayerEntity)player);
            if (morphName != null && !morphName.isEmpty()) {
                result.add(morphName);
                continue;
            }
            result.add(player.getGameProfile().getName());
        }
        if (replayName != null && !replayName.isEmpty()) {
            for (ServerPlayerEntity player : this.field_14351) {
                String morphName = ChatMorphHelper.getPlayerMorphName((PlayerEntity)player);
                if (morphName == null || morphName.equalsIgnoreCase(replayName)) continue;
                final String targetMorph = morphName;
                result.removeIf(n -> n.equalsIgnoreCase(targetMorph));
            }
        }
        cir.setReturnValue(result.toArray(new String[0]));
    }
}

