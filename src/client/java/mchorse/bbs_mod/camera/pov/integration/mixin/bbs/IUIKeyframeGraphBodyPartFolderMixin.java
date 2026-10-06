/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartFolderSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={IUIKeyframeGraph.class}, remap=false)
public interface IUIKeyframeGraphBodyPartFolderMixin {
    @Inject(method={"addKeyframeManually"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$blockBodyPartFolderKeys(UIKeyframeSheet sheet, float tick, Object value, CallbackInfoReturnable<Keyframe> info) {
        if (sheet instanceof PovBodyPartFolderSheet) {
            info.setReturnValue(null);
        }
    }
}

