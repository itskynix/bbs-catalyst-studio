/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Keyboard
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.playback.PovPlaybackInput;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Keyboard.class}, priority=1100)
public abstract class KeyboardPovPlaybackMixin {
    @Inject(method={"onKey"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$lockPlaybackKeys(long window, int key, int scancode, int action, int modifiers, CallbackInfo info) {
        if (action != 0 && PovPlaybackInput.isLocked() && !PovPlaybackInput.isAllowed(key, scancode)) {
            info.cancel();
        }
    }

    @Inject(method={"onChar"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$lockPlaybackText(long window, int codePoint, int modifiers, CallbackInfo info) {
        if (PovPlaybackInput.isLocked()) {
            info.cancel();
        }
    }
}

