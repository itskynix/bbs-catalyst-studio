/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.hud.InGameHud
 *  net.minecraft.entity.Entity
 *  net.minecraft.util.Identifier
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.InGameHudVignettePovAccess;
import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={InGameHud.class})
public abstract class InGameHudPovMixin
implements InGameHudVignettePovAccess {
    @Shadow
    public float field_2013;
    @Shadow
    public float field_27959;
    @Unique
    private boolean bbsPov$oldHudHidden;
    @Unique
    private boolean bbsPov$changedHudHidden;

    @Shadow
    public abstract void method_1735(DrawContext var1, Entity var2);

    @Override
    public void bbsPov$renderVignetteOverlay(DrawContext context, Entity entity) {
        this.method_1735(context, entity);
    }

    @Override
    public float bbsPov$getVignetteDarkness() {
        return this.field_2013;
    }

    @Override
    public float bbsPov$getSpyglassScale() {
        return this.field_27959;
    }

    @Unique
    private static boolean bbsPov$shouldSuppressScreenEffects() {
        return PovEffectSuppression.isBbsActive();
    }

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$enterPlaybackF1(DrawContext context, float tickDelta, CallbackInfo info) {
        boolean liveOverlay;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean f4Recording = BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording();
        boolean bl = liveOverlay = f4Recording && (client.currentScreen != null && !(client.currentScreen instanceof UIScreen) || client.player != null && client.player.getSleepTimer() > 0);
        if (PovPlaybackContext.getActive() != null || liveOverlay) {
            this.bbsPov$oldHudHidden = client.options.hudHidden;
            this.bbsPov$changedHudHidden = true;
            client.options.hudHidden = true;
        }
    }

    @Inject(method={"renderVignetteOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$skipHudVignetteAfterOverlay(DrawContext context, Entity entity, CallbackInfo info) {
        if (InGameHudPovMixin.bbsPov$shouldSuppressScreenEffects()) {
            info.cancel();
        }
    }

    @Inject(method={"renderPortalOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$skipHudPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo info) {
        if (InGameHudPovMixin.bbsPov$shouldSuppressScreenEffects()) {
            info.cancel();
        }
    }

    @Inject(method={"renderSpyglassOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$skipHudSpyglassOverlay(DrawContext context, float scale, CallbackInfo info) {
        if (InGameHudPovMixin.bbsPov$shouldSuppressScreenEffects()) {
            info.cancel();
        }
    }

    @Inject(method={"renderOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$skipHudCustomOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo info) {
        if (InGameHudPovMixin.bbsPov$shouldSuppressScreenEffects()) {
            info.cancel();
        }
    }

    @Inject(method={"renderStatusEffectOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$skipHudStatusEffectOverlay(DrawContext context, CallbackInfo info) {
        if (InGameHudPovMixin.bbsPov$shouldSuppressScreenEffects()) {
            info.cancel();
        }
    }

    @Inject(method={"render"}, at={@At(value="RETURN")})
    private void bbsPov$leavePlaybackF1(DrawContext context, float tickDelta, CallbackInfo info) {
        if (this.bbsPov$changedHudHidden) {
            MinecraftClient.getInstance().options.hudHidden = this.bbsPov$oldHudHidden;
            this.bbsPov$changedHudHidden = false;
        }
    }

    @Inject(method={"renderCrosshair"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideCrosshair(DrawContext context, CallbackInfo info) {
        if (MinecraftClient.getInstance().currentScreen != null || PovPlaybackContext.getActive() != null) {
            info.cancel();
        }
    }

    @Inject(method={"renderStatusEffectOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$hideStatusEffectOverlay(DrawContext context, CallbackInfo info) {
        if (PovPlaybackContext.getActive() != null || PovReplaySettings.getFilmPanel() != null) {
            info.cancel();
        }
    }
}

