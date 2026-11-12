package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.camera.WorldFreeCam;
import mchorse.bbs_mod.forms.structure.StructureWand;
import mchorse.bbs_mod.film.VehicleRide;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The structure wand takes the mouse buttons here, before the game decides what a click means:
 * this is the one place a click passes through whether it lands on a block, an entity or the air,
 * and the wand must act the same on all three.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin
{
    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void bbs$appendStage(CallbackInfoReturnable<String> info)
    {
        String suffix = mchorse.bbs_mod.dev.DevStageBadge.windowSuffix();

        if (!suffix.isEmpty())
        {
            info.setReturnValue(info.getReturnValue() + " " + suffix);
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    public void wandAttack(CallbackInfoReturnable<Boolean> cir)
    {
        if (StructureWand.onAttack())
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    public void wandUse(CallbackInfo ci)
    {
        if (StructureWand.onUse())
        {
            ci.cancel();
        }
    }

    /* A vehicle in a film takes the right click on its doors and seats, and every click while seated */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    public void vehicleUse(CallbackInfo ci)
    {
        if (VehicleRide.onUse())
        {
            ci.cancel();
        }
    }

    /* The player stands still while the free camera flies: its clicks do not reach the world */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    public void freeCamAttack(CallbackInfoReturnable<Boolean> cir)
    {
        if (WorldFreeCam.isActive())
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    public void freeCamUse(CallbackInfo ci)
    {
        if (WorldFreeCam.isActive())
        {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    public void freeCamBreak(boolean breaking, CallbackInfo ci)
    {
        if (WorldFreeCam.isActive())
        {
            ci.cancel();
        }
    }
}
