package mchorse.bbs_mod.mixin.client.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Opens the chunk renderer and the region storage of Sodium's section manager, which the portal
 * terrain pass needs to draw sections of its own choosing. Only applied while Sodium is loaded.
 */
@Mixin(RenderSectionManager.class)
public interface RenderSectionManagerAccessor
{
    @Accessor(value = "chunkRenderer", remap = false)
    public ChunkRenderer bbs$getChunkRenderer();

    @Accessor(value = "regions", remap = false)
    public RenderRegionManager bbs$getRegions();
}

