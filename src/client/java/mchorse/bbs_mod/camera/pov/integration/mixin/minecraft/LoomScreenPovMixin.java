/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ingame.LoomScreen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LoomScreen.class})
public abstract class LoomScreenPovMixin {
    @Shadow
    private int field_39190;

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$captureLoomState(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        GuiSnapshotCapture.updateLoom(this.field_39190);
    }

    @Inject(method={"mouseScrolled"}, at={@At(value="RETURN")})
    private void bbsPov$captureLoomWheel(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> info) {
        GuiSnapshotCapture.updateLoom(this.field_39190);
    }

    @Inject(method={"mouseDragged"}, at={@At(value="RETURN")})
    private void bbsPov$captureLoomScrollbar(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> info) {
        GuiSnapshotCapture.updateLoom(this.field_39190);
    }
}

