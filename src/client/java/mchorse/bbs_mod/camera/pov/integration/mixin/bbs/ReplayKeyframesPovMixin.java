/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.core.ValueGroup
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ReplayKeyframes.class}, remap=false)
public class ReplayKeyframesPovMixin
implements ReplayKeyframesPovAccess {
    @Unique
    private RecordedHudData bbsPov$hud;
    @Unique
    private RecordedHandData bbsPov$hand;
    @Unique
    private RecordedPovActions bbsPov$actions;

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void bbsPov$addChannels(String id, CallbackInfo info) {
        this.bbsPov$hud = new RecordedHudData();
        this.bbsPov$hud.addTo((ValueGroup)(Object)this);
        this.bbsPov$hand = new RecordedHandData();
        this.bbsPov$hand.addTo((ValueGroup)(Object)this);
        this.bbsPov$actions = new RecordedPovActions();
        ((ReplayKeyframes)(Object)this).add((BaseValue)this.bbsPov$actions);
    }

    @Override
    public RecordedHudData bbsPov$getHud() {
        return this.bbsPov$hud;
    }

    @Override
    public RecordedHandData bbsPov$getHand() {
        return this.bbsPov$hand;
    }

    @Override
    public RecordedPovActions bbsPov$getActions() {
        return this.bbsPov$actions;
    }
}

