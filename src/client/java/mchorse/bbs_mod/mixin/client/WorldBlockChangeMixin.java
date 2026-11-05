package mchorse.bbs_mod.mixin.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.shadow.BlockShadowCache;

@Mixin(ClientWorld.class)
public class WorldBlockChangeMixin
{
    @Inject(
        method = "updateListeners(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V",
        at = @At("HEAD")
    )
    private void irlite$invalidateBlockShadows(
        BlockPos pos, BlockState oldState, BlockState newState, int flags,
        CallbackInfo ci)
    {
        if (oldState == newState)
        {
            return;
        }
        BlockShadowCache.invalidateChange((ClientWorld) (Object) this, pos, oldState, newState);
    }
}

