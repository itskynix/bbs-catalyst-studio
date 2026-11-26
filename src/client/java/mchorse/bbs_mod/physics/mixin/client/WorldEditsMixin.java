package mchorse.bbs_mod.physics.mixin.client;

import mchorse.bbs_mod.physics.client.scene.FilmScenes;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every block of the client's world that changes passes through here — broken, placed, opened,
 * whether by the player or by the server — and is handed to the physics scenes, so a ragdoll lying
 * on a block that was dug out falls into the hole and a block placed is something to land on.
 */
@Mixin(ClientWorld.class)
public abstract class WorldEditsMixin
{
    @Inject(method = "updateListeners", at = @At("HEAD"))
    private void bbs_physics$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo info)
    {
        FilmScenes.blockChanged((ClientWorld) (Object) this, pos.toImmutable(), oldState, newState);
    }
}
