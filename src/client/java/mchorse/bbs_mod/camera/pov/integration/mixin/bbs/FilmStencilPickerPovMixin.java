/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.systems.VertexSorter
 *  mchorse.bbs_mod.BBSSettings
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.FilmControllerContext
 *  mchorse.bbs_mod.film.FilmEntityRenderer
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.graphics.texture.Texture
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.controller.FilmStencilPicker
 *  mchorse.bbs_mod.ui.film.controller.UIFilmController
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.utils.StencilMap
 *  mchorse.bbs_mod.ui.utils.Area
 *  mchorse.bbs_mod.ui.utils.StencilFormFramebuffer
 *  mchorse.bbs_mod.utils.Pair
 *  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.VertexConsumerProvider$Immediate
 *  net.minecraft.client.render.WorldRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.world.BlockRenderView
 *  org.joml.Matrix4f
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.PovAddon;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandGizmo;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.hand.playback.PovHandPlayback;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandMatrices;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmStencilPicker;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FilmStencilPicker.class}, remap=false)
public class FilmStencilPickerPovMixin {
    @Shadow
    @Final
    private UIFilmController controller;
    @Shadow
    @Final
    private StencilFormFramebuffer stencil;
    @Shadow
    @Final
    private StencilMap stencilMap;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"renderStencil"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$renderHandPicker(WorldRenderContext worldContext, UIContext context, boolean alt, CallbackInfo info) {
        UIFilmPanelPovAccess access;
        boolean handEditor;
        if (this.controller.getPovMode() != 6) {
            return;
        }
        info.cancel();
        UIFilmPanel panel = this.controller.panel;
        Area viewport = panel.preview.getViewport();
        boolean bl = handEditor = panel instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)panel).bbsPov$getEditor() != null && access.bbsPov$getEditor().isPoseGizmoSection();
        if (!handEditor || !viewport.isInside(context) || viewport.w <= 0 || viewport.h <= 0) {
            this.stencil.clearPicking();
            PovHandPicking.abortStencil();
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            this.stencil.clearPicking();
            PovHandPicking.abortStencil();
            return;
        }
        this.stencil.setup(Link.bbs((String)"stencil_film"));
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        int videoWidth = BBSRendering.getVideoWidth();
        int videoHeight = BBSRendering.getVideoHeight();
        if (texture.width != videoWidth || texture.height != videoHeight) {
            this.stencil.resizeGUI(videoWidth, videoHeight);
            texture = this.stencil.getFramebuffer().getMainTexture();
        }
        this.stencilMap.setup();
        this.stencil.apply();
        PovHandPicking.beginStencil(this.stencilMap);
        MatrixStack matrices = worldContext.matrixStack();
        matrices.push();
        matrices.loadIdentity();
        RenderSystem.getModelViewStack().push();
        RenderSystem.getModelViewStack().loadIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            Matrix4f handProjection = PovHandPicking.getProjection();
            if (handProjection == null) {
                handProjection = client.gameRenderer.getBasicProjectionMatrix(70.0);
                if (videoWidth > 0 && videoHeight > 0) {
                    handProjection.m00(handProjection.m11() / ((float)videoWidth / (float)videoHeight));
                }
                PovHandPicking.captureProjection(handProjection);
            }
            RenderSystem.setProjectionMatrix((Matrix4f)handProjection, (VertexSorter)VertexSorter.BY_Z);
            boolean began = PovHandPlayback.begin(worldContext.tickDelta());
            if (began) {
                PovHandPlayback.useCapturedPose(PovHandMatrices.getCapturedPose());
                VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
                BlockPos cameraPos = BlockPos.ofFloored((double)panel.getCamera().position.x, (double)panel.getCamera().position.y, (double)panel.getCamera().position.z);
                int light = client.world == null ? 0xF000F0 : WorldRenderer.getLightmapCoordinates((BlockRenderView)client.world, (BlockPos)cameraPos);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask((boolean)true);
                RenderSystem.depthFunc((int)515);
                PovHandPlayback.render(client.gameRenderer.firstPersonRenderer, worldContext.tickDelta(), matrices, consumers, player, light);
                consumers.draw();
                PovHandGizmo.renderStencil(this.stencilMap);
            }
            int x = (int)((float)(context.mouseX - viewport.x) / (float)viewport.w * (float)texture.width);
            int y = (int)((1.0f - (float)(context.mouseY - viewport.y) / (float)viewport.h) * (float)texture.height);
            int tolerance = Math.round((float)((Integer)BBSSettings.gizmoHoverTolerance.get() * texture.width) / (float)viewport.w);
            this.stencil.pick(x, y, tolerance, 19);
            this.stencil.unbind(this.stencilMap);
            PovHandPicking.finishStencil((Pair<Form, String>)this.stencil.getPicked());
        }
        catch (Throwable throwable) {
            PovAddon.LOGGER.error("POV hand stencil render failed", throwable);
            this.stencil.unbind(this.stencilMap);
            PovHandPicking.abortStencil();
        }
        finally {
            PovHandPlayback.end();
            RenderSystem.getModelViewStack().pop();
            RenderSystem.applyModelViewMatrix();
            matrices.pop();
            client.getFramebuffer().beginWrite(true);
        }
    }

    @Redirect(method={"renderStencil"}, at=@At(value="INVOKE", target="Lmchorse/bbs_mod/film/FilmEntityRenderer;renderEntity(Lmchorse/bbs_mod/film/FilmControllerContext;)V"))
    private void bbsPov$hideHeadLookSourceFromPicker(FilmControllerContext renderContext) {
        float filmTick;
        UIFilmPanel panel = this.controller.panel;
        if (UIPovHandEditor.isActive() && renderContext.replay == panel.replayEditor.getReplay()) {
            return;
        }
        int povMode = this.controller.getPovMode();
        if (povMode == 1 || povMode == 2) {
            FilmEntityRenderer.renderEntity((FilmControllerContext)renderContext);
            return;
        }
        Film film = (Film)panel.getData();
        PovCameraClip clip = PovCameraClips.resolve(film, filmTick = panel.getRunner() != null && panel.getRunner().isRunning() ? (float)panel.getRunner().ticks + renderContext.transition : (float)panel.getCursor());
        if (clip != null && ((Boolean)clip.headLook.get()).booleanValue() && renderContext.replay == PovCameraClips.resolveReplay(film, clip)) {
            return;
        }
        FilmEntityRenderer.renderEntity((FilmControllerContext)renderContext);
    }
}

