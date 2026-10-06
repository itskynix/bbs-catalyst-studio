/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import java.util.function.Consumer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIKeyframes.class}, remap=false)
public abstract class UIKeyframesPovMixin {
    @Shadow
    private Consumer<Keyframe> callback;
    @Shadow
    private IUIKeyframeGraph currentGraph;
    @Unique
    private boolean bbsPov$hadSelection;

    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void bbsPov$autoCloseEditorOnDeselect(UIContext context, CallbackInfo info) {
        if (this.currentGraph != null) {
            boolean hasSelected;
            boolean bl = hasSelected = this.currentGraph.getSelected() != null;
            if (!hasSelected && this.bbsPov$hadSelection && this.callback != null) {
                this.callback.accept(null);
            }
            this.bbsPov$hadSelection = hasSelected;
        }
    }
}

