package mchorse.bbs_mod.mixin.client.sodium;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Opens the section manager of Sodium's world renderer. Only applied while Sodium is loaded
 * ({@code BBSClientMixinPlugin} filters every mixin of this package).
 */
@Mixin(SodiumWorldRenderer.class)
public interface SodiumWorldRendererAccessor
{
    @Accessor(value = "renderSectionManager", remap = false)
    public RenderSectionManager bbs$getRenderSectionManager();
}

