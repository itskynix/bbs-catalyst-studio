/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.pose.Transform
 */
package mchorse.bbs_mod.camera.pov.hud.editor;

import mchorse.bbs_mod.camera.pov.hud.editor.IUIPropTransform2DLayout;
import java.util.List;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;

public final class UIHotbarTransformKeyframeFactory
extends UITransformKeyframeFactory {
    public UIHotbarTransformKeyframeFactory(Keyframe<Transform> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        UIElement rotateRowElement;
        Object object;
        List rows = List.copyOf(this.transform.getChildren());
        this.transform.tz.removeFromParent();
        this.transform.sz.removeFromParent();
        this.transform.rx.removeFromParent();
        this.transform.ry.removeFromParent();
        if (rows.size() > 3 && (object = rows.get(3)) instanceof UIElement && !(rotateRowElement = (UIElement)object).getChildren().isEmpty()) {
            ((UIElement)rotateRowElement.getChildren().get(0)).setEnabled(false);
        }
        if (!rows.isEmpty()) {
            this.transform.remove((UIElement)rows.get(0));
        }
        for (int i = 4; i < rows.size(); ++i) {
            this.transform.remove((UIElement)rows.get(i));
        }
        this.transform.h(48);
        object = this.transform;
        if (object instanceof IUIPropTransform2DLayout) {
            IUIPropTransform2DLayout layout = (IUIPropTransform2DLayout)object;
            layout.bbsPov$set2DLayout(true);
        }
    }

    public void render(UIContext context) {
        context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), -15461356);
        super.render(context);
    }
}

