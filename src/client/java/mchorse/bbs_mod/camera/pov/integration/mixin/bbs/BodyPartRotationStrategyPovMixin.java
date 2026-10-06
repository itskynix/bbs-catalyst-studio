/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.ArcballDrag
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.DragContext
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory$Variant
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp
 *  mchorse.bbs_mod.utils.Axis
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartGizmoDrag;
import mchorse.bbs_mod.ui.framework.elements.input.drag.ArcballDrag;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategy;
import mchorse.bbs_mod.ui.framework.elements.input.drag.DragStrategyFactory;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.utils.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={DragStrategyFactory.class}, remap=false)
public class BodyPartRotationStrategyPovMixin {
    @Inject(method={"create"}, at={@At(value="HEAD")}, cancellable=true)
    private static void bbsPov$freeBodyPartRotation(DragContext context, TransformOp operation, Axis axis, Axis axis2, DragStrategyFactory.Variant variant, CallbackInfoReturnable<DragStrategy> info) {
        if (context.drag() instanceof PovBodyPartGizmoDrag && operation == TransformOp.ROTATE && variant == DragStrategyFactory.Variant.TRACKBALL) {
            info.setReturnValue(new ArcballDrag(context));
        }
    }
}

