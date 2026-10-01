package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.renderers.utils.FramebufferDebug;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
    @Shadow
    public Framebuffer entityOutlinesFramebuffer;

    /* Deferred form translucency spans the frame: forms enqueue their translucent pass while
     * entities render, and the queue flushes right before the translucent terrain layer so the
     * blending sits under water/glass the way vanilla entities do. The RETURN hook is a safety
     * net for frames where the translucent layer never draws (e.g. a replaced terrain pipeline). */
    @Inject(method = "render", at = @At("HEAD"))
    public void onRenderWorldStart(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo info)
    {
        FormTranslucentQueue.begin();
        FramebufferDebug.newFrame();

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world != null)
        {
            Camera cam = camera != null ? camera : mc.gameRenderer.getCamera();

            if (this.shouldUseStudioCameraForSky() && cam != null)
            {
                int chunkX = ChunkSectionPos.getSectionCoord(cam.getPos().x);
                int chunkZ = ChunkSectionPos.getSectionCoord(cam.getPos().z);

                mc.world.getChunkManager().setChunkMapCenter(chunkX, chunkZ);
            }

            if (BBSRendering.getFramebuffer() != null)
            {
                if (BBSSettings.chromaSkyEnabled.get())
                {
                    Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
                    int argb = fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get();
                    Color color = Color.rgba(argb);

                    BBSRendering.getFramebuffer().setClearColor(color.r, color.g, color.b, 1F);
                }
                else
                {
                    BBSRendering.getFramebuffer().setClearColor(
                        BBSRendering.getFogRed(),
                        BBSRendering.getFogGreen(),
                        BBSRendering.getFogBlue(),
                        1F
                    );
                }
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void onRenderWorldEnd(CallbackInfo info)
    {
        FormTranslucentQueue.flush();
    }

    @ModifyConstant(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", constant = @Constant(floatValue = -90F))
    private float rotateSunHorizontally(float rotation)
    {
        return rotation + BBSRendering.getSunHorizontalRotation();
    }

    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    public void onRenderSky(CallbackInfo info)
    {
        if (BBSSettings.chromaSkyEnabled.get())
        {
            Integer fromCurve = BBSRendering.getChromaSkyColorArgb();
            int argb = fromCurve != null ? fromCurve : BBSSettings.chromaSkyColor.get();
            Color color = Color.rgba(argb);

            GL11.glClearColor(color.r, color.g, color.b, 1F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            RenderSystem.setShaderFogColor(color.r, color.g, color.b, 1F);

            info.cancel();
        }
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    public void onRenderLayer(RenderLayer renderLayer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix, CallbackInfo info)
    {
        /* Iris' shadow pass re-runs renderLayer into the shadow map early in the frame — its
         * translucent layer must not fire the flush, or the queue deactivates before the main
         * pass has even rendered the forms. */
        if (renderLayer == RenderLayer.getTranslucent() && !BBSRendering.isIrisShadowPass())
        {
            FormTranslucentQueue.flush();
        }

        if (BBSSettings.chromaSkyEnabled.get() && !BBSSettings.chromaSkyTerrain.get())
        {
            BBSRendering.onRenderChunkLayer(matrices);

            info.cancel();
        }
    }

    @Inject(method = "renderLayer", at = @At("TAIL"))
    public void onRenderChunkLayer(RenderLayer layer, MatrixStack stack, double x, double y, double z, Matrix4f positionMatrix, CallbackInfo info)
    {
        if (layer == RenderLayer.getSolid())
        {
            BBSRendering.onRenderChunkLayer(stack);
        }
    }

    @Inject(at = @At("RETURN"), method = "loadEntityOutlinePostProcessor")
    private void onLoadEntityOutlineShader(CallbackInfo info)
    {
        BBSRendering.resizeExtraFramebuffers();
    }

    @Inject(at = @At("RETURN"), method = "onResized")
    private void onResized(CallbackInfo info)
    {
        if (this.entityOutlinesFramebuffer == null)
        {
            return;
        }

        BBSRendering.resizeExtraFramebuffers();
    }

    private boolean shouldUseStudioCameraForSky()
    {
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            return true;
        }

        if (BBSRendering.isRenderingWorld() || BBSRendering.isCustomSize())
        {
            return true;
        }

        if (BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording())
        {
            return true;
        }

        mchorse.bbs_mod.ui.framework.UIBaseMenu currentMenu = mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();

        if (currentMenu instanceof mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard
            && dashboard.getPanels().panel instanceof mchorse.bbs_mod.ui.film.UIFilmPanel)
        {
            return true;
        }

        return false;
    }

    @Redirect(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d redirectRenderSkyCameraPos(ClientPlayerEntity player, float tickDelta)
    {
        if (this.shouldUseStudioCameraForSky())
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null)
            {
                return mc.gameRenderer.getCamera().getPos();
            }
        }

        return player != null ? player.getCameraPosVec(tickDelta) : Vec3d.ZERO;
    }
}
