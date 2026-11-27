package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recap Studio leaving the beacon beams out while it shows a recap that hides them. */
@Mixin(BeaconBlockEntityRenderer.class)
public class RecapBeaconMixin
{
    @Inject(method = "render(Lnet/minecraft/block/entity/BeaconBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bbs$recapHidesBeams(CallbackInfo info)
    {
        if (mchorse.bbs_mod.recap.RecapScene.hidesBeacons())
        {
            info.cancel();
        }
    }
}
