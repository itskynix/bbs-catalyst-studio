/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.actions.ActionPlayer
 *  net.minecraft.server.network.ServerPlayerEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.playback.PovPlayerProtection;
import mchorse.bbs_mod.actions.ActionPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ActionPlayer.class}, remap=false)
public abstract class ActionPlayerPovMixin {
    @Shadow
    private ServerPlayerEntity serverPlayer;
    @Shadow
    private boolean borrowedEquipment;
    @Unique
    private boolean bbsPov$protectedPlayer;

    @Redirect(method={"<init>"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/actions/ActionPlayer;applyFilmPlayerSettingsTo"))
    private void bbsPov$preserveRealHealth(ServerPlayerEntity player, float filmHealth, float filmHunger, int filmXpLevel, float filmXpProgress) {
        float safeHealth = Math.max(1.0f, player.getHealth());
        ActionPlayer.applyFilmPlayerSettingsTo((ServerPlayerEntity)player, (float)safeHealth, (float)filmHunger, (int)filmXpLevel, (float)filmXpProgress);
    }

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void bbsPov$protectBorrowedPlayer(CallbackInfo info) {
        if (this.borrowedEquipment && this.serverPlayer != null) {
            this.serverPlayer.hurtTime = 0;
            this.serverPlayer.maxHurtTime = 0;
            PovPlayerProtection.acquire(this.serverPlayer);
            this.bbsPov$protectedPlayer = true;
        }
    }

    @Inject(method={"stop"}, at={@At(value="RETURN")})
    private void bbsPov$releaseBorrowedPlayer(CallbackInfo info) {
        if (this.bbsPov$protectedPlayer) {
            PovPlayerProtection.release(this.serverPlayer);
            this.bbsPov$protectedPlayer = false;
        }
    }
}

