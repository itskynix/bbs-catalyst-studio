/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory
 *  mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel
 *  mchorse.bbs_mod.utils.Pair
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelinePanel;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UIKeyframeEditor.class}, remap=false)
public abstract class UIKeyframeEditorPovMixin
extends UITimelinePanel {
    @Shadow
    public UIKeyframeFactory<?> editor;

    @Inject(method={"pickKeyframe"}, at={@At(value="TAIL")})
    private void bbsPov$dynamicallyAdjustViewWidth(Keyframe keyframe, CallbackInfo info) {
        UIKeyframeEditor self = (UIKeyframeEditor)(Object)this;
        if (self.view != null) {
            if (this.target == null) {
                if (this.editor != null) {
                    self.view.w(1.0f, -140);
                } else {
                    self.view.w(1.0f);
                }
                self.resize();
            } else {
                self.view.w(1.0f);
                self.resize();
            }
        }
    }

    @Inject(method={"getBone"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$getParentPoseBone(CallbackInfoReturnable<Pair<String, TransformSpace>> info) {
        UIKeyframeFactory<?> uIKeyframeFactory = this.editor;
        if (!(uIKeyframeFactory instanceof UIPoseKeyframeFactory)) {
            return;
        }
        UIPoseKeyframeFactory poseFactory = (UIPoseKeyframeFactory)uIKeyframeFactory;
        Keyframe keyframe = poseFactory.getKeyframe();
        if (keyframe == null || keyframe.getParent() == null) {
            return;
        }
        String channel = keyframe.getParent().getId();
        if (!"pov_hand_pose".equals(channel) && !"pov_hand_item_pose".equals(channel)) {
            return;
        }
        String bone = (String)poseFactory.poseEditor.groups.list.getCurrentFirst();
        if (bone != null && !bone.isBlank()) {
            info.setReturnValue(new Pair(bone, poseFactory.poseEditor.transform.getSpace()));
        }
    }
}

