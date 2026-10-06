/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect.editor;

import mchorse.bbs_mod.camera.pov.bodypart.PovBodyPartFolderSheet;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public final class ScreenEffectFolderSheet
extends PovBodyPartFolderSheet {
    public ScreenEffectFolderSheet(String id, IKey title, int color) {
        super(id, title, new KeyframeChannel(id, (IKeyframeFactory)KeyframeFactories.BOOLEAN));
        this.color = color;
    }

    public ScreenEffectFolderSheet(String id, IKey title, KeyframeChannel<?> channel) {
        super(id, title, channel != null ? channel : new KeyframeChannel(id, (IKeyframeFactory)KeyframeFactories.BOOLEAN));
    }
}

