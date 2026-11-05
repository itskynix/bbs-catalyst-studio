package mchorse.bbs_mod.mixin.client.iris;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.diag.ProfileCapture;

import java.util.function.Supplier;

@Mixin(value = CompositeRenderer.class, remap = false)
public class CompositeRendererTimerMixin
{
    @Inject(method = "createProgram", at = @At("RETURN"), remap = false)
    private void irlite$recordPassName(ProgramSource source, ImmutableSet<Integer> flipped,
                                       ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
                                       Supplier<?> shadowTargetsSupplier,
                                       CallbackInfoReturnable<Program> cir)
    {
        VlProfiler.registerPassName(cir.getReturnValue(), source.getName());
        if (cir.getReturnValue() != null) ProfileCapture.programSource(source);
    }

    @Redirect(method = "renderAll",
              at = @At(value = "INVOKE",
                       target = "Lnet/irisshaders/iris/gl/program/Program;use()V"),
              require = 0, expect = 1,
              remap = false)
    private void irlite$beginTimedPass(Program program)
    {
        VlProfiler.beginPass(VlProfiler.irisPassName(program));
        program.use();
    }

    @Redirect(method = "renderAll",
              at = @At(value = "INVOKE",
                       target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),
              require = 0, expect = 1,
              remap = false)
    private void irlite$endTimedPass(FullScreenQuadRenderer instance)
    {
        instance.renderQuad();
        VlProfiler.endPass();
    }
}

