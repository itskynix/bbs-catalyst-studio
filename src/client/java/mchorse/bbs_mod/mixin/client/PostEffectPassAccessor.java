package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.IntSupplier;

@Mixin(PostEffectPass.class)
public interface PostEffectPassAccessor
{
    @Accessor("input")
    @Mutable
    void setInput(Framebuffer input);

    @Accessor("output")
    @Mutable
    void setOutput(Framebuffer output);

    @Accessor("samplerNames")
    List<String> getSamplerNames();

    @Accessor("samplerValues")
    List<IntSupplier> getSamplerValues();

    @Accessor("samplerWidths")
    List<Integer> getSamplerWidths();

    @Accessor("samplerHeights")
    List<Integer> getSamplerHeights();
}
