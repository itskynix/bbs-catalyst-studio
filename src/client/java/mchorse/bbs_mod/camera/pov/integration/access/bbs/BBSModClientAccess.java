/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.option.KeyBinding
 */
package mchorse.bbs_mod.camera.pov.integration.access.bbs;

import mchorse.bbs_mod.camera.pov.integration.mixin.bbs.BBSModClientAccessor;
import net.minecraft.client.option.KeyBinding;

public final class BBSModClientAccess {
    private BBSModClientAccess() {
    }

    public static KeyBinding bbsPov$getKeyPlayFilm() {
        return BBSModClientAccessor.bbsPov$getKeyPlayFilm();
    }

    public static KeyBinding bbsPov$getKeyRecordVideo() {
        return BBSModClientAccessor.bbsPov$getKeyRecordVideo();
    }
}

