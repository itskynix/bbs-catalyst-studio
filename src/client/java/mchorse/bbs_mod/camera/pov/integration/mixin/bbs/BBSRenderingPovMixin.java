/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.gui.render.LiveGuiPreviewRenderer;
import mchorse.bbs_mod.camera.pov.hand.render.PovHandDepthManager;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.recording.PovRecordingSession;
import mchorse.bbs_mod.camera.pov.render.PovOverlayRenderer;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BBSRendering.class}, remap=false)
public abstract class BBSRenderingPovMixin {
    @Inject(method={"onWorldRenderBegin"}, at={@At(value="HEAD")})
    private static void bbsPov$beginOverlayFrame(CallbackInfo info) {
        PovOverlayRenderer.beginFrame();
        PovHandDepthManager.onRenderWorldStart();
    }

    @Inject(method={"onWorldRenderEnd"}, at={@At(value="HEAD")})
    private static void bbsPov$renderPlaybackOverlay(CallbackInfo info) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getTickDelta();
        if (PovPlaybackContext.getActive(tickDelta) != null) {
            DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            PovOverlayRenderer.renderPlayback(new Batcher2D(context), tickDelta);
            context.draw();
            return;
        }
        UIFilmPanel filmPanel = PovViewportMetrics.resolveFilmPanel();
        if (filmPanel != null) {
            DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            PovOverlayRenderer.render(context.getMatrices(), new Batcher2D(context));
            context.draw();
            return;
        }
        PovRecordingSession.sampleCursor(tickDelta);
        if (BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording() && client.player != null && (client.currentScreen != null && !(client.currentScreen instanceof UIScreen) || client.player.getSleepTimer() > 0)) {
            DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            LiveGuiPreviewRenderer.render(new Batcher2D(context), tickDelta);
            context.draw();
        }
    }
}

