/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Mouse
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.playback.PovPlaybackInput;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Mouse.class}, priority=1100)
public abstract class MousePovPlaybackMixin {
    @Inject(method={"onMouseButton"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$lockPlaybackMouseButtons(long window, int button, int action, int mods, CallbackInfo info) {
        if (action != 0 && PovPlaybackInput.isFirstPersonPlayback()) {
            info.cancel();
        }
    }

    @Inject(method={"onMouseScroll"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$lockPlaybackMouseScroll(long window, double horizontal, double vertical, CallbackInfo info) {
        if (PovPlaybackInput.isFirstPersonPlayback()) {
            info.cancel();
        }
    }

    @Inject(method={"updateMouse"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$lockPlaybackMouseMove(CallbackInfo info) {
        if (PovPlaybackInput.isFirstPersonPlayback()) {
            info.cancel();
        }
    }
}

