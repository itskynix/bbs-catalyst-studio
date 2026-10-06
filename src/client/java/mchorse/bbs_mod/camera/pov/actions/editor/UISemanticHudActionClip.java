/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.UITrackpad
 *  mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.SemanticHudPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;

public class UISemanticHudActionClip
extends UIPovActionClip<SemanticHudPovActionClip> {
    public UITrackpad opacity;
    public UITextbox state;

    public UISemanticHudActionClip(SemanticHudPovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        this.opacity = new UITrackpad(value -> this.editor.editMultiple(((SemanticHudPovActionClip)this.clip).opacity, channel -> {
            if (channel.isEmpty()) {
                channel.insert(0.0f, Float.valueOf(value.floatValue()));
            } else {
                channel.get(0).setValue(Float.valueOf(value.floatValue()));
            }
        }));
        this.opacity.limit(0.0, 1.0, true).forcedLabel(IKey.constant((String)"Opacity"));
        this.state = new UITextbox(10000, text -> this.editor.editMultiple(((SemanticHudPovActionClip)this.clip).state, channel -> {
            if (channel.isEmpty()) {
                channel.insert(0.0f, text);
            } else {
                channel.get(0).setValue(text);
            }
        }));
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add((IUIElement)this.section(IKey.constant((String)(((SemanticHudPovActionClip)this.clip).getActionType().title + " Settings")), new UIElement[]{this.opacity, this.state}));
    }

    @Override
    public void fillData() {
        super.fillData();
        this.opacity.setValue(((SemanticHudPovActionClip)this.clip).opacity.isEmpty() ? 1.0 : (double)((Float)((SemanticHudPovActionClip)this.clip).opacity.get(0).getValue()).floatValue());
        this.state.setText(((SemanticHudPovActionClip)this.clip).state.isEmpty() ? "" : (String)((SemanticHudPovActionClip)this.clip).state.get(0).getValue());
    }
}

