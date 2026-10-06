/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.camera.clips.misc.Subtitle
 *  mchorse.bbs_mod.ui.film.UISubtitleRenderer
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.util.math.MatrixStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import java.util.List;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UISubtitleRenderer.class}, remap=false)
public abstract class UISubtitleRendererPovMixin {
    @Inject(method={"renderSubtitles"}, at={@At(value="RETURN")})
    private static void bbsPov$renderHotbar(MatrixStack matrices, Batcher2D batcher, List<Subtitle> subtitles, CallbackInfo info) {
    }
}

