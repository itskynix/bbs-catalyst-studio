/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.hud.InGameOverlayRenderer
 *  net.minecraft.client.texture.Sprite
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.registry.tag.FluidTags
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.utils.PovEffectSuppression;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={InGameOverlayRenderer.class})
public abstract class InGameOverlayRendererPovMixin {
    @Inject(method={"renderUnderwaterOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$hideUnderwaterOverlay(MinecraftClient client, MatrixStack matrices, CallbackInfo info) {
        if (PovEffectSuppression.isBbsActive()) {
            info.cancel();
        }
    }

    @Inject(method={"renderFireOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$hideFireOverlay(MinecraftClient client, MatrixStack matrices, CallbackInfo info) {
        if (PovEffectSuppression.isBbsActive()) {
            if (client.player != null && (client.player.isSubmergedIn(FluidTags.LAVA) || client.player.isInLava())) {
                return;
            }
            info.cancel();
        }
    }

    @Inject(method={"renderInWallOverlay"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$hideInWallOverlay(Sprite sprite, MatrixStack matrices, CallbackInfo info) {
        if (PovEffectSuppression.isBbsActive()) {
            info.cancel();
        }
    }
}

