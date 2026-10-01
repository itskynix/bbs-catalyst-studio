package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor
{
    @Accessor("mainTarget")
    @Mutable
    void setMainTarget(Framebuffer mainTarget);

    @Accessor("targetsByName")
    Map<String, Framebuffer> getTargetsByName();

    @Accessor("passes")
    List<PostEffectPass> getPasses();

    @Accessor("width")
    int getWidth();

    @Accessor("height")
    int getHeight();
}
