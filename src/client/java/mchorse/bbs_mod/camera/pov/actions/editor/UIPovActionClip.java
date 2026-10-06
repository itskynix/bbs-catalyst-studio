/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.film.clips.UIClip
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;

public class UIPovActionClip<T extends PovActionClip>
extends UIClip<T> {
    public UIPovActionClip(T clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    protected void registerUI() {
        super.registerUI();
    }

    protected void registerPanels() {
        super.registerPanels();
    }

    protected void addEnvelopes() {
    }

    public void fillData() {
        super.fillData();
    }
}

