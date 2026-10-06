/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet$Section
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 */
package mchorse.bbs_mod.camera.pov.bodypart;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public class PovBodyPartFolderSheet
extends UIKeyframeSheet {
    public PovBodyPartFolderSheet(String id, IKey title, KeyframeChannel<?> channel) {
        super(id, title, 0, channel, null);
        this.section = new UIKeyframeSheet.Section(id, title, null, 0);
    }
}

