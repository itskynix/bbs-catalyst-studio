/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.text.Text
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.chat.ChatMorphHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayerEntity.class})
public abstract class PlayerEntityPovMixin {
    @Inject(method={"getName"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$getMorphedName(CallbackInfoReturnable<Text> cir) {
        String replayName = ChatMorphHelper.getActiveReplayName();
        if (replayName != null && !replayName.isEmpty()) {
            cir.setReturnValue(Text.literal((String)replayName));
            return;
        }
        PlayerEntity self = (PlayerEntity)(Object)this;
        String morphName = ChatMorphHelper.getPlayerMorphName(self);
        if (morphName != null && !morphName.isEmpty()) {
            cir.setReturnValue(Text.literal((String)morphName));
        }
    }

    @Inject(method={"getDisplayName"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$getMorphedDisplayName(CallbackInfoReturnable<Text> cir) {
        String replayName = ChatMorphHelper.getActiveReplayName();
        if (replayName != null && !replayName.isEmpty()) {
            cir.setReturnValue(Text.literal((String)replayName));
            return;
        }
        PlayerEntity self = (PlayerEntity)(Object)this;
        String morphName = ChatMorphHelper.getPlayerMorphName(self);
        if (morphName != null && !morphName.isEmpty()) {
            cir.setReturnValue(Text.literal((String)morphName));
        }
    }
}

