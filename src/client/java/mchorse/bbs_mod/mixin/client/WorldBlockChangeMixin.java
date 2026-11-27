package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.DecayFormRenderer;
import mchorse.bbs_mod.recap.RecapRecorder;
import mchorse.bbs_mod.recap.RecapWorldOverlay;
import mchorse.bbs_mod.utils.VersionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.qualet.irl.light.shadow.BlockShadowCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class WorldBlockChangeMixin
{
    @Inject(
        method = "updateListeners(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;I)V",
        at = @At("HEAD")
    )
    private void irlite$invalidateBlockShadows(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci)
    {
        ClientWorld world = (ClientWorld) (Object) this;

        if (oldState == newState)
        {
            if (newState.hasBlockEntity() && !RecapWorldOverlay.isApplying())
            {
                BlockEntity be = world.getBlockEntity(pos);
                NbtCompound toNbt = be == null ? null : VersionCompat.createNbtWithId(be);

                if (toNbt != null)
                {
                    RecapRecorder.onBlockChanged(world, pos, oldState, newState, null, toNbt);
                }
            }

            return;
        }

        BlockShadowCache.invalidateChange(world, pos, oldState, newState);

        NbtCompound toNbt = null;

        if (newState.hasBlockEntity() && !RecapWorldOverlay.isApplying())
        {
            BlockEntity be = world.getBlockEntity(pos);

            if (be != null)
            {
                toNbt = VersionCompat.createNbtWithId(be);
            }
        }

        /* Recap: a block broken, placed or used goes into the recording at its tick, with its
         * dimension and BlockEntity payload; over a block a shown recap set, it updates live state */
        RecapRecorder.onBlockChanged(world, pos, oldState, newState, null, toNbt);

        /* A decay volume over this block rebuilds its surfaces in the very next frame */
        DecayFormRenderer.onBlockChanged(pos);
    }

    @Inject(method = "addSyncedBlockEvent", at = @At("HEAD"), require = 0)
    private void bbs$onBlockAction(BlockPos pos, Block block, int type, int data, CallbackInfo ci)
    {
        RecapRecorder.onBlockAction((ClientWorld) (Object) this, pos, block, type, data);
    }
}
