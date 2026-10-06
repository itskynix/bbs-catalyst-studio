/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.ui.utils.UI
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 */
package mchorse.bbs_mod.camera.pov.actions.chat.editor;

import mchorse.bbs_mod.camera.pov.actions.chat.editor.UIFormattedTextarea;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIChatTextKeyframeFactory
extends UIKeyframeFactory<String> {
    public UIFormattedTextarea textarea;
    private String lastValue;

    public UIChatTextKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        this.lastValue = (String)keyframe.getValue();
        this.textarea = new UIFormattedTextarea(str -> {
            this.setValue(str);
            this.lastValue = str;
        });
        this.textarea.h(80);
        this.textarea.setFormattedText(this.lastValue != null ? this.lastValue : "");
        this.scroll.add(new IUIElement[]{UI.label((IKey)IKey.constant((String)"Chat Text:")), this.textarea});
    }

    public void update() {
        super.update();
        String val = (String)this.keyframe.getValue();
        if (val == null ? this.lastValue != null : !val.equals(this.lastValue)) {
            this.lastValue = val;
            this.textarea.setFormattedText(val != null ? val : "");
        }
    }

    public void render(UIContext context) {
        context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), -15461356);
        super.render(context);
    }
}

