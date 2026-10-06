/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.integration.access.bbs;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;

public interface ReplayKeyframesPovAccess {
    public RecordedHudData bbsPov$getHud();

    public RecordedHandData bbsPov$getHand();

    public RecordedPovActions bbsPov$getActions();
}

