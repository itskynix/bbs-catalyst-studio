/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.forms.editors.utils.UICropOverlayPanel
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  org.joml.Vector4f
 *  org.joml.Vector4fc
 */
package mchorse.bbs_mod.camera.pov.config;

import mchorse.bbs_mod.camera.pov.config.CursorCropValue;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.utils.UICropOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public class UICursorCropOverlayPanel
extends UICropOverlayPanel {
    private final CursorCropValue value;
    private final Vector4f workingCrop;

    public UICursorCropOverlayPanel(Link texture, CursorCropValue value) {
        this(texture, value, UICursorCropOverlayPanel.createWorkingCrop(value));
    }

    private UICursorCropOverlayPanel(Link texture, CursorCropValue value, Vector4f workingCrop) {
        super(texture, workingCrop);
        this.value = value;
        this.workingCrop = workingCrop;
        this.title.label = IKey.constant((String)"Crop Cursor");
        this.cropEditor.relative(this.content).xy(0, 0).w(1.0f).h(1.0f, -32);
        UIButton cancel = new UIButton(IKey.constant((String)"Cancel"), button -> this.close());
        UIButton ok = new UIButton(IKey.constant((String)"OK"), button -> {
            UICursorCropOverlayPanel.normalizeBottomEdge(this.workingCrop);
            this.value.set(new Vector4f((Vector4fc)this.workingCrop));
            this.close();
        });
        UIElement controls = new UIElement();
        controls.relative(this.content).x(6).y(1.0f, -26).w(1.0f, -12).h(20);
        cancel.relative(controls).xy(0, 0).w(0.5f, -3).h(1.0f);
        ok.relative(controls).x(0.5f, 3).y(0).w(0.5f, -3).h(1.0f);
        controls.add(new IUIElement[]{cancel, ok});
        this.content.add((IUIElement)controls);
    }

    private static Vector4f createWorkingCrop(CursorCropValue value) {
        Vector4f crop = new Vector4f((Vector4fc)value.get());
        UICursorCropOverlayPanel.normalizeBottomEdge(crop);
        return crop;
    }

    private static void normalizeBottomEdge(Vector4f crop) {
        if (crop.w == 1.0f) {
            crop.w = 0.0f;
        }
    }
}

