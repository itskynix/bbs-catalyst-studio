/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.UITrackpad
 *  mchorse.bbs_mod.ui.framework.elements.input.UITransform
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UITransform.class}, remap=false)
public abstract class UITransformPovMixin
extends UIElement {
    @Shadow
    public UITrackpad tz;
    @Shadow
    public UITrackpad sz;

    @Inject(method={"toggleUniformScale"}, at={@At(value="RETURN")})
    private void bbsPov$keepLayoutScaleTwoDimensional(CallbackInfo info) {
        if (!this.tz.hasParent() && this.sz.hasParent()) {
            this.sz.removeFromParent();
            UIElement container = this.getParentContainer();
            if (container != null) {
                container.resize();
            }
        }
    }
}

