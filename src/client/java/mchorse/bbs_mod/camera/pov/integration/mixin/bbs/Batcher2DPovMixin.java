/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.ui.utils.icons.Icon
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Batcher2D.class}, remap=false)
public abstract class Batcher2DPovMixin {
    @Inject(method={"icon(Lmchorse/bbs_mod/ui/utils/icons/Icon;IFFFF)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$onIcon(Icon icon, int color, float x, float y, float ax, float ay, CallbackInfo info) {
        if (icon == Icons.CURSOR && PovSettings.cursorTexture != null && PovSettings.cursorTexture.get() != null) {
            Batcher2D batcher = (Batcher2D)(Object)this;
            batcher.getContext().getMatrices().push();
            batcher.getContext().getMatrices().translate(x -= (float)icon.w * ax, y -= (float)icon.h * ay, 0.0f);
            float scale = PovSettings.getCursorDefaultScale();
            batcher.getContext().getMatrices().scale(scale, scale, 1.0f);
            PovSettings.renderCursor(batcher);
            batcher.flush();
            batcher.getContext().getMatrices().pop();
            info.cancel();
        }
    }
}

