package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;
import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.forms.WorldLightGuideOverlay;
import qualet.irlite.client.light.LightCollector;
import qualet.irlite.client.light.ReplayOutlineContext;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        WorldLightGuideOverlay.beginFrame();
        VlProfiler.frameTick();
        ReplayOutlineContext.beginFrame();
        VlProfiler.beginPass(VlProfiler.PASS_BAKE);
        long pipelineT0 = System.nanoTime();
        try
        {
            FramePipeline.frame(tickDelta, IrisShadersState::shadersDisabled, LightCollector::collect, () -> {});
        }
        catch (RuntimeException | Error e)
        {
            VlProfiler.invalidateFrame();
            throw e;
        }
        finally
        {
            VlProfiler.cpuSample("pipeline", System.nanoTime() - pipelineT0);
            VlProfiler.endPass();
        }
    }

    /**
     * Deferred SSBO upload, injected just AFTER this frame's Camera.update (offset ~180
     * in renderWorld, still well before WorldRenderer.render / Iris activation at ~562):
     * the origin the light SSBO is made relative to must be the post-update, current-frame
     * eye that the shaderpack reconstructs fragments against, not the stale HEAD camera.
     */
    @Inject(method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
                     shift = At.Shift.AFTER,
                     ordinal = 0),
            require = 1)
    private void irlite$uploadLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        long uploadT0 = System.nanoTime();
        FramePipeline.uploadIfPending();
        VlProfiler.cpuSample("upload", System.nanoTime() - uploadT0);
    }

    /** Iris has finished world compositing; BBS has not captured the film preview yet. */
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        shift = At.Shift.AFTER), require = 1)
    private void irlite$drawLightGuides(float tickDelta, long startTime, boolean tick, CallbackInfo ci)
    {
        WorldLightGuideOverlay.flush();
    }
}

