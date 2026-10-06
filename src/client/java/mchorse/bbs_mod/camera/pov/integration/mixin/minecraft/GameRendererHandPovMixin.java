/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  mchorse.bbs_mod.ui.utils.Area
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.BufferBuilderStorage
 *  net.minecraft.client.render.Camera
 *  net.minecraft.client.render.GameRenderer
 *  net.minecraft.client.render.LightmapTextureManager
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  net.minecraft.client.render.WorldRenderer
 *  net.minecraft.client.render.item.HeldItemRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.world.BlockRenderView
 *  org.joml.Matrix4f
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.HandState;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandGizmo;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandDepthManager;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.utils.Area;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GameRenderer.class}, priority=200)
public abstract class GameRendererHandPovMixin {
    @Shadow
    @Final
    private MinecraftClient field_4015;
    @Shadow
    @Final
    private BufferBuilderStorage field_20948;
    @Shadow
    @Final
    private LightmapTextureManager field_4028;
    @Shadow
    @Final
    public HeldItemRenderer field_4012;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"renderHand"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$renderHandBeforeBbsCancellation(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo info) {
        Area editorFrame;
        if (UIPovHandEditor.isActive()) {
            info.cancel();
            return;
        }
        if (BBSRendering.isIrisShadersEnabled() && PovHandPlayback.isHandActive(tickDelta)) {
            info.cancel();
            return;
        }
        if (!PovHandPlayback.begin(tickDelta)) {
            if (PovPlaybackContext.getActive() != null || PovReplaySettings.getFilmPanel() != null || BBSModClient.getCameraController().getCurrent() != null || this.field_4015.currentScreen instanceof UIScreen) {
                info.cancel();
            }
            return;
        }
        HandState state = PovHandPlayback.getActiveState();
        boolean worldInteraction = state != null && state.worldInteraction;
        boolean replayInteraction = state != null && state.replayInteraction;
        ClientPlayerEntity player = this.field_4015.player;
        if (player == null) {
            PovHandPlayback.end();
            return;
        }
        GameRenderer renderer = (GameRenderer)(Object)this;
        double fov = 70.0;
        Matrix4f handProjection = renderer.getBasicProjectionMatrix(fov);
        int videoWidth = BBSRendering.isCustomSize() ? BBSRendering.getVideoWidth() : this.field_4015.getWindow().getFramebufferWidth();
        int videoHeight = BBSRendering.isCustomSize() ? BBSRendering.getVideoHeight() : this.field_4015.getWindow().getFramebufferHeight();
        boolean inHandEditor = UIPovHandEditor.isActive();
        Area area = editorFrame = inHandEditor && UIPovHandEditor.getActive() != null ? UIPovHandEditor.getActive().getFrameArea() : null;
        if (inHandEditor && editorFrame != null) {
            RenderSystem.clearColor((float)0.094f, (float)0.094f, (float)0.094f, (float)1.0f);
            RenderSystem.clear((int)16640, (boolean)MinecraftClient.IS_SYSTEM_MAC);
            double scale = this.field_4015.getWindow().getScaleFactor();
            int vx = (int)Math.round((double)editorFrame.x * scale);
            int vy = (int)Math.round((double)(this.field_4015.getWindow().getScaledHeight() - (editorFrame.y + editorFrame.h)) * scale);
            int vw = (int)Math.round((double)editorFrame.w * scale);
            int vh = (int)Math.round((double)editorFrame.h * scale);
            RenderSystem.viewport((int)vx, (int)vy, (int)vw, (int)vh);
            handProjection.setPerspective((float)Math.toRadians(fov), 1.7777778f, 0.05f, 100.0f);
            renderer.loadProjectionMatrix(handProjection);
        } else {
            if (videoWidth > 0 && videoHeight > 0) {
                float aspect = (float)videoWidth / (float)videoHeight;
                handProjection.m00(handProjection.m11() / aspect);
                renderer.loadProjectionMatrix(handProjection);
            } else {
                renderer.loadProjectionMatrix(handProjection);
            }
            PovHandDepthManager.prepareDepthForHand(worldInteraction, replayInteraction);
        }
        PovHandPicking.captureProjection(RenderSystem.getProjectionMatrix());
        matrices.push();
        matrices.loadIdentity();
        this.field_4028.enable();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.depthFunc((int)515);
        try {
            VertexConsumerProvider.Immediate consumers = this.field_20948.getEntityVertexConsumers();
            int light = this.field_4015.world == null ? 0xF000F0 : WorldRenderer.getLightmapCoordinates((BlockRenderView)this.field_4015.world, (BlockPos)camera.getBlockPos());
            PovHandPlayback.render(this.field_4012, tickDelta, matrices, consumers, player, light);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean)true);
            RenderSystem.depthFunc((int)515);
            consumers.draw();
            PovHandGizmo.captureVisual();
        }
        finally {
            if (inHandEditor) {
                RenderSystem.viewport((int)0, (int)0, (int)this.field_4015.getWindow().getFramebufferWidth(), (int)this.field_4015.getWindow().getFramebufferHeight());
                RenderSystem.clearColor((float)0.0f, (float)0.0f, (float)0.0f, (float)0.0f);
            }
            PovHandDepthManager.endHandDepth(worldInteraction, replayInteraction);
            this.field_4028.disable();
            matrices.pop();
            PovHandPlayback.end();
        }
        info.cancel();
    }
}

