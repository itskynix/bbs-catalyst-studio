/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.GlStateManager$DstFactor
 *  com.mojang.blaze3d.platform.GlStateManager$SrcFactor
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.blaze3d.systems.VertexSorter
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.BackgroundRenderer
 *  net.minecraft.client.render.CameraSubmersionType
 *  net.minecraft.client.util.math.MatrixStack
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package mchorse.bbs_mod.camera.pov.render;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.actions.bossbar.render.BossBarActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.chat.render.ChatActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.menu.render.MenuActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.render.ScreenEffectActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.statuseffect.render.StatusEffectActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.toast.render.ToastActionRenderer;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import mchorse.bbs_mod.camera.pov.editor.UIPovHandEditor;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.HudState;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.hud.playback.HudSampler;
import mchorse.bbs_mod.camera.pov.hud.render.AttackIndicatorRenderer;
import mchorse.bbs_mod.camera.pov.hud.render.HeldItemTooltipRenderer;
import mchorse.bbs_mod.camera.pov.hud.render.HudRenderer;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.render.PovCrosshairRenderer;
import mchorse.bbs_mod.camera.pov.render.PovCursorRenderer;
import mchorse.bbs_mod.camera.pov.render.PovViewportMetrics;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class PovOverlayRenderer {
    private static boolean vignetteDrawnThisFrame;

    private PovOverlayRenderer() {
    }

    public static void beginFrame() {
        vignetteDrawnThisFrame = false;
    }

    public static boolean shouldSkipHudVignette() {
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void render(MatrixStack matrices, Batcher2D batcher) {
        ReplayKeyframes replayKeyframes;
        Replay replay;
        PovCameraClip clip;
        UIFilmPanel filmPanel = PovViewportMetrics.resolveFilmPanel();
        if (filmPanel == null) {
            return;
        }
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        float filmTick = filmPanel.getRunner() != null && filmPanel.getRunner().isRunning() ? (float)filmPanel.getRunner().ticks + tickDelta : (float)filmPanel.getCursor();
        int povMode = filmPanel.getController().getPovMode();
        if (povMode == 1 || povMode == 2 || UIPovHandEditor.isActive()) {
            return;
        }
        boolean povEditMode = povMode == 6;
        Film film = (Film)filmPanel.getData();
        if (film == null) {
            return;
        }
        PovCameraClip povCameraClip = clip = povEditMode ? null : PovCameraClips.resolve(film, filmTick);
        if (!povEditMode && clip == null) {
            return;
        }
        Replay replay2 = replay = povEditMode ? filmPanel.replayEditor.getReplay() : PovCameraClips.resolveReplay(film, clip);
        if (replay == null) {
            replay = film.getFirstPersonReplay();
        }
        if (replay == null && film.replays != null && !film.replays.getList().isEmpty()) {
            replay = (Replay)film.replays.getList().get(0);
        }
        if (replay == null || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        boolean showHud = povEditMode || (Boolean)clip.hud.get() != false;
        boolean showCrosshairOutput = povEditMode || (Boolean)clip.crosshair.get() != false;
        boolean showActions = povEditMode || (Boolean)clip.actions.get() != false;
        boolean showScreenEffects = povEditMode || (Boolean)clip.actions.get() != false && (Boolean)clip.screenEffects.get() != false;
        boolean showCursor = povEditMode || (Boolean)clip.cursor.get() != false;
        RecordedHudData hud = access.bbsPov$getHud();
        RecordedHandData hand = access.bbsPov$getHand();
        RecordedPovActions actions = access.bbsPov$getActions();
        int looping = (Integer)replay.looping.get();
        float replayTick = looping > 0 ? filmTick % (float)looping : filmTick;
        HudState state = hud == null ? null : HudSampler.sample(hud, replay.keyframes, replayTick);
        int width = PovViewportMetrics.getFilmScaledWidth();
        int height = PovViewportMetrics.getFilmScaledHeight();
        Matrix4f previousProjection = new Matrix4f((Matrix4fc)RenderSystem.getProjectionMatrix());
        Matrix4f screenProjection = new Matrix4f().ortho(0.0f, (float)width, (float)height, 0.0f, -1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)screenProjection, (VertexSorter)VertexSorter.BY_Z);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate((GlStateManager.SrcFactor)GlStateManager.SrcFactor.SRC_ALPHA, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, (GlStateManager.SrcFactor)GlStateManager.SrcFactor.ZERO, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null || client.gameRenderer.getCamera().getSubmersionType() != CameraSubmersionType.LAVA) {
            BackgroundRenderer.clearFog();
        }
        try {
            boolean showCrosshair;
            if (showScreenEffects && actions != null) {
                ScreenEffectActionRenderer.render(matrices, batcher, actions, replayTick, width, height);
            }
            if (showHud && state != null && state.visible) {
                HudRenderer.renderHotbars(matrices, batcher, List.of(state), 0, 0, width, height);
                batcher.flush();
                if (!showActions || actions == null || actions.getActiveGui(replayTick) == null) {
                    HeldItemTooltipRenderer.renderPlayback(batcher, replay.keyframes, state, replayTick, width, height);
                }
            }
            boolean stateAllowsCrosshair = state == null || state.crosshair;
            boolean bl = showCrosshair = showCrosshairOutput && stateAllowsCrosshair && (!showActions || actions == null || actions.getActiveGui(replayTick) == null && !MenuActionRenderer.blocksCrosshair(actions, replayTick));
            if (showCrosshair) {
                PovCrosshairRenderer.render(batcher, width, height, AttackIndicatorRenderer.progress(replay.keyframes, hud, hand, replayTick));
            }
            if (showActions && actions != null) {
                BossBarActionRenderer.render(batcher, actions, replayTick, width, height);
                StatusEffectActionRenderer.renderHUD(batcher, actions, replayTick, width, height);
                ChatActionRenderer.renderHUD(batcher, film, actions, replayTick, filmTick, width, height, state);
                ToastActionRenderer.renderHUD(batcher, actions, replayTick, width, height);
                GuiActionRenderer.render(matrices, batcher, replay.keyframes, actions, hand, replayTick, width, height, showCursor);
                MenuActionRenderer.render(matrices, batcher, actions, replayTick, width, height);
            }
            if (showCursor && !MenuActionRenderer.hidesCursor(actions, replayTick)) {
                PovCursorRenderer.render(batcher, state, width, height);
            }
        }
        finally {
            if (batcher.getContext() != null) {
                batcher.getContext().draw();
            }
            batcher.flush();
            RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean)true);
            RenderSystem.setProjectionMatrix((Matrix4f)previousProjection, (VertexSorter)VertexSorter.BY_Z);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void renderPlayback(Batcher2D batcher, float tickDelta) {
        ReplayKeyframes replayKeyframes;
        PovPlaybackContext.Frame playback = PovPlaybackContext.getActive(tickDelta);
        if (playback == null || !((replayKeyframes = playback.replay().keyframes) instanceof ReplayKeyframesPovAccess)) {
            return;
        }
        ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
        PovCameraClip clip = playback.clip();
        boolean showHud = clip == null || (Boolean)clip.hud.get() != false;
        boolean showCrosshairOutput = clip == null || (Boolean)clip.crosshair.get() != false;
        boolean showActions = clip == null || (Boolean)clip.actions.get() != false;
        boolean showCursor = clip == null || (Boolean)clip.cursor.get() != false;
        RecordedHudData hud = access.bbsPov$getHud();
        RecordedHandData hand = access.bbsPov$getHand();
        RecordedPovActions actions = access.bbsPov$getActions();
        HudState state = hud == null ? null : HudSampler.sample(hud, playback.replay().keyframes, playback.replayTick());
        int width = PovViewportMetrics.getMinecraftScaledWidth();
        int height = PovViewportMetrics.getMinecraftScaledHeight();
        MatrixStack matrices = batcher.getContext().getMatrices();
        Matrix4f previousProjection = new Matrix4f((Matrix4fc)RenderSystem.getProjectionMatrix());
        Matrix4f screenProjection = new Matrix4f().ortho(0.0f, (float)width, (float)height, 0.0f, -1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix((Matrix4f)screenProjection, (VertexSorter)VertexSorter.BY_Z);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate((GlStateManager.SrcFactor)GlStateManager.SrcFactor.SRC_ALPHA, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, (GlStateManager.SrcFactor)GlStateManager.SrcFactor.ZERO, (GlStateManager.DstFactor)GlStateManager.DstFactor.ONE);
        try {
            boolean showCrosshair;
            if (showActions && actions != null) {
                ScreenEffectActionRenderer.render(matrices, batcher, actions, playback.replayTick(), width, height);
            }
            if (showHud && state != null && state.visible) {
                HudRenderer.renderHotbars(matrices, batcher, List.of(state), 0, 0, width, height);
                batcher.flush();
                if (!showActions || actions == null || actions.getActiveGui(playback.replayTick()) == null) {
                    HeldItemTooltipRenderer.renderPlayback(batcher, playback.replay().keyframes, state, playback.replayTick(), width, height);
                }
            }
            boolean stateAllowsCrosshair = state == null || state.crosshair;
            boolean bl = showCrosshair = showCrosshairOutput && stateAllowsCrosshair && (!showActions || actions == null || actions.getActiveGui(playback.replayTick()) == null && !MenuActionRenderer.blocksCrosshair(actions, playback.replayTick()));
            if (showCrosshair) {
                PovCrosshairRenderer.render(batcher, width, height, AttackIndicatorRenderer.progress(playback.replay().keyframes, hud, hand, playback.replayTick()));
            }
            if (showActions && actions != null) {
                BossBarActionRenderer.render(batcher, actions, playback.replayTick(), width, height);
                StatusEffectActionRenderer.renderHUD(batcher, actions, playback.replayTick(), width, height);
                ChatActionRenderer.renderHUD(batcher, playback.film(), actions, playback.replayTick(), playback.filmTick(), width, height, state);
                ToastActionRenderer.renderHUD(batcher, actions, playback.replayTick(), width, height);
                GuiActionRenderer.render(matrices, batcher, playback.replay().keyframes, actions, hand, playback.replayTick(), width, height, showCursor);
                MenuActionRenderer.render(matrices, batcher, actions, playback.replayTick(), width, height);
            }
            if (showCursor && !MenuActionRenderer.hidesCursor(actions, playback.replayTick())) {
                PovCursorRenderer.render(batcher, state, width, height);
            }
        }
        finally {
            if (batcher.getContext() != null) {
                batcher.getContext().draw();
            }
            batcher.flush();
            RenderSystem.setShaderColor((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean)true);
            RenderSystem.setProjectionMatrix((Matrix4f)previousProjection, (VertexSorter)VertexSorter.BY_Z);
        }
    }
}

