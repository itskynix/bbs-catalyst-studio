/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.Screen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Screen.class})
public abstract class ScreenPovMixin {
    @Inject(method={"renderWithTooltip"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$suppressScreenDuringLiveVideoOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        boolean videoRecording;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean bl = videoRecording = BBSModClient.getVideoRecorder() != null && BBSModClient.getVideoRecorder().isRecording();
        if (videoRecording && client.currentScreen != null && !(client.currentScreen instanceof UIScreen) && (client.player == null || client.player.getSleepTimer() <= 0)) {
            info.cancel();
        }
    }
}

