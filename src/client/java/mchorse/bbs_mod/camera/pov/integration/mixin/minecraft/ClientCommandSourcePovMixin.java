/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientCommandSource
 *  net.minecraft.entity.player.PlayerEntity
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
import java.util.Collection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ClientCommandSource.class})
public class ClientCommandSourcePovMixin {
    @Shadow
    @Final
    private MinecraftClient field_3725;

    @Inject(method={"getPlayerNames"}, at={@At(value="RETURN")}, cancellable=true)
    private void bbsPov$replacePlayerNames(CallbackInfoReturnable<Collection<String>> cir) {
        Collection<String> original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        String playerName = this.field_3725.player != null && this.field_3725.player.getGameProfile() != null ? this.field_3725.player.getGameProfile().getName() : null;
        String morphName = this.field_3725.player != null ? ChatMorphHelper.getPlayerMorphName((PlayerEntity)this.field_3725.player) : null;
        String replayName = ChatMorphHelper.getActiveReplayName();
        ArrayList<String> modified = new ArrayList<String>();
        for (String name : original) {
            if (playerName != null && name.equalsIgnoreCase(playerName)) {
                if (replayName != null && !replayName.isEmpty()) {
                    if (modified.contains(replayName)) continue;
                    modified.add(replayName);
                    continue;
                }
                if (morphName != null && !morphName.isEmpty()) {
                    if (modified.contains(morphName)) continue;
                    modified.add(morphName);
                    continue;
                }
                if (modified.contains(name)) continue;
                modified.add(name);
                continue;
            }
            if (morphName != null && name.equalsIgnoreCase(morphName)) {
                if (replayName != null && !replayName.isEmpty()) {
                    if (modified.contains(replayName)) continue;
                    modified.add(replayName);
                    continue;
                }
                if (modified.contains(name)) continue;
                modified.add(name);
                continue;
            }
            if (modified.contains(name)) continue;
            modified.add(name);
        }
        if (replayName != null && !replayName.isEmpty() && morphName != null && !morphName.equalsIgnoreCase(replayName)) {
            modified.removeIf(n -> n.equalsIgnoreCase(morphName));
        }
        cir.setReturnValue(modified);
    }
}

