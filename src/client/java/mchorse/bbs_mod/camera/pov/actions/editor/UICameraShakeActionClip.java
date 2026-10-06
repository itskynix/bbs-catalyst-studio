/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.CameraShakePovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UICameraShakeActionClip
extends UIPovActionClip<CameraShakePovActionClip> {
    public UIButton editKeyframes;
    public UIKeyframeEditor keyframes;

    public UICameraShakeActionClip(CameraShakePovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> (Integer)((CameraShakePovActionClip)this.clip).duration.get());
        this.editKeyframes = new UIButton(IKey.constant((String)"Edit Keyframes"), button -> {
            this.updateKeyframeSheets();
            this.editor.embedView((UIElement)this.keyframes);
            this.keyframes.view.resetView();
            if (this.keyframes.view.getGraph() != null) {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
    }

    private void updateKeyframeSheets() {
        this.keyframes.view.removeAllSheets();
        this.keyframes.view.addSheet(new UIKeyframeSheet("active", IKey.constant((String)"Active"), 0xFF5577, ((CameraShakePovActionClip)this.clip).active, null).icon(Icons.PLAY).seed(() -> true));
        this.keyframes.view.addSheet(new UIKeyframeSheet("hurt_time", IKey.constant((String)"Hurt Time"), 0xFF8844, ((CameraShakePovActionClip)this.clip).hurtTime, null).icon(Icons.TIME).seed(() -> 10));
        this.keyframes.view.addSheet(new UIKeyframeSheet("max_hurt_time", IKey.constant((String)"Max Hurt Time"), 0xCC6633, ((CameraShakePovActionClip)this.clip).maxHurtTime, null).icon(Icons.TIME).seed(() -> 10));
        this.keyframes.view.addSheet(new UIKeyframeSheet("damage_tilt_yaw", IKey.constant((String)"Damage Tilt Yaw"), 4891615, ((CameraShakePovActionClip)this.clip).damageTiltYaw, null).icon(Icons.LOOKING).seed(() -> Float.valueOf(0.0f)));
        this.keyframes.view.addSheet(new UIKeyframeSheet("death_time", IKey.constant((String)"Death Time"), 0x888888, ((CameraShakePovActionClip)this.clip).deathTime, null).icon(Icons.CLOSE).seed(() -> 0));
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add((IUIElement)this.section(IKey.constant((String)"Camera Shake Keyframes"), new UIElement[]{this.editKeyframes}));
    }

    @Override
    public void fillData() {
        super.fillData();
        this.updateKeyframeSheets();
        if (this.keyframes != null && this.keyframes.view != null && this.keyframes.view.getGraph() != null) {
            this.keyframes.view.getGraph().clearSelection();
        }
    }

    public void render(UIContext context) {
        if (this.keyframes != null && !this.keyframes.hasParent() && this.keyframes.view != null && this.keyframes.view.getGraph() != null && this.keyframes.view.getGraph().getSelected() != null) {
            this.keyframes.view.getGraph().clearSelection();
        }
        super.render(context);
    }
}

