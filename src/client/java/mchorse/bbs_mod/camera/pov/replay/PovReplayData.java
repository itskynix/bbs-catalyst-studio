/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.replay;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;

public final class PovReplayData {
    public final RecordedHudData hud;
    public final RecordedHandData hand;
    public final RecordedPovActions actions;

    public PovReplayData(RecordedHudData hud, RecordedHandData hand, RecordedPovActions actions) {
        this.hud = hud;
        this.hand = hand;
        this.actions = actions;
    }

    public static PovReplayData of(ReplayKeyframesPovAccess access) {
        return new PovReplayData(access.bbsPov$getHud(), access.bbsPov$getHand(), access.bbsPov$getActions());
    }
}

