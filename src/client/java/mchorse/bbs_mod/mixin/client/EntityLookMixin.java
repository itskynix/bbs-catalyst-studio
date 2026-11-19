package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.physics.InWorldPhysicsTool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the physics gun turns what it holds, the mouse turns that instead of the view: taken here, on every frame
 * the mouse moves, so the turn is as smooth as the mouse itself.
 */
@Mixin(Entity.class)
public class EntityLookMixin
{
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void bbs$physicsToolLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo info)
    {
        if ((Object) this == MinecraftClient.getInstance().player && (InWorldPhysicsTool.consumeLook(cursorDeltaX, cursorDeltaY) || mchorse.bbs_mod.film.OpenableAnimator.consumeLook(cursorDeltaX, cursorDeltaY)))
        {
            info.cancel();
        }
    }
}
