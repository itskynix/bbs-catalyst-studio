/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.UITrackpad
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 */
package mchorse.bbs_mod.camera.pov.hud.editor;

import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIHotbarIntegerKeyframeFactory
extends UIKeyframeFactory<Integer> {
    private UITrackpad value = new UITrackpad(arg_0 -> ((UIHotbarIntegerKeyframeFactory)this).setValue(arg_0));
    private UIBezierHandles handles;

    public UIHotbarIntegerKeyframeFactory(Keyframe<Integer> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        this.value.integer();
        this.value.setValue((double)((Integer)keyframe.getValue()).intValue());
        this.handles = new UIBezierHandles(keyframe);
        this.scroll.add(new IUIElement[]{this.value, this.handles.createColumn()});
    }

    public void update() {
        super.update();
        this.value.setValue((double)((Integer)this.keyframe.getValue()).intValue());
        this.handles.update();
    }
}

